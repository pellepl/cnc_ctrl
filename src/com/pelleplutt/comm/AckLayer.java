package com.pelleplutt.comm;

public abstract class AckLayer extends Layer {
	public AckLayer(Comm comm) {
		super(comm);
	}
	
	abstract int ack(CommArgument rx);
}
