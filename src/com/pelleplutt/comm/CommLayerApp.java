package com.pelleplutt.comm;

import com.pelleplutt.util.AppSystem;

public class CommLayerApp extends AckLayer {

	public CommLayerApp(Comm comm) {
		super(comm);
	}

	@Override
	int tx(CommArgument tx) {
		int res = lowerLayer.tx(tx);
		return res;
	}

	@Override
	int rx(CommArgument rx) {
		return comm.callback.rx(rx, AppSystem.subByteArray(rx.data, Comm.COMM_H_SIZE, rx.len));
	}

	@Override
	int ack(CommArgument rx) {
		return comm.callback.ack(rx, AppSystem.subByteArray(rx.data, Comm.COMM_H_SIZE, rx.len));
	}

}
