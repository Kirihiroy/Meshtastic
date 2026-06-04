package com.example.meshtastic.ui.connection;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meshtastic.R;
import com.example.meshtastic.data.repository.MeshConnectionRepository;
import com.example.meshtastic.data.repository.MeshConnectionRepository.State;
import com.example.meshtastic.databinding.FragmentConnectionBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран «Соединение» — выбор и подключение к Meshtastic-устройству по BLE.
 * Дизайн повторяет оригинальное Meshtastic-Android: большая карточка статуса,
 * сегмент-контрол транспортов (BT/Сеть/COM), список устройств с радио-кнопкой
 * и одна state-зависимая кнопка действия.
 */
public class ConnectionFragment extends Fragment {

    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 100;
    private static final int REQUEST_LOCATION_PERMISSION = 101;

    private FragmentConnectionBinding binding;
    private MeshConnectionRepository repo;
    private DevicesAdapter adapter;
    private BluetoothDevice selectedDevice;
    private State lastState = State.DISCONNECTED;
    private boolean scanning = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentConnectionBinding.inflate(inflater, container, false);
        repo = MeshConnectionRepository.getInstance(requireContext());

        binding.connDevicesRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DevicesAdapter(device -> {
            selectedDevice = device;
            repo.selectDevice(device);
            adapter.setSelectedAddress(device.getAddress());
            renderState(lastState);
        });
        binding.connDevicesRecycler.setAdapter(adapter);

        // Сегмент-контрол — только Bluetooth активен в MVP.
        binding.connTabBt.setOnClickListener(v -> {
            // Уже выбрана — ничего не делаем.
        });
        binding.connTabNet.setOnClickListener(v -> tabPlaceholder(R.string.connection_tab_network));
        binding.connTabCom.setOnClickListener(v -> tabPlaceholder(R.string.connection_tab_com));

        binding.connActionButton.setOnClickListener(v -> onActionClicked());

        repo.getState().observe(getViewLifecycleOwner(), this::renderState);
        repo.getDevices().observe(getViewLifecycleOwner(), this::renderDevices);

        // Если уже было соединение/устройство — отразим в адаптере.
        BluetoothDevice already = repo.getSelectedDevice().getValue();
        if (already != null) {
            selectedDevice = already;
            adapter.setSelectedAddress(already.getAddress());
        }

