package com.example.meshtastic.ui.nodes;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.meshtastic.R;
import com.example.meshtastic.data.model.NodeInfo;
import com.example.meshtastic.data.repository.MeshConnectionRepository;
import com.example.meshtastic.databinding.FragmentNodesBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

        return binding.getRoot();
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
