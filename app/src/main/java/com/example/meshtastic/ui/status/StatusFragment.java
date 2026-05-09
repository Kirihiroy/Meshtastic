package com.example.meshtastic.ui.status;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.meshtastic.data.model.DeviceStatus;
import com.example.meshtastic.data.repository.MeshConnectionRepository;
import com.example.meshtastic.databinding.FragmentStatusBinding;

/**
 * Экран состояния устройства и сети.
 */
public class StatusFragment extends Fragment {

    private FragmentStatusBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentStatusBinding.inflate(inflater, container, false);

        MeshConnectionRepository repo = MeshConnectionRepository.getInstance(requireContext());
        repo.getDeviceStatus().observe(getViewLifecycleOwner(), this::renderStatus);

        return binding.getRoot();
    }

    private void renderStatus(DeviceStatus status) {
        if (binding == null || status == null) return;
        binding.stateText.setText(safe(status.getState()));
        binding.statusText.setText(safe(status.getStatusText()));
        binding.deviceNameText.setText(safe(status.getDeviceName()));
        binding.nodeNumText.setText(valueOrDash(status.getNodeNum()));
        binding.firmwareText.setText(safe(status.getFirmwareVersion()));
        binding.batteryText.setText(percentOrDash(status.getBatteryPercent()));
        binding.snrText.setText(floatOrDash(status.getSnr()));
        binding.lastHeardText.setText(valueOrDash(status.getLastHeard()));
        binding.lastRxText.setText(valueOrDash(status.getLastRxAt()));
        binding.lastRxHexText.setText(safe(status.getLastRxHex()));
        binding.lastParsedText.setText(safe(status.getLastSummary()));
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "—" : value;
    }

    private static String valueOrDash(Long value) {
        return value == null || value == 0 ? "—" : String.valueOf(value);
    }

    private static String percentOrDash(Integer value) {
        return value == null || value < 0 ? "—" : value + "%";
    }

    private static String floatOrDash(Float value) {
        return value == null ? "—" : String.format("%.1f", value);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
