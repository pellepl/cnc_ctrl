package com.pelleplutt.comm.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.pelleplutt.comm.Callback;
import com.pelleplutt.comm.Comm;
import com.pelleplutt.comm.CommArgument;
import com.pelleplutt.comm.CommRxer;
import com.pelleplutt.comm.CommTimeoutException;
import com.pelleplutt.comm.CommTxer;
import com.pelleplutt.util.HexUtil;
import comm.pelleplutt.io.Port;
import comm.pelleplutt.io.PortConnector;
import comm.pelleplutt.util.Log;

public class UartPinger {
	PortConnector connector;
	String portString;
	int baud;
	Comm comm;
	InputStream in;
	OutputStream out;
	volatile boolean running = true;
	long ping_timestamp;
	int ping_sent = 0;
	int ping_recd = 0;
	static long time = 0;
	
	public UartPinger(String portString, int baud) {
		this.portString = portString;
		this.baud = baud;
	}

	public static void main(String[] args) {
		UartPinger up = new UartPinger("/dev/ttyUSB0", 115200);
		try {
			up.start();
		} catch (Throwable t) {
			Log.printStackTrace(t);
			try {
				up.connector.disconnect();
			} catch (IOException ignore) {
			}
		} finally {
		}
	}
	
	void connect() throws Exception {
		Port portSetting = new Port();
		portSetting.baud = baud;
		portSetting.databits = Port.BYTESIZE_8;
		portSetting.parity = Port.PARITY_NO;
		portSetting.portName = portString;
		portSetting.stopbits = Port.STOPBIT_ONE;
		portSetting.uiName = "Urban";
		connector = PortConnector.getPortConnector();
		connector.setTimeout(7000);
		connector.connect(portSetting);
		in = connector.getInputStream();
		out = connector.getOutputStream();
	}

	void start() throws Exception {
		connect();
		SerialHandler rxtx = new SerialHandler();
		comm = new Comm(2, new LoggingCallback(), rxtx, rxtx);
		Thread thrRx = new Thread(new Runnable() {
			public void run() {
				Log.println("reader started");
				while (comm.phyRx() == Comm.R_COMM_OK);
				Log.println("reader ended");
			}
		}, "rxer");
		thrRx.setPriority(Thread.MAX_PRIORITY);
		thrRx.start();
		Thread thrTx = new Thread(new Runnable() {
			int del = 100;
			public void run() {
				Log.println("pinger started");
				int snopp = 0;
				while (running) {
					try {
						del--;
						double r = Math.random();
						//Thread.sleep(Math.max(del, 0) + (int)(r*50) + (r < 0.001 ? 500 : 0));
						//Thread.sleep(30);
						Thread.yield();
					} catch (Throwable e) {
					}
					if (running) {
						int res;
						byte[] msg = {(byte)0x20};
						res = comm.tx(1, msg, true);
						if (res == Comm.R_COMM_TRA_PEND_Q_FULL) {
							try {
								Thread.sleep(20);
							} catch (InterruptedException e) {
							}
							snopp--;
						} else {
							Log.println("sending msg " + HexUtil.toHex(res) + ", res: " + res);
						}
					}
				}
				Log.println("pinger ended");
				try {
					connector.disconnect();
				} catch (IOException ignore) {
				}
			}
		}, "txer");
		thrTx.setPriority(Thread.NORM_PRIORITY);
		thrTx.start();
		Thread thrTicker = new Thread(new Runnable() {
			public void run() {
				while(running) {
					try {
						Thread.sleep(20);
					} catch (Throwable t) {}
					comm.tick(time++);
				}
			}
		}, "ticker");
		thrTicker.setPriority(Thread.MIN_PRIORITY);
		thrTicker.start();
	}
	
	class SerialHandler implements CommRxer, CommTxer {
		@Override
		public int tx(int i) {
			try {
				out.write(i);
			} catch (IOException ioe) {
				Log.printStackTrace(ioe);
				running = false;
			}
			return 0;
		}

		@Override
		public int rx() throws CommTimeoutException {
			try {
				return in.read();
			} catch (IOException ioe) {
				Log.printStackTrace(ioe);
				running = false;
			}
			return -1;
		}

    @Override
    public int flush(CommArgument tx) throws IOException {
      // TODO Auto-generated method stub
      return 0;
    }
	}
	
	class LoggingCallback implements Callback {
		@Override
		public int ack(CommArgument rx, byte[] data) {
			Log.println("ack: " + Integer.toHexString(rx.seqno));
      byte[] data2 = new byte[rx.len];
      System.arraycopy(data, Comm.COMM_H_SIZE, data2, 0, rx.len);
      Log.println("" + HexUtil.formatData(data2));
			return 0;
		}

		public int rx(CommArgument rx, byte[] data) {
		  byte[] data2 = new byte[rx.len];
		  System.arraycopy(data, Comm.COMM_H_SIZE, data2, 0, rx.len);
			Log.println("rx: " +Integer.toHexString(rx.seqno));
			Log.println(HexUtil.formatData(data2));
			return 0;
		}

		@Override
		public void inf(CommArgument rx) {
			if ((rx.data[rx.dataIx] & 0xff) == Comm.COMM_TRA_INF_PONG) {
				long delta = System.currentTimeMillis() - ping_timestamp;
				//Log.println("ping roundtrip: " + delta + "ms [" + (int)(0xfff & rx.seqno) + "]");
				ping_recd++;
				//Log.println("ping tx: " + ping_sent + "   rx: " + ping_recd + " lost:" + (ping_sent - ping_recd));
			} else {
				Log.println("inf");
			}
		}

		@Override
		public void error(CommArgument a, boolean txOtherwiseRx, int error) {
			Log.println("err: " + error);
		}

		@Override
		public long getTime() {
			return time;
		}

		@Override
		public long getAndAddTime() {
			return time++;
		}

		@Override
		public void registerComm(Comm comm) {
			// nop
		}

    @Override
    public int rx(CommArgument rx, byte[] data, int offset) {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public void nodeAlert(int commAddress, int type, byte[] extra) {
      // TODO Auto-generated method stub
      
    }
	}
}
