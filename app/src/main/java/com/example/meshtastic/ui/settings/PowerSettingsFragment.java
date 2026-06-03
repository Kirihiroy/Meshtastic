package com.example.meshtastic.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.meshtastic.R;
import com.example.meshtastic.data.model.SettingsDraft;
import com.example.meshtastic.data.storage.SettingsStore;
import com.example.meshtastic.databinding.FragmentPowerSettingsBinding;

/** Под-экран «Питание» — PowerConfig (черновик). */
public class PowerSettingsFragment extends Fragment {

    private FragmentPowerSettingsBinding b;
    private SettingsStore store;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentPowerSettingsBinding.inflate(inflater, container, false);
        store = new SettingsStore(requireContext());
        SettingsDraft d = store.loadWithPower();

        b.powerSwitchSaving.switchLabel.setText(R.string.power_label_saving);
        b.powerSwitchSaving.switchToggle.setChecked(d.isPowerSaving());

        b.powerShutdownSecs.setText(String.valueOf(d.getPowerShutdownAfterSecs()));
        b.powerWaitBtSecs.setText(String.valueOf(d.getPowerWaitBluetoothSecs()));
        b.powerMinWakeSecs.setText(String.valueOf(d.getPowerMinWakeSecs()));

        b.powerBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        b.powerSave.setOnClickListener(v -> save());
        return b.getRoot();
    }

    private void save() {
        SettingsDraft d = store.loadWithPower();
        d.setPowerSaving(b.powerSwitchSaving.switchToggle.isChecked());
        try { d.setPowerShutdownAfterSecs(Integer.parseInt(textOf(b.powerShutdownSecs))); } catch (NumberFormatException ignore) {}
        try { d.setPowerWaitBluetoothSecs(Integer.parseInt(textOf(b.powerWaitBtSecs))); } catch (NumberFormatException ignore) {}
        try { d.setPowerMinWakeSecs(Integer.parseInt(textOf(b.powerMinWakeSecs))); } catch (NumberFormatException ignore) {}
        store.savePower(d);
        Toast.makeText(requireContext(), R.string.power_toast_saved, Toast.LENGTH_SHORT).show();
    }

    private static String textOf(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    @Override public void onDestroyView() { b = null; super.onDestroyView(); }
}
