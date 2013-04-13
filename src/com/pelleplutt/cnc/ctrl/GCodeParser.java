package com.pelleplutt.cnc.ctrl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.pelleplutt.cnc.types.CoolantType;
import com.pelleplutt.cnc.types.OrientationType;
import com.pelleplutt.cnc.types.Point;
import com.pelleplutt.cnc.types.PositioningType;
import com.pelleplutt.cnc.types.StopType;
import com.pelleplutt.cnc.types.UnitType;
import com.pelleplutt.util.Log;


/**
 * Transforms register groups from GCodeLexer into GCommands.
 * Performs parser checking. 
 * @author petera
 */
public class GCodeParser implements GCodeLexer.Emitter{
  public enum Warning {
    FEEDRATE_NOT_SET,
    POSITIONING_NOT_SET,
    UNIT_NOT_SET
  }
  
  final static boolean LOG = false;
  
  // GCode state
  int line;
  int startOffset;
  int endOffset;
  // positioning
  PositioningType positioning;
  // working unit
  UnitType unit;
  // current feed
  double curFeed;
  
  // machine state
  Point curPoint;

  // gcodes
  Map<Character, CodeHandler> codeHandlers;
  
  Emitter emitter;
  
  public GCodeParser(Emitter emitter) {
    codeHandlers = new HashMap<Character, CodeHandler>();
    codeHandlers.put('G', new GCodeHandler());
    codeHandlers.put('M', new MCodeHandler());
    codeHandlers.put('N', new NCodeHandler());
    codeHandlers.put('O', new OCodeHandler());
    codeHandlers.put('T', new TCodeHandler());
    
    this.emitter = emitter;
  }
  
  public void reset() {
    positioning = null;
    unit = null; 
    curPoint = new Point(UnitType.MILLIMETERS, 0, 0, 0);
    curFeed = 0;
  }
  
  // parser->machine state
  
  void moveCurPos(Point dst, boolean rapid) {
    if (!rapid && curFeed == 0) {
      emitter.warning(Warning.FEEDRATE_NOT_SET, line, startOffset, endOffset);
    }
    if (positioning == null) {
      emitter.warning(Warning.POSITIONING_NOT_SET, line, startOffset, endOffset);
      positioning = PositioningType.ABSOLUTE;
    }
    if (unit == null) {
      emitter.warning(Warning.UNIT_NOT_SET, line, startOffset, endOffset);
      unit = UnitType.MILLIMETERS;
    }
    switch (positioning) {
    case ABSOLUTE:
      curPoint = dst;
      break;
    case RELATIVE:
      curPoint.translateI(dst);
      break;
    }
  }
  
  void doSetFeed(double feed) {
    curFeed = feed;
    if (curFeed <= 0) {
      throw new CommandError("Bad feed " + feed);
    }
    emitter.emit(new GCommand.FeedRate(feed), line, startOffset, endOffset);
  }
  
  void doSetPositioning(PositioningType p) {
    positioning = p;
    emitter.emit(new GCommand.Positioning(p), line, startOffset, endOffset);
  }
  
  void doMove(Point dst, boolean rapid) {
    moveCurPos(dst, rapid);
    emitter.emit(rapid ? new GCommand.MovementRapid(dst) : new GCommand.Movement(dst), line, startOffset, endOffset);
  }
  
  void doArcRadius(Point dst, OrientationType o, double r) {
    moveCurPos(dst, false);
    emitter.emit(new GCommand.ArcRadius(dst, o, r), line, startOffset, endOffset);
  }
  
  void doArcCentre(Point dst, OrientationType o, Point centre) {
    moveCurPos(dst, false);
    emitter.emit(new GCommand.ArcCentre(dst, o, centre), line, startOffset, endOffset);
  }
  
  void doSetUnit(UnitType u) {
    unit = u;
    curPoint = curPoint.toUnit(u);
    emitter.emit(new GCommand.Unit(u), line, startOffset, endOffset);
  }
  
  // parser 
  
  @Override
  public void onCode(Map<Character, String> regs, int line, int startOffset,
      int endOffset) {
    this.line = line;
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    Set<Character> regDefs = regs.keySet();
    CodeHandler ch = null;
    for (Character character : regDefs) {
      ch = codeHandlers.get(character);
      if (ch != null) {
        break;
      }
    }
    if (ch != null) {
      ch.exe(regs);
    } else {
      throw new CommandError("Internal error, no codeHandler for " + regs);
    }
  }

