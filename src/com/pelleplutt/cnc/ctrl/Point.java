package com.pelleplutt.cnc.ctrl;

import com.pelleplutt.cnc.types.UnitType;


public class Point {
  static final double INCH_PER_MM = 25.4;
  static final double MM_PER_INCH = 1 / INCH_PER_MM;
  
  public UnitType unit;
  public double x;
  public double y;
  public double z;
  
  public Point(UnitType unit, double x, double y, double z) {
    this.unit = unit;
    this.x = x;
    this.y = y;
    this.z = z;
  }
  
  public Point(double x, double y, double z) {
    this.unit = null;
    this.x = x;
    this.y = y;
    this.z = z;
  }
  
  public Point(Point p) {
    unit = p.unit;
    x = p.x;
    y = p.y;
    z = p.z;
  }
  
  public Point toUnit(UnitType unit) {
    if (this.unit == null) {
      throw new Error("Cannot convert from undefined unit");
    }
    if (this.unit == unit) {
      return new Point(this);
    } else if (unit == UnitType.MILLIMETERS) {
      return new Point(UnitType.MILLIMETERS, x*MM_PER_INCH, y*MM_PER_INCH, z*MM_PER_INCH);
    } else if (unit == UnitType.INCHES) {
      return new Point(UnitType.INCHES, x*INCH_PER_MM, y*INCH_PER_MM, z*INCH_PER_MM);
    } else {
      return null;
    }
  }
  
  public static double toUnit(double v, UnitType fromUnit, UnitType toUnit) {
	    if (fromUnit == null) {
	        throw new Error("Cannot convert from undefined unit");
	      }
	    if (toUnit == null) {
	        throw new Error("Cannot convert to undefined unit");
	      }
	      if (toUnit == fromUnit) {
	        return v;
	      } else if (toUnit == UnitType.MILLIMETERS) {
	        return v * MM_PER_INCH;
	      } else if (toUnit == UnitType.INCHES) {
		        return v * INCH_PER_MM;
	      } else {
	        return Double.NaN;
	      }
  }

  public Point translate(Point delta) {
    Point p = toUnit(delta.unit);
    p.x += delta.x;
    p.y += delta.y;
    p.z += delta.z;
    return p;
  }
  
  public void translateI(Point delta) {
    if (delta.unit != unit) {
      delta = delta.toUnit(unit);
    }
    x += delta.x;
    y += delta.y;
    z += delta.z;
  }
  
  public Point negate(Point p) {
    return new Point(p.unit, -p.x, -p.y, -p.z);
  }

  public void negateI() {
    x = -x;
    y = -y;
    z = -z;
  }

  public void multiplyI(double m) {
    x *= m;
    y *= m;
    z *= m;
  }
  
  public Point diff(Point p) {
      p = p.toUnit(unit);
      p.x -= x;
      p.y -= y;
      p.z -= z;
      return p;
  }
  
  public double distance(Point p) {
	    if (p.unit != unit) {
	        p = p.toUnit(unit);
	      }
	  return Math.sqrt((p.x-x)*(p.x-x)+(p.y-y)*(p.y-y)+(p.z-z)*(p.z-z));
  }

  public double distance2(Point p) {
	    if (p.unit != unit) {
	        p = p.toUnit(unit);
	      }
	  return (p.x-x)*(p.x-x)+(p.y-y)*(p.y-y)+(p.z-z)*(p.z-z);
  }
  public String toString() {
	  return "[X"+x+" Y"+y+" Z"+z+"]";
	  
  }
}
