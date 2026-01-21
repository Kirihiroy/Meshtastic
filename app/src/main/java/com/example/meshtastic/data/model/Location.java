package com.example.meshtastic.data.model;

/**
 * Модель географических координат.
 */
public class Location {
    private double latitude;
    private double longitude;
    private float accuracy; // Точность в метрах
    private double altitude; // Высота над уровнем моря (если доступно)
    private long timestamp;
    
    public Location() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public Location(double latitude, double longitude) {
        this();
        this.latitude = latitude;
        this.longitude = longitude;
    }
    
    public Location(double latitude, double longitude, float accuracy) {
        this(latitude, longitude);
        this.accuracy = accuracy;
    }
    
    /**
     * Вычисляет расстояние до другой точки в метрах (формула гаверсинуса).
     */
    public double distanceTo(Location other) {
        final int R = 6371000; // Радиус Земли в метрах
        
        double lat1Rad = Math.toRadians(this.latitude);
        double lat2Rad = Math.toRadians(other.latitude);
        double deltaLatRad = Math.toRadians(other.latitude - this.latitude);
        double deltaLonRad = Math.toRadians(other.longitude - this.longitude);
        
        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
    
    // Getters and Setters
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
    
    public float getAccuracy() {
        return accuracy;
    }
    
    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }
    
    public double getAltitude() {
        return altitude;
    }
    
    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
