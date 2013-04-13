package com.pelleplutt.cnc;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

import com.pelleplutt.util.AppSystem;


public class Test {

  static int commands = 0;
  static int comments = 0;
  static int warnings = 0;

  /**
   * @param args
   */
  public static void main(String[] args) throws Throwable {
/*    GVirtualCNC gvcnc = new GVirtualCNC();

    // load program
    
    //File file = new File("/home/petera/proj/cnc_ctrl/spindle_ctrlbot.nc");
    //File file = new File("/home/petera/proj/cnc_ctrl/ioexp_breakouttop.nc");
    File file = new File("/home/petera/proj/cnc_ctrl/x.gc");
    
    gvcnc.reset();
    gvcnc.setHardcodedConfiguration();
    Program program = Program.loadGCode(file, gvcnc);
    BluePrintPanel bpp = new BluePrintPanel(1200, 800);
    bpp.addBluePrint(program.bluePrintRenderer);
    
*/
    // setup ui
    
    Controller.construct();
    
    JFrame f = new JFrame();
    f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    f.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        AppSystem.disposeAll();
      }
    });
    
    //f.getContentPane().add(bpp);
    f.getContentPane().add(Controller.getUI());
    f.setLocation(100, 200);
    f.pack();
    f.setVisible(true);
    
    //Controller.connect("/dev/ttyUSB0");

    // start bridge
/*    
    CNCBridge bridge = new CNCBridge();
    bridge.addListener(bpp);
    bridge.setMachine(gvcnc);
    CNCCommunication comm = new CNCCommunication();
    comm.setListener(bpp);
    comm.connect("/dev/ttyUSB0", 115200, bridge);

    bridge.initLatchQueue();
    bridge.addQueuedCommands(program.cncCommandList);
    bridge.enterProgramMode();
    bridge.latchQueueStart();
    */
  }

}
