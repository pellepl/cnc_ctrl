package com.pelleplutt.cnc.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

import com.pelleplutt.cnc.types.Point;

public class UserBluePrintRenderer extends BluePrintRenderer {
  static final Color COLOR_USER = new Color(255,255,64, 128);

  Line userLine = null;
  
  public UserBluePrintRenderer(int w, int h) {
    super(w, h);
    imgPath = null;
  }
  
  public void clearUser() {
    userLine = null;
    renderAll();
  }
  
  public void setUser(Point p1, Point p2) {
    userLine = new Line(0, p1, p2);
    renderAll();
  }
  
  public Line getUser()  {
    return userLine;
  }

  @Override
  public void renderPath() {
  }

  @Override
  public void renderBlueprint() {
    clearImage(imgAll);
    if (userLine != null) {
      render(userLine, (Graphics2D) imgAll.getGraphics(), imgAll.getWidth(), imgAll.getHeight(), 
          STROKE_NORMAL, COLOR_USER, COLOR_USER);
    }
  }
  
  public void paint(Graphics gg) {
    super.paint(gg);
  }
  
  
  private static final long serialVersionUID = -3257202505095110107L;
}
