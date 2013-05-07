package com.pelleplutt.cnc.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.pelleplutt.cnc.Controller;
import com.pelleplutt.cnc.ctrl.CNCCommand;
import com.pelleplutt.cnc.ctrl.CNCCommand.Coolant;
import com.pelleplutt.cnc.ctrl.CNCCommand.EndOfProgram;
import com.pelleplutt.cnc.ctrl.CNCCommand.Move;
import com.pelleplutt.cnc.ctrl.GCommand.Dwell;
import com.pelleplutt.cnc.ctrl.GCommand.Spindle;
import com.pelleplutt.cnc.ctrl.GCommand.Stop;
import com.pelleplutt.cnc.ctrl.GCommand.Tool;
import com.pelleplutt.cnc.ctrl.GVirtualCNC;
import com.pelleplutt.cnc.io.CommMux.Transport;
import com.pelleplutt.cnc.types.Point;
import com.pelleplutt.cnc.types.StopType;
import com.pelleplutt.cnc.types.UnitType;
import com.pelleplutt.comm.Comm;
import com.pelleplutt.comm.CommArgument;
import com.pelleplutt.util.AppSystem;
import com.pelleplutt.util.AppSystem.Disposable;
import com.pelleplutt.util.HexUtil;
import com.pelleplutt.util.Log;

/**
 * The software representation of the physical CNC communicating over the
 * CNCProtocol. Translates CNCCommands to the CNCProtocol. Handles callbacks
 * from CNCProtocol. Keeps a queue of CNCCommands to be invoked on actual CNC
 * and handles the transport of those commands over the CNCProtocol depending on
 * CNC machine state.
 * 
 * @author petera
 * 
 */
public class CNCBridge implements Transport, CNCProtocol, Disposable {
  // indicates if the bridge should shut down
  volatile boolean disposed = false;
  // Communication stack
  Comm comm;
  // Indicates if communication is up or not
  boolean connected = false;
  // Mutex for sending commands
  final Object LOCK_TX = new Object();
  // Lock used when awaiting ack for txed packet
  final Object LOCK_TX_ACK = new Object();

  // Current cnc status
  int srCur;
  // Previous cnc status, used to keep track of flanks
  int srPrev;

  // First motion id identifying first latch q command
  public static final int MOTION_ID_START = 1000;

  // indicates if latch queue is running or not
  volatile boolean latchQRunning = false;
  // Lock for latchQueue, notified by CNC status changes
  final Object LOCK_SR = new Object();
  // Commands to be sent
  List<CNCCommand> latchQ = new ArrayList<CNCCommand>();
  // Current index of command to send in queued commands
  int latchQIx = 0;
  // Current communication time
  volatile long commTime = 0;
  // Current id of last sent command
  int latchId = 0;
  // Map with commands that have been sent to CNC but not yet executed
  Map<Integer, CNCCommand> motionPipe = new HashMap<Integer, CNCCommand>();

  double feedMul = 1;

  // if stop command should stop latch insertions
  volatile boolean stopAtOptional = true;

  enum BridgeState {
    // nothing to do
    IDLE,
    // cue for execute program
    STARTING,
    // sending and executing program
    SENDING,
    // sent all program, awaiting cnc still
    AWAIT_EOP,
    // program pause request
    PAUSE,
    // got cnc still after sending all program
    EOP_REPORT,
    // executing cleanup after program
    CODA_EXE,
    // awaiting cnc still after executed cleanup
    AWAIT_STILL,
    // got cnc still after cleanup execute
    CNC_STILL_REPORT,
  }

  final Object LOCK_STATE = new Object();
  BridgeState state = BridgeState.IDLE;

  List<CNCListener> cncListeners = new ArrayList<CNCListener>();
  List<CNCMacroListener> macroListeners = new ArrayList<CNCMacroListener>();

  GVirtualCNC machine;

  static final int ACK_MATCH = -1;

  public void setMachine(GVirtualCNC m) {
    machine = m;
  }

  public void addCNCListener(CNCListener l) {
    cncListeners.add(l);
  }

  public void addCNCMacroListener(CNCMacroListener l) {
    macroListeners.add(l);
  }

  public void initLatchQueue() {
    Thread latch = new Thread(new Runnable() {
      public void run() {
        statusLatchLoop();
      }
    }, "statusmonitor");
    latch.setPriority(Thread.MAX_PRIORITY);
    latch.start();
    new Thread(new Runnable() {
      public void run() {
        stateCheckLoop();
      }
    }, "bridgestatecheck").start();
    AppSystem.addDisposable(this);

    Log.println("started");
  }

