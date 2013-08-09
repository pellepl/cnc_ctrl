package com.pelleplutt.cnc.io;


public interface CNCProtocol {
  public static final int  CNC_COMM_VERSION                   = 0x00010000;

  public static final int  COMM_PROTOCOL_INFO                 = 0x00;
  public static final int  COMM_PROTOCOL_CNC_ENABLE           = 0x01;
  public static final int  COMM_PROTOCOL_GET_STATUS           = 0x02;
  public static final int  COMM_PROTOCOL_SET_SR_MASK          = 0x03;
  public static final int  COMM_PROTOCOL_IS_LATCH_FREE        = 0x04;
  public static final int  COMM_PROTOCOL_CUR_MOTION_ID        = 0x05;
  public static final int  COMM_PROTOCOL_SET_LATCH_ID         = 0x06;
  public static final int  COMM_PROTOCOL_PIPE_ENABLE          = 0x07;
  public static final int  COMM_PROTOCOL_PIPE_FLUSH           = 0x08;
  public static final int  COMM_PROTOCOL_LATCH_XYZ            = 0x09;
  public static final int  COMM_PROTOCOL_LATCH_PAUSE          = 0x0a;
  public static final int  COMM_PROTOCOL_SET_POS              = 0x0b;
  public static final int  COMM_PROTOCOL_SET_IMM_XYZ          = 0x0c;
  public static final int  COMM_PROTOCOL_SR_TIMER_DELTA       = 0x0d;
  public static final int  COMM_PROTOCOL_POS_TIMER_DELTA      = 0x0e;

  public static final int  COMM_PROTOCOL_CONFIG               = 0x10;
  public static final int  COMM_PROTOCOL_CONFIG_MAX_X_FREQ    = 0x01;
  public static final int  COMM_PROTOCOL_CONFIG_MAX_Y_FREQ    = 0x02;
  public static final int  COMM_PROTOCOL_CONFIG_MAX_Z_FREQ    = 0x03;
  public static final int  COMM_PROTOCOL_CONFIG_RAPID_X_D     = 0x11;
  public static final int  COMM_PROTOCOL_CONFIG_RAPID_Y_D     = 0x12;
  public static final int  COMM_PROTOCOL_CONFIG_RAPID_Z_D     = 0x13;
  
  public static final int  COMM_PROTOCOL_GET_POS              = 0x20;
  public static final int  COMM_PROTOCOL_SET_OFFS_POS         = 0x21;
  public static final int  COMM_PROTOCOL_GET_OFFS_POS         = 0x22;
  
  public static final int  COMM_PROTOCOL_EVENT_SR_TIMER       = 0xe1;
  public static final int  COMM_PROTOCOL_EVENT_POS_TIMER      = 0xe2;
  public static final int  COMM_PROTOCOL_EVENT_SR_POS_TIMER   = 0xe3;
  public static final int  COMM_PROTOCOL_EVENT_SR             = 0xe4;
  public static final int  COMM_PROTOCOL_EVENT_ID             = 0xe5;

  public static final int  COMM_PROTOCOL_RESET                = 0xfe;
  
  public static final int  CNC_FP_DECIMALS        = 14;

  public static final int  CNC_STATUS_BIT_CONTROL_ENABLED   = 0;
  public static final int  CNC_STATUS_BIT_MOVEMENT_STILL    = 1;
  public static final int  CNC_STATUS_BIT_MOVEMENT_PAUSE    = 2;
  public static final int  CNC_STATUS_BIT_MOVEMENT_RAPID    = 3;
  public static final int  CNC_STATUS_BIT_PIPE_ACTIVE       = 4;
  public static final int  CNC_STATUS_BIT_PIPE_EMPTY        = 5;
  public static final int  CNC_STATUS_BIT_PIPE_FULL         = 6;
  public static final int  CNC_STATUS_BIT_LATCH_FULL        = 7;

  public static final int  COMM_ERROR_BIT_EMERGENCY         = 0;
  public static final int  COMM_ERROR_BIT_SETTINGS_CORRUPT  = 1;
  public static final int  COMM_ERROR_BIT_COMM_LOST         = 2;
  
  public static final int  CNC_STATUS_CONTROL_ENABLED   = 1 << CNC_STATUS_BIT_CONTROL_ENABLED;
  public static final int  CNC_STATUS_MOVEMENT_STILL    = 1 << CNC_STATUS_BIT_MOVEMENT_STILL;
  public static final int  CNC_STATUS_MOVEMENT_PAUSE    = 1 << CNC_STATUS_BIT_MOVEMENT_PAUSE;
  public static final int  CNC_STATUS_MOVEMENT_RAPID    = 1 << CNC_STATUS_BIT_MOVEMENT_RAPID;
  public static final int  CNC_STATUS_PIPE_ACTIVE       = 1 << CNC_STATUS_BIT_PIPE_ACTIVE;
  public static final int  CNC_STATUS_PIPE_EMPTY        = 1 << CNC_STATUS_BIT_PIPE_EMPTY;
  public static final int  CNC_STATUS_PIPE_FULL         = 1 << CNC_STATUS_BIT_PIPE_FULL;
  public static final int  CNC_STATUS_LATCH_FULL        = 1 << CNC_STATUS_BIT_LATCH_FULL;

  public static final int  CNC_STATUS_ERR_EMERGENCY     = 1 << (COMM_ERROR_BIT_EMERGENCY + 8);
  public static final int  CNC_STATUS_ERR_SETTINGS_CORRUPT  = 1 << (COMM_ERROR_BIT_SETTINGS_CORRUPT + 8);
  public static final int  CNC_STATUS_ERR_COMM_LOST     = 1 << (COMM_ERROR_BIT_COMM_LOST + 8);
  
  public static final int CNC_ERR_LATCH_BUSY			   = -1;
}
