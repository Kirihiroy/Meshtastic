# Быстрый старт проекта Meshtastic

## Шаг 1: Добавь зависимости

Открой `app/build.gradle.kts` и добавь зависимости из файла `DEPENDENCIES.md`.

## Шаг 2: Добавь разрешения

Открой `app/src/main/AndroidManifest.xml` и добавь разрешения из `DEPENDENCIES.md`.

## Шаг 3: Первый тест Bluetooth

Создай простой тестовый экран для проверки Bluetooth:

1. Создай `ConnectionFragment.java` в пакете `ui/connection`
2. Добавь кнопку "Поиск устройств"
3. Используй `BluetoothManager` для поиска и подключения
4. Выводи в лог найденные устройства

**Пример кода для ConnectionFragment:**

```java
// В onCreateView:
BluetoothManager bluetoothManager = new BluetoothManager();

if (!bluetoothManager.isBluetoothEnabled()) {
    // Показать сообщение пользователю
    return;
}

Set<BluetoothDevice> pairedDevices = bluetoothManager.getPairedDevices();
// Отобразить список устройств
```

## Шаг 4: Тестирование

1. Подключи Android устройство через USB
2. Включи режим разработчика и USB отладку
3. Запусти приложение
4. Проверь логи в Logcat (фильтр по тегам: "BluetoothManager")

## Следующие шаги

Следуй плану из `PROJECT_PLAN.md`, начиная с **Этапа 1**.

---

## Полезные команды для отладки

### Просмотр логов Bluetooth:
```bash
adb logcat | grep -i bluetooth
```

### Просмотр всех логов приложения:
```bash
adb logcat | grep -i meshtastic
```

### Проверка разрешений на устройстве:
```bash
adb shell dumpsys package com.example.meshtastic | grep permission
```

---

## Частые проблемы

### Bluetooth не работает в эмуляторе
**Решение**: Используй реальное Android устройство. Эмулятор плохо поддерживает Bluetooth.

### Ошибка "BluetoothAdapter is null"
**Решение**: Убедись, что устройство поддерживает Bluetooth (все современные устройства поддерживают).

### Разрешения не запрашиваются
**Решение**: Проверь, что добавил разрешения в AndroidManifest.xml и используешь правильный API для запроса (ActivityResultContracts для Android 6+).

---

## Контакты и ресурсы

- **Meshtastic документация**: https://meshtastic.org/docs
- **Meshtastic GitHub**: https://github.com/meshtastic
- **Android Bluetooth Guide**: https://developer.android.com/guide/topics/connectivity/bluetooth
