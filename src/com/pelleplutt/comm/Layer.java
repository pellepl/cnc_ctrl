package com.pelleplutt.comm;

public abstract class Layer {
	Layer upperLayer;
	Layer lowerLayer;
	Comm comm;
	
	public Layer(Comm comm) {
		this.comm = comm;
	}
	
	public void setAdjacentLayers(Layer lowerLayer, Layer upperLayer) {
		this.lowerLayer = lowerLayer;
		this.upperLayer = upperLayer;
	}
	
	abstract int tx(CommArgument tx);
	abstract int rx(CommArgument rx);
}
