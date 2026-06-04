package com.example.meshtastic.ui.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.meshtastic.R;
import com.example.meshtastic.data.model.SettingsDraft;
import com.example.meshtastic.data.repository.MeshConnectionRepository;
import com.example.meshtastic.data.storage.SettingsStore;
import com.example.meshtastic.databinding.FragmentSecuritySettingsBinding;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Под-экран «Безопасность» из меню Настройки.
 * Показывает E2E публичный ключ устройства (read-only, копируется в clipboard),
 * до трёх доверенных admin-ключей (hex) и переключатель is_managed.
 */
public class SecuritySettingsFragment extends Fragment {

    private FragmentSecuritySettingsBinding binding;
    private SettingsStore store;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSecuritySettingsBinding.inflate(inflater, container, false);
        store = new SettingsStore(requireContext());

        // Публичный ключ из E2eKeyManager (генерируется при первом запуске приложения)
        MeshConnectionRepository repo = MeshConnectionRepository.getInstance(requireContext());
        String pubHex = repo.getE2eKeyManager() != null
                ? repo.getE2eKeyManager().getPublicKeyHex()
                : "";
        if (pubHex.isEmpty()) {
            binding.securityMyPubkey.setText(R.string.security_toast_pubkey_unavailable);
            binding.securityCopyPubkey.setEnabled(false);
        } else {
            binding.securityMyPubkey.setText(pubHex);
        }

        // Локальные admin-ключи + is_managed
        SettingsDraft draft = store.loadWithSecurity();
        binding.securityAdmin0.setText(draft.getAdminKey(0));
        binding.securityAdmin1.setText(draft.getAdminKey(1));
        binding.securityAdmin2.setText(draft.getAdminKey(2));
        binding.securityIsManaged.setChecked(draft.isManaged());

        // Лейблы для admin-ключей с номером (1/2/3 — для пользователя приятнее, чем 0/1/2)
        binding.securityAdminLayout0.setHint(getString(R.string.security_label_admin_key_format, 1));
        binding.securityAdminLayout1.setHint(getString(R.string.security_label_admin_key_format, 2));
        binding.securityAdminLayout2.setHint(getString(R.string.security_label_admin_key_format, 3));

        // Кнопки
        binding.securityBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        binding.securityCopyPubkey.setOnClickListener(v -> copyPublicKey(pubHex));
        binding.securitySave.setOnClickListener(v -> save());

        return binding.getRoot();
    }

    private void copyPublicKey(String hex) {
        if (hex == null || hex.isEmpty()) {
            toast(R.string.security_toast_pubkey_unavailable);
            return;
        }
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Meshtastic E2E pubkey", hex));
            toast(R.string.security_toast_pubkey_copied);
        }
    }

    private void save() {
        String k0 = textOf(binding.securityAdmin0);
        String k1 = textOf(binding.securityAdmin1);
        String k2 = textOf(binding.securityAdmin2);

        if (!isValidHex(k0)) { adminKeyError(0); return; }
        if (!isValidHex(k1)) { adminKeyError(1); return; }
        if (!isValidHex(k2)) { adminKeyError(2); return; }

        SettingsDraft draft = store.loadWithSecurity();
        draft.setAdminKey(0, k0);
        draft.setAdminKey(1, k1);
        draft.setAdminKey(2, k2);
        draft.setManaged(binding.securityIsManaged.isChecked());
        store.saveSecurity(draft);

        toast(R.string.security_toast_saved);
    }

    /** Hex или пусто. */
    private static boolean isValidHex(String s) {
        if (s == null || s.isEmpty()) return true;
        return s.matches("^[0-9a-fA-F]+$") && s.length() % 2 == 0;
    }

    private void adminKeyError(int index) {
        // 1-based нумерация для пользователя
        Toast.makeText(requireContext(),
                getString(R.string.security_toast_bad_admin_key, index + 1),
                Toast.LENGTH_SHORT).show();
    }

    private static String textOf(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private void toast(int resId) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
