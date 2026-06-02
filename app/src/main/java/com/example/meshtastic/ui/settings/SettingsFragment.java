package com.example.meshtastic.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.example.meshtastic.R;
import com.example.meshtastic.databinding.FragmentSettingsBinding;

/**
 * Главный экран настроек — список разделов в стиле оригинального Meshtastic-Android.
 * Сами под-экраны (LoRa, Каналы, Безопасность, ...) пока заглушены Toast'ом
 * до отдельной реализации каждого раздела.
 */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);

        // Группа: LoRa / Каналы / Безопасность
        bindEntry(binding.entryLora.getRoot(), R.drawable.ic_lora, R.string.settings_entry_lora);
        bindEntry(binding.entryChannels.getRoot(), R.drawable.ic_list, R.string.settings_entry_channels);
        bindEntry(binding.entrySecurity.getRoot(), R.drawable.ic_shield, R.string.settings_entry_security);

        // Группа: Настройки устройства
        bindEntry(binding.entryUser.getRoot(), R.drawable.ic_person, R.string.settings_entry_user);
        bindEntry(binding.entryDevice.getRoot(), R.drawable.ic_router, R.string.settings_entry_device);
        bindEntry(binding.entryLocation.getRoot(), R.drawable.ic_location_pin, R.string.settings_entry_location);
        bindEntry(binding.entryPower.getRoot(), R.drawable.ic_plug, R.string.settings_entry_power);
        bindEntry(binding.entryNetwork.getRoot(), R.drawable.ic_wifi, R.string.settings_entry_network);
        bindEntry(binding.entryDisplay.getRoot(), R.drawable.ic_display, R.string.settings_entry_display);
        bindEntry(binding.entryBluetooth.getRoot(), R.drawable.ic_bluetooth, R.string.settings_entry_bluetooth);

        return binding.getRoot();
    }

    /**
     * Настраивает одну строку: иконка, подпись, обработчик клика-заглушка.
     * Заглушка показывает Toast «раздел не реализован» — при добавлении настоящего
     * sub-экрана заменяется на навигацию (NavController.navigate / FragmentManager.replace).
     */
    private void bindEntry(@NonNull View root, @DrawableRes int iconRes, @StringRes int labelRes) {
        ImageView icon = root.findViewById(R.id.entry_icon);
        TextView label = root.findViewById(R.id.entry_label);
        icon.setImageResource(iconRes);
        label.setText(labelRes);
        String labelText = getString(labelRes);
        root.setOnClickListener(v -> Toast.makeText(requireContext(),
                getString(R.string.settings_entry_not_implemented, labelText),
                Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