  public synchronized void cncInit() {
    cncSetSrMask(CNC_STATUS_CONTROL_ENABLED | CNC_STATUS_LATCH_FULL
        | CNC_STATUS_MOVEMENT_STILL | CNC_STATUS_PIPE_FULL);
    cncSetTimerSR(50);
    cncSetTimerPos(50);
    cncConfigure(machine);
  }
  
  public void setConnected(boolean c) {
    connected = c;
  }

  public void reset(boolean connected) {
    Log.println("reset connected:" + connected);

    motionPipe.clear();
    latchQueueStop();

    if (connected) {
      try {
        cncReset();
        cncConfigure(machine);
        // define motion sequence id starting number
        cncSetMotionIdSequence(MOTION_ID_START);
      } catch (Throwable t) {
        Log.printStackTrace(t);
      }

    }
    synchronized (LOCK_SR) {
      latchId = MOTION_ID_START;
      latchQIx = 0;
      Log.println("latchQ cleared");
      latchQ.clear();
      LOCK_SR.notifyAll();
    }
    synchronized (LOCK_STATE) {
      setState(BridgeState.IDLE);
      LOCK_STATE.notify();
    }
    for (CNCMacroListener l : macroListeners) {
      l.stopped(isProgramExecuted(), null, null);
    }
    Log.println("reset done");
  }

  public synchronized void enterProgramMode() {
    motionPipe.clear();
    Log.println("program mode enter");
    cncEnable(false);
    // reset movement registers = stop
    cncSetXYZ(0, 0, 0, 0, 0, 0);
    // setup sr
    cncInit();
    // stop and flush pipeline
    cncPipeEnable(false);
    cncPipeFlush();
    // define motion sequence id starting number
    cncSetMotionIdSequence(MOTION_ID_START);
    latchId = MOTION_ID_START;
    // reset positioning
    Point p = cncGetPos();
    Point o = cncGetOffsPos();
    cncSetOffs((int) -(p.x - o.x), (int) -(p.y - o.y), (int) -(p.z - o.z));
    // start pipeline
    cncPipeEnable(true);
    // enable cnc
    cncEnable(true);
    Log.println("program mode entered");
  }

  public synchronized void pauseProgramMode() {
    Log.println("program pause request");
    synchronized (LOCK_STATE) {
      if (!(state == BridgeState.SENDING || state == BridgeState.AWAIT_EOP)) {
        Log.println("program pause request DENIED, state " + state);
        return;
      }
    }

    latchQueueStop();
    // stop CNC pipeline
    cncPipeEnable(false);
    synchronized (LOCK_STATE) {
      setState(BridgeState.PAUSE);
      LOCK_STATE.notify();
    }

    Log.println("program pause requested");
  }
  
  public synchronized void goSomewhere(Point p, Point feed, boolean zFirst) {
    cncPipeEnable(false);
    cncPipeFlush();
    Point curP = cncGetPos();
    
    feed = Controller.feedUPMtoSPS(feed);

    if (zFirst && p.z < 0) {
      if (curP.z < machine.stepsPerUnit.z) {
        int lift = (int)(machine.stepsPerUnit.z-curP.z);
        cncLatchXYZ(0,0,0,0, lift, feed.z, false);
        p.z -= lift;
      }
      cncLatchXYZ((int)p.x, feed.x, (int)p.y, feed.y, 0,0, false);
      cncLatchXYZ(0,0,0,0, (int)p.z, feed.z, false);
    } else if (zFirst && p.z >= 0) {
      if (curP.z < machine.stepsPerUnit.z) {
        int lift = (int)(machine.stepsPerUnit.z-curP.z);
        cncLatchXYZ(0,0,0,0, lift, feed.z, false);
        p.z -= lift;
      }
      cncLatchXYZ((int)p.x, feed.x, (int)p.y, feed.y, 0,0, false);
      cncLatchXYZ(0,0,0,0, (int)p.z, feed.z, false);
    } else {
      cncLatchXYZ((int)p.x, feed.x, (int)p.y, feed.y, (int)p.z, feed.z, false);
    }
    cncPipeEnable(true);
    synchronized (LOCK_STATE) {
      setState(BridgeState.PAUSE);
      LOCK_STATE.notify();
    }

  }

  public synchronized void resumeProgramMode() {
    Log.println("program resume request");
    synchronized (LOCK_STATE) {
      if (!(state == BridgeState.IDLE)) {
        Log.println("program resume request DENIED, state " + state);
        return;
      }
    }
    latchQueueStart();
    // start CNC pipeline
    cncPipeEnable(true);
    Log.println("program resume requested");
  }

