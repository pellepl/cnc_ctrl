package com.pelleplutt.comm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import com.pelleplutt.util.HexUtil;
import com.pelleplutt.util.Log;


public class CommLayerTransport extends Layer implements TickListener {
	static final int COMM_TRA_SEQNO_MASK = 0xfff0;
	static final int COMM_H_SIZE_TRA = 2;
	
	Map<Integer, Short> seqnoMap = new HashMap<Integer, Short>();
	Map<Integer, List<Short>> acksRxPending = new HashMap<Integer, List<Short>>();
	List<CommTraPacket> acksTxPending = new LinkedList<CommTraPacket>();
	List<CommTraPacket> acksTxPendingRemove = new LinkedList<CommTraPacket>();
	
	public CommLayerTransport(Comm comm) {
		super(comm);
	}
	
	@Override
	public void setAdjacentLayers(Layer lowerLayer, Layer upperLayer) {
		super.setAdjacentLayers(lowerLayer, upperLayer);
		if (!(upperLayer instanceof AckLayer)) {
			throw new Error("Upper layer on transport must be of AppLayer type");
		}
	}

	@Override
	int tx(CommArgument tx) {
		int seqno;
		if (comm.userDifferentiation &&
				tx.dst == Comm.COMM_NWK_BROADCAST &&
				(tx.flags & Comm.COMM_FLAG_REQACK_BIT) != 0) {
			return Comm.R_COMM_TRA_CANNOT_ACK_BROADCAST;
		}
		if ((tx.flags & Comm.COMM_STAT_ALERT_BIT) != 0) {
		  return tx(tx, (short)0);
		}
		int u = comm.userDifferentiation ? tx.dst : 0;
		if ((tx.flags & Comm.COMM_STAT_REPLY_BIT) == 0) {
			// plain send, take nbr from sequence index for this dst and increase
			Short sseqno = this.seqnoMap.get(u);
			seqno = sseqno == null ? 0 : sseqno.shortValue(); 
		} else {
			seqno = tx.seqno;
		}
		int res = tx(tx, (short)(seqno & (COMM_TRA_SEQNO_MASK>>>4)));
		if (res >= Comm.R_COMM_OK && (tx.flags & Comm.COMM_STAT_REPLY_BIT) == 0) {
			seqnoMap.put(u, (short)(seqno+1));
		}
		return res;
	}
	
	int tx(CommArgument tx, short seqno) {
		synchronized (this) {
	    if ((tx.flags & Comm.COMM_STAT_ALERT_BIT) != 0) {
	      tx.seqno = 0;
	    } else {
  			tx.seqno = seqno;
  			short traHeader = (short)((seqno << 4) | ((tx.flags) & ~(COMM_TRA_SEQNO_MASK)));
  			tx.len += COMM_H_SIZE_TRA;
  			tx.dataIx -= COMM_H_SIZE_TRA;
  			tx.data[tx.dataIx] = (byte)(traHeader >> 8);
  			tx.data[tx.dataIx+1] = (byte)(traHeader & 0xff);
  			if ((tx.flags & Comm.COMM_FLAG_REQACK_BIT) != 0) {
  			    // this is to be acked, save for resend
  				int res = registerTxAck(tx);
  				if (res != Comm.R_COMM_OK) {
  					return res;
  				}
  			}
	    }
			return lowerLayer.tx(tx);
		}
	}
	
	int registerTxAck(CommArgument tx) {
		if (acksTxPending.size() > Comm.COMM_MAX_PENDING) {
			return Comm.R_COMM_TRA_PEND_Q_FULL;
		}
		CommTraPacket ackedTx = new CommTraPacket(tx);
		ackedTx.timestamp = comm.callback.getTime();
		short xtraHeader = (short)(Comm.COMM_FLAG_RESENT_BIT);
		
		ackedTx.tx.data[ackedTx.tx.dataIx] |= (byte)(xtraHeader >> 8);
		ackedTx.tx.data[ackedTx.tx.dataIx+1] |= (byte)(xtraHeader & 0xff);
		acksTxPending.add(ackedTx);
		return Comm.R_COMM_OK;
	}

