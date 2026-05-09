package com.example.meshtastic.data.model;

import java.util.Objects;

/**
 * Информация об узле в mesh-сети.
 */
public class NodeInfo {
    private long nodeNum;             // Номер узла
    private String userId;            // User.id
    private String longName;          // User.long_name
    private String shortName;         // User.short_name
    private double latitude;          // В градусах
    private double longitude;         // В градусах
    private float snr;                // Последний SNR
    private int batteryLevel = -1;    // 0-100 или -1 если неизвестно
    private long lastHeard;           // epoch seconds, как в протоколе
    private boolean viaMqtt;          // Узел слышали через MQTT
    private Integer hopsAway;         // Кол-во хопов, если есть
    private Integer channel;          // Индекс канала, если не основной

    public long getNodeNum() {
        return nodeNum;
    }

    public void setNodeNum(long nodeNum) {
        this.nodeNum = nodeNum;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getLongName() {
        return longName;
    }

    public void setLongName(String longName) {
        this.longName = longName;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public float getSnr() {
        return snr;
    }

    public void setSnr(float snr) {
        this.snr = snr;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public long getLastHeard() {
        return lastHeard;
    }

    public void setLastHeard(long lastHeard) {
        this.lastHeard = lastHeard;
    }

    public boolean isViaMqtt() {
        return viaMqtt;
    }

    public void setViaMqtt(boolean viaMqtt) {
        this.viaMqtt = viaMqtt;
    }

    public Integer getHopsAway() {
        return hopsAway;
    }

    public void setHopsAway(Integer hopsAway) {
        this.hopsAway = hopsAway;
    }

    public Integer getChannel() {
        return channel;
    }

    public void setChannel(Integer channel) {
        this.channel = channel;
    }

    /** Идентичность узла — по nodeNum (стабильный ключ из протокола). */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeInfo)) return false;
        NodeInfo that = (NodeInfo) o;
        return nodeNum == that.nodeNum
                && Double.compare(that.latitude, latitude) == 0
                && Double.compare(that.longitude, longitude) == 0
                && Float.compare(that.snr, snr) == 0
                && batteryLevel == that.batteryLevel
                && lastHeard == that.lastHeard
                && viaMqtt == that.viaMqtt
                && Objects.equals(userId, that.userId)
                && Objects.equals(longName, that.longName)
                && Objects.equals(shortName, that.shortName)
                && Objects.equals(hopsAway, that.hopsAway)
                && Objects.equals(channel, that.channel);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(nodeNum);
    }
}