  public synchronized void abortProgramMode() {
    Log.println("program mode abort");
    motionPipe.clear();
    latchQueueStop();

    cncEnable(false);
    cncPipeFlush();
    cncEnable(true);
    synchronized (LOCK_SR) {
      latchQIx = 0;
      Log.println("latchQ cleared");
      latchQ.clear();
      LOCK_SR.notifyAll();
    }
    synchronized (LOCK_STATE) {
      setState(BridgeState.AWAIT_EOP);
      LOCK_STATE.notifyAll();
    }

    Log.println("program mode aborted");
  }

  private synchronized void exitProgramMode() {
    motionPipe.clear();

    Log.println("exit program mode");
    cncPipeEnable(false);
    cncPipeFlush();
    
    synchronized (LOCK_SR) {
      latchQIx = 0;
      Log.println("latchQ cleared");
      latchQ.clear();
      LOCK_SR.notifyAll();
    }

    Point p = cncGetPos();
    // lift tool up from last position plus one unit (inch or mm)
    cncLatchXYZ(0, 0, 0, 0, (int) (-p.z + machine.stepsPerUnit.z),
        machine.maxFeedsSPS.z, false);
    // goto home
    cncLatchXYZ((int) -p.x, machine.maxFeedsSPS.x, (int) -p.y, machine.maxFeedsSPS.y,
        0, 0, true);
    cncSetTimerSR(100);
    cncSetTimerPos(100);

    cncPipeEnable(true);
    Log.println("exited program done");
    for (CNCListener l : cncListeners) {
      l.curCommand(-1, 0, 0, null);
    }
  }

  public synchronized void enterManualMode() {
    Log.println("manual mode enter");
    cncEnable(false);
    cncPipeEnable(false);
    cncPipeFlush();
    cncEnable(true);
    Log.println("manual mode entered");
  }

  public synchronized void exitManualMode() {
    Log.println("manual mode exit");
  }

  public void doManual(int x, double fx, int y, double fy, int z, double fz) {
    cncSetXYZ(x, fx, y, fy, z, fz);
  }

  public void dispose() {
    disposed = true;
    synchronized (LOCK_SR) {
      LOCK_SR.notifyAll();
    }
    synchronized (LOCK_STATE) {
      LOCK_STATE.notifyAll();
    }
  }

  // CNC Protocol commands

  // latch queue

  public void addQueuedCommand(CNCCommand c) {
    synchronized (LOCK_SR) {
      Log.println("latchQ add " + c);
      latchQ.add(c);
      LOCK_SR.notifyAll();
    }
  }

  public void addQueuedCommands(List<CNCCommand> c) {
    synchronized (LOCK_SR) {
      Log.println("latchQ add " + c.size() + " commands");
      latchQ.addAll(c);
      LOCK_SR.notifyAll();
    }
  }

  public void latchQueueStart() {
    synchronized (LOCK_SR) {
      Log.println("latchQ start");
      latchQRunning = true;
      LOCK_SR.notifyAll();
    }
  }

  public void latchQueueStop() {
    synchronized (LOCK_SR) {
      Log.println("latchQ stop");
      latchQRunning = false;
      LOCK_SR.notifyAll();
    }
  }

  public boolean isProgramSent() {
    synchronized (LOCK_SR) {
      return latchQIx >= latchQ.size();
    }
  }

  public boolean isProgramExecuted() {
    synchronized (LOCK_SR) {
      return (latchId - MOTION_ID_START) >= latchQ.size();
    }
  }

  //
  // CNC Status monitoring, command dispatcher
  //

  public boolean exe(CNCCommand c) {
    boolean res = true;
    // cncLatchXYZ and cncPause are put into pipe via latch
    // successful invokation means latch id is autoincremented
    // so we keep track of it here
    if (c instanceof Move) {
      res = cncLatchXYZ((int) ((Move) c).stepsTranslation.x,
          ((Move) c).feedStepsPerSec.x, (int) ((Move) c).stepsTranslation.y,
          ((Move) c).feedStepsPerSec.y, (int) ((Move) c).stepsTranslation.z,
          ((Move) c).feedStepsPerSec.z, ((Move) c).rapid);
    } else if (c instanceof Dwell) {
      res = cncLatchPause((int) ((Dwell) c).milliseconds);
    } else if (c instanceof Spindle) {
      // TODO
    } else if (c instanceof Coolant) {
      // TODO
    } else if (c instanceof Stop) {
      if (((Stop) c).type == StopType.OPTIONAL) {
        if (stopAtOptional) {
          latchQueueStop();
        }
      } else {
        latchQueueStop();
      }
    } else if (c instanceof Tool) {
      // TODO
    } else if (c instanceof EndOfProgram) {
      synchronized (LOCK_STATE) {
        setState(BridgeState.AWAIT_EOP);
        LOCK_STATE.notifyAll();
      }
      res = true;
    }
    if (res) {
      registerLatchedCommand(c);
    }
    return res;
  }

