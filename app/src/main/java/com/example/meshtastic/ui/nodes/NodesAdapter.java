package com.example.meshtastic.ui.nodes;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meshtastic.R;
import com.example.meshtastic.data.model.NodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Адаптер списка нод в стиле оригинального Meshtastic-Android:
 * цветной бейдж, иконка PSK, метрики PWR/ChUtil/AirUtil, hw-model/role/!node-id.
 */
class NodesAdapter extends ListAdapter<NodeInfo, NodesAdapter.VH> {

    /** Источник правды — полный список из репозитория. */
    private List<NodeInfo> sourceList = new ArrayList<>();
    /** Текущий текст фильтра. */
    private String filterText = "";

    private static final DiffUtil.ItemCallback<NodeInfo> DIFF = new DiffUtil.ItemCallback<NodeInfo>() {
        @Override
        public boolean areItemsTheSame(@NonNull NodeInfo a, @NonNull NodeInfo b) {
            return a.getNodeNum() == b.getNodeNum();
        }

        @Override
        public boolean areContentsTheSame(@NonNull NodeInfo a, @NonNull NodeInfo b) {
            return a.equals(b);
        }
    };

    NodesAdapter() {
        super(DIFF);
    }

    /** Полное обновление источника + применение текущего фильтра. */
    void setSource(List<NodeInfo> src) {
        sourceList = src == null ? new ArrayList<>() : new ArrayList<>(src);
        applyFilter();
    }

    /** Обновить только фильтр и переподать список. */
    void setFilter(String query) {
        filterText = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        applyFilter();
    }

    private void applyFilter() {
        if (filterText.isEmpty()) {
            submitList(new ArrayList<>(sourceList));
            return;
        }
        List<NodeInfo> filtered = new ArrayList<>();
        for (NodeInfo n : sourceList) {
            if (matches(n, filterText)) filtered.add(n);
        }
        submitList(filtered);
    }

    int getVisibleCount() {
        return getCurrentList().size();
    }

    int getTotalCount() {
        return sourceList.size();
    }

    private static boolean matches(NodeInfo n, String q) {
        if (n.getLongName() != null && n.getLongName().toLowerCase(Locale.getDefault()).contains(q)) return true;
        if (n.getShortName() != null && n.getShortName().toLowerCase(Locale.getDefault()).contains(q)) return true;
        if (n.getUserId() != null && n.getUserId().toLowerCase(Locale.getDefault()).contains(q)) return true;
        if (n.getHwModel() != null && n.getHwModel().toLowerCase(Locale.getDefault()).contains(q)) return true;
        if (n.getRole() != null && n.getRole().toLowerCase(Locale.getDefault()).contains(q)) return true;
        return shortIdHex(n.getNodeNum()).contains(q);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_node, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        NodeInfo n = getItem(position);
        Context ctx = h.itemView.getContext();

        // Бейдж — короткое имя или короткий ID
        String badgeText = n.getShortName();
        if (badgeText == null || badgeText.isEmpty()) {
            badgeText = shortIdHex(n.getNodeNum());
            if (badgeText.length() > 4) badgeText = badgeText.substring(badgeText.length() - 4);
        }
        h.badge.setText(badgeText);
        applyBadgeColor(h.badge, n.getNodeNum());

        // PSK lock — у нас по умолчанию шифрование считается включённым
        h.lock.setImageResource(n.isHasPsk() ? R.drawable.ic_lock_closed : R.drawable.ic_lock_closed);
        int lockColor = ContextCompat.getColor(ctx,
                n.isHasPsk() ? R.color.node_lock_green : R.color.app_on_surface_muted);
        h.lock.setColorFilter(lockColor);

        // Имя
        h.title.setText(displayName(n, ctx));

        // Last seen
        h.lastSeen.setText(formatLastSeen(ctx, n.getLastHeard()));

        // MQTT-off иконка показывается, если узел НЕ через MQTT (как в оригинале — облако перечёркнуто)
        h.mqttOff.setVisibility(n.isViaMqtt() ? View.GONE : View.VISIBLE);

        // Метрики мощности: иконка батареи + текст
        // Возможные комбинации:
        //   voltage + battery%  → "🔋 PWR 4,35V · 85%"     (синий)
        //   только voltage      → "🔋 PWR 4,35V"           (синий)
        //   только battery%     → "🔋 0%"                  (приглушённый)
        //   ничего              → "🔋 0%"                  (placeholder, приглушённый)
        boolean hasVoltage = n.getVoltage() > 0f;
        boolean hasBattery = n.getBatteryLevel() >= 0;

        // Второй (отдельный) блок батареи в новом дизайне всегда скрыт —
        // батарея либо включена в PWR-строку, либо PWR-строка показывает только %.
        h.batteryIcon.setVisibility(View.GONE);
        h.battery.setVisibility(View.GONE);

        h.powerIcon.setVisibility(View.VISIBLE);
        h.voltage.setVisibility(View.VISIBLE);
        if (hasVoltage && hasBattery) {
            h.voltage.setText(ctx.getString(
                    R.string.node_pwr_with_percent_format,
                    n.getVoltage(),
                    n.getBatteryLevel()));
            tintPowerRow(h, ContextCompat.getColor(ctx, R.color.node_metric_blue));
        } else if (hasVoltage) {
            h.voltage.setText(ctx.getString(R.string.node_pwr_format, n.getVoltage()));
            tintPowerRow(h, ContextCompat.getColor(ctx, R.color.node_metric_blue));
        } else if (hasBattery) {
            h.voltage.setText(ctx.getString(
                    R.string.node_battery_percent_format, n.getBatteryLevel()));
            tintPowerRow(h, ContextCompat.getColor(ctx, R.color.app_on_surface_muted));
        } else {
            // placeholder (как в оригинальном Meshtastic-Android, когда нода молчит)
            h.voltage.setText(ctx.getString(R.string.node_battery_percent_format, 0));
            tintPowerRow(h, ContextCompat.getColor(ctx, R.color.app_on_surface_muted));
        }

        if (n.getAltitude() != null) {
            h.altitude.setText(ctx.getString(R.string.node_altitude_format, n.getAltitude()));
            h.altitude.setVisibility(View.VISIBLE);
            h.altitudeIcon.setVisibility(View.VISIBLE);
        } else {
            h.altitude.setVisibility(View.GONE);
            h.altitudeIcon.setVisibility(View.GONE);
        }

        // ChUtil / AirUtil — показываем всегда, "—" если нет данных
        h.chUtil.setText(ctx.getString(R.string.node_chutil_format,
                n.getChUtilization() >= 0 ? n.getChUtilization() : 0f));
        h.airUtil.setText(ctx.getString(R.string.node_airutil_format,
                n.getAirUtilTx() >= 0 ? n.getAirUtilTx() : 0f));

        // HW model / role / node-id
        h.hwModel.setText(orDash(ctx, n.getHwModel()).toUpperCase(Locale.ROOT));
        h.role.setText(orDash(ctx, n.getRole()).toUpperCase(Locale.ROOT));
        h.nodeId.setText(ctx.getString(R.string.node_id_format, shortIdHex(n.getNodeNum())));
    }

