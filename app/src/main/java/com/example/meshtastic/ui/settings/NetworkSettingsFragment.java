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
import com.example.meshtastic.databinding.FragmentNetworkSettingsBinding;

/** Под-экран «Сеть» — NetworkConfig (черновик). */
public class NetworkSettingsFragment extends Fragment {

    private FragmentNetworkSettingsBinding b;
    private SettingsStore store;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentNetworkSettingsBinding.inflate(inflater, container, false);
        store = new SettingsStore(requireContext());
        SettingsDraft d = store.loadWithNetwork();

        b.networkSwitchWifi.switchLabel.setText(R.string.network_label_wifi_enabled);
        b.networkSwitchWifi.switchToggle.setChecked(d.isNetworkWifiEnabled());
        b.networkSwitchEth.switchLabel.setText(R.string.network_label_eth_enabled);
        b.networkSwitchEth.switchToggle.setChecked(d.isNetworkEthEnabled());

        b.networkWifiSsid.setText(d.getNetworkWifiSsid());
        b.networkWifiPsk.setText(d.getNetworkWifiPsk());
        b.networkNtp.setText(d.getNetworkNtpServer());

        b.networkBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        b.networkSave.setOnClickListener(v -> save());
        return b.getRoot();
    }

    private void save() {
        SettingsDraft d = store.loadWithNetwork();
        d.setNetworkWifiEnabled(b.networkSwitchWifi.switchToggle.isChecked());
        d.setNetworkEthEnabled(b.networkSwitchEth.switchToggle.isChecked());
        d.setNetworkWifiSsid(textOf(b.networkWifiSsid));
        d.setNetworkWifiPsk(textOf(b.networkWifiPsk));
        d.setNetworkNtpServer(textOf(b.networkNtp));
        store.saveNetwork(d);
        Toast.makeText(requireContext(), R.string.network_toast_saved, Toast.LENGTH_SHORT).show();
    }

    private static String textOf(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    @Override public void onDestroyView() { b = null; super.onDestroyView(); }
}
