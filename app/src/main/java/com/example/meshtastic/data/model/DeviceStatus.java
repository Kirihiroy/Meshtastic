package com.example.meshtastic.data.model;

import java.util.Objects;

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

    public DeviceStatus() { }

    /** Копирующий конструктор: используется в Repository для иммутабельных постов в LiveData. */
    public DeviceStatus(DeviceStatus other) {
        if (other == null) return;
        this.state = other.state;
        this.statusText = other.statusText;
        this.deviceName = other.deviceName;
        this.nodeNum = other.nodeNum;
        this.firmwareVersion = other.firmwareVersion;
        this.batteryPercent = other.batteryPercent;
        this.snr = other.snr;
        this.lastHeard = other.lastHeard;
        this.lastRxAt = other.lastRxAt;
        this.lastSummary = other.lastSummary;
        this.lastRxHex = other.lastRxHex;
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeviceStatus)) return false;
        DeviceStatus that = (DeviceStatus) o;
        return Objects.equals(state, that.state)
                && Objects.equals(statusText, that.statusText)
                && Objects.equals(deviceName, that.deviceName)
                && Objects.equals(nodeNum, that.nodeNum)
                && Objects.equals(firmwareVersion, that.firmwareVersion)
                && Objects.equals(batteryPercent, that.batteryPercent)
                && Objects.equals(snr, that.snr)
                && Objects.equals(lastHeard, that.lastHeard)
                && Objects.equals(lastRxAt, that.lastRxAt)
                && Objects.equals(lastSummary, that.lastSummary)
                && Objects.equals(lastRxHex, that.lastRxHex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, statusText, deviceName, nodeNum, firmwareVersion,
                batteryPercent, snr, lastHeard, lastRxAt, lastSummary, lastRxHex);
    }
}
