package com.example.meshtastic.ui.status;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.example.meshtastic.R;
import com.example.meshtastic.data.repository.MeshConnectionRepository;

/**
 * Экран статуса: показывает состояние подключения и последнюю активность.
 * Это закрывает пункты (2) и (3): мониторинг + обратная связь.
 */
public class StatusFragment extends Fragment {

    private TextView stateText;
    private TextView statusText;
    private TextView lastRxText;
    private TextView lastParsedText;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_status, container, false);

        stateText = view.findViewById(R.id.state_text);
        statusText = view.findViewById(R.id.status_text);
        lastRxText = view.findViewById(R.id.last_rx_text);
        lastParsedText = view.findViewById(R.id.last_parsed_text);

        MeshConnectionRepository repo = MeshConnectionRepository.getInstance(requireContext());

        repo.getState().observe(getViewLifecycleOwner(), s -> stateText.setText(String.valueOf(s)));
        repo.getStatusText().observe(getViewLifecycleOwner(), t -> statusText.setText(t));
        repo.getLastRx().observe(getViewLifecycleOwner(), bytes -> {
            if (bytes == null || bytes.length == 0) {
                lastRxText.setText("—");
            } else {
                lastRxText.setText(toHex(bytes));
            }
        });

        repo.getLastFromRadioSummary().observe(getViewLifecycleOwner(), s -> {
            if (s == null || s.isEmpty()) {
                lastParsedText.setText("—");
            } else {
                lastParsedText.setText(s);
            }
        });

        return view;
    }

    private static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}

