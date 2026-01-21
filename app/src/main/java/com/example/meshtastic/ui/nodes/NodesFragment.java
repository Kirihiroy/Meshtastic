package com.example.meshtastic.ui.nodes;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meshtastic.R;
import com.example.meshtastic.data.model.NodeInfo;
import com.example.meshtastic.data.repository.MeshConnectionRepository;

import java.util.List;

/**
 * Список узлов сети: показывает имя, батарею, SNR, позицию, время последнего приёма.
 */
public class NodesFragment extends Fragment {

    private NodesAdapter adapter;
    private TextView emptyText;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_nodes, container, false);

        RecyclerView rv = view.findViewById(R.id.nodes_recycler);
        emptyText = view.findViewById(R.id.empty_text);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new NodesAdapter();
        rv.setAdapter(adapter);

        MeshConnectionRepository repo = MeshConnectionRepository.getInstance(requireContext());
        repo.getNodes().observe(getViewLifecycleOwner(), this::renderNodes);

        return view;
    }

    private void renderNodes(List<NodeInfo> list) {
        if (list == null || list.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            adapter.submit(List.of());
        } else {
            emptyText.setVisibility(View.GONE);
            adapter.submit(list);
        }
    }
}