  void registerLatchedCommand(CNCCommand c) {
    boolean motion = c instanceof Move || c instanceof Dwell;
    if (motion) {
      motionPipe.put(latchId, c);
      synchronized (LOCK_SR) {
        latchId++;
      }
    }
    synchronized (LOCK_STATE) {
      if (state == BridgeState.IDLE) {
        setState(BridgeState.STARTING);
        LOCK_STATE.notifyAll();
      }
    }
  }

  /**
   * Status monitor loop, used to monitor latch status and insert commands into
   * latch when free
   */
  void statusLatchLoop() {
    Log.println("statusLatchLoop started");
    while (!disposed) {
      CNCCommand command;

      synchronized (LOCK_SR) {
        command = null;

        // await latchQRunning
        while (!disposed && !latchQRunning) {
          AppSystem.waitSilently(LOCK_SR);
        }

        // await free latch or command to insert
        while (!disposed && latchQRunning
            && ((srCur & CNCProtocol.CNC_STATUS_LATCH_FULL) != 0) || !disposed
            && isProgramSent()) {
          AppSystem.waitSilently(LOCK_SR);
        }

        if (!disposed && latchQRunning
            && (srCur & CNCProtocol.CNC_STATUS_LATCH_FULL) == 0 && connected
            && !isProgramSent()) {
          // all green, enabled, free, connected and ready => get a command to
          // send
          command = latchQ.get(latchQIx++);
          if (isProgramSent()) {
            Log.println("EOP await by end of latchQ");
            synchronized (LOCK_STATE) {
              setState(BridgeState.AWAIT_EOP);
              LOCK_STATE.notifyAll();
            }
          }
        }
      } // synch

      // execute command
      try {
        if (!disposed && command != null) {
          boolean res = exe(command);
          if (!res) {
            // failed for some reason, force mark sr latch as full and retry same
            // command next time
            Log.println("!!!!!!! reinserting command " + latchQIx + " " + command);
            synchronized (LOCK_SR) {
              latchQIx--;
              srCur |= CNCProtocol.CNC_STATUS_LATCH_FULL;
              srPrev |= CNCProtocol.CNC_STATUS_LATCH_FULL;
            }
          }
        }
      } catch (CNCBridge.CNCBridgeError cncErr) {
        Log.println("resetting bridge due to error: " + cncErr.getMessage());
        reset(false);
      } catch (Throwable t) {
        Log.printStackTrace(t);
      }
    } // while !disposed
    Log.println("statusLatchLoop dead");
  }

  /*
   * IDLE -------->--------
   *  |                    |
   *  |             -- STARTING <======[cnc command sent | cnc command executed]
   *  |            |       |
   *  |            |    STARTED
   *  |            |       |   fireStarted
   *  |  [all cmds |       |--------------------------->------------------------------
   *  |    sent]   |       |                                                          |
   *  |             -> AWAIT_EOP <=====[all commands sent | user abort]               |
   *  |                    |                                                          |
   *  |                    |--------------------------->------------------------------|
   *  |                    |                                                          |
   *  |                EOP_REPORT <====[cnc latch empty & cnc pipe empty & cnc still] |
   *  |                    |                                                        PAUSE <== [user pause]
   *  |                 CODA_EXE                                                      |  firePausing
   *  |                    |   exitProgramMode                                        |
   *  |                    |   fireEnding                                             |
   *  |                    |                                                          |
   *  |               AWAIT_STILL ---------------------<------------------------------
   *  |                    |
   *  |             CNC_STILL_REPORT <=[cnc still]
   *  |                    |   fireStopped
   *  |                    |
   *   ------<-------------                    
   */
  void stateCheckLoop() {
    BridgeState prevState = state;
    while (!disposed) {
      boolean fireStarted = false, firePausing = false, fireEnding = false, fireStopped = false;
      synchronized (LOCK_STATE) {
        while (!disposed && state == prevState) {
          AppSystem.waitSilently(LOCK_STATE);
        }
      }
      boolean programSent = isProgramSent();
      synchronized (LOCK_STATE) {
        Log.println("bridge state notify: " + prevState + " => " + state);
        prevState = state;
        switch (state) {
        case STARTING:
          fireStarted = true;          
          setState(programSent ? BridgeState.AWAIT_EOP : BridgeState.SENDING);
          break;
        case EOP_REPORT:
          fireEnding = true;
          setState(BridgeState.CODA_EXE); // intermediate state, changed below in fires
          break;
        case PAUSE:
          firePausing = true;
          setState(BridgeState.AWAIT_STILL);
          break;
        case CNC_STILL_REPORT:
          fireStopped = true;
          setState(BridgeState.IDLE);
          break;
        case IDLE:
        case SENDING:
        case AWAIT_EOP:
        case AWAIT_STILL:
          break;
        default:
          break;
        }
      } // synch
      if (!disposed) {
        if (fireStarted) {
          for (CNCMacroListener l : macroListeners) {
            l.started();
          }
        }
        if (firePausing || fireEnding) {
          Point p = cncGetPos();
          Point op = cncGetOffsPos();
          if (fireEnding) {
            // keep CODA_EXE while exiting program mode to avoid premature CNC_STILL
            exitProgramMode();
            setState(BridgeState.AWAIT_STILL); // from CODA_EXE if fireEnding
          }
          for (CNCMacroListener l : macroListeners) {
            l.ending(p, op);
          }
        }
        if (fireStopped) {
          Point p = cncGetPos();
          Point op = cncGetOffsPos();
          for (CNCMacroListener l : macroListeners) {
            l.stopped(isProgramExecuted(), p, op);
          }
        }
      }
    }
  }