    /** Перекрашивает иконку батареи + текст PWR одним цветом. */
    private static void tintPowerRow(VH h, int color) {
        h.powerIcon.setColorFilter(color);
        h.voltage.setTextColor(color);
    }

    private static String orDash(Context ctx, String v) {
        return (v == null || v.isEmpty()) ? ctx.getString(R.string.node_value_dash) : v;
    }

    private static String displayName(NodeInfo n, Context ctx) {
        if (n.getLongName() != null && !n.getLongName().isEmpty()) return n.getLongName();
        if (n.getShortName() != null && !n.getShortName().isEmpty()) {
            return ctx.getString(R.string.node_default_name_format, n.getShortName());
        }
        return ctx.getString(R.string.node_default_name_format, shortIdHex(n.getNodeNum()));
    }

    /** Последние 8 hex-символов nodeNum, нижний регистр (как в оригинальном Meshtastic). */
    private static String shortIdHex(long nodeNum) {
        return String.format(Locale.ROOT, "%08x", nodeNum & 0xFFFFFFFFL);
    }

    /**
     * Цвет бейджа — стабильно из nodeNum.
     * HSV: тон берём из хеша, насыщенность/яркость фиксируем — даёт яркие пастельные цвета.
     */
    private void applyBadgeColor(TextView badge, long nodeNum) {
        float hue = (Math.abs((int) (nodeNum ^ (nodeNum >>> 32))) % 360);
        float[] hsv = new float[]{hue, 0.45f, 0.85f};
        int color = Color.HSVToColor(hsv);
        GradientDrawable bg = (GradientDrawable) ContextCompat.getDrawable(
                badge.getContext(), R.drawable.bg_node_badge);
        if (bg != null) {
            bg = (GradientDrawable) bg.mutate();
            bg.setColor(color);
            badge.setBackground(bg);
        }
    }

    private static String formatLastSeen(Context ctx, long lastHeardSec) {
        if (lastHeardSec <= 0) return ctx.getString(R.string.last_seen_unknown);
        long nowSec = System.currentTimeMillis() / 1000L;
        long deltaSec = nowSec - lastHeardSec;
        if (deltaSec < 60) return ctx.getString(R.string.last_seen_just_now);
        long deltaMin = deltaSec / 60;
        if (deltaMin < 60) return ctx.getString(R.string.last_seen_min_format, (int) deltaMin);
        long deltaHr = deltaMin / 60;
        if (deltaHr < 24) return ctx.getString(R.string.last_seen_hr_format, (int) deltaHr);
        long deltaDay = deltaHr / 24;
        return ctx.getString(R.string.last_seen_day_format, (int) deltaDay);
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView badge;
        final ImageView lock;
        final TextView title;
        final TextView lastSeen;
        final ImageView mqttOff;
        final ImageView powerIcon;
        final TextView voltage;
        final ImageView batteryIcon;
        final TextView battery;
        final ImageView altitudeIcon;
        final TextView altitude;
        final TextView chUtil;
        final TextView airUtil;
        final TextView hwModel;
        final TextView role;
        final TextView nodeId;

        VH(@NonNull View v) {
            super(v);
            badge = v.findViewById(R.id.node_badge);
            lock = v.findViewById(R.id.node_lock);
            title = v.findViewById(R.id.node_title);
            lastSeen = v.findViewById(R.id.node_last_seen);
            mqttOff = v.findViewById(R.id.node_mqtt_off);
            powerIcon = v.findViewById(R.id.node_power_icon);
            voltage = v.findViewById(R.id.node_voltage);
            batteryIcon = v.findViewById(R.id.node_battery_icon);
            battery = v.findViewById(R.id.node_battery);
            altitudeIcon = v.findViewById(R.id.node_altitude_icon);
            altitude = v.findViewById(R.id.node_altitude);
            chUtil = v.findViewById(R.id.node_chutil);
            airUtil = v.findViewById(R.id.node_airutil);
            hwModel = v.findViewById(R.id.node_hw_model);
            role = v.findViewById(R.id.node_role);
            nodeId = v.findViewById(R.id.node_id);
        }
    }
}
