package com.pelleplutt.cnc;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.swing.JButton;

import com.pelleplutt.cnc.ctrl.CNCCommand;
import com.pelleplutt.cnc.ctrl.GVirtualCNC;
import com.pelleplutt.cnc.io.CNCBridge;
import com.pelleplutt.cnc.io.CNCBridge.CNCListener;
import com.pelleplutt.cnc.io.CNCBridge.CNCMacroListener;
import com.pelleplutt.cnc.io.CNCCommunication;
import com.pelleplutt.cnc.io.CNCCommunicationUART;
import com.pelleplutt.cnc.io.CNCFile;
import com.pelleplutt.cnc.io.CNCProtocol;
import com.pelleplutt.cnc.io.CommMux;
import com.pelleplutt.cnc.types.Point;
import com.pelleplutt.cnc.types.UnitType;
import com.pelleplutt.cnc.ui.CNCUI;
import com.pelleplutt.util.Log;
import com.pelleplutt.util.UIUtil;

public class Controller {
  static CNCUI ui;
  static CNCBridge bridge;
  static CNCFile fileTx;
  static CNCCommunication cncComm;
  static CommMux commMux;
  static GVirtualCNC cncVirtual;
  static ManualController manualControl;

  static StateConnection connectionState;
  static StateProgram programState;

  static Program currentProgram = null;

  public static final String ACTION_CONNECT_DISCONNECT = "conndis";
  public static final String ACTION_SWITCH_MODE = "mode";
  public static final String ACTION_ON_OFF = "onoff";
  public static final String ACTION_PROGRAM_LOAD = "programload";
  public static final String ACTION_PROGRAM = "program";
  public static final String ACTION_PROGRAM_ABORT = "programabort";
  public static final String ACTION_RESET = "reset";
  public static final String ACTION_FIRMWARE = "fw";
  public static final String ACTION_SET_REF_XY = "refxy";
  public static final String ACTION_SET_REF_Z = "refz";
  public static final String ACTION_GO_USER = "gouser";
  public static final String ACTION_GO_HOME = "gohome";
  public static final String ACTION_GO_ORIGO = "goorigo";
  public static final String ACTION_CONFIG_APPLY = "confapply";

  static int curSr = -1;

  public static void construct() {
    cncVirtual = new GVirtualCNC();
    cncVirtual.reset();
    cncVirtual.setHardcodedConfiguration();

    commMux = new CommMux();
    
    bridge = new CNCBridge();
    bridge.setMachine(cncVirtual);
    fileTx = new CNCFile();
    
    commMux.addTransport(bridge);
    commMux.addTransport(fileTx);
    
    ui = new CNCUI();

    bridge.addCNCListener(ui.getBluePrintPanel());
    bridge.addCNCListener(cncControllerListener);
    bridge.addCNCMacroListener(cncProgramControllerListener);

    bridge.initLatchQueue();

    connectionState = StateConnection.DISCONNECTED;
    bridge.setConnected(false);
    
    manualControl = new ManualController(bridge);
    manualControl.start();
    
    KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    manager.addKeyEventDispatcher(manualControl.keyDispatcher);

    reset();
  }
  
  public static int tx(CommMux.Transport t, byte[] data, boolean ack) {
    return commMux.tx(t, data, ack);
  }

  public static byte[] constructPacket(int command, Object...params) {
    ByteArrayOutputStream packet = new ByteArrayOutputStream();
    packet.write(0xff); // protocol id, to be defined in CommMux
    packet.write(command);
    for (int i = 0; i < params.length; i++) {
      if (params[i] instanceof Integer) {
        itoarr32((Integer)params[i], packet);
      } else if (params[i] instanceof Short) {
        itoarr16((Short)params[i], packet);
      } else if (params[i] instanceof Character) {
        itoarr16((short)((Character)params[i]).charValue(), packet);
      } else if (params[i] instanceof Byte) {
        packet.write((byte)((Byte)params[i]).byteValue());
      } else if (params[i] instanceof ByteBuffer) {
        try {
          packet.write(((ByteBuffer)params[i]).array());
        } catch (IOException e) {
          Log.printStackTrace(e);
        }
      } else if (params[i] instanceof String) {
        String s = (String)params[i];
        byte[] sb = s.getBytes();
        for (int t = 0; t < sb.length; t++) {
          packet.write((byte)(sb[t]));
        }
        packet.write((byte)0);
      } else {
        throw new RuntimeException("Unknown packet type " + params[i].getClass().getName());
      }
    }
    
    byte[] raw = packet.toByteArray();
    return raw;
  }
  

