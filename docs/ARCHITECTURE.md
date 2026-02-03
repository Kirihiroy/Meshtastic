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
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐│
│  │Connection│  │  Status  │  │  Nodes   │  │ Settings ││
│  │ Fragment │  │ Fragment │  │ Fragment │  │ Fragment ││
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘│
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

#### ConnectionFragment
```java
Назначение: Управление BLE подключением
Ключевые элементы:
  - scanButton: поиск устройств
  - connectButton: подключение к выбранному устройству
  - deviceListText: список найденных устройств
  - lastRxText: последние входящие байты

Observe:
  - repo.getStatusText()
  - repo.getDevices()
  - repo.getLastRx()

Actions:
  - repo.startScan()
  - repo.stopScan()
  - repo.connect()
```

#### StatusFragment
```java
Назначение: Мониторинг состояния устройства
Ключевые элементы:
  - stateText: состояние соединения
  - statusText: текстовое сообщение
  - deviceNameText, firmwareText: информация о устройстве
  - batteryText, snrText: телеметрия
  - lastRxHexText: сырые данные
  - lastSummaryText: расшифрованное FromRadio

Observe:
  - repo.getDeviceStatus()

Обновление: автоматическое через LiveData
```

#### NodesFragment
```java
Назначение: Список узлов сети
Ключевые элементы:
  - RecyclerView с NodesAdapter
  - Отображает: имя, ID, батарею, SNR, координаты

Observe:
  - repo.getNodes()

Обновление: при получении NODE_INFO от устройства
```

#### SettingsFragment
```java
Назначение: Конфигурация канала и PSK
Ключевые элементы:
  - nodeNameEdit, regionEdit: локальные настройки
  - channelNameEdit, pskEdit: настройки канала
  - saveButton: сохранить черновик
  - applyButton: применить на устройство

Actions:
  - SettingsStore.save() — локальное хранение
  - repo.applyChannelPsk() — отправка по BLE
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
Назначение: Локальный черновик настроек
Поля:
  - nodeName: String
  - region: String
  - channelName: String
  - psk: String
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
