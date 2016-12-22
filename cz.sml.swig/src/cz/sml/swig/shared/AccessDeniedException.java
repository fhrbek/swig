package cz.sml.swig.shared;

public class AccessDeniedException extends Exception {

	private static final long serialVersionUID = -8492328758359544146L;

	public AccessDeniedException() {
	}

	public AccessDeniedException(String message) {
		super(message);
	}

	public AccessDeniedException(Throwable cause) {
		super(cause);
	}

	public AccessDeniedException(String message, Throwable cause) {
		super(message, cause);
	}

}
