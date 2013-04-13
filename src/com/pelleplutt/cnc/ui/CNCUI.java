package com.pelleplutt.cnc.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import com.pelleplutt.cnc.Controller;
import com.pelleplutt.cnc.Program;
import com.pelleplutt.cnc.ctrl.CNCCommand;
import com.pelleplutt.cnc.ctrl.GVirtualCNC;
import com.pelleplutt.cnc.ctrl.PropertyAxis;
import com.pelleplutt.cnc.ctrl.PropertyHolder;
import com.pelleplutt.cnc.io.CNCProtocol;
import com.pelleplutt.cnc.types.Point;
import com.pelleplutt.cnc.types.UnitType;
import com.pelleplutt.io.PortConnector;
import com.pelleplutt.util.AppSystem;
import com.pelleplutt.util.AppSystem.Disposable;
import com.pelleplutt.util.Log;

public class CNCUI extends JPanel implements Disposable {
  private static final long serialVersionUID = -1323923914648403876L;
  static final Color COLOR_AXIS = new Color(64,255,64);
  static final Color COLOR_AXIS_MOD = new Color(255,64,64);
  static final Color COLOR_FEED = new Color(255,255,64);
  static final Color COLOR_AXIS_DISABLED = new Color(0,192,0);
  static final Color COLOR_FEED_DISABLED = new Color(192,192,0);
  static final Color COLOR_AXIS_DISABLED_MOD = new Color(192,0,0);
  static final Color COLOR_FEED_DISABLED_MOD = new Color(192,192,0);

  JList<Program> programList;
  DefaultListModel<Program> programListModel;
  JButton programLoadBut;

  JComboBox<String> connectionBox;
  JButton connectBut;

  JButton modeBut;
  JButton onoffBut;
  JButton resetBut;

  JButton progBut;
  JButton progAbortBut;
  JButton fwBut;
  JSlider progCmdSlider;
  JLabel progCmdLabel;

  PositionTextField xField, yField, zField;
  JTextField xFeedField, yFeedField, zFeedField;
  JSlider xFeedSlider, yFeedSlider, zFeedSlider;
  JButton setRefButXY;
  JButton setRefButZ;
  JButton gotoUserBut;
  JButton gotoHomeBut;
  JButton gotoOrigoBut;

  CNCTextField[] maxFeedFields;
  CNCTextField[] stepsPerUnitFields;
  CNCTextField[] absMaxFeedFields;
  CNCTextField[] rapidDeltaFields;
  CNCCheckBox[] invertCheckboxes;
  JButton configBut;
  JButton configLoadBut;
  JButton configSaveBut;
  JButton configSaveAsBut;
  
  BluePrintPanel bluePrintPanel;

  int panelW = 800;
  int panelH = 600;
  
  int progCommandCount, progCommandIx;

  volatile boolean autoCmdIxChange = false;
  
  final Object LOCK_POS_FIELD = new Object();
  Point lastPos = new Point(UnitType.STEPS, Double.MAX_VALUE,Double.MAX_VALUE,Double.MAX_VALUE);


  public CNCUI() {
    build();
  }

