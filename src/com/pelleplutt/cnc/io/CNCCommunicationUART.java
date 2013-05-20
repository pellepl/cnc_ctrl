package com.pelleplutt.cnc.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.pelleplutt.comm.Comm;
import com.pelleplutt.comm.CommArgument;
import com.pelleplutt.comm.CommRxer;
import com.pelleplutt.comm.CommTimeoutException;
import com.pelleplutt.comm.CommTxer;
import com.pelleplutt.io.Port;
import com.pelleplutt.io.PortConnector;
import com.pelleplutt.util.Log;

public class CNCCommunicationUART extends CNCCommunication {
  PortConnector connector;
  String portString;
  int baud;
  InputStream in;
  OutputStream out;
  SerialHandler rxtx;

  public void connectImpl(String port, int baud)
      throws Exception {
    if (port.startsWith("tty")) {
      port = "/dev/" + port;
    }
    portString = port;
    Port portSetting = new Port();
    portSetting.baud = baud;
    portSetting.databits = Port.BYTESIZE_8;
    portSetting.parity = Port.PARITY_NO;
    portSetting.portName = portString;
    portSetting.stopbits = Port.STOPBIT_ONE;
    portSetting.uiName = "Stewie";
    connector = PortConnector.getPortConnector();
    connector.setTimeout(7000);
    connector.connect(portSetting);
    in = connector.getInputStream();
    out = connector.getOutputStream();

    rxtx = new SerialHandler();
  }
  
  protected CommRxer createRxer() {
    return rxtx;
  }

  protected CommTxer createTxer() {
    return rxtx;
  }
  
  protected void disconnectMedia() throws IOException {
    connector.disconnect();
  }

  class SerialHandler implements CommRxer, CommTxer {
    @Override
    public int tx(int i) {
      try {
        signalTx();
        out.write(i);
      } catch (IOException ioe) {
        Log.printStackTrace(ioe);
        fatalError();
        return Comm.R_COMM_PHY_FAIL;
      }
      return Comm.R_COMM_OK;
    }

    @Override
    public int flush(CommArgument tx) {
      try {
        out.flush();
      } catch (IOException e) {
        Log.printStackTrace(e);
        fatalError();
        return Comm.R_COMM_PHY_FAIL;
      }
      return Comm.R_COMM_OK;
    }

    @Override
    public int rx() throws CommTimeoutException {
      try {
        int c = in.read();
        signalRx();
        return c;
      } catch (IOException ioe) {
        Log.printStackTrace(ioe);
        fatalError();
      }
      return -1;
    }
  }
}
