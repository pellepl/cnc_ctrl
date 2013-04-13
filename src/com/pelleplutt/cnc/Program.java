package com.pelleplutt.cnc;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import com.pelleplutt.cnc.ctrl.CNCCommand;
import com.pelleplutt.cnc.ctrl.GCodeLexer;
import com.pelleplutt.cnc.ctrl.GCodeParser;
import com.pelleplutt.cnc.ctrl.GCommand;
import com.pelleplutt.cnc.ctrl.GVirtualCNC;
import com.pelleplutt.cnc.ctrl.GCodeParser.Warning;
import com.pelleplutt.cnc.ui.BluePrintPanel;
import com.pelleplutt.cnc.ui.BluePrintRenderer;

public class Program {
  public int commands, comments, warnings;
  public BluePrintRenderer bluePrintRenderer;
  public List<CNCCommand> cncCommandList;
  public File file;
  
  protected Program() {
  }
  
  public static Program loadGCode(File f, GVirtualCNC gvcnc, BluePrintPanel bp) throws FileNotFoundException {
    // Load list of GCodes
    final Program program = new Program();
    program.file = f;
    final List<GCommand> gList = new ArrayList<GCommand>();
    GCodeParser gcodeParser = new GCodeParser(new GCodeParser.Emitter() {
      @Override
      public void emit(GCommand c, int line, int startOffset, int endOffset) {
        gList.add(c);
        if (c instanceof GCommand.Comment) {
          program.comments++;
        } else {
          program.commands++;
        }
      }

      @Override
      public void warning(Warning w, int line, int startOffset, int endOffset) {
        //warnings++;
        System.out.println("Warning at line " + line + " : " + w);
      }
    });
    GCodeLexer gcodeLexer = new GCodeLexer(gcodeParser);
    gcodeParser.reset();
    gcodeLexer.reset();
    gcodeLexer.parse(f);
    
    // convert gcodes to cnc commands
    program.cncCommandList = new ArrayList<CNCCommand>();
    
    program.bluePrintRenderer = new BluePrintRenderer(bp.getBluePrintWidth(), bp.getBluePrintHeight());
    program.bluePrintRenderer.setMagnificationAndOffset(bp.getMagnification(), bp.getOffsetX(), bp.getOffsetY());
    gvcnc.setRenderListener(program.bluePrintRenderer);
    gvcnc.setEmitter(new GVirtualCNC.Emitter() {
      @Override
      public void emit(CNCCommand c) {
        program.cncCommandList.add(c);
      }
    });
    gvcnc.exeCommands(gList);

    return program;
  }
  
  public String toString() {
    return file.getName();
  }
}
