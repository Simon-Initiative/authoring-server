package edu.cmu.oli.content;

public class InvalidDocumentException extends RuntimeException {
    public InvalidDocumentException() {
        super();
    }

    public InvalidDocumentException(String message) {
        super(message);
    }

    public InvalidDocumentException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidDocumentException(Throwable cause) {
        super(cause);
    }
}
