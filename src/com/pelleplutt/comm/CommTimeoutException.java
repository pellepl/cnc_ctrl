package com.pelleplutt.comm;

import java.io.IOException;

public class CommTimeoutException extends IOException {
	private static final long serialVersionUID = 3545413790068719084L;

	public CommTimeoutException() {
	}

	public CommTimeoutException(String message) {
		super(message);
	}

	public CommTimeoutException(Throwable cause) {
		super(cause);
	}

	public CommTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}
}
