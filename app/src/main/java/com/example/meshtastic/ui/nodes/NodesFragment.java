package com.example.meshtastic.ui.nodes;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.meshtastic.data.model.NodeInfo;
import com.example.meshtastic.data.repository.MeshConnectionRepository;
import com.example.meshtastic.databinding.FragmentNodesBinding;

import java.util.Collections;
import java.util.List;

/**
 * Список узлов сети: показывает имя, батарею, SNR, позицию, время последнего приёма.
 */
public class NodesFragment extends Fragment {

    private FragmentNodesBinding binding;
    private NodesAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentNodesBinding.inflate(inflater, container, false);

        binding.nodesRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new NodesAdapter();
        binding.nodesRecycler.setAdapter(adapter);

        MeshConnectionRepository repo = MeshConnectionRepository.getInstance(requireContext());
        repo.getNodes().observe(getViewLifecycleOwner(), this::renderNodes);

        return binding.getRoot();
    }

    private void renderNodes(List<NodeInfo> list) {
        if (binding == null) return;
        if (list == null || list.isEmpty()) {
            binding.emptyText.setVisibility(View.VISIBLE);
            adapter.submitList(Collections.emptyList());
        } else {
            binding.emptyText.setVisibility(View.GONE);
            adapter.submitList(list);
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