  void build() {
    // connection
    connectionBox = new JComboBox<String>();
    connectionBox.setPreferredSize(new Dimension(100, 24));
    connectionBox.setMinimumSize(new Dimension(100, 24));
    connectionBox.setSize(new Dimension(100, 24));
    connectBut = new JButton("Connect");
    connectionBox.addPopupMenuListener(new PopupMenuListener() {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        String sel = getSelectedPort();
        connectionBox.setModel(new DefaultComboBoxModel<String>(PortConnector
            .getPortConnector().getDevices()));
        if (sel != null)
          connectionBox.setSelectedItem(sel);
      }

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
      }

      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {
      }
    });

    // main mode panel
    onoffBut = new JButton("---");
    onoffBut.setForeground(Color.red);
    modeBut = new JButton("Manual");
    resetBut = new JButton("Reset");

    JPanel mainCtrlPanel = new JPanel(new GridLayout(5, 1, 4, 4));
    mainCtrlPanel.add(connectionBox);
    mainCtrlPanel.add(connectBut);
    mainCtrlPanel.add(onoffBut);
    mainCtrlPanel.add(modeBut);
    mainCtrlPanel.add(resetBut);

    // program control panel
    progBut = new JButton("Run program");
    progAbortBut = new JButton("Abort program");
    programLoadBut = new JButton("Load");
    JPanel programCtrlPanel = new JPanel(new GridLayout(2,2,4,4));
    programCtrlPanel.add(progBut);
    programCtrlPanel.add(progAbortBut);
    programCtrlPanel.add(programLoadBut);

    // program panel
    JPanel programListPanel = new JPanel(new BorderLayout());
    programListModel = new DefaultListModel<Program>();
    programList = new JList<Program>(programListModel);
    programListModel.addListDataListener(programListListener);
    programList.addKeyListener(programListKeyListener);
    programList.setPreferredSize(new Dimension(200, 50));
    programList.setMinimumSize(new Dimension(200, 50));
    programList.setSize(new Dimension(200, 50));
    programListPanel.add(new JScrollPane(programList), BorderLayout.CENTER);
    programListPanel.add(programCtrlPanel, BorderLayout.SOUTH);

    
    // program special panel
    progCmdSlider = new JSlider(SwingConstants.HORIZONTAL);
    progCmdSlider.setSnapToTicks(false);
    progCmdSlider.setPaintLabels(false);
    progCmdSlider.setPaintTicks(false);
    progCmdSlider.getModel().addChangeListener(progCmdSlideListener);
    progCmdLabel = new JLabel();
    JPanel progSpecDetailPanel = new JPanel();
    progSpecDetailPanel.add(progCmdSlider);
    progSpecDetailPanel.add(progCmdLabel);
    JPanel progSpecPanel = new JPanel(new BorderLayout());
    progSpecPanel.add(progSpecDetailPanel, BorderLayout.CENTER);
    progSpecPanel.add(programListPanel, BorderLayout.EAST);

    fwBut = new JButton("Firware upload");
    
    progSpecPanel.add(fwBut, BorderLayout.WEST);

    // manual special panel
    xField = new PositionTextField();
    yField = new PositionTextField();
    zField = new PositionTextField();
    xFeedField = new JTextField();
    yFeedField = new JTextField();
    zFeedField = new JTextField();
    xFeedSlider = new JSlider(SwingConstants.HORIZONTAL);
    xFeedSlider.setSnapToTicks(false);
    xFeedSlider.setPaintLabels(false);
    xFeedSlider.setPaintTicks(false);
    yFeedSlider = new JSlider(SwingConstants.HORIZONTAL);
    yFeedSlider.setSnapToTicks(false);
    yFeedSlider.setPaintLabels(false);
    yFeedSlider.setPaintTicks(false);
    zFeedSlider = new JSlider(SwingConstants.HORIZONTAL);
    zFeedSlider.setSnapToTicks(false);
    zFeedSlider.setPaintLabels(false);
    zFeedSlider.setPaintTicks(false);
    createConnector(xFeedField, xFeedSlider);
    createConnector(yFeedField, yFeedSlider);
    createConnector(zFeedField, zFeedSlider);
    
    xFeedSlider.setValue((int)Controller.getVCNC().maxFeedsUPM.x / 2);
    yFeedSlider.setValue((int)Controller.getVCNC().maxFeedsUPM.y / 2);
    zFeedSlider.setValue((int)Controller.getVCNC().maxFeedsUPM.z / 2);

        JLabel xLabel = new JLabel("X");
    JLabel yLabel = new JLabel("Y");
    JLabel zLabel = new JLabel("Z");
    JLabel xUnitLabel = new JLabel("mm");
    JLabel yUnitLabel = new JLabel("mm");
    JLabel zUnitLabel = new JLabel("mm");
    JPanel manualSpecPosPanel = new JPanel();
    GridBagLayout gbl = new GridBagLayout();
    manualSpecPosPanel.setLayout(gbl);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;

    gbc.ipady = 4;

    insertManualSpec(manualSpecPosPanel, gbc, gbl, xLabel, xField, xUnitLabel,
        xFeedField, xFeedSlider);
    insertManualSpec(manualSpecPosPanel, gbc, gbl, yLabel, yField, yUnitLabel,
        yFeedField, yFeedSlider);
    insertManualSpec(manualSpecPosPanel, gbc, gbl, zLabel, zField, zUnitLabel,
        zFeedField, zFeedSlider);

    setRefButXY = new JButton("Ref XY");
    setRefButZ = new JButton("Ref Z");
    gotoUserBut = new JButton("Go");
    gotoHomeBut = new JButton("Home");
    gotoOrigoBut= new JButton("Abs Origo");
    
    JPanel manualSpecButPanel = new JPanel(new GridLayout(5, 1,4,4));
    manualSpecButPanel.add(setRefButXY);
    manualSpecButPanel.add(setRefButZ);
    manualSpecButPanel.add(gotoUserBut);
    manualSpecButPanel.add(gotoHomeBut);
    manualSpecButPanel.add(gotoOrigoBut);
    
    JPanel manualSpecPanel = new JPanel(new BorderLayout());
    manualSpecPanel.add(manualSpecPosPanel, BorderLayout.CENTER);
    manualSpecPanel.add(manualSpecButPanel, BorderLayout.EAST);
    
    // config panel
    JPanel configSpecPanel = new JPanel();
    configSpecPanel.setLayout(new GridLayout(6,PropertyAxis.values().length+1));
    maxFeedFields = new CNCTextField[PropertyAxis.values().length];
    stepsPerUnitFields = new CNCTextField[PropertyAxis.values().length];
    absMaxFeedFields = new CNCTextField[PropertyAxis.values().length];
    rapidDeltaFields = new CNCTextField[PropertyAxis.values().length];
    invertCheckboxes = new CNCCheckBox[PropertyAxis.values().length];
    for (int i = 0; i < PropertyAxis.values().length; i++) {
      stepsPerUnitFields[i] = new CNCTextField(Controller.getVCNC(), GVirtualCNC.CONFIG_SPU, PropertyAxis.values()[i]) {
        public String format(String s) {
          double d = Double.valueOf(s);
          return Integer.toString((int)d);
        }
      };
      maxFeedFields[i] = new CNCTextField(Controller.getVCNC(), GVirtualCNC.CONFIG_MAX_FEED, PropertyAxis.values()[i]) {
        public String format(String s) {
          double d = Double.valueOf(s);
          d = d * 60.0 / Controller.getVCNC().getPropertyDouble(GVirtualCNC.CONFIG_SPU, axis);
          return Integer.toString((int)d);
        }
        public String unformat(String s) {
          double d = Double.valueOf(s);
          d = d * Controller.getVCNC().getPropertyDouble(GVirtualCNC.CONFIG_SPU, axis) / 60;
          return Integer.toString((int)d);
        }
      };
      absMaxFeedFields[i] = new CNCTextField(Controller.getVCNC(), GVirtualCNC.CONFIG_ABS_MAX_FEED, PropertyAxis.values()[i]){
        public String format(String s) {
          double d = Double.valueOf(s);
          d = d * 60.0 / Controller.getVCNC().getPropertyDouble(GVirtualCNC.CONFIG_SPU, axis);
          return Integer.toString((int)d);
        }
        public String unformat(String s) {
          double d = Double.valueOf(s);
          d = d * Controller.getVCNC().getPropertyDouble(GVirtualCNC.CONFIG_SPU, axis) / 60;
          return Integer.toString((int)d);
        }
      };
      rapidDeltaFields[i] = new CNCTextField(Controller.getVCNC(), GVirtualCNC.CONFIG_RAPID_D, PropertyAxis.values()[i]) {
        public String format(String s) {
          double d = Double.valueOf(s);
          d = d / (double)(1 << CNCProtocol.CNC_FP_DECIMALS);
          return Double.toString(d);
        }
        public String unformat(String s) {
          double d = Double.valueOf(s);
          d = d * (double)(1 << CNCProtocol.CNC_FP_DECIMALS);
          return Integer.toString((int)d);
        }
      };
      invertCheckboxes[i] = new CNCCheckBox(Controller.getVCNC(), GVirtualCNC.CONFIG_INVERT, PropertyAxis.values()[i]);
    }
    
    configSpecPanel.add(new JLabel());
    for (PropertyAxis axis : PropertyAxis.values()) {
      configSpecPanel.add(new JLabel(axis.name().toUpperCase()));
    }

    configSpecPanel.add(new JLabel("Steps Per Unit"));
    for (int i = 0; i < PropertyAxis.values().length; i++) {
      configSpecPanel.add(stepsPerUnitFields[i]);
    }

    configSpecPanel.add(new JLabel("Max Feed"));
    for (int i = 0; i < PropertyAxis.values().length; i++) {
      configSpecPanel.add(maxFeedFields[i]);
    }

    configSpecPanel.add(new JLabel("Rapid Feed"));
    for (int i = 0; i < PropertyAxis.values().length; i++) {
      configSpecPanel.add(absMaxFeedFields[i]);
    }

    configSpecPanel.add(new JLabel("Rapid Feed Delta"));
    for (int i = 0; i < PropertyAxis.values().length; i++) {
      configSpecPanel.add(rapidDeltaFields[i]);
    }

    configSpecPanel.add(new JLabel("Axis Invert"));
    for (int i = 0; i < PropertyAxis.values().length; i++) {
      configSpecPanel.add(invertCheckboxes[i]);
    }

    JPanel configButPanel = new JPanel();
    configButPanel.setLayout(new GridLayout(4,1));
    
    configBut = new JButton("Apply");
    configLoadBut = new JButton("Load");
    configSaveBut = new JButton("Save");
    configSaveAsBut = new JButton("Save as");
    configButPanel.add(configBut);
    configButPanel.add(configLoadBut);
    configButPanel.add(configSaveBut);
    configButPanel.add(configSaveAsBut);
      
    JPanel configPanel = new JPanel(new BorderLayout());
    configPanel.add(configSpecPanel, BorderLayout.CENTER);
    configPanel.add(configButPanel, BorderLayout.EAST);
    
    // main control panel
    updateAccordingToMachineSettings();

    JTabbedPane specTabs = new JTabbedPane();
    specTabs.addTab("Program", progSpecPanel);
    specTabs.addTab("Manual", manualSpecPanel);
    specTabs.addTab("Configuration", configPanel);
    specTabs.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));

    JPanel controlPanel = new JPanel(new BorderLayout());
    controlPanel.add(mainCtrlPanel, BorderLayout.WEST);
    controlPanel.add(specTabs, BorderLayout.CENTER);

    // main drawing panel
    bluePrintPanel = new BluePrintPanel(panelW, panelH);

    setLayout(new BorderLayout());
    add(bluePrintPanel, BorderLayout.CENTER);
    add(controlPanel, BorderLayout.SOUTH);

    // actions
    Controller.setAction(connectBut, Controller.ACTION_CONNECT_DISCONNECT);
    Controller.setAction(modeBut, Controller.ACTION_SWITCH_MODE);
    Controller.setAction(onoffBut, Controller.ACTION_ON_OFF);
    Controller.setAction(programLoadBut, Controller.ACTION_PROGRAM_LOAD);
    Controller.setAction(progBut, Controller.ACTION_PROGRAM);
    Controller.setAction(progAbortBut, Controller.ACTION_PROGRAM_ABORT);
    Controller.setAction(resetBut, Controller.ACTION_RESET);

    Controller.setAction(fwBut, Controller.ACTION_FIRMWARE);

    
    Controller.setAction(setRefButXY, Controller.ACTION_SET_REF_XY);
    Controller.setAction(setRefButZ, Controller.ACTION_SET_REF_Z);
    Controller.setAction(gotoUserBut, Controller.ACTION_GO_USER);
    Controller.setAction(gotoHomeBut, Controller.ACTION_GO_HOME);
    Controller.setAction(gotoOrigoBut, Controller.ACTION_GO_ORIGO);

    Controller.setAction(configBut, Controller.ACTION_CONFIG_APPLY);
    //Controller.setAction(configLoadBut, Controller.);
    //Controller.setAction(configSaveAsBut, Controller.);
    //Controller.setAction(configSaveBut, Controller.);

    setConnectionState(Controller.StateConnection.DISCONNECTED);

    startUIMsgThread();
    AppSystem.addDisposable(this);
  }

  private void insertManualSpec(JPanel manualSpecPanel, GridBagConstraints gbc,
      GridBagLayout gbl, JLabel axisLabel, JTextField axisField,
      JLabel axisUnitLabel, JTextField axisFeedField, JSlider axisFeedSlider) {
    axisFeedField.setBackground(Color.black);
    axisFeedField.setForeground(COLOR_FEED);
    axisFeedField.setDisabledTextColor(COLOR_FEED_DISABLED);
    axisField.setCaretColor(Color.white);
    axisFeedField.setCaretColor(Color.white);
    gbc.gridwidth = 1;
    gbc.gridheight = 2;
    gbc.weightx = 1.0;
    gbc.weighty = 2.0;
    gbl.setConstraints(axisLabel, gbc);
    manualSpecPanel.add(axisLabel);
    gbc.gridheight = 1;
    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weighty = 1.0;
    gbl.setConstraints(axisField, gbc);
    manualSpecPanel.add(axisField);
    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbl.setConstraints(axisUnitLabel, gbc);
    manualSpecPanel.add(axisUnitLabel);
    gbc.weightx = 1.0;
    gbc.gridwidth = 1;
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbl.setConstraints(axisFeedField, gbc);
    manualSpecPanel.add(axisFeedField);
    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbl.setConstraints(axisFeedSlider, gbc);
    manualSpecPanel.add(axisFeedSlider);

  }

  private void createConnector(JTextField field, JSlider slider) {
    FieldSliderListener cl = new FieldSliderListener(field, slider);
    field.getDocument().addDocumentListener(cl);
    slider.addChangeListener(cl);
  }
  
  public void updateAccordingToMachineSettings() {
    // TODO
    xFeedSlider.setMaximum((int)Controller.getVCNC().maxFeedsUPM.x);
    yFeedSlider.setMaximum((int)Controller.getVCNC().maxFeedsUPM.y);
    zFeedSlider.setMaximum((int)Controller.getVCNC().maxFeedsUPM.z);
    
    for (int i = 0; i < PropertyAxis.values().length; i++) {
      maxFeedFields[i].setUnModified();
      stepsPerUnitFields[i].setUnModified();
      absMaxFeedFields[i].setUnModified();
      rapidDeltaFields[i].setUnModified();
      invertCheckboxes[i].setUnModified();
    }
  }
  
  private void setManualEnabled(boolean e) {
    xField.setEnabled(e);
    xFeedField.setEnabled(e);
    xFeedSlider.setEnabled(e);
    yField.setEnabled(e);
    yFeedField.setEnabled(e);
    yFeedSlider.setEnabled(e);
    zField.setEnabled(e);
    zFeedField.setEnabled(e);
    zFeedSlider.setEnabled(e);
    
    gotoHomeBut.setEnabled(e);
    gotoOrigoBut.setEnabled(e);
    gotoUserBut.setEnabled(e);
    setRefButXY.setEnabled(e);
    setRefButZ.setEnabled(e);
  }
  
  private void updateManualEnabled() {
    setManualEnabled(Controller.isManualEnabled());
  }

  public BluePrintPanel getBluePrintPanel() {
    return bluePrintPanel;
  }

  public void addProgram(Program p) {
    programListModel.addElement(p);
    bluePrintPanel.addBluePrint(p.bluePrintRenderer);
  }

  public void removeProgram(Program p) {
    programListModel.removeElement(p);
    bluePrintPanel.removeBluePrint(p.bluePrintRenderer);
  }

  public void setCommandInterval(int commands) {
    progCommandCount = commands;
    progCmdSlider.setMinimum(0);
    progCmdSlider.setMaximum(commands);
    updateProgCommandLabel();
  }

  public void setCommandIndex(int ix) {
    progCommandIx = ix;
    autoCmdIxChange = true;
    progCmdSlider.setValue(ix);
    autoCmdIxChange = false;
    updateProgCommandLabel();
  }
  
  void updateProgCommandLabel() {
    if (progCommandIx < 0) {
      progCmdLabel.setText("");
    } else {
      progCmdLabel.setText(progCommandIx + " / " + progCommandCount);
    }
  }

  private void setModeState(Controller.StateMode m) {
    if (m == Controller.StateMode.MANUAL) {
      modeBut.setText("Program");
    } else {
      modeBut.setText("Manual");
    }
    repaint();
  }

  private void setConnectionState(Controller.StateConnection c) {
    if (c == Controller.StateConnection.CONNECTED) {
      connectBut.setText("Disconnect");
      onoffBut.setEnabled(true);
      modeBut.setEnabled(true);
      progBut.setEnabled(true);
    } else {
      connectBut.setText("Connect");
      onoffBut.setEnabled(false);
      modeBut.setEnabled(false);
      progBut.setEnabled(false);
      progCmdSlider.setEnabled(false);
    }
    updateManualEnabled();
    repaint();
  }

  private void setOnOffState(Controller.StateOnOff c) {
    if (c == Controller.StateOnOff.ON) {
      onoffBut.setText("CNC Deactivate");
    } else {
      onoffBut.setText("CNC Activate");
    }
    repaint();
  }

  private void setProgramState(Controller.StateProgram p) {
    if (p == Controller.StateProgram.STOPPED) {
      progBut.setText("Run program");
      progBut.setEnabled(true);
      progAbortBut.setEnabled(false);
      progCmdSlider.setEnabled(false);
      xField.setUnModified();
      yField.setUnModified();
      zField.setUnModified();
    } else if (p == Controller.StateProgram.RUNNING) {
      progBut.setText("Pause program");
      progAbortBut.setEnabled(true);
      progBut.setEnabled(true);
      progCmdSlider.setEnabled(false);
    } else if (p == Controller.StateProgram.ENDING) {
      progBut.setEnabled(false);
      progAbortBut.setEnabled(false);
      progCmdSlider.setEnabled(false);
    } else if (p == Controller.StateProgram.PAUSED) {
      progBut.setText("Resume program");
      progAbortBut.setEnabled(true);
      progBut.setEnabled(true);
      progCmdSlider.setEnabled(true);
    }
    updateManualEnabled();
    repaint();
  }
  
  private void setPosition(Point p) {
    synchronized (LOCK_POS_FIELD) {
      if (lastPos.x != p.x) xField.setText(Double.toString(p.x));
      if (lastPos.y != p.y) yField.setText(Double.toString(p.y));
      if (lastPos.z != p.z) zField.setText(Double.toString(p.z));
      lastPos = p;
    }
  }

  public String getSelectedPort() {
    Object i = connectionBox.getSelectedItem();
    return i == null ? null : i.toString();
  }

  //
  // Program list listeners
  //

  ListDataListener programListListener = new ListDataListener() {
    @Override
    public void contentsChanged(ListDataEvent e) {
      Log.println(e);
    }

    @Override
    public void intervalAdded(ListDataEvent e) {
      Log.println(e);
    }

    @Override
    public void intervalRemoved(ListDataEvent e) {
      Log.println(e);
    }
  };

  KeyListener programListKeyListener = new KeyListener() {
    @Override
    public void keyTyped(KeyEvent e) {
      if (e.getKeyChar() == 0x7f || e.getKeyChar() == 0x08) {
        int[] indices = programList.getSelectedIndices();
        for (int i = indices.length - 1; i >= 0; i--) {
          Program p = programListModel.elementAt(indices[i]);
          removeProgram(p);
        }
      }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
    }
  };

  public Program getProgram() {
    // TODO FIX
    return programList.getSelectedValue();
  }

  volatile boolean running = true;
  final Object LOCK_MSG = new Object();
  List<Controller.State> msgList = new ArrayList<Controller.State>();

  @Override
  public void dispose() {
    running = false;
    synchronized (LOCK_MSG) {
      LOCK_MSG.notify();
    }
  }

  void startUIMsgThread() {
    new Thread(new Runnable() {
      public void run() {
        msgLoop();
      }
    }, "uimsgthread").start();
  }

  void msgLoop() {
    while (running) {
      synchronized (LOCK_MSG) {
        AppSystem.waitSilently(LOCK_MSG);
        while (running && !msgList.isEmpty()) {
          Controller.State s = msgList.remove(0);
          if (s instanceof Controller.StateConnection) {
            setConnectionState((Controller.StateConnection) s);
          } else if (s instanceof Controller.StateMode) {
            setModeState((Controller.StateMode) s);
          } else if (s instanceof Controller.StateOnOff) {
            setOnOffState((Controller.StateOnOff) s);
          } else if (s instanceof Controller.StateProgram) {
            setProgramState((Controller.StateProgram) s);
          } else if (s instanceof Controller.StateCommandExe) {
            Controller.StateCommandExe sce = (Controller.StateCommandExe) s;
            setCommandIndex(sce.ix);
          } else if (s instanceof Controller.StatePosition) {
            Controller.StatePosition sp = (Controller.StatePosition) s;
            setPosition(sp.p);
          }
        }
      }
    }
  }

  public void uiNotify(Controller.State state) {
    synchronized (LOCK_MSG) {
      msgList.add(state);
      LOCK_MSG.notify();
    }
  }
  
  public Point getUserFeedRates() {
    Point p = new Point(UnitType.MILLIMETERS, 
        xFeedSlider.getValue(),
        yFeedSlider.getValue(),
        zFeedSlider.getValue());

    return p;
  }

  public Point getUserPosition() {
    synchronized (LOCK_POS_FIELD) {
      double x,y,z;
      x = Double.parseDouble(xField.getText());
      y = Double.parseDouble(yField.getText());
      z = Double.parseDouble(zField.getText());
      Point p = new Point(UnitType.MILLIMETERS, x, y, z);
      return p;
    }
  }
  
  public Point getConfig(String config) {
    Point p = null;
    if (config.equals(GVirtualCNC.CONFIG_ABS_MAX_FEED)) {
      p = new Point(UnitType.MILLIMETERS,
          Double.parseDouble(absMaxFeedFields[0].getValue()),
          Double.parseDouble(absMaxFeedFields[1].getValue()),
          Double.parseDouble(absMaxFeedFields[2].getValue()));
    }
    else if (config.equals(GVirtualCNC.CONFIG_MAX_FEED)) {
      p = new Point(UnitType.MILLIMETERS,
          Double.parseDouble(maxFeedFields[0].getValue()),
          Double.parseDouble(maxFeedFields[1].getValue()),
          Double.parseDouble(maxFeedFields[2].getValue()));
    }
    else if (config.equals(GVirtualCNC.CONFIG_SPU)) {
      p = new Point(UnitType.MILLIMETERS,
          Double.parseDouble(stepsPerUnitFields[0].getValue()),
          Double.parseDouble(stepsPerUnitFields[1].getValue()),
          Double.parseDouble(stepsPerUnitFields[2].getValue()));
    }
    else if (config.equals(GVirtualCNC.CONFIG_RAPID_D)) {
      p = new Point(
          Double.parseDouble(rapidDeltaFields[0].getValue()),
          Double.parseDouble(rapidDeltaFields[1].getValue()),
          Double.parseDouble(rapidDeltaFields[2].getValue()));
    }
    else if (config.equals(GVirtualCNC.CONFIG_INVERT)) {
      p = new Point(
          Double.parseDouble(invertCheckboxes[0].getValue()),
          Double.parseDouble(invertCheckboxes[1].getValue()),
          Double.parseDouble(invertCheckboxes[2].getValue()));
    }
    
    return p;
  }

  protected ChangeListener progCmdSlideListener = new ChangeListener() {
    @Override
    public void stateChanged(ChangeEvent ce) {
      if (autoCmdIxChange)
        return;
      int ix = progCmdSlider.getValue();
      Program p = Controller.getProgram();
      if (ix >= 0 && ix < p.cncCommandList.size()) {
        progCommandIx = ix;
        updateProgCommandLabel();
        CNCCommand c = p.cncCommandList.get(ix);
        if (c instanceof CNCCommand.Move) {
          bluePrintPanel.markCommand(c);
        }
      }
    }
  };
  
  class CNCTextField extends JTextField implements KeyListener {
    private static final long serialVersionUID = 5295732175872382949L;
    volatile boolean modified = false;
    PropertyHolder p;
    String key;
    PropertyAxis axis;
    
    void init() {
      this.addKeyListener(this);
      setBackground(Color.black);
      setForeground(COLOR_AXIS);
      setDisabledTextColor(COLOR_AXIS_DISABLED);
    }
    
    public CNCTextField(PropertyHolder ph, String key, PropertyAxis axis) {
      super();
      init();
      this.p = ph;
      this.key = key;
      this.axis = axis;
    }

    void setModified() {
      if (!modified) {
        modified = true;
        setForeground(COLOR_AXIS_MOD);
        setDisabledTextColor(COLOR_AXIS_DISABLED_MOD);
      }
    }
    
    public String format(String s) {
      return s;
    }
    
    public String unformat(String s) {
      return s;
    }
    
    public String getValue() {
      return unformat(getText());
    }
    
    public void setDefaultValue() {
      if (p != null && key != null) {
        String s = p.getProperty(key, axis);
        if (s != null) {
          super.setText(format(s));
        }
      }
    }
    
    public void setUnModified() {
      modified = false;
      setForeground(COLOR_AXIS);
      setDisabledTextColor(COLOR_AXIS_DISABLED);
      setDefaultValue();
    }
    public void setText(String s) {
      if (!modified) {
        super.setText(format(s));
      }
    }
    @Override
    public void keyPressed(KeyEvent ke) {
      if (ke.getKeyCode() != KeyEvent.VK_ESCAPE) {
        setModified();
      }
    }
    @Override
    public void keyReleased(KeyEvent ke) {
      if (ke.getKeyCode() == KeyEvent.VK_ESCAPE) {
        setUnModified();
      } else {
        setModified();
      }
    }
    @Override
    public void keyTyped(KeyEvent ke) {
      setModified();
    }
  }
  
  class CNCCheckBox extends JCheckBox implements ChangeListener {
    private static final long serialVersionUID = 5295732175872382941L;
    volatile boolean modified = false;
    PropertyHolder p;
    String key;
    PropertyAxis axis;
    
    void init() {
      this.getModel().addChangeListener(this);
      this.setForeground(COLOR_AXIS);
    }
    
    public CNCCheckBox(PropertyHolder ph, String key, PropertyAxis axis) {
      init();
      this.p = ph;
      this.key = key;
      this.axis = axis;
    }

    void setModified() {
      if (!modified) {
        modified = true;
        setForeground(COLOR_AXIS_MOD);
      }
    }
    
    public boolean format(String s) {
      return Double.parseDouble(s) < 0;
    }
    
    public String unformat(String s) {
      return Double.parseDouble(s) > 0 ? "1" : "-1";
    }
    
    public String getValue() {
      return unformat(getModel().isSelected() ? "-1" : "1");
    }
    
    public void setDefaultValue() {
      if (p != null && key != null) {
        String s = p.getProperty(key, axis);
        if (s != null) {
          super.setSelected(format(s));
        }
      }
    }
    
    public void setUnModified() {
      modified = false;
      setForeground(COLOR_AXIS);
      setDefaultValue();
    }
    public void setSelected(boolean s) {
      if (!modified) {
        super.setSelected(s);
      }
    }

    @Override
    public void stateChanged(ChangeEvent arg0) {
      if (format(p.getProperty(key, axis)) != getModel().isSelected()) {
        setModified();
      } else {
        setUnModified();
      }
    }
  }
  
  class PositionTextField extends CNCTextField implements KeyListener {
    private static final long serialVersionUID = 5295732175872382940L;
    public PositionTextField() {
      super(null, null, null);
      init();
    }
    public void setUnModified() {
      super.setUnModified();
      lastPos.maximizeI();
    }
  }
  
  class FieldSliderListener implements DocumentListener, ChangeListener {
    JTextField tf;
    JSlider sl;
    volatile boolean slMod, tfMod;

    public FieldSliderListener(JTextField tf, JSlider sl) {
      this.tf = tf;
      this.sl = sl;
    }

    @Override
    public void stateChanged(ChangeEvent e) {
      if (!slMod) {
        tfMod = true;
        tf.setText(Integer.toString(sl.getValue()));
        tfMod = false;
      }
    }

    void updateText() {
      if (!tfMod) {
        slMod = true;
        try {
          String text = tf.getText();
          int v;
          if (text.trim().length() == 0) {
            v = 0;
          } else {
            v = Integer.parseInt(tf.getText());
          }
          sl.setValue(v);
        } catch (Throwable t) {
          slMod = false;
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              tfMod = true;
              tf.setText(Integer.toString(sl.getValue()));
              tfMod = false;
            }
          });
        }
        slMod = false;
      }
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
      updateText();
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
      updateText();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
      updateText();
    }

  }
}
