package dev.agenttrace.ingest.jev;

/** Never include the Authorization header or api key in this exception's message. */
public class JevException extends RuntimeException {

	public JevException(String message) {
		super(message);
	}

	public JevException(String message, Throwable cause) {
		super(message, cause);
	}

}
