# Архитектура приложения

## Обзор

Приложение построено на архитектуре **MVVM (Model-View-ViewModel)** с использованием **LiveData** для реактивного обновления UI и **Repository Pattern** для централизации логики данных.

### Ключевые принципы

- **Разделение ответственности**: UI, бизнес-логика и данные разделены
- **Единственный источник истины**: `MeshConnectionRepository` — центральная точка состояния
- **Реактивность**: LiveData автоматически обновляет UI при изменении данных
- **Очередь операций**: GATT операции выполняются последовательно

---

## Архитектурные слои

```
┌─────────────────────────────────────────────────────────┐
│                    UI Layer (View)                       │
│  ┌──────────┐ ┌──────┐ ┌──────┐ ┌──────────┐ ┌────────┐│
│  │Connection│ │Nodes │ │ Chat │ │ E2eDm    │ │Settings││
│  │ Fragment │ │ Frag │ │ Frag │ │ Fragment │ │ Frag   ││
│  └──────────┘ └──────┘ └──────┘ └──────────┘ └────────┘│
│       + 10 sub-fragments под Settings (LoRa, Channels,  │
│       Security, User, Device, Location, Power, Network, │
│       Display, Bluetooth)                                │
└─────────────────────────────────────────────────────────┘
              ↕ LiveData observation
┌─────────────────────────────────────────────────────────┐
│              Repository Layer (ViewModel)                │
│         ┌──────────────────────────────────┐            │
│         │  MeshConnectionRepository        │            │
│         │  - state: LiveData<State>        │            │
│         │  - nodes: LiveData<List>         │            │
│         │  - deviceStatus: LiveData        │            │
│         └──────────────────────────────────┘            │
└─────────────────────────────────────────────────────────┘
              ↕ BLE callbacks
┌─────────────────────────────────────────────────────────┐
│                Bluetooth Layer                           │
│         ┌──────────────────────────────────┐            │
│         │         BleManager               │            │
│         │  - scan, connect, disconnect     │            │
│         │  - GATT operations queue         │            │
│         │  - FromRadio drain loop          │            │
│         └──────────────────────────────────┘            │
└─────────────────────────────────────────────────────────┘
              ↕ GATT protocol
┌─────────────────────────────────────────────────────────┐
│              Hardware (Meshtastic Device)                │
│         ToRadio ← [write] ← [read] → FromRadio          │
└─────────────────────────────────────────────────────────┘
```

---

## Слой 1: UI (View)

### Фрагменты

#### ConnectionFragment (раздел «Соединение»)
```java
Назначение: Выбор транспорта и подключение к устройству Meshtastic
Дизайн: повторяет оригинальный Meshtastic-Android

Ключевые элементы:
  - connBigIcon + connBigText: большая карточка статуса устройства
  - connTabBt/Net/Com: segment-control транспортов (только BT функционален)
  - connDevicesRecycler: список «Сопряжённые устройства» с RadioButton
  - connActionButton: state-aware кнопка (Сканирование/Остановить/
                      Подключиться/Подключение…/Отключиться)
  - connStatusChip: чип статуса внизу

Observe:
  - repo.getState()
  - repo.getDevices()

Actions:
  - repo.startScan() / stopScan()
  - repo.selectDevice(d) / connect() / disconnect()
```

> Старый `StatusFragment` удалён. Его метрики перенесены в баннер на
> экране «Ноды».

#### NodesFragment (раздел «Ноды»)
```java
Назначение: Список узлов сети + встроенный статус-баннер устройства

Ключевые элементы:
  - nodes_header + nodes_counter: «Ноды (онлайн X / показано Y / всего Z)»
  - nodes_status_banner: цветная точка + статус + RX-время + строка метрик
                        (клик = repo.requestConfigManual())
  - nodes_filter + nodes_sort: поле фильтра и кнопка сортировки
  - nodes_recycler: ListAdapter с DiffUtil

Карточка ноды (item_node.xml):
  - бейдж short_name (HSV-цвет по nodeNum)
  - замок PSK, имя, last-seen, иконка MQTT-off
  - метрики PWR/Batt/ChUtil/AirUtil/Altitude
  - HW-model, role, !node-id

Observe:
  - repo.getNodes()
  - repo.getDeviceStatus()  ← для баннера
```

