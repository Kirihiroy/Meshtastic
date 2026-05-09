package com.example.meshtastic.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meshtastic.R;
import com.example.meshtastic.data.model.Message;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class ChatAdapter extends ListAdapter<Message, ChatAdapter.MessageViewHolder> {

    private static final int VIEW_TYPE_SENT = 0;
    private static final int VIEW_TYPE_RECEIVED = 1;

    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private static final DiffUtil.ItemCallback<Message> DIFF = new DiffUtil.ItemCallback<Message>() {
        @Override
        public boolean areItemsTheSame(@NonNull Message a, @NonNull Message b) {
            return Objects.equals(a.getId(), b.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Message a, @NonNull Message b) {
            return a.equals(b);
        }
    };

    public ChatAdapter() {
        super(DIFF);
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).isOwnMessage() ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == VIEW_TYPE_SENT
                ? R.layout.item_message_sent
                : R.layout.item_message_received;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message msg = getItem(position);
        holder.tvText.setText(msg.getText());
        holder.tvTime.setText(TIME_FMT.format(new Date(msg.getTimestamp())));
        if (holder.tvSender != null) {
            holder.tvSender.setText(msg.getSenderId());
        }
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvText, tvTime, tvSender;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tv_message_text);
            tvTime = itemView.findViewById(R.id.tv_message_time);
            tvSender = itemView.findViewById(R.id.tv_message_sender);
        }
    }
}
