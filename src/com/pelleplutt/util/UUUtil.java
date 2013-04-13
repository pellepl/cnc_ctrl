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

/**
 * This could be optimized and tweaked in many ways, but I guess we have Hz to
 * spare on this side.
 */
public class UUUtil {
	public static byte[] uuencode(byte[] data, int offset, int len) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(len + 0x20);
		for (int i = offset; i < offset+len; i += 3) {
			int a = (i + 0 < data.length) ? data[i + 0] : 0;
			int b = (i + 1 < data.length) ? data[i + 1] : 0;
			int c = (i + 2 < data.length) ? data[i + 2] : 0;
			int w = 0x20 + ((a & 0xfc) >> 2);
			int x = 0x20 + (((a & 0x03)) << 4 | ((b & 0xf0) >> 4));
			int y = 0x20 + (((b & 0x0f)) << 2 | ((c & 0xc0) >> 6));
			int z = 0x20 + (c & 0x3f);
			out.write(w == 0x20 ? 0x60 : w);
			out.write(x == 0x20 ? 0x60 : x);
			out.write(y == 0x20 ? 0x60 : y);
			out.write(z == 0x20 ? 0x60 : z);
		}
		return out.toByteArray();
	}

	public static byte[] uudecode(byte[] data) {
		int len = (data[0] & 0xff) - 0x20;
		ByteArrayOutputStream out = new ByteArrayOutputStream(len);
		for (int i = 1; i < data.length; i += 4) {
			int w = data[i + 0] & 0xff;
			int x = data[i + 1] & 0xff;
			int y = data[i + 2] & 0xff;
			int z = data[i + 3] & 0xff;
			w = (w == 0x60 ? 0 : (w - 0x20)); // a6
			x = (x == 0x60 ? 0 : (x - 0x20)); // a2 + b4
			y = (y == 0x60 ? 0 : (y - 0x20)); // b4 + c2
			z = (z == 0x60 ? 0 : (z - 0x20)); // c6
			int a = (w << 2) | (x >> 4);
			int b = (x << 4) | (y >> 2);
			int c = (y << 6) | (z >> 0);
			int rem = len - out.size();
			if (rem >= 3) {
				out.write(a);
				out.write(b);
				out.write(c);
			} else if (rem >= 2) {
				out.write(a);
				out.write(b);
			} else {
				out.write(a);
			}
		}
		return out.toByteArray();
	}
}
