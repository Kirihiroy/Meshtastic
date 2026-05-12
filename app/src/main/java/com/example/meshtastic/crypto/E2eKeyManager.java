package com.example.meshtastic.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Управляет локальной EC-парой ключей (P-256) для E2E шифрования.
 * <p>Приватный ключ шифруется AES-256-GCM перед записью в SharedPreferences;
 * AES-ключ хранится в Android Keystore (alias {@code e2e_wrap_key_v1}).
 * Публичный ключ сохраняется в открытом виде (X.509 + Base64).
 * При переустановке приложения или очистке данных генерируется новая пара.
 */
public final class E2eKeyManager {

    private static final String TAG = "E2eKeyManager";
    private static final String PREFS = "e2e_keystore";

    // Старый незашифрованный формат — для миграции на новый.
    private static final String PREF_PRIV_LEGACY = "priv_pkcs8";
    // Новый формат: PKCS#8, обёрнутый AES-GCM.
    private static final String PREF_PRIV_ENC = "priv_pkcs8_enc";
    private static final String PREF_PRIV_IV = "priv_pkcs8_iv";
    private static final String PREF_PUB = "pub_x509";

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String WRAP_KEY_ALIAS = "e2e_wrap_key_v1";
    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public E2eKeyManager(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        KeyPair loaded = tryLoad(prefs);
        if (loaded != null) {
            privateKey = loaded.getPrivate();
            publicKey = loaded.getPublic();
        } else {
            KeyPair generated = generateAndSave(prefs);
            privateKey = generated != null ? generated.getPrivate() : null;
            publicKey = generated != null ? generated.getPublic() : null;
        }
    }

    private static KeyPair tryLoad(SharedPreferences prefs) {
        String pubB64 = prefs.getString(PREF_PUB, null);
        if (pubB64 == null) return null;

        // 1. Новый формат: зашифрованный PKCS#8 + IV.
        String encB64 = prefs.getString(PREF_PRIV_ENC, null);
        String ivB64 = prefs.getString(PREF_PRIV_IV, null);
        if (encB64 != null && ivB64 != null) {
            try {
                SecretKey wrap = getOrCreateWrapKey();
                byte[] iv = Base64.decode(ivB64, Base64.NO_WRAP);
                byte[] enc = Base64.decode(encB64, Base64.NO_WRAP);
                Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, wrap, new GCMParameterSpec(GCM_TAG_BITS, iv));
                byte[] pkcs8 = cipher.doFinal(enc);
                return buildKeyPair(pkcs8, Base64.decode(pubB64, Base64.NO_WRAP));
            } catch (Exception e) {
                Log.w(TAG, "Encrypted E2E key unreadable, regenerating");
                return null;
            }
        }

        // 2. Миграция со старого формата: загрузить, перешифровать, удалить открытый.
        String legacyPrivB64 = prefs.getString(PREF_PRIV_LEGACY, null);
        if (legacyPrivB64 != null) {
            try {
                byte[] pkcs8 = Base64.decode(legacyPrivB64, Base64.NO_WRAP);
                KeyPair kp = buildKeyPair(pkcs8, Base64.decode(pubB64, Base64.NO_WRAP));
                if (savePrivateEncrypted(prefs, pkcs8)) {
                    prefs.edit().remove(PREF_PRIV_LEGACY).apply();
                }
                return kp;
            } catch (Exception e) {
                Log.w(TAG, "Legacy E2E key invalid, regenerating");
                return null;
            }
        }

        return null;
    }

    private static KeyPair generateAndSave(SharedPreferences prefs) {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair kp = gen.generateKeyPair();

            byte[] pkcs8 = kp.getPrivate().getEncoded();
            boolean saved = savePrivateEncrypted(prefs, pkcs8);
            if (!saved) {
                // Шифрование недоступно — оставляем ключи в памяти, но не сохраняем небезопасно.
                Log.w(TAG, "E2E private key not persisted (Keystore unavailable for this session)");
            }
            prefs.edit()
                    .putString(PREF_PUB, Base64.encodeToString(kp.getPublic().getEncoded(), Base64.NO_WRAP))
                    .apply();
            return kp;
        } catch (Exception e) {
            Log.e(TAG, "E2E key generation failed");
            return null;
        }
    }

    private static boolean savePrivateEncrypted(SharedPreferences prefs, byte[] pkcs8) {
        try {
            SecretKey wrap = getOrCreateWrapKey();
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, wrap);
            byte[] iv = cipher.getIV();
            byte[] enc = cipher.doFinal(pkcs8);
            prefs.edit()
                    .putString(PREF_PRIV_ENC, Base64.encodeToString(enc, Base64.NO_WRAP))
                    .putString(PREF_PRIV_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                    .apply();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "AES-GCM wrap failed: " + e.getClass().getSimpleName());
            return false;
        }
    }

    private static SecretKey getOrCreateWrapKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(WRAP_KEY_ALIAS)) {
            SecretKey existing = (SecretKey) ks.getKey(WRAP_KEY_ALIAS, null);
            if (existing != null) return existing;
        }
        KeyGenerator kg = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        kg.init(new KeyGenParameterSpec.Builder(WRAP_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }

    private static KeyPair buildKeyPair(byte[] pkcs8, byte[] x509) throws Exception {
        KeyFactory kf = KeyFactory.getInstance("EC");
        PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(x509));
        return new KeyPair(pub, priv);
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public boolean isAvailable() {
        return privateKey != null && publicKey != null;
    }

    /** Полный X.509-encoded публичный ключ в виде hex-строки (для передачи собеседнику). */
    public String getPublicKeyHex() {
        if (publicKey == null) return "";
        return bytesToHex(publicKey.getEncoded());
    }

    /** Разобрать hex-строку X.509-encoded EC публичного ключа. Бросает исключение при ошибке. */
    public static PublicKey parsePublicKey(String hex) throws Exception {
        byte[] encoded = hexToBytes(hex);
        // EC P-256 X.509-encoded ключ имеет ~91 байт; узкие границы отсекают мусор сразу.
        if (encoded.length < 50 || encoded.length > 200) {
            throw new Exception("Неверная длина EC X.509 ключа: " + encoded.length + " байт");
        }
        KeyFactory kf = KeyFactory.getInstance("EC");
        return kf.generatePublic(new X509EncodedKeySpec(encoded));
    }

    public static byte[] hexToBytes(String hex) throws Exception {
        String h = hex.trim().toLowerCase();
        if (h.length() % 2 != 0) throw new Exception("hex: нечётная длина");
        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(h.charAt(i * 2), 16);
            int lo = Character.digit(h.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new Exception("не hex-строка");
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    public static String bytesToHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