  public static int arrtoi(byte[] data, int offset) {
    return ((data[offset++] & 0xff) << 0) | ((data[offset++] & 0xff) << 8)
        | ((data[offset++] & 0xff) << 16) | ((data[offset] & 0xff) << 24);
  }

  public static void itoarr32(int i, ByteArrayOutputStream out) {
    out.write((byte)i);
    out.write((byte)(i>>8));
    out.write((byte)(i>>16));
    out.write((byte)(i>>24));
  }

  public static void itoarr16(short i, ByteArrayOutputStream out) {
    out.write((byte)i);
    out.write((byte)(i>>8));
  }

  
  public static void reset() {
    programState = StateProgram.STOPPED;
    bridge.reset(connectionState == StateConnection.CONNECTED);
    currentProgram = null;
  }

  public static CNCUI getUI() {
    return ui;
  }

  public static void uiNotify(State s) {
    ui.uiNotify(s);
  }
  
  public static GVirtualCNC getVCNC() {
    return cncVirtual;
  }

  public static void disconnect() {
    try {
      cncComm.disconnect();
      bridge.setConnected(false);
      connectionState = StateConnection.DISCONNECTED;
      uiNotify(connectionState);
      cncComm = null;
    } catch (Throwable t) {
      Log.printStackTrace(t);
    }
  }
  
  public static CNCCommunication getCncCommunicator() {
    return cncComm;
  }

  public static void connect(String port) {
    Log.println(port);
    int tries = 1;
    while (tries-- > 0 && connectionState == StateConnection.DISCONNECTED) {
      try {
        cncComm = new CNCCommunicationUART(); // TODO PETER
        //cncComm = new CNCCommunicationUDP(); // TODO PETER
        cncComm.setListener(ui.getBluePrintPanel());

        cncComm.connect(port, 115200, commMux);
        connectionState = StateConnection.CONNECTED;
        bridge.setConnected(true);
        cncControllerListener.sr(bridge.cncGetStatus());
        bridge.cncInit();
        commMux.txNodeAlert(0x12, null);
      } catch (Throwable t) {
        Log.printStackTrace(t);
        try {
          if (cncComm != null) cncComm.disconnect();
        } catch (IOException e) {}
        connectionState = StateConnection.DISCONNECTED;
        bridge.setConnected(false);
      }
    }
    uiNotify(connectionState);
  }

  public static void loadProgram(File f) {
    try {
      cncVirtual.setOrigin();
      Program p = Program.loadGCode(f, cncVirtual, ui.getBluePrintPanel());
      ui.addProgram(p);
    } catch (Throwable t) {
      Log.printStackTrace(t);
    }
  }

  public static Program getProgram() {
    return currentProgram;
  }
  
  public static boolean isManualEnabled() {
    return (programState == StateProgram.PAUSED || programState == StateProgram.STOPPED)
    && connectionState == StateConnection.CONNECTED;
  }
  
  public static boolean isConnected() {
    return connectionState == StateConnection.CONNECTED;
  }
  
  public static Point unitToStep(Point p) {
    return cncVirtual.unitToStep(p);
  }

  public static Point stepToUnit(Point p) {
    return cncVirtual.stepToUnit(p);
  }
  
  public static Point feedUPMtoSPS(Point p) {
    p = unitToStep(p);
    p.divideI(60);
    return p;
  }

  // Action mappings

  static void actionConnectDisconnect() {
    if (connectionState == StateConnection.CONNECTED) {
      Log.println("<< DISCONNECTING");
      disconnect();
    } else {
      Log.println(">> CONNECTING");
      connect(ui.getSelectedPort());
    }
  }

  static void actionOnOff() {
    if ((curSr & CNCProtocol.CNC_STATUS_CONTROL_ENABLED) == 0) {
      bridge.cncEnable(true);
    } else {
      bridge.cncEnable(false);
    }
  }

  public static void actionProgram() {
    switch (programState) {
    case STOPPED:
      Program p = ui.getProgram();
      currentProgram = p;
      bridge.enterProgramMode();
      Log.println("queueing " + p.cncCommandList.size() + " commands");
      bridge.addQueuedCommands(p.cncCommandList);
      bridge.latchQueueStart();
      ui.setCommandInterval(p.cncCommandList.size());
      break;
    case RUNNING:
      bridge.pauseProgramMode();
      break;
    case ENDING:
      break;
    case PAUSED:
      bridge.resumeProgramMode();
      break;
    }

  }

  public static void actionReset() {
    reset();
  }

  public static void actionProgramAbort() {
    bridge.abortProgramMode();
  }
  
  public static void actionRefXY() {
    Point p = bridge.cncGetPos();
    Point o = bridge.cncGetOffsPos();
    bridge.cncSetOffs((int)(o.x - p.x), (int)(o.y - p.y), (int)(o.z));
  }

  public static void actionRefZ() {
    Point p = bridge.cncGetPos();
    Point o = bridge.cncGetOffsPos();
    bridge.cncSetOffs((int)(o.x), (int)(o.y), (int)(o.z - p.z));
  }

  public static void actionGoUser() {
    Point s = bridge.cncGetPos();
    Point d = cncVirtual.unitToStep(ui.getUserPosition());
    Point m = s.diff(d);
    Log.println("S" + s + " D" + d + " M" + m);
    bridge.goSomewhere(m, ui.getUserFeedRates(), true);
  }

  public static void actionGoHome() {
    Point p = bridge.cncGetPos();
    p.negateI();
    bridge.goSomewhere(p, ui.getUserFeedRates(), true);
  }

  public static void actionGoOrigo() {
    Point p = bridge.cncGetPos();
    Point o = bridge.cncGetOffsPos();
    Point m = p.diff(o);
    bridge.goSomewhere(m, ui.getUserFeedRates(), true);
  }
  
  public static void actionConfigApply() {
    Point stepsPerUnit = ui.getConfig(GVirtualCNC.CONFIG_SPU);
    Point maxFeedsSPS = ui.getConfig(GVirtualCNC.CONFIG_MAX_FEED);
    Point absoluteMaxFrequency = ui.getConfig(GVirtualCNC.CONFIG_ABS_MAX_FEED);
    Point rapidDelta = ui.getConfig(GVirtualCNC.CONFIG_RAPID_D);
    Point axisInversion = ui.getConfig(GVirtualCNC.CONFIG_INVERT);
    
    cncVirtual.setStepsPerUnit(stepsPerUnit);
    cncVirtual.setMaxFeedsAsStepsPerSec(maxFeedsSPS);
    cncVirtual.setRapidFrequency(absoluteMaxFrequency);
    cncVirtual.setRapidDelta(rapidDelta);
    cncVirtual.setInversion(axisInversion);
    
    ui.updateAccordingToMachineSettings();
    
    bridge.cncConfigure(cncVirtual);
  }
  
  public static void actionFirmware() {
    // TODO
    File f = UIUtil.selectFile(ui, "Select fw", "Load");
    fileTx.sendFile(f);
  }

  public static void setAction(Component c, String action) {
    if (c instanceof JButton) {
      ((JButton) c).setActionCommand(action);
      ((JButton) c).addActionListener(actionListener);
    }
  }

