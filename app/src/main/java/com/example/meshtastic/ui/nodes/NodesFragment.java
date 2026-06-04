package com.example.meshtastic.ui.nodes;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.meshtastic.R;
import com.example.meshtastic.data.model.DeviceStatus;
import com.example.meshtastic.data.model.NodeInfo;
import com.example.meshtastic.data.repository.MeshConnectionRepository;
import com.example.meshtastic.databinding.FragmentNodesBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Список нод в стиле оригинального Meshtastic-Android.
 * Поддерживает фильтрацию по имени/ID/hw-model/role и сортировку (по last_heard).
 */
public class NodesFragment extends Fragment {

    /** Окно "онлайн" — нода считается онлайн, если её слышали за последние 2 часа. */
    private static final long ONLINE_WINDOW_SEC = 2 * 60 * 60L;

    private FragmentNodesBinding binding;
    private NodesAdapter adapter;
    private List<NodeInfo> latestSource = new ArrayList<>();
    private boolean sortByLastHeardAsc = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentNodesBinding.inflate(inflater, container, false);

        binding.nodesRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new NodesAdapter();
        binding.nodesRecycler.setAdapter(adapter);

        binding.nodesFilter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                adapter.setFilter(s == null ? "" : s.toString());
                updateCounter();
                updateEmptyState();
            }
        });

        binding.nodesSort.setOnClickListener(v -> {
            sortByLastHeardAsc = !sortByLastHeardAsc;
            applySort();
        });

        MeshConnectionRepository repo = MeshConnectionRepository.getInstance(requireContext());
        repo.getNodes().observe(getViewLifecycleOwner(), this::renderNodes);
        repo.getDeviceStatus().observe(getViewLifecycleOwner(), this::renderStatusBanner);

        return binding.getRoot();
    }

    /** Заполнить статус-баннер «как в Панели состояния» — теперь компактно вверху Нод. */
    private void renderStatusBanner(DeviceStatus ds) {
        if (binding == null || ds == null) return;

        // Цвет индикатора-точки + основной текст
        String stateName = ds.getState();
        String statusText = ds.getStatusText();
        int dotColor;
        if ("CONNECTED".equals(stateName)) {
            dotColor = 0xFF4CAF50; // зелёный
        } else if ("CONNECTING".equals(stateName) || "SCANNING".equals(stateName)) {
            dotColor = 0xFFFFC107; // жёлтый
        } else if ("ERROR".equals(stateName)) {
            dotColor = 0xFFE53935; // красный
        } else {
            dotColor = ContextCompat.getColor(requireContext(), R.color.app_on_surface_muted);
        }
        // Тинтуем фон точки (shape — становится GradientDrawable)
        GradientDrawable dot = (GradientDrawable) ContextCompat.getDrawable(
                requireContext(), R.drawable.bg_node_badge);
        if (dot != null) {
            dot = (GradientDrawable) dot.mutate();
            dot.setColor(dotColor);
            dot.setCornerRadius(dpToPx(5));
            binding.nodesStatusDot.setBackground(dot);
        }

        binding.nodesStatusText.setText(
                statusText != null && !statusText.isEmpty()
                        ? statusText
                        : (stateName != null ? stateName : getString(R.string.nodes_status_dash)));

        // Last RX время
        Long lastRxAt = ds.getLastRxAt();
        if (lastRxAt != null && lastRxAt > 0) {
            binding.nodesStatusLastRx.setText(formatLastRxRelative(lastRxAt));
        } else {
            binding.nodesStatusLastRx.setText(getString(R.string.nodes_status_dash));
        }

        // Строка метрик: Батарея · SNR · firmware
        StringBuilder metrics = new StringBuilder();
        if (ds.getBatteryPercent() != null && ds.getBatteryPercent() >= 0) {
            metrics.append(getString(R.string.nodes_status_battery_label))
                    .append(' ').append(ds.getBatteryPercent()).append('%');
        }
        if (ds.getSnr() != null && ds.getSnr() != 0f) {
            if (metrics.length() > 0) metrics.append(" · ");
            metrics.append(getString(R.string.nodes_status_snr_label))
                    .append(' ').append(String.format(Locale.ROOT, "%.1f", ds.getSnr()));
        }
        if (ds.getFirmwareVersion() != null && !ds.getFirmwareVersion().isEmpty()) {
            if (metrics.length() > 0) metrics.append(" · ");
            metrics.append("v").append(ds.getFirmwareVersion());
        }
        if (metrics.length() == 0) {
            binding.nodesStatusMetrics.setText(R.string.nodes_status_dash);
        } else {
            binding.nodesStatusMetrics.setText(metrics.toString());
        }
    }

    /** Форматирует "последний RX был X сек/мин/ч назад" компактно. */
    private String formatLastRxRelative(long lastRxAtMs) {
        long deltaMs = System.currentTimeMillis() - lastRxAtMs;
        if (deltaMs < 60_000L) return "RX " + (deltaMs / 1000L) + " с назад";
        if (deltaMs < 3_600_000L) return "RX " + (deltaMs / 60_000L) + " мин назад";
        if (deltaMs < 86_400_000L) return "RX " + (deltaMs / 3_600_000L) + " ч назад";
        return "RX " + (deltaMs / 86_400_000L) + " дн назад";
    }

    private float dpToPx(int dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private void renderNodes(List<NodeInfo> list) {
        if (binding == null) return;
        latestSource = list == null ? Collections.emptyList() : new ArrayList<>(list);
        applySort();
    }

    private void applySort() {
        if (binding == null) return;
        List<NodeInfo> sorted = new ArrayList<>(latestSource);
        Collections.sort(sorted, (a, b) -> {
            int cmp = Long.compare(b.getLastHeard(), a.getLastHeard()); // свежие сверху
            return sortByLastHeardAsc ? -cmp : cmp;
        });
        adapter.setSource(sorted);
        updateCounter();
        updateEmptyState();
    }

    private void updateCounter() {
        if (binding == null) return;
        long nowSec = System.currentTimeMillis() / 1000L;
        int online = 0;
        for (NodeInfo n : latestSource) {
            if (n.getLastHeard() > 0 && (nowSec - n.getLastHeard()) <= ONLINE_WINDOW_SEC) online++;
        }
        binding.nodesCounter.setText(getString(
                R.string.nodes_count_format,
                online,
                adapter.getVisibleCount(),
                adapter.getTotalCount()));
    }

    private void updateEmptyState() {
        if (binding == null) return;
        boolean empty = adapter.getVisibleCount() == 0;
        binding.emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
