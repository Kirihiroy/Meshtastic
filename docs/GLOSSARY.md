# Глоссарий терминов

## A

**ADB (Android Debug Bridge)**
Инструмент командной строки для взаимодействия с Android устройствами. Используется для установки APK, просмотра логов, отладки.

**AES (Advanced Encryption Standard)**
Стандарт шифрования. Meshtastic использует AES-128 (16 байт ключ) или AES-256 (32 байта ключ).

**APK (Android Package)**
Формат установочного файла для Android приложений.

**API (Application Programming Interface)**
Набор методов и структур данных для взаимодействия с системой или библиотекой.

---

## B

**BLE (Bluetooth Low Energy)**
Энергоэффективная версия Bluetooth. Используется для связи телефона с устройством Meshtastic.

**Beacon**
BLE устройство, которое периодически передаёт сигнал для определения местоположения.

**Broadcast**
Отправка сообщения всем узлам в сети (без конкретного получателя).

**Base64**
Кодирование бинарных данных в ASCII текст. Используется для представления PSK.

---

## C

**CCCD (Client Characteristic Configuration Descriptor)**
Descriptor в BLE для включения notify/indicate на характеристике. Используется для FromNum.

**Channel (Канал)**
Группа узлов с общим PSK. PRIMARY канал определяет частоту LoRa.

**Connection Interval**
Интервал между BLE пакетами. Меньше интервал = быстрее данные, но больше расход батареи.

**ConcurrentHashMap**
Thread-safe HashMap. Используется для хранения nodeMap.

---

## D

**Drain Loop**
Процесс последовательного чтения FromRadio до получения пустого ответа. Критически важен для Meshtastic BLE.

**Descriptor**
Дополнительная метаинформация BLE характеристики. Пример: CCCD.

---

## F

**FromNum**
BLE характеристика (UUID: `ed9da18c-a800-4f66-a670-aa7547e34453`). Содержит счётчик входящих FromRadio пакетов.

**FromRadio**
BLE характеристика (UUID: `2c55e69e-4993-11ed-b878-0242ac120002`). Содержит данные от устройства к телефону в формате protobuf.

**Firmware**
Программное обеспечение, прошитое в устройство Meshtastic.

**Fragment**
Компонент Android UI, часть Activity. Примеры: StatusFragment, SettingsFragment.

---

## G

**GATT (Generic Attribute Profile)**
Протокол для структурированного доступа к BLE данным через Services и Characteristics.

**GPS (Global Positioning System)**
Спутниковая система навигации. Используется в Meshtastic для передачи координат.

**Gradle**
Система сборки для Android проектов.

---

## H

**Hop (Хоп)**
Промежуточный узел в mesh-сети. "2 hops away" = через 2 ретранслятора.

**Hex (Hexadecimal)**
Шестнадцатеричная система счисления. Используется для отображения бинарных данных.

---

## J

**JAVA_HOME**
Переменная окружения, указывающая на путь к JDK. Требуется для Gradle.

**JDK (Java Development Kit)**
Набор инструментов для разработки на Java. Версия 11 требуется для проекта.

---

## L

**LiveData**
Компонент Android Architecture Components. Наблюдаемый holder данных, lifecycle-aware.

**LoRa (Long Range)**
Технология дальнобойной радиосвязи. Работает на частотах 433/868/915 МГц.

**Logcat**
Система логирования Android. Доступна через `adb logcat`.

---

## M

**Mesh Network**
Сеть, где каждый узел может ретранслировать сообщения для других узлов.

**Meshtastic**
Открытый проект для создания mesh-сетей на базе LoRa модулей.

**MTU (Maximum Transmission Unit)**
Максимальный размер BLE пакета. По умолчанию 23 байта, можно договориться до 512.

**MVVM (Model-View-ViewModel)**
Архитектурный паттерн. Model = данные, View = UI, ViewModel = логика.

---

## N

**NodeInfo**
Информация об узле сети: ID, имя, позиция, батарея, SNR.

**Notify**
BLE механизм: сервер отправляет данные клиенту автоматически при изменении.

---

## P

**Protobuf (Protocol Buffers)**
Формат сериализации данных от Google. Используется в Meshtastic для всех сообщений.

