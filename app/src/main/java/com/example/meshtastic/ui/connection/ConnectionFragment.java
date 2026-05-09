package com.example.meshtastic.ui.connection;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.meshtastic.R;
import com.example.meshtastic.data.repository.MeshConnectionRepository;
import com.example.meshtastic.databinding.FragmentConnectionBinding;

/**
 * Фрагмент для поиска и подключения к устройству Meshtastic через BLE.
 */
public class ConnectionFragment extends Fragment {
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 100;
    private static final int REQUEST_LOCATION_PERMISSION = 101;

    private FragmentConnectionBinding binding;
    private MeshConnectionRepository repo;
    private BluetoothDevice selectedDevice;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentConnectionBinding.inflate(inflater, container, false);

        repo = MeshConnectionRepository.getInstance(requireContext());

        repo.getStatusText().observe(getViewLifecycleOwner(), text -> {
            if (binding == null) return;
            binding.statusText.setText(text);
            binding.progressBar.setVisibility(View.GONE);
            binding.connectButton.setEnabled(true);
            binding.sendTestButton.setEnabled(text != null && text.startsWith("Подключено"));
        });

        repo.getLastRx().observe(getViewLifecycleOwner(), data -> {
            if (binding == null) return;
            if (data == null || data.length == 0) {
                binding.lastRxText.setText(R.string.connection_dash);
            } else {
                binding.lastRxText.setText(toHex(data));
            }
        });

        binding.scanButton.setOnClickListener(v -> scanForDevices());
        binding.connectButton.setOnClickListener(v -> connectToDevice());
        binding.connectButton.setEnabled(false);
        binding.sendTestButton.setEnabled(false);
        binding.sendTestButton.setOnClickListener(v -> sendTest());

        checkPermissions();
        updateBluetoothStatus();

        return binding.getRoot();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
                Toast.makeText(requireContext(), R.string.connection_toast_permissions_granted, Toast.LENGTH_SHORT).show();
                updateBluetoothStatus();
            } else {
                Toast.makeText(requireContext(),
                        R.string.connection_toast_permissions_required,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void updateBluetoothStatus() {
        if (repo == null || binding == null) return;

        if (!repo.isBluetoothEnabled()) {
            binding.statusText.setText(R.string.connection_bluetooth_off);
            binding.statusText.setTextColor(ContextCompat.getColor(requireContext(),
                    android.R.color.holo_red_dark));
            binding.scanButton.setEnabled(false);
        } else {
            binding.statusText.setText(R.string.connection_bluetooth_on);
            binding.statusText.setTextColor(ContextCompat.getColor(requireContext(),
                    android.R.color.holo_green_dark));
            binding.scanButton.setEnabled(true);
        }

        binding.connectButton.setText(R.string.connection_btn_connect);
        binding.sendTestButton.setEnabled(false);
    }

    private void scanForDevices() {
        if (!repo.isBluetoothEnabled()) {
            Toast.makeText(requireContext(),
                    R.string.connection_toast_enable_bt,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.scanButton.setEnabled(false);
        binding.deviceListText.setText(R.string.connection_status_scanning);
        binding.connectButton.setEnabled(false);

        repo.startScan();

        repo.getDevices().observe(getViewLifecycleOwner(), devices -> {
            if (binding == null) return;
            binding.deviceListText.setText("");
            if (devices == null || devices.isEmpty()) {
                binding.deviceListText.setText(R.string.connection_status_searching);
                binding.connectButton.setEnabled(false);
                return;
            }
            int i = 1;
            for (BluetoothDevice d : devices) {
                String name = d.getName() != null ? d.getName() : getString(R.string.connection_device_unknown);
                binding.deviceListText.append(getString(R.string.connection_device_line, i, name, d.getAddress()));
                i++;
            }
            if (selectedDevice == null) {
                selectedDevice = devices.get(0);
                repo.selectDevice(selectedDevice);
                binding.connectButton.setEnabled(true);
            }
        });

        binding.scanButton.postDelayed(() -> {
            if (binding == null) return;
            repo.stopScan();
            binding.progressBar.setVisibility(View.GONE);
            binding.scanButton.setEnabled(true);
            if (selectedDevice == null) {
                binding.deviceListText.append(getString(R.string.connection_status_no_devices));
            }
        }, 6000);
    }

    private void connectToDevice() {
        if (selectedDevice == null) {
            Toast.makeText(requireContext(),
                    R.string.connection_toast_select_device,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.connectButton.setEnabled(false);
        binding.statusText.setText(R.string.connection_status_connecting);

        repo.selectDevice(selectedDevice);
        repo.connect();
    }

    private void sendTest() {
        // Запрос конфигурации - устройство ВСЕГДА отвечает на want_config_id
        int configId = (int) (System.currentTimeMillis() & 0x7fffffff);
        boolean ok = repo.sendToRadio(
                org.meshtastic.proto.MeshProtos.ToRadio.newBuilder()
                        .setWantConfigId(configId)
                        .build()
        );
        Toast.makeText(requireContext(),
                ok ? R.string.connection_test_sent : R.string.connection_test_failed,
                Toast.LENGTH_SHORT).show();
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
        binding = null;
        super.onDestroyView();
        // Соединение НЕ закрываем: оно общее для приложения и нужно другим экранам.
    }
}
