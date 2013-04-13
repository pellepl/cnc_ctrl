package com.pelleplutt.cnc.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JPanel;

import com.pelleplutt.cnc.Controller;
import com.pelleplutt.cnc.ctrl.CNCCommand;
import com.pelleplutt.cnc.io.CNCBridge;
import com.pelleplutt.cnc.io.CNCCommunication.RxTxListener;
import com.pelleplutt.cnc.io.CNCProtocol;
import com.pelleplutt.cnc.types.Point;
import com.pelleplutt.util.UIUtil;

public class BluePrintPanel extends JPanel implements
    CNCBridge.CNCListener, MouseListener,
    MouseMotionListener, MouseWheelListener, RxTxListener {

  private static final long serialVersionUID = 1500564120445117767L;

  public static final int DRAW_MODE_TOP = 0;
  public static final int DRAW_MODE_ORTO = 1;
  
  volatile int drawMode = DRAW_MODE_TOP;
  
  int status;
  Point cncPos = new Point(0, 0, 0);
  double mag = 5;
  double dx = 10;
  double dy = 10;
  double exePercentage = 0;
  double sentPercentage = 0;
  
  volatile double mx, my;
  
  ImageIcon iconPipeDisabled, iconPipeEnabled, iconPipePopulated, iconPipeFull;
  ImageIcon iconCncDisabled, iconCncEnabled;
  ImageIcon iconLatchFree, iconLatchBusy;
  ImageIcon iconMovStill, iconMovPause, iconMovLin, iconMovRap;
  ImageIcon iconRxTxDis, iconRxTxConn, iconRxTxRx, iconRxTxTx, iconRxTxRxTx;
  ImageIcon iconErrEmergency, iconErrSettings, iconErrConnection;
  
  Color colorExeGlow = new Color(0,255,255,64);
  Color colorExe = new Color(255,255,255,255);
  Color colorSentGlow = new Color(0,255,255,32);
  Color colorSent = new Color(64,255,255,192);
  
  Color colorOrigo = new Color(32,32,32);
  Color colorGridMax = new Color(20,20,20);
  Color colorGridMin = new Color(10,10,10);

  Font font = Font.decode("courier-plain-12");
  
  boolean rx, tx;

  double gridMaxDiv = 10.0;
  double gridMinDiv = 2.5;
  
  List<BluePrintRenderer> blueprints = new ArrayList<BluePrintRenderer>();

  int bpw, bph;
  
  public BluePrintPanel(int w, int h) {
    super();
    this.bpw = w;
    this.bph = h;
    addMouseListener(this);
    addMouseMotionListener(this);
    addMouseWheelListener(this);
    
    iconCncDisabled = UIUtil.createImageIcon("cnc_disabled.png");
    iconCncEnabled = UIUtil.createImageIcon("cnc_enabled.png");

    iconPipeDisabled = UIUtil.createImageIcon("pipe_disabled.png");
    iconPipeEnabled = UIUtil.createImageIcon("pipe_enabled.png");
    iconPipePopulated = UIUtil.createImageIcon("pipe_populated.png");
    iconPipeFull = UIUtil.createImageIcon("pipe_full.png");

    iconLatchFree= UIUtil.createImageIcon("latch_free.png");
    iconLatchBusy = UIUtil.createImageIcon("latch_busy.png");
    
    iconMovStill = UIUtil.createImageIcon("mov_still.png");
    iconMovPause = UIUtil.createImageIcon("mov_pause.png");
    iconMovLin = UIUtil.createImageIcon("mov_lin.png");
    iconMovRap = UIUtil.createImageIcon("mov_rap.png");
    
    iconRxTxDis = UIUtil.createImageIcon("comm_disconnect.png");
    iconRxTxConn = UIUtil.createImageIcon("comm_connect.png");
    iconRxTxRx = UIUtil.createImageIcon("comm_rx.png");
    iconRxTxTx = UIUtil.createImageIcon("comm_tx.png");
    iconRxTxRxTx = UIUtil.createImageIcon("comm_rxtx.png");

    iconErrEmergency = UIUtil.createImageIcon("err_emergency.png");
    iconErrConnection = UIUtil.createImageIcon("err_comm.png");
    iconErrSettings = UIUtil.createImageIcon("err_settings.png");
  }

  public void addBluePrint(BluePrintRenderer bp) {
    bp.setBluePrintPanel(this);
    bp.renderAll();
    blueprints.add(bp);
    repaint();
  }

  public void removeBluePrint(BluePrintRenderer bp) {
    blueprints.remove(bp);
    repaint();
  }
  
  public void markCommand(CNCCommand c) {
    for (BluePrintRenderer bp : blueprints) {
      bp.setCurrentCommand(c);
    }

    repaint();
  }

  int getX(int x, int y, int z, int w, int h) {
    if (drawMode == DRAW_MODE_ORTO) {
      return (int) (Math.round(1.5 * (double) (x + y))); 
    } else {
      return x;
    }
  }

  int getY(int x, int y, int z, int w, int h) {
    if (drawMode == DRAW_MODE_ORTO) {
      return h - z - y;  
    } else {
      return h-y;
    }
  }
  
  void renderAll() {
    for (BluePrintRenderer bp : blueprints) {
      bp.renderAll();
    }
  }

  public void paint(Graphics g) {
//    dx = -cncPos.x + bpw/mag/2;
//    dy = -cncPos.y + bph/mag/2;
//    for (BluePrintRenderer bp : blueprints) {
//      bp.setOffset(this.dx, this.dy);
//    }

    int w = getWidth();
    int h = getHeight();
    g.setColor(Color.black);
    g.fillRect(0, 0, w, h);
    
    int gdx = (int)(dx*mag);
    int gdy = (int)(dy*mag);
    
    // grids
    if (gridMinDiv*mag > 8) {
      g.setColor(colorGridMin);
      for (double gx = ((dx%gridMinDiv)*mag); gx < bpw; gx += (gridMinDiv*mag)) {
        g.drawLine(
            getX((int)gx, -10000, 0, bpw, bph), 
            getY((int)gx, -10000, 0, bpw, bph), 
            getX((int)gx, 10000, 0, bpw, bph), 
            getY((int)gx, 10000, 0, bpw, bph)); 
      }
      for (double gy = ((dy%gridMinDiv)*mag); gy < bph; gy += (gridMinDiv*mag)) {
        g.drawLine(
            getX(-10000, (int)gy, 0, bpw, bph), 
            getY(-10000, (int)gy, 0, bpw, bph), 
            getX(10000, (int)gy, 0, bpw, bph), 
            getY(10000, (int)gy, 0, bpw, bph));
      }
    }
    
    if (gridMaxDiv*mag > 8) {
      g.setColor(colorGridMax);
      for (double gx = ((dx%gridMaxDiv)*mag); gx < bpw; gx += (gridMaxDiv*mag)) {
        g.drawLine(
            getX((int)gx, -10000, 0, bpw, bph), 
            getY((int)gx, -10000, 0, bpw, bph), 
            getX((int)gx, 10000, 0, bpw, bph), 
            getY((int)gx, 10000, 0, bpw, bph)); 
      }
      for (double gy = ((dy%gridMaxDiv)*mag); gy < bph; gy += (gridMaxDiv*mag)) {
        g.drawLine(
            getX(-10000, (int)gy, 0, bpw, bph), 
            getY(-10000, (int)gy, 0, bpw, bph), 
            getX(10000, (int)gy, 0, bpw, bph), 
            getY(10000, (int)gy, 0, bpw, bph));
      }
    }
    
    g.setColor(colorOrigo);
    g.drawLine(
        getX(gdx, -10000, 0, bpw, bph), 
        getY(gdx, -10000, 0, bpw, bph), 
        getX(gdx, 10000, 0, bpw, bph), 
        getY(gdx, 10000, 0, bpw, bph)); 
    g.drawLine(
        getX(-10000, gdy, 0, bpw, bph), 
        getY(-10000, gdy, 0, bpw, bph), 
        getX(10000, gdy, 0, bpw, bph), 
        getY(10000, gdy, 0, bpw, bph));
    
    // blueprints
    for (BluePrintRenderer bp : blueprints) {
      bp.paint(g);
    }
    
    g.setFont(font);

    if (Controller.isConnected()) {
      // cnc cursor
      g.setColor(cncPos.z < 0 ? Color.red : Color.green);
      int xp = (int) Math.round((cncPos.x + dx) * mag);
      int yp = (int) Math.round((cncPos.y + dy) * mag);
      int zp = (int) Math.round(cncPos.z * mag);
      int gxp = getX(xp, yp, zp, bpw, bph);
      int gyp = getY(xp, yp, zp, bpw, bph);
      g.drawLine(gxp - 5, gyp, gxp + 5, gyp);
      g.drawLine(gxp, gyp - 5, gxp, gyp + 5);
  
      // cnc info
      g.setColor(Color.yellow);
      int x = 4;
      int y = 4;
      boolean cncEnabled = (status & CNCProtocol.CNC_STATUS_CONTROL_ENABLED) != 0;
      boolean latchFull = (status & CNCProtocol.CNC_STATUS_LATCH_FULL) != 0;
  
      boolean errEmergency = (status & CNCProtocol.CNC_STATUS_ERR_EMERGENCY) != 0;
      boolean errSettings = (status & CNCProtocol.CNC_STATUS_ERR_SETTINGS_CORRUPT) != 0;
      boolean errCommLost = (status & CNCProtocol.CNC_STATUS_ERR_COMM_LOST) != 0;
      int mov;
      if (((status & CNCProtocol.CNC_STATUS_MOVEMENT_PAUSE) != 0) && ((status & CNCProtocol.CNC_STATUS_MOVEMENT_STILL) == 0)) {
        mov = 1; // pause
      } else if (((status & CNCProtocol.CNC_STATUS_MOVEMENT_STILL) != 0)) {
        mov = 0; // still;
      } else if (((status & CNCProtocol.CNC_STATUS_MOVEMENT_RAPID) != 0)) {
        mov = 3; // rapid
      } else {
        mov = 2; // linear
      }
      int pipe;
      if (((status & CNCProtocol.CNC_STATUS_PIPE_ACTIVE) == 0)) {
        pipe = 0; // inactive
      } else if (((status & CNCProtocol.CNC_STATUS_PIPE_EMPTY) != 0)) {
        pipe = 1; // active, empty
      } else if (((status & CNCProtocol.CNC_STATUS_PIPE_FULL) != 0)) {
        pipe = 3; // active, full
      } else {
        pipe = 2; // active, non-full
      }
  
      g.drawImage(cncEnabled ? iconCncEnabled.getImage() : iconCncDisabled.getImage(), x, y, this);
      x += 32;
      if (mov == 0) {
        g.drawImage(iconMovStill.getImage(), x, y, this);
      } else if (mov == 1) {
        g.drawImage(iconMovPause.getImage(), x, y, this);
      } else if (mov == 2) {
        g.drawImage(iconMovLin.getImage(), x, y, this);
      } else if (mov == 3) {
        g.drawImage(iconMovRap.getImage(), x, y, this);
      }
      x += 32;
      if (pipe == 0) {
        g.drawImage(iconPipeDisabled.getImage(), x, y, this);
      } else if (pipe == 1) {
        g.drawImage(iconPipeEnabled.getImage(), x, y, this);
      } else if (pipe == 2) {
        g.drawImage(iconPipePopulated.getImage(), x, y, this);
      } else if (pipe == 3) {
        g.drawImage(iconPipeFull.getImage(), x, y, this);
      }
      x += 32;
      g.drawImage(latchFull ? iconLatchBusy.getImage() : iconLatchFree.getImage(), x, y, this);
      x += 32+4;
      if (!rx && !tx) {
        g.drawImage(iconRxTxConn.getImage(), x, y, this);
      } else if (rx && tx) {
        g.drawImage(iconRxTxRxTx.getImage(), x, y, this);
      } else if (rx) {
        g.drawImage(iconRxTxRx.getImage(), x, y, this);
      } else if (tx) {
        g.drawImage(iconRxTxTx.getImage(), x, y, this);
      }
      
      x = w-32-4;
      if (errCommLost) {
        g.drawImage(iconErrConnection.getImage(), x, y, this);
      }
      x -= 32;
      if (errSettings) {
        g.drawImage(iconErrSettings.getImage(), x, y, this);
      }
      x -= 32;
      if (errEmergency) {
        g.drawImage(iconErrEmergency.getImage(), x, y, this);
      }
  
      y += 32 + 4;
      x = 4;
      g.setColor(Color.cyan);
      g.drawRect(x+3, y, 32*4-6, 16);
      if (sentPercentage > 0) {
        g.setColor(colorSentGlow);
        g.fillRect(x+6-1, y+3-1, (int)((32*4-6-4) * sentPercentage)+2, 16 - 5+2);
        g.fillRoundRect(x+6-1, y+3-1, (int)((32*4-6-4) * sentPercentage)+2, 16 - 5+2, 9, 9);
        g.setColor(colorSent);
        g.fillRect(x+6, y+3, (int)((32*4-6-4) * sentPercentage), 16 - 5);
      }
      if (exePercentage > 0) {
        g.setColor(colorExeGlow);
        g.fillRect(x+6-1, y+3-1, (int)((32*4-6-4) * exePercentage)+2, 16 - 5+2);
        g.fillRoundRect(x+6-1, y+3-1, (int)((32*4-6-4) * exePercentage)+2, 16 - 5+2, 9, 9);
        g.setColor(colorExe);
        g.fillRect(x+6, y+3, (int)((32*4-6-4) * exePercentage), 16 - 5);
      }
    }
    
    // mouse coords
    int yy = h - 8;
    int xx = 8;
    g.setColor(Color.green);
    g.drawString("ptr:" + (float)((int)(mx*1000))/1000, xx, yy);
    xx += 90;
    g.drawString("" + (float)((int)(my*1000))/1000, xx, yy);

    // mag
    xx += 90;
    g.drawString("X" + (float)((int)(mag*1000))/1000, xx, yy);
    
    yy-=12;
    
    // cnc coords
    if (Controller.isConnected()) {
      xx = 8;
      g.setColor(Color.green);
      g.drawString("cnc:" + (float)((int)(cncPos.x*1000))/1000, xx, yy);
      xx += 90;
      g.drawString("" + (float)((int)(cncPos.y*1000))/1000, xx, yy);
      xx += 90;
      g.drawString("" + (float)((int)(cncPos.z*1000))/1000, xx, yy);
    }
  }
  
  
  // CNCListener

  public void sr(int sr) {
    this.status = sr;
    repaint();
  }

  public void pos(double x, double y, double z) {
    cncPos = new Point(x, y, z);
    //Log.println(pos.toString());
    repaint();
  }

  public void curCommand(int id, double exePercentage, double sentPercentage, CNCCommand c) {
    this.exePercentage = exePercentage;
    this.sentPercentage = sentPercentage;
    markCommand(c);
  }
  
  // RxTxListener

  @Override
  public void rxtx(boolean rx, boolean tx) {
    this.rx = rx;
    this.tx = tx;
    repaint();
  }

  // JPanel overrides

  @Override
  public Dimension getMaximumSize() {
    return new Dimension(bpw, bph);
  }

  @Override
  public Dimension getMinimumSize() {
    return new Dimension(bpw, bph);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(bpw, bph);
  }

  // Mouse callbacks

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {
    int dw = e.getWheelRotation();
    int sign = (int)Math.signum(dw);
    double dmag = 0.1;
    if (mag >= 1000 && sign > 0 || mag > 1000 && sign < 0) {
      dmag = 100;
    } else if (mag >= 100 && sign > 0 || mag > 100 && sign < 0) {
      dmag = 10;
    } else if (mag >= 50 && sign > 0 || mag > 50 && sign < 0) {
      dmag = 5;
    } else if (mag >= 10 && sign > 0 || mag > 10 && sign < 0) {
      dmag = 2;
    } else if (mag >= 5 && sign > 0 || mag > 5 && sign < 0) {
      dmag = 0.5;
    } else if (mag > 1) {
      dmag = 0.1;
    } 
    
    double mx = (double)e.getX();
    mx = mx/mag - dx;
    double my = bph - (double)e.getY();
    my = my/mag - dy;

    if (mag - dmag < 1 && sign < 0) {
      dmag = 0;
      dw = 1;
    }
  
    dmag *= sign;
    mag += dmag;
    mag = (Math.round(mag * 10))/10.0;

    double mx2 = (double)e.getX();
    mx2 = mx2/mag - dx;
    double my2 = bph - (double)e.getY();
    my2 = my2/mag - dy;

    dx += mx2-mx;
    dy += my2-my;

    for (BluePrintRenderer bp : blueprints) {
      bp.setMagnificationAndOffset(mag, dx, dy);
    }

    repaint();
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    int dx = e.getX() - xdrag;
    int dy = e.getY() - ydrag;
    this.dx += (double) dx / mag;
    this.dy -= (double) dy / mag;
    xdrag = e.getX();
    ydrag = e.getY();
    for (BluePrintRenderer bp : blueprints) {
      bp.setOffset(this.dx, this.dy);
    }

    repaint();
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    mx = (double)e.getX()/mag - dx;
    my = (bph - (double)e.getY()) / mag - dy;
    repaint();
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    // TODO elsewhere
    if (drawMode == DRAW_MODE_ORTO) {
      drawMode = DRAW_MODE_TOP;
    } else {
      drawMode = DRAW_MODE_ORTO;
    }
    for (BluePrintRenderer bp : blueprints) {
      bp.setMagnificationAndOffset(mag, dx, dy);
    }
    repaint();
  }

  @Override
  public void mouseEntered(MouseEvent e) {
  }

  @Override
  public void mouseExited(MouseEvent e) {
  }

  int xdrag, ydrag;

  @Override
  public void mousePressed(MouseEvent e) {
    xdrag = e.getX();
    ydrag = e.getY();
  }

  @Override
  public void mouseReleased(MouseEvent e) {
  }

  public int getBluePrintWidth() {
    return bpw;
  }

  public int getBluePrintHeight() {
    return bph;
  }

  public double getOffsetX() {
    return dx;
  }

  public double getOffsetY() {
    return dy;
  }

  public double getMagnification() {
    return mag;
  }

}
