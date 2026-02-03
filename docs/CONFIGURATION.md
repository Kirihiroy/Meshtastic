# Конфигурация сети и безопасности

## Обзор

Настройка Meshtastic устройства через BLE реализована с помощью отправки admin-пакетов по протоколу protobuf. Текущая версия поддерживает настройку **канала** и **PSK (Pre-Shared Key)**.

---

## Протокол настройки

### 1. Структура admin-пакета

```
ToRadio
  └─ packet: MeshPacket
      └─ decoded: Data
          ├─ portnum: ADMIN_APP (6)
          └─ payload: <admin command>
```

### 2. Применение канала и PSK

```protobuf
// Структура настройки канала
message Channel {
  int32 index = 1;              // 0 = PRIMARY channel
  ChannelSettings settings = 2;
  Role role = 3;                // PRIMARY, SECONDARY, DISABLED
}

message ChannelSettings {
  bytes psk = 2;                // 0, 16 или 32 байта
  string name = 3;              // до 11 символов
  fixed32 id = 4;               // channel ID (опционально)
  bool uplink_enabled = 5;
  bool downlink_enabled = 6;
}
```

### 3. Код применения (из SettingsFragment)

```java
public boolean applyChannelPsk(String channelName, String pskText) {
    // 1. Проверка состояния
    if (state.getValue() != State.CONNECTED) return false;
    
    // 2. Преобразование PSK в байты
    byte[] pskBytes = pskText.trim().getBytes(StandardCharsets.UTF_8);
    
    // 3. Построение ChannelSettings
    ChannelProtos.ChannelSettings settings = 
        ChannelProtos.ChannelSettings.newBuilder()
            .setName(channelName.trim())
            .setPsk(ByteString.copyFrom(pskBytes))
            .build();
    
    // 4. Построение Channel (PRIMARY, index=0)
    ChannelProtos.Channel channel = 
        ChannelProtos.Channel.newBuilder()
            .setIndex(0)
            .setRole(ChannelProtos.Channel.Role.PRIMARY)
            .setSettings(settings)
            .build();
    
    // 5. Упаковка в FromRadio (да, это правильно!)
    MeshProtos.FromRadio out = 
        MeshProtos.FromRadio.newBuilder()
            .setChannel(channel)
            .build();
    
    // 6. Упаковка в MeshPacket
    MeshProtos.MeshPacket packet = 
        MeshProtos.MeshPacket.newBuilder()
            .setDecoded(MeshProtos.Data.newBuilder()
                .setPortnum(Portnums.PortNum.ADMIN_APP)
                .setPayload(out.toByteString())
                .build())
            .build();
    
    // 7. Упаковка в ToRadio
    MeshProtos.ToRadio msg = 
        MeshProtos.ToRadio.newBuilder()
            .setPacket(packet)
            .build();
    
    // 8. Отправка по BLE
    return sendToRadio(msg);
}
```

---

## PSK (Pre-Shared Key)

### Формат PSK

Meshtastic поддерживает три длины PSK:

| Длина | Тип шифрования | Описание |
|-------|----------------|----------|
| 0 байт | Без шифрования | Открытый канал (не рекомендуется) |
| 16 байт | AES-128 | Быстрое, умеренная безопасность |
| 32 байта | AES-256 | Медленнее, высокая безопасность |

### Специальные значения PSK

Meshtastic имеет встроенные PSK для совместимости:

```java
// 1 байт = специальный код
0x00 = без шифрования
0x01 = "default" key: {0xd4, 0xf1, 0xbb, 0x3a, 0x20, 0x29, 0x07, 0x59, 
                       0xf0, 0xbc, 0xff, 0xab, 0xcf, 0x4e, 0x69, 0x01}
0x02-0x0A = "default" key с инкрементом последнего байта на (n-1)
```

### Генерация безопасного PSK

#### Вариант 1: ASCII строка (16 символов)
```java
String psk = "MySecretKey12345";  // ровно 16 символов ASCII
byte[] pskBytes = psk.getBytes(StandardCharsets.US_ASCII);
// Длина: 16 байт
```

#### Вариант 2: ASCII строка (32 символа)
```java
String psk = "ThisIsA32CharacterSecurePassKey!";  // 32 символа
byte[] pskBytes = psk.getBytes(StandardCharsets.US_ASCII);
// Длина: 32 байта
```

#### Вариант 3: Hex-строка
```java
// 16 байт = 32 hex символа
String hexPsk = "0123456789ABCDEF0123456789ABCDEF";
byte[] pskBytes = new byte[16];
for (int i = 0; i < 16; i++) {
    pskBytes[i] = (byte) Integer.parseInt(
        hexPsk.substring(i * 2, i * 2 + 2), 16
    );
}
```

