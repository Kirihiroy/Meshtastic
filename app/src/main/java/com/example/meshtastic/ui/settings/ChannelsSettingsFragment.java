package com.example.meshtastic.ui.settings;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meshtastic.R;
import com.example.meshtastic.data.model.ChannelDraft;
import com.example.meshtastic.data.model.SettingsDraft;
import com.example.meshtastic.data.storage.SettingsStore;
import com.example.meshtastic.databinding.FragmentChannelsSettingsBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Список 8 каналов. Тап по строке открывает {@link ChannelEditFragment} для редактирования.
 */
public class ChannelsSettingsFragment extends Fragment {

    private FragmentChannelsSettingsBinding binding;
    private Adapter adapter;
    private SettingsStore store;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChannelsSettingsBinding.inflate(inflater, container, false);
        store = new SettingsStore(requireContext());

        binding.channelsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new Adapter(this::openEdit);
        binding.channelsRecycler.setAdapter(adapter);

        binding.channelsBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Перезагружаем каналы при возврате с экрана редактирования.
        SettingsDraft draft = store.load();
        adapter.submitList(Arrays.asList(draft.getChannels()));
    }

    private void openEdit(int index) {
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, ChannelEditFragment.newInstance(index))
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    /** Простой адаптер списка каналов. */
    private static class Adapter extends ListAdapter<ChannelDraft, Adapter.VH> {

        interface OnClick { void onClick(int index); }

        private final OnClick onClick;

        Adapter(OnClick onClick) {
            super(new DiffUtil.ItemCallback<ChannelDraft>() {
                @Override public boolean areItemsTheSame(@NonNull ChannelDraft a, @NonNull ChannelDraft b) {
                    return a.getIndex() == b.getIndex();
                }
                @Override public boolean areContentsTheSame(@NonNull ChannelDraft a, @NonNull ChannelDraft b) {
                    return a.getIndex() == b.getIndex()
                            && a.getName().equals(b.getName())
                            && a.getPsk().equals(b.getPsk())
                            && a.getRole().equals(b.getRole());
                }
            });
            this.onClick = onClick;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_channel, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ChannelDraft c = getItem(position);
            h.index.setText(String.valueOf(c.getIndex()));
            applyBadgeColor(h.index, c.getIndex());

            String name = c.getName();
            h.name.setText(name.isEmpty()
                    ? h.itemView.getContext().getString(R.string.channel_name_placeholder)
                    : name);
            h.role.setText(c.getRole());

            h.lock.setVisibility(c.hasPsk() ? View.VISIBLE : View.GONE);
            int lockColor = ContextCompat.getColor(h.itemView.getContext(), R.color.node_lock_green);
            h.lock.setColorFilter(lockColor);

            h.itemView.setOnClickListener(v -> onClick.onClick(c.getIndex()));
        }

        private static void applyBadgeColor(TextView badge, int index) {
            // Стабильный цвет по индексу: HSV сектора (45° step)
            float[] hsv = new float[]{(index * 45f) % 360f, 0.45f, 0.85f};
            int color = Color.HSVToColor(hsv);
            GradientDrawable bg = (GradientDrawable) ContextCompat.getDrawable(
                    badge.getContext(), R.drawable.bg_node_badge);
            if (bg != null) {
                bg = (GradientDrawable) bg.mutate();
                bg.setColor(color);
                badge.setBackground(bg);
            }
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView index;
            final TextView name;
            final TextView role;
            final ImageView lock;
            VH(View v) {
                super(v);
                index = v.findViewById(R.id.channel_index);
                name = v.findViewById(R.id.channel_name);
                role = v.findViewById(R.id.channel_role);
                lock = v.findViewById(R.id.channel_lock);
            }
        }
    }
}
