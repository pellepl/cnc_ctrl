package comm.pelleplutt.io;

import java.io.IOException;

/**
 * @todo check against LinuxPortConnector and extract super class
 */
public class WinPortConnector extends PortConnector {
	WinSerialPortUARTSocket port;

	public String[] getDevices() {
		String[] devs =  {"COM1", "COM2", "COM3", "COM4"};
		return devs;
	}

	public void doConnect(Port portSetting) throws Exception {
		UARTSocket winUartSocket = new WinSerialPortUARTSocket();
		port = (WinSerialPortUARTSocket)WinSerialPortUARTSocket.getPort(portSetting.portName, winUartSocket);
		configure(portSetting);
		setInputStream(port.openInputStream());
		setOutputStream(port.openOutputStream());
	}

	public void doDisconnect() throws IOException {
		port.close();
	}

	@Override
	protected void doSetTimeout(long timeout) throws IOException {
		if (port != null) {
			port.configureTimeout(timeout != 0 ? (timeout + 100) : 0);
		}
	}

	@Override
	public void setRTSDTR(boolean rtshigh, boolean dtrhigh) throws IOException {
		port.setRTSDTR(rtshigh, dtrhigh);
	}
	protected void doConfigure(Port portSetting) throws IOException {
		int baud = 0;
		switch (portSetting.baud) {
		case Port.BAUD_110:
			baud = 110;
			break;
		case Port.BAUD_300:
			baud = 300;
			break;
		case Port.BAUD_600:
			baud = 600;
			break;
		case Port.BAUD_1200:
			baud = 1200;
			break;
		case Port.BAUD_2400:
			baud = 2400;
			break;
		case Port.BAUD_4800:
			baud = 4800;
			break;
		case Port.BAUD_9600:
			baud = 9600;
			break;
		case Port.BAUD_14400:
			baud = 14400;
			break;
		case Port.BAUD_19200:
			baud = 19200;
			break;
		case Port.BAUD_38400:
			baud = 38400;
			break;
		case Port.BAUD_57600:
			baud = 57600;
			break;
		case Port.BAUD_115200:
			baud = 115200;
			break;
		case Port.BAUD_128000:
			baud = 128000;
			break;
		case Port.BAUD_230400:
			baud = 230400;
			break;
		case Port.BAUD_256000:
			baud = 256000;
			break;
		case Port.BAUD_460800:
			baud = 460800;
			break;
		case Port.BAUD_921600:
			baud = 921600;
			break;
		}
		int databits = portSetting.databits;
		int stopbits = 0;
		switch (portSetting.stopbits) {
		case Port.STOPBIT_ONE:
			stopbits = LinuxSerialPortUARTSocket.STOPBITS_1;
			break;
		case Port.STOPBIT_TWO:
			stopbits = LinuxSerialPortUARTSocket.STOPBITS_2;
			break;
		}
		int parity = 0;
		switch (portSetting.parity) {
		case Port.PARITY_NO:
			parity = LinuxSerialPortUARTSocket.PARITY_NONE;
			break;
		case Port.PARITY_EVEN:
			parity = LinuxSerialPortUARTSocket.PARITY_EVEN;
			break;
		case Port.PARITY_MARK:
			parity = LinuxSerialPortUARTSocket.PARITY_NONE;
			break;
		case Port.PARITY_ODD:
			parity = LinuxSerialPortUARTSocket.PARITY_ODD;
			break;
		case Port.PARITY_SPACE:
			parity = LinuxSerialPortUARTSocket.PARITY_NONE;
			break;
		}
		port.configure(baud, databits, parity, stopbits, false, false, false,
				timeout != 0 ? (timeout + 1000) : 0);
	}

}
