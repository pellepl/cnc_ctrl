package com.pelleplutt.cnc.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.pelleplutt.comm.Comm;
import com.pelleplutt.comm.CommArgument;
import com.pelleplutt.comm.CommRxer;
import com.pelleplutt.comm.CommTimeoutException;
import com.pelleplutt.comm.CommTxer;
import com.pelleplutt.util.AppSystem;
import com.pelleplutt.util.HexUtil;
import com.pelleplutt.util.Log;

public class CNCCommunicationUDP extends CNCCommunication implements CommRxer, CommTxer {
  static final int UDP_PORT = 0xcafe;
  DatagramSocket udpBroadcastSocket;
  DatagramSocket udpUnicastSocket;
  InetAddress broadcastAddr;
  DatagramPacket broadcastPacket;

  byte[] uniBuf = new byte[512];
  byte[] brdBuf = new byte[512];
  
  final Object RX_LOCK = new Object();
  
  PipedInputStream rxBufIn;
  PipedOutputStream rxBufOut;
  
  Map<Integer, InetAddress> ipMap = new HashMap<Integer, InetAddress>();
    
  public static InetAddress getBroadcastAddress() throws SocketException {
    Enumeration<NetworkInterface> nie = NetworkInterface.getNetworkInterfaces();
    while (nie.hasMoreElements()) {
      NetworkInterface ni = nie.nextElement();
      if (ni.isUp() && !ni.isLoopback()) {
        List<InterfaceAddress> ial = ni.getInterfaceAddresses();
        for (InterfaceAddress ifcAddr : ial) {
          InetAddress brdcastAddr = ifcAddr.getBroadcast();
          if (brdcastAddr != null) {
            return brdcastAddr;
          }
        }
      }
    }
    return null;
  }
  
  @Override
  protected CommRxer createRxer() {
    return this;
  }

  @Override
  protected CommTxer createTxer() {
    return this;
  }

  @Override
  protected void connectImpl(String port, int baud) throws Exception {
    broadcastAddr = getBroadcastAddress();
    udpBroadcastSocket = new DatagramSocket(UDP_PORT);
    udpUnicastSocket = new DatagramSocket();
    
    rxBufIn = new PipedInputStream();
    rxBufOut = new PipedOutputStream(rxBufIn);
    
    Log.println("UDP port:" + UDP_PORT + ", brdcast addr:" + broadcastAddr.getHostAddress());

    new Thread(new Runnable() {
      public void run() {
        Log.println("comm-udp-uni rx started");
        while (running) {
          DatagramPacket udpPkt = new DatagramPacket(uniBuf, uniBuf.length);
          try {
            udpUnicastSocket.receive(udpPkt);
            if (running) {
              synchronized (RX_LOCK) {
                rxBufOut.write(udpPkt.getData(),0,udpPkt.getLength());
                RX_LOCK.notifyAll();
              }
            }
          } catch (IOException e) {
            fatalError();
            break;
          }
        }
        Log.println("comm-udp-uni rx ended");
      }
    }, "comm-udp-uni").start();
    new Thread(new Runnable() {
      public void run() {
        Log.println("comm-udp-brd rx started");
        while (running) {
          DatagramPacket udpPkt = new DatagramPacket(brdBuf, brdBuf.length);
          try {
            udpBroadcastSocket.receive(udpPkt);
            broadcastPacket = udpPkt;
            if (running) {
              synchronized (RX_LOCK) {
                rxBufOut.write(udpPkt.getData(),0,udpPkt.getLength());
                RX_LOCK.notifyAll();
              }
            }
          } catch (IOException e) {
            fatalError();
            break;
          }
        }
        Log.println("comm-udp-brd rx ended");
      }
    }, "comm-udp-brd").start();
  }

  @Override
  protected void disconnectMedia() throws IOException {
    synchronized (RX_LOCK) {
      RX_LOCK.notifyAll();
    }
    if (udpBroadcastSocket != null) udpBroadcastSocket.close();
    if (udpUnicastSocket != null) udpUnicastSocket.close();
  }
  
  @Override
  public void gotNode(int address, int type, byte[] extra) {
    if (broadcastPacket!= null && broadcastPacket.getPort() == UDP_PORT) {
      ipMap.put(address, broadcastPacket.getAddress());
      Log.println("UDP client registered: " + address + " @ " + broadcastPacket.getAddress().getHostAddress());
      if (address == comm.getAddress()) {
        Log.println("this is me..!");
      }
    }
  }

  // CommTxer impl

  ByteArrayOutputStream tmpTx = new ByteArrayOutputStream();
  
  @Override
  public int tx(int i) {
    tmpTx.write(i);
    return Comm.R_COMM_OK;
  }

  @Override
  public int flush(CommArgument tx) throws IOException {
    byte[] pkt = tmpTx.toByteArray();
    tmpTx.reset();
    if ((tx.flags & Comm.COMM_STAT_ALERT_BIT) != 0) {
      // Send a broadcast alert packet
      DatagramPacket udpPkt = 
          new DatagramPacket(pkt, pkt.length, broadcastAddr, UDP_PORT);
      udpBroadcastSocket.send(udpPkt);  
    } else {
      // Send a directed packet to node
      InetAddress dstIp = ipMap.get(tx.dst);
      // TODO PETER
      byte[] hardcoded = {(byte)192, (byte)168, 0, (byte)231};
      dstIp = InetAddress.getByAddress(hardcoded);
      if (dstIp == null) {
        return Comm.R_COMM_NWK_BAD_ADDR;
      }
      DatagramPacket udpPkt = 
          new DatagramPacket(pkt, pkt.length, dstIp, UDP_PORT);
      udpUnicastSocket.send(udpPkt);
    }
    signalTx();
    return Comm.R_COMM_OK;
  }

  // CommRxer impl
  
  @Override
  public int rx() throws CommTimeoutException {
    int data = -1;
    synchronized (RX_LOCK) {
      try {
        while (running && rxBufIn.available() == 0) {
          AppSystem.waitSilently(RX_LOCK, 5000);
        }
        if (running && rxBufIn.available() > 0) {
          data = rxBufIn.read();
          signalRx();
        }
      } catch (IOException e) {
        data = -1;
      }
    }
    return data;
  }
}