  @Override
  public void onComment(String comment, int line, int startOffset,
      int endOffset) {
    this.line = line;
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    emitter.emit(new GCommand.Comment(comment.trim()), line, startOffset, endOffset);
  }

  
  abstract class CodeHandler {
    abstract void exe(Map<Character, String> regs);
    
    Point extractCoord(Map<Character, String> regs, char cx, char cy, char cz) {
      String xs = regs.get(cx);
      String ys = regs.get(cy);
      String zs = regs.get(cz);
      if (xs == null && ys == null && zs == null) {
        return null;
      }
      double x = (xs == null ? (positioning == PositioningType.ABSOLUTE ? curPoint.x : 0) : Double.parseDouble(xs));
      double y = (ys == null ? (positioning == PositioningType.ABSOLUTE ? curPoint.y : 0) : Double.parseDouble(ys));
      double z = (zs == null ? (positioning == PositioningType.ABSOLUTE ? curPoint.z : 0) : Double.parseDouble(zs));
      return new Point(unit, x, y, z);
    }
    
    Point extractXYZ(Map<Character, String> regs) {
      return extractCoord(regs, 'X', 'Y', 'Z');
    }
    
    Point extractIJK(Map<Character, String> regs) {
      return extractCoord(regs, 'I', 'J', 'K');
    }
    
    double extractRegister(Map<Character, String> regs, char reg) {
      String rs = regs.get(reg);
      double r = (rs == null ? Double.NaN : Double.parseDouble(rs));
      return r;
    }
  }
  
  class GCodeHandler extends CodeHandler {
    static final char CODE = 'G';
    void exe(Map<Character, String> regs) {
      Point p = null;
      Point pc = null;
      double r;
      String feedStr = regs.get('F');
      if (feedStr != null) {
        // feed
        if (LOG) Log.println("feed " + feedStr);
        doSetFeed(Double.parseDouble(feedStr));
      }
      int code = (int)(Double.parseDouble(regs.get(CODE)));
      switch (code) {
      case 0:
        // rapid movement | xyz
        if (LOG) Log.println("move rapid " + regs.get('X') + "," + regs.get('Y') + "," + regs.get('Z'));
        p = extractXYZ(regs);
        if (p != null) {
          doMove(p, true);
        }
        break;
      case 1:
        // linear movement | xyz
        if (LOG) Log.println("move linear " + regs.get('X') + "," + regs.get('Y') + "," + regs.get('Z'));
        p = extractXYZ(regs);
        if (p != null) {
          doMove(p, false);
        }
        break;
      case 2:
      case 3:
        // circular cw/ccw | xyzijk / xyzr
        if (LOG) Log.println("circ " + regs.get('X') + "," + regs.get('Y') + "," + regs.get('Z') + "," + regs.get('I') + "," + regs.get('J') + "," + regs.get('R'));
        p = extractXYZ(regs);
        pc = extractIJK(regs);
        r = extractRegister(regs, 'R');
        if (pc == null && !Double.isNaN(r)) {
          doArcRadius(p, code == 2 ? OrientationType.CW : OrientationType.CCW, r);
        } else if (pc != null && Double.isNaN(r)) {
          doArcCentre(p, code == 2 ? OrientationType.CW : OrientationType.CCW, pc);
        } else if (pc != null && !Double.isNaN(r)){
          throw new CommandError("arc registers overdefined");
        } else {
          // ignore
        }
        break;
      case 4:
        // dwell | p
        if (LOG) Log.println("dwell " + regs.get('P'));
        r = extractRegister(regs, 'P');
        if (Double.isNaN(r)) {
          throw new CommandError("dwell time not specified");
        }
        emitter.emit(new GCommand.Dwell(r), line, startOffset, endOffset);
        break;
      case 20:
        // inches
        if (LOG) Log.println("inches");
        doSetUnit(UnitType.INCHES);
        break;
      case 21:
        // millimeters
        if (LOG) Log.println("millimeters");
        doSetUnit(UnitType.MILLIMETERS);
        break;
      case 90:
        // absolute pos
        if (LOG) Log.println("absolute");
        doSetPositioning(PositioningType.ABSOLUTE);
        break;
      case 91:
        // incremental pos
        if (LOG) Log.println("incremental");
        doSetPositioning(PositioningType.RELATIVE);
        break;
      default:
          throw new CommandError("Unhandled " + CODE + " code " + regs.get(CODE));
      }
    }
  }
  
