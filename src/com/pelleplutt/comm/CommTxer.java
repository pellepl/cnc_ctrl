package com.pelleplutt.comm;

import java.io.IOException;

public interface CommTxer {
	public int tx(int i) throws IOException;
	public int flush(CommArgument tx) throws IOException;
}