  /**
   * Change of CNC status register, monitor latch Notify statusLatchLoop that a
   * change of interest have happened so we may try putting a new command in
   * latch
   * 
   * @param sr
   */
  void cncSrChange(int sr) {
    // Log.println("SR_EVENT:" + HexUtil.toHex(sr));
    boolean endOfCnc = false;
    synchronized (LOCK_SR) {
      srPrev = srCur;
      srCur = sr;
      // CNC_STATUS_LATCH_FULL: low flank, the latch reg just got free
      boolean latchFree = (srPrev & CNCProtocol.CNC_STATUS_LATCH_FULL) != 0
          && (srCur & CNCProtocol.CNC_STATUS_LATCH_FULL) == 0;

      // CNC_STATUS_MOVEMENT_STILL: high flank, motion just became still and not
      // paused
      boolean movementStill = (srPrev & CNCProtocol.CNC_STATUS_MOVEMENT_STILL) == 0
          && (srCur & CNCProtocol.CNC_STATUS_MOVEMENT_STILL) != 0
          && (srCur & CNCProtocol.CNC_STATUS_MOVEMENT_PAUSE) == 0;

      // CNC_STATUS_PIPE_EMPTY: high flank, pipe was just emptied,
      boolean pipeEmpty = (srPrev & CNCProtocol.CNC_STATUS_PIPE_EMPTY) == 0
          && (srCur & CNCProtocol.CNC_STATUS_PIPE_EMPTY) != 0;

      synchronized (LOCK_STATE) {
        endOfCnc = (state == BridgeState.AWAIT_EOP
            && (srCur & CNCProtocol.CNC_STATUS_LATCH_FULL) == 0
            && (srCur & CNCProtocol.CNC_STATUS_MOVEMENT_STILL) != 0
            && (srCur & CNCProtocol.CNC_STATUS_MOVEMENT_PAUSE) == 0 && (srCur & CNCProtocol.CNC_STATUS_PIPE_EMPTY) != 0)
            || (state == BridgeState.AWAIT_STILL
                && (srCur & CNCProtocol.CNC_STATUS_MOVEMENT_STILL) != 0 && (srCur & CNCProtocol.CNC_STATUS_MOVEMENT_PAUSE) == 0);
      }

      // endOfCnc &= motionPipe.isEmpty();
      if (latchFree || movementStill || pipeEmpty) {
        // do something, send a command or something
        LOCK_SR.notifyAll();
      }
    } // synch LOCK_SR
    if (endOfCnc) {
      synchronized (LOCK_STATE) {
        if (state == BridgeState.AWAIT_EOP) {
          setState(BridgeState.EOP_REPORT);
          LOCK_STATE.notifyAll();
        } else if (state == BridgeState.AWAIT_STILL) {
          setState(BridgeState.CNC_STILL_REPORT);
          LOCK_STATE.notifyAll();
        }
      } // synch LOCK_STATE
    }
  }

  //
  // CNC Protocol API
  //

