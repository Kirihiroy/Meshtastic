package com.example.meshtastic.bluetooth;

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
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.UUID;

/**
 * BLE менеджер для Meshtastic.
 *
 * Поддерживает 2 варианта протокола:
 * - Meshtastic BLE Client API (чаще всего на устройствах типа Heltec)
 *   Service: 6ba1b218-15a8-461f-9fa8-5dcae273eafd
 *   toRadio (write): f75c76d2-129e-4dad-a1dd-7866124401e7
 *   fromRadio (read): 2c55e69e-4993-11ed-b878-0242ac120002
 *   fromNum (notify): ed9da18c-a800-4f66-a670-aa7547e34453
 *
 * - Nordic UART Service (NUS) (встречается реже, оставлен как fallback)
 *   Service: 6e400001-b5a3-f393-e0a9-e50e24dcca9e
 *   RX (write): 6e400002-b5a3-f393-e0a9-e50e24dcca9e
 *   TX (notify): 6e400003-b5a3-f393-e0a9-e50e24dcca9e
 */
public class BleManager {
    private static final String TAG = "BleManager";

    public static final UUID MESHTASTIC_SERVICE_UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd");
    public static final UUID MESHTASTIC_TORADIO_UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7");
    public static final UUID MESHTASTIC_FROMRADIO_UUID = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002");
    public static final UUID MESHTASTIC_FROMNUM_UUID = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453");

