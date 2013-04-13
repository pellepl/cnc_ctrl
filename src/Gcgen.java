import java.io.PrintStream;


public class Gcgen {
  // nut inner diameter
  double dia_i = 12;
  double rad_i = dia_i/2;
  // nut hole wall thickness
  double hole_rad_t = 0.5;
  
  // nut height
  double h = 6;
  // nut top height (if any)
  double top = 1;
  
  // work feed rate
  double feed = 150;
  // tool diameter
  double tool_dia = 3.2;
  double tool_rad = tool_dia/2;
  // tool overlap rad
  double tool_dia_ov_rad = 0.2;
  // route z increments
  double z_inc = 1;

  // work piece height
  double workh = 10;
  
  PrintStream out = System.out;

  public static void main(String[] args) {
    new Gcgen().gen();
  }
  
  void gen() {
    out.println("(header)");
    out.println("G21");
    out.println("G90");
    out.println("G00 Z1");
    out.println("G00 X0 Y0");
    double rad_o = rad_i*2;

    out.println("(make depth)");
    double z;
    // grind away unnecessary material
    for (z = 1; z < (workh-top-h); z += z_inc) {
      out.println("G01 X0 Y0");
      fillCircle(0,0,z, 0, rad_o + tool_rad);
    }

    // do the top
    out.println("(top)");
    out.println("G01 X0 Y0");
    
    for (;z < (workh-h); z += z_inc) {
      fillCircle(0,0,z, (rad_o - rad_i)/2 + rad_i, rad_o + tool_rad);
      out.println("G01 X0 Y0");
    }

    // hole
    out.println("(hole)");
    out.println("G00 Z1");
    out.println("G00 X0 Y0");
    out.println("G01 Z-" + i(workh-top-h) + " F" + i(feed));
    
    for (z = (workh-top-h); z < workh; z += z_inc) {
      fillCircle(0,0,z, 0, rad_i - tool_rad - hole_rad_t);
      out.println("G01 X0 Y0");
    }


    // nut
    out.println("(nut)");
    out.println("G00 Z1");
    out.println("G00 X0 Y0");
    double r = rad_o + tool_rad;
    for (z = (workh -h); z < workh; z += z_inc) {
      for (int sides = 0; sides < 7; sides++) {
        double x = r*Math.cos(Math.toRadians(360*(double)sides/6.0));
        double y = r*Math.sin(Math.toRadians(360*(double)sides/6.0));
        out.println("G01 X"+i(x)+" Y"+i(y));
        if (sides == 0) {
          out.println("G01 Z-" + i(z) + " F" + i(feed));
        }
      }
    }
    
    // end
    out.println("(end)");
    out.println("G01 Z1");
    out.println("G00 X0 Y0");
    out.println("M05");
    out.println("M02");
  }
  
  void fillCircle(double cx, double cy, double z, double sr, double er) {
    double tr = tool_rad - tool_dia_ov_rad;
    double cr;
    if (sr == 0) sr = tr;
    out.println("G01 X" + i(sr) + " Y0");
    out.println("G01 Z-" + i(z) + " F" + i(feed));
    for (cr = sr; cr < er-tool_rad; cr += tr) {
      out.println("G01 X" + i(cr) + " Y0");
      out.println("G03 X-" + i(cr) + " Y0 R"+i(cr));
      out.println("G03 X" + i(cr) + " Y0 R"+i(cr));
    }
    if (cr < er) {
      cr = er;
      out.println("G01 X" + i(cr) + " Y0");
      out.println("G03 X-" + i(cr) + " Y0 R"+i(cr));
      out.println("G03 X" + i(cr) + " Y0 R"+i(cr));
    }
  }
  
  static double i(double i) {
    return ((int)(Math.round(i*100000)))/100000.0;
  }
}