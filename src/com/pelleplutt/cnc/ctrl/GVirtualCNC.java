package com.pelleplutt.cnc.ctrl;

import java.lang.reflect.Field;

import com.pelleplutt.cnc.ctrl.GCommand.Comment;
import com.pelleplutt.cnc.ctrl.GCommand.Coolant;
import com.pelleplutt.cnc.ctrl.GCommand.Dwell;
import com.pelleplutt.cnc.ctrl.GCommand.EndOfProgram;
import com.pelleplutt.cnc.ctrl.GCommand.Label;
import com.pelleplutt.cnc.ctrl.GCommand.Program;
import com.pelleplutt.cnc.ctrl.GCommand.Spindle;
import com.pelleplutt.cnc.ctrl.GCommand.Stop;
import com.pelleplutt.cnc.ctrl.GCommand.Tool;
import com.pelleplutt.cnc.io.CNCProtocol;
import com.pelleplutt.cnc.types.OrientationType;
import com.pelleplutt.cnc.types.Point;
import com.pelleplutt.cnc.types.PositioningType;
import com.pelleplutt.cnc.types.UnitType;

/**
 * Executes GCommands on a virtual CNC machine to extract
 * actual step calculations and feeds. Translates GCommands
 * into CNCCommands. Handles error minimization when converting
 * from units to steps.
 * 
 * @author petera
 */
public class GVirtualCNC extends GCommandDispatcher implements PropertyHolder {
  
  public static final String CONFIG_MAX_FEED = "maxFeedsSPS";
  public static final String CONFIG_SPU = "stepsPerUnit";
  public static final String CONFIG_ABS_MAX_FEED = "absoluteMaxFrequency";
  public static final String CONFIG_RAPID_D = "rapidDelta";
  public static final String CONFIG_INVERT = "axisInversion";
  

	// HW machine local configuration
  // max feed: unit per minute
  public Point maxFeedsUPM;
  // max feed: steps per second
  public Point maxFeedsSPS;
	// nbr of steps per unit
	public Point stepsPerUnit;
	// axis multiplier
	public Point axisInversion;
	
	// HW machine remote configuration
	// max frequency to stepper motor as integers
  public Point absoluteMaxFrequency;
  // during rapid motion, delta frequency in Qx.y format
  public Point rapidDelta;

	
	// arc delta
	Point arcDelta;

	// GCode state
	// positioning
	PositioningType positioning;
	// current feed
	double curFeed;
	// unit
	UnitType unit;

	// machine state
	// current point in units
	Point exactPoint;
	// current point in steps
	Point cncPoint;
	
	RenderListener renderListener;
	Emitter emitter;
	
	public GVirtualCNC() {
	}

	// configuration

	public void reset() {
		stepsPerUnit = new Point(UnitType.MILLIMETERS, 0, 0, 0);
		maxFeedsUPM = new Point(UnitType.MILLIMETERS, 0, 0, 0);
		axisInversion = new Point(UnitType.STEPS, 1, 1, 1);
    absoluteMaxFrequency = new Point(UnitType.MILLIMETERS, 0,0,0);
    rapidDelta = new Point(UnitType.MILLIMETERS, 0,0,0);
		
		positioning = PositioningType.ABSOLUTE;
		exactPoint = new Point(UnitType.MILLIMETERS, 0, 0, 0);
		cncPoint = new Point(UnitType.STEPS, 0, 0, 0);
		arcDelta = new Point(UnitType.MILLIMETERS, 0.25, 0.25, 0.25);
		curFeed = 0;
		unit = UnitType.MILLIMETERS;
	}
	
  public void setOrigin() {
    exactPoint = new Point(exactPoint.unit, 0, 0, 0);
    cncPoint = new Point(UnitType.STEPS, 0, 0, 0);
  }

	public void setStepsPerUnit(Point stepsPerUnit) {
		this.stepsPerUnit = stepsPerUnit.toUnit(unit);
	}

