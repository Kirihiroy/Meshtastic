package com.example.meshtastic.crypto;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Гибридное шифрование: эфемерный ECDH (P-256) для выработки общего секрета,
 * SHA-256 в роли KDF, AES-256-GCM для шифрования сообщения.
 *
 * <p>Формат пакета:
 * [1 байт: длина epk][epk байты (X.509 EC public key)][12 байт: GCM IV][шифртекст + 16 байт тег]
 *
 * <p>Каждое сообщение использует новую эфемерную пару ключей (forward secrecy на уровне сообщения).
 */
public final class E2eCipher {

    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;

    private E2eCipher() {}

    /**
     * Зашифровать plaintext для получателя с publicKey recipientPubKey.
     * Возвращает готовый к передаче байтовый пакет.
     */
    public static byte[] encrypt(PublicKey recipientPubKey, byte[] plaintext) throws Exception {
        // Эфемерная пара ключей
        java.security.KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        java.security.KeyPair ephemeral = gen.generateKeyPair();

        // ECDH: общий секрет
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(ephemeral.getPrivate());
        ka.doPhase(recipientPubKey, true);
        byte[] shared = ka.generateSecret();

        // KDF: SHA-256(shared) → 32-байтный ключ AES-256
        byte[] aesKey = MessageDigest.getInstance("SHA-256").digest(shared);

        // AES-256-GCM шифрование
        byte[] iv = new byte[GCM_IV_LEN];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(aesKey, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ct = cipher.doFinal(plaintext);

        // Пакет: [1 byte: len(epk)][epk][iv][ct]
        byte[] epk = ephemeral.getPublic().getEncoded();
        byte[] out = new byte[1 + epk.length + GCM_IV_LEN + ct.length];
        int p = 0;
        out[p++] = (byte) (epk.length & 0xFF);
        System.arraycopy(epk, 0, out, p, epk.length);
        p += epk.length;
        System.arraycopy(iv, 0, out, p, GCM_IV_LEN);
        p += GCM_IV_LEN;
        System.arraycopy(ct, 0, out, p, ct.length);
        return out;
    }

    /**
     * Расшифровать пакет, зашифрованный {@link #encrypt}.
     * Бросает исключение при любой ошибке (неверный ключ, повреждённые данные, и т.д.).
     */
    public static byte[] decrypt(PrivateKey myPrivateKey, byte[] data) throws Exception {
        // Минимальный размер: 1 (len) + 1 (epk) + 12 (iv) + 16 (тег GCM) = 30 байт
        if (data == null || data.length < 30) {
            throw new Exception("пакет слишком короткий (" + (data == null ? 0 : data.length) + " байт)");
        }
        int p = 0;
        int epkLen = data[p++] & 0xFF;
        if (p + epkLen + GCM_IV_LEN + 16 > data.length) {
            throw new Exception("неверная длина epk=" + epkLen + " в пакете длиной " + data.length);
        }

        byte[] epkBytes = Arrays.copyOfRange(data, p, p + epkLen);
        p += epkLen;
        byte[] iv = Arrays.copyOfRange(data, p, p + GCM_IV_LEN);
        p += GCM_IV_LEN;
        byte[] ct = Arrays.copyOfRange(data, p, data.length);

        // Воссоздать эфемерный публичный ключ
        KeyFactory kf = KeyFactory.getInstance("EC");
        PublicKey ephemeralPub = kf.generatePublic(new X509EncodedKeySpec(epkBytes));

        // ECDH
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(myPrivateKey);
        ka.doPhase(ephemeralPub, true);
        byte[] shared = ka.generateSecret();

        // KDF
        byte[] aesKey = MessageDigest.getInstance("SHA-256").digest(shared);

        // AES-256-GCM расшифрование (проверит тег аутентификации)
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(aesKey, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(ct);
    }
}
