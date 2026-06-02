package com.example.meshtastic.data.storage;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.meshtastic.data.model.SettingsDraft;

/**
 * Хранилище черновиков настроек в SharedPreferences.
 */
public class SettingsStore {
    private static final String PREFS_NAME = "settings_draft";
    private static final String KEY_NODE_NAME = "node_name";
    private static final String KEY_REGION = "region";
    private static final String KEY_CHANNEL_NAME = "channel_name";
    private static final String KEY_PSK = "psk";

    // LoRa
    private static final String KEY_LORA_PRESET = "lora_modem_preset";
    private static final String KEY_LORA_HOP = "lora_hop_limit";
    private static final String KEY_LORA_TX_POWER = "lora_tx_power";
    private static final String KEY_LORA_TX_ENABLED = "lora_tx_enabled";
    private static final String KEY_LORA_IGNORE_MQTT = "lora_ignore_mqtt";

    private final SharedPreferences prefs;

    public SettingsStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public SettingsDraft load() {
        SettingsDraft draft = new SettingsDraft();
        draft.setNodeName(prefs.getString(KEY_NODE_NAME, ""));
        draft.setRegion(prefs.getString(KEY_REGION, ""));
        draft.setChannelName(prefs.getString(KEY_CHANNEL_NAME, ""));
        draft.setPsk(prefs.getString(KEY_PSK, ""));

        draft.setLoraModemPreset(prefs.getString(KEY_LORA_PRESET, "LONG_FAST"));
        draft.setLoraHopLimit(prefs.getInt(KEY_LORA_HOP, 3));
        draft.setLoraTxPower(prefs.getInt(KEY_LORA_TX_POWER, 20));
        draft.setLoraTxEnabled(prefs.getBoolean(KEY_LORA_TX_ENABLED, true));
        draft.setLoraIgnoreMqtt(prefs.getBoolean(KEY_LORA_IGNORE_MQTT, false));
        return draft;
    }

    public void save(SettingsDraft draft) {
        if (draft == null) return;
        prefs.edit()
                .putString(KEY_NODE_NAME, safe(draft.getNodeName()))
                .putString(KEY_REGION, safe(draft.getRegion()))
                .putString(KEY_CHANNEL_NAME, safe(draft.getChannelName()))
                .putString(KEY_PSK, safe(draft.getPsk()))
                .putString(KEY_LORA_PRESET, safe(draft.getLoraModemPreset()))
                .putInt(KEY_LORA_HOP, draft.getLoraHopLimit())
                .putInt(KEY_LORA_TX_POWER, draft.getLoraTxPower())
                .putBoolean(KEY_LORA_TX_ENABLED, draft.isLoraTxEnabled())
                .putBoolean(KEY_LORA_IGNORE_MQTT, draft.isLoraIgnoreMqtt())
                .apply();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

