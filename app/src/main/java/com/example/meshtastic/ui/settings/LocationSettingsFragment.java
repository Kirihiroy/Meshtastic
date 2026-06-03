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
import com.example.meshtastic.databinding.FragmentLocationSettingsBinding;

import java.util.Arrays;
import java.util.List;

/** Под-экран «Местоположение» — PositionConfig (черновик). */
public class LocationSettingsFragment extends Fragment {

    private static final List<String> GPS_MODES = Arrays.asList("DISABLED", "ENABLED", "NOT_PRESENT");

    private FragmentLocationSettingsBinding b;
    private SettingsStore store;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentLocationSettingsBinding.inflate(inflater, container, false);
        store = new SettingsStore(requireContext());
        SettingsDraft d = store.loadWithPosition();

        b.locationGpsMode.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_list_item_1, GPS_MODES));
        b.locationGpsMode.setText(d.getPositionGpsMode(), false);
        b.locationBroadcastSecs.setText(String.valueOf(d.getPositionBroadcastSecs()));

        b.locationSwitchFixed.switchLabel.setText(R.string.location_label_fixed);
        b.locationSwitchFixed.switchToggle.setChecked(d.isPositionFixedEnabled());
        b.locationSwitchSmart.switchLabel.setText(R.string.location_label_smart);
        b.locationSwitchSmart.switchToggle.setChecked(d.isPositionSmartEnabled());

        b.locationBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        b.locationSave.setOnClickListener(v -> save());
        return b.getRoot();
    }

    private void save() {
        SettingsDraft d = store.loadWithPosition();
        d.setPositionGpsMode(textOf(b.locationGpsMode));
        try {
            d.setPositionBroadcastSecs(Integer.parseInt(textOf(b.locationBroadcastSecs)));
        } catch (NumberFormatException ignore) {}
        d.setPositionFixedEnabled(b.locationSwitchFixed.switchToggle.isChecked());
        d.setPositionSmartEnabled(b.locationSwitchSmart.switchToggle.isChecked());
        store.savePosition(d);
        Toast.makeText(requireContext(), R.string.location_toast_saved, Toast.LENGTH_SHORT).show();
    }

    private static String textOf(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    @Override public void onDestroyView() { b = null; super.onDestroyView(); }
}
