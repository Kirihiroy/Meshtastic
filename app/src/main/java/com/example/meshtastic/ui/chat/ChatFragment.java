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
import com.example.meshtastic.data.repository.MeshConnectionRepository;
import com.example.meshtastic.databinding.FragmentChatBinding;

public class ChatFragment extends Fragment {

    private FragmentChatBinding binding;
    private MeshConnectionRepository repo;
    private ChatAdapter adapter;

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

        repo.getMessages().observe(getViewLifecycleOwner(), msgs -> {
            adapter.submitList(msgs, () -> {
                if (binding != null && msgs != null && !msgs.isEmpty()) {
                    binding.rvMessages.scrollToPosition(msgs.size() - 1);
                }
            });
        });
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
