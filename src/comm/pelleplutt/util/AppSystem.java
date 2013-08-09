package comm.pelleplutt.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.LinkedList;
import java.util.List;

/**
 * General system functionality utilities.
 * 
 * @author petera
 */
public class AppSystem {
	static List<Disposable> disposables = new LinkedList<Disposable>();
	
	/**
	 * Copies an application resource (from jar) to given local destination.
	 * 
	 * @param path
	 *            application resource path
	 * @param dest
	 *            local file destination
	 * @throws IOException
	 */
	public static void copyAppResource(String path, File dest)
			throws IOException {
		InputStream is = null;
		OutputStream os = null;
		try {
			is = Thread.currentThread().getContextClassLoader()
					.getResourceAsStream(path);
			if (dest.exists()) {
				dest.delete();
			}
			dest.getParentFile().mkdirs();
			dest.createNewFile();
			os = new FileOutputStream(dest);
			copyStreams(is, os);
		} finally {
			closeSilently(is);
			closeSilently(os);
		}
	}

	/**
	 * Copies the data provided in inputstream to the outputstream.
	 * 
	 * @param is
	 * @param os
	 * @throws IOException
	 */
	public static void copyStreams(InputStream is, OutputStream os)
			throws IOException {
		byte[] tmp = new byte[8 * 1024];
		int len;
		while ((len = is.read(tmp)) != -1) {
			os.write(tmp, 0, len);
			os.flush();
		}
	}

	/**
	 * Nullchecks and closes an inputstream and discards any ioexceptions
	 * 
	 * @param is
	 */
	public static void closeSilently(InputStream is) {
		try {
			if (is != null) {
				is.close();
			}
		} catch (IOException ignore) {
		}
	}

	/**
	 * Nullchecks and closes an outputstream and discards any ioexceptions
	 * 
	 * @param os
	 */
	public static void closeSilently(OutputStream os) {
		try {
			if (os != null) {
				os.close();
			}
		} catch (IOException ignore) {
		}
	}

	/**
	 * Runs given command as a process. Conditionally catches stdout and err and
	 * returns result on exit. Blocks until finished.
	 * 
	 * @param cmd
	 *            the command to run
	 * @param envp
	 * @param execDir
	 * @param getOut
	 *            true to catch stdout, false to ignore
	 * @param getErr
	 *            true to catch err, false to ignore
	 * @return a structure with the processes information.
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static ProcessResult run(String cmd, String[] envp, File execDir,
			boolean getOut, boolean getErr) throws IOException,
			InterruptedException {
		Process p = Runtime.getRuntime().exec(cmd, envp, execDir);
		InputStream out = null;
		InputStream err = null;
		if (getOut) {
			out = new BufferedInputStream(p.getInputStream());
		}
		if (getErr) {
			err = new BufferedInputStream(p.getErrorStream());
		}
		int code = p.waitFor();
		String serr = "";
		String sout = "";
		if (getErr) {
			byte[] errb = new byte[err.available()];
			err.read(errb);
			serr = new String(errb);
		}
		if (getOut) {
			byte[] outb = new byte[out.available()];
			out.read(outb);
			sout = new String(outb);
		}
		return new ProcessResult(code, sout, serr);
	}

	static public class ProcessResult {
		public final int code;
		public final String output;
		public final String err;

		public ProcessResult(int code, String output, String err) {
			this.code = code;
			this.output = output;
			this.err = err;
		}
	}

	/**
	 * Sleeps given milliseconds. Ignores interruptedexceptions.
	 * 
	 * @param i
	 */
	public static void sleep(int i) {
		try {
			Thread.sleep(i);
		} catch (InterruptedException ignore) {
		}
	}

	/**
	 * Nullchecks and closes a reader and discards any ioexceptions.
	 * 
	 * @param r
	 */
	public static void closeSilently(Reader r) {
		try {
			if (r != null) {
				r.close();
			}
		} catch (IOException e) {
		}
	}

  public static byte[] subByteArray(byte[] data, int offset, int len) {
    byte[] data2 = new byte[len];
    System.arraycopy(data, offset, data2, 0, len);
    return data2;
  }
  
  public synchronized static void addDisposable(Disposable d) {
	  disposables.add(d);
  }
  
  public synchronized static void dispose() {
	  while (!disposables.isEmpty()) {
		  disposables.get(0).dispose();
	  }
  }

  public synchronized static void removeDisposable(Disposable d) {
	disposables.remove(d);
  }

  public static void waitSilently(Object l, long ms) {
		try {
			l.wait(ms);
		} catch (InterruptedException e) {
		}
	}
  public static void waitSilently(Object l) {
		try {
			l.wait();
		} catch (InterruptedException e) {
		}
	}
}