	public void setMaxFeeds(Point maxFeeds) {
		this.maxFeedsUPM = maxFeeds.toUnit(unit);
		this.maxFeedsSPS = unitToStep(maxFeedsUPM);
		this.maxFeedsSPS.divideI(60);
	}

	public void setMaxFeedsAsStepsPerSec(Point maxFeedsSpS) {
		Point maxFeed = new Point(stepsPerUnit.unit, (60 * maxFeedsSpS.x)
				/ stepsPerUnit.x, (60 * maxFeedsSpS.y) / stepsPerUnit.y,
				(60 * maxFeedsSpS.z) / stepsPerUnit.z);
		setMaxFeeds(maxFeed);
	}
	
	public void setRapidFrequency(Point rapidFreq) {
	  this.absoluteMaxFrequency = rapidFreq.toUnit(unit);
	}
	
	public void setRapidDelta(Point rapidDelta) {
	  this.rapidDelta = rapidDelta;
	}
	
	public void setInversion(Point axes) {
	  this.axisInversion = axes;
	}

	public void setHardcodedConfiguration() {
		unit = UnitType.MILLIMETERS;
		Point spm = new Point(UnitType.MILLIMETERS, 400, 400, 400);
		setStepsPerUnit(spm);
		Point maxFeed = new Point(UnitType.MILLIMETERS, 300, 300, 300);
		setMaxFeeds(maxFeed);
		
    absoluteMaxFrequency = new Point(UnitType.MILLIMETERS, 
        480*spm.x / 60, 480*spm.y / 60, 480*spm.z / 60);
    rapidDelta = new Point(UnitType.MILLIMETERS, 
        ((1<<CNCProtocol.CNC_FP_DECIMALS)/2),
        ((1<<CNCProtocol.CNC_FP_DECIMALS)/2),
        ((1<<CNCProtocol.CNC_FP_DECIMALS)/2));
	}

	/**
	 * Sets a callback which is invoked each time current position
	 * is moved.
	 * @param renderListener
	 */
	public void setRenderListener(RenderListener renderListener) {
	  this.renderListener = renderListener;
	}
	
	public void setEmitter(Emitter e) {
		emitter = e;
	}

	// command->machine state

	public void doSetFeed(GCommand.FeedRate f) {
		curFeed = f.rate;
	}

	public void doSetPositioning(GCommand.Positioning p) {
		positioning = p.type;
	}
	
	Point calcFeed(Point src, Point dst, double feed, boolean rapid) {
	  // TODO check max feedrates
	  Point delta;
	  if (rapid) {
      delta = maxFeedsUPM.toUnit(unit);
    } else {
      if (src.unit != dst.unit) {
        throw new MachineError("feed calculation with different units");
      }
      double dist = src.distance(dst);
      delta = src.diff(dst);
      delta.x = (Math.abs(delta.x) * feed) / dist;
      delta.y = (Math.abs(delta.y) * feed) / dist;
      delta.z = (Math.abs(delta.z) * feed) / dist;
    }
    return delta;
	}

	void move(GCommand g, Point movement, boolean rapid) {
		Point src = exactPoint.toUnit(UnitType.MILLIMETERS);
		switch (positioning) {
		case ABSOLUTE:
			exactPoint = movement.toUnit(unit);
			break;
		case RELATIVE:
			exactPoint.translateI(movement.toUnit(unit));
			break;
		}
		
		// keep exact point updated to even out steps/unit error
		Point cncDeltaUnits = stepToUnit(cncPoint).diff(movement);
		Point cncDelta = unitToStep(cncDeltaUnits);
		cncPoint.translateI(cncDelta);
		
		Point feedUnitPerMinute = calcFeed(src, exactPoint, curFeed, rapid);
		Point feedStepPerSec = unitToStep(feedUnitPerMinute);
		feedStepPerSec.divideI(60);
		Point dst = exactPoint.toUnit(UnitType.MILLIMETERS);
		doCncMove(g, unitToStep(src), rapid, cncDelta, feedStepPerSec);
		if (renderListener != null) {
		  renderListener.move(g, rapid, src, dst, cncDelta, feedUnitPerMinute);
		}
	}

