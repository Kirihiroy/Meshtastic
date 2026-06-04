package com.example.meshtastic.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.meshtastic.R;
import com.example.meshtastic.data.model.SettingsDraft;
import com.example.meshtastic.data.storage.SettingsStore;
import com.example.meshtastic.databinding.FragmentBluetoothSettingsBinding;

import java.util.Arrays;
import java.util.List;

/** Под-экран «Bluetooth» — BluetoothConfig (черновик). */
public class BluetoothSettingsFragment extends Fragment {

    private static final List<String> MODES = Arrays.asList("RANDOM_PIN", "FIXED_PIN", "NO_PIN");

    private FragmentBluetoothSettingsBinding b;
    private SettingsStore store;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentBluetoothSettingsBinding.inflate(inflater, container, false);
        store = new SettingsStore(requireContext());
        SettingsDraft d = store.loadWithBluetooth();

        b.bluetoothSwitchEnabled.switchLabel.setText(R.string.bluetooth_label_enabled);
        b.bluetoothSwitchEnabled.switchToggle.setChecked(d.isBluetoothEnabled());

        b.bluetoothMode.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_list_item_1, MODES));
        b.bluetoothMode.setText(d.getBluetoothMode(), false);
        b.bluetoothFixedPin.setText(String.format(java.util.Locale.ROOT, "%06d", d.getBluetoothFixedPin()));

        b.bluetoothBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        b.bluetoothSave.setOnClickListener(v -> save());
        return b.getRoot();
    }

    private void save() {
        String pin = textOf(b.bluetoothFixedPin);
        if (pin.length() != 6 || !pin.matches("\\d{6}")) {
            Toast.makeText(requireContext(), R.string.bluetooth_toast_bad_pin, Toast.LENGTH_SHORT).show();
            return;
        }
        SettingsDraft d = store.loadWithBluetooth();
        d.setBluetoothEnabled(b.bluetoothSwitchEnabled.switchToggle.isChecked());
        d.setBluetoothMode(textOf(b.bluetoothMode));
        try { d.setBluetoothFixedPin(Integer.parseInt(pin)); } catch (NumberFormatException ignore) {}
        store.saveBluetooth(d);
        Toast.makeText(requireContext(), R.string.bluetooth_toast_saved, Toast.LENGTH_SHORT).show();
    }

    private static String textOf(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    @Override public void onDestroyView() { b = null; super.onDestroyView(); }
}
