package com.pelleplutt.cnc.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import com.pelleplutt.cnc.ctrl.CNCCommand;
import com.pelleplutt.cnc.ctrl.GCommand;
import com.pelleplutt.cnc.ctrl.GVirtualCNC;
import com.pelleplutt.cnc.types.Point;
import com.pelleplutt.cnc.types.UnitType;

public class BluePrintRenderer extends JPanel implements GVirtualCNC.RenderListener {
  private static final long serialVersionUID = -411533208717876871L;
  
  static final Color COLOR_LINEAR = new Color(0,128,128, 255);
  static final Color COLOR_RAPID = new Color(255,0,0, 128);
  static final Color COLOR_DRILL = new Color(255,255,128, 128);
  static final Color COLOR_CURRENT_LINE = new Color(255,255,255);
  static final Color COLOR_CURRENT_PATH = new Color(0,255,255);
  
  static final Stroke STROKE_NORMAL = new BasicStroke();
  static final Stroke STROKE_SELECTED = 
      new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
  
  BufferedImage imgAll;
  BufferedImage imgPath;
  
  BluePrintPanel bpp;
  
  List<Graphic> allMoves = new ArrayList<Graphic>();
  double mag = 5;
  double dx = 0;
  double dy = 0;
  
  GCommand currentGCode = null;
  Map<GCommand, List<Graphic>> gfxForGCode = new HashMap<GCommand, List<Graphic>>();
  List<Graphic> currentGfxForGCode = null;
  Map<GCommand, List<Graphic>>  pathForGCode = new HashMap<GCommand, List<Graphic>>();
  List<Graphic> currentPathForGCode = null;
  List<Graphic> currentPath = null;

  public BluePrintRenderer(int w, int h) {
    super();
    imgAll = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    imgPath = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
  }
  
  
  static final int[] NULLS = new int[1024*1024];
  
  void setBluePrintPanel(BluePrintPanel bpp) {
    this.bpp = bpp;
  }
  
  void clearImage(BufferedImage i) {
    int[] raster = ((DataBufferInt) i.getRaster().getDataBuffer()).getData();
    int ix = 0;
    while (ix < raster.length) {
      int len = Math.min(raster.length - ix, NULLS.length);
      System.arraycopy(NULLS, 0, raster, ix, len);
      ix += len;
    }
  }

  public Graphic addLine(int flags, Point p1, Point p2) {
    Graphic line = new Line(flags, p1, p2);
    allMoves.add(line);
    return line;
  }

  public Graphic addCircle(int flags, Point p1, double r) {
    Graphic c = new Circle(flags, p1, r);
    allMoves.add(c);
    return c;
  }

  public void renderAll() {
    renderBlueprint();
    renderPath();
  }

  public void renderBlueprint() {
    clearImage(imgAll);
    render(allMoves, (Graphics2D) imgAll.getGraphics(), imgAll.getWidth(), imgAll.getHeight(), 
        STROKE_NORMAL, COLOR_LINEAR, COLOR_RAPID);
  }

  public void renderPath() {
    clearImage(imgPath);
    if (currentPathForGCode != null) {
      render(currentPathForGCode, (Graphics2D) imgPath.getGraphics(), imgPath.getWidth(), imgPath.getHeight(),
          STROKE_NORMAL, COLOR_CURRENT_PATH, COLOR_CURRENT_PATH);
    }
  }
  

  public void setCurrentCommand(CNCCommand c) {
    currentGCode = c == null ? null : c.getGCommand();
    if (currentGCode != null) {
      currentGfxForGCode = gfxForGCode.get(currentGCode);
      currentPathForGCode = pathForGCode.get(currentGCode);
    } else {
      currentGfxForGCode = null;
      currentPathForGCode = null;
    }
    renderPath();
  }
  
