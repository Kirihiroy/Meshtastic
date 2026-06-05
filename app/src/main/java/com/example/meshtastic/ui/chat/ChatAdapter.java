package com.example.meshtastic.ui.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
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

/**
 * Адаптер чата в стиле оригинального Meshtastic-Android.
 * 3 типа элементов: отправленное, полученное, заголовок даты.
 * Группировка: повторные сообщения от одного отправителя в пределах 5 минут
 * рендерятся без аватара/имени и со скруглённым (а не «вырезанным») углом пузыря.
 */
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
            return new DateHeaderVH(inflater.inflate(R.layout.item_date_header, parent, false));
        }
        if (viewType == VIEW_TYPE_SENT) {
            return new SentMessageVH(inflater.inflate(R.layout.item_message_sent, parent, false));
        }
        return new ReceivedMessageVH(inflater.inflate(R.layout.item_message_received, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatListItem item = getItem(position);
        if (item.type == VIEW_TYPE_DATE_HEADER) {
            ((DateHeaderVH) holder).bind(item.dateLabel);
        } else if (holder instanceof SentMessageVH) {
            ((SentMessageVH) holder).bind(item.message);
        } else if (holder instanceof ReceivedMessageVH) {
            String senderName = item.isGrouped ? null : resolveDisplayName(item.message.getSenderId());
            String avatarText = item.isGrouped ? null : resolveAvatarText(item.message.getSenderId());
            ((ReceivedMessageVH) holder).bind(item.message, senderName, avatarText, item.isGrouped);
        }
    }

    /** Получает имя из nodeMap по hex-ID `!aabbccdd` или числу. Fallback — сам ID. */
    private String resolveDisplayName(String senderId) {
        if (senderId == null || senderId.isEmpty()) return "?";
        NodeInfo node = lookupNode(senderId);
        if (node != null) {
            String name = node.getLongName();
            if (name != null && !name.isEmpty()) return name;
            name = node.getShortName();
            if (name != null && !name.isEmpty()) return name;
        }
        return senderId;
    }

    /** До 4 символов для аватарки. Берёт short_name, иначе последние 4 hex-цифры ID. */
    private String resolveAvatarText(String senderId) {
        if (senderId == null || senderId.isEmpty()) return "?";
        NodeInfo node = lookupNode(senderId);
        if (node != null && node.getShortName() != null && !node.getShortName().isEmpty()) {
            String s = node.getShortName();
            return s.length() <= 4 ? s : s.substring(0, 4);
        }
        // !aabbccdd → "ccdd"
        String hex = senderId.startsWith("!") ? senderId.substring(1) : senderId;
        return hex.length() <= 4 ? hex : hex.substring(hex.length() - 4);
    }

    private @Nullable NodeInfo lookupNode(String senderId) {
        try {
            long nodeNum = senderId.startsWith("!")
                    ? Long.parseLong(senderId.substring(1), 16)
                    : Long.parseLong(senderId);
            return nodeMap.get(nodeNum);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** Цвет аватара/имени — стабильно из ID, чтобы разные отправители выделялись. */
    private static int colorFromSender(String senderId) {
        int hash = senderId == null ? 0 : senderId.hashCode();
        float hue = Math.abs(hash) % 360;
        return Color.HSVToColor(new float[]{hue, 0.45f, 0.85f});
    }

    /** Раскрашивает фон аватарки (overlay поверх bg_chat_avatar). */
    private static void tintAvatar(TextView badge, int color) {
        GradientDrawable bg = (GradientDrawable) ContextCompat.getDrawable(
                badge.getContext(), R.drawable.bg_chat_avatar);
        if (bg != null) {
            bg = (GradientDrawable) bg.mutate();
            bg.setColor(color);
            badge.setBackground(bg);
        }
    }

    // ───────────── ViewHolders ─────────────

    static class SentMessageVH extends RecyclerView.ViewHolder {
        final TextView tvText, tvTime;
        final ImageView ivStatus;
        final LinearLayout bubble;

        SentMessageVH(@NonNull View v) {
            super(v);
            tvText = v.findViewById(R.id.tv_message_text);
            tvTime = v.findViewById(R.id.tv_message_time);
            ivStatus = v.findViewById(R.id.iv_delivery_status);
            bubble = v.findViewById(R.id.bubble_container);
        }

        void bind(Message msg) {
            tvText.setText(msg.getText());
            tvTime.setText(TIME_FMT.format(new Date(msg.getTimestamp())));
            ivStatus.setImageResource(deliveryIcon(msg.getDeliveryStatus()));
            int color = msg.getDeliveryStatus() == Message.DeliveryStatus.FAILED
                    ? 0xFFE53935
                    : ContextCompat.getColor(itemView.getContext(), R.color.chat_bubble_outgoing_time);
            ivStatus.setColorFilter(color);
            attachContextMenu(itemView, msg);
        }
    }

    static class ReceivedMessageVH extends RecyclerView.ViewHolder {
        final TextView tvText, tvTime, tvSender, tvHops, avatar;
        final LinearLayout bubble;
        final LinearLayout hopsChip;

        ReceivedMessageVH(@NonNull View v) {
            super(v);
            tvText = v.findViewById(R.id.tv_message_text);
            tvTime = v.findViewById(R.id.tv_message_time);
            tvSender = v.findViewById(R.id.tv_message_sender);
            tvHops = v.findViewById(R.id.tv_hops);
            avatar = v.findViewById(R.id.avatar_badge);
            bubble = v.findViewById(R.id.bubble_container);
            hopsChip = v.findViewById(R.id.hops_chip);
        }

        void bind(Message msg, @Nullable String senderName,
                  @Nullable String avatarText, boolean grouped) {
            tvText.setText(msg.getText());
            tvTime.setText(TIME_FMT.format(new Date(msg.getTimestamp())));

            // Имя/аватар: только у первого сообщения в группе.
            if (grouped) {
                tvSender.setVisibility(View.GONE);
                avatar.setVisibility(View.INVISIBLE);  // место сохраняем, чтобы пузыри были выровнены
                bubble.setBackgroundResource(R.drawable.bg_bubble_incoming_grouped);
            } else {
                if (senderName != null && !senderName.isEmpty()) {
                    tvSender.setVisibility(View.VISIBLE);
                    tvSender.setText(senderName);
                    int color = colorFromSender(msg.getSenderId());
                    tvSender.setTextColor(color);
                    if (avatarText != null) {
                        avatar.setVisibility(View.VISIBLE);
                        avatar.setText(avatarText);
                        tintAvatar(avatar, color);
                    } else {
                        avatar.setVisibility(View.INVISIBLE);
                    }
                } else {
                    tvSender.setVisibility(View.GONE);
                    avatar.setVisibility(View.INVISIBLE);
                }
                bubble.setBackgroundResource(R.drawable.bg_bubble_incoming);
            }

            // Hops chip — показываем если известно и > 0.
            if (msg.getHopsAway() > 0) {
                hopsChip.setVisibility(View.VISIBLE);
                tvHops.setText(itemView.getContext().getString(
                        R.string.chat_hops_format, msg.getHopsAway()));
            } else {
                hopsChip.setVisibility(View.GONE);
            }

            attachContextMenu(itemView, msg);
        }
    }

    static class DateHeaderVH extends RecyclerView.ViewHolder {
        final TextView tvDate;

        DateHeaderVH(@NonNull View v) {
            super(v);
            tvDate = v.findViewById(R.id.tv_date_header);
        }

        void bind(String label) {
            tvDate.setText(label);
        }
    }

    // ───────────── helpers ─────────────

    private static int deliveryIcon(Message.DeliveryStatus s) {
        if (s == null) return R.drawable.ic_check;
        switch (s) {
            case SENDING:   return R.drawable.ic_clock;
            case DELIVERED: return R.drawable.ic_check_double;
            case FAILED:    return R.drawable.ic_warning;
            case SENT:
            default:        return R.drawable.ic_check;
        }
    }

    /** Контекстное меню по долгому нажатию: Скопировать / Ответить / Удалить локально. */
    private static void attachContextMenu(View root, Message msg) {
        root.setOnLongClickListener(v -> {
            PopupMenu menu = new PopupMenu(v.getContext(), v);
            menu.getMenu().add(0, 1, 0, R.string.chat_msg_action_copy);
            menu.getMenu().add(0, 2, 1, R.string.chat_msg_action_reply);
            menu.getMenu().add(0, 3, 2, R.string.chat_msg_action_delete);
            menu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                Context ctx = v.getContext();
                if (id == 1) {
                    ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("message", msg.getText()));
                    Toast.makeText(ctx, R.string.chat_copied, Toast.LENGTH_SHORT).show();
                } else if (id == 2) {
                    Toast.makeText(ctx, R.string.chat_msg_action_reply_toast, Toast.LENGTH_SHORT).show();
                } else if (id == 3) {
                    Toast.makeText(ctx, R.string.chat_msg_action_delete_toast, Toast.LENGTH_SHORT).show();
                }
                return true;
            });
            menu.show();
            return true;
        });
    }

    // ───────────── ChatListItem ─────────────

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