  public int cncProtocolInfo() {
    Log.println("");
    byte[] res = sendCommand(COMM_PROTOCOL_INFO);
    int r = Controller.arrtoi(res, 0);
    Log.println("res:" + HexUtil.toHex(r));
    return r;
  }

  public void cncEnable(boolean on) {
    sendCommand(COMM_PROTOCOL_CNC_ENABLE, on ? 1 : 0);
    Log.println("" + on);
  }

  public int cncGetStatus() {
    byte[] res = sendCommand(COMM_PROTOCOL_GET_STATUS);
    int r = Controller.arrtoi(res, 0);
    Log.println("res:" + HexUtil.toHex(r));
    return r;
  }

  public void cncSetSrMask(int srmask) {
    sendCommand(COMM_PROTOCOL_SET_SR_MASK, srmask/* | (0xff00)*/); // TODO error in firmware
    Log.println("" + srmask);
  }

  public boolean cncIsLatchFree() {
    byte[] res = sendCommand(COMM_PROTOCOL_IS_LATCH_FREE);
    int r = Controller.arrtoi(res, 0);
    Log.println("res:" + HexUtil.toHex(r));
    return r != 0;
  }

  public int cncCurrentMotionId() {
    byte[] res = sendCommand(COMM_PROTOCOL_CUR_MOTION_ID);
    int r = Controller.arrtoi(res, 0);
    Log.println("res:" + HexUtil.toHex(r));
    return r;
  }

  public void cncSetMotionIdSequence(int id) {
    sendCommand(COMM_PROTOCOL_SET_LATCH_ID, id);
    Log.println("" + id);
  }

  public void cncPipeEnable(boolean on) {
    sendCommand(COMM_PROTOCOL_PIPE_ENABLE, on ? 1 : 0);
    Log.println("" + on);
  }

  public void cncPipeFlush() {
    sendCommand(COMM_PROTOCOL_PIPE_FLUSH);
    Log.println("");
  }

  public boolean cncLatchXYZ(int x, double fx, int y, double fy, int z,
      double fz, boolean rapid) {
    if (machine.axisInversion.x < 0) {
      x = -x;
    }
    if (machine.axisInversion.y < 0) {
      y = -y;
    }
    if (machine.axisInversion.z < 0) {
      z = -z;
    }
    Log.println("XYZ: " + x + "/" + fx + " " + y + "/" + fy + " " + z + "/"
        + fz + " " + rapid);
    byte[] res = sendCommand(COMM_PROTOCOL_LATCH_XYZ, x,
        (int) Math.round(fx * feedMul * (1 << CNC_FP_DECIMALS)), y,
        (int) Math.round(fy * feedMul * (1 << CNC_FP_DECIMALS)), z,
        (int) Math.round(fz * feedMul * (1 << CNC_FP_DECIMALS)), rapid ? 1 : 0);
    int r = Controller.arrtoi(res, 0);
    // Log.println("res:" + HexUtil.toHex(r));
    return r != 0;
  }

  public boolean cncLatchPause(int ms) {
    byte[] res = sendCommand(COMM_PROTOCOL_LATCH_PAUSE, ms);
    int r = Controller.arrtoi(res, 0);
    // Log.println("res:" + HexUtil.toHex(r));
    return r != 0;
  }

  public void cncSetPos(int x, int y, int z) {
    if (machine.axisInversion.x < 0) {
      x = -x;
    }
    if (machine.axisInversion.y < 0) {
      y = -y;
    }
    if (machine.axisInversion.z < 0) {
      z = -z;
    }
    sendCommand(COMM_PROTOCOL_SET_POS, x, y, z);
    Log.println("");
  }

  public void cncSetXYZ(int x, double fx, int y, double fy, int z, double fz) {
    if (machine.axisInversion.x < 0) {
      x = -x;
    }
    if (machine.axisInversion.y < 0) {
      y = -y;
    }
    if (machine.axisInversion.z < 0) {
      z = -z;
    }
    sendCommand(COMM_PROTOCOL_SET_IMM_XYZ, x,
        (int) Math.round(fx * (1 << CNC_FP_DECIMALS)), y,
        (int) Math.round(fy * (1 << CNC_FP_DECIMALS)), z,
        (int) Math.round(fz * (1 << CNC_FP_DECIMALS)));
    Log.println("");
  }

  public void cncSetTimerSR(int ms) {
    sendCommand(COMM_PROTOCOL_SR_TIMER_DELTA, ms);
    Log.println("" + ms);
  }

  public void cncSetTimerPos(int ms) {
    sendCommand(COMM_PROTOCOL_POS_TIMER_DELTA, ms);
    Log.println("" + ms);
  }

