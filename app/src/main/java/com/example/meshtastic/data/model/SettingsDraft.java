package com.example.meshtastic.data.model;

/**
 * Локальный черновик настроек, сохраняется до применения на устройство.
 */
public class SettingsDraft {
    private String nodeName;
    private String region;
    private String channelName;
    private String psk;

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
}

