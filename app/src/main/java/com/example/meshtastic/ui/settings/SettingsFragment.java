package com.example.meshtastic.ui.settings;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.meshtastic.R;
import com.example.meshtastic.data.model.SettingsDraft;
import com.example.meshtastic.data.repository.MeshConnectionRepository;
import com.example.meshtastic.data.storage.SettingsStore;
import com.example.meshtastic.databinding.FragmentSettingsBinding;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Экран настройки сети и безопасности (черновик + применение по BLE).
 */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SettingsStore store;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);

        store = new SettingsStore(requireContext());
        fillDraft(store.load());

        binding.saveButton.setOnClickListener(v -> saveDraft());
        binding.applyButton.setOnClickListener(v -> applyToDevice());

        return binding.getRoot();
    }

    private void fillDraft(SettingsDraft draft) {
        if (draft == null || binding == null) return;
        binding.nodeNameEdit.setText(draft.getNodeName());
        binding.regionEdit.setText(draft.getRegion());
        binding.channelNameEdit.setText(draft.getChannelName());
        binding.pskEdit.setText(draft.getPsk());
    }

    private SettingsDraft collectDraft() {
        SettingsDraft draft = new SettingsDraft();
        draft.setNodeName(textOf(binding.nodeNameEdit));
        draft.setRegion(textOf(binding.regionEdit));
        draft.setChannelName(textOf(binding.channelNameEdit));
        draft.setPsk(textOf(binding.pskEdit));
        return draft;
    }

    private void saveDraft() {
        SettingsDraft draft = collectDraft();
        store.save(draft);
        Toast.makeText(requireContext(), R.string.settings_toast_draft_saved, Toast.LENGTH_SHORT).show();
    }

    private void applyToDevice() {
        SettingsDraft draft = collectDraft();
        store.save(draft);

        if (TextUtils.isEmpty(draft.getChannelName()) || TextUtils.isEmpty(draft.getPsk())) {
            Toast.makeText(requireContext(), R.string.settings_toast_missing_channel, Toast.LENGTH_SHORT).show();
            return;
        }

        MeshConnectionRepository repo = MeshConnectionRepository.getInstance(requireContext());
        boolean ok = repo.applyChannelPsk(draft.getChannelName(), draft.getPsk());

        Toast.makeText(requireContext(),
                ok ? R.string.settings_toast_applied : R.string.settings_toast_apply_failed,
                Toast.LENGTH_SHORT).show();
    }

    private static String textOf(TextInputEditText edit) {
        return edit.getText() != null ? edit.getText().toString().trim() : "";
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
