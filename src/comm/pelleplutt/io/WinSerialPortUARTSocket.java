package comm.pelleplutt.io;


import java.io.File;
import java.io.IOException;

import comm.pelleplutt.util.AppSystem;
import comm.pelleplutt.util.Log;
/**
 * Serial port for win.
 * @author petera
 */
public class WinSerialPortUARTSocket extends UARTSocket {
	public static final String PROP_PATH_BIN = "portconnector.win.bin";
	public static final String PROP_NAME = "portconnector.win.name";
	
	protected WinSerialPortUARTSocket() {
	}

	void checkBinary(File exe) throws IOException, InterruptedException {
		// cpy our little binary 
		if (!exe.exists()) {
			AppSystem.copyAppResource("native/win/uartsocket.exe", exe);
			Log.println("copy ok");
		}
	}

	File getBinFile() {
		String name = System.getProperty(PROP_NAME) + ".exe";
		File binFile = new File(System.getProperty(PROP_PATH_BIN), name);
		return binFile;
	}
}
