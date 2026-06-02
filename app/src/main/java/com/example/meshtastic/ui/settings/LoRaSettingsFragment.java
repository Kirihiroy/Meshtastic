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
import com.example.meshtastic.data.model.SettingsDraft;
import com.example.meshtastic.data.storage.SettingsStore;
import com.example.meshtastic.databinding.FragmentLoraSettingsBinding;

import java.util.Arrays;
import java.util.List;

/**
 * Под-экран «LoRa» из меню Настройки.
 * Редактирует черновик LoRa-конфига (регион, пресет модема, hop limit, TX мощность,
 * TX включён, игнорировать MQTT). Сохранение пока локальное — в SettingsStore.
 * Apply-на-устройство (admin proto Config.LoRaConfig) — отдельным PR.
 */
public class LoRaSettingsFragment extends Fragment {

    private static final List<String> REGIONS = Arrays.asList(
            "UNSET", "US", "EU_433", "EU_868", "CN", "JP", "ANZ",
            "KR", "TW", "RU", "IN", "NZ_865", "TH", "LORA_24",
            "UA_433", "UA_868", "MY_433", "MY_919", "SG_923"
    );

    private static final List<String> MODEM_PRESETS = Arrays.asList(
            "LONG_FAST", "LONG_MODERATE", "MEDIUM_SLOW", "MEDIUM_FAST",
            "SHORT_SLOW", "SHORT_FAST", "SHORT_TURBO", "LONG_TURBO"
    );

    private FragmentLoraSettingsBinding binding;
    private SettingsStore store;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLoraSettingsBinding.inflate(inflater, container, false);

        store = new SettingsStore(requireContext());
        SettingsDraft draft = store.load();

        // Dropdowns
        binding.loraRegion.setAdapter(new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                REGIONS));
        binding.loraModemPreset.setAdapter(new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                MODEM_PRESETS));

        // Текущие значения
        String region = draft.getRegion();
        if (region == null || region.isEmpty()) region = "UNSET";
        binding.loraRegion.setText(region, false);

        String preset = draft.getLoraModemPreset();
        if (preset == null || preset.isEmpty()) preset = "LONG_FAST";
        binding.loraModemPreset.setText(preset, false);

        binding.loraHopLimit.setText(String.valueOf(draft.getLoraHopLimit()));
        binding.loraTxPower.setText(String.valueOf(draft.getLoraTxPower()));
        binding.loraTxEnabled.setChecked(draft.isLoraTxEnabled());
        binding.loraIgnoreMqtt.setChecked(draft.isLoraIgnoreMqtt());

        // Кнопки
        binding.loraBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        binding.loraSave.setOnClickListener(v -> saveDraft());

        return binding.getRoot();
    }

    private void saveDraft() {
        SettingsDraft draft = store.load();

        // Валидация числовых полей
        Integer hop = parseInt(binding.loraHopLimit.getText() == null ? "" : binding.loraHopLimit.getText().toString());
        if (hop == null || hop < 1 || hop > 7) {
            Toast.makeText(requireContext(), R.string.lora_toast_bad_hop, Toast.LENGTH_SHORT).show();
            return;
        }
        Integer tx = parseInt(binding.loraTxPower.getText() == null ? "" : binding.loraTxPower.getText().toString());
        if (tx == null || tx < 0 || tx > 30) {
            Toast.makeText(requireContext(), R.string.lora_toast_bad_tx_power, Toast.LENGTH_SHORT).show();
            return;
        }

        draft.setRegion(binding.loraRegion.getText().toString().trim());
        draft.setLoraModemPreset(binding.loraModemPreset.getText().toString().trim());
        draft.setLoraHopLimit(hop);
        draft.setLoraTxPower(tx);
        draft.setLoraTxEnabled(binding.loraTxEnabled.isChecked());
        draft.setLoraIgnoreMqtt(binding.loraIgnoreMqtt.isChecked());

        store.save(draft);
        Toast.makeText(requireContext(), R.string.lora_toast_saved, Toast.LENGTH_SHORT).show();
    }

    private static Integer parseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
