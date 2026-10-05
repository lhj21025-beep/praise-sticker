package kr.family.praisesticker;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

final class SecureSession {
  static javax.crypto.SecretKey key() throws Exception {
    KeyStore s = KeyStore.getInstance("AndroidKeyStore");
    s.load(null);
    if (!s.containsAlias("praise-session")) {
      KeyGenerator g = KeyGenerator.getInstance("AES", "AndroidKeyStore");
      g.init(
          new KeyGenParameterSpec.Builder(
                  "praise-session", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build());
      g.generateKey();
    }
    return (javax.crypto.SecretKey) s.getKey("praise-session", null);
  }

  static void save(Context c, String value) {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key());
      c.getSharedPreferences("session", 0)
          .edit()
          .putString("data", Base64.encodeToString(cipher.doFinal(value.getBytes("UTF-8")), 2))
          .putString("iv", Base64.encodeToString(cipher.getIV(), 2))
          .apply();
    } catch (Exception e) {
      clear(c);
    }
  }

  static String read(Context c) {
    try {
      android.content.SharedPreferences p = c.getSharedPreferences("session", 0);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          key(),
          new GCMParameterSpec(128, Base64.decode(p.getString("iv", ""), 2)));
      return new String(cipher.doFinal(Base64.decode(p.getString("data", ""), 2)), "UTF-8");
    } catch (Exception e) {
      return "";
    }
  }

  static void clear(Context c) {
    c.getSharedPreferences("session", 0).edit().clear().apply();
  }
}
