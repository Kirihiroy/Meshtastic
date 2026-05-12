package com.example.meshtastic.ui.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meshtastic.R;
import com.example.meshtastic.data.model.Message;
import com.example.meshtastic.data.model.NodeInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ChatAdapter extends ListAdapter<ChatAdapter.ChatListItem, RecyclerView.ViewHolder> {

    static final int VIEW_TYPE_SENT = 0;
    static final int VIEW_TYPE_RECEIVED = 1;
    static final int VIEW_TYPE_DATE_HEADER = 2;

    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private Map<Long, NodeInfo> nodeMap = new HashMap<>();

    private static final DiffUtil.ItemCallback<ChatListItem> DIFF = new DiffUtil.ItemCallback<ChatListItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull ChatListItem a, @NonNull ChatListItem b) {
            if (a.type != b.type) return false;
            if (a.type == VIEW_TYPE_DATE_HEADER) return Objects.equals(a.dateLabel, b.dateLabel);
            return Objects.equals(a.message.getId(), b.message.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull ChatListItem a, @NonNull ChatListItem b) {
            if (a.type != b.type) return false;
            if (a.type == VIEW_TYPE_DATE_HEADER) return Objects.equals(a.dateLabel, b.dateLabel);
            return a.message.equals(b.message) && a.isGrouped == b.isGrouped;
        }
    };

    public ChatAdapter() {
        super(DIFF);
    }

    public void updateNodeMap(Map<Long, NodeInfo> map) {
        this.nodeMap = map != null ? map : new HashMap<>();
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_DATE_HEADER) {
            return new DateHeaderViewHolder(inflater.inflate(R.layout.item_date_header, parent, false));
        }
        int layout = viewType == VIEW_TYPE_SENT ? R.layout.item_message_sent : R.layout.item_message_received;
        return new MessageViewHolder(inflater.inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatListItem item = getItem(position);
        if (item.type == VIEW_TYPE_DATE_HEADER) {
            ((DateHeaderViewHolder) holder).bind(item.dateLabel);
        } else {
            String senderName = (!item.message.isOwnMessage() && !item.isGrouped)
                    ? resolveDisplayName(item.message.getSenderId()) : null;
            ((MessageViewHolder) holder).bind(item.message, senderName);
        }
    }

    private String resolveDisplayName(String senderId) {
        if (senderId == null || senderId.isEmpty()) return "?";
        try {
            long nodeNum = senderId.startsWith("!")
                    ? Long.parseLong(senderId.substring(1), 16)
                    : Long.parseLong(senderId);
            NodeInfo node = nodeMap.get(nodeNum);
            if (node != null) {
                String name = node.getLongName();
                if (name != null && !name.isEmpty()) return name;
                name = node.getShortName();
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (NumberFormatException ignored) {}
        return senderId;
    }

    // ---- ViewHolders ----

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        final TextView tvText, tvTime, tvSender;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tv_message_text);
            tvTime = itemView.findViewById(R.id.tv_message_time);
            tvSender = itemView.findViewById(R.id.tv_message_sender);
        }

        void bind(Message msg, @Nullable String senderName) {
            tvText.setText(msg.getText());
            tvTime.setText(TIME_FMT.format(new Date(msg.getTimestamp())));
            if (tvSender != null) {
                if (senderName != null) {
                    tvSender.setVisibility(View.VISIBLE);
                    tvSender.setText(senderName);
                } else {
                    tvSender.setVisibility(View.GONE);
                }
            }
            itemView.setOnLongClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) v.getContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("message", msg.getText()));
                Toast.makeText(v.getContext(), R.string.chat_copied, Toast.LENGTH_SHORT).show();
                return true;
            });
        }
    }

    static class DateHeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDate;

        DateHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_date_header);
        }

        void bind(String label) {
            tvDate.setText(label);
        }
    }

    // ---- ChatListItem ----

    public static class ChatListItem {
        final int type;
        final Message message;
        final String dateLabel;
        final boolean isGrouped;

        private ChatListItem(int type, Message message, String dateLabel, boolean isGrouped) {
            this.type = type;
            this.message = message;
            this.dateLabel = dateLabel;
            this.isGrouped = isGrouped;
        }

        public static ChatListItem forMessage(Message msg, boolean isGrouped) {
            int type = msg.isOwnMessage() ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
            return new ChatListItem(type, msg, null, isGrouped);
        }

        public static ChatListItem forDateHeader(String label) {
            return new ChatListItem(VIEW_TYPE_DATE_HEADER, null, label, false);
        }
    }
}