  public Point cncGetPos() {
    byte[] res = sendCommand(COMM_PROTOCOL_GET_POS);
    int x = Controller.arrtoi(res, 0);
    int y = Controller.arrtoi(res, 4);
    int z = Controller.arrtoi(res, 8);
    if (machine.axisInversion.x < 0) {
      x = -x;
    }
    if (machine.axisInversion.y < 0) {
      y = -y;
    }
    if (machine.axisInversion.z < 0) {
      z = -z;
    }
    return new Point(UnitType.STEPS, x, y, z);
  }

  public void cncSetOffs(int x, int y, int z) {
    if (machine.axisInversion.x < 0) {
      x = -x;
    }
    if (machine.axisInversion.y < 0) {
      y = -y;
    }
    if (machine.axisInversion.z < 0) {
      z = -z;
    }
    sendCommand(COMM_PROTOCOL_SET_OFFS_POS, x, y, z);
    Log.println("");
  }

  public Point cncGetOffsPos() {
    byte[] res = sendCommand(COMM_PROTOCOL_GET_OFFS_POS);
    int x = Controller.arrtoi(res, 0);
    int y = Controller.arrtoi(res, 4);
    int z = Controller.arrtoi(res, 8);
    if (machine.axisInversion.x < 0) {
      x = -x;
    }
    if (machine.axisInversion.y < 0) {
      y = -y;
    }
    if (machine.axisInversion.z < 0) {
      z = -z;
    }
    return new Point(UnitType.STEPS, x, y, z);
  }
  
  public void cncReset() {
    sendCommand(COMM_PROTOCOL_RESET);
  }
  
  public boolean cncConfigure(GVirtualCNC machine) {
    byte[] res = sendCommand(COMM_PROTOCOL_CONFIG, 
        (byte)COMM_PROTOCOL_CONFIG_MAX_X_FREQ, (int)machine.absoluteMaxFrequency.x,
        (byte)COMM_PROTOCOL_CONFIG_MAX_Y_FREQ, (int)machine.absoluteMaxFrequency.y,
        (byte)COMM_PROTOCOL_CONFIG_MAX_Z_FREQ, (int)machine.absoluteMaxFrequency.z,
        (byte)COMM_PROTOCOL_CONFIG_RAPID_X_D, (int)machine.rapidDelta.x,
        (byte)COMM_PROTOCOL_CONFIG_RAPID_Y_D, (int)machine.rapidDelta.y,
        (byte)COMM_PROTOCOL_CONFIG_RAPID_Z_D, (int)machine.rapidDelta.z);
    int r = Controller.arrtoi(res, 0);
    // Log.println("res:" + HexUtil.toHex(r));
    return r != 0;
  }

  //
  // Communication low-level
  //
  
  /**
   * Sends a raw command over CNCProtocol, awaits ack and returns data
   * 
   * @param command
   * @param params
   * @return data piggybacked on ack
   */
  byte[] sendCommand(int command, Object... params) {
    byte[] raw = Controller.constructPacket(command, params);
    // Log.println("TX:" + HexUtil.formatData(raw));

    byte[] ret = new byte[0];
    synchronized (LOCK_TX) {
      synchronized (LOCK_TX_ACK) {
        int res = Controller.tx(this, raw, true);
        if (res < 0) {
          latchQueueStop();
          throw new CNCBridgeError("Error sending command " + res);
        }
        ackSeqNo = res;
        AppSystem.waitSilently(LOCK_TX_ACK, 
            ((2 + Comm.COMM_MAX_RESENDS) * Comm.COMM_RESEND_TICK) * CNCCommunication.COMM_TICK_TIME);
        if (ackSeqNo != ACK_MATCH) {
          // noone acked (indicated we got acked) so disconnect, bail out
          throw new CNCBridgeError("Error sending command, timeout");
        }
        ret = new byte[ackLen];
        System.arraycopy(ackData, 0, ret, 0, ackLen);
      }
    }
    return ret;
  }

  int ackSeqNo;
  byte[] ackData = new byte[Comm.COMM_LNK_MAX_DATA];
  int ackLen;

