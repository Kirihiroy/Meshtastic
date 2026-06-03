package com.example.meshtastic.data.model;

/**
 * Локальный черновик настроек, сохраняется до применения на устройство.
 */
public class SettingsDraft {
    public static final int MAX_CHANNELS = 8;

    private String nodeName;
    private String region;             // LoRa: имя enum (UNSET, US, EU_868, ...)
    // channelName/psk теперь живут в channels[0] (primary), геттеры/сеттеры
    // ниже делегируют туда — это сохраняет совместимость с MeshConnectionRepository.applyChannelPsk().

    // LoRa-параметры (черновик; применение на устройство — отдельным PR)
    private String loraModemPreset = "LONG_FAST";
    private int loraHopLimit = 3;       // 1..7
    private int loraTxPower = 20;       // 0..30 dBm
    private boolean loraTxEnabled = true;
    private boolean loraIgnoreMqtt = false;

    // 8 слотов каналов: индекс 0 — PRIMARY, 1..7 — SECONDARY/DISABLED
    private final ChannelDraft[] channels = new ChannelDraft[MAX_CHANNELS];

    {
        for (int i = 0; i < MAX_CHANNELS; i++) channels[i] = new ChannelDraft(i);
    }

    // Безопасность (раздел «Безопасность»)
    public static final int MAX_ADMIN_KEYS = 3;
    private final String[] adminKeys = new String[MAX_ADMIN_KEYS];

    {
        for (int i = 0; i < MAX_ADMIN_KEYS; i++) adminKeys[i] = "";
    }

    private boolean isManaged = false;

    // Пользователь (раздел «Пользователь»)
    // longName = nodeName (используем существующее поле, в Meshtastic это user.long_name)
    private String userShortName = "";   // до 4 символов, user.short_name
    private String userRole = "CLIENT";  // user.role (CLIENT, ROUTER, REPEATER, ...)
    private boolean userIsLicensed = false; // user.is_licensed (HAM)

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getChannelName() {
        return channels[0].getName();
    }

    public void setChannelName(String channelName) {
        channels[0].setName(channelName);
    }

    public String getPsk() {
        return channels[0].getPsk();
    }

    public void setPsk(String psk) {
        channels[0].setPsk(psk);
    }

    public String getLoraModemPreset() { return loraModemPreset; }
    public void setLoraModemPreset(String v) { this.loraModemPreset = v; }

    public int getLoraHopLimit() { return loraHopLimit; }
    public void setLoraHopLimit(int v) { this.loraHopLimit = v; }

    public int getLoraTxPower() { return loraTxPower; }
    public void setLoraTxPower(int v) { this.loraTxPower = v; }

    public boolean isLoraTxEnabled() { return loraTxEnabled; }
    public void setLoraTxEnabled(boolean v) { this.loraTxEnabled = v; }

    public boolean isLoraIgnoreMqtt() { return loraIgnoreMqtt; }
    public void setLoraIgnoreMqtt(boolean v) { this.loraIgnoreMqtt = v; }

    /** Возвращает черновик канала по индексу 0..7. */
    public ChannelDraft getChannel(int index) {
        if (index < 0 || index >= MAX_CHANNELS) {
            throw new IndexOutOfBoundsException("channel index " + index);
        }
        return channels[index];
    }

    public ChannelDraft[] getChannels() { return channels; }

    public String getAdminKey(int index) {
        if (index < 0 || index >= MAX_ADMIN_KEYS) {
            throw new IndexOutOfBoundsException("admin key index " + index);
        }
        return adminKeys[index] == null ? "" : adminKeys[index];
    }

    public void setAdminKey(int index, String hex) {
        if (index < 0 || index >= MAX_ADMIN_KEYS) {
            throw new IndexOutOfBoundsException("admin key index " + index);
        }
        adminKeys[index] = hex == null ? "" : hex;
    }

    public boolean isManaged() { return isManaged; }
    public void setManaged(boolean v) { this.isManaged = v; }

    public String getUserShortName() { return userShortName == null ? "" : userShortName; }
    public void setUserShortName(String v) { this.userShortName = v == null ? "" : v; }

    public String getUserRole() { return userRole == null ? "CLIENT" : userRole; }
    public void setUserRole(String v) { this.userRole = v == null ? "CLIENT" : v; }

    public boolean isUserLicensed() { return userIsLicensed; }
    public void setUserLicensed(boolean v) { this.userIsLicensed = v; }

    // Устройство (DeviceConfig)
    private String deviceRebroadcastMode = "ALL"; // ALL/ALL_SKIP_DECODING/LOCAL_ONLY/KNOWN_ONLY/NONE/CORE_PORTNUMS_ONLY
    private int deviceNodeInfoBroadcastSecs = 10800;
    private boolean deviceSerialEnabled = false;
    private boolean deviceDebugLogEnabled = false;
    private boolean deviceLedHeartbeatDisabled = false;

    public String getDeviceRebroadcastMode() { return deviceRebroadcastMode == null ? "ALL" : deviceRebroadcastMode; }
    public void setDeviceRebroadcastMode(String v) { this.deviceRebroadcastMode = v == null ? "ALL" : v; }
    public int getDeviceNodeInfoBroadcastSecs() { return deviceNodeInfoBroadcastSecs; }
    public void setDeviceNodeInfoBroadcastSecs(int v) { this.deviceNodeInfoBroadcastSecs = v; }
    public boolean isDeviceSerialEnabled() { return deviceSerialEnabled; }
    public void setDeviceSerialEnabled(boolean v) { this.deviceSerialEnabled = v; }
    public boolean isDeviceDebugLogEnabled() { return deviceDebugLogEnabled; }
    public void setDeviceDebugLogEnabled(boolean v) { this.deviceDebugLogEnabled = v; }
    public boolean isDeviceLedHeartbeatDisabled() { return deviceLedHeartbeatDisabled; }
    public void setDeviceLedHeartbeatDisabled(boolean v) { this.deviceLedHeartbeatDisabled = v; }

