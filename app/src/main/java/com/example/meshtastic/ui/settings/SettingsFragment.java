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

import java.util.function.Supplier;

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
        bindEntry(binding.entryLora.getRoot(), R.drawable.ic_lora,
                R.string.settings_entry_lora, LoRaSettingsFragment::new);
        bindEntry(binding.entryChannels.getRoot(), R.drawable.ic_list,
                R.string.settings_entry_channels, ChannelsSettingsFragment::new);
        bindEntry(binding.entrySecurity.getRoot(), R.drawable.ic_shield,
                R.string.settings_entry_security, null);

        // Группа: Настройки устройства
        bindEntry(binding.entryUser.getRoot(), R.drawable.ic_person,
                R.string.settings_entry_user, null);
        bindEntry(binding.entryDevice.getRoot(), R.drawable.ic_router,
                R.string.settings_entry_device, null);
        bindEntry(binding.entryLocation.getRoot(), R.drawable.ic_location_pin,
                R.string.settings_entry_location, null);
        bindEntry(binding.entryPower.getRoot(), R.drawable.ic_plug,
                R.string.settings_entry_power, null);
        bindEntry(binding.entryNetwork.getRoot(), R.drawable.ic_wifi,
                R.string.settings_entry_network, null);
        bindEntry(binding.entryDisplay.getRoot(), R.drawable.ic_display,
                R.string.settings_entry_display, null);
        bindEntry(binding.entryBluetooth.getRoot(), R.drawable.ic_bluetooth,
                R.string.settings_entry_bluetooth, null);

        return binding.getRoot();
    }

    /**
     * Настраивает одну строку меню. Если {@code fragmentFactory} задан — клик
     * открывает соответствующий под-экран через FragmentTransaction с back stack.
     * Если {@code null} — показывает Toast «раздел не реализован» (старое поведение).
     */
    private void bindEntry(@NonNull View root,
                           @DrawableRes int iconRes,
                           @StringRes int labelRes,
                           @Nullable Supplier<Fragment> fragmentFactory) {
        ImageView icon = root.findViewById(R.id.entry_icon);
        TextView label = root.findViewById(R.id.entry_label);
        icon.setImageResource(iconRes);
        label.setText(labelRes);
        String labelText = getString(labelRes);
        root.setOnClickListener(v -> {
            if (fragmentFactory != null) {
                openSubScreen(fragmentFactory.get());
            } else {
                Toast.makeText(requireContext(),
                        getString(R.string.settings_entry_not_implemented, labelText),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openSubScreen(@NonNull Fragment fragment) {
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
