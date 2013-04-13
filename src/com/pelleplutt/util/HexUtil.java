/*
 Copyright (c) 2012, Peter Andersson pelleplutt1976@gmail.com

 Permission to use, copy, modify, and/or distribute this software for any
 purpose with or without fee is hereby granted, provided that the above
 copyright notice and this permission notice appear in all copies.

 THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
 REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
 AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
 INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
 LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
 OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 PERFORMANCE OF THIS SOFTWARE.
*/
package com.pelleplutt.util;

import java.io.ByteArrayOutputStream;

public class HexUtil {

  public static String toHex(char b) {
    if (b == (char) -1) {
      return "..";
    }
    int e = (b >> 4) & 0x0f;
    int d = b & 0x0f;
    return Character.toString((char) (e > 9 ? e - 10 + 'a' : e + '0'))
        + Character.toString((char) (d > 9 ? d - 10 + 'a' : d + '0'));
  }

  public static String toHex16(char b) {
    StringBuilder s = new StringBuilder(4);
    for (int i = 1; i >= 0; i--) {
      s.append(toHex((char) ((b >> (i * 8)) & 0xff)));
    }
    return s.toString();
  }

  public static String toHex(int b) {
    StringBuilder s = new StringBuilder(8);
    for (int i = 3; i >= 0; i--) {
      s.append(toHex((char) ((b >> (i * 8)) & 0xff)));
    }
    return s.toString();
  }

  public static String toHex(long address) {
    return toHex((int) address);
  }

  public static String formatData(byte[] data) {
    int col = 0;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < data.length; i++) {
      int d = data[i] & 0xff;
      sb.append((d < 0x10 ? "0" : "") + Integer.toHexString(d));
      col++;
      if ((col & 0x3) == 0) {
        sb.append(' ');
      }
      if (col >= 8 * 4) {
        sb.append("\r\n");
        col = 0;
      }
    }
    return sb.toString();
  }

  public static String formatDataSimple(byte[] data) {
    return formatDataSimple(data, (char)0);
  }

  public static String formatDataSimple(byte[] data, int offs, int len) {
    return formatDataSimple(data, offs, len, (char)0);
  }
  
  public static String formatDataSimple(byte[] data, char separatorChar) {
    if (data == null) return "<null>";
    return formatDataSimple(data, 0, data.length, separatorChar);
  }
  
  public static String formatDataSimple(byte[] data, int offs, int len, char separatorChar) {
    if (data == null) return "<null>";
    int col = 0;
    StringBuilder sb = new StringBuilder();
    for (int i = offs; i < Math.min(data.length, offs+len); i++) {
      int d = data[i] & 0xff;
      sb.append((d < 0x10 ? '0' : "") + Integer.toHexString(d));
      col++;
      if (separatorChar != 0 && (col & 0x3) == 0) {
        sb.append(' ');
      }
    }
    return sb.toString();
  }
  
  public static int chartonibble(char hex) {
    if (hex >= '0' && hex <= '9') {
      return hex - '0';
    } else if (hex >= 'A' && hex <= 'F') {
      return hex - 'A' + 10;
    } else if (hex >= 'a' && hex <= 'f') {
      return hex - 'a' + 10;
    } else {
      return -1;
    }
  }

  public static byte[] toBytes(String hex) {
    return toBytes(hex, false);
  }
  
  public static byte[] toBytes(String hex, boolean errorAtUndefinedChars, int dataLen) {
    ByteArrayOutputStream hout = new ByteArrayOutputStream();
    int l = dataLen*2;
    for (int i = 0; i < l; i += 2) {
      int hi = chartonibble(hex.charAt(i));
      if (errorAtUndefinedChars && hi == -1) {
        throw new Error("Undefined hex char '" + hex.charAt(i) + "'");
      }
      int lo = chartonibble(hex.charAt(i+1));
      if (errorAtUndefinedChars && hi == -1) {
        throw new Error("Undefined hex char '" + hex.charAt(i+1) + "'");
      }
      byte b = (byte)((hi << 4) | lo);
      hout.write(b);
    }
    return hout.toByteArray();
  }
  
  public static byte[] toBytes(String hex, boolean errorAtUndefinedChars) {
    return toBytes(hex, errorAtUndefinedChars, hex.length()/2);
  }
}
