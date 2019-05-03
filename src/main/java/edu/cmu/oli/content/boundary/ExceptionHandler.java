package edu.cmu.oli.content.boundary;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.ResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author Raphael Gachuhi
 */
public class ExceptionHandler {

    private ExceptionHandler() {
    }

    static Logger log = LoggerFactory.getLogger(ExceptionHandler.class);

    /**
     * Catch all for errors that bubble up to the api layer
     *
     * @param t
     * @return
     */
    public static Response handleExceptions(Throwable t) {
        Response.Status status = Response.Status.INTERNAL_SERVER_ERROR;
        t = getRootThrowable(t);
        String message = t.getLocalizedMessage();
        log.debug(message);
        if (t instanceof ResourceException) {
            status = ((ResourceException) t).getStatus() == null ? Response.Status.NOT_FOUND
                    : ((ResourceException) t).getStatus();
        }

        return errorResponse(message, status);
    }

    public static Response errorResponse(String message, Response.Status status) {
        JsonElement errorMessage = null;
        try {
            JsonParser jsonParser = new JsonParser();
            errorMessage = jsonParser.parse(message);
        } catch (Exception e) {
        }

        if (errorMessage == null || errorMessage.isJsonPrimitive()) {
            JsonObject jsonMessage = new JsonObject();
            jsonMessage.addProperty("message", message);
            errorMessage = jsonMessage;
        }
        return Response.status(status).entity(AppUtils.gsonBuilder().create().toJson(errorMessage))
                .type(MediaType.APPLICATION_JSON).build();
    }

    public static Throwable getRootThrowable(Throwable t) {
        if (t.getCause() != null) {
            return getRootThrowable(t.getCause());
        }
        return t;
    }

    public static boolean checkForDBLockExceptions(Throwable t) {
        if (t instanceof org.hibernate.StaleStateException
                || t instanceof org.hibernate.exception.LockAcquisitionException) {
            return true;
        }
        if (t.getCause() != null) {
            return checkForDBLockExceptions(t.getCause());
        }
        return false;
    }
}
