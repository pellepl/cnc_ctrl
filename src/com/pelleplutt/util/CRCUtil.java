package com.pelleplutt.util;

public class CRCUtil {
  
  public static final char CRC16_CCITT_INIT = 0xffff;
  
  public static char calcCrcCCITT(byte b, char crc) {
    char x;
    
    x = (char)(((crc>>8)^b) & 0xff);
    x ^= x>>4;
    crc = (char)((crc << 8) ^ (x << 12) ^ (x <<5) ^ x);

    return crc;
  }

  public static char calcCrcCCITT(byte[] buf) {
    return calcCrcCCITT(buf, 0, buf.length);
  }

  public static char calcCrcCCITT(byte[] buf, int offs, int len) {
    char crc = CRC16_CCITT_INIT;
    for (int i = offs; i < offs + len; i++) {
      byte b = buf[i];
      crc = calcCrcCCITT(b, crc);
    }
    return crc;
  }
  
  public static void main(String[] args) {
    byte[] buf = new byte[16];
    for (int i = 0; i < buf.length; i++) {
      buf[i] = (byte)0xee;
    }
    System.out.println(HexUtil.toHex((int)(calcCrcCCITT(buf))));
  }
}
