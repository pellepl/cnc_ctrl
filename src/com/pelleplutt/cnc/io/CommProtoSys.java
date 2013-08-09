package com.pelleplutt.cnc.io;

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

import com.pelleplutt.cnc.io.CommMux.Transport;
import com.pelleplutt.comm.Comm;
import com.pelleplutt.comm.CommArgument;
import com.pelleplutt.util.Log;

public class CommProtoSys implements Transport {
  FileChannel transmitFileCh;
  RandomAccessFile transmitFile = null;
  
  public static final int  COMM_PROTOCOL_SYS_ALIVE      = 0x00;

  // Transport impl

  @Override
  public int ack(CommArgument rx, byte[] data) {
    return Comm.R_COMM_OK;
  }

  @Override
  public synchronized int rx(CommArgument rx, byte[] data, int offset) {
    return Comm.R_COMM_OK;
  }

  @Override
  public void error(CommArgument a, boolean txElseRx, int error) {
    Log.println("COMMSYS error pktseq:" + a.seqno + " err:" + error);
  }

  @Override
  public boolean myRx(byte[] data, int offset) {
    int proto = data[offset] & 0xff;
    return proto == CommMux.PROTOCOL_SYS;
  }
  
  @Override
  public int getProtocolId() {
    return CommMux.PROTOCOL_SYS;
  }
}
