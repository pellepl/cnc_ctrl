package com.pelleplutt.cnc.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.pelleplutt.cnc.Controller;
import com.pelleplutt.comm.Callback;
import com.pelleplutt.comm.Comm;
import com.pelleplutt.comm.CommRxer;
import com.pelleplutt.comm.CommTimeoutException;
import com.pelleplutt.comm.CommTxer;
import com.pelleplutt.io.Port;
import com.pelleplutt.io.PortConnector;
import com.pelleplutt.util.AppSystem;
import com.pelleplutt.util.AppSystem.Disposable;
import com.pelleplutt.util.Log;

public class CNCCommunication implements Disposable {
  PortConnector connector;
  String portString;
  int baud;
  Comm comm;
  InputStream in;
  OutputStream out;
  volatile boolean running = true;
  volatile int txTime, rxTime;
  boolean txReported;
  boolean rxReported;
  static final int MAX_TIME_TX = 5;
  static final int MAX_TIME_RX = 5;
  RxTxListener l;
  static public final long COMM_TICK_TIME = 20;

  public void setListener(RxTxListener listener) {
    this.l = listener;
  }

  public void connect(String port, int baud, Callback callback)
      throws Exception {
    if (port.startsWith("tty")) {
      port = "/dev/" + port;
    }
    portString = port;
    Port portSetting = new Port();
    portSetting.baud = baud;
    portSetting.databits = Port.BYTESIZE_8;
    portSetting.parity = Port.PARITY_NO;
    portSetting.portName = portString;
    portSetting.stopbits = Port.STOPBIT_ONE;
    portSetting.uiName = "Stewie";
    connector = PortConnector.getPortConnector();
    connector.setTimeout(7000);
    connector.connect(portSetting);
    in = connector.getInputStream();
    out = connector.getOutputStream();

    SerialHandler rxtx = new SerialHandler();
    comm = new Comm(2, callback, rxtx, rxtx);

    Thread thrRx = new Thread(new Runnable() {
      public void run() {
        Log.println("cnccomm reader started");
        while (running && comm.phyRx() == Comm.R_COMM_OK)
          ;
        Log.println("cnccomm reader ended");
        Controller.disconnect();
      }
    }, "rxer");
    thrRx.setPriority(Thread.MAX_PRIORITY);
    thrRx.start();

    Thread thrTicker = new Thread(new Runnable() {
      public void run() {
        while (running) {
          try {
            Thread.sleep(COMM_TICK_TIME);
          } catch (Throwable t) {
          }
          comm.tick(comm.getAndAddTime());

          if (txTime == MAX_TIME_TX && !txReported) {
            txReported = true;
            if (l != null) {
              l.rxtx(rxTime > 0, txTime > 0);
            }
          } else if (txTime == 1 && txReported) {
            txReported = false;
          }
          if (txTime >= 1) {
            txTime--;
            if (txTime == 0) {
              if (l != null) {
                l.rxtx(rxTime > 0, txTime > 0);
              }
            }
          }
          if (rxTime == MAX_TIME_RX && !rxReported) {
            rxReported = true;
            if (l != null) {
              l.rxtx(rxTime > 0, txTime > 0);
            }
          } else if (rxTime == 1 && rxReported) {
            rxReported = false;
          }
          if (rxTime >= 1) {
            rxTime--;
            if (rxTime == 0) {
              if (l != null) {
                l.rxtx(rxTime > 0, txTime > 0);
              }
            }
          }
        }
      }
    }, "ticker");
    thrTicker.setPriority(Thread.MIN_PRIORITY);
    thrTicker.start();

    Log.println("cnccomm connected");
    AppSystem.addDisposable(this);
  }

  private void doDisconnect() throws IOException {
    Log.println("cnccomm disconnecting");
    running = false;
    connector.disconnect();
    Log.println("cnccomm disconnected");
  }

  public void disconnect() throws IOException {
    AppSystem.dispose(this);
  }

  public void dispose() {
    try {
      doDisconnect();
    } catch (Throwable t) {
    }
    Log.println("disposed");
  }

  class SerialHandler implements CommRxer, CommTxer {
    @Override
    public void tx(int i) {
      try {
        txTime = MAX_TIME_TX;
        out.write(i);
      } catch (IOException ioe) {
        Log.printStackTrace(ioe);
        running = false;
      }
    }

    @Override
    public int rx() throws CommTimeoutException {
      try {
        int c = in.read();
        rxTime = MAX_TIME_RX;
        return c;
      } catch (IOException ioe) {
        Log.printStackTrace(ioe);
        running = false;
      }
      return -1;
    }
  }

  public interface RxTxListener {
    public void rxtx(boolean rx, boolean tx);
  }

}
