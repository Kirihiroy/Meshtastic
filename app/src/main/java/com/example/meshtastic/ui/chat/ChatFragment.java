package com.example.meshtastic.ui.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meshtastic.R;
import com.example.meshtastic.data.repository.MeshConnectionRepository;

public class ChatFragment extends Fragment {

    private MeshConnectionRepository repo;
    private ChatAdapter adapter;
    private RecyclerView recyclerView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repo = MeshConnectionRepository.getInstance(requireContext());

        recyclerView = view.findViewById(R.id.rv_messages);
        adapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        EditText etMessage = view.findViewById(R.id.et_message);
        ImageButton btnSend = view.findViewById(R.id.btn_send);

        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (text.isEmpty()) return;
            boolean sent = repo.sendTextMessage(text);
            if (sent) {
                etMessage.setText("");
            } else {
                Toast.makeText(requireContext(),
                        "Нет подключения к устройству", Toast.LENGTH_SHORT).show();
            }
        });

        repo.getMessages().observe(getViewLifecycleOwner(), msgs -> {
            adapter.setMessages(msgs);
            if (msgs != null && !msgs.isEmpty()) {
                recyclerView.scrollToPosition(msgs.size() - 1);
            }
        });
    }
}
