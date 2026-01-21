package com.example.meshtastic.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.meshtastic.R;

/**
 * Экран настроек (локально + позже отправка на устройство).
 * Закрывает пункт (4): кастомизация параметров/безопасности через UI.
 */
public class SettingsFragment extends Fragment {

    private static final String PREFS = "meshtastic_prefs";
    private static final String KEY_NODE_NAME = "node_name";
    private static final String KEY_REGION = "region";
    private static final String KEY_CHANNEL_NAME = "channel_name";
    private static final String KEY_CHANNEL_PSK = "channel_psk";

    private EditText nodeNameEdit;
    private EditText regionEdit;
    private EditText channelNameEdit;
    private EditText pskEdit;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        nodeNameEdit = view.findViewById(R.id.node_name_edit);
        regionEdit = view.findViewById(R.id.region_edit);
        channelNameEdit = view.findViewById(R.id.channel_name_edit);
        pskEdit = view.findViewById(R.id.psk_edit);
        Button saveBtn = view.findViewById(R.id.save_button);

        SharedPreferences sp = requireContext().getSharedPreferences(PREFS, 0);
        nodeNameEdit.setText(sp.getString(KEY_NODE_NAME, ""));
        regionEdit.setText(sp.getString(KEY_REGION, ""));
        channelNameEdit.setText(sp.getString(KEY_CHANNEL_NAME, ""));
        pskEdit.setText(sp.getString(KEY_CHANNEL_PSK, ""));

        saveBtn.setOnClickListener(v -> {
            sp.edit()
                    .putString(KEY_NODE_NAME, nodeNameEdit.getText().toString().trim())
                    .putString(KEY_REGION, regionEdit.getText().toString().trim())
                    .putString(KEY_CHANNEL_NAME, channelNameEdit.getText().toString().trim())
                    .putString(KEY_CHANNEL_PSK, pskEdit.getText().toString().trim())
                    .apply();
            Toast.makeText(requireContext(), "Сохранено (локально). Следующий шаг — применить на устройство через Protobuf.", Toast.LENGTH_SHORT).show();
        });

        return view;
    }
}

