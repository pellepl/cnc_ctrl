package com.pelleplutt.comm;

public class CommLayerNetwork extends Layer {
	int addr;
	public static final int COMM_H_SIZE_NWK = 1;
	
	public CommLayerNetwork(Comm comm) {
		super(comm);
	}

	@Override
	int tx(CommArgument tx) {
		tx.src = comm.addr;
		int dst = tx.dst;
		if (dst == comm.addr && comm.userDifferentiation) {
			return Comm.R_COMM_NWK_TO_SELF;
		}
		if (dst > Comm.COMM_MAX_USERS && !comm.userDifferentiation) {
			return Comm.R_COMM_NWK_BAD_ADDR;
		}
		tx.len += COMM_H_SIZE_NWK;
		tx.dataIx -= COMM_H_SIZE_NWK;
		tx.data[tx.dataIx] = (byte)(((comm.addr << 4) & 0xf0) | (dst & 0x0f));
		
		return lowerLayer.tx(tx);
	}

	@Override
	int rx(CommArgument rx) {
		byte addresses = rx.data[rx.dataIx];
		rx.src = ((addresses & 0xf0) >> 4);
		rx.dst = ((addresses & 0x0f));
		rx.dataIx += COMM_H_SIZE_NWK;
		rx.len -= COMM_H_SIZE_NWK;
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