#### ChatFragment (раздел «Чат»)
```java
Назначение: Общий канал mesh-сети
Дизайн: пузыри с группировкой по 5 минут, аватары, статус доставки

Ключевые элементы:
  - tvChatSubtitle: «Канал: LongFast · участников: N»
  - rvMessages: 3 view-type (sent / received / date_header)
  - btnNewMessages: FAB «↓ N новых» при прокрутке вверх
  - btnAttach: иконка скрепки (заглушка)
  - btnSend: круглая зелёная кнопка

Группировка:
  - GROUP_WINDOW_MS = 5 * 60 * 1000
  - findLastVisibleItemPosition для определения «у низа ли»
  - unreadCount накапливается при апдейте если был не у низа

Observe:
  - repo.getMessages()
  - repo.getNodes() (для resolve имени отправителя)
  - repo.getState()
```

#### E2eDmFragment (раздел «E2E ЛС»)
```java
Назначение: Личные сообщения с ECDH+AES-GCM шифрованием

Ключевые элементы:
  - tvMyPubkey: свой публичный ключ (read-only, копируется)
  - etRecipientNode: ID получателя (!aabbccdd или число)
  - etRecipientPubkey: публичный ключ получателя (hex)
  - etE2eMessage: ввод текста
  - btnE2eSend: отправка через repo.sendE2eMessage()
  - rvE2eMessages: лента (использует тот же ChatAdapter)

Observe:
  - repo.getE2eMessages()
  - repo.getState()
```

#### SettingsFragment (раздел «Настройки»)
```java
Назначение: Меню из 10 разделов конфигурации

Ключевые элементы:
  - 10 строк-«entry»: иконка + подпись + шеврон
  - Каждая открывает sub-фрагмент через
    FragmentTransaction.replace(R.id.fragment_container, X)
       .addToBackStack(null)

Sub-фрагменты:
  LoRaSettingsFragment, ChannelsSettingsFragment + ChannelEditFragment,
  SecuritySettingsFragment, UserSettingsFragment, DeviceSettingsFragment,
  LocationSettingsFragment, PowerSettingsFragment,
  NetworkSettingsFragment, DisplaySettingsFragment,
  BluetoothSettingsFragment

Persistence:
  - SettingsStore + SettingsDraft
  - Раздельные секции loadWithX / saveX — каждый sub-экран пишет только
    свою подсекцию, чтобы не затирать чужие черновики
```

---

## Слой 2: Repository (ViewModel)

### MeshConnectionRepository

**Паттерн:** Singleton  
**Жизненный цикл:** На весь процесс приложения

#### Состояния (State enum)
```java
DISCONNECTED → начальное состояние
SCANNING     → идёт BLE сканирование
CONNECTING   → установка GATT соединения
CONNECTED    → соединение активно
ERROR        → ошибка соединения
```

#### Ключевые LiveData

```java
// Состояние соединения
MutableLiveData<State> state

// Текстовый статус для UI
MutableLiveData<String> statusText

// Список найденных BLE устройств
MutableLiveData<List<BluetoothDevice>> devices

// Выбранное устройство
MutableLiveData<BluetoothDevice> selectedDevice

// Последние входящие байты (сырые)
MutableLiveData<byte[]> lastRx

// Расшифрованное FromRadio
MutableLiveData<String> lastFromRadioSummary

// Список узлов сети
MutableLiveData<List<NodeInfo>> nodes

// Агрегированный статус устройства
MutableLiveData<DeviceStatus> deviceStatus
```

#### Основные методы

```java
// Управление BLE
void startScan()
void stopScan()
void selectDevice(BluetoothDevice device)
void connect()
void disconnect()

// Отправка данных
boolean write(byte[] data)
boolean sendToRadio(MeshProtos.ToRadio msg)
boolean applyChannelPsk(String channelName, String pskText)

// Приватные обработчики
void handleFromRadio(byte[] data)
void updateDeviceStatus(Consumer<DeviceStatus> updater)
NodeInfo convertNode(MeshProtos.NodeInfo ni)
```

#### Поток данных

```
Вход (от BleManager):
  byte[] → handleFromRadio()
         → MeshProtos.FromRadio.parseFrom()
         → switch(payloadVariant)
            ├─ MY_INFO → обновить nodeNum
            ├─ NODE_INFO → добавить в nodeMap
            ├─ METADATA → обновить firmware version
            └─ другие → игнорировать

Выход (в UI):
  LiveData.postValue() → автоматическое обновление фрагментов
```

---

## Слой 3: Bluetooth

### BleManager

**Паттерн:** Stateful Manager  
**Потоки:** MainThread + GattThread

#### GATT Thread

Все GATT операции выполняются в отдельном потоке для предотвращения блокировки UI:

