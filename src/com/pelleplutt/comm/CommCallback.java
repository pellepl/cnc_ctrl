package com.pelleplutt.comm;

public interface CommCallback {
	public void ack(CommArgument rx, byte[] data);
	
	public void rx(CommArgument rx, byte[] data);
	
	public void error(CommArgument a, boolean txOtherwiseRx, int error);
	
	public long getTime();
}