  static ActionListener actionListener = new ActionListener() {

    @Override
    public void actionPerformed(ActionEvent e) {
      if (e.getActionCommand().equals(ACTION_CONNECT_DISCONNECT)) {
        actionConnectDisconnect();
      } else if (e.getActionCommand().equals(ACTION_ON_OFF)) {
        actionOnOff();
      } else if (e.getActionCommand().equals(ACTION_PROGRAM_LOAD)) {
        File f = UIUtil.selectFile(ui, "Load program", "OK");
        loadProgram(f);
      } else if (e.getActionCommand().equals(ACTION_PROGRAM_ABORT)) {
        actionProgramAbort();
      } else if (e.getActionCommand().equals(ACTION_PROGRAM)) {
        actionProgram();
      } else if (e.getActionCommand().equals(ACTION_RESET)) {
        actionReset();
      } else if (e.getActionCommand().equals(ACTION_FIRMWARE)) {
        actionFirmware();
      } else if (e.getActionCommand().equals(ACTION_SET_REF_XY)) {
        actionRefXY();
      } else if (e.getActionCommand().equals(ACTION_SET_REF_Z)) {
        actionRefZ();
      } else if (e.getActionCommand().equals(ACTION_GO_USER)) {
        actionGoUser();
      } else if (e.getActionCommand().equals(ACTION_GO_HOME)) {
        actionGoHome();
      } else if (e.getActionCommand().equals(ACTION_GO_ORIGO)) {
        actionGoOrigo();
      } else if (e.getActionCommand().equals(ACTION_CONFIG_APPLY)) {
        actionConfigApply();
      }
    }
  };

  // cnc listeners

  static CNCListener cncControllerListener = new CNCListener() {
    @Override
    public void sr(int sr) {
      if (curSr == -1) {
        curSr = ~sr;
      }
      int changeSr = curSr ^ sr;
      if ((changeSr & CNCProtocol.CNC_STATUS_CONTROL_ENABLED) != 0) {
        uiNotify((sr & CNCProtocol.CNC_STATUS_CONTROL_ENABLED) == 0 ? StateOnOff.OFF
            : StateOnOff.ON);
      }
      curSr = sr;
    }

    @Override
    public void pos(double x, double y, double z) {
      uiNotify(new StatePosition(new Point(UnitType.MILLIMETERS, x, y, z)));
    }

    @Override
    public void curCommand(int id, double e, double s, CNCCommand c) {
      uiNotify(new StateCommandExe(id - CNCBridge.MOTION_ID_START, c));
    }
  };

  public static CNCMacroListener cncProgramControllerListener = new CNCMacroListener() {

    @Override
    public void stopped(boolean programEnd, Point where, Point offset) {
      programState = programEnd ? StateProgram.STOPPED : StateProgram.PAUSED;
      if (where != null) {
        Log.println("cnc stop @ " + where + " offset " + offset);
      }
      uiNotify(programState);
    }

    @Override
    public void ending(Point where, Point offset) {
      if (where != null) {
        Log.println("cnc ending @ " + where + " offset " + offset);
      }
      programState = StateProgram.ENDING;
      uiNotify(programState);
    }

    @Override
    public void started() {
      programState = StateProgram.RUNNING;
      uiNotify(programState);
    }
  };

  // state enums

  public interface State {
  }

  public static class StateCommandExe implements State {
    public int ix;
    CNCCommand c;

    public StateCommandExe(int ix, CNCCommand c) {
      this.ix = ix;
      this.c = c;
    }
  }

  public static class StatePosition implements State {
    public Point p;

    public StatePosition(Point p) {
      this.p = p;
    }
  }

  public enum StateConnection implements State {
    CONNECTED, DISCONNECTED
  }

  public enum StateMode implements State {
    PROGRAM, MANUAL
  }

  public enum StateOnOff implements State {
    ON, OFF
  }

  public enum StateProgram implements State {
    STOPPED, RUNNING, PAUSED, ENDING
  }

}