```java
HandlerThread gattThread = new HandlerThread("MeshtasticBleGatt")
Handler gattHandler = new Handler(gattThread.getLooper())

// Пример операции
gattHandler.post(() -> {
    gatt.writeCharacteristic(toRadio);
});
```

#### Очередь операций

GATT API позволяет только **одну операцию в моменте времени**. Поэтому используется очередь:

```java
ArrayDeque<GattOp> opQueue
GattOp inFlight  // текущая операция

Алгоритм:
  1. Добавить операцию в очередь
  2. Если inFlight == null, взять из очереди
  3. Выполнить операцию (write/read/writeDesc)
  4. Дождаться callback
  5. inFlight = null
  6. Обработать следующую операцию
```

#### Типы операций (GattOp)
```java
WRITE_CHAR  → запись в ToRadio
READ_CHAR   → чтение FromRadio
WRITE_DESC  → включение notify на FromNum
```

#### Таймаут операций

```java
final long OP_TIMEOUT_MS = 8000;

mainHandler.postDelayed(opTimeoutRunnable, OP_TIMEOUT_MS);

// При завершении операции:
mainHandler.removeCallbacks(opTimeoutRunnable);
```

### Протокол Meshtastic BLE

#### UUID сервиса и характеристик
```
Service:   6ba1b218-15a8-461f-9fa8-5dcae273eafd
ToRadio:   f75c76d2-129e-4dad-a1dd-7866124401e7 (write)
FromNum:   ed9da18c-a800-4f66-a670-aa7547e34453 (notify)
FromRadio: 2c55e69e-4993-11ed-b878-0242ac120002 (read)
```

#### Жизненный цикл соединения

```
1. Scan
   startScan() → scanner.startScan(filters, settings, callback)
   
2. Connect
   device.connectGatt() → onConnectionStateChange(CONNECTED)
   
3. Request MTU
   gatt.requestMtu(512) → onMtuChanged(mtu, status)
   
4. Discover Services
   gatt.discoverServices() → onServicesDiscovered()
   
5. Enable FromNum Notifications
   gatt.setCharacteristicNotification(fromNum, true)
   cccd.setValue(ENABLE_NOTIFICATION_VALUE)
   gatt.writeDescriptor(cccd) → onDescriptorWrite()
   
6. Drain FromRadio
   while(true) {
     gatt.readCharacteristic(fromRadio) → onCharacteristicRead()
     if (data.length == 0) break;
     bytesListener.onBytes(data);
   }
   
7. Poll FromNum (fallback)
   Каждые 1.5 секунды читать FromNum
   Если значение изменилось → drainFromRadio()
```

#### Drain Loop (критически важно!)

Meshtastic буферизует FromRadio пакеты. Клиент **обязан** читать до получения пустого ответа:

```java
private void drainFromRadio() {
    if (drainingFromRadio) return;
    drainingFromRadio = true;
    enqueueRead(fromRadioChar);
}

private void handleFromRadioValue(byte[] value) {
    if (value == null || value.length == 0) {
        drainingFromRadio = false;
        return;
    }
    
    bytesListener.onBytes(value);
    enqueueRead(fromRadioChar);  // продолжить drain
}
```

---

## Слой 4: Data Models

### DeviceStatus
```java
Назначение: Агрегированное состояние устройства для UI
Поля:
  - state: String
  - statusText: String
  - deviceName: String
  - nodeNum: Long
  - firmwareVersion: String
  - batteryPercent: Integer
  - snr: Float
  - lastHeard: Long (epoch seconds)
  - lastRxAt: Long (timestamp)
  - lastSummary: String
  - lastRxHex: String
```

### NodeInfo
```java
Назначение: Информация об узле сети
Поля:
  - nodeNum: long
  - userId: String
  - longName: String
  - shortName: String
  - latitude: double
  - longitude: double
  - snr: float
  - batteryLevel: int
  - lastHeard: long
  - viaMqtt: boolean
  - hopsAway: Integer
  - channel: Integer
```

