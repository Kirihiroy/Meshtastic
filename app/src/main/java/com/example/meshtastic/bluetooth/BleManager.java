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
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/**
 * BLE менеджер для Meshtastic.
 *
 * Поддерживает 2 варианта протокола:
 * - Meshtastic BLE Client API (чаще всего на устройствах типа Heltec)
 *   Service: 6ba1b218-15a8-461f-9fa8-5dcae273eafd
 *   toRadio (write): f75c76d2-129e-4dad-a1dd-7866124401e7
 *   fromRadio (notify): 2c55e69e-4993-11ed-b878-0242ac120002
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
    private ScanListener scanListener;
    private ConnectionListener connectionListener;
    private DataListener dataListener;
    private byte[] pendingWrite;
    private int pendingOffset;
    private int lastChunkSize;
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
        pendingWrite = null;
        pendingOffset = 0;
        lastChunkSize = 0;
    }

    public boolean write(byte[] data) {
        if (bluetoothGatt == null || rxCharacteristic == null) return false;
        if (pendingWrite != null) return false;
        pendingWrite = data;
        pendingOffset = 0;
        return writeNextChunk();
    }

    public boolean writeString(String text) {
        return write(text.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isConnected() {
        return bluetoothGatt != null && txCharacteristic != null && rxCharacteristic != null;
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
                gatt.requestMtu(517);
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
                txCharacteristic = service.getCharacteristic(MESHTASTIC_FROMRADIO_UUID); // notify
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
            if (rxCharacteristic == null || txCharacteristic == null) {
                if (connectionListener != null) connectionListener.onError("TX/RX characteristics not found");
                return;
            }

            // enable notifications on TX
            gatt.setCharacteristicNotification(txCharacteristic, true);
            BluetoothGattDescriptor descriptor = txCharacteristic.getDescriptor(CLIENT_CHAR_CFG);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }
            if (connectionListener != null) connectionListener.onConnected();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            UUID uuid = characteristic.getUuid();
            if (MESHTASTIC_FROMRADIO_UUID.equals(uuid) || NUS_TX_UUID.equals(uuid)) {
                byte[] data = characteristic.getValue();
                if (dataListener != null) dataListener.onDataReceived(data);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status != BluetoothGatt.GATT_SUCCESS && connectionListener != null) {
                connectionListener.onError("Write failed: " + status);
            }
            if (status == BluetoothGatt.GATT_SUCCESS && pendingWrite != null) {
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
    };

    private boolean writeNextChunk() {
        if (bluetoothGatt == null || rxCharacteristic == null || pendingWrite == null) return false;
        int maxPayload = Math.max(20, mtu - 3);
        int remaining = pendingWrite.length - pendingOffset;
        int size = Math.min(maxPayload, remaining);
        byte[] chunk = Arrays.copyOfRange(pendingWrite, pendingOffset, pendingOffset + size);
        lastChunkSize = size;
        rxCharacteristic.setValue(chunk);
        rxCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        return bluetoothGatt.writeCharacteristic(rxCharacteristic);
    }
}
