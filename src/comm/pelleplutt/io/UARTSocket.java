package comm.pelleplutt.io;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;

import comm.pelleplutt.util.AppSystem;
import comm.pelleplutt.util.Log;

public abstract class UARTSocket {
	static int serverPort_g = 10000 + (int)(Math.random() * 10000);
	static Process serverProcess;
	static boolean shutdownHookAdded = false;
	
	public static final int PARITY_NONE = 0;
	public static final int PARITY_EVEN = 1;
	public static final int PARITY_ODD = 2;

	public static final int STOPBITS_1 = 1;
	public static final int STOPBITS_2 = 2;
	
	String serialport;
	volatile boolean isOpen = false;
	Socket sCtrl, sData;
	InputStream ctrlInStr;
	BufferedReader ctrlIn;
	DataOutputStream ctrlOut;
	BufferedReader dataCIn;
	DataOutputStream dataCOut;
	InputStream dataIn;
	OutputStream dataOut;
	
	String server = "localhost";
	int serverPort = serverPort_g;

	
	public static UARTSocket getPort(String portname, UARTSocket port) throws IOException {
		portname = port.preprocessPortName(portname);
		port.serverPort = serverPort_g++;
		port.serialport = portname;
		
		int portIx = portname.indexOf(':');
		if (portIx > 0) {
			int serverPort = Integer.parseInt(portname.substring(portIx+1));
			port.serverPort = serverPort;
			portname = portname.substring(0, portIx);
		}
		int serverNameIx = portname.indexOf('@');
		if (serverNameIx > 0) {
			port.serialport = portname.substring(0,serverNameIx);
			port.server = portname.substring(serverNameIx+1);
		}
		
		if (port.server.equalsIgnoreCase("localhost") || port.server.equals("127.0.0.1")) {
			try {
				boolean ok = false;
				int tries = 5;
				while (tries-- > 0 && !ok) {
					try {
						startServer(port);
						port.setup();
						ok = true;
					} catch (ConnectException e) {
						// port probably busy, server not opened
						Log.println("could not connect to port " + port.serverPort + " : " + e.getMessage());
						try {
							port.close();
						} catch (Throwable ignore) {}
						try {
							killServer(port.serverPort);
						} catch (Throwable ignore) {}
						port.serverPort = serverPort_g++;
					}
				}
				port.isOpen = ok;
			} catch (UnknownHostException e) {
				throw new IOException(e);
			}
		} else {
			port.setup();
		}
		return port;
	}
	
	String preprocessPortName(String portname) {
		return portname;
	}
	
	void setup() throws UnknownHostException, IOException {
		// open control channel socket
		sCtrl = new Socket(server, serverPort);
		ctrlInStr = sCtrl.getInputStream();
		ctrlIn = new BufferedReader(new InputStreamReader(ctrlInStr));
		ctrlOut = new DataOutputStream(sCtrl.getOutputStream());
		String[] res;
		// open device
		res = controlCommand(true, "O " + serialport, 0);
		// get control channel index
		res = controlCommand(true, "I", 1);
		int ctrlIndex = Integer.parseInt(res[0]);
		
		// open data channel socket
		sData = new Socket(server, serverPort);
		//sData.setSendBufferSize(128);
		//sData.setReceiveBufferSize(128);
		dataIn = sData.getInputStream();
		dataOut = sData.getOutputStream();
		dataCIn = new BufferedReader(new InputStreamReader(dataIn));
		dataCOut = new DataOutputStream(dataOut);
		// attach channel to control channel, make data channel
		res = controlCommand(false, "A " + ctrlIndex, 0);
	}

	public void configure(int baud, int databits, int parity, int stopbits,
			boolean hardwareHandshake, boolean xonxoff, boolean modemControl, long timeout)
			throws IOException {
		sData.setSoTimeout((int)timeout + ((timeout > 0) ? 100 : 0));
		timeout /= 100;
		String command = "U"
			+ " B" + baud 
			+ " D" + databits
			+ " S" + stopbits
			+ " P" + (parity == PARITY_NONE ? 'n' : (parity == PARITY_EVEN ? 'e' : 'o'))
			+ " T" + timeout
			+ " M" + (timeout == 0 ? '1' : '0');
		controlCommand(true, command, 0);
	}
	

