package com.example.meshtastic.data.model;

/**
 * Локальный черновик настроек, сохраняется до применения на устройство.
 */
public class SettingsDraft {
    private String nodeName;
    private String region;             // LoRa: имя enum (UNSET, US, EU_868, ...)
    private String channelName;
    private String psk;

    // LoRa-параметры (черновик; применение на устройство — отдельным PR)
    private String loraModemPreset = "LONG_FAST";
    private int loraHopLimit = 3;       // 1..7
    private int loraTxPower = 20;       // 0..30 dBm
    private boolean loraTxEnabled = true;
    private boolean loraIgnoreMqtt = false;

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
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getPsk() {
        return psk;
    }

    public void setPsk(String psk) {
        this.psk = psk;
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
}