    public static final UUID NUS_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID NUS_RX_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID NUS_TX_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID CLIENT_CHAR_CFG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public interface ScanListener {
        void onDeviceFound(BluetoothDevice device);
        void onScanFailed(int errorCode);
    }

    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onError(String message);
    }

    public interface DataListener {
        void onDataReceived(byte[] data);
    }

    private final Context context;
    private final BluetoothAdapter adapter;
    private final BluetoothLeScanner scanner;

    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic txCharacteristic;
    private BluetoothGattCharacteristic rxCharacteristic;
    private BluetoothGattCharacteristic fromRadioCharacteristic;
    private BluetoothGattCharacteristic fromNumCharacteristic;
    private ScanListener scanListener;
    private ConnectionListener connectionListener;
    private DataListener dataListener;
    private boolean waitingForCccdWrite;
    private final GattOpQueue opQueue = new GattOpQueue();
    private int mtu = 23;

    public BleManager(Context context) {
        this.context = context.getApplicationContext();
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager != null ? manager.getAdapter() : null;
        scanner = adapter != null ? adapter.getBluetoothLeScanner() : null;
    }

    public boolean isBluetoothEnabled() {
        return adapter != null && adapter.isEnabled();
    }

    public void startScan(ScanListener listener) {
        this.scanListener = listener;
        if (scanner == null) {
            if (listener != null) listener.onScanFailed(-1);
            return;
        }
        scanner.startScan(scanCallback);
    }

    public void stopScan() {
        if (scanner != null) {
            scanner.stopScan(scanCallback);
        }
    }

    public void connect(BluetoothDevice device,
                        ConnectionListener connectionListener,
                        DataListener dataListener) {
        this.connectionListener = connectionListener;
        this.dataListener = dataListener;
        if (device == null) {
            if (connectionListener != null) connectionListener.onError("Устройство не выбрано");
            return;
        }
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        fromRadioCharacteristic = null;
        fromNumCharacteristic = null;
        waitingForCccdWrite = false;
        opQueue.clear();
    }

    public boolean write(byte[] data) {
        if (bluetoothGatt == null || rxCharacteristic == null) return false;
        if (data == null || data.length == 0) return false;
        int maxPayload = Math.max(20, mtu - 3);
        int offset = 0;
        while (offset < data.length) {
            int size = Math.min(maxPayload, data.length - offset);
            byte[] chunk = Arrays.copyOfRange(data, offset, offset + size);
            opQueue.enqueueWrite(rxCharacteristic, chunk, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            offset += size;
        }
        opQueue.kick();
        return true;
    }

    public boolean writeString(String text) {
        return write(text.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isConnected() {
        return bluetoothGatt != null
                && rxCharacteristic != null
                && (txCharacteristic != null || fromNumCharacteristic != null);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            if (scanListener != null) {
                scanListener.onDeviceFound(result.getDevice());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            if (scanListener != null) {
                scanListener.onScanFailed(errorCode);
            }
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "BLE connected, discovering services");
                gatt.requestMtu(512);
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "BLE disconnected");
                if (connectionListener != null) connectionListener.onDisconnected();
                disconnect();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (connectionListener != null) connectionListener.onError("Services discovery failed: " + status);
                return;
            }

            // Лог всех сервисов: очень помогает понять, что реально отдает прошивка
            for (BluetoothGattService s : gatt.getServices()) {
                Log.d(TAG, "Service found: " + s.getUuid());
            }

            BluetoothGattService service = null;

            // 1) Предпочтительно: Meshtastic BLE Client API
            BluetoothGattService meshtasticService = gatt.getService(MESHTASTIC_SERVICE_UUID);
            if (meshtasticService != null) {
                service = meshtasticService;
                rxCharacteristic = service.getCharacteristic(MESHTASTIC_TORADIO_UUID); // write
                fromRadioCharacteristic = service.getCharacteristic(MESHTASTIC_FROMRADIO_UUID); // read
                fromNumCharacteristic = service.getCharacteristic(MESHTASTIC_FROMNUM_UUID); // notify
                txCharacteristic = null;
                Log.d(TAG, "Using Meshtastic BLE service");
            } else {
                // 2) Fallback: Nordic UART Service
                BluetoothGattService nusService = gatt.getService(NUS_SERVICE_UUID);
                if (nusService != null) {
                    service = nusService;
                    rxCharacteristic = service.getCharacteristic(NUS_RX_UUID);
                    txCharacteristic = service.getCharacteristic(NUS_TX_UUID);
                    Log.d(TAG, "Using Nordic UART (NUS) service");
                }
            }

            if (service == null) {
                if (connectionListener != null) {
                    connectionListener.onError("Meshtastic/NUS service not found. Посмотри UUID сервисов в Logcat (tag BleManager) или через nRF Connect.");
                }
                return;
            }
            if (rxCharacteristic == null || (txCharacteristic == null && fromNumCharacteristic == null)) {
                if (connectionListener != null) connectionListener.onError("TX/RX characteristics not found");
                return;
            }

            BluetoothGattCharacteristic notifyCharacteristic = txCharacteristic;
            if (fromNumCharacteristic != null) {
                notifyCharacteristic = fromNumCharacteristic;
            }

            // enable notifications
            gatt.setCharacteristicNotification(notifyCharacteristic, true);
            BluetoothGattDescriptor descriptor = notifyCharacteristic.getDescriptor(CLIENT_CHAR_CFG);
            if (descriptor != null) {
                waitingForCccdWrite = true;
                opQueue.enqueueDescriptorWrite(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                opQueue.kick();
            } else {
                if (connectionListener != null) connectionListener.onConnected();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            UUID uuid = characteristic.getUuid();
            if (MESHTASTIC_FROMNUM_UUID.equals(uuid)) {
                drainFromRadio();
                return;
            }
            if (NUS_TX_UUID.equals(uuid)) {
                byte[] data = characteristic.getValue();
                if (dataListener != null) dataListener.onDataReceived(data);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicRead status=" + status + " uuid=" + characteristic.getUuid());
            opQueue.completeActive(status == BluetoothGatt.GATT_SUCCESS);
            if (status != BluetoothGatt.GATT_SUCCESS) return;
            if (MESHTASTIC_FROMRADIO_UUID.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();
                Log.d(TAG, "FromRadio len=" + (data == null ? 0 : data.length));
                if (data != null && data.length > 0) {
                    if (dataListener != null) dataListener.onDataReceived(data);
                    opQueue.enqueueRead(fromRadioCharacteristic);
                    opQueue.kick();
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.d(TAG, "onDescriptorWrite status=" + status + " uuid=" + descriptor.getUuid());
            opQueue.completeActive(status == BluetoothGatt.GATT_SUCCESS);
            if (!waitingForCccdWrite) {
                return;
            }
            waitingForCccdWrite = false;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (connectionListener != null) connectionListener.onConnected();
            } else {
                if (connectionListener != null) connectionListener.onError("CCCD write failed: " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicWrite status=" + status + " uuid=" + characteristic.getUuid());
            opQueue.completeActive(status == BluetoothGatt.GATT_SUCCESS);
            if (status != BluetoothGatt.GATT_SUCCESS && connectionListener != null) {
                connectionListener.onError("Write failed: " + status);
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                pendingWrite = null;
                pendingOffset = 0;
                lastChunkSize = 0;
                return;
            }
            if (pendingWrite != null) {
                pendingOffset += lastChunkSize;
                if (pendingOffset >= pendingWrite.length) {
                    pendingWrite = null;
                    lastChunkSize = 0;
                } else {
                    writeNextChunk();
                }
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BleManager.this.mtu = mtu;
                Log.d(TAG, "MTU updated: " + mtu);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BleManager.this.mtu = mtu;
                Log.d(TAG, "MTU updated: " + mtu);
            }
        }
    };

    private void drainFromRadio() {
        if (fromRadioCharacteristic == null) return;
        opQueue.enqueueRead(fromRadioCharacteristic);
        opQueue.kick();
    }

    private class GattOpQueue {
        private static final long TIMEOUT_MS = 5000L;
        private static final int MAX_RETRIES = 4;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Deque<GattOp> queue = new ArrayDeque<>();
        private GattOp active;

        void enqueueWrite(BluetoothGattCharacteristic characteristic, byte[] value, int writeType) {
            queue.add(new GattOp(GattOp.Type.WRITE_CHAR, characteristic, value, writeType));
        }

        void enqueueRead(BluetoothGattCharacteristic characteristic) {
            queue.add(new GattOp(GattOp.Type.READ_CHAR, characteristic, null, 0));
        }

        void enqueueDescriptorWrite(BluetoothGattDescriptor descriptor, byte[] value) {
            queue.add(new GattOp(GattOp.Type.WRITE_DESC, descriptor, value));
        }

        void clear() {
            queue.clear();
            active = null;
            handler.removeCallbacksAndMessages(null);
        }

        void kick() {
            if (active != null) return;
            startNext();
        }

        void completeActive(boolean success) {
            if (active == null) return;
            handler.removeCallbacks(active.timeoutRunnable);
            if (!success) {
                Log.d(TAG, "GattOp failed: " + active.describe());
            }
            active = null;
            startNext();
        }

        private void startNext() {
            if (active != null) return;
            if (bluetoothGatt == null) return;
            active = queue.poll();
            if (active == null) return;
            startActive();
        }

        private void startActive() {
            if (active == null || bluetoothGatt == null) return;
            boolean started = active.start();
            if (!started) {
                scheduleRetry();
                return;
            }
            Log.d(TAG, "GattOp started: " + active.describe());
            active.timeoutRunnable = () -> {
                Log.d(TAG, "GattOp timeout: " + active.describe());
                active = null;
                startNext();
            };
            handler.postDelayed(active.timeoutRunnable, TIMEOUT_MS);
        }

        private void scheduleRetry() {
            if (active == null) return;
            if (active.retries >= MAX_RETRIES) {
                Log.d(TAG, "GattOp retries exhausted: " + active.describe());
                active = null;
                startNext();
                return;
            }
            int delay = active.nextBackoffMs();
            Log.d(TAG, "GattOp retry " + active.retries + ": " + active.describe() + " in " + delay + "ms");
            handler.postDelayed(this::startActive, delay);
        }
    }

    private class GattOp {
        enum Type {WRITE_CHAR, READ_CHAR, WRITE_DESC}

        private final Type type;
        private final BluetoothGattCharacteristic characteristic;
        private final BluetoothGattDescriptor descriptor;
        private final byte[] value;
        private final int writeType;
        private int retries;
        private Runnable timeoutRunnable;

        GattOp(Type type, BluetoothGattCharacteristic characteristic, byte[] value, int writeType) {
            this.type = type;
            this.characteristic = characteristic;
            this.descriptor = null;
            this.value = value;
            this.writeType = writeType;
        }

        GattOp(Type type, BluetoothGattDescriptor descriptor, byte[] value) {
            this.type = type;
            this.characteristic = null;
            this.descriptor = descriptor;
            this.value = value;
            this.writeType = 0;
        }

        boolean start() {
            retries++;
            switch (type) {
                case WRITE_CHAR:
                    if (characteristic == null) return false;
                    characteristic.setValue(value);
                    characteristic.setWriteType(writeType);
                    return bluetoothGatt.writeCharacteristic(characteristic);
                case READ_CHAR:
                    if (characteristic == null) return false;
                    return bluetoothGatt.readCharacteristic(characteristic);
                case WRITE_DESC:
                    if (descriptor == null) return false;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        return bluetoothGatt.writeDescriptor(descriptor, value) == BluetoothGatt.GATT_SUCCESS;
                    }
                    descriptor.setValue(value);
                    return bluetoothGatt.writeDescriptor(descriptor);
                default:
                    return false;
            }
        }

        int nextBackoffMs() {
            int base = 120;
            int delay = base << Math.min(retries, 4);
            return Math.min(delay, 1500);
        }

        String describe() {
            String id = type.name();
            if (characteristic != null) {
                return id + " char=" + characteristic.getUuid();
            }
            if (descriptor != null) {
                return id + " desc=" + descriptor.getUuid();
            }
            return id;
        }
    }
}
