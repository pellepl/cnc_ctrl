package comm.pelleplutt.util;

public class HexUtil {

	public static String toHex(char b) {
		if (b == (char)-1) {
			return "..";
		}
		int e = (b >> 4) & 0x0f;
		int d = b & 0x0f;
		return Character.toString((char) (e > 9 ? e - 10 + 'A' : e + '0'))
				+ Character.toString((char) (d > 9 ? d - 10 + 'A' : d + '0'));
	}

	public static String toHex(int b) {
		StringBuilder s = new StringBuilder(8);
		for (int i = 3; i >= 0; i--) {
			s.append(toHex((char) ((b >> (i * 8)) & 0xff)));
		}
		return s.toString();
	}
	

	public static String toHex(long address) {
		return toHex((int)address);
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
}
