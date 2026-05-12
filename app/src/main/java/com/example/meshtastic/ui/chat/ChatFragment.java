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

public class ChatFragment extends Fragment {

    private FragmentChatBinding binding;
    private MeshConnectionRepository repo;
    private ChatAdapter adapter;

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("d MMM yyyy", new Locale("ru"));

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
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        binding.rvMessages.setLayoutManager(layoutManager);
        binding.rvMessages.setAdapter(adapter);

        binding.btnSend.setOnClickListener(v -> {
            String text = binding.etMessage.getText().toString().trim();
            if (text.isEmpty()) return;
            boolean sent = repo.sendTextMessage(text);
            if (sent) {
                binding.etMessage.setText("");
            } else {
                Toast.makeText(requireContext(),
                        R.string.chat_toast_no_connection, Toast.LENGTH_SHORT).show();
            }
        });

        repo.getState().observe(getViewLifecycleOwner(), this::applyConnectionState);

        repo.getNodes().observe(getViewLifecycleOwner(), nodes -> {
            Map<Long, NodeInfo> map = new HashMap<>();
            if (nodes != null) {
                for (NodeInfo n : nodes) map.put(n.getNodeNum(), n);
            }
            adapter.updateNodeMap(map);
        });

        repo.getMessages().observe(getViewLifecycleOwner(), msgs -> {
            List<ChatAdapter.ChatListItem> displayList = buildDisplayList(msgs);
            adapter.submitList(displayList, () -> {
                if (binding != null && !displayList.isEmpty()) {
                    binding.rvMessages.scrollToPosition(displayList.size() - 1);
                }
            });
            if (binding != null) {
                binding.tvEmptyChat.setVisibility(
                        (msgs == null || msgs.isEmpty()) ? View.VISIBLE : View.GONE);
            }
        });
    }

    private List<ChatAdapter.ChatListItem> buildDisplayList(@Nullable List<Message> messages) {
        List<ChatAdapter.ChatListItem> items = new ArrayList<>();
        if (messages == null || messages.isEmpty()) return items;

        String lastDate = null;
        String lastSender = null;

        for (Message msg : messages) {
            String date = getDateLabel(msg.getTimestamp());
            if (!date.equals(lastDate)) {
                items.add(ChatAdapter.ChatListItem.forDateHeader(date));
                lastDate = date;
                lastSender = null;
            }
            boolean grouped = !msg.isOwnMessage()
                    && msg.getSenderId() != null
                    && msg.getSenderId().equals(lastSender);
            items.add(ChatAdapter.ChatListItem.forMessage(msg, grouped));
            lastSender = msg.getSenderId();
        }
        return items;
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
        super.onDestroyView();
    }
}
