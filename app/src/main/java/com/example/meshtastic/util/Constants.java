package com.example.meshtastic.util;

/**
 * Константы приложения.
 */
public class Constants {
    
    // Предустановленные сообщения для быстрой отправки
    public static final String[] QUICK_MESSAGES = {
        "Я в порядке",
        "Нужна помощь",
        "Иду к точке сбора",
        "Обнаружил препятствие",
        "Все хорошо"
    };
    
    // UUID для Bluetooth SPP
    public static final String SPP_UUID_STRING = "00001101-0000-1000-8000-00805F9B34FB";
    
    // Таймауты и интервалы (в миллисекундах)
    public static final int BLUETOOTH_CONNECTION_TIMEOUT = 10000;
    public static final int GPS_UPDATE_INTERVAL = 5000; // 5 секунд
    public static final int GPS_UPDATE_DISTANCE = 10; // 10 метров
    
    // Размеры буферов
    public static final int READ_BUFFER_SIZE = 1024;
    
    private Constants() {
        // Утилитный класс, не должен быть инстанциирован
    }
}
