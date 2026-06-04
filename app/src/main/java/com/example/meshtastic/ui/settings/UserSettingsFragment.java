package com.example.meshtastic.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.meshtastic.R;
import com.example.meshtastic.data.model.SettingsDraft;
import com.example.meshtastic.data.storage.SettingsStore;
import com.example.meshtastic.databinding.FragmentUserSettingsBinding;

import java.util.Arrays;
import java.util.List;

/**
 * Под-экран «Пользователь» из меню Настройки.
 * Имя (long), короткое имя, роль, флаг HAM. Сохраняется в локальный черновик.
 */
public class UserSettingsFragment extends Fragment {

    /** Роли из Config.DeviceConfig.Role (исключая UNSET и устаревшие). */
    private static final List<String> ROLES = Arrays.asList(
            "CLIENT", "CLIENT_MUTE", "ROUTER", "ROUTER_LATE", "REPEATER",
            "TRACKER", "SENSOR", "TAK", "CLIENT_HIDDEN", "LOST_AND_FOUND",
            "TAK_TRACKER"
    );

    private FragmentUserSettingsBinding binding;
    private SettingsStore store;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentUserSettingsBinding.inflate(inflater, container, false);
        store = new SettingsStore(requireContext());

        SettingsDraft draft = store.loadWithUser();
        binding.userLongName.setText(safe(draft.getNodeName()));
        binding.userShortName.setText(safe(draft.getUserShortName()));
        binding.userRole.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_list_item_1, ROLES));
        binding.userRole.setText(safe(draft.getUserRole()), false);
        binding.userIsLicensed.setChecked(draft.isUserLicensed());

        binding.userBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        binding.userSave.setOnClickListener(v -> save());

        return binding.getRoot();
    }

    private void save() {
        String longName = textOf(binding.userLongName);
        String shortName = textOf(binding.userShortName);
        String role = textOf(binding.userRole);

        if (shortName.length() > 4) {
            Toast.makeText(requireContext(), R.string.user_toast_bad_short_name, Toast.LENGTH_SHORT).show();
            return;
        }

        SettingsDraft draft = store.loadWithUser();
        draft.setNodeName(longName); // long_name живёт в nodeName
        draft.setUserShortName(shortName);
        draft.setUserRole(role.isEmpty() ? "CLIENT" : role);
        draft.setUserLicensed(binding.userIsLicensed.isChecked());
        store.saveUser(draft);

        Toast.makeText(requireContext(), R.string.user_toast_saved, Toast.LENGTH_SHORT).show();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String textOf(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