### SettingsDraft
```java
Назначение: Локальный черновик настроек ВСЕХ разделов меню
Поля:
  // User (длинное имя = nodeName)
  - nodeName, userShortName, userRole, userIsLicensed

  // LoRa
  - region, loraModemPreset, loraHopLimit, loraTxPower,
    loraTxEnabled, loraIgnoreMqtt

  // Channels: массив на 8 слотов
  - ChannelDraft[] channels  (index, name, psk, role)

  // Security
  - String[] adminKeys (3 слота), isManaged

  // Device
  - deviceRebroadcastMode, deviceNodeInfoBroadcastSecs,
    deviceSerialEnabled, deviceDebugLogEnabled,
    deviceLedHeartbeatDisabled

  // Position
  - positionGpsMode, positionBroadcastSecs,
    positionFixedEnabled, positionSmartEnabled

  // Power
  - powerIsSaving, powerShutdownAfterSecs,
    powerWaitBluetoothSecs, powerMinWakeSecs

  // Network
  - networkWifiEnabled, networkWifiSsid, networkWifiPsk,
    networkNtpServer, networkEthEnabled

  // Display
  - displayScreenOnSecs, displayUnits, displayFlipScreen,
    displayUse12hClock, displayHeadingBold

  // Bluetooth
  - bluetoothEnabled, bluetoothMode, bluetoothFixedPin

Старые getChannelName()/getPsk() делегируют на channels[0]
для backward compat с MeshConnectionRepository.applyChannelPsk().
```

### Message
```java
Назначение: Текстовое сообщение чата (общий или E2E)
Поля:
  - id, text, senderId, timestamp, isOwnMessage
  - decryptFailed: true для E2E пакетов которые не расшифровались
  - hopsAway: int (-1 = неизвестно), число ретрансляций
  - deliveryStatus: enum DeliveryStatus
                    {SENDING, SENT, DELIVERED, FAILED}
```

---

## Слой 5: Protobuf Parsing

### MeshProtoParser

```java
public static String parseFromRadioSummary(byte[] data) {
    MeshProtos.FromRadio msg = MeshProtos.FromRadio.parseFrom(data);
    
    switch (msg.getPayloadVariantCase()) {
        case MY_INFO:
            return "FromRadio id=" + msg.getId() + " MY_INFO";
        
        case NODE_INFO:
            return "FromRadio id=" + msg.getId() + " NODE_INFO";
        
        case CONFIG:
            return "FromRadio id=" + msg.getId() + " CONFIG update";
        
        case CHANNEL:
            return "FromRadio id=" + msg.getId() + " CHANNEL info";
        
        case PACKET:
            PortNum port = msg.getPacket().getDecoded().getPortnum();
            return "FromRadio id=" + msg.getId() + " PACKET on port " + port.name();
        
        case METADATA:
            return "FromRadio id=" + msg.getId() + " METADATA: " 
                   + msg.getMetadata().getFirmwareVersion();
        
        default:
            return "FromRadio id=" + msg.getId() + " (" 
                   + msg.getPayloadVariantCase().name() + ")";
    }
}
```

---

## Потоки данных

### 1. BLE → UI (входящие данные)

```
Meshtastic Device
    ↓ BLE notify/read
[BleManager.onCharacteristicRead]
    ↓ bytesListener.onBytes()
[MeshConnectionRepository.handleFromRadio]
    ↓ parseFromRadio()
    ├─ lastRx.postValue(data)
    ├─ lastFromRadioSummary.postValue(summary)
    └─ switch(payloadVariant)
        ├─ NODE_INFO → nodes.postValue()
        ├─ MY_INFO → deviceStatus.postValue()
        └─ METADATA → deviceStatus.postValue()
    ↓ LiveData propagation
[Fragment.observe]
    ↓ UI update
TextView.setText()
```

### 2. UI → BLE (исходящие команды)

```
[SettingsFragment.applyToDevice]
    ↓ repo.applyChannelPsk(name, psk)
[MeshConnectionRepository]
    ↓ build Channel protobuf
    ↓ build MeshPacket (portnum=ADMIN_APP)
    ↓ build ToRadio
    ↓ sendToRadio(msg)
    ↓ bleManager.write(bytes)
[BleManager]
    ↓ enqueueWrite(toRadioChar, bytes)
    ↓ processNextOp()
    ↓ gatt.writeCharacteristic()
    ↓ onCharacteristicWrite callback
    ↓ finishOp()
Meshtastic Device
```

---

## Многопоточность

### Потоки в приложении

```
MainThread (UI Thread)
  - Все LiveData обновления
  - onClick listeners
  - UI отрисовка

GattThread (BleManager)
  - GATT операции (write/read)
  - Очередь операций
  - Callbacks от Android BLE API

Background Thread (Room, если используется)
  - Запросы к БД
  - I/O операции
```

### Синхронизация

```java
// Repository: thread-safe через LiveData.postValue()
deviceStatus.postValue(newStatus);  // безопасно из любого потока

// NodeMap: concurrent access
ConcurrentHashMap<Long, NodeInfo> nodeMap;

// GATT queue: защищена через Handler
gattHandler.post(() -> { /* операция */ });
```

---

## Управление памятью

