package com.example.meshtastic.ui.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meshtastic.R;
import com.example.meshtastic.data.model.Message;
import com.example.meshtastic.data.model.NodeInfo;
import com.example.meshtastic.data.repository.MeshConnectionRepository;
import com.example.meshtastic.databinding.FragmentChatBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Фрагмент «Чат» — общий канал mesh-сети.
 * Дизайн повторяет оригинальное Meshtastic-Android: шапка с подзаголовком про канал,
 * группировка по отправителю + 5 минут, FAB «N новых» при прокрутке вверх,
 * статус доставки исходящих сообщений.
 */
public class ChatFragment extends Fragment {

    /** Максимальный интервал, в котором два соседних сообщения от одного отправителя
     *  считаются «продолжением группы» (для скрытия имени и аватара у второго). */
    private static final long GROUP_WINDOW_MS = 5 * 60 * 1000L;

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("d MMM yyyy", new Locale("ru"));

    private FragmentChatBinding binding;
    private MeshConnectionRepository repo;
    private ChatAdapter adapter;
    private LinearLayoutManager layoutManager;

    /** Сколько новых сообщений пришло, пока пользователь был прокручен вверх. */
    private int unreadCount = 0;
    /** Был ли пользователь у нижнего края на момент прошлого апдейта. */
    private boolean wasAtBottom = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repo = MeshConnectionRepository.getInstance(requireContext());

        adapter = new ChatAdapter();
        layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        binding.rvMessages.setLayoutManager(layoutManager);
        binding.rvMessages.setAdapter(adapter);

        // Если пользователь прокрутил к низу — гасим FAB и сбрасываем счётчик «новых».
        binding.rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (isAtBottom()) clearUnread();
            }
        });

        binding.btnSend.setOnClickListener(v -> sendCurrent());
        binding.btnAttach.setOnClickListener(v -> Toast.makeText(
                requireContext(),
                "Прикрепление вложений пока не реализовано",
                Toast.LENGTH_SHORT).show());
        binding.btnNewMessages.setOnClickListener(v -> {
            scrollToBottom();
            clearUnread();
        });

        repo.getState().observe(getViewLifecycleOwner(), this::applyConnectionState);

        repo.getNodes().observe(getViewLifecycleOwner(), nodes -> {
            Map<Long, NodeInfo> map = new HashMap<>();
            if (nodes != null) for (NodeInfo n : nodes) map.put(n.getNodeNum(), n);
            adapter.updateNodeMap(map);
            updateSubtitle(nodes);
        });

        repo.getMessages().observe(getViewLifecycleOwner(), this::renderMessages);

        updateSubtitle(repo.getNodes().getValue());
    }

    private void sendCurrent() {
        String text = binding.etMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        boolean sent = repo.sendTextMessage(text);
        if (sent) {
            binding.etMessage.setText("");
            // Только что отправили — гарантированно прокручиваем к низу.
            wasAtBottom = true;
        } else {
            Toast.makeText(requireContext(),
                    R.string.chat_toast_no_connection, Toast.LENGTH_SHORT).show();
        }
    }

    private void renderMessages(@Nullable List<Message> msgs) {
        if (binding == null) return;

        int previousSize = adapter.getItemCount();
        boolean wasBottom = wasAtBottom;
        List<ChatAdapter.ChatListItem> displayList = buildDisplayList(msgs);

        adapter.submitList(displayList, () -> {
            if (binding == null) return;
            int newSize = displayList.size();

            if (wasBottom) {
                // Пользователь был у низа — продолжаем держать его там.
                scrollToBottom();
                clearUnread();
            } else if (newSize > previousSize) {
                // Сверху прибавилось сообщений — увеличиваем счётчик «новых».
                unreadCount += newSize - previousSize;
                showUnreadBadge();
            }
            wasAtBottom = isAtBottom();
        });

        binding.tvEmptyChat.setVisibility(
                (msgs == null || msgs.isEmpty()) ? View.VISIBLE : View.GONE);
    }

    /** Группировка: разделитель даты при смене дня + сворачивание подряд идущих от одного
     *  отправителя в пределах GROUP_WINDOW_MS (5 минут). */
    private List<ChatAdapter.ChatListItem> buildDisplayList(@Nullable List<Message> messages) {
        List<ChatAdapter.ChatListItem> items = new ArrayList<>();
        if (messages == null || messages.isEmpty()) return items;

        String lastDate = null;
        String lastSender = null;
        long lastTs = 0L;

        for (Message msg : messages) {
            String date = getDateLabel(msg.getTimestamp());
            if (!date.equals(lastDate)) {
                items.add(ChatAdapter.ChatListItem.forDateHeader(date));
                lastDate = date;
                lastSender = null;
                lastTs = 0L;
            }
            boolean sameSender = !msg.isOwnMessage()
                    && msg.getSenderId() != null
                    && msg.getSenderId().equals(lastSender);
            boolean withinWindow = (msg.getTimestamp() - lastTs) <= GROUP_WINDOW_MS;
            boolean grouped = sameSender && withinWindow;

            items.add(ChatAdapter.ChatListItem.forMessage(msg, grouped));
            lastSender = msg.getSenderId();
            lastTs = msg.getTimestamp();
        }
        return items;
    }

    private void updateSubtitle(@Nullable List<NodeInfo> nodes) {
        if (binding == null) return;
        int count = nodes == null ? 0 : nodes.size();
        // Имя канала — для MVP константа LongFast; источник правды появится после
        // реализации полной синхронизации Channel списка с устройства.
        String channelName = getString(R.string.chat_channel_default);
        binding.tvChatSubtitle.setText(getString(
                R.string.chat_subtitle_format, channelName, count));
    }

    private String getDateLabel(long timestamp) {
        Calendar today = Calendar.getInstance();
        Calendar msgDay = Calendar.getInstance();
        msgDay.setTimeInMillis(timestamp);
        if (isSameDay(today, msgDay)) return getString(R.string.chat_date_today);
        today.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(today, msgDay)) return getString(R.string.chat_date_yesterday);
        return DATE_FMT.format(new Date(timestamp));
    }

    private static boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private boolean isAtBottom() {
        if (layoutManager == null || adapter.getItemCount() == 0) return true;
        int last = layoutManager.findLastVisibleItemPosition();
        return last >= adapter.getItemCount() - 1;
    }

    private void scrollToBottom() {
        if (binding == null || adapter.getItemCount() == 0) return;
        binding.rvMessages.scrollToPosition(adapter.getItemCount() - 1);
    }

    private void showUnreadBadge() {
        if (binding == null) return;
        binding.btnNewMessages.setText(getString(R.string.chat_new_messages_format, unreadCount));
        binding.btnNewMessages.setVisibility(View.VISIBLE);
    }

    private void clearUnread() {
        if (binding == null) return;
        unreadCount = 0;
        binding.btnNewMessages.setVisibility(View.GONE);
    }

    private void applyConnectionState(MeshConnectionRepository.State state) {
        boolean connected = state == MeshConnectionRepository.State.CONNECTED;
        binding.etMessage.setEnabled(connected);
        binding.btnSend.setEnabled(connected);
        binding.etMessage.setHint(connected
                ? getString(R.string.chat_hint_message)
                : getString(R.string.chat_hint_not_connected));
        binding.tvNoConnectionBanner.setVisibility(connected ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        layoutManager = null;
        super.onDestroyView();
    }
}
