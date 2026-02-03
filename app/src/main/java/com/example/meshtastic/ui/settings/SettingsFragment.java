package com.example.meshtastic.ui.settings;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.meshtastic.R;
import com.example.meshtastic.data.model.SettingsDraft;
import com.example.meshtastic.data.repository.MeshConnectionRepository;
import com.example.meshtastic.data.storage.SettingsStore;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Экран настройки сети и безопасности (черновик + применение по BLE).
 */
public class SettingsFragment extends Fragment {

    private TextInputEditText nodeNameEdit;
    private TextInputEditText regionEdit;
    private TextInputEditText channelNameEdit;
    private TextInputEditText pskEdit;

    private SettingsStore store;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        nodeNameEdit = view.findViewById(R.id.node_name_edit);
        regionEdit = view.findViewById(R.id.region_edit);
        channelNameEdit = view.findViewById(R.id.channel_name_edit);
        pskEdit = view.findViewById(R.id.psk_edit);

        store = new SettingsStore(requireContext());
        fillDraft(store.load());

        view.findViewById(R.id.save_button).setOnClickListener(v -> saveDraft());
        view.findViewById(R.id.apply_button).setOnClickListener(v -> applyToDevice());

        return view;
    }

    private void fillDraft(SettingsDraft draft) {
        if (draft == null) return;
        nodeNameEdit.setText(draft.getNodeName());
        regionEdit.setText(draft.getRegion());
        channelNameEdit.setText(draft.getChannelName());
        pskEdit.setText(draft.getPsk());
    }

    private SettingsDraft collectDraft() {
        SettingsDraft draft = new SettingsDraft();
        draft.setNodeName(textOf(nodeNameEdit));
        draft.setRegion(textOf(regionEdit));
        draft.setChannelName(textOf(channelNameEdit));
        draft.setPsk(textOf(pskEdit));
        return draft;
    }

    private void saveDraft() {
        SettingsDraft draft = collectDraft();
        store.save(draft);
        Toast.makeText(requireContext(), "Черновик сохранен", Toast.LENGTH_SHORT).show();
    }

    private void applyToDevice() {
        SettingsDraft draft = collectDraft();
        store.save(draft);

        if (TextUtils.isEmpty(draft.getChannelName()) || TextUtils.isEmpty(draft.getPsk())) {
            Toast.makeText(requireContext(), "Укажите имя канала и PSK", Toast.LENGTH_SHORT).show();
            return;
        }

        MeshConnectionRepository repo = MeshConnectionRepository.getInstance(requireContext());
        boolean ok = repo.applyChannelPsk(draft.getChannelName(), draft.getPsk());

        Toast.makeText(requireContext(), ok ? "Настройки отправлены" : "Не удалось отправить", Toast.LENGTH_SHORT).show();
    }

    private static String textOf(TextInputEditText edit) {
        return edit.getText() != null ? edit.getText().toString().trim() : "";
    }
}

