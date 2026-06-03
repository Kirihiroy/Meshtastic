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
import com.example.meshtastic.databinding.FragmentDisplaySettingsBinding;

import java.util.Arrays;
import java.util.List;

/** Под-экран «Дисплей» — DisplayConfig (черновик). */
public class DisplaySettingsFragment extends Fragment {

    private static final List<String> UNITS = Arrays.asList("METRIC", "IMPERIAL");

    private FragmentDisplaySettingsBinding b;
    private SettingsStore store;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentDisplaySettingsBinding.inflate(inflater, container, false);
        store = new SettingsStore(requireContext());
        SettingsDraft d = store.loadWithDisplay();

        b.displayScreenOnSecs.setText(String.valueOf(d.getDisplayScreenOnSecs()));
        b.displayUnits.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_list_item_1, UNITS));
        b.displayUnits.setText(d.getDisplayUnits(), false);

        b.displaySwitchFlip.switchLabel.setText(R.string.display_label_flip);
        b.displaySwitchFlip.switchToggle.setChecked(d.isDisplayFlipScreen());
        b.displaySwitch12h.switchLabel.setText(R.string.display_label_12h);
        b.displaySwitch12h.switchToggle.setChecked(d.isDisplayUse12hClock());
        b.displaySwitchBold.switchLabel.setText(R.string.display_label_heading_bold);
        b.displaySwitchBold.switchToggle.setChecked(d.isDisplayHeadingBold());

        b.displayBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        b.displaySave.setOnClickListener(v -> save());
        return b.getRoot();
    }

    private void save() {
        SettingsDraft d = store.loadWithDisplay();
        try { d.setDisplayScreenOnSecs(Integer.parseInt(textOf(b.displayScreenOnSecs))); } catch (NumberFormatException ignore) {}
        d.setDisplayUnits(textOf(b.displayUnits));
        d.setDisplayFlipScreen(b.displaySwitchFlip.switchToggle.isChecked());
        d.setDisplayUse12hClock(b.displaySwitch12h.switchToggle.isChecked());
        d.setDisplayHeadingBold(b.displaySwitchBold.switchToggle.isChecked());
        store.saveDisplay(d);
        Toast.makeText(requireContext(), R.string.display_toast_saved, Toast.LENGTH_SHORT).show();
    }

    private static String textOf(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    @Override public void onDestroyView() { b = null; super.onDestroyView(); }
}
