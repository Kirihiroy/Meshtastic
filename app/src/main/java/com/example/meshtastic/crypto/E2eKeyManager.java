package com.example.meshtastic.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Управляет локальной EC-парой ключей (P-256) для E2E шифрования.
 * Ключи хранятся в SharedPreferences в PKCS#8/X.509 форматах (Base64).
 * При переустановке приложения генерируются новые ключи — партнёры должны повторно
 * обменяться публичными ключами.
 */
public final class E2eKeyManager {

    private static final String TAG = "E2eKeyManager";
    private static final String PREFS = "e2e_keystore";
    private static final String PREF_PRIV = "priv_pkcs8";
    private static final String PREF_PUB = "pub_x509";

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
        String privB64 = prefs.getString(PREF_PRIV, null);
        String pubB64 = prefs.getString(PREF_PUB, null);
        if (privB64 == null || pubB64 == null) return null;
        try {
            KeyFactory kf = KeyFactory.getInstance("EC");
            PrivateKey priv = kf.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.decode(privB64, Base64.NO_WRAP)));
            PublicKey pub = kf.generatePublic(
                    new X509EncodedKeySpec(Base64.decode(pubB64, Base64.NO_WRAP)));
            return new KeyPair(pub, priv);
        } catch (Exception e) {
            Log.w(TAG, "Saved E2E keys invalid, regenerating", e);
            return null;
        }
    }

    private static KeyPair generateAndSave(SharedPreferences prefs) {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair kp = gen.generateKeyPair();
            prefs.edit()
                    .putString(PREF_PRIV, Base64.encodeToString(kp.getPrivate().getEncoded(), Base64.NO_WRAP))
                    .putString(PREF_PUB, Base64.encodeToString(kp.getPublic().getEncoded(), Base64.NO_WRAP))
                    .apply();
            return kp;
        } catch (Exception e) {
            Log.e(TAG, "E2E key generation failed", e);
            return null;
        }
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