**PSK (Pre-Shared Key)**
Ключ шифрования канала. Должен быть одинаковым на всех устройствах канала.

**PRIMARY Channel**
Основной канал, определяет частоту LoRa. Может быть только один.

**PortNum**
Номер порта в protobuf пакете. Определяет тип данных. Примеры: TEXT_MESSAGE_APP (1), ADMIN_APP (6).

---

## Q

**Queue (Очередь)**
Структура данных FIFO. GATT операции выполняются через очередь.

---

## R

**Repository Pattern**
Архитектурный паттерн: централизация логики данных в одном классе.

**RSSI (Received Signal Strength Indicator)**
Мощность принятого сигнала в dBm. Чем выше (ближе к 0), тем лучше.

**RecyclerView**
Android компонент для отображения списков. Используется в NodesFragment.

---

## S

**SNR (Signal-to-Noise Ratio)**
Отношение сигнал/шум в dB. Чем выше, тем качественнее связь.

**Service (BLE)**
Группа характеристик. Meshtastic Service UUID: `6ba1b218-15a8-461f-9fa8-5dcae273eafd`.

**SharedPreferences**
Android механизм хранения key-value данных. Используется для черновика настроек.

**Singleton**
Паттерн проектирования: только один экземпляр класса. Пример: MeshConnectionRepository.

---

## T

**ToRadio**
BLE характеристика (UUID: `f75c76d2-129e-4dad-a1dd-7866124401e7`). Используется для отправки команд на устройство.

**Thread**
Поток выполнения. BleManager использует отдельный GattThread для GATT операций.

**Toast**
Короткое всплывающее уведомление в Android.

---

## U

**UUID (Universally Unique Identifier)**
128-битный идентификатор. Используется для BLE сервисов и характеристик.

**UI (User Interface)**
Пользовательский интерфейс.

**UTF-8**
Кодировка Unicode. Используется для PSK и текстовых сообщений.

---

## V

**ViewModel**
Компонент Android Architecture Components. Хранит данные для UI, переживает пересоздание Activity.

---

## W

**Watchdog**
Механизм контроля зависания. Таймаут GATT операций = 8 секунд.

**Write (BLE)**
Запись данных в BLE характеристику. ToRadio использует WRITE_TYPE_DEFAULT (с подтверждением).

---

## Специфичные для Meshtastic

**Admin Packet**
Пакет с portnum=ADMIN_APP (6). Используется для настройки устройства.

**Channel Hash**
Хеш имени канала. Используется для вычисления channel_num.

**Config**
Конфигурация устройства (device, lora, network, power и т.д.).

**MQTT Bridge**
Мост между mesh-сетью и MQTT брокером для интернет-связности.

**Node Number (nodeNum)**
Уникальный 32-битный номер узла в сети.

**User ID**
Текстовый идентификатор узла. Формат: `!a1b2c3d4` (hex от MAC адреса).

**Long Name**
Полное имя узла (до 32 символов). Пример: "Team Leader".

**Short Name**
Краткое имя узла (до 4 символов). Пример: "TL".

**Hop Limit**
Максимальное количество ретрансляций пакета. По умолчанию 3.

**Region Code**
Код региона для LoRa частот. Примеры: EU_868, US, CN.

**Modem Preset**
Предустановка LoRa параметров. Примеры: LONG_FAST, LONG_SLOW, SHORT_FAST.

---

## Сокращения

- **API** = Application Programming Interface
- **APK** = Android Package
- **BLE** = Bluetooth Low Energy
- **dB** = decibel (децибел)
- **dBm** = decibel-milliwatts
- **GPS** = Global Positioning System
- **GATT** = Generic Attribute Profile
- **hex** = hexadecimal
- **ID** = identifier
- **MB** = megabyte
- **MQTT** = Message Queuing Telemetry Transport
- **MTU** = Maximum Transmission Unit
- **PSK** = Pre-Shared Key
- **RSSI** = Received Signal Strength Indicator
- **SNR** = Signal-to-Noise Ratio
- **UI** = User Interface
- **USB** = Universal Serial Bus
- **UUID** = Universally Unique Identifier

