package com.pelleplutt.cnc.ctrl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.pelleplutt.util.AppSystem;
import com.pelleplutt.util.Log;


/**
 * Reads GCode content, groups registers and emits groups.
 * Basic lexer syntax checking. 
 * @author petera
 */
public class GCodeLexer {
  // parser
  Emitter emitter;

  int line;
  int contentLine;
  int offset;
  int contentOffset;
  int handleOffset;
  
  State state;
  char curCode;
  StringBuffer num;
  StringBuffer comment;
  int commentNest;
  char lastAction = CODE_UNDEF;
  String lastActionNum;
  Map<Character, String> registerSet;
  static final String CODES = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  static final String NUMERALS = "+-0123456789.";
  static final String BLANKS = " %\r\t";
  static final String DELIMITER = " \n";
  static final char CODE_UNDEF = '?';
  // syntax
  static final String ACTION_CODES = "GMNOT";
  
  static final boolean LOG = false;
  
  public void reset() {
    state = State.STRIP;
    curCode = CODE_UNDEF;
    commentNest = 0;
    registerSet = new HashMap<Character, String>();
    line = 0;
    contentLine = 0;
    offset = 0;
    handleOffset = 0;
    contentOffset = 0;
  }
  
  public GCodeLexer(Emitter emitter) {
    this.emitter = emitter;
  }
  
  // parser
  
  enum State {
    STRIP,		// trying to find content, stripping blanks
    COMMENT,	// comment, ignore all but ()
    LINECOMMENT, // line comment
    CODE,		// code parsing
    NUM			// numeral parsing
  };
  
  StringBuffer offsetTest;

  public void parse(File f) throws FileNotFoundException {
    FileInputStream fin = new FileInputStream(f);
    parse(fin);
  }
  
  public void parse(InputStream in) {
    offsetTest = new StringBuffer();
    try {
      int c;
      while ((c = in.read()) != -1) {
        parseChar((char)c);
        offsetTest.append((char)c);
        offset++;
      }
      handleAccumulated();

    } catch (ParseError e) {
      throw e;
    } catch (Throwable t) {
      throw new ParseError(t);
    } finally {
      AppSystem.closeSilently(in);
    }
  }
  
  
  void handleRegisters(Map<Character, String> regs) {
    boolean action = false;
    Set<Character> registers = registerSet.keySet();
    for (int i = 0; i < ACTION_CODES.length(); i++) {
      if (registers.contains((char)ACTION_CODES.charAt(i))) {
        action = true;
        lastAction = (char)ACTION_CODES.charAt(i);
        lastActionNum = regs.get(lastAction);
        break;
      }
    }
    if (action == false && lastAction != CODE_UNDEF) {
    	registerSet.put(lastAction, lastActionNum);
    }
    if (!registerSet.isEmpty()) {
	    int startOffset = handleOffset;
	    int endOffset = contentOffset;
	    handleOffset = endOffset;
	    emitter.onCode(regs,  contentLine, startOffset, endOffset);
	    registerSet = new HashMap<Character, String>();
	    if (LOG) Log.println("--");
    }
  }
  
  void handleComment(String s) {
    int startOffset = handleOffset;
    int endOffset = contentOffset;
    handleOffset = endOffset + 1;
    emitter.onComment(s,  contentLine, startOffset, endOffset);
  }
  
  // parser

  void handleAccumulated() {
    // emit current accumulated registerset
	if (!registerSet.isEmpty()) {
	  handleRegisters(registerSet);
	}
  }
  
  void handleRegister(char c, StringBuffer num) {
    if (ACTION_CODES.indexOf(c) >= 0) {
      // emit current accumulated registerset
      handleAccumulated();
    }
    if (registerSet.containsKey(c)) {
      throw new ParseError("Redefinition of " + c + " from " + registerSet.get(c) + " to " + num);
    }
    try {
      Double.parseDouble(num.toString());
    } catch (NumberFormatException nfe) {
      throw new ParseError(num + " is not a number");
    }
    registerSet.put(c, num.toString());
    if (LOG) Log.print(c + "=" + num + "  ");
  }

  void parseChar(char c) {
    State oldState = null;
loop:
    do {
      oldState = state;
      switch (state) {
      case STRIP:
        if (NUMERALS.indexOf(c) >= 0) {
          state = State.NUM;
        }
        else if (CODES.indexOf(c) >= 0) {
          num = new StringBuffer();
          state = State.CODE;
        }
        else if (c == '(') {
          contentLine = line;
          contentOffset = offset;
          handleAccumulated();
          comment = new StringBuffer();
          state = State.COMMENT;
        }
        else if (c == ')') {
          throw new ParseError("Unexpected closing comment");
        }
        else if (c == ';') {
          contentLine = line;
          contentOffset = offset;
          handleAccumulated();
          comment = new StringBuffer();
          state = State.LINECOMMENT;
        }
        else if (BLANKS.indexOf(c) >= 0) {
            break loop;
        } else if (DELIMITER.indexOf(c) >= 0) {
          if (!registerSet.isEmpty()) {
            handleAccumulated();
          }
          line++;
          break loop;
        } else {
          throw new ParseError("Unexpected character " + c);
        }
        break;
      case COMMENT:
      comment.append(c);
        if (c == '(') {
          commentNest++;
        } else if (c == ')') {
          commentNest--;
          if (commentNest == 0) {
            state = State.STRIP;
            contentLine = line;
            contentOffset = offset;
            handleComment(comment.toString());
            comment = null;
            break loop;
          }
        }
        break;
      case LINECOMMENT:
      comment.append(c);
        if (c == '\n') {
          state = State.STRIP;
          contentLine = line;
          contentOffset = offset;
          handleComment(comment.toString());
          comment = null;
          break loop;
        }
        break;
      case CODE:
        if (CODES.indexOf(c) >= 0) {
          curCode = c;
          state = State.STRIP;
          contentLine = line;
          contentOffset = offset;
          break loop;
        }
        break;
      case NUM:
    	if (NUMERALS.indexOf(c) >= 0) {
          state = State.NUM;
          num.append(c);
        } else {
          state = State.STRIP;
          if (curCode == CODE_UNDEF) {
            throw new ParseError("Unexpected numeral " + num);
          }
          handleRegister(curCode, num);
          curCode = CODE_UNDEF;
        }
        break;
      }
    } while (state != oldState);
  }

  
  public interface Emitter {
    public void onCode(Map<Character, String> regs, int line, int startOffset, int endOffset);
    public void onComment(String comment, int line, int startOffset, int endOffset);
  }
  
  public static class ParseError extends Error {
    private static final long serialVersionUID = -9051402971703505455L;
    public ParseError(String s) {
      super(s);
    }
    public ParseError(Throwable t) {
      super(t);
    }
  }
}
