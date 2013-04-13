package com.pelleplutt.comm;

import java.io.IOException;

public class CommLayerPhy extends Layer {
	int consecutiveTimeouts;
	int maxConsecutiveTimeouts;
	boolean lastRxWasTimeout;
	
	public CommLayerPhy(Comm comm) {
		super(comm);
	}

	@Override
	int tx(CommArgument tx) {
		comm.txer.tx(tx.txByte);
		return Comm.R_COMM_OK;
	}

	@Override
	int rx(CommArgument rx) {
		int c;
		try {
			c = comm.rxer.rx();
			if (c == -1) throw new IOException("stream closed");
			lastRxWasTimeout = false;
			consecutiveTimeouts = 0;
			rx.rxByte = (byte)c;
			upperLayer.rx(rx);
		} catch (CommTimeoutException cte) {
			if (lastRxWasTimeout) {
				consecutiveTimeouts++;
				if (maxConsecutiveTimeouts > 0 && consecutiveTimeouts == maxConsecutiveTimeouts) {
					consecutiveTimeouts = 0;
					// TODO comm.reportError()
				}
			} else {
				lastRxWasTimeout = true;
			}
			return Comm.R_COMM_PHY_TMO;
		} catch (Throwable t) {
			t.printStackTrace();
			// TODO comm.reportError()
			return Comm.R_COMM_PHY_FAIL;
		}
		return Comm.R_COMM_OK;
	}

}
