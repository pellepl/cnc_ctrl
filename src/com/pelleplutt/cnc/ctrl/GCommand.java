package com.pelleplutt.cnc.ctrl;

import com.pelleplutt.cnc.types.CoolantType;
import com.pelleplutt.cnc.types.OrientationType;
import com.pelleplutt.cnc.types.Point;
import com.pelleplutt.cnc.types.PositioningType;
import com.pelleplutt.cnc.types.StopType;
import com.pelleplutt.cnc.types.UnitType;

/**
 * GCommand is a intermediate representation of a gcode set of registers,
 * used for generating accurate CNCCommands
 * @author petera
 */
public abstract class GCommand {
  public static class Movement extends GCommand {
    public Point p;
    public Movement(Point p) {
      this.p = p;
    }
  }

  public static class ArcRadius extends GCommand {
    public Point p;
    public OrientationType orientation;
    public double radius;
    public ArcRadius(Point p, OrientationType o, double r) {
      this.p = p;
      this.orientation = o;
      this.radius = r;
    }
  }

  public static class ArcCentre extends GCommand {
    public Point p;
    public OrientationType orientation;
    public Point centre;
    public ArcCentre(Point p, OrientationType o, Point c) {
      this.p = p;
      this.orientation = o;
      this.centre = c;
    }
  }

  public static class MovementRapid extends Movement {
    public MovementRapid(Point p) {
      super(p);
    }
  }

  public static class FeedRate extends GCommand {
    public FeedRate(double feed) {
      rate = feed;
    }

    public double rate;
  }

  public static class EndOfProgram extends GCommand {
  }

  public static class Spindle extends GCommand {
    public Spindle(boolean on, OrientationType o) {
      this.on = on;
      this.orientation = o;
    }
    public Spindle(boolean on) {
      this.on = on;
    }
    public boolean on;
    public OrientationType orientation;
  }

  public static class Coolant extends GCommand {
    public Coolant(boolean on, CoolantType c) {
      this.on = on;
      this.type = c;
    }
    public Coolant(boolean on) {
      this.on = on;
    }
    public boolean on;
    public CoolantType type;
  }

  public static class Dwell extends GCommand {
    public Dwell(double r) {
      milliseconds = r;
    }

    public double milliseconds;
  }

  public static class Unit extends GCommand {
    public Unit(UnitType u) {
      type = u;
    }

    public UnitType type;
  }

  public static class Positioning extends GCommand {
    public Positioning(PositioningType p) {
      type = p;
    }

    public PositioningType type;
  }

  public static class Stop extends GCommand {
    public Stop(StopType type) {
      this.type = type;
    }

    public StopType type;
  }

  public static class Tool extends GCommand {
    public Tool(int index) {
      this.index = index;
    }
    int index;
  }

  public static class Label extends GCommand {
    public Label(double l) {
      label = l;
    }

    public double label;
  }

  public static class Comment extends GCommand {
    public Comment(String comment) {
      this.comment = comment;
    }
    public String comment;
  }

  public static class Program extends GCommand {
    public Program(String program) {
      this.program = program;
    }
    public String program;
  }
}
