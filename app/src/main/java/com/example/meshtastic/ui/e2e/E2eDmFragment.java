package com.example.meshtastic.ui.e2e;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.meshtastic.R;
import com.example.meshtastic.crypto.E2eKeyManager;
import com.example.meshtastic.data.model.Message;
import com.example.meshtastic.data.repository.MeshConnectionRepository;
import com.example.meshtastic.databinding.FragmentE2eDmBinding;
import com.example.meshtastic.ui.chat.ChatAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class E2eDmFragment extends Fragment {

    private FragmentE2eDmBinding binding;
    private MeshConnectionRepository repo;
    private ChatAdapter adapter;
    private MeshConnectionRepository.State currentState = MeshConnectionRepository.State.DISCONNECTED;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentE2eDmBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repo = MeshConnectionRepository.getInstance(requireContext());
        E2eKeyManager keyManager = repo.getE2eKeyManager();

        // Показать публичный ключ этого устройства
        String myPubKeyHex = keyManager.isAvailable() ? keyManager.getPublicKeyHex() : "";
        binding.tvMyPubkey.setText(myPubKeyHex.isEmpty()
                ? getString(R.string.e2e_key_unavailable)
                : myPubKeyHex);

        binding.btnCopyKey.setOnClickListener(v -> {
            if (myPubKeyHex.isEmpty()) return;
            ClipboardManager cm = (ClipboardManager) requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("E2E Public Key", myPubKeyHex));
            Toast.makeText(requireContext(), R.string.e2e_key_copied, Toast.LENGTH_SHORT).show();
        });

        // Список E2E-сообщений
        adapter = new ChatAdapter();
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);
        binding.rvE2eMessages.setLayoutManager(lm);
        binding.rvE2eMessages.setAdapter(adapter);

        repo.getE2eMessages().observe(getViewLifecycleOwner(), msgs -> {
            List<ChatAdapter.ChatListItem> displayList = buildDisplayList(msgs);
            adapter.submitList(displayList, () -> {
                if (binding != null && !displayList.isEmpty()) {
                    binding.rvE2eMessages.scrollToPosition(displayList.size() - 1);
                }
            });
            if (binding != null) {
                binding.tvE2eEmpty.setVisibility(
                        (msgs == null || msgs.isEmpty()) ? View.VISIBLE : View.GONE);
            }
        });

        repo.getState().observe(getViewLifecycleOwner(), state -> {
            currentState = state;
            updateInputState();
        });

        TextWatcher recipientWatcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { updateInputState(); }
            public void afterTextChanged(Editable s) {}
        };
        binding.etRecipientNode.addTextChangedListener(recipientWatcher);
        binding.etRecipientPubkey.addTextChangedListener(recipientWatcher);

        binding.btnE2eSend.setOnClickListener(v -> sendMessage());
    }

    private void updateInputState() {
        if (binding == null) return;
        boolean connected = currentState == MeshConnectionRepository.State.CONNECTED;
        String nodeText = getText(binding.etRecipientNode.getText());
        String pubKeyText = getText(binding.etRecipientPubkey.getText());
        boolean hasRecipient = !nodeText.isEmpty() && !pubKeyText.isEmpty();

        boolean canSend = connected && hasRecipient;
        binding.etE2eMessage.setEnabled(canSend);
        binding.btnE2eSend.setEnabled(canSend);

        if (!connected) {
            binding.etE2eMessage.setHint(getString(R.string.e2e_hint_msg_no_connection));
        } else if (!hasRecipient) {
            binding.etE2eMessage.setHint(getString(R.string.e2e_hint_msg_no_recipient));
        } else {
            binding.etE2eMessage.setHint(getString(R.string.e2e_hint_message));
        }

        String label = nodeText.isEmpty()
                ? getString(R.string.e2e_recipient_not_set)
                : getString(R.string.e2e_to_label, nodeText);
        binding.tvRecipientIndicator.setText(label);
    }

    private void sendMessage() {
        String nodeText = getText(binding.etRecipientNode.getText());
        String pubKeyHex = getText(binding.etRecipientPubkey.getText());
        String text = getText(binding.etE2eMessage.getText());

        if (text.isEmpty()) return;

        if (nodeText.isEmpty()) {
            Toast.makeText(requireContext(), R.string.e2e_error_no_recipient, Toast.LENGTH_SHORT).show();
            return;
        }
        if (pubKeyHex.isEmpty()) {
            Toast.makeText(requireContext(), R.string.e2e_error_no_pubkey, Toast.LENGTH_SHORT).show();
            return;
        }

        long recipientNum;
        try {
            if (nodeText.startsWith("!")) {
                recipientNum = Long.parseLong(nodeText.substring(1), 16);
            } else {
                recipientNum = Long.parseLong(nodeText);
            }
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), R.string.e2e_error_bad_recipient, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean sent = repo.sendE2eMessage(recipientNum, pubKeyHex, text);
        if (sent) {
            binding.etE2eMessage.setText("");
        } else {
            Toast.makeText(requireContext(), R.string.e2e_send_error_toast, Toast.LENGTH_SHORT).show();
        }
    }

    private static String getText(Editable editable) {
        return editable != null ? editable.toString().trim() : "";
    }

    private List<ChatAdapter.ChatListItem> buildDisplayList(@Nullable List<Message> messages) {
        List<ChatAdapter.ChatListItem> items = new ArrayList<>();
        if (messages == null || messages.isEmpty()) return items;
        String lastDate = null;
        String lastSender = null;
        SimpleDateFormat dateFmt = new SimpleDateFormat("d MMM yyyy", new Locale("ru"));
        for (Message msg : messages) {
            String date = getDateLabel(msg.getTimestamp(), dateFmt);
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

    private String getDateLabel(long timestamp, SimpleDateFormat dateFmt) {
        Calendar today = Calendar.getInstance();
        Calendar msgDay = Calendar.getInstance();
        msgDay.setTimeInMillis(timestamp);
        if (today.get(Calendar.YEAR) == msgDay.get(Calendar.YEAR)
                && today.get(Calendar.DAY_OF_YEAR) == msgDay.get(Calendar.DAY_OF_YEAR)) {
            return getString(R.string.chat_date_today);
        }
        today.add(Calendar.DAY_OF_YEAR, -1);
        if (today.get(Calendar.YEAR) == msgDay.get(Calendar.YEAR)
                && today.get(Calendar.DAY_OF_YEAR) == msgDay.get(Calendar.DAY_OF_YEAR)) {
            return getString(R.string.chat_date_yesterday);
        }
        return dateFmt.format(new Date(timestamp));
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
