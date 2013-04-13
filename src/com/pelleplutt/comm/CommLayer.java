package com.pelleplutt.comm;

public abstract class CommLayer {
	CommLayer upperLayer;
	CommLayer lowerLayer;
	Comm comm;
	
	public CommLayer(Comm comm) {
		this.comm = comm;
	}
	
	public void setAdjacentLayers(CommLayer lowerLayer, CommLayer upperLayer) {
		this.lowerLayer = lowerLayer;
		this.upperLayer = upperLayer;
	}
	
	abstract int tx(CommArgument tx);
	abstract int rx(CommArgument rx);
}
