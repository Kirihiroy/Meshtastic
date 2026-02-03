package com.example.meshtastic.ui.connection;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.meshtastic.R;
import com.example.meshtastic.data.repository.MeshConnectionRepository;

/**
 * Фрагмент для поиска и подключения к устройству Meshtastic через BLE.
 */
public class ConnectionFragment extends Fragment {
    private static final String TAG = "ConnectionFragment";
    
    // Коды запроса разрешений
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 100;
    private static final int REQUEST_LOCATION_PERMISSION = 101;
    
    private MeshConnectionRepository repo;
    private TextView statusText;
    private TextView deviceListText;
    private TextView lastRxText;
    private Button scanButton;
    private Button connectButton;
    private Button sendTestButton;
    private ProgressBar progressBar;
    
    private BluetoothDevice selectedDevice;
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connection, container, false);
        
        // Инициализация UI элементов
        statusText = view.findViewById(R.id.status_text);
        deviceListText = view.findViewById(R.id.device_list_text);
        lastRxText = view.findViewById(R.id.last_rx_text);
        scanButton = view.findViewById(R.id.scan_button);
        connectButton = view.findViewById(R.id.connect_button);
        sendTestButton = view.findViewById(R.id.send_test_button);
        progressBar = view.findViewById(R.id.progress_bar);
        
        // Репозиторий соединения (единый на всё приложение)
        repo = MeshConnectionRepository.getInstance(requireContext());
        
        // Подписки на состояние (подписываем один раз)
        repo.getStatusText().observe(getViewLifecycleOwner(), text -> {
            statusText.setText(text);
            progressBar.setVisibility(View.GONE);
            connectButton.setEnabled(true);
            // Разрешаем отправку теста только когда есть связь
            sendTestButton.setEnabled(text != null && text.startsWith("Подключено"));
        });

        repo.getLastRx().observe(getViewLifecycleOwner(), data -> {
            if (data == null || data.length == 0) {
                lastRxText.setText("—");
            } else {
                lastRxText.setText(toHex(data));
            }
        });

        // Настройка кнопок
        scanButton.setOnClickListener(v -> scanForDevices());
        connectButton.setOnClickListener(v -> connectToDevice());
        connectButton.setEnabled(false);
        sendTestButton.setEnabled(false);
        sendTestButton.setOnClickListener(v -> sendTest());
        
        // Проверка разрешений при создании
        checkPermissions();
        
        // Проверка состояния Bluetooth
        updateBluetoothStatus();
        
        return view;
    }
    
    /**
     * Проверяет и запрашивает необходимые разрешения.
     */
    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ требует новые разрешения
            if (ContextCompat.checkSelfPermission(requireContext(), 
                    Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), 
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                        },
                        REQUEST_BLUETOOTH_PERMISSIONS);
            }
        }
        
        // Разрешение на местоположение необходимо для поиска устройств
        if (ContextCompat.checkSelfPermission(requireContext(), 
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS || 
            requestCode == REQUEST_LOCATION_PERMISSION) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                Toast.makeText(requireContext(), "Разрешения предоставлены", Toast.LENGTH_SHORT).show();
                updateBluetoothStatus();
            } else {
                Toast.makeText(requireContext(), 
                        "Необходимы разрешения для работы Bluetooth", 
                        Toast.LENGTH_LONG).show();
            }
        }
    }
    
    /**
     * Обновляет статус Bluetooth и отображает его в UI.
     */
    private void updateBluetoothStatus() {
        if (repo == null) {
            return;
        }
        
        if (!repo.isBluetoothEnabled()) {
            statusText.setText("Bluetooth выключен. Включите Bluetooth в настройках.");
            statusText.setTextColor(ContextCompat.getColor(requireContext(), 
                    android.R.color.holo_red_dark));
            scanButton.setEnabled(false);
        } else {
            statusText.setText("Bluetooth включен. Готов к поиску устройств.");
            statusText.setTextColor(ContextCompat.getColor(requireContext(), 
                    android.R.color.holo_green_dark));
            scanButton.setEnabled(true);
        }
        
        connectButton.setText("Подключиться");
        sendTestButton.setEnabled(false);
    }
    
    /**
     * Ищет BLE устройства Meshtastic.
     */
    private void scanForDevices() {
        if (!repo.isBluetoothEnabled()) {
            Toast.makeText(requireContext(), 
                    "Включите Bluetooth", 
                    Toast.LENGTH_SHORT).show();
            return;
        }
        
        progressBar.setVisibility(View.VISIBLE);
        scanButton.setEnabled(false);
        deviceListText.setText("Поиск BLE устройств Meshtastic...");
        connectButton.setEnabled(false);

        repo.startScan();

        // Подпишемся на список устройств и обновим текст
        repo.getDevices().observe(getViewLifecycleOwner(), devices -> {
            deviceListText.setText("");
            if (devices == null || devices.isEmpty()) {
                deviceListText.setText("Поиск…");
                connectButton.setEnabled(false);
                return;
            }
            int i = 1;
            for (BluetoothDevice d : devices) {
                String name = d.getName() != null ? d.getName() : "Неизвестное устройство";
                deviceListText.append(i + ". " + name + "\n   " + d.getAddress() + "\n\n");
                i++;
            }
            // выбираем первое найденное устройство
            if (selectedDevice == null) {
                selectedDevice = devices.get(0);
                repo.selectDevice(selectedDevice);
                connectButton.setEnabled(true);
            }
        });

        // Останавливаем скан через 6 секунд
        scanButton.postDelayed(() -> {
            repo.stopScan();
            progressBar.setVisibility(View.GONE);
            scanButton.setEnabled(true);
            if (selectedDevice == null) {
                deviceListText.append("Устройства не найдены. Убедитесь, что Heltec включен и рядом.\n");
            }
        }, 6000);
    }
    
    /**
     * Подключается к выбранному устройству.
     */
    private void connectToDevice() {
        if (selectedDevice == null) {
            Toast.makeText(requireContext(), 
                    "Сначала выберите устройство", 
                    Toast.LENGTH_SHORT).show();
            return;
        }
        
        progressBar.setVisibility(View.VISIBLE);
        connectButton.setEnabled(false);
        statusText.setText("Подключение...");

        repo.selectDevice(selectedDevice);
        repo.connect();
    }

    private void sendTest() {
        // Запрос конфигурации - устройство ВСЕГДА отвечает на want_config_id
        // Heartbeat НЕ вызывает ответа (он только для поддержания serial-соединения)
        int configId = (int) (System.currentTimeMillis() & 0x7fffffff);
        boolean ok = repo.sendToRadio(
                org.meshtastic.proto.MeshProtos.ToRadio.newBuilder()
                        .setWantConfigId(configId)
                        .build()
        );
        Toast.makeText(requireContext(), ok ? "Запрос конфигурации отправлен" : "Не удалось отправить", Toast.LENGTH_SHORT).show();
    }

    private static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Соединение НЕ закрываем: оно общее для приложения и нужно другим экранам
    }
}
