# Устранение неполадок

## Содержание

1. [Проблемы с BLE](#проблемы-с-ble)
2. [Проблемы с подключением](#проблемы-с-подключением)
3. [Проблемы с данными](#проблемы-с-данными)
4. [Проблемы с настройками](#проблемы-с-настройками)
5. [Проблемы со сборкой](#проблемы-со-сборкой)
6. [Диагностические инструменты](#диагностические-инструменты)

---

## Проблемы с BLE

### 1.1 Устройство не найдено при сканировании

**Симптомы:**
- После "Поиск устройств" список пустой
- Таймаут сканирования без результатов

**Причины и решения:**

#### Причина 1: Bluetooth выключен
```
Проверка:
  Настройки → Bluetooth → должен быть включён

Решение:
  Включите Bluetooth на телефоне
```

#### Причина 2: Нет разрешения на геолокацию
```
Проверка:
  Настройки → Приложения → Meshtastic → Разрешения
  "Местоположение" должно быть "Разрешено"

Решение (Android 12+):
  Настройки → Приложения → Meshtastic → Разрешения
  → Местоположение → "Разрешить только при использовании"
  
Решение (через ADB):
  adb shell pm grant com.example.meshtastic \
      android.permission.ACCESS_FINE_LOCATION
```

#### Причина 3: Устройство Meshtastic выключено
```
Проверка:
  - Посмотрите на экран устройства (если есть)
  - Проверьте индикатор питания (LED)

Решение:
  - Включите устройство (долгое нажатие кнопки power)
  - Зарядите батарею
```

#### Причина 4: Устройство слишком далеко
```
BLE работает на расстоянии:
  - В помещении: до 10 метров
  - На открытом пространстве: до 50 метров
  - Через стены: 1-5 метров

Решение:
  Подойдите ближе к устройству (< 5 метров для стабильного сканирования)
```

#### Причина 5: BLE не включён на устройстве Meshtastic
```
Проверка через serial console (USB):
  screen /dev/ttyUSB0 115200
  > ls
  # Должно показать bluetooth: enabled

Решение:
  В настройках устройства включите BLE
```

### 1.2 Устройство найдено, но не подключается

**Симптомы:**
- Устройство в списке
- При "Подключиться" → таймаут или ошибка

**Причины и решения:**

#### Причина 1: Устройство занято другим подключением
```
BLE поддерживает только одно подключение одновременно

Проверка:
  - Закройте другие BLE приложения
  - Проверьте, подключён ли другой телефон

Решение:
  1. Отключите все другие BLE клиенты
  2. Перезагрузите устройство Meshtastic
  3. Повторите подключение
```

#### Причина 2: Кэш BLE на Android
```
Android может хранить устаревшие GATT данные

Решение:
  1. В приложении: отключитесь
  2. Настройки → Bluetooth → "забыть устройство"
  3. Перезапустите Bluetooth
  4. Повторите сканирование
```

#### Причина 3: Слишком много GATT устройств в кэше
```
Решение через ADB:
  adb shell settings put global ble_scan_always_enabled 0
  adb shell settings put global ble_scan_always_enabled 1
```

### 1.3 Подключение разрывается

**Симптомы:**
- Подключается, затем сразу отключается
- "Connected" → "Disconnected" в течение нескольких секунд

**Причины и решения:**

#### Причина 1: Слабый сигнал BLE
```
Проверка:
  Откройте вкладку "Статус" → проверьте SNR
  Если SNR < -5 dB → сигнал слабый

Решение:
  - Подойдите ближе
  - Уберите препятствия (металл, бетон)
  - Переместите устройство выше
```

#### Причина 2: Энергосбережение Android
```
Android может ограничивать BLE в фоне

Решение:
  Настройки → Приложения → Meshtastic
  → Батарея → "Неограниченно"
```

#### Причина 3: Перегрузка GATT
```
Слишком много операций в очереди

Решение:
  - Перезапустите приложение
  - Очистите кэш приложения:
    Настройки → Приложения → Meshtastic → Хранилище → Очистить кэш
```

---

## Проблемы с подключением

### 2.1 "Bluetooth выключен" хотя Bluetooth включён

**Причины и решения:**

```
Причина: BluetoothAdapter не инициализирован

Решение:
  1. Перезапустите приложение
  2. Перезапустите Bluetooth:
     Настройки → Bluetooth → выкл/вкл
  3. Перезагрузите телефон
```

### 2.2 "GATT operation failed"

**Симптомы:**
- В логах: "GATT error: 133"

**Причины и решения:**

```
Код 133 = GATT_ERROR

Причины:
  - Устройство отключилось
  - Таймаут операции
  - BLE stack зависание

Решение:
  1. adb shell settings put global bluetooth_disabled_profiles ""
  2. Перезагрузите телефон
  3. Переподключитесь
```

### 2.3 Разрешения постоянно запрашиваются

**Причины и решения:**

```
Причина: Разрешения revoked или never granted

Решение через ADB:
  # Android 12+
  adb shell pm grant com.example.meshtastic \
      android.permission.BLUETOOTH_SCAN
  adb shell pm grant com.example.meshtastic \
      android.permission.BLUETOOTH_CONNECT
  adb shell pm grant com.example.meshtastic \
      android.permission.ACCESS_FINE_LOCATION
```

---

## Проблемы с данными

### 3.1 Нет входящих данных (FromRadio)

**Симптомы:**
- Подключено, но поле "Последние входящие байты" = "—"
- Нет обновлений на вкладке "Статус"

**Диагностика:**

#### Шаг 1: Проверьте GATT характеристики
```powershell
adb logcat | Select-String "FromRadio characteristic"

Должно быть:
  "FromRadio characteristic found: 2c55e69e-..."
  
Если нет:
  - UUID не совпадает с прошивкой
  - Сервис не обнаружен
```

#### Шаг 2: Проверьте уведомления FromNum
```powershell
adb logcat | Select-String "setCharacteristicNotification"

Должно быть:
  "setCharacteristicNotification(fromNum) ok=true"
  
Если false:
  - Устройство не поддерживает notify
  - CCCD descriptor не найден
```

#### Шаг 3: Проверьте drain loop
```powershell
adb logcat | Select-String "drainFromRadio"

Должно быть периодически:
  "drainFromRadio started"
  "onCharacteristicRead FromRadio len=XX"
  
Если нет:
  - Drain loop не запустился
  - Очередь GATT заблокирована
```

**Решения:**

```
Решение 1: Принудительный drain
  Подключение → "Отправить тест (ping)"
  Должно вызвать ответ от устройства

Решение 2: Перезапуск BLE
  1. Отключиться
  2. Выключить Bluetooth
  3. Включить Bluetooth
  4. Подключиться заново

Решение 3: Перезагрузка устройства Meshtastic
  Долгое нажатие кнопки power → Reboot
```

### 3.2 Данные приходят, но не расшифровываются

**Симптомы:**
- "Последние входящие байты" обновляются (hex)
- "Расшифрованное FromRadio" = "—" или ошибка

**Диагностика:**

```powershell
adb logcat | Select-String "parseFromRadio"

Ошибки:
  "InvalidProtocolBufferException" → неправильный формат
  "Не удалось распарсить" → данные повреждены
```

**Решения:**

```
Причина 1: Старая версия protobuf схемы
  Решение: Обновите proto файлы из официального репозитория

Причина 2: Несовместимая прошивка устройства
  Решение: Обновите прошивку Meshtastic до 2.0+

Причина 3: Данные повреждены (шум BLE)
  Решение: Подойдите ближе к устройству
```

### 3.3 Узлы не отображаются

**Симптомы:**
- Подключено, данные приходят
- Вкладка "Узлы" пустая

**Диагностика:**

```powershell
adb logcat | Select-String "NODE_INFO"

Должно быть:
  "FromRadio id=XX NODE_INFO"
  "Added node to map: nodeNum=XXXXX"
```

**Решения:**

```
Причина 1: Сеть пустая (только ваше устройство)
  Решение: Дождитесь появления других узлов в радиусе LoRa

Причина 2: NODE_INFO не запрашивается
  Решение: Отправьте want_config_id для получения базы узлов

Причина 3: LiveData не обновляется
  Решение: Перейдите на другую вкладку и вернитесь
```

---

## Проблемы с настройками

### 4.1 "Не удалось отправить" при применении настроек

**Симптомы:**
- Нажатие "Применить на устройство" → Toast "Не удалось отправить"

**Диагностика:**

#### Проверка 1: Состояние подключения
```java
Вкладка "Подключение" → статус должен быть "Подключено: ..."
Если нет → переподключитесь
```

#### Проверка 2: Заполнены ли поля
```java
Обязательные поля:
  - Имя канала (не пусто)
  - PSK (не пусто)
```

#### Проверка 3: Длина PSK
```java
PSK должен быть 16 или 32 ASCII символа

Проверка:
  String psk = "MyKey";
  int len = psk.getBytes(StandardCharsets.UTF_8).length;
  System.out.println("PSK length: " + len);  // должно быть 16 или 32
```

**Решения:**

```
Решение 1: Проверьте подключение
  Вкладка "Подключение" → убедитесь в "Connected"

Решение 2: Исправьте PSK
  - Используйте ровно 16 символов: "MySecretKey12345"
  - Или ровно 32 символа: "ThisIsA32CharacterSecurePassKey!"

Решение 3: Повторите попытку
  Иногда GATT queue переполнен — подождите 5 секунд и повторите
```

### 4.2 Настройки отправлены, но не применились

**Симптомы:**
- Toast "Настройки отправлены"
- Устройство не изменило канал/PSK

**Диагностика:**

```powershell
# Подключитесь через serial console
screen /dev/ttyUSB0 115200

# Должно вывести:
INFO | Received admin packet: CHANNEL
INFO | Setting channel 0: name=YOUR_CHANNEL
INFO | PSK length: 16
INFO | Channel config saved

Если нет:
  - Пакет не дошёл
  - Устройство не обработало
```

**Решения:**

```
Решение 1: Перезагрузите устройство Meshtastic
  Многие настройки требуют reboot для применения

Решение 2: Проверьте версию прошивки
  Меню → About → Firmware version
  Требуется: 2.0+

Решение 3: Сбросьте настройки на устройстве
  Меню → Factory Reset → подтвердите
  Повторите настройку
```

---

## Проблемы со сборкой

### 5.1 JAVA_HOME not set

**Симптомы:**
```
ERROR: JAVA_HOME is not set and no 'java' command could be found
```

**Решения:**

#### Windows PowerShell
```powershell
# Временно (текущая сессия)
$env:JAVA_HOME = "C:\Program Files\Java\jdk-11"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# Постоянно (требует admin)
[System.Environment]::SetEnvironmentVariable(
    "JAVA_HOME",
    "C:\Program Files\Java\jdk-11",
    "Machine"
)

# Проверка
java -version
```

#### macOS / Linux
```bash
# Добавьте в ~/.zshrc или ~/.bashrc
export JAVA_HOME=$(/usr/libexec/java_home -v 11)
export PATH=$JAVA_HOME/bin:$PATH

# Применить
source ~/.zshrc

# Проверка
java -version
```

### 5.2 Gradle sync failed

**Симптомы:**
```
Could not resolve all dependencies
Could not download protobuf-javalite.jar
```

**Решения:**

```
Решение 1: Очистить кэш Gradle
  .\gradlew.bat clean
  rm -r $env:USERPROFILE\.gradle\caches
  .\gradlew.bat --refresh-dependencies

Решение 2: Проверить proxy/firewall
  # Если за корпоративным proxy, добавьте в gradle.properties:
  systemProp.http.proxyHost=proxy.company.com
  systemProp.http.proxyPort=8080

Решение 3: Использовать VPN
  Некоторые репозитории могут быть недоступны без VPN
```

### 5.3 Android SDK not found

**Симптомы:**
```
SDK location not found
```

**Решение:**

Создайте `local.properties` в корне проекта:
```properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
```

Или укажите через environment variable:
```powershell
$env:ANDROID_SDK_ROOT = "C:\Users\<user>\AppData\Local\Android\Sdk"
```

---

## Диагностические инструменты

### 6.1 ADB Logcat

#### Базовые команды
```powershell
# Весь лог приложения
adb logcat | Select-String "com.example.meshtastic"

# Только ошибки
adb logcat *:E | Select-String "Meshtastic"

# Только BLE
adb logcat | Select-String "BleManager"

# Сохранить в файл
adb logcat > logcat.txt
```

#### Фильтры по тегам
```powershell
# Подключение
adb logcat | Select-String "ConnectionFragment"

# Репозиторий
adb logcat | Select-String "MeshConnectionRepository"

# Protobuf
adb logcat | Select-String "MeshProtoParser"
```

### 6.2 Проверка состояния BLE

```powershell
# Включён ли Bluetooth
adb shell settings get global bluetooth_on
# 1 = включён, 0 = выключен

# Список подключённых устройств
adb shell dumpsys bluetooth_manager | Select-String "connected"

# GATT сервисы
adb shell dumpsys bluetooth_manager | Select-String "GATT"
```

### 6.3 Проверка разрешений

```powershell
# Все разрешения приложения
adb shell dumpsys package com.example.meshtastic | Select-String "permission"

# Статус конкретного разрешения
adb shell dumpsys package com.example.meshtastic | Select-String "BLUETOOTH_CONNECT"
```

### 6.4 Очистка данных приложения

```powershell
# Очистить всё
adb shell pm clear com.example.meshtastic

# Только кэш
adb shell pm trim-caches 1000M
```

### 6.5 nRF Connect (внешний инструмент)

Установите [nRF Connect](https://www.nordicsemi.com/Products/Development-tools/nrf-connect-for-mobile) для глубокой диагностики BLE:

1. Сканирование → найдите Meshtastic
2. Подключитесь
3. Просмотрите сервисы и характеристики
4. Попробуйте read/write/notify вручную
5. Проверьте MTU, connection interval

---

## Чек-лист диагностики

### Быстрая проверка (5 минут)

- [ ] Bluetooth включён на телефоне
- [ ] Разрешения предоставлены (BLE + геолокация)
- [ ] Устройство Meshtastic включено и рядом (< 5м)
- [ ] Нет других BLE подключений к устройству
- [ ] Приложение в foreground (не в фоне)

### Полная диагностика (15 минут)

- [ ] Проверка через nRF Connect: устройство видимо
- [ ] ADB logcat: нет критических ошибок
- [ ] Serial console: устройство отвечает
- [ ] MTU: >= 23 (желательно 512)
- [ ] GATT queue: не перегружена
- [ ] Drain loop: работает
- [ ] Protobuf: парсится корректно

---

## Когда обращаться за помощью

Если после всех шагов проблема не решена:

1. Соберите информацию:
```powershell
adb logcat > full_log.txt
adb shell dumpsys bluetooth_manager > bt_dump.txt
adb bugreport > bugreport.zip
```

2. Опишите проблему:
   - Модель телефона и версия Android
   - Модель устройства Meshtastic и версия прошивки
   - Точные шаги для воспроизведения
   - Логи из шага 1

3. Создайте issue в GitHub репозитории проекта
