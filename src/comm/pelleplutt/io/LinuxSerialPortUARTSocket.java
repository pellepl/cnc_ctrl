package comm.pelleplutt.io;


import java.io.File;
import java.io.IOException;

import comm.pelleplutt.util.AppSystem;
import comm.pelleplutt.util.Log;
/**
 * Serial port for linux.
 * 
 * @author petera
 */
public class LinuxSerialPortUARTSocket extends UARTSocket {
	public static final String PROP_PATH_BIN = "portconnector.linux.bin";
	public static final String PROP_PATH_SRC = "portconnector.linux.src";
	public static final String PROP_NAME = "portconnector.linux.name";
	
	protected LinuxSerialPortUARTSocket() {
	}

	String preprocessPortName(String portname) {
		if (!portname.startsWith("/dev/")) {
			portname = "/dev/" + portname;
		}
		return portname;
	}

	public void configureTimeout(long timeout)
			throws IOException {
		sData.setSoTimeout((int)timeout + ((timeout > 0) ? 100 : 0));
		timeout /= 100;
		controlCommand(true, "U T" + timeout
			+ " M" + (timeout == 0 ? '1' : '0'), 0);
	}

	void checkBinary(File exe) throws IOException, InterruptedException {
		// compile our little binary 
		if (!exe.exists()) {
			Log.println("compiling");
			File srcFile = new File(System.getProperty(PROP_PATH_SRC),
					System.getProperty(PROP_NAME) + ".c");
			AppSystem.copyAppResource("native/linux/src/uartsocket.c", srcFile);
			exe.getParentFile().mkdirs();
			AppSystem.ProcessResult res = AppSystem.run(
					"gcc -o " + exe.getAbsolutePath() + " "
							+ srcFile.getAbsolutePath() + " -lpthread", null, null, true, true);
			if (res.code != 0) {
				exe.delete();
				throw new IOException("Could not compile uartsocket binary: "
						+ res.err);
			}
			Log.println("compile ok");
		}
	}

	File getBinFile() {
		String name = System.getProperty(PROP_NAME);
		File binFile = new File(System.getProperty(PROP_PATH_BIN), name);
		return binFile;
	}
}
