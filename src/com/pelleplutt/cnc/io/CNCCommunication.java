package com.pelleplutt.cnc.io;

import java.io.IOException;

import com.pelleplutt.cnc.Controller;
import com.pelleplutt.cnc.Essential;
import com.pelleplutt.comm.Callback;
import com.pelleplutt.comm.Comm;
import com.pelleplutt.comm.CommRxer;
import com.pelleplutt.comm.CommTxer;
import com.pelleplutt.util.AppSystem;
import com.pelleplutt.util.AppSystem.Disposable;
import com.pelleplutt.util.Log;

public abstract class CNCCommunication implements Disposable {
  Comm comm;
  volatile boolean running = true;
  volatile int txTime, rxTime;
  boolean txReported;
  boolean rxReported;
  static final int MAX_TIME_TX = 5;
  static final int MAX_TIME_RX = 5;
  RxTxListener l;
  static public final long COMM_TICK_TIME = 90;

  public void setListener(RxTxListener listener) {
    this.l = listener;
  }
  
  public void gotNode(int address, int type, byte[] extra) {
  }

  protected abstract CommRxer createRxer();
  protected abstract CommTxer createTxer();
  protected abstract void connectImpl(String port, int baud)
      throws Exception;
  
  public void connect(String port, int baud, Callback callback)
      throws Exception {
    connectImpl(port, baud);
    
    comm = new Comm(Essential.COMM_ADDRESS, callback, createRxer(), createTxer());

    Thread thrRx = new Thread(new Runnable() {
      public void run() {
        Log.println("cnccomm reader started");
        while (running && comm.phyRx() == Comm.R_COMM_OK)
          ;
        Log.println("cnccomm reader ended");
        Controller.disconnect();
      }
    }, "comm-rxer");
    thrRx.setPriority(Thread.MAX_PRIORITY);
    thrRx.start();

    Thread thrTicker = new Thread(new Runnable() {
      public void run() {
        while (running) {
          try {
            Thread.sleep(COMM_TICK_TIME);
          } catch (Throwable t) {
          }
          if (!running) break;
          
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
    }, "comm-ticker");
    thrTicker.setPriority(Thread.MIN_PRIORITY);
    thrTicker.start();

    Log.println("cnccomm connected");
    AppSystem.addDisposable(this);
  }

  abstract protected void disconnectMedia() throws IOException;
  
  protected void fatalError() {
    running = false;
  }
  
  protected void signalTx() {
    txTime = MAX_TIME_TX;
  }
  
  protected void signalRx() {
    rxTime = MAX_TIME_RX;
  }

  private void doDisconnect() throws IOException {
    Log.println("cnccomm disconnecting");
    running = false;
    disconnectMedia();
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

  public interface RxTxListener {
    public void rxtx(boolean rx, boolean tx);
  }
}