  public void doMove(GCommand.Movement m) {
		boolean rapid = m instanceof GCommand.MovementRapid;
		move(m, m.p, rapid);
	}

	public void arcDoMove(GCommand g, Point src, Point dst, Point mid, double radius,
			OrientationType o) {
		// calc delta radians
		double drad = arcDelta.x / radius; // d = 2nr*a/2n = r*a => a = d / r
    double angStart = 2*Math.PI - Math.atan2((mid.y - src.y),(mid.x - src.x));
    double angEnd = 2*Math.PI - Math.atan2((mid.y - dst.y),(mid.x - dst.x));
		if (o == OrientationType.CW) {
			if (angEnd < angStart) {
				angEnd += Math.PI * 2;
			}

			do {
				angStart += drad;
				move(g, new Point(unit, mid.x - radius * Math.cos(angStart),
						mid.y + radius * Math.sin(angStart), mid.z), false);
			} while (angStart < angEnd - drad);
		}
		if (o == OrientationType.CCW) {
			if (angEnd > angStart) {
				angStart += Math.PI * 2;
			}
			do {
				angStart -= drad;
				move(g, new Point(unit, mid.x - radius * Math.cos(angStart),
						mid.y + radius * Math.sin(angStart), mid.z), false);
			} while (angStart > angEnd + drad);
		}
		move(g, dst, false);
	}

	public void doArcC(GCommand.ArcCentre a) {
		Point dst = a.p.toUnit(unit);
		Point centre = a.centre.toUnit(unit);
		double r1 = exactPoint.distance(centre);
		double r2 = dst.distance(centre);
		double d = exactPoint.distance(dst);

		double r1c = (double) ((int) Math.round(r1 * 10000)) / 10000;
		double r2c = (double) ((int) Math.round(r2 * 10000)) / 10000;
		double dc = (double) ((int) Math.round(d * 10000)) / 10000;

		if (r1c != r2c)
			throw new MachineError("arc start and end radii mismatch, " + r1c
					+ " != " + r2c);
		if (dc > r1c * 2)
			throw new MachineError(
					"arc start and end distance bigger than diameter, " + dc
							+ " > " + r1c * 2);
		if (exactPoint.z != dst.z)
			throw new MachineError("z arcs not supported");
		if (dc == 0)
			return;

		arcDoMove(a, exactPoint, dst, centre, r1, a.orientation);
	}

	public void doArcR(GCommand.ArcRadius a) {
		double r = Point.toUnit(a.radius, a.p.unit, unit);
		Point dst = a.p.toUnit(unit);

		// checks
		double d = exactPoint.distance(dst);
		double dc = (double) ((int) Math.round(d * 10000)) / 10000;
		if (dc > r * 2)
			throw new MachineError(
					"arc endpoint distance bigger than diameter, " + dc + " > "
							+ r * 2);
		if (exactPoint.z != dst.z)
			throw new MachineError("z arcs not supported");
		if (dc == 0)
			return;

		// calc circle midpoint
		double fact = Math.sqrt(r * r - d * d / 4) / d;
		double xx, yy;
		if (a.orientation == OrientationType.CW) {
			xx = (exactPoint.x + dst.x) / 2 + fact * (dst.y - exactPoint.y);
			yy = (exactPoint.y + dst.y) / 2 - fact * (dst.x - exactPoint.x);
		} else {
			xx = (exactPoint.x + dst.x) / 2 - fact * (dst.y - exactPoint.y);
			yy = (exactPoint.y + dst.y) / 2 + fact * (dst.x - exactPoint.x);
		}

		Point centre = new Point(exactPoint.unit, xx, yy, exactPoint.z);
		arcDoMove(a, exactPoint, dst, centre, r, a.orientation);
	}

