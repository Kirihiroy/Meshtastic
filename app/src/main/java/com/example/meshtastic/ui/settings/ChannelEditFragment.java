package com.example.meshtastic.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.meshtastic.R;
import com.example.meshtastic.data.model.ChannelDraft;
import com.example.meshtastic.data.model.SettingsDraft;
import com.example.meshtastic.data.storage.SettingsStore;
import com.example.meshtastic.databinding.FragmentChannelEditBinding;

import java.util.Arrays;
import java.util.List;

/**
 * Редактирование одного канала по индексу 0..7.
 * Канал #0 — PRIMARY (роль не меняется), остальные могут быть SECONDARY/DISABLED.
 */
public class ChannelEditFragment extends Fragment {

    private static final String ARG_INDEX = "channel_index";

    /** Роли в выпадающем списке для НЕ-primary каналов. */
    private static final List<String> SECONDARY_ROLES = Arrays.asList(
            ChannelDraft.ROLE_SECONDARY, ChannelDraft.ROLE_DISABLED);

    /** Роли для primary (только PRIMARY, без возможности выбора). */
    private static final List<String> PRIMARY_ROLES = java.util.Collections.singletonList(
            ChannelDraft.ROLE_PRIMARY);

    public static ChannelEditFragment newInstance(int index) {
        ChannelEditFragment f = new ChannelEditFragment();
        Bundle b = new Bundle();
        b.putInt(ARG_INDEX, index);
        f.setArguments(b);
        return f;
    }

    private FragmentChannelEditBinding binding;
    private SettingsStore store;
    private int index;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChannelEditBinding.inflate(inflater, container, false);
        store = new SettingsStore(requireContext());
        index = requireArguments().getInt(ARG_INDEX, 0);

        ChannelDraft channel = store.load().getChannel(index);

        binding.channelEditTitle.setText(getString(R.string.channel_edit_title_format, index));
        binding.channelEditName.setText(channel.getName());
        binding.channelEditPsk.setText(channel.getPsk());

        // Роль — primary заблокирована, остальным даём выбор SECONDARY/DISABLED.
        List<String> roleOptions = (index == 0) ? PRIMARY_ROLES : SECONDARY_ROLES;
        binding.channelEditRole.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_list_item_1, roleOptions));
        binding.channelEditRole.setText(channel.getRole(), false);
        if (index == 0) {
            binding.channelEditRole.setEnabled(false);
            binding.channelEditRole.setOnClickListener(v -> Toast.makeText(requireContext(),
                    R.string.channel_toast_primary_role_locked, Toast.LENGTH_SHORT).show());
        }

        binding.channelEditBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        binding.channelEditSave.setOnClickListener(v -> save());

        return binding.getRoot();
    }

    private void save() {
        String name = textOf(binding.channelEditName);
        String psk = textOf(binding.channelEditPsk);
        String role = textOf(binding.channelEditRole);

        if (!isValidPsk(psk)) {
            Toast.makeText(requireContext(), R.string.channel_toast_bad_psk, Toast.LENGTH_SHORT).show();
            return;
        }

        SettingsDraft draft = store.load();
        ChannelDraft channel = draft.getChannel(index);
        channel.setName(name);
        channel.setPsk(psk);
        // Primary всегда PRIMARY, чтобы случайно не сломать.
        channel.setRole(index == 0 ? ChannelDraft.ROLE_PRIMARY : role);
        store.save(draft);

        Toast.makeText(requireContext(),
                getString(R.string.channel_toast_saved, index),
                Toast.LENGTH_SHORT).show();
    }

    /** PSK: пусто (без шифрования) ИЛИ hex длиной 32 или 64 символа. */
    private static boolean isValidPsk(String psk) {
        if (psk == null || psk.isEmpty()) return true;
        if (psk.length() != 32 && psk.length() != 64) return false;
        return psk.matches("^[0-9a-fA-F]+$");
    }

    private static String textOf(android.widget.EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