#### Вариант 4: Base64
```java
import java.util.Base64;

String base64Psk = "SGVsbG9Xb3JsZDEyMzQ1Ng==";  // 16 байт после декодирования
byte[] pskBytes = Base64.getDecoder().decode(base64Psk);
```

#### Вариант 5: Криптографически случайный
```java
import java.security.SecureRandom;

SecureRandom random = new SecureRandom();
byte[] pskBytes = new byte[32];  // AES-256
random.nextBytes(pskBytes);

// Сохранить в hex или base64 для последующего использования
String hexPsk = bytesToHex(pskBytes);
```

### Рекомендации по безопасности PSK

#### ✓ Хорошие практики
- Используйте минимум 16 символов
- Комбинируйте буквы (A-Z, a-z), цифры (0-9), символы (!@#$%)
- Используйте генераторы паролей
- Меняйте PSK периодически (раз в месяц/квартал)
- Не используйте словарные слова
- Храните PSK в защищённом хранилище (password manager)

#### ✗ Плохие практики
- "password", "1234", "test", "qwerty"
- Имена, даты рождения
- Повторяющиеся символы ("aaaa1111")
- PSK длиной < 8 символов
- Передача PSK через незащищённые каналы (SMS, email без шифрования)

### Проверка длины PSK

```java
public static boolean isValidPsk(String pskText) {
    if (pskText == null || pskText.isEmpty()) {
        return false;  // пустой PSK = без шифрования
    }
    
    byte[] bytes = pskText.getBytes(StandardCharsets.UTF_8);
    int len = bytes.length;
    
    // Только 0, 16 или 32 байта
    return len == 0 || len == 16 || len == 32;
}
```

---

## Настройка канала

### Имя канала

```java
Правила:
  - Максимум 11 символов
  - Только ASCII (a-z, A-Z, 0-9, -, _)
  - Без пробелов
  
Примеры:
  ✓ "TEAM-A"
  ✓ "Rescue_1"
  ✓ "BASE"
  ✗ "My Team" (пробел)
  ✗ "Очень длинное имя" (> 11 символов, кириллица)
```

### Channel ID

Channel ID генерируется автоматически на основе имени:
```
hash(channel_name) % NUM_CHANNELS
```

Можно указать вручную:
```java
ChannelSettings.Builder settings = ChannelSettings.newBuilder()
    .setName("TEAM")
    .setId(0x12345678);  // фиксированный ID
```

### Роли каналов

```java
DISABLED  → канал отключён
PRIMARY   → основной канал (определяет частоту)
SECONDARY → дополнительный (только для шифрования/дешифрования)
```

**Важно:** В устройстве может быть только **один PRIMARY** канал.

---

## Черновик настроек (SettingsStore)

### Реализация локального хранения

```java
public class SettingsStore {
    private static final String PREFS_NAME = "settings_draft";
    private final SharedPreferences prefs;
    
    public SettingsStore(Context context) {
        prefs = context.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public SettingsDraft load() {
        SettingsDraft draft = new SettingsDraft();
        draft.setNodeName(prefs.getString("node_name", ""));
        draft.setRegion(prefs.getString("region", ""));
        draft.setChannelName(prefs.getString("channel_name", ""));
        draft.setPsk(prefs.getString("psk", ""));
        return draft;
    }
    
    public void save(SettingsDraft draft) {
        prefs.edit()
            .putString("node_name", draft.getNodeName())
            .putString("region", draft.getRegion())
            .putString("channel_name", draft.getChannelName())
            .putString("psk", draft.getPsk())
            .apply();
    }
}
```

### Использование

```java
// В SettingsFragment
SettingsStore store = new SettingsStore(requireContext());

// Загрузка при открытии экрана
SettingsDraft draft = store.load();
fillFields(draft);

// Сохранение при нажатии "Сохранить"
draft = collectFieldsFromUI();
store.save(draft);
```

---

## Отладка настроек

### Проверка отправки

```java
// Добавьте логирование в MeshConnectionRepository
public boolean applyChannelPsk(String channelName, String pskText) {
    Log.d(TAG, "Applying channel: " + channelName + ", PSK length: " 
              + pskText.getBytes(StandardCharsets.UTF_8).length);
    
    // ... построение пакета
    
    boolean result = sendToRadio(msg);
    Log.d(TAG, "Send result: " + result);
    return result;
}
```

### Проверка приёма на устройстве

Подключитесь через serial console (USB):
```bash
screen /dev/ttyUSB0 115200

# Должно вывести:
INFO | Received admin packet: CHANNEL
INFO | Setting channel 0: name=TEAM
INFO | PSK length: 16
```

### Проверка через nRF Connect

1. Установите [nRF Connect](https://www.nordicsemi.com/Products/Development-tools/nrf-connect-for-mobile)
2. Подключитесь к устройству Meshtastic
3. Найдите ToRadio характеристику
4. Запишите hex-данные пакета
5. Проверьте, что устройство ответило

---

## Примеры конфигураций

### Конфигурация 1: Открытый канал (тестирование)
```java
channelName = "PUBLIC"
psk = ""  // пустой = без шифрования
```

### Конфигурация 2: Базовая безопасность
```java
channelName = "TEAM-A"
psk = "SecureKey1234567"  // 16 символов
```

### Конфигурация 3: Высокая безопасность
```java
channelName = "OPSEC"
psk = "aB3$kL9@mN7*qP2!rT5#wX8&yZ1^"  // 32 символа
```

### Конфигурация 4: Группа с подгруппами
```java
// PRIMARY канал (index=0)
channelName = "MAIN"
psk = "MainChannelKey16"

// SECONDARY канал (index=1, настраивается отдельно)
channelName = "ALPHA"
psk = "AlphaSubgroupK16"
```

---

## Расширенные настройки (будущие версии)

### Настройка региона (LoRa)

```java
// Пример: отправка LoraConfig
ConfigProtos.Config.LoRaConfig loraConfig = 
    ConfigProtos.Config.LoRaConfig.newBuilder()
        .setRegion(ConfigProtos.Config.LoRaConfig.RegionCode.EU_868)
        .setModemPreset(ConfigProtos.Config.LoRaConfig.ModemPreset.LONG_FAST)
        .setHopLimit(3)
        .build();

ConfigProtos.Config config = 
    ConfigProtos.Config.newBuilder()
        .setLora(loraConfig)
        .build();

// Упаковать в MeshPacket с ADMIN_APP и отправить
```

### Настройка имени узла (User)

```java
MeshProtos.User user = MeshProtos.User.newBuilder()
    .setLongName("Team Leader")
    .setShortName("TL")
    .build();

// Отправить как admin-команду
```

### Настройка мощности передатчика

```java
ConfigProtos.Config.LoRaConfig loraConfig = 
    ConfigProtos.Config.LoRaConfig.newBuilder()
        .setTxPower(20)  // dBm (максимум зависит от региона)
        .build();
```

---

## Безопасность и лучшие практики

### Управление PSK

1. **Никогда не hardcode PSK в коде**
   ```java
   // ✗ ПЛОХО
   final String PSK = "MyPassword123";
   
   // ✓ ХОРОШО
   String psk = settingsStore.load().getPsk();
   ```

2. **Используйте KeyStore для хранения**
   ```java
   // Android KeyStore для надёжного хранения
   KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
   // ... шифруйте PSK перед сохранением в SharedPreferences
   ```

3. **Проверяйте ввод пользователя**
   ```java
   if (!isValidPsk(psk)) {
       showError("PSK должен быть 16 или 32 символа");
       return;
   }
   ```

### Ротация PSK

Меняйте PSK регулярно:
- Раз в месяц для критичных сетей
- Раз в квартал для обычных сетей
- Немедленно при подозрении на компрометацию

### Передача PSK новым участникам

1. Лично (самый безопасный)
2. Через зашифрованный канал (Signal, WhatsApp)
3. QR-код (для быстрого обмена на месте)

---

## Troubleshooting

### Настройки не применяются

**Симптом:** После "Применить" ничего не происходит

**Диагностика:**
1. Проверьте состояние: должно быть `CONNECTED`
2. Откройте вкладку "Статус" → проверьте "Последние входящие байты"
3. Проверьте ADB logcat:
```powershell
adb logcat | Select-String "applyChannelPsk"
```

**Решения:**
- Перезагрузите устройство Meshtastic
- Повторите применение
- Проверьте длину PSK (должна быть 0, 16 или 32 байта)

### Устройство перестало отвечать после применения

**Симптом:** После смены PSK связь потеряна

**Причина:** Устройство переключилось на новый PSK

**Решение:**
1. Перезагрузите устройство Meshtastic
2. Переподключитесь по BLE
3. Убедитесь, что PSK совпадает на всех устройствах

### Ошибка "Channel name too long"

**Причина:** Имя канала > 11 символов

**Решение:** Сократите имя канала

```java
String channelName = "VeryLongChannelName";  // ✗ 19 символов
String channelName = "LongChan";             // ✓ 8 символов
```

---

## Часто задаваемые вопросы

**Q: Можно ли использовать emoji в PSK?**  
A: Технически да, но не рекомендуется. Используйте ASCII для совместимости.

**Q: Как узнать текущий PSK устройства?**  
A: PSK не передаётся обратно по соображениям безопасности. Храните его локально.

**Q: Сколько каналов поддерживается?**  
A: До 8 каналов (0-7), но только один PRIMARY.

**Q: Можно ли применить настройки массово на несколько устройств?**  
A: Да, подключайтесь к каждому по очереди и применяйте один и тот же черновик.

**Q: Что делать, если забыл PSK?**  
A: Необходим factory reset устройства Meshtastic.
