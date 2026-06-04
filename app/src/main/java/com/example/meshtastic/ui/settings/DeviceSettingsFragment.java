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
import com.example.meshtastic.databinding.FragmentDeviceSettingsBinding;

import java.util.Arrays;
import java.util.List;

/** Под-экран «Устройство» — DeviceConfig (черновик). */
public class DeviceSettingsFragment extends Fragment {

    private static final List<String> REBROADCAST_MODES = Arrays.asList(
            "ALL", "ALL_SKIP_DECODING", "LOCAL_ONLY", "KNOWN_ONLY", "NONE", "CORE_PORTNUMS_ONLY");

    private FragmentDeviceSettingsBinding b;
    private SettingsStore store;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentDeviceSettingsBinding.inflate(inflater, container, false);
        store = new SettingsStore(requireContext());
        SettingsDraft d = store.loadWithDevice();

        b.deviceRebroadcast.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_list_item_1, REBROADCAST_MODES));
        b.deviceRebroadcast.setText(d.getDeviceRebroadcastMode(), false);
        b.deviceNodeinfoSecs.setText(String.valueOf(d.getDeviceNodeInfoBroadcastSecs()));

        b.deviceSwitchSerial.switchLabel.setText(R.string.device_label_serial);
        b.deviceSwitchSerial.switchToggle.setChecked(d.isDeviceSerialEnabled());
        b.deviceSwitchDebug.switchLabel.setText(R.string.device_label_debug_log);
        b.deviceSwitchDebug.switchToggle.setChecked(d.isDeviceDebugLogEnabled());
        b.deviceSwitchLed.switchLabel.setText(R.string.device_label_led_off);
        b.deviceSwitchLed.switchToggle.setChecked(d.isDeviceLedHeartbeatDisabled());

        b.deviceBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        b.deviceSave.setOnClickListener(v -> save());
        return b.getRoot();
    }

    private void save() {
        SettingsDraft d = store.loadWithDevice();
        d.setDeviceRebroadcastMode(textOf(b.deviceRebroadcast));
        try {
            d.setDeviceNodeInfoBroadcastSecs(Integer.parseInt(textOf(b.deviceNodeinfoSecs)));
        } catch (NumberFormatException ignore) {
            // Поле пустое или мусор — оставляем дефолт из draft.
        }
        d.setDeviceSerialEnabled(b.deviceSwitchSerial.switchToggle.isChecked());
        d.setDeviceDebugLogEnabled(b.deviceSwitchDebug.switchToggle.isChecked());
        d.setDeviceLedHeartbeatDisabled(b.deviceSwitchLed.switchToggle.isChecked());
        store.saveDevice(d);
        Toast.makeText(requireContext(), R.string.device_toast_saved, Toast.LENGTH_SHORT).show();
    }

    private static String textOf(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    @Override public void onDestroyView() { b = null; super.onDestroyView(); }
}
