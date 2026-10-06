package com.xperiatouch.clock;

import android.content.Context;
import android.content.SharedPreferences;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecretStore {
    private static final String ALIAS = "xperia-touch-ha-token";
    private static final String PREFS = "xperia_touch_secrets";

    private SecretStore() { }

    static void saveHomeAssistantToken(Context context, String token) throws Exception {
        if (token == null || token.trim().isEmpty()) return;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(token.trim().getBytes(StandardCharsets.UTF_8));
        ByteBuffer packed = ByteBuffer.allocate(1 + cipher.getIV().length + encrypted.length);
        packed.put((byte) cipher.getIV().length).put(cipher.getIV()).put(encrypted);
        prefs(context).edit().putString("ha_token", Base64.encodeToString(packed.array(), Base64.NO_WRAP)).apply();
    }

    static String homeAssistantToken(Context context) throws Exception {
        String encoded = prefs(context).getString("ha_token", "");
        if (encoded.isEmpty()) return "";
        ByteBuffer packed = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP));
        int ivLength = packed.get() & 0xff;
        byte[] iv = new byte[ivLength];
        packed.get(iv);
        byte[] encrypted = new byte[packed.remaining()];
        packed.get(encrypted);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    static void clearHomeAssistantToken(Context context) {
        prefs(context).edit().remove("ha_token").apply();
    }

    static boolean hasHomeAssistantToken(Context context) {
        return !prefs(context).getString("ha_token", "").isEmpty();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        java.security.Key existing = store.getKey(ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).setKeySize(256).build());
        return generator.generateKey();
    }
}