### Жизненный цикл объектов

```
MeshConnectionRepository
  Scope: Application (Singleton)
  Cleanup: при завершении процесса

BleManager
  Scope: Application (создается в Repository)
  Cleanup: disconnect() при выходе

Fragment
  Scope: UI lifecycle
  Cleanup: onDestroyView() → unobserve LiveData

LiveData
  Scope: Repository
  Cleanup: автоматически при отсутствии observers
```

### Предотвращение утечек

```java
// Fragment observe с lifecycleOwner
repo.getDeviceStatus().observe(getViewLifecycleOwner(), this::renderStatus);
// ✓ автоматически unsubscribe при onDestroyView

// НЕ ДЕЛАТЬ:
repo.getDeviceStatus().observeForever(observer);
// ✗ требует ручного removeObserver
```

---

## Расширяемость архитектуры

### Добавление нового экрана

1. Создать `NewFragment.java` в `ui/`
2. Добавить в `activity_main.xml` элемент навигации
3. Subscribe на нужные LiveData из Repository
4. (Опционально) Добавить новые LiveData в Repository

### Добавление нового типа данных

1. Создать модель в `data/model/`
2. Добавить LiveData в `MeshConnectionRepository`
3. Обработать в `handleFromRadio()` соответствующий payload variant
4. Обновить LiveData через `postValue()`

### Добавление новой команды

1. Создать protobuf сообщение (ToRadio)
2. Добавить метод в `MeshConnectionRepository`:
```java
public boolean sendCustomCommand(params) {
    MeshProtos.ToRadio msg = MeshProtos.ToRadio.newBuilder()
        .set...()
        .build();
    return sendToRadio(msg);
}
```
3. Вызвать из UI: `repo.sendCustomCommand()`

---

## Диаграмма последовательности (подключение)

```
User              Fragment       Repository      BleManager      Device
 │                   │               │               │             │
 │ tap "Connect"     │               │               │             │
 ├──────────────────>│               │               │             │
 │                   │ connect()     │               │             │
 │                   ├──────────────>│               │             │
 │                   │               │ connect()     │             │
 │                   │               ├──────────────>│             │
 │                   │               │               │ connectGatt │
 │                   │               │               ├────────────>│
 │                   │               │               │<────────────┤
 │                   │               │               │ CONNECTED   │
 │                   │               │ onConnected() │             │
 │                   │               │<──────────────┤             │
 │                   │ state=CONN    │               │             │
 │                   │<──────────────┤               │             │
 │ UI update         │               │               │             │
 │<──────────────────┤               │               │             │
 │                   │               │               │ requestMtu  │
 │                   │               │               ├────────────>│
 │                   │               │               │<────────────┤
 │                   │               │               │ onMtuChanged│
 │                   │               │               │ discoverSvc │
 │                   │               │               ├────────────>│
 │                   │               │               │<────────────┤
 │                   │               │               │ onSvcDisc   │
 │                   │               │ enableNotify  │             │
 │                   │               │<──────────────┤             │
 │                   │               │               │ writeDesc   │
 │                   │               │               ├────────────>│
 │                   │               │               │<────────────┤
 │                   │               │               │ onDescWrite │
 │                   │               │ drainFromRadio│             │
 │                   │               │<──────────────┤             │
 │                   │               │               │ readChar    │
 │                   │               │               ├────────────>│
 │                   │               │               │<────────────┤
 │                   │               │               │ onCharRead  │
 │                   │               │ onBytes()     │             │
 │                   │               │<──────────────┤             │
 │                   │ lastRx update │               │             │
 │                   │<──────────────┤               │             │
 │ UI update         │               │               │             │
 │<──────────────────┤               │               │             │
```

---

## Оптимизации и best practices

### 1. GATT очередь
- Всегда используйте очередь для GATT операций
- Один таймаут на операцию (8 секунд)
- Если операция зависла — очистить и продолжить

### 2. LiveData
- Используйте `postValue()` из фоновых потоков
- Используйте `setValue()` только из MainThread
- Всегда observe с `viewLifecycleOwner` во фрагментах

### 3. BLE drain
- Обязательно читайте FromRadio до пустого ответа
- Используйте poll fallback (1.5 сек) на случай пропуска notify

### 4. Память
- Не храните большие byte[] в LiveData долго
- Используйте `ConcurrentHashMap` для nodeMap
- Очищайте старые данные при disconnect

### 5. Ошибки
- Всегда оборачивайте protobuf parse в try-catch
- Логируйте все BLE callbacks для отладки
- Показывайте понятные сообщения пользователю
