package com.pelleplutt.comm;

public class CommArgument {
	public byte[] data = new byte[Comm.COMM_LNK_MAX_DATA]; 
	public int len;
	public int src;
	public int dst;
	public char flags;
	public short seqno;
	public long timestamp;
	byte txByte;
	byte rxByte;
	public int dataIx;
	
	public CommArgument(CommArgument c) {
		len = c.len;
		src = c.src;
		dst = c.dst;
		flags = c.flags;
		seqno = c.seqno;
		timestamp = c.timestamp;
		txByte = c.txByte;
		rxByte = c.rxByte;
		dataIx = c.dataIx;
		System.arraycopy(c.data, 0, data, 0, Comm.COMM_LNK_MAX_DATA);
	}

	public CommArgument() {
	}
}