	@Override
	int rx(CommArgument rx) {
		short traHeader = (short)(((rx.data[rx.dataIx])<<8) | (rx.data[rx.dataIx+1] & 0xff));
		rx.dataIx += COMM_H_SIZE_TRA;
		rx.len -= COMM_H_SIZE_TRA;
		char flags = (char)(traHeader & ~(COMM_TRA_SEQNO_MASK));
		int seqno = (traHeader & COMM_TRA_SEQNO_MASK) >>> 4;
		rx.flags = flags;
		rx.seqno = (short)(seqno);
		return handleRx(rx);
	}
	
	int handleRx(CommArgument rx) {
		int res = Comm.R_COMM_OK;
		if ((rx.flags & Comm.COMM_FLAG_INF_BIT) != 0) {
			// remote reports error
			res = handleRemoteInf(rx);
		} else if ((rx.flags & Comm.COMM_FLAG_REQACK_BIT) != 0) {
			// this packet wants to be acked
			res = registerRxReqAckPending(rx);
		} else if ((rx.flags & Comm.COMM_FLAG_ISACK_BIT) != 0) {
			res = gotRxAck(rx);
			if (res == Comm.R_COMM_OK) {
				if ((rx.flags & Comm.COMM_STAT_ACK_MISS_BIT) == 0) {
					if (upperLayer instanceof AckLayer) {
						((AckLayer)upperLayer).ack(rx);
					} else {
						comm.callback.ack(rx, rx.data);
					}
				}
			}
		}
		if (res == Comm.R_COMM_OK && rx.len > 0) {
		    // only send up to app if all ok and len is ok
			if ((rx.flags & (Comm.COMM_FLAG_ISACK_BIT | Comm.COMM_FLAG_INF_BIT)) == 0) {
				// acks already reported
				res = upperLayer.rx(rx);
				// now, see if app wants to/has sent ack itself
				if (res == Comm.R_COMM_OK &&
						(rx.flags & Comm.COMM_STAT_REPLY_BIT) == 1 &&
						(rx.flags & Comm.COMM_FLAG_ISACK_BIT) == 1) {
					deregisterRxAck(rx);
				}
			}
			if (Comm.COMM_ACK_DIRECTLY && 
					(rx.flags & Comm.COMM_FLAG_REQACK_BIT) != 0 &&
					(rx.flags & Comm.COMM_STAT_REPLY_BIT) == 0) {
				txPendingAcks();
			}
		}
		return res;
	}
	
	int sendInf(int dst, int inf) {
		CommArgument tx_inf = new CommArgument();
		tx_inf.data = new byte[Comm.COMM_H_SIZE + 2];
		tx_inf.dataIx = Comm.COMM_H_SIZE;
		tx_inf.dst = dst;
		tx_inf.flags = Comm.COMM_FLAG_INF_BIT;
		tx_inf.data[tx_inf.dataIx] = (byte)(inf);
		tx_inf.len = 1;
		int res = tx(tx_inf);
		return res;
	}
	
	int handleRemoteInf(CommArgument rx) {
		if (rx.len > 0) {
			int code = rx.data[rx.dataIx] & 0xff;
			switch(code) {
			case Comm.COMM_TRA_INF_PING:
				return sendInf(rx.src, Comm.COMM_TRA_INF_PONG);
			case Comm.COMM_TRA_INF_PONG:
			case Comm.COMM_TRA_INF_CONGESTION:
				comm.callback.inf(rx);
			}
		}
		return Comm.R_COMM_OK;
	}
	
	int registerRxReqAckPending(CommArgument rx) {
		int u = comm.userDifferentiation ? rx.src : 0;
		List<Short> acks = acksRxPending.get(u);
		if (acks == null) {
			acks = new ArrayList<Short>();
			acksRxPending.put(u, acks);
		}
		if (acks.contains(rx.seqno)) {
		      // already in ack queue, fill same
			rx.flags |= Comm.COMM_STAT_RESEND_BIT;
		} else {
			if (acks.size() > Comm.COMM_MAX_PENDING) {
				return Comm.R_COMM_TRA_ACK_Q_FULL;
			}
			acks.add(rx.seqno);
		}
		return Comm.R_COMM_OK;
	}

	
	void deregisterRxAck(CommArgument rx) {
		int u = comm.userDifferentiation ? rx.src : 0;
		List<Short> acks = acksRxPending.get(u);
		if (acks == null) {
			return;
		}
		acks.remove(new Short(rx.seqno));
	}
	
