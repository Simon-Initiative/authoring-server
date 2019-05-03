package edu.cmu.oli.content;

/**
 * @author Raphael Gachuhi
 */
public class ContentServiceException extends Exception {

    public ContentServiceException() {
        super();
    }

    public ContentServiceException(String message) {
        super(message);
    }

    public ContentServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public ContentServiceException(Throwable cause) {
        super(cause);
    }
}