  /**
   * Packet reception, command muxxing
   * 
   * @param data
   */
  void handleRx(byte[] data, int offset) {
    int comm = (int) (data[offset] & 0xff);
    switch (comm) {
    case COMM_PROTOCOL_EVENT_SR:
    case COMM_PROTOCOL_EVENT_SR_TIMER: {
      int sr = Controller.arrtoi(data, offset+1);
      cncSrChange(sr);
      for (CNCListener l : cncListeners) {
        l.sr(sr);
      }
      break;
    }
    case COMM_PROTOCOL_EVENT_POS_TIMER: {
      int x = Controller.arrtoi(data, offset+1);
      int y = Controller.arrtoi(data, offset+5);
      int z = Controller.arrtoi(data, offset+9);
      if (machine.axisInversion.x < 0) {
        x = -x;
      }
      if (machine.axisInversion.y < 0) {
        y = -y;
      }
      if (machine.axisInversion.z < 0) {
        z = -z;
      }
      Point stepsPerMm = machine.stepsPerUnit.toUnit(UnitType.MILLIMETERS);
      double xx = x / stepsPerMm.x;
      double yy = y / stepsPerMm.y;
      double zz = z / stepsPerMm.z;
      for (CNCListener l : cncListeners) {
        l.pos(xx, yy, zz);
      }
      break;
    }
    case COMM_PROTOCOL_EVENT_SR_POS_TIMER: {
      int sr = Controller.arrtoi(data, offset+1);
      int x = Controller.arrtoi(data, offset+5);
      int y = Controller.arrtoi(data, offset+9);
      int z = Controller.arrtoi(data, offset+13);
      if (machine.axisInversion.x < 0) {
        x = -x;
      }
      if (machine.axisInversion.y < 0) {
        y = -y;
      }
      if (machine.axisInversion.z < 0) {
        z = -z;
      }
      Point stepsPerMm = machine.stepsPerUnit.toUnit(UnitType.MILLIMETERS);
      double xx = x / stepsPerMm.x;
      double yy = y / stepsPerMm.y;
      double zz = z / stepsPerMm.z;
      cncSrChange(sr);
      for (CNCListener l : cncListeners) {
        l.sr(sr);
        l.pos(xx, yy, zz);
      }
      break;
    }
    case COMM_PROTOCOL_EVENT_ID: {
      int curMotionId = Controller.arrtoi(data, offset+1);
      CNCCommand c = motionPipe.remove(curMotionId);
      Log.println("executing " + curMotionId + ", sent " + latchId + ", "
          + motionPipe.size() + " in pipe");
      synchronized (LOCK_STATE) {
        if (state == BridgeState.IDLE) {
          setState(BridgeState.STARTING);
          LOCK_STATE.notifyAll();
        }
      }
      if (c != null) {
        double exe = (double) (curMotionId-MOTION_ID_START) / (double) latchQ.size();
        double sent = (double) (latchQIx) / (double) latchQ.size();
        for (CNCListener l : cncListeners) {
          l.curCommand(curMotionId, exe, sent, c);
        }
      }
      break;
    }
    case COMM_PROTOCOL_ALIVE: {
      // TODO
      break;
    }
    default:
      Log.println("UNKNOWN RX:" + HexUtil.formatDataSimple(data));
    }
  }
  
  void setState(BridgeState s) {
    Log.println("bridge state change: " + state + " => " + s);
    state = s;
  }

  //
  // Communication Transport stack callbacks
  //

  @Override
  public int ack(CommArgument rx, byte[] data) {
    synchronized (LOCK_TX_ACK) {
      if (rx.seqno == ackSeqNo) {
        System.arraycopy(data, 0, ackData, 0, rx.len);
        ackLen = rx.len;
        ackSeqNo = ACK_MATCH; // indicate we got acked
        LOCK_TX_ACK.notifyAll();
      }
    }
    return Comm.R_COMM_OK;
  }

  @Override
  public int rx(CommArgument rx, byte[] data, int offset) {
    handleRx(data, offset);
    return Comm.R_COMM_OK;
  }
  
  @Override
  public boolean myRx(byte[] data, int offset) {
    int proto = data[offset] & 0xff;
    return proto == CommMux.PROTOCOL_CNC;
  }
  
  @Override
  public void error(CommArgument a, boolean txOtherwiseRx, int error) {
    Log.println("CNCBridge: comm error seq:" + a.seqno + " err:" + error);
  }
  
  @Override
  public int getProtocolId() {
    return CommMux.PROTOCOL_CNC;
  }

  // thisnthat

  public interface CNCListener {
    public void sr(int sr);

    public void pos(double x, double y, double z);

    public void curCommand(int id, double exePerc, double sentPerc, CNCCommand c);
  }

  public interface CNCMacroListener {
    public void started();

    public void ending(Point where, Point offset);

    public void stopped(boolean programEnd, Point where, Point offset);
  }

  public static class CNCBridgeError extends Error {
    private static final long serialVersionUID = -4278850992694071369L;

    public CNCBridgeError(String s) {
      super(s);
    }
  }

}
