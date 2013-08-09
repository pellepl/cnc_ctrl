package com.pelleplutt.cnc.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import com.pelleplutt.cnc.Controller;
import com.pelleplutt.cnc.io.CommMux.Transport;
import com.pelleplutt.comm.Comm;
import com.pelleplutt.comm.CommArgument;
import com.pelleplutt.util.AppSystem;
import com.pelleplutt.util.CRCUtil;
import com.pelleplutt.util.HexUtil;
import com.pelleplutt.util.Log;

public class CommProtoFile implements Transport {
  FileChannel transmitFileCh;
  RandomAccessFile transmitFile = null;
  
  public static final int  COMM_PROTOCOL_FILE_TRANSFER_R      = 0x80;
  public static final int  COMM_PROTOCOL_FILE_TRANSFER_A      = 0x81;
  public static final int  COMM_FILE_REPLY_OK                 = 0x04;
  public static final int  COMM_FILE_MAX_DATA_PKT             = Comm.COMM_APP_MAX_DATA - 5 - 1;

  final Object WAIT_ACK = new Object();
  volatile boolean ack = false;
  volatile byte[] ackData;
  List<Integer> seqSent = new ArrayList<Integer>();
  
  byte[] sendCommand(int cmd, Object... params) {
    byte[] pkt = Controller.constructPacket(cmd, params);
    synchronized (WAIT_ACK) {
      ack = false;
      ackData = null;
      int res = Controller.tx(this, pkt, true);
      Log.println("PKT_TX  ===> " + res);
      if (res >= 0) {
        int timeout = 0;
        while (!ack && timeout++ < 3) {
          AppSystem.waitSilently(WAIT_ACK, 1000);
        }
      }
    }
    return ackData;
  }

  public void sendFile(File f) {
    char crc = 0;
    seqSent.clear();
    Log.println("comm file sending file req: " + f.getName());
    try {
      transmitFile = new RandomAccessFile(f, "r");
      transmitFileCh = transmitFile.getChannel();
      transmitFileCh.position(0);
      crc = CRCUtil.calcCrcCCITT(transmitFileCh);
      transmitFileCh.position(0);
    } catch (FileNotFoundException e) {
      Log.printStackTrace(e);
      return;
    } catch (IOException e) {
      Log.printStackTrace(e);
      return;
    }
    byte[] res = sendCommand(COMM_PROTOCOL_FILE_TRANSFER_R, (int) 0xffffffff,
        (int) f.length(), (short)crc, (String) f.getName());
    //Log.println("comm file req res data: " + HexUtil.formatData(res));
    int r = (res[0] & 0xff);
    if (r != COMM_FILE_REPLY_OK) {
      try {
        if (transmitFile != null) {
          transmitFile.close();
        }
      } catch (IOException e) {
        Log.printStackTrace(e);
      } finally {
      }
    }
    // Log.println("res:" + HexUtil.toHex(r));
  }

  // Transport impl

  @Override
  public int ack(CommArgument rx, byte[] data) {
    synchronized (WAIT_ACK) {
      ack = true;
      ackData = new byte[data.length];
      System.arraycopy(data, 0, ackData, 0, data.length);
      Log.println("PKT_ACK <=!= " + rx.seqno);
      WAIT_ACK.notify();      
    }
    return Comm.R_COMM_OK;
  }

  @Override
  public synchronized int rx(CommArgument rx, byte[] data, int offset) {

    int cmd = data[offset] & 0xff;
    if (cmd == COMM_PROTOCOL_FILE_TRANSFER_A) {
      int res = (int) (data[offset + 1] & 0xff);
      final int seq = Controller.arrtoi(data, offset + 2);
      Log.println(seqSent);
      if (seqSent.contains(seq) && (rx.flags & Comm.COMM_FLAG_RESENT_BIT) != 0) {
        // already got this request
        Log.println("PKT_RX  <=== " + rx.seqno + " ALREADY PROCESSED");
        return Comm.R_COMM_OK;
      }
      // store that we got this request to filter out resends
      if (seqSent.size() >= 3) {
        seqSent.remove(0);
      }
      seqSent.add(seq);
      Log.println("PKT_RX  <=== " + rx.seqno);
      Log.println("COMMFILE RX <-------- seq: " + seq + " result " + (res==4 ? "OK" : "BAD"));
      if (res == COMM_FILE_REPLY_OK) {
        int ix = seq * COMM_FILE_MAX_DATA_PKT;
        try {
          transmitFileCh.position(ix);
          int len = (int) Math.min(transmitFileCh.size(), ix
              + COMM_FILE_MAX_DATA_PKT)
              - ix;
          final ByteBuffer fileData = ByteBuffer.allocate(len);
          transmitFileCh.read(fileData);
          new Thread(new Runnable() {
            public void run() {
              Log.println("COMMFILE TX --------> seq: " + seq);
              byte[] ack = sendCommand(COMM_PROTOCOL_FILE_TRANSFER_R, seq,
                  fileData);
              Log.println("COMMFILE ACK <---!--- seq: " + seq + " " + HexUtil.formatDataSimple(ack));
              Log.println("");
            }
          }, "filetx").start();
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }
    return Comm.R_COMM_OK;
  }

  @Override
  public void error(CommArgument a, boolean txElseRx, int error) {
    Log.println("COMMFILE error pktseq:" + a.seqno + " err:" + error);
  }

  @Override
  public boolean myRx(byte[] data, int offset) {
    int proto = data[offset] & 0xff;
    return proto == CommMux.PROTOCOL_FILE;
  }
  
  @Override
  public int getProtocolId() {
    return CommMux.PROTOCOL_FILE;
  }
}