	public void configureTimeout(long timeout)
			throws IOException {
		sData.setSoTimeout((int)timeout + ((timeout > 0) ? 100 : 0));
		controlCommand(true, "U T" + timeout
			+ " M" + (timeout == 0 ? '1' : '0'), 0);
	}
	
	public void setRTSDTR(boolean rtshigh, boolean dtrhigh) throws IOException {
		controlCommand(true, "U r" + (rtshigh ? '1' : '0')
				+ " d" + (dtrhigh ? '1' : '0'), 0);
	}

	String[] controlCommand(boolean ctrl, String s, int result) throws IOException {
		DataOutputStream out = ctrl ? ctrlOut : dataCOut;
		out.writeBytes(s);
		out.write('\n');
		out.flush();
		String[] res = new String[result];
		for (int i = 0; i < result; i++) {
			res[i] = controlRead(ctrl);
		}
		String q;
		if (!((q = controlRead(ctrl)).equals("OK"))) {
			throw new IOException("Expected OK but got " + q); 
		}
		return res;
	}
	
	String controlRead(boolean ctrl) throws IOException {
		String s = ctrl ? ctrlIn.readLine() : dataCIn.readLine();
		if (s.startsWith("ERROR")) {
			throw new IOException(s);
		}
		return s;
	}
	

	public InputStream openInputStream() throws IOException {
		return dataIn;
	}

	public OutputStream openOutputStream() throws IOException {
		return dataOut;
	}

	public void close() throws IOException {
		isOpen = false;
		try {
			controlCommand(true, "C", 0);
		} catch (Throwable ignore) {}
		AppSystem.closeSilently(dataIn);
		AppSystem.closeSilently(dataOut);
		AppSystem.closeSilently(ctrlInStr);
		AppSystem.closeSilently(ctrlOut);
		sCtrl.close();
		sData.close();
	}
	
	protected boolean isOpen() {
		return isOpen;
	}

	
	protected static void startServer(UARTSocket uartSocket) {
		final int port = uartSocket.serverPort;
		if (!shutdownHookAdded) {
			Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
				public void run() {
					killServer(port);
				}
			}));
			shutdownHookAdded = true;
		}
		try {
			uartSocket.checkBinary(uartSocket.getBinFile());
			if (serverProcess == null) {
				Log.println("trying port " + port);
				String cmd = uartSocket.getBinFile().getAbsolutePath() + " " + port;
				Log.println(cmd);
				serverProcess = Runtime.getRuntime().exec(cmd);
//				new Thread(new Runnable() {
//
//					@Override
//					public void run() {
//						InputStream i = serverProcess.getInputStream();
//						int c ;
//						try {
//							while ((c = i.read()) != -1) {
//								System.out.print((char)c);
//							}
//						} catch (IOException e) {
//							// TODO Auto-generated catch block
//							e.printStackTrace();
//						}
//						System.out.println("process thread closed");
//					}
//				}).start();
				validateRunningProcess(serverProcess);
			}
		} catch (IOException ignore) {
		} catch (InterruptedException ignore) {
		}
	}
	static boolean validateRunningProcess(Process p) {
		try {
			int e = p.exitValue(); // throws ex if running
			Log.println("Process ended: exit code " + e);
			return false;
		} catch (IllegalThreadStateException itse) {
			// it is running 
			return true;
		}
	}
	static void killServer(int serverPort) {
		Socket sCtrl = null;
		try {
			try {
				Log.println("issuing server close");
				sCtrl = new Socket("localhost", serverPort);
				OutputStream out = sCtrl.getOutputStream();
				out.write("X\n".getBytes());
				out.flush();
				out.close();
			} catch (UnknownHostException ignore) {
			} catch (IOException ignore) {}
			if (serverProcess != null) {
				Log.println("give server some time to die");
				AppSystem.sleep(400);
				if (validateRunningProcess(serverProcess)) {
					Log.println("server not yet closed, killing");
					serverProcess.destroy();
					AppSystem.sleep(100);
				}
				Log.println("server dead: " + !validateRunningProcess(serverProcess));
			}
		} finally {
			if (sCtrl != null) {
				try {
					sCtrl.close();
				} catch (Throwable ignore) {}
			}
		}
		if (serverProcess != null) {
			serverProcess.destroy();
			serverProcess = null;
		}
	}
	
	abstract void checkBinary(File exe) throws IOException, InterruptedException;

	abstract File getBinFile();
}
