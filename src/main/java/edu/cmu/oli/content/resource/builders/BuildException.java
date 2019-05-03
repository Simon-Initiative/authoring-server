package edu.cmu.oli.content.resource.builders;

/**
 * @author Raphael Gachuhi
 */
public class BuildException extends Exception {

    public BuildException(String message) {
        super(message);
    }

    public BuildException(String message, Throwable cause) {
        super(message, cause);
    }
}
