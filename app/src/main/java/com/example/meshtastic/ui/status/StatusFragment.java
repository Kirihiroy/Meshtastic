package com.example.meshtastic.ui.status;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.meshtastic.R;
import com.example.meshtastic.data.model.DeviceStatus;
import com.example.meshtastic.data.repository.MeshConnectionRepository;

/**
 * Экран состояния устройства и сети.
 */
public class StatusFragment extends Fragment {

    private TextView stateText;
    private TextView statusText;
    private TextView deviceNameText;
    private TextView nodeNumText;
    private TextView firmwareText;
    private TextView batteryText;
    private TextView snrText;
    private TextView lastHeardText;
    private TextView lastRxText;
    private TextView lastRxHexText;
    private TextView lastSummaryText;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_status, container, false);

        stateText = view.findViewById(R.id.state_text);
        statusText = view.findViewById(R.id.status_text);
        deviceNameText = view.findViewById(R.id.device_name_text);
        nodeNumText = view.findViewById(R.id.node_num_text);
        firmwareText = view.findViewById(R.id.firmware_text);
        batteryText = view.findViewById(R.id.battery_text);
        snrText = view.findViewById(R.id.snr_text);
        lastHeardText = view.findViewById(R.id.last_heard_text);
        lastRxHexText = view.findViewById(R.id.last_rx_hex_text);
        lastRxText = view.findViewById(R.id.last_rx_text);
        lastSummaryText = view.findViewById(R.id.last_parsed_text);

        MeshConnectionRepository repo = MeshConnectionRepository.getInstance(requireContext());
        repo.getDeviceStatus().observe(getViewLifecycleOwner(), this::renderStatus);

        return view;
    }

    private void renderStatus(DeviceStatus status) {
        if (status == null) return;
        stateText.setText(safe(status.getState()));
        statusText.setText(safe(status.getStatusText()));
        deviceNameText.setText(safe(status.getDeviceName()));
        nodeNumText.setText(valueOrDash(status.getNodeNum()));
        firmwareText.setText(safe(status.getFirmwareVersion()));
        batteryText.setText(percentOrDash(status.getBatteryPercent()));
        snrText.setText(floatOrDash(status.getSnr()));
        lastHeardText.setText(valueOrDash(status.getLastHeard()));
        lastRxText.setText(valueOrDash(status.getLastRxAt()));
        lastRxHexText.setText(safe(status.getLastRxHex()));
        lastSummaryText.setText(safe(status.getLastSummary()));
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
}
