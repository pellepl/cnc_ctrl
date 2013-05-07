package com.pelleplutt.comm;

import java.io.IOException;

import com.pelleplutt.util.HexUtil;
import com.pelleplutt.util.Log;

public class CommLayerLink extends Layer {
	public static final byte COMM_LNK_PREAMBLE = 0x5a;
	public static final byte STATE_PRE = 0;
	public static final byte STATE_LEN = 1;
	public static final byte STATE_DAT = 2;
	public static final byte STATE_CRC = 3;
	
	public static final short COMM_CRC_INIT = (short)0xffff;

	int state = STATE_PRE;
	int ix = 0;
	short remoteCRC;
	short localCRC;
	
	public CommLayerLink(Comm comm) {
		super(comm);
	}

	static short crc_ccitt_16(short crc, byte data) {
		crc = (short) (((crc >> 8) & 0xff) | (crc << 8));
		crc ^= (short) (data & 0xff);
		crc ^= ((crc & 0xff) & 0xff) >> 4;
		crc ^= (crc << 8) << 4;
		crc ^= ((crc & 0xff) << 4) << 1;
		return crc;
	}
	
	int txByte(CommArgument tx, int b) {
		tx.txByte = (byte)(b & 0xff);
		return lowerLayer.tx(tx);
	}

	@Override
	synchronized int tx(CommArgument tx) {
		int res;
		short crc = COMM_CRC_INIT;
		for (int i = 0; i < tx.len; i++) {
			crc = crc_ccitt_16(crc, tx.data[i]);
		}
		
		res = txByte(tx, COMM_LNK_PREAMBLE);
		if (res != Comm.R_COMM_OK) {
			return res;
		}
		
		res = txByte(tx, tx.len-1);
		if (res != Comm.R_COMM_OK) {
			return res;
		}
		
		for (int i = 0; i < tx.len; i++) {
			res = txByte(tx, tx.data[i]);
			if (res != Comm.R_COMM_OK) {
				return res;
			}
		}

		res = txByte(tx, (crc>>8) & 0xff);
		if (res != Comm.R_COMM_OK) {
			return res;
		}
		
		res = txByte(tx, crc & 0xff);
    if (res != Comm.R_COMM_OK) {
      return res;
    }
    
    try {
      comm.txer.flush(tx);
    } catch (IOException ioe) {
      Log.printStackTrace(ioe);
      res = Comm.R_COMM_PHY_FAIL;
    }
		return res;
	}

	@Override
	int rx(CommArgument rx) {
		switch(state) {
		case STATE_PRE:
			if (rx.rxByte == COMM_LNK_PREAMBLE) {
				state = STATE_LEN;
			} else {
				Log.println("preamble fail " + HexUtil.toHex((char)rx.rxByte));
				return Comm.R_COMM_LNK_PRE_FAIL;
			}
			break;
		case STATE_LEN:
			rx.len = (int)((rx.rxByte & 0xff) + 1);
			rx.dataIx = 0;
			localCRC = COMM_CRC_INIT;
			ix = 0;
			state = STATE_DAT;
			break;
		case STATE_DAT:
			rx.data[ix++] = rx.rxByte;
			localCRC = crc_ccitt_16(localCRC, rx.rxByte);
			if (ix == rx.len) {
				ix = 1;
				state = STATE_CRC;
			}
			break;
		case STATE_CRC:
			if (ix == 1) {
				remoteCRC = (short)((rx.rxByte & 0xff) << 8);
				ix = 0;
			} else {
				remoteCRC |= (short)(rx.rxByte & 0xff);
				state = STATE_PRE;
				if (localCRC == remoteCRC) {
					return upperLayer.rx(rx);
				} else {
					Log.println("crc fail " + HexUtil.toHex(localCRC) + " != " + HexUtil.toHex(remoteCRC)); 
					return Comm.R_COMM_LNK_CRC_FAIL;
				}
			}
		}
		return Comm.R_COMM_OK;
	}
}
