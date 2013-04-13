package com.pelleplutt.comm;

public interface CommRxer {
	public int rx() throws CommTimeoutException;
}