	public void doSetUnit(GCommand.Unit unitC) {
		UnitType u = unitC.type;
		curFeed = Point.toUnit(curFeed, unit, u);
		unit = u;
		exactPoint = exactPoint.toUnit(u);
		maxFeedsUPM = maxFeedsUPM.toUnit(u);
		stepsPerUnit = stepsPerUnit.toUnit(u);
		arcDelta = arcDelta.toUnit(u);
	}

	public void doComment(Comment c) {
	}

	public void doLabel(Label l) {
	}

	public void doProgram(Program p) {
	}

	// CNC action commands
	
	public void doCoolant(Coolant c) {
		if (emitter != null) {
			emitter.emit(new CNCCommand.Coolant(c));
		}
	}

	public void doDwell(Dwell d) {
		if (emitter != null) {
			emitter.emit(new CNCCommand.Dwell(d));
		}
	}

	public void doSpindle(Spindle s) {
		if (emitter != null) {
			emitter.emit(new CNCCommand.Spindle(s));
		}
	}

	public void doStop(Stop s) {
		if (emitter != null) {
			emitter.emit(new CNCCommand.Stop(s));
		}
	}

	public void doTool(Tool t) {
		if (emitter != null) {
			emitter.emit(new CNCCommand.Tool(t));
		}
	}
	
  public void doEndOfProgram(EndOfProgram eop) {
    if (emitter != null) {
      emitter.emit(new CNCCommand.EndOfProgram(eop));
    }
  }

	public void doCncMove(GCommand g, Point src, boolean rapid, Point cncDelta, Point feedStepPerSec) {
//    Log.println("CNCMOVE " 
//        + cncDelta.x + "/" + feedStepPerSec.x + " "
//         + cncDelta.y + "/" + feedStepPerSec.y + " "
//         + cncDelta.z + "/" + feedStepPerSec.z + " "
//         + rapid);
		if (emitter != null && 
				((cncDelta.x != 0 && feedStepPerSec.x != 0) || 
			     (cncDelta.y != 0 && feedStepPerSec.y != 0) || 
			     (cncDelta.z != 0 && feedStepPerSec.z != 0))) {
			emitter.emit(new CNCCommand.Move(g, src, cncDelta, feedStepPerSec, rapid));
		}
	}

	// thisnthat

	public Point unitToStep(Point p) {
		p = p.toUnit(unit);
		p.unit = UnitType.STEPS;
		p.x = (int) (Math.round(p.x * stepsPerUnit.x));
		p.y = (int) (Math.round(p.y * stepsPerUnit.y));
		p.z = (int) (Math.round(p.z * stepsPerUnit.z));
		return p;
	}
	
	public Point stepToUnit(Point p) {
		if (p.unit != UnitType.STEPS) {
			throw new MachineError("stepsToUnit point must be in steps");
		}
		Point pu = new Point(unit,
				p.x / stepsPerUnit.x,
				p.y / stepsPerUnit.y,
				p.z / stepsPerUnit.z);
		return pu;
	}

	public static class MachineError extends Error {
		private static final long serialVersionUID = -6971651828178810505L;

		public MachineError(String s) {
			super(s);
		}
	}
	
	public interface RenderListener {
	  public void move(GCommand g, boolean rapid, Point p1, Point p2, Point cncDelta, Point feeds);
	}
	
	public interface Emitter {
		public void emit(CNCCommand c);
	}

  @Override
  public String getProperty(String key, PropertyAxis axis) {
    try {
      Field var = getClass().getField(key);
      Object axes = var.get(this);
      Field avar = axes.getClass().getField(axis.name());
      Double val = avar.getDouble(axes);
      return val.toString();
    } catch (Throwable t) {
      return null;
    }
  }

  public double getPropertyDouble(String key, PropertyAxis axis) {
    String s = getProperty(key, axis);
    try {
      return Double.parseDouble(s);
    } catch (Throwable t) {
      return 0;
    }
  }
}