  void render(Graphic gfx, Graphics2D g, int w, int h, Stroke s, Color clin, Color crap) {
    g.setStroke(s);
    if (gfx instanceof Line) {
      g.setColor(gfx.flags == 0 ? clin : crap);
      Line line = (Line)gfx;
      int x1 = (int) Math.round((line.o.x + dx) * mag);
      int y1 = (int) Math.round((line.o.y + dy) * mag);
      int z1 = (int) Math.round(line.o.z * mag);
      int x2 = (int) Math.round((line.d.x + dx) * mag);
      int y2 = (int) Math.round((line.d.y + dy) * mag);
      int z2 = (int) Math.round(line.d.z * mag);
      g.drawLine(bpp.getX(x1, y1, z1, w, h), bpp.getY(x1, y1, z1, w, h),
          bpp.getX(x2, y2, z2, w, h), bpp.getY(x2, y2, z2, w, h));
    } else if (gfx instanceof Circle) {
      g.setColor(COLOR_DRILL);
      Circle c = (Circle)gfx;
      int x1 = (int) Math.round((c.o.x + dx) * mag);
      int y1 = (int) Math.round((c.o.y + dy) * mag);
      int z1 = (int) Math.round(c.o.z * mag);
      int r = (int)(c.r * mag);
      g.drawArc(bpp.getX(x1 - r/2, y1 + r/2, z1, w, h), 
          bpp.getY(x1 + r/2, y1 + r/2, z1, w, h),
          r,r,0,360);
    }
  }

  void render(List<Graphic> l, Graphics2D g, int w, int h, Stroke s, Color clin, Color crap) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON);

    for (Graphic gfx : l) {
      render(gfx, g, w, h, s, clin, crap);
    }
  }
  
  // RenderListener (GVirtualCNC)
  
  @Override
  public void move(GCommand g, boolean rapid, Point p1, Point p2,
      Point cncDelta, Point feeds) {
    // get list of lines per gcode
    List<Graphic> gcodeGfx = gfxForGCode.get(g);
    if (gcodeGfx == null) {
      gcodeGfx = new ArrayList<Graphic>();
      gfxForGCode.put(g, gcodeGfx);
    }
    // get the graphics for this move
    Graphic gfx;
    if (p1.x == p2.x && p1.y == p2.y && p1.z > 0 && p2.z < 0 &&  !rapid) {
      gfx = addCircle(0, p1, 0.5);
    } else {
      gfx = addLine(rapid ? 1 : 0, p1, p2);
    }
    // add line to list of lines per gcode
    gcodeGfx.add(gfx);
    
    // add line to path
    if (rapid) {
      currentPath = null;
    } else {
      if (currentPath == null) {
        currentPath = new ArrayList<Graphic>();
      }
      currentPath.add(gfx);
      pathForGCode.put(g, currentPath);
    }
  }

  // JPanel

  @Override
  public void paint(Graphics gg) {
    int w = imgAll.getWidth();
    int h = imgAll.getHeight();
    
    if (imgAll != null)
      gg.drawImage(imgAll, 0, 0, this);
    if (imgPath != null)
      gg.drawImage(imgPath, 0, 0, this);

    Graphics2D g = (Graphics2D) gg;
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON);

    if (currentGfxForGCode != null) {
      List<Graphic> l = currentGfxForGCode;
      for (Graphic gfx : l) {
        render(gfx, g, w, h, STROKE_SELECTED, COLOR_CURRENT_LINE, COLOR_CURRENT_LINE);
      }
      g.setStroke(STROKE_NORMAL);
    }
  }

  // JPanel overrides

  @Override
  public Dimension getMaximumSize() {
    return new Dimension(imgAll.getWidth(), imgAll.getHeight());
  }

  @Override
  public Dimension getMinimumSize() {
    return new Dimension(imgAll.getWidth(), imgAll.getHeight());
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(imgAll.getWidth(), imgAll.getHeight());
  }

  public void setMagnification(double newMag) {
    this.mag = newMag;
    renderAll();
  }

  
  public void setOffset(double dx, double dy) {
    if (this.dx == dx && this.dy == dy) return;
    this.dx = dx;
    this.dy = dy;
    renderAll();
  }

  public void setMagnificationAndOffset(double newMag, double dx, double dy) {
    if (this.mag == mag && this.dx == dx && this.dy == dy) return;
    this.mag = newMag;
    this.dx = dx;
    this.dy = dy;
    renderAll();
  }

  
  class Graphic {
    Point o;
    int flags;

    public Graphic(int flags, Point o) {
      this.flags = flags;
      this.o = o.toUnit(UnitType.MILLIMETERS);
    }
  }
  
  class Line extends Graphic {
    Point d;
    public Line(int flags, Point o, Point d) {
      super(flags, o);
      this.d = d.toUnit(UnitType.MILLIMETERS);
    }
  }

  class Circle extends Graphic {
    double r;
    public Circle(int flags, Point o, double r) {
      super(flags, o);
      this.r = r;
    }
  }
}
