package com.example.meshtastic.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.ActivityCompat;

/**
 * Вспомогательный класс для работы с GPS-координатами.
 * Упрощенная реализация для MVP. В продакшене лучше использовать FusedLocationProviderClient.
 */
public class LocationHelper implements LocationListener {
    private static final String TAG = "LocationHelper";
    
    private LocationManager locationManager;
    private Context context;
    private LocationCallback callback;
    
    public interface LocationCallback {
        void onLocationReceived(android.location.Location location);
    }
    
    public LocationHelper(Context context) {
        this.context = context;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }
    
    /**
     * Проверяет, есть ли разрешение на доступ к местоположению.
     */
    public boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED ||
               ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Проверяет, включен ли GPS.
     */
    public boolean isGpsEnabled() {
        return locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }
    
    /**
     * Начинает получение обновлений местоположения.
     */
    public void startLocationUpdates(LocationCallback callback) {
        if (!hasLocationPermission()) {
            Log.e(TAG, "Нет разрешения на доступ к местоположению");
            return;
        }
        
        if (!isGpsEnabled()) {
            Log.e(TAG, "GPS не включен");
            return;
        }
        
        this.callback = callback;
        
        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    Constants.GPS_UPDATE_INTERVAL,
                    Constants.GPS_UPDATE_DISTANCE,
                    this
            );
            Log.d(TAG, "Начато получение обновлений GPS");
        } catch (SecurityException e) {
            Log.e(TAG, "Ошибка при запросе обновлений местоположения", e);
        }
    }
    
    /**
     * Останавливает получение обновлений местоположения.
     */
    public void stopLocationUpdates() {
        if (locationManager != null) {
            locationManager.removeUpdates(this);
            Log.d(TAG, "Остановлено получение обновлений GPS");
        }
    }
    
    /**
     * Получает последнее известное местоположение.
     */
    public android.location.Location getLastKnownLocation() {
        if (!hasLocationPermission()) {
            return null;
        }
        
        try {
            android.location.Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            return location;
        } catch (SecurityException e) {
            Log.e(TAG, "Ошибка при получении последнего местоположения", e);
            return null;
        }
    }
    
    @Override
    public void onLocationChanged(android.location.Location location) {
        if (callback != null) {
            callback.onLocationReceived(location);
        }
    }
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Не используется в MVP
    }
    
    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "Провайдер включен: " + provider);
    }
    
    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "Провайдер выключен: " + provider);
    }
    
    /**
     * Конвертирует android.location.Location в модель приложения.
     */
    public static com.example.meshtastic.data.model.Location convertToAppLocation(android.location.Location androidLocation) {
        com.example.meshtastic.data.model.Location appLocation = new com.example.meshtastic.data.model.Location(
                androidLocation.getLatitude(),
                androidLocation.getLongitude(),
                androidLocation.getAccuracy()
        );
        if (androidLocation.hasAltitude()) {
            appLocation.setAltitude(androidLocation.getAltitude());
        }
        return appLocation;
    }
}
