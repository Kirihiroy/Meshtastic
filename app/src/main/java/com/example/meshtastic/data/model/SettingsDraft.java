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
}

