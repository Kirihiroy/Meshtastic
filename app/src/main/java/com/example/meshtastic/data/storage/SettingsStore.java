package com.example.meshtastic.data.storage;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.meshtastic.data.model.ChannelDraft;
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

        draft.setLoraModemPreset(prefs.getString(KEY_LORA_PRESET, "LONG_FAST"));
        draft.setLoraHopLimit(prefs.getInt(KEY_LORA_HOP, 3));
        draft.setLoraTxPower(prefs.getInt(KEY_LORA_TX_POWER, 20));
        draft.setLoraTxEnabled(prefs.getBoolean(KEY_LORA_TX_ENABLED, true));
        draft.setLoraIgnoreMqtt(prefs.getBoolean(KEY_LORA_IGNORE_MQTT, false));

        // Загрузка 8 каналов
        for (int i = 0; i < SettingsDraft.MAX_CHANNELS; i++) {
            ChannelDraft ch = draft.getChannel(i);
            ch.setName(prefs.getString(channelKey(i, "name"), ""));
            ch.setPsk(prefs.getString(channelKey(i, "psk"), ""));
            String role = prefs.getString(channelKey(i, "role"),
                    i == 0 ? ChannelDraft.ROLE_PRIMARY : ChannelDraft.ROLE_DISABLED);
            ch.setRole(role);
        }

        // Миграция: если новые ключи channel_0_* пусты, но есть старые KEY_CHANNEL_NAME/KEY_PSK —
        // переносим их в primary канал (для пользователей со старой формой настроек).
        if (draft.getChannel(0).getName().isEmpty() && prefs.contains(KEY_CHANNEL_NAME)) {
            draft.getChannel(0).setName(prefs.getString(KEY_CHANNEL_NAME, ""));
        }
        if (draft.getChannel(0).getPsk().isEmpty() && prefs.contains(KEY_PSK)) {
            draft.getChannel(0).setPsk(prefs.getString(KEY_PSK, ""));
        }
        return draft;
    }

    public void save(SettingsDraft draft) {
        if (draft == null) return;
        SharedPreferences.Editor edit = prefs.edit()
                .putString(KEY_NODE_NAME, safe(draft.getNodeName()))
                .putString(KEY_REGION, safe(draft.getRegion()))
                // Сохраняем старые ключи тоже — для совместимости с MeshConnectionRepository
                // (применение primary канала на устройство всё ещё читает их).
                .putString(KEY_CHANNEL_NAME, safe(draft.getChannelName()))
                .putString(KEY_PSK, safe(draft.getPsk()))
                .putString(KEY_LORA_PRESET, safe(draft.getLoraModemPreset()))
                .putInt(KEY_LORA_HOP, draft.getLoraHopLimit())
                .putInt(KEY_LORA_TX_POWER, draft.getLoraTxPower())
                .putBoolean(KEY_LORA_TX_ENABLED, draft.isLoraTxEnabled())
                .putBoolean(KEY_LORA_IGNORE_MQTT, draft.isLoraIgnoreMqtt());

        for (int i = 0; i < SettingsDraft.MAX_CHANNELS; i++) {
            ChannelDraft ch = draft.getChannel(i);
            edit.putString(channelKey(i, "name"), safe(ch.getName()));
            edit.putString(channelKey(i, "psk"), safe(ch.getPsk()));
            edit.putString(channelKey(i, "role"), safe(ch.getRole()));
        }
        edit.apply();
    }

    private static String channelKey(int index, String suffix) {
        return "channel_" + index + "_" + suffix;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

