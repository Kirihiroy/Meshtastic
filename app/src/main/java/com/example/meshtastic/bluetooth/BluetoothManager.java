package com.example.meshtastic.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

/**
 * Менеджер для работы с Bluetooth соединением через Serial Port Profile (SPP).
 * Отвечает за поиск устройств, подключение и базовую работу с потоками данных.
 */
public class BluetoothManager {
    private static final String TAG = "BluetoothManager";
    
    // UUID для Serial Port Profile (стандартный)
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private boolean isConnected = false;
    
    public BluetoothManager() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }
    
    /**
     * Проверяет, включен ли Bluetooth на устройстве.
     */
    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }
    
    /**
     * Получает список уже сопряженных устройств.
     */
    public Set<BluetoothDevice> getPairedDevices() {
        if (bluetoothAdapter == null) {
            return null;
        }
        return bluetoothAdapter.getBondedDevices();
    }
    
    /**
     * Подключается к указанному устройству через SPP.
     * @param device устройство для подключения
     * @return true если подключение успешно
     */
    public boolean connect(BluetoothDevice device) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth не включен");
            return false;
        }
        
        try {
            // Создаем сокет для подключения
            bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            bluetoothSocket.connect();
            
            // Получаем потоки для чтения/записи
            inputStream = bluetoothSocket.getInputStream();
            outputStream = bluetoothSocket.getOutputStream();
            
            isConnected = true;
            Log.d(TAG, "Подключено к устройству: " + device.getName());
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "Ошибка подключения", e);
            closeConnection();
            return false;
        }
    }
    
    /**
     * Отправляет данные через Bluetooth.
     * @param data массив байтов для отправки
     * @return true если отправка успешна
     */
    public boolean sendData(byte[] data) {
        if (!isConnected || outputStream == null) {
            Log.e(TAG, "Нет активного соединения");
            return false;
        }
        
        try {
            outputStream.write(data);
            outputStream.flush();
            Log.d(TAG, "Отправлено байт: " + data.length);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Ошибка отправки данных", e);
            return false;
        }
    }
    
    /**
     * Читает данные из потока (блокирующий вызов).
     * В реальной реализации это должно быть в отдельном потоке.
     * @param buffer буфер для чтения
     * @return количество прочитанных байтов, или -1 при ошибке
     */
    public int readData(byte[] buffer) {
        if (!isConnected || inputStream == null) {
            return -1;
        }
        
        try {
            return inputStream.read(buffer);
        } catch (IOException e) {
            Log.e(TAG, "Ошибка чтения данных", e);
            return -1;
        }
    }
    
    /**
     * Закрывает соединение и освобождает ресурсы.
     */
    public void closeConnection() {
        isConnected = false;
        
        try {
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
                bluetoothSocket = null;
            }
            Log.d(TAG, "Соединение закрыто");
        } catch (IOException e) {
            Log.e(TAG, "Ошибка при закрытии соединения", e);
        }
    }
    
    /**
     * Проверяет состояние соединения.
     */
    public boolean isConnected() {
        return isConnected && bluetoothSocket != null && bluetoothSocket.isConnected();
    }
}
