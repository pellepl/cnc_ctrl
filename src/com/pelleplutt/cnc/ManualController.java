package com.pelleplutt.cnc;

import java.awt.KeyEventDispatcher;
import java.awt.event.KeyEvent;

import com.pelleplutt.cnc.io.CommProtoCnc;
import com.pelleplutt.cnc.types.Point;
import com.pelleplutt.util.AppSystem;
import com.pelleplutt.util.AppSystem.Disposable;
import com.pelleplutt.util.Log;

public class ManualController implements Disposable {
  final static int DIR_UP = 1;
  final static int DIR_DOWN = 2;
  final static int DIR_LEFT = 4;
  final static int DIR_RIGHT = 8;
  final static int DIR_IN = 16;
  final static int DIR_OUT = 32;

  int dirMask = 0;
  CommProtoCnc bridge;
  final Object LOCK_MANUAL = new Object();
  volatile boolean disposed = false;
  boolean movement;
  
  public ManualController(CommProtoCnc bridge) {
    this.bridge = bridge;
    AppSystem.addDisposable(this);
  }
  
  public void start() {
    Thread t = new Thread(new Runnable() {
      public void run() {
        Log.println("manual control started");
        manualLoop();
        Log.println("manual control stopped");
      }
    }, "manualcontrol");
    t.start();
  }
  
  void control() {
    final int steps = 800*10;
    Point feeds = Controller.getUI().getUserFeedRates();
    feeds = Controller.feedUPMtoSPS(feeds);
    int sx = 0, sy = 0, sz = 0;
    sx += (dirMask & DIR_RIGHT) != 0 ? steps : 0;
    sx -= (dirMask & DIR_LEFT) != 0 ? steps : 0;
    sy += (dirMask & DIR_UP) != 0 ? steps : 0;
    sy -= (dirMask & DIR_DOWN) != 0 ? steps : 0;
    sz += (dirMask & DIR_OUT) != 0 ? steps : 0;
    sz -= (dirMask & DIR_IN) != 0 ? steps : 0;
    try {
      bridge.cncSetXYZ(sx, feeds.x, sy, feeds.y, sz, feeds.z);
    } catch (Throwable t) {
      Log.printStackTrace(t);
    }
  }
  
  void manualLoop() {
    while (!disposed) {
      synchronized (LOCK_MANUAL) {
        while (!disposed && !movement) {
          AppSystem.waitSilently(LOCK_MANUAL, 40);
        }
        movement = false;
      }
      if (!disposed) {
        control();
      }
    }
  }
  
  // global key listener

  public final KeyEventDispatcher keyDispatcher = new KeyEventDispatcher() {
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
      if (Controller.isManualEnabled()) {
        boolean mod = false;
        if (e.getKeyCode() == KeyEvent.VK_UP) {
          if (e.getID() == KeyEvent.KEY_PRESSED || e.getID() == KeyEvent.KEY_TYPED) {
            dirMask |= DIR_UP;
          } else if (e.getID() == KeyEvent.KEY_RELEASED) {
            dirMask &= ~DIR_UP;
          }
          mod = true;
        }
        else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
          if (e.getID() == KeyEvent.KEY_PRESSED || e.getID() == KeyEvent.KEY_TYPED) {
            dirMask |= DIR_DOWN;
          } else if (e.getID() == KeyEvent.KEY_RELEASED) {
            dirMask &= ~DIR_DOWN;
          }
          mod = true;
        }
        else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
          if (e.getID() == KeyEvent.KEY_PRESSED || e.getID() == KeyEvent.KEY_TYPED) {
            dirMask |= DIR_RIGHT;
          } else if (e.getID() == KeyEvent.KEY_RELEASED) {
            dirMask &= ~DIR_RIGHT;
          }
          mod = true;
        }
        else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
          if (e.getID() == KeyEvent.KEY_PRESSED || e.getID() == KeyEvent.KEY_TYPED) {
            dirMask |= DIR_LEFT;
          } else if (e.getID() == KeyEvent.KEY_RELEASED) {
            dirMask &= ~DIR_LEFT;
          }
          mod = true;
        }
        else if (e.getKeyCode() == KeyEvent.VK_PAGE_UP) {
          if (e.getID() == KeyEvent.KEY_PRESSED || e.getID() == KeyEvent.KEY_TYPED) {
            dirMask |= DIR_OUT;
          } else if (e.getID() == KeyEvent.KEY_RELEASED) {
            dirMask &= ~DIR_OUT;
          }
          mod = true;
        }
        else if (e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
          if (e.getID() == KeyEvent.KEY_PRESSED || e.getID() == KeyEvent.KEY_TYPED) {
            dirMask |= DIR_IN;
          } else if (e.getID() == KeyEvent.KEY_RELEASED) {
            dirMask &= ~DIR_IN;
          }
          mod = true;
        }
        if (mod) {
          synchronized (LOCK_MANUAL) {
            movement = true;
            LOCK_MANUAL.notify();
          }
          return true;
        }
      }
      return false;
    }
  };

  @Override
  public void dispose() {
    Log.println("disposing");
    disposed = true;
    synchronized (LOCK_MANUAL) {
      LOCK_MANUAL.notify();
    }
  }
}
