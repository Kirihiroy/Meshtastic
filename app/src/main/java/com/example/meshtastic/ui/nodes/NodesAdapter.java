package com.example.meshtastic.ui.nodes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meshtastic.R;
import com.example.meshtastic.data.model.NodeInfo;

import java.util.ArrayList;
import java.util.List;

class NodesAdapter extends RecyclerView.Adapter<NodesAdapter.VH> {

    private final List<NodeInfo> items = new ArrayList<>();

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_node, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        NodeInfo n = items.get(position);
        holder.title.setText(displayName(n));
        holder.subtitle.setText("ID: " + n.getUserId() + " | Num: " + n.getNodeNum());

        StringBuilder meta = new StringBuilder();
        if (n.getBatteryLevel() >= 0) meta.append("Batt: ").append(n.getBatteryLevel()).append("%  ");
        meta.append("SNR: ").append(String.format("%.1f", n.getSnr()));
        if (n.getHopsAway() != null) meta.append("  Hops: ").append(n.getHopsAway());
        if (n.getChannel() != null) meta.append("  Ch: ").append(n.getChannel());
        if (n.isViaMqtt()) meta.append("  via MQTT");
        holder.meta.setText(meta.toString());

        if (n.getLatitude() != 0 || n.getLongitude() != 0) {
            holder.coords.setText(
                    String.format("Lat: %.5f  Lon: %.5f", n.getLatitude(), n.getLongitude())
            );
            holder.coords.setVisibility(View.VISIBLE);
        } else {
            holder.coords.setVisibility(View.GONE);
        }

        holder.time.setText("last heard: " + (n.getLastHeard()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    void submit(List<NodeInfo> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    private String displayName(NodeInfo n) {
        if (n.getLongName() != null && !n.getLongName().isEmpty()) return n.getLongName();
        if (n.getShortName() != null && !n.getShortName().isEmpty()) return n.getShortName();
        if (n.getUserId() != null && !n.getUserId().isEmpty()) return n.getUserId();
        return "Node " + n.getNodeNum();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        final TextView meta;
        final TextView coords;
        final TextView time;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.node_title);
            subtitle = itemView.findViewById(R.id.node_subtitle);
            meta = itemView.findViewById(R.id.node_meta);
            coords = itemView.findViewById(R.id.node_coords);
            time = itemView.findViewById(R.id.node_time);
        }
    }
}
