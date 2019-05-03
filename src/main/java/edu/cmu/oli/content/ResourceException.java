package edu.cmu.oli.content;

import javax.ws.rs.core.Response.Status;

/**
 * @author Raphael Gachuhi
 */
public class ResourceException extends RuntimeException {
    private String id;
    private Status status;

//    public ResourceException(String message, String id) {
//        super(message);
//        this.id = id;
//    }

    public ResourceException(Status status, String id, String message) {
        super(message);
        this.status = status;
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