        checkPermissions();
        return binding.getRoot();
    }

    /** Главная кнопка: smart-actions в зависимости от текущего состояния. */
    private void onActionClicked() {
        State st = lastState;
        if (scanning) {
            repo.stopScan();
            scanning = false;
            renderState(st);
            return;
        }
        if (st == State.CONNECTED || st == State.CONNECTING) {
            repo.disconnect();
            return;
        }
        if (selectedDevice != null) {
            if (!repo.isBluetoothEnabled()) {
                Toast.makeText(requireContext(), R.string.connection_toast_enable_bt, Toast.LENGTH_SHORT).show();
                return;
            }
            repo.connect();
            return;
        }
        // ничего не выбрано — запускаем сканирование
        if (!repo.isBluetoothEnabled()) {
            Toast.makeText(requireContext(), R.string.connection_toast_enable_bt, Toast.LENGTH_SHORT).show();
            return;
        }
        scanning = true;
        repo.startScan();
        renderState(st);
        // Авто-остановка через 8 сек
        if (binding != null) {
            binding.connActionButton.postDelayed(() -> {
                if (binding == null) return;
                if (scanning) {
                    repo.stopScan();
                    scanning = false;
                    renderState(lastState);
                }
            }, 8000);
        }
    }

    private void renderState(State state) {
        if (binding == null) return;
        if (state != null) lastState = state;

        // Большая карточка
        if (lastState == State.CONNECTED || lastState == State.CONNECTING) {
            binding.connBigIcon.setImageResource(R.drawable.ic_router_big);
            binding.connBigText.setText(selectedDevice != null
                    ? safeName(selectedDevice)
                    : getString(R.string.connection_chip_connected));
        } else if (selectedDevice != null) {
            binding.connBigIcon.setImageResource(R.drawable.ic_router_big);
            binding.connBigText.setText(safeName(selectedDevice));
        } else {
            binding.connBigIcon.setImageResource(R.drawable.ic_router_off);
            binding.connBigText.setText(R.string.connection_no_device);
        }

        // Кнопка-действие
        if (scanning) {
            binding.connActionButton.setText(R.string.connection_btn_stop_scan);
            binding.connActionButton.setIconResource(R.drawable.ic_search);
        } else if (lastState == State.CONNECTED) {
            binding.connActionButton.setText(R.string.connection_btn_disconnect);
            binding.connActionButton.setIconResource(R.drawable.ic_cloud_off);
        } else if (lastState == State.CONNECTING) {
            binding.connActionButton.setText(R.string.connection_chip_connecting);
            binding.connActionButton.setIconResource(R.drawable.ic_search);
        } else if (selectedDevice != null) {
            binding.connActionButton.setText(R.string.connection_btn_connect);
            binding.connActionButton.setIconResource(R.drawable.ic_router);
        } else {
            binding.connActionButton.setText(R.string.connection_btn_scan);
            binding.connActionButton.setIconResource(R.drawable.ic_search);
        }

        // Чип статуса
        int chipText;
        if (scanning) chipText = R.string.connection_chip_scanning;
        else if (lastState == State.CONNECTED) chipText = R.string.connection_chip_connected;
        else if (lastState == State.CONNECTING) chipText = R.string.connection_chip_connecting;
        else chipText = R.string.connection_chip_disconnected;
        binding.connStatusChip.setText(chipText);
    }

    private void renderDevices(List<BluetoothDevice> devices) {
        adapter.submitList(devices == null ? new ArrayList<>() : new ArrayList<>(devices));
    }

    private void tabPlaceholder(int tabLabelRes) {
        Toast.makeText(requireContext(),
                getString(R.string.connection_toast_tab_not_implemented, getString(tabLabelRes)),
                Toast.LENGTH_SHORT).show();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean needScan = ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED;
            boolean needConnect = ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED;
            if (needScan || needConnect) {
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT},
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS || requestCode == REQUEST_LOCATION_PERMISSION) {
            boolean allGranted = true;
            for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            int msg = allGranted
                    ? R.string.connection_toast_permissions_granted
                    : R.string.connection_toast_permissions_required;
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        }
    }

    /** Безопасное имя устройства. Static, чтобы вызывать из nested static-адаптера. */
    private static String safeName(BluetoothDevice d) {
        try {
            String n = d.getName();
            return (n == null || n.isEmpty()) ? d.getAddress() : n;
        } catch (SecurityException e) {
            return d.getAddress();
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // ───────────── adapter ─────────────

    private static class DevicesAdapter extends ListAdapter<BluetoothDevice, DevicesAdapter.VH> {

        interface OnClick { void onClick(BluetoothDevice device); }

        private final OnClick onClick;
        private String selectedAddress;

        DevicesAdapter(OnClick onClick) {
            super(new DiffUtil.ItemCallback<BluetoothDevice>() {
                @Override public boolean areItemsTheSame(@NonNull BluetoothDevice a, @NonNull BluetoothDevice b) {
                    return a.getAddress().equals(b.getAddress());
                }
                @Override public boolean areContentsTheSame(@NonNull BluetoothDevice a, @NonNull BluetoothDevice b) {
                    return a.getAddress().equals(b.getAddress());
                }
            });
            this.onClick = onClick;
        }

        void setSelectedAddress(String addr) {
            selectedAddress = addr;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_connection_device, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            BluetoothDevice d = getItem(position);
            h.name.setText(safeName(d));
            h.radio.setChecked(d.getAddress().equals(selectedAddress));
            h.itemView.setOnClickListener(v -> onClick.onClick(d));
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView name;
            final RadioButton radio;
            VH(@NonNull View v) {
                super(v);
                name = v.findViewById(R.id.device_name);
                radio = v.findViewById(R.id.device_radio);
            }
        }
    }
}
