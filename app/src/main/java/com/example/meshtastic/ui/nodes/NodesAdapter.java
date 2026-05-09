package com.example.meshtastic.ui.nodes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meshtastic.R;
import com.example.meshtastic.data.model.NodeInfo;

class NodesAdapter extends ListAdapter<NodeInfo, NodesAdapter.VH> {

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

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_node, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        NodeInfo n = getItem(position);
        holder.title.setText(displayName(n, holder.itemView.getContext()));
        holder.subtitle.setText(holder.itemView.getContext().getString(
                R.string.nodes_subtitle_format,
                n.getUserId() == null ? "—" : n.getUserId(),
                String.valueOf(n.getNodeNum())));

        StringBuilder meta = new StringBuilder();
        if (n.getBatteryLevel() >= 0) {
            meta.append(holder.itemView.getContext().getString(R.string.nodes_meta_battery, n.getBatteryLevel()));
            meta.append("  ");
        }
        meta.append(holder.itemView.getContext().getString(R.string.nodes_meta_snr, n.getSnr()));
        if (n.getHopsAway() != null) {
            meta.append("  ");
            meta.append(holder.itemView.getContext().getString(R.string.nodes_meta_hops, n.getHopsAway()));
        }
        if (n.getChannel() != null) {
            meta.append("  ");
            meta.append(holder.itemView.getContext().getString(R.string.nodes_meta_channel, n.getChannel()));
        }
        if (n.isViaMqtt()) {
            meta.append("  ");
            meta.append(holder.itemView.getContext().getString(R.string.nodes_meta_via_mqtt));
        }
        holder.meta.setText(meta.toString());

        if (n.getLatitude() != 0 || n.getLongitude() != 0) {
            holder.coords.setText(holder.itemView.getContext().getString(
                    R.string.nodes_coords_format, n.getLatitude(), n.getLongitude()));
            holder.coords.setVisibility(View.VISIBLE);
        } else {
            holder.coords.setVisibility(View.GONE);
        }

        holder.time.setText(holder.itemView.getContext().getString(
                R.string.nodes_last_heard_format, n.getLastHeard()));
    }

    private static String displayName(NodeInfo n, android.content.Context ctx) {
        if (n.getLongName() != null && !n.getLongName().isEmpty()) return n.getLongName();
        if (n.getShortName() != null && !n.getShortName().isEmpty()) return n.getShortName();
        if (n.getUserId() != null && !n.getUserId().isEmpty()) return n.getUserId();
        return ctx.getString(R.string.nodes_default_name, n.getNodeNum());
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