	int gotRxAck(CommArgument rx) {
		synchronized (this) {
			for (CommTraPacket txReqAckPkt : acksTxPending) {
				if (txReqAckPkt.tx.seqno == rx.seqno && 
						(txReqAckPkt.tx.dst == rx.src|| !comm.userDifferentiation)) {
					acksTxPending.remove(txReqAckPkt);
					return Comm.R_COMM_OK;
				}
			}
		}
		rx.flags |= Comm.COMM_STAT_ACK_MISS_BIT;
		return Comm.R_COMM_OK;
	}

	@Override
	public void tick(long time) {
		synchronized (this) {
			if (!Comm.COMM_ACK_DIRECTLY) {
				txPendingAcks();
			}
			txResend(time);
			acksTxPending.removeAll(acksTxPendingRemove);
		}
	}
	
	void txPendingAcks() {
		int acks = 0;
		Set<Integer> users = acksRxPending.keySet();
		for (Integer user : users) {
			List<Short> seqnos = acksRxPending.get(user);
			while (!seqnos.isEmpty()) {
				short seqno = seqnos.get(0);
				CommArgument ack = new CommArgument();
				ack.src = comm.addr;
				ack.dst = user;
				ack.flags = Comm.COMM_FLAG_ISACK_BIT;
				ack.len = 0;
				ack.seqno = seqno;
				ack.dataIx = Comm.COMM_H_SIZE;

				int res = tx(ack, seqno);
				if (res == Comm.R_COMM_OK){
					seqnos.remove(0);
					acks++;
					if (Comm.COMM_ACK_THROTTLE > 0 && acks > Comm.COMM_ACK_THROTTLE) {
						return;
					}
				} else {
					switch (res) {
					case Comm.R_COMM_PHY_FAIL:
			            // major error, report and bail out
						comm.callback.error(ack, true, res);
						return;
					case Comm.R_COMM_PHY_TMO:
			            // just mark this ack as free and continue silently, rely on resend
						seqnos.remove(new Short(seqno));
						break;
					default:
			            // report and mark as free, rely on resend
						comm.callback.error(ack, true, res);
						seqnos.remove(new Short(seqno));
						break;
					}
				}
			}
		}
	}
	
	void txResend(long time) {
		ListIterator<CommTraPacket> i = acksTxPending.listIterator();
		while (i.hasNext()) {
			CommTraPacket pkt = i.next();
			if (time - pkt.timestamp > Comm.COMM_RESEND_TICK) {
				if (pkt.resends > Comm.COMM_MAX_RESENDS) {
					acksTxPendingRemove.add(pkt);
					comm.callback.error(pkt.tx, true, Comm.R_COMM_TRA_NO_ACK);
					continue;
				} else {
					Log.println("resending " + HexUtil.toHex(pkt.tx.seqno) + " try #" + pkt.resends);
					pkt.resends++;
					pkt.timestamp = comm.callback.getTime();
					int t_dataIx = pkt.tx.dataIx;
					int t_len = pkt.tx.len;
					pkt.tx.flags |= Comm.COMM_FLAG_RESENT_BIT;
					int res = lowerLayer.tx(pkt.tx);
					pkt.tx.dataIx = t_dataIx;
					pkt.tx.len = t_len;
					
					switch (res) {
					case Comm.R_COMM_OK:
						break;
					case Comm.R_COMM_PHY_FAIL:
						comm.callback.error(pkt.tx, true, res);
						return;
					default:
						acksTxPendingRemove.add(pkt);
						comm.callback.error(pkt.tx, true, res);
						break;
					}
				}
			}
		}
	}
	
	class CommTraPacket {
		long timestamp;
		int resends;
		CommArgument tx;
		public CommTraPacket(CommArgument tx) {
			this.tx = new CommArgument(tx);
			this.timestamp = this.tx.timestamp;
			this.resends = 0;
		}
	}

}
