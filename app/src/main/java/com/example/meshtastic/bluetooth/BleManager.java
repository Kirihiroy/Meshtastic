package com.example.meshtastic.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Meshtastic BLE transport:
 * - ToRadio: client writes protobuf bytes
 * - FromNum: notify/indicate counter (signals pending packets)
 * - FromRadio: client reads repeatedly until it returns empty
 *
 * Key point: you MUST drain FromRadio (READ ops), otherwise you'll never see inbound packets.
 */
public class BleManager {

    private static final String TAG = "BleManager";

    // Meshtastic BLE service + characteristics (as used by Meshtastic firmware)
    private static final UUID UUID_MESHTASTIC_SERVICE = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd");
    private static final UUID UUID_TO_RADIO           = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7");
    private static final UUID UUID_FROM_NUM           = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453");
    // Common Meshtastic FromRadio UUID in newer firmware (HackMD/protocol docs)
    private static final UUID UUID_FROM_RADIO         = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002");
    private static final UUID UUID_CCCD               = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // Timeouts (ms)
    private static final long OP_TIMEOUT_MS = 8000;

    // Fallback poll interval if notifications/indications don't fire (ms)
    private static final long FROM_NUM_POLL_MS = 1500;

    public interface ScanListener {
        void onDeviceFound(BluetoothDevice device, int rssi);
    }

    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onError(String msg);
    }

    public interface BytesListener {
        void onBytes(byte[] data);
    }

    private final Context appContext;
    private boolean readyNotified = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final HandlerThread gattThread = new HandlerThread("MeshtasticBleGatt");
    private final Handler gattHandler;

    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;

    private ScanCallback scanCallback;

    private BluetoothGatt gatt;
    private BluetoothDevice device;

    private BluetoothGattCharacteristic toRadioChar;
    private BluetoothGattCharacteristic fromRadioChar;
    private BluetoothGattCharacteristic fromNumChar;

    private ConnectionListener connectionListener;
    private BytesListener bytesListener;

    private int mtu = 23;
    private boolean connected = false;

    // --- Operation queue (required: only one GATT op in-flight at a time) ---
    private enum OpType { WRITE_CHAR, READ_CHAR, WRITE_DESC }

    private static class GattOp {
        final OpType type;
        final BluetoothGattCharacteristic ch;
        final BluetoothGattDescriptor desc;
        final byte[] value;

        GattOp(OpType t, BluetoothGattCharacteristic ch, BluetoothGattDescriptor desc, byte[] value) {
            this.type = t;
            this.ch = ch;
            this.desc = desc;
            this.value = value;
        }

        @Override public String toString() {
            switch (type) {
                case WRITE_DESC: return "WRITE_DESC desc=" + (desc != null ? desc.getUuid() : null);
                case READ_CHAR:  return "READ_CHAR  char=" + (ch != null ? ch.getUuid() : null);
                case WRITE_CHAR: return "WRITE_CHAR char=" + (ch != null ? ch.getUuid() : null) + " len=" + (value != null ? value.length : 0);
            }
            return type.name();
        }
    }

    private final ArrayDeque<GattOp> opQueue = new ArrayDeque<>();
    private GattOp inFlight = null;
    private final Runnable opTimeoutRunnable = new Runnable() {
        @Override public void run() {
            gattHandler.post(() -> {
                if (inFlight != null) {
                    Log.w(TAG, "GattOp timeout: " + inFlight);
                    // Clear the stuck op and move on; if the stack is truly wedged, later ops will also timeout.
                    inFlight = null;
                    processNextOp();
                }
            });
        }
    };

    // --- FromRadio draining state ---
    private boolean drainingFromRadio = false;
    private long lastFromNum = -1;

    // --- Poll fallback ---
    private boolean fromNumPollEnabled = false;
    private final Runnable fromNumPollRunnable = new Runnable() {
        @Override public void run() {
            gattHandler.post(() -> {
                if (!fromNumPollEnabled || gatt == null || fromNumChar == null) return;
                enqueueRead(fromNumChar);
                mainHandler.postDelayed(fromNumPollRunnable, FROM_NUM_POLL_MS);
            });
        }
    };

    public BleManager(Context ctx) {
        this.appContext = ctx.getApplicationContext();
        gattThread.start();
        gattHandler = new Handler(gattThread.getLooper());

        BluetoothManager bm = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = bm != null ? bm.getAdapter() : null;
        scanner = (adapter != null) ? adapter.getBluetoothLeScanner() : null;
    }

    public boolean isBluetoothEnabled() {
        return adapter != null && adapter.isEnabled();
    }

    // -------------------- Scan --------------------

    @SuppressLint("MissingPermission")
    public void startScan(ScanListener listener) {
        if (scanner == null) {
            Log.w(TAG, "BLE scanner unavailable");
            return;
        }
        stopScan();

        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(UUID_MESHTASTIC_SERVICE))
                .build());

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        scanCallback = new ScanCallback() {
            @Override public void onScanResult(int callbackType, ScanResult result) {
                if (result == null || result.getDevice() == null) return;
                if (listener != null) listener.onDeviceFound(result.getDevice(), result.getRssi());
            }

            @Override public void onBatchScanResults(List<ScanResult> results) {
                if (results == null) return;
                for (ScanResult r : results) onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, r);
            }

            @Override public void onScanFailed(int errorCode) {
                Log.w(TAG, "scan failed: " + errorCode);
            }
        };

        scanner.startScan(filters, settings, scanCallback);
    }

    @SuppressLint("MissingPermission")
    public void stopScan() {
        if (scanner != null && scanCallback != null) {
            try { scanner.stopScan(scanCallback); } catch (Exception ignored) {}
        }
        scanCallback = null;
    }

    // -------------------- Connect / Disconnect --------------------

    @SuppressLint("MissingPermission")
    public void connect(BluetoothDevice device,
                        ConnectionListener connectionListener,
                        BytesListener bytesListener) {
        this.device = device;
        this.connectionListener = connectionListener;
        this.bytesListener = bytesListener;

        gattHandler.post(() -> {
            cleanupGattNoCallback();

            if (device == null) {
                notifyError("connect(): device == null");
                return;
            }
            Log.d(TAG, "Connecting to " + device.getAddress());
            // TRANSPORT_LE is API 23+, safe for modern apps.
            gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        });
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        gattHandler.post(() -> {
            fromNumPollEnabled = false;
            mainHandler.removeCallbacks(fromNumPollRunnable);

            drainingFromRadio = false;
            opQueue.clear();
            inFlight = null;
            mainHandler.removeCallbacks(opTimeoutRunnable);

            if (gatt != null) {
                try { gatt.disconnect(); } catch (Exception ignored) {}
                try { gatt.close(); } catch (Exception ignored) {}
            }
            gatt = null;
            connected = false;
            readyNotified = false;
            toRadioChar = null;
            fromRadioChar = null;
            fromNumChar = null;

            if (connectionListener != null) {
                mainHandler.post(() -> connectionListener.onDisconnected());
            }
        });
    }

    private void cleanupGattNoCallback() {
        fromNumPollEnabled = false;
        mainHandler.removeCallbacks(fromNumPollRunnable);

        drainingFromRadio = false;
        opQueue.clear();
        inFlight = null;
        mainHandler.removeCallbacks(opTimeoutRunnable);

        if (gatt != null) {
            try { gatt.disconnect(); } catch (Exception ignored) {}
            try { gatt.close(); } catch (Exception ignored) {}
        }
        gatt = null;
        connected = false;
        toRadioChar = null;
        fromRadioChar = null;
        fromNumChar = null;
    }

    // -------------------- Write to radio --------------------

    /**
     * Write a complete ToRadio protobuf message as bytes.
     * NOTE: Meshtastic firmware expects the full protobuf in a single write.
     */
    public void write(byte[] toRadioProtobufBytes) {
        if (toRadioProtobufBytes == null) return;
        gattHandler.post(() -> {
            if (gatt == null || !connected || toRadioChar == null) {
                Log.w(TAG, "write(): not connected/ready yet");
                return;
            }
            enqueueWrite(toRadioChar, toRadioProtobufBytes);
            // After sending, proactively try to drain in case the radio already queued a reply.
            drainFromRadio();
        });
    }

    // -------------------- GATT callback --------------------

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        @SuppressLint("MissingPermission")
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            gattHandler.post(() -> {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "onConnectionStateChange status=" + status + " newState=" + newState);
                }

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connected = true;
                    mtu = 23;
                    Log.d(TAG, "BLE connected, requesting MTU");
                    try { gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH); } catch (Exception ignored) {}

                    boolean started = false;
                    try { started = gatt.requestMtu(512); } catch (Exception ignored) {}
                    Log.d(TAG, "requestMtu(512) started=" + started);
                    if (!started) {
                        // Some stacks may refuse; proceed anyway.
                        boolean ds = gatt.discoverServices();
                        Log.d(TAG, "discoverServices started=" + ds);
                    }

                    if (connectionListener != null) {
                        mainHandler.post(() -> connectionListener.onConnected());
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "BLE disconnected");
                    cleanupGattNoCallback();
                    if (connectionListener != null) {
                        mainHandler.post(() -> connectionListener.onDisconnected());
                    }
                }
            });
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            gattHandler.post(() -> {
                Log.d(TAG, "onMtuChanged mtu=" + mtu + " status=" + status);
                if (status == BluetoothGatt.GATT_SUCCESS && mtu > 0) {
                    BleManager.this.mtu = mtu;
                    Log.d(TAG, "MTU updated: " + mtu);
                }
                boolean started = gatt.discoverServices();
                Log.d(TAG, "discoverServices started=" + started);
            });
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            gattHandler.post(() -> {
                Log.d(TAG, "onServicesDiscovered status=" + status);
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    notifyError("discoverServices failed status=" + status);
                    return;
                }

                BluetoothGattService svc = gatt.getService(UUID_MESHTASTIC_SERVICE);
                if (svc == null) {
                    notifyError("Meshtastic BLE service not found");
                    return;
                }

                Log.d(TAG, "Using Meshtastic BLE service");

                // Debug: list characteristics
                for (BluetoothGattCharacteristic c : svc.getCharacteristics()) {
                    Log.d(TAG, "Char: " + c.getUuid() + " props=" + c.getProperties());
                }


                toRadioChar = svc.getCharacteristic(UUID_TO_RADIO);
                fromNumChar = svc.getCharacteristic(UUID_FROM_NUM);
                fromRadioChar = svc.getCharacteristic(UUID_FROM_RADIO);



                if (fromRadioChar == null) {
                    // Fallback: choose a readable characteristic within this service that is not ToRadio or FromNum.
                    for (BluetoothGattCharacteristic c : svc.getCharacteristics()) {
                        UUID u = c.getUuid();
                        if (u == null) continue;
                        if (u.equals(UUID_TO_RADIO) || u.equals(UUID_FROM_NUM)) continue;
                        int p = c.getProperties();
                        if ((p & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                            fromRadioChar = c;
                            Log.w(TAG, "FromRadio UUID fallback picked: " + u);
                            break;
                        }
                    }
                }
                if (toRadioChar == null) notifyError("ToRadio characteristic not found");
                if (fromNumChar == null) notifyError("FromNum characteristic not found");
                if (fromRadioChar == null) notifyError("FromRadio characteristic not found");

                if (toRadioChar == null || fromNumChar == null || fromRadioChar == null) {
                    return;
                }

                // Enable notifications/indications on FromNum.
                enableFromNumNotifications();
            });
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            gattHandler.post(() -> {
                Log.d(TAG, "onDescriptorWrite status=" + status + " uuid=" + (descriptor != null ? descriptor.getUuid() : null));
                finishOp();

                // After CCCD is enabled, start polling fallback and drain immediately.
                if (descriptor != null && UUID_CCCD.equals(descriptor.getUuid())) {
                    startFromNumPollFallback();
                    // First drain immediately (there may already be messages queued)
                    drainFromRadio();
                    // Also read FromNum once to capture a baseline counter
                    enqueueRead(fromNumChar);
                }
            });
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            gattHandler.post(() -> {
                Log.d(TAG, "onCharacteristicWrite status=" + status + " uuid=" + (characteristic != null ? characteristic.getUuid() : null));
                finishOp();
                // After any write, try draining (radio responses can arrive quickly)
                drainFromRadio();
            });
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            gattHandler.post(() -> {
                UUID uuid = (characteristic != null) ? characteristic.getUuid() : null;
                byte[] value = (characteristic != null) ? characteristic.getValue() : null;

                // Log reads lightly; FromRadio can be noisy.
                if (uuid != null && !UUID_FROM_RADIO.equals(uuid)) {
                    Log.d(TAG, "onCharacteristicRead status=" + status + " uuid=" + uuid + " len=" + (value != null ? value.length : 0));
                }

                finishOp();

                if (status != BluetoothGatt.GATT_SUCCESS || uuid == null) {
                    return;
                }

                if (UUID_FROM_NUM.equals(uuid)) {
                    handleFromNumValue(value);
                    return;
                }

                if (UUID_FROM_RADIO.equals(uuid)) {
                    handleFromRadioValue(value);
                }
            });
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            gattHandler.post(() -> {
                UUID uuid = (characteristic != null) ? characteristic.getUuid() : null;
                byte[] value = (characteristic != null) ? characteristic.getValue() : null;

                if (uuid == null) return;

                if (UUID_FROM_NUM.equals(uuid)) {
                    Log.d(TAG, "onCharacteristicChanged FromNum len=" + (value != null ? value.length : 0));
                    handleFromNumValue(value);
                }
            });
        }
    };

    // -------------------- Notification + drain logic --------------------

    @SuppressLint("MissingPermission")
    private void enableFromNumNotifications() {
        if (gatt == null || fromNumChar == null) return;

        boolean setOk = false;
        try {
            setOk = gatt.setCharacteristicNotification(fromNumChar, true);
        } catch (Exception e) {
            Log.w(TAG, "setCharacteristicNotification failed", e);
        }
        Log.d(TAG, "setCharacteristicNotification(fromNum) ok=" + setOk);

        BluetoothGattDescriptor cccd = fromNumChar.getDescriptor(UUID_CCCD);
        if (cccd == null) {
            notifyError("CCCD not found on FromNum");
            return;
        }

        // Some firmwares expose INDICATE instead of NOTIFY; be robust.
        final int props = fromNumChar.getProperties();
        final boolean indicate = (props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
        final byte[] enableValue = indicate
                ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;

        enqueueWriteDesc(cccd, enableValue);
    }

    private void handleFromNumValue(byte[] value) {
        long v = decodeUint32LittleEndian(value);
        if (v < 0) return;

        if (lastFromNum < 0) {
            lastFromNum = v;
            Log.d(TAG, "FromNum baseline=" + lastFromNum);
            // Even if baseline, try draining once (some radios won't bump counter until after you drain)
            drainFromRadio();
            return;
        }

        if (v != lastFromNum) {
            Log.d(TAG, "FromNum changed " + lastFromNum + " -> " + v);
            lastFromNum = v;
            drainFromRadio();
        }
    }

    private void handleFromRadioValue(byte[] value) {
        if (!drainingFromRadio) {
            // A read might have been triggered manually; still handle.
            drainingFromRadio = true;
        }

        if (value == null || value.length == 0) {
            // No more packets queued on the device.
            drainingFromRadio = false;
            return;
        }

        if (bytesListener != null) {
            byte[] copy = new byte[value.length];
            System.arraycopy(value, 0, copy, 0, value.length);
            mainHandler.post(() -> bytesListener.onBytes(copy));
        }

        // Continue draining until empty.
        enqueueRead(fromRadioChar);
    }

    private void drainFromRadio() {
        if (gatt == null || fromRadioChar == null) return;
        if (drainingFromRadio) return;

        drainingFromRadio = true;
        enqueueRead(fromRadioChar);
    }

    private void startFromNumPollFallback() {
        if (fromNumPollEnabled) return;
        fromNumPollEnabled = true;
        mainHandler.removeCallbacks(fromNumPollRunnable);
        mainHandler.postDelayed(fromNumPollRunnable, FROM_NUM_POLL_MS);
    }

    private static long decodeUint32LittleEndian(byte[] value) {
        if (value == null || value.length == 0) return -1;
        int len = Math.min(4, value.length);
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        bb.put(value, 0, len);
        bb.rewind();
        // as unsigned
        return bb.getInt() & 0xffffffffL;
    }

    // -------------------- Queue helpers --------------------

    private void enqueueWrite(BluetoothGattCharacteristic ch, byte[] value) {
        if (ch == null || gatt == null) return;
        opQueue.add(new GattOp(OpType.WRITE_CHAR, ch, null, value));
        processNextOp();
    }

    private void enqueueRead(BluetoothGattCharacteristic ch) {
        if (ch == null || gatt == null) return;
        opQueue.add(new GattOp(OpType.READ_CHAR, ch, null, null));
        processNextOp();
    }

    private void enqueueWriteDesc(BluetoothGattDescriptor desc, byte[] value) {
        if (desc == null || gatt == null) return;
        opQueue.add(new GattOp(OpType.WRITE_DESC, null, desc, value));
        processNextOp();
    }

    @SuppressLint("MissingPermission")
    private void processNextOp() {
        if (gatt == null) return;
        if (inFlight != null) return;
        if (opQueue.isEmpty()) return;

        inFlight = opQueue.poll();
        if (inFlight == null) return;

        Log.d(TAG, "GattOp started: " + inFlight);

        boolean started = false;
        try {
            switch (inFlight.type) {
                case WRITE_DESC: {
                    BluetoothGattDescriptor d = inFlight.desc;
                    if (d == null) break;
                    d.setValue(inFlight.value);
                    started = gatt.writeDescriptor(d);
                    break;
                }
                case READ_CHAR: {
                    BluetoothGattCharacteristic c = inFlight.ch;
                    if (c == null) break;
                    started = gatt.readCharacteristic(c);
                    break;
                }
                case WRITE_CHAR: {
                    BluetoothGattCharacteristic c = inFlight.ch;
                    if (c == null) break;
                    // Use write-with-response for reliability.
                    c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    c.setValue(inFlight.value);
                    started = gatt.writeCharacteristic(c);
                    break;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "GattOp failed to start: " + inFlight, e);
            started = false;
        }

        if (!started) {
            Log.w(TAG, "GattOp did not start: " + inFlight);
            inFlight = null;
            processNextOp();
            return;
        }

        // Arm timeout
        mainHandler.removeCallbacks(opTimeoutRunnable);
        mainHandler.postDelayed(opTimeoutRunnable, OP_TIMEOUT_MS);
    }

    private void finishOp() {
        // Cancel timeout for current op and continue.
        mainHandler.removeCallbacks(opTimeoutRunnable);
        inFlight = null;
        processNextOp();
    }

    private void notifyError(String msg) {
        Log.e(TAG, msg);
        if (connectionListener != null) {
            mainHandler.post(() -> connectionListener.onError(msg));
        }
    }
    private void notifyReadyConnectedOnce() {
        if (readyNotified) return;
        readyNotified = true;
        if (connectionListener != null) {
            mainHandler.post(() -> connectionListener.onConnected());
        }
    }
}



