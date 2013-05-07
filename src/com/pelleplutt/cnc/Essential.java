package com.pelleplutt.cnc;

/**
 * Parameters used during compilation 
 * @author petera
 */
public interface Essential {
	/** App name */
	String name = "CNC_CTRL";
	/** Version major */
	int vMaj = 1;
	/** Version minor */
	int vMin = 0;
	/** Version micro */
	int vMic = 0;
	/** Path for app data */
	String userSettingPath = ".cnc_ctrl";
	/** Name of settings file */
	String settingsFile = "cnc_ctrl.settings";
	/** tmp sub path */
	String tmpPath = "tmp";
	/** bin sub path */
	String binPath = "bin";
	
	/** Logging */
	static final boolean LOG_C = true;
	static final boolean LOG_CLASS = true;
	static final boolean LOG_METHOD = true;
	static final boolean LOG_LINE = true;
	static final String LOG_SETTING_FILE_NAME = ".cnc_ctrl.log";
	
  static final int COMM_OTHER_ADDRESS = 1;
  static final int COMM_ADDRESS = 2;
}
