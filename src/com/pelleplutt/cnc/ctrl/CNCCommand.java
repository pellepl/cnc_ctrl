package com.pelleplutt.cnc.ctrl;

import com.pelleplutt.cnc.types.CoolantType;
import com.pelleplutt.cnc.types.OrientationType;
import com.pelleplutt.cnc.types.Point;
import com.pelleplutt.cnc.types.StopType;

public interface CNCCommand {
  public GCommand getGCommand();
  
	static public class Move implements CNCCommand {
    public Point origin;
    public Point stepsTranslation;
		public Point feedStepsPerSec;
		public boolean rapid;
		GCommand g;
    public GCommand getGCommand() {
      return g;
    }
		public Move(GCommand g, Point origin, Point stepsTranslation, Point feedStepsPerSec, boolean rapid) {
		  this.g = g;
		  this.origin = origin;
			this.stepsTranslation = stepsTranslation;
			this.feedStepsPerSec = feedStepsPerSec;
			this.rapid = rapid;
		}
		
		public String toString() {
		  return "Move<" + stepsTranslation + "," + feedStepsPerSec + "," + rapid + ">";
		}
	}
	
	static public class Coolant extends GCommand.Coolant implements CNCCommand {
    GCommand g;
    public GCommand getGCommand() {
      return g;
    }
		public Coolant(boolean on, CoolantType c) {
			super(on, c);
		}
		public Coolant(com.pelleplutt.cnc.ctrl.GCommand.Coolant c) {
			super(c.on, c.type);
      g = c;
		}
	}

	static public class Dwell extends GCommand.Dwell implements CNCCommand {
    GCommand g;
    public GCommand getGCommand() {
      return g;
    }

    public Dwell(long ms) {
			super(ms);
		}

		public Dwell(com.pelleplutt.cnc.ctrl.GCommand.Dwell d) {
			super(d.milliseconds);
			g = d;
		}
	}
	
	static public class Spindle extends GCommand.Spindle implements CNCCommand {
    GCommand g;
    public GCommand getGCommand() {
      return g;
    }
		public Spindle(boolean on, OrientationType o) {
			super(on, o);
		}

		public Spindle(com.pelleplutt.cnc.ctrl.GCommand.Spindle s) {
			super(s.on, s.orientation);
			g = s;
		}
	}
	
	static public class Stop extends GCommand.Stop implements CNCCommand {
    GCommand g;
    public GCommand getGCommand() {
      return g;
    }
		public Stop(StopType type) {
			super(type);
		}

		public Stop(com.pelleplutt.cnc.ctrl.GCommand.Stop s) {
			super(s.type);
			g = s;
		}
	}
	
  static public class Tool extends GCommand.Tool implements CNCCommand {
    GCommand g;
    public GCommand getGCommand() {
      return g;
    }
    public Tool(int t) {
      super(t);
    }

    public Tool(com.pelleplutt.cnc.ctrl.GCommand.Tool t) {
      super(t.index);
      g = t;
    }
  }

  static public class EndOfProgram extends GCommand.EndOfProgram implements CNCCommand {
    GCommand g;
    public GCommand getGCommand() {
      return g;
    }
    public EndOfProgram(GCommand.EndOfProgram e) {
      super();
      g = e;
    }
  }
}
