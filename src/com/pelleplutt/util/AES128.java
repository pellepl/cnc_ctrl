package com.pelleplutt.util;

import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AES128 {
  private static byte[] getRawKey(byte[] seed) throws Exception {
    KeyGenerator kgen = KeyGenerator.getInstance("AES");
    SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
    sr.setSeed(seed);
    kgen.init(128, sr); // 192 and 256 bits may not be available
    SecretKey skey = kgen.generateKey();
    byte[] raw = skey.getEncoded();
    return raw;
  }

  public static byte[] encrypt(byte[] key, byte[] clear) throws Exception {
    SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
    Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
    IvParameterSpec zeroIv = new IvParameterSpec(new byte[16]);
    cipher.init(Cipher.ENCRYPT_MODE, skeySpec, zeroIv);
    byte[] encrypted = cipher.doFinal(clear);
    return encrypted;
  }

  public static byte[] decrypt(byte[] key, byte[] encrypted) throws Exception {
    SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
    Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
    IvParameterSpec zeroIv = new IvParameterSpec(new byte[16]);
    cipher.init(Cipher.DECRYPT_MODE, skeySpec, zeroIv);
    byte[] decrypted = cipher.doFinal(encrypted);
    return decrypted;
  }
  
  public static void main(String[] args) throws Exception {
    String hexU = "C9559D88A6737DBA7DC5EBFB1AF5E29283841A7B3F39EBF38204168223D440FD";
    String hex = "";
    for (char c : hexU.toCharArray()) {
      if (c != ' ') {
        hex += c;
      }
    }
    //byte key[] = HexUtil.toBytes("ffffffffffffffffffffffffffffffff");
    Log.println("ENC:" + hex);
    for (int kkk = 1; kkk < 0x100; kkk++){
      String keyN = HexUtil.toHex((char)kkk);
      String k = "";
      for (int i = 0; i < 16; i++) {
        k += keyN;
      }
      byte key[] = HexUtil.toBytes(k);
      byte cry[] = HexUtil.toBytes(hex);
      byte uncry[] = decrypt(key, cry);
      Log.println("DEC:" + HexUtil.formatDataSimple(uncry) + " KEY:" + k);
    }
    
  }
}