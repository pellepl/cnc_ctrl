package com.pelleplutt.cnc.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.pelleplutt.cnc.Controller;
import com.pelleplutt.cnc.Essential;
import com.pelleplutt.comm.Callback;
import com.pelleplutt.comm.Comm;
import com.pelleplutt.comm.CommArgument;
import com.pelleplutt.util.HexUtil;
import com.pelleplutt.util.Log;

public class CommMux implements Callback {
  public static final int PROTOCOL_CNC = 0x01;
  public static final int PROTOCOL_FILE = 0x02;
  public static final int PROTOCOL_SYS = 0xff;
  
  
  final boolean DBG = false;

  // Current communication time
  volatile long commTime = 0;
  Comm comm;
  Map<Short, Transport> ackDisp = new HashMap<Short, Transport>();
  List<Transport> transports = new ArrayList<Transport>();
  
  public void addTransport(Transport t) {
    transports.add(t);
  }

  public int tx(Transport t, byte[] data, boolean ack) {
    int res;
    data[0] = (byte)t.getProtocolId();
    res = comm.tx(Essential.COMM_OTHER_ADDRESS, data, ack);
    if (res >= Comm.R_COMM_OK) {
      synchronized(this) {
        ackDisp.put((short)res, t);
      }
      if (DBG) Log.println("tx " + res + " : " + t.getClass().getName());
    }
    return res;
  }
  
  public void txNodeAlert(int type, byte[] extra) {
    comm.alert(type, extra);
  }
  
  // Comm interface
  
  @Override
  public int ack(CommArgument rx, byte[] data) {
    int res = Comm.R_COMM_OK;
    Transport t;
    synchronized(this) {
      t = ackDisp.get(rx.seqno);
      if (t != null) {
        ackDisp.remove(rx.seqno);
      }
    }
    if (t != null) {
      if (DBG) Log.println("ack " + rx.seqno + " : " + t.getClass().getName());
        res = t.ack(rx, data);
    } else {
      Log.println("COMMMUX: unknown ack seq:" + rx.seqno + " data:" + HexUtil.formatDataSimple(data));
    }
    return res;
  }

  @Override
  public int rx(CommArgument rx, byte[] data, int offset) {
    int res = Comm.R_COMM_OK;
    Transport chosen = null;
    synchronized(this) {
      for (Transport t : transports) {
        if (t.myRx(data, offset)) {
          chosen = t;
          break;
        }
      }
    }
    if (chosen == null) {
      Log.println("COMMMUX: discarded rx seq:" + rx.seqno + " data:" + HexUtil.formatDataSimple(data));
    } else {
      if (DBG) Log.println("rx " + rx.seqno + " : " + chosen.getClass().getName());
      res = chosen.rx(rx, data, offset+1);
    }
    return res;
  }

  @Override
  public void inf(CommArgument rx) {
    Log.println("COMMMUX: inf " + rx.seqno);
  }

  @Override
  public void error(CommArgument a, boolean txOtherwiseRx, int error) {
    Transport t;
    synchronized(this) {
      t = ackDisp.get(a.seqno);
      if (t != null) {
        ackDisp.remove(a.seqno);
      } else {
        Log.println("COMMMUX: comm error:" + a.seqno + " err:" + error);
      }
    }
    if (t != null) {
      t.error(a, txOtherwiseRx, error);
    }
  }

  @Override
  public long getTime() {
    return commTime;
  }

  @Override
  public long getAndAddTime() {
    return commTime++;
  }

  @Override
  public void registerComm(Comm comm) {
    this.comm = comm;
  }
  
  @Override
  public void nodeAlert(int commAddress, int type, byte[] extra) {
    Log.println("ALERT: " + commAddress + " type:" + HexUtil.toHex((char)type&0xff));
    Controller.getCncCommunicator().gotNode(commAddress, type, extra);
  }

  public interface Transport {
    public int ack(CommArgument rx, byte[] data);
    
    public int rx(CommArgument rx, byte[] data, int offset);
    
    public void error(CommArgument a, boolean txElseRx, int error);
    
    public boolean myRx(byte[] data, int offset);
    
    public int getProtocolId();
  }
}
