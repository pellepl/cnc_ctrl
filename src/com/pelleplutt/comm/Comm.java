package com.pelleplutt.comm;

import java.util.ArrayList;
import java.util.List;

public class Comm {
	public static final int COMM_MAX_USERS = 4;
	public static final int COMM_MAX_PENDING = 4;

	
	public static final int COMM_NWK_BROADCAST = 0;
	public static final int COMM_LNK_MAX_DATA = 256;
	public static final int COMM_APP_MAX_DATA = COMM_LNK_MAX_DATA - 1 - 2;
	
	public static final int R_COMM_OK = 0;

	public static final int R_COMM_PHY_FAIL = -1;
	public static final int R_COMM_PHY_TMO = -2;

	public static final int R_COMM_LNK_PRE_FAIL = -3;
	public static final int R_COMM_LNK_CRC_FAIL = -4;

	public static final int R_COMM_NWK_NOT_ME = -5;
	public static final int R_COMM_NWK_TO_SELF = -6;
	public static final int R_COMM_NWK_BAD_ADDR = -7;

	public static final int R_COMM_TRA_PEND_Q_FULL = -8;
	public static final int R_COMM_TRA_ACK_Q_FULL = -9;
	public static final int R_COMM_TRA_NO_ACK = -10;
	public static final int R_COMM_TRA_CANNOT_ACK_BROADCAST = -11;

	public static final int R_COMM_APP_NOT_AN_ACK = -12;
	
	public static final int COMM_TRA_INF_PING        = 0x01;
	public static final int COMM_TRA_INF_CONGESTION  = 0x02;
	public static final int COMM_TRA_INF_PONG        = 0x81;
	
	// bits set by txer/rxer, will be tranceived in packet
	public static final int COMM_FLAG_REQACK_BIT    = (1<<0);  /* indicates that ack is requested */
	public static final int COMM_FLAG_ISACK_BIT     = (1<<1);  /* indicates that this is an ack */
	public static final int COMM_FLAG_INF_BIT       = (1<<2);  /* indicates an info packet */
  public static final int COMM_FLAG_RESENT_BIT    = (1<<3);  /* indicates a resent packet */
	// status flags set in transport layer, flags used lacally only
	public static final int COMM_STAT_RESEND_BIT    = (1<<4);  /* indicates a packet whose ack is already registered - ie packet is resent */
	public static final int COMM_STAT_ACK_MISS_BIT  = (1<<5);  /* indicates an ack for an already acked packet or a packet not wanting ack */
	// status flags set in app layer
	public static final int COMM_STAT_REPLY_BIT     = (1<<6);  /* indicates that this message will be acked on app level */
  public static final int COMM_STAT_ALERT_BIT     = (1<<7);  /* indicates an alert packet */

	
  public static final int COMM_H_SIZE = CommLayerNetwork.COMM_H_SIZE_NWK + CommLayerTransport.COMM_H_SIZE_TRA;
	public static final int COMM_H_SIZE_ALERT = 2;
	
	public static final int COMM_ACK_THROTTLE = 0;
	public static final long COMM_RESEND_TICK = 2;
	public static final int COMM_MAX_RESENDS = 5;
	public static final boolean COMM_ACK_DIRECTLY = true;
	
	public int addr;
	public Callback callback;
	CommRxer rxer;
	CommTxer txer;
	List<Layer> layers = new ArrayList<Layer>();
	CommLayerPhy phy;
	Layer app;

	boolean userDifferentiation = true;
	
	public Comm(int addr, Callback callback, CommRxer rxer, CommTxer txer) {
		this.addr = addr;
		this.callback = callback;
		this.rxer = rxer;
		this.txer = txer;
		phy = new CommLayerPhy(this);
		Layer lnk = new CommLayerLink(this);
		Layer nwk = new CommLayerNetwork(this);
		Layer tra = new CommLayerTransport(this);
		app = new CommLayerApp(this);
		
		phy.setAdjacentLayers(null, lnk);
		lnk.setAdjacentLayers(phy, nwk);
		nwk.setAdjacentLayers(lnk, tra);
		tra.setAdjacentLayers(nwk, app);
		app.setAdjacentLayers(tra, null);
		
		layers.add(phy);
		layers.add(lnk);
		layers.add(nwk);
		layers.add(tra);
		layers.add(app);
		
		callback.registerComm(this);
	}
	
  public int tx(int dst, byte[] data, boolean ack) {
    CommArgument tx = new CommArgument();
    tx.data = new byte[COMM_LNK_MAX_DATA];
    System.arraycopy(data, 0, tx.data, COMM_H_SIZE, data.length);
    tx.dataIx = Comm.COMM_H_SIZE;
    tx.dst = dst;
    tx.len = data.length;
    tx.flags = (char)(ack ? Comm.COMM_FLAG_REQACK_BIT : 0);
    int res = app.tx(tx);
    return res == Comm.R_COMM_OK ? (int)(0xfff & tx.seqno) : res;
  }
  
  public int alert(int type, byte[] data) {
    CommArgument tx = new CommArgument();
    tx.data = new byte[COMM_LNK_MAX_DATA];
    if (data != null) {
      System.arraycopy(data, 0, tx.data, COMM_H_SIZE - CommLayerTransport.COMM_H_SIZE_TRA + Comm.COMM_H_SIZE_ALERT, data.length);
      tx.len = 2 + data.length;
    } else {
      tx.len = 2;
    }
    tx.data[COMM_H_SIZE - CommLayerTransport.COMM_H_SIZE_TRA] = (byte)addr; 
    tx.data[COMM_H_SIZE - CommLayerTransport.COMM_H_SIZE_TRA + 1] = (byte)type; 
    tx.dataIx = Comm.COMM_H_SIZE - CommLayerTransport.COMM_H_SIZE_TRA;
    tx.flags = (char)(Comm.COMM_STAT_ALERT_BIT);
    int res = app.tx(tx);
    return res;
  }
  
	public int ping(int dst) {
		CommArgument tx = new CommArgument();
		tx.data = new byte[COMM_H_SIZE + 1];
		tx.data[COMM_H_SIZE] = (byte)COMM_TRA_INF_PING;
		tx.dataIx = Comm.COMM_H_SIZE;
		tx.dst = dst;
		tx.len = 1;
		tx.flags = Comm.COMM_FLAG_INF_BIT;
		return app.tx(tx);
	}
	
	public int reply(CommArgument rtx, byte[] data) {
		if ((rtx.flags & Comm.COMM_FLAG_REQACK_BIT) == 0) {
			return Comm.R_COMM_APP_NOT_AN_ACK;
		}
		System.arraycopy(data, 0, rtx.data, rtx.dataIx, data.length);
		rtx.len = data.length;
		rtx.flags = Comm.COMM_STAT_REPLY_BIT | Comm.COMM_FLAG_ISACK_BIT;
		int tmpSrc = rtx.src;
		rtx.src = rtx.dst;
		rtx.dst = tmpSrc;
		rtx.timestamp = callback.getTime();
		return app.tx(rtx);
	}
	
	public void tick(long time) {
		for (Layer layer : layers) {
			if (layer instanceof TickListener) {
				((TickListener)layer).tick(time);
			}
		}
	}

	CommArgument rxArg = new CommArgument();
	
	public int phyRx() {
		return phy.rx(rxArg);
	}

	public long getAndAddTime() {
		return callback.getAndAddTime();
	}

  public int getAddress() {
    return addr;
  }
}
