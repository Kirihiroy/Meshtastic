package com.example.meshtastic.data.model;

/**
 * Снимок состояния устройства и соединения для экрана статуса.
 */
public class DeviceStatus {
    private String state;
    private String statusText;
    private String deviceName;
    private Long nodeNum;
    private String firmwareVersion;
    private Integer batteryPercent;
    private Float snr;
    private Long lastHeard;
    private Long lastRxAt;
    private String lastSummary;
    private String lastRxHex;

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public Long getNodeNum() {
        return nodeNum;
    }

    public void setNodeNum(Long nodeNum) {
        this.nodeNum = nodeNum;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public void setFirmwareVersion(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
    }

    public Integer getBatteryPercent() {
        return batteryPercent;
    }

    public void setBatteryPercent(Integer batteryPercent) {
        this.batteryPercent = batteryPercent;
    }

    public Float getSnr() {
        return snr;
    }

    public void setSnr(Float snr) {
        this.snr = snr;
    }

    public Long getLastHeard() {
        return lastHeard;
    }

    public void setLastHeard(Long lastHeard) {
        this.lastHeard = lastHeard;
    }

    public Long getLastRxAt() {
        return lastRxAt;
    }

    public void setLastRxAt(Long lastRxAt) {
        this.lastRxAt = lastRxAt;
    }

    public String getLastSummary() {
        return lastSummary;
    }

    public void setLastSummary(String lastSummary) {
        this.lastSummary = lastSummary;
    }

    public String getLastRxHex() {
        return lastRxHex;
    }

    public void setLastRxHex(String lastRxHex) {
        this.lastRxHex = lastRxHex;
    }
}