    // Местоположение (PositionConfig)
    private String positionGpsMode = "ENABLED"; // DISABLED/ENABLED/NOT_PRESENT
    private int positionBroadcastSecs = 900;
    private boolean positionFixedEnabled = false;
    private boolean positionSmartEnabled = true;

    public String getPositionGpsMode() { return positionGpsMode == null ? "ENABLED" : positionGpsMode; }
    public void setPositionGpsMode(String v) { this.positionGpsMode = v == null ? "ENABLED" : v; }
    public int getPositionBroadcastSecs() { return positionBroadcastSecs; }
    public void setPositionBroadcastSecs(int v) { this.positionBroadcastSecs = v; }
    public boolean isPositionFixedEnabled() { return positionFixedEnabled; }
    public void setPositionFixedEnabled(boolean v) { this.positionFixedEnabled = v; }
    public boolean isPositionSmartEnabled() { return positionSmartEnabled; }
    public void setPositionSmartEnabled(boolean v) { this.positionSmartEnabled = v; }

    // Питание (PowerConfig)
    private boolean powerIsSaving = false;
    private int powerShutdownAfterSecs = 0;
    private int powerWaitBluetoothSecs = 60;
    private int powerMinWakeSecs = 10;

    public boolean isPowerSaving() { return powerIsSaving; }
    public void setPowerSaving(boolean v) { this.powerIsSaving = v; }
    public int getPowerShutdownAfterSecs() { return powerShutdownAfterSecs; }
    public void setPowerShutdownAfterSecs(int v) { this.powerShutdownAfterSecs = v; }
    public int getPowerWaitBluetoothSecs() { return powerWaitBluetoothSecs; }
    public void setPowerWaitBluetoothSecs(int v) { this.powerWaitBluetoothSecs = v; }
    public int getPowerMinWakeSecs() { return powerMinWakeSecs; }
    public void setPowerMinWakeSecs(int v) { this.powerMinWakeSecs = v; }

    // Сеть (NetworkConfig)
    private boolean networkWifiEnabled = false;
    private String networkWifiSsid = "";
    private String networkWifiPsk = "";
    private String networkNtpServer = "0.pool.ntp.org";
    private boolean networkEthEnabled = false;

    public boolean isNetworkWifiEnabled() { return networkWifiEnabled; }
    public void setNetworkWifiEnabled(boolean v) { this.networkWifiEnabled = v; }
    public String getNetworkWifiSsid() { return networkWifiSsid == null ? "" : networkWifiSsid; }
    public void setNetworkWifiSsid(String v) { this.networkWifiSsid = v == null ? "" : v; }
    public String getNetworkWifiPsk() { return networkWifiPsk == null ? "" : networkWifiPsk; }
    public void setNetworkWifiPsk(String v) { this.networkWifiPsk = v == null ? "" : v; }
    public String getNetworkNtpServer() { return networkNtpServer == null ? "0.pool.ntp.org" : networkNtpServer; }
    public void setNetworkNtpServer(String v) { this.networkNtpServer = v == null ? "0.pool.ntp.org" : v; }
    public boolean isNetworkEthEnabled() { return networkEthEnabled; }
    public void setNetworkEthEnabled(boolean v) { this.networkEthEnabled = v; }

    // Дисплей (DisplayConfig)
    private int displayScreenOnSecs = 30;
    private String displayUnits = "METRIC"; // METRIC/IMPERIAL
    private boolean displayFlipScreen = false;
    private boolean displayUse12hClock = false;
    private boolean displayHeadingBold = false;

    public int getDisplayScreenOnSecs() { return displayScreenOnSecs; }
    public void setDisplayScreenOnSecs(int v) { this.displayScreenOnSecs = v; }
    public String getDisplayUnits() { return displayUnits == null ? "METRIC" : displayUnits; }
    public void setDisplayUnits(String v) { this.displayUnits = v == null ? "METRIC" : v; }
    public boolean isDisplayFlipScreen() { return displayFlipScreen; }
    public void setDisplayFlipScreen(boolean v) { this.displayFlipScreen = v; }
    public boolean isDisplayUse12hClock() { return displayUse12hClock; }
    public void setDisplayUse12hClock(boolean v) { this.displayUse12hClock = v; }
    public boolean isDisplayHeadingBold() { return displayHeadingBold; }
    public void setDisplayHeadingBold(boolean v) { this.displayHeadingBold = v; }

    // Bluetooth (BluetoothConfig)
    private boolean bluetoothEnabled = true;
    private String bluetoothMode = "RANDOM_PIN"; // RANDOM_PIN/FIXED_PIN/NO_PIN
    private int bluetoothFixedPin = 123456;

    public boolean isBluetoothEnabled() { return bluetoothEnabled; }
    public void setBluetoothEnabled(boolean v) { this.bluetoothEnabled = v; }
    public String getBluetoothMode() { return bluetoothMode == null ? "RANDOM_PIN" : bluetoothMode; }
    public void setBluetoothMode(String v) { this.bluetoothMode = v == null ? "RANDOM_PIN" : v; }
    public int getBluetoothFixedPin() { return bluetoothFixedPin; }
    public void setBluetoothFixedPin(int v) { this.bluetoothFixedPin = v; }
}