  class MCodeHandler extends CodeHandler {
    static final char CODE = 'M';
    void exe(Map<Character, String> regs) {
      int code = (int)(Double.parseDouble(regs.get(CODE)));
      switch (code) {
      case 0:
        // compulsory stop
        if (LOG) Log.println("compulsory stop");
        emitter.emit(new GCommand.Stop(StopType.COMPULSORY), line, startOffset, endOffset);
        break;
      case 1:
        // optional stop
        if (LOG) Log.println("optional stop");
        emitter.emit(new GCommand.Stop(StopType.OPTIONAL), line, startOffset, endOffset);
        break;
      case 2:
        // end of program
        if (LOG) Log.println("end of program");
        emitter.emit(new GCommand.EndOfProgram(), line, startOffset, endOffset);
        break;
      case 3:
        // spindle on cw
        if (LOG) Log.println("spindle on cw");
        emitter.emit(new GCommand.Spindle(true, OrientationType.CW), line, startOffset, endOffset);
        break;
      case 4:
        // spindle on ccw
        if (LOG) Log.println("spindle on ccw");
        emitter.emit(new GCommand.Spindle(true, OrientationType.CCW), line, startOffset, endOffset);
        break;
      case 5:
        // spindle off 
        if (LOG) Log.println("spindle off");
        emitter.emit(new GCommand.Spindle(false), line, startOffset, endOffset);
        break;
      case 6:
        // coolant mist on
        if (LOG) Log.println("coolant mist on");
        emitter.emit(new GCommand.Coolant(true, CoolantType.MIST), line, startOffset, endOffset);
        break;
      case 7:
        // coolant flood on
        if (LOG) Log.println("coolant flood on");
        emitter.emit(new GCommand.Coolant(true, CoolantType.FLOOD), line, startOffset, endOffset);
        break;
      case 8:
        // coolant off
        if (LOG) Log.println("coolant off");
        emitter.emit(new GCommand.Coolant(false), line, startOffset, endOffset);
        break;
      case 13:
        // spindle on cw, coolant flood on
        if (LOG) Log.println("spindle on cw, coolant flood on");
        emitter.emit(new GCommand.Coolant(true, CoolantType.FLOOD), line, startOffset, endOffset);
        emitter.emit(new GCommand.Spindle(true, OrientationType.CW), line, startOffset, endOffset);
        break;
      default:
          throw new CommandError("Unhandled " + CODE + " code " + regs.get(CODE));
      }
    }
  }
  
  class NCodeHandler extends CodeHandler {
    static final char CODE = 'N';
    void exe(Map<Character, String> regs) {
      int code = (int)(Double.parseDouble(regs.get(CODE)));
      if (LOG) Log.println("line " + code);
      emitter.emit(new GCommand.Label(code), line, startOffset, endOffset);
    }
  }
  
  class OCodeHandler extends CodeHandler {
    static final char CODE = 'O';
    void exe(Map<Character, String> regs) {
      String program = regs.get(CODE);
      // program id
      if (LOG) Log.println("program " + program);
      emitter.emit(new GCommand.Program(program), line, startOffset, endOffset);
    }
  }
  
  class TCodeHandler extends CodeHandler {
    static final char CODE = 'T';
    void exe(Map<Character, String> regs) {
      int tool = (int)(Double.parseDouble(regs.get(CODE)));
      // tool
      if (LOG) Log.println("tool " + tool);
      emitter.emit(new GCommand.Tool(tool), line, startOffset, endOffset);
    }
  }
  
  // thisnthat
  
  static class CommandError extends Error {
    private static final long serialVersionUID = 3615623254302311298L;

    public CommandError(String s) {
      super(s);
    }
  }
  
  public static interface Emitter {
    public void emit(GCommand c, int line, int startOffset, int endOffset);
    public void warning(Warning w, int line, int startOffset, int endOffset);
  }
}
