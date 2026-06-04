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

    // Безопасность
    private static final String KEY_ADMIN_PREFIX = "admin_key_";
    private static final String KEY_IS_MANAGED = "is_managed";

    // Пользователь
    private static final String KEY_USER_SHORT_NAME = "user_short_name";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_USER_LICENSED = "user_is_licensed";

    /** Загрузить только секцию «Пользователь» поверх базового черновика. */
    public SettingsDraft loadWithUser() {
        SettingsDraft draft = load();
        draft.setUserShortName(prefs.getString(KEY_USER_SHORT_NAME, ""));
        draft.setUserRole(prefs.getString(KEY_USER_ROLE, "CLIENT"));
        draft.setUserLicensed(prefs.getBoolean(KEY_USER_LICENSED, false));
        return draft;
    }

    /** Сохранить только секцию «Пользователь». */
    public void saveUser(SettingsDraft draft) {
        if (draft == null) return;
        prefs.edit()
                .putString(KEY_NODE_NAME, safe(draft.getNodeName())) // longName
                .putString(KEY_USER_SHORT_NAME, safe(draft.getUserShortName()))
                .putString(KEY_USER_ROLE, safe(draft.getUserRole()))
                .putBoolean(KEY_USER_LICENSED, draft.isUserLicensed())
                .apply();
    }

    // ─────────────────────────────────────────────────────────────
    // Секционные методы для остальных под-экранов меню «Настройки».
    // Каждая пара loadWithX / saveX работает только со своей подсекцией,
    // чтобы редактирование одного раздела не затирало черновики других.
    // ─────────────────────────────────────────────────────────────

    // Устройство
    private static final String K_DEV_REBROADCAST = "dev_rebroadcast_mode";
    private static final String K_DEV_NODEINFO_SECS = "dev_nodeinfo_broadcast_secs";
    private static final String K_DEV_SERIAL = "dev_serial_enabled";
    private static final String K_DEV_DEBUG_LOG = "dev_debug_log_enabled";
    private static final String K_DEV_LED_HEARTBEAT_DISABLED = "dev_led_heartbeat_disabled";

    public SettingsDraft loadWithDevice() {
        SettingsDraft d = load();
        d.setDeviceRebroadcastMode(prefs.getString(K_DEV_REBROADCAST, "ALL"));
        d.setDeviceNodeInfoBroadcastSecs(prefs.getInt(K_DEV_NODEINFO_SECS, 10800));
        d.setDeviceSerialEnabled(prefs.getBoolean(K_DEV_SERIAL, false));
        d.setDeviceDebugLogEnabled(prefs.getBoolean(K_DEV_DEBUG_LOG, false));
        d.setDeviceLedHeartbeatDisabled(prefs.getBoolean(K_DEV_LED_HEARTBEAT_DISABLED, false));
        return d;
    }

    public void saveDevice(SettingsDraft d) {
        if (d == null) return;
        prefs.edit()
                .putString(K_DEV_REBROADCAST, safe(d.getDeviceRebroadcastMode()))
                .putInt(K_DEV_NODEINFO_SECS, d.getDeviceNodeInfoBroadcastSecs())
                .putBoolean(K_DEV_SERIAL, d.isDeviceSerialEnabled())
                .putBoolean(K_DEV_DEBUG_LOG, d.isDeviceDebugLogEnabled())
                .putBoolean(K_DEV_LED_HEARTBEAT_DISABLED, d.isDeviceLedHeartbeatDisabled())
                .apply();
    }

    // Местоположение
    private static final String K_POS_GPS_MODE = "pos_gps_mode";
    private static final String K_POS_BROADCAST_SECS = "pos_broadcast_secs";
    private static final String K_POS_FIXED = "pos_fixed_enabled";
    private static final String K_POS_SMART = "pos_smart_enabled";

    public SettingsDraft loadWithPosition() {
        SettingsDraft d = load();
        d.setPositionGpsMode(prefs.getString(K_POS_GPS_MODE, "ENABLED"));
        d.setPositionBroadcastSecs(prefs.getInt(K_POS_BROADCAST_SECS, 900));
        d.setPositionFixedEnabled(prefs.getBoolean(K_POS_FIXED, false));
        d.setPositionSmartEnabled(prefs.getBoolean(K_POS_SMART, true));
        return d;
    }

    public void savePosition(SettingsDraft d) {
        if (d == null) return;
        prefs.edit()
                .putString(K_POS_GPS_MODE, safe(d.getPositionGpsMode()))
                .putInt(K_POS_BROADCAST_SECS, d.getPositionBroadcastSecs())
                .putBoolean(K_POS_FIXED, d.isPositionFixedEnabled())
                .putBoolean(K_POS_SMART, d.isPositionSmartEnabled())
                .apply();
    }

    // Питание
    private static final String K_PWR_SAVING = "pwr_is_saving";
    private static final String K_PWR_SHUTDOWN_SECS = "pwr_shutdown_after_secs";
    private static final String K_PWR_WAIT_BT_SECS = "pwr_wait_bluetooth_secs";
    private static final String K_PWR_MIN_WAKE_SECS = "pwr_min_wake_secs";

    public SettingsDraft loadWithPower() {
        SettingsDraft d = load();
        d.setPowerSaving(prefs.getBoolean(K_PWR_SAVING, false));
        d.setPowerShutdownAfterSecs(prefs.getInt(K_PWR_SHUTDOWN_SECS, 0));
        d.setPowerWaitBluetoothSecs(prefs.getInt(K_PWR_WAIT_BT_SECS, 60));
        d.setPowerMinWakeSecs(prefs.getInt(K_PWR_MIN_WAKE_SECS, 10));
        return d;
    }

    public void savePower(SettingsDraft d) {
        if (d == null) return;
        prefs.edit()
                .putBoolean(K_PWR_SAVING, d.isPowerSaving())
                .putInt(K_PWR_SHUTDOWN_SECS, d.getPowerShutdownAfterSecs())
                .putInt(K_PWR_WAIT_BT_SECS, d.getPowerWaitBluetoothSecs())
                .putInt(K_PWR_MIN_WAKE_SECS, d.getPowerMinWakeSecs())
                .apply();
    }

    // Сеть
    private static final String K_NET_WIFI_ENABLED = "net_wifi_enabled";
    private static final String K_NET_WIFI_SSID = "net_wifi_ssid";
    private static final String K_NET_WIFI_PSK = "net_wifi_psk";
    private static final String K_NET_NTP = "net_ntp_server";
    private static final String K_NET_ETH_ENABLED = "net_eth_enabled";

    public SettingsDraft loadWithNetwork() {
        SettingsDraft d = load();
        d.setNetworkWifiEnabled(prefs.getBoolean(K_NET_WIFI_ENABLED, false));
        d.setNetworkWifiSsid(prefs.getString(K_NET_WIFI_SSID, ""));
        d.setNetworkWifiPsk(prefs.getString(K_NET_WIFI_PSK, ""));
        d.setNetworkNtpServer(prefs.getString(K_NET_NTP, "0.pool.ntp.org"));
        d.setNetworkEthEnabled(prefs.getBoolean(K_NET_ETH_ENABLED, false));
        return d;
    }

    public void saveNetwork(SettingsDraft d) {
        if (d == null) return;
        prefs.edit()
                .putBoolean(K_NET_WIFI_ENABLED, d.isNetworkWifiEnabled())
                .putString(K_NET_WIFI_SSID, safe(d.getNetworkWifiSsid()))
                .putString(K_NET_WIFI_PSK, safe(d.getNetworkWifiPsk()))
                .putString(K_NET_NTP, safe(d.getNetworkNtpServer()))
                .putBoolean(K_NET_ETH_ENABLED, d.isNetworkEthEnabled())
                .apply();
    }

    // Дисплей
    private static final String K_DSP_SCREEN_ON = "dsp_screen_on_secs";
    private static final String K_DSP_UNITS = "dsp_units";
    private static final String K_DSP_FLIP = "dsp_flip_screen";
    private static final String K_DSP_12H = "dsp_use_12h_clock";
    private static final String K_DSP_HEADING_BOLD = "dsp_heading_bold";

    public SettingsDraft loadWithDisplay() {
        SettingsDraft d = load();
        d.setDisplayScreenOnSecs(prefs.getInt(K_DSP_SCREEN_ON, 30));
        d.setDisplayUnits(prefs.getString(K_DSP_UNITS, "METRIC"));
        d.setDisplayFlipScreen(prefs.getBoolean(K_DSP_FLIP, false));
        d.setDisplayUse12hClock(prefs.getBoolean(K_DSP_12H, false));
        d.setDisplayHeadingBold(prefs.getBoolean(K_DSP_HEADING_BOLD, false));
        return d;
    }

    public void saveDisplay(SettingsDraft d) {
        if (d == null) return;
        prefs.edit()
                .putInt(K_DSP_SCREEN_ON, d.getDisplayScreenOnSecs())
                .putString(K_DSP_UNITS, safe(d.getDisplayUnits()))
                .putBoolean(K_DSP_FLIP, d.isDisplayFlipScreen())
                .putBoolean(K_DSP_12H, d.isDisplayUse12hClock())
                .putBoolean(K_DSP_HEADING_BOLD, d.isDisplayHeadingBold())
                .apply();
    }

    // Bluetooth
    private static final String K_BT_ENABLED = "bt_enabled";
    private static final String K_BT_MODE = "bt_mode";
    private static final String K_BT_FIXED_PIN = "bt_fixed_pin";

    public SettingsDraft loadWithBluetooth() {
        SettingsDraft d = load();
        d.setBluetoothEnabled(prefs.getBoolean(K_BT_ENABLED, true));
        d.setBluetoothMode(prefs.getString(K_BT_MODE, "RANDOM_PIN"));
        d.setBluetoothFixedPin(prefs.getInt(K_BT_FIXED_PIN, 123456));
        return d;
    }

    public void saveBluetooth(SettingsDraft d) {
        if (d == null) return;
        prefs.edit()
                .putBoolean(K_BT_ENABLED, d.isBluetoothEnabled())
                .putString(K_BT_MODE, safe(d.getBluetoothMode()))
                .putInt(K_BT_FIXED_PIN, d.getBluetoothFixedPin())
                .apply();
    }

    /** Загрузить только секцию «Безопасность» из уже-загруженного черновика. */
    public SettingsDraft loadWithSecurity() {
        SettingsDraft draft = load();
        for (int i = 0; i < SettingsDraft.MAX_ADMIN_KEYS; i++) {
            draft.setAdminKey(i, prefs.getString(KEY_ADMIN_PREFIX + i, ""));
        }
        draft.setManaged(prefs.getBoolean(KEY_IS_MANAGED, false));
        return draft;
    }

    /** Сохранить только секцию «Безопасность» поверх существующих преф. */
    public void saveSecurity(SettingsDraft draft) {
        if (draft == null) return;
        SharedPreferences.Editor edit = prefs.edit();
        for (int i = 0; i < SettingsDraft.MAX_ADMIN_KEYS; i++) {
            edit.putString(KEY_ADMIN_PREFIX + i, safe(draft.getAdminKey(i)));
        }
        edit.putBoolean(KEY_IS_MANAGED, draft.isManaged());
        edit.apply();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

