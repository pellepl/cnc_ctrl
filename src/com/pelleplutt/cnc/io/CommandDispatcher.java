package com.pelleplutt.cnc.io;

import java.util.List;

import com.pelleplutt.cnc.ctrl.GCommand;

public abstract class CommandDispatcher {
	public abstract void doSetFeed(GCommand.FeedRate f);
	public abstract void doSetPositioning(GCommand.Positioning p);
	public abstract void doMove(GCommand.Movement m);
	public abstract void doArcC(GCommand.ArcCentre a);
	public abstract void doArcR(GCommand.ArcRadius a);
	public abstract void doSetUnit(GCommand.Unit unitC);
	public abstract void doComment(GCommand.Comment c);
	public abstract void doCoolant(GCommand.Coolant c);
	public abstract void doDwell(GCommand.Dwell d);
	public abstract void doEndOfProgram(GCommand.EndOfProgram eop);
	public abstract void doLabel(GCommand.Label l);
	public abstract void doProgram(GCommand.Program p);
	public abstract void doSpindle(GCommand.Spindle s);
	public abstract void doStop(GCommand.Stop s);
	public abstract void doTool(GCommand.Tool t);
	public void doOther(GCommand c) {
	}
	
	public void exeCommands(List<GCommand> c) {
		for (GCommand command : c) {
			exeCommand(command);
		}
	}
	
	public void exeCommand(GCommand c) {
		if (c instanceof GCommand.ArcCentre) {
			doArcC((GCommand.ArcCentre) c);
		} else if (c instanceof GCommand.ArcRadius) {
			doArcR((GCommand.ArcRadius) c);
		} else if (c instanceof GCommand.Movement) {
			doMove((GCommand.Movement) c);
		} else if (c instanceof GCommand.Unit) {
			doSetUnit((GCommand.Unit) c);
		} else if (c instanceof GCommand.FeedRate) {
			doSetFeed((GCommand.FeedRate) c);
		} else if (c instanceof GCommand.Positioning) {
			doSetPositioning((GCommand.Positioning) c);
		} else if (c instanceof GCommand.Comment) {
			doComment((GCommand.Comment)c);
		} else if (c instanceof GCommand.Coolant) {
			doCoolant((GCommand.Coolant)c);
		} else if (c instanceof GCommand.Dwell) {
			doDwell((GCommand.Dwell)c);
		} else if (c instanceof GCommand.EndOfProgram) {
			doEndOfProgram((GCommand.EndOfProgram)c);
		} else if (c instanceof GCommand.Label) {
			doLabel((GCommand.Label)c);
		} else if (c instanceof GCommand.Program) {
			doProgram((GCommand.Program)c);
		} else if (c instanceof GCommand.Spindle) {
			doSpindle((GCommand.Spindle)c);
		} else if (c instanceof GCommand.Stop) {
			doStop((GCommand.Stop)c);
		} else if (c instanceof GCommand.Tool) {
			doTool((GCommand.Tool)c);
		} else {
			doOther(c);
		}
	}

}
