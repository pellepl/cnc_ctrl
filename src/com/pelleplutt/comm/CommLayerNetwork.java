package com.pelleplutt.comm;

import com.pelleplutt.util.AppSystem;

public class CommLayerNetwork extends Layer {
	int addr;
	public static final int COMM_H_SIZE_NWK = 1;
	
	public CommLayerNetwork(Comm comm) {
		super(comm);
	}

	@Override
	int tx(CommArgument tx) {
		byte nwkData;
		if ((tx.flags & Comm.COMM_STAT_ALERT_BIT) != 0) {
		  nwkData = 0;
		} else {
	    tx.src = comm.addr;
	    int dst = tx.dst;
	    if (dst == comm.addr && comm.userDifferentiation) {
	      return Comm.R_COMM_NWK_TO_SELF;
	    }
	    if (dst > Comm.COMM_MAX_USERS && !comm.userDifferentiation) {
	      return Comm.R_COMM_NWK_BAD_ADDR;
	    }
		  nwkData = (byte)(((comm.addr << 4) & 0xf0) | (dst & 0x0f));
		}
		tx.len += COMM_H_SIZE_NWK;
		tx.dataIx -= COMM_H_SIZE_NWK;
		tx.data[tx.dataIx] = nwkData;
		
		return lowerLayer.tx(tx);
	}

	@Override
	int rx(CommArgument rx) {
		byte addresses = rx.data[rx.dataIx];
		rx.src = ((addresses & 0xf0) >> 4);
		rx.dst = ((addresses & 0x0f));
		rx.dataIx += COMM_H_SIZE_NWK;
		rx.len -= COMM_H_SIZE_NWK;
		if (rx.src == Comm.COMM_NWK_BROADCAST && rx.dst == Comm.COMM_NWK_BROADCAST) {
		  // alert packet
		  rx.flags |= Comm.COMM_STAT_ALERT_BIT;
		  comm.callback.nodeAlert(rx.data[rx.dataIx], rx.data[rx.dataIx+1], 
		      AppSystem.subByteArray(rx.data, rx.dataIx+2, rx.len - 2));
		  return Comm.R_COMM_OK;
		}
		if (rx.src == comm.addr && comm.userDifferentiation) {
			return Comm.R_COMM_NWK_TO_SELF;
		}
		if (rx.dst == Comm.COMM_NWK_BROADCAST || rx.dst == comm.addr || !comm.userDifferentiation) {
			return upperLayer.rx(rx);
		} else {
			return Comm.R_COMM_NWK_NOT_ME;
		}
	}
}
