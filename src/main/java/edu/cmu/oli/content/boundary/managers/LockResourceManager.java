package edu.cmu.oli.content.boundary.managers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.ResourceException;
import edu.cmu.oli.content.controllers.LockController;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.ResourceEditLock;
import edu.cmu.oli.content.security.AppSecurityContext;
import edu.cmu.oli.content.security.AppSecurityController;
import edu.cmu.oli.content.security.Scopes;
import edu.cmu.oli.content.security.Secure;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Arrays;

import static edu.cmu.oli.content.security.Roles.ADMIN;
import static edu.cmu.oli.content.security.Roles.CONTENT_DEVELOPER;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

/**
 * @author Raphael Gachuhi
 */
@Stateless
public class LockResourceManager {

    enum LockActions {
        AQUIRE,
        RELEASE,
        STATUS
    }

    @Inject
    @Logging
    Logger log;

    @Inject
    @Secure
    AppSecurityController securityManager;

    @Inject
    LockController lockController;

    public JsonElement lock(AppSecurityContext session, String packageId, String resourceId, String action) {
        securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER),
                packageId, "name=" + packageId, Arrays.asList(Scopes.VIEW_MATERIAL_ACTION));
        LockActions act;
        try {
            act = LockActions.valueOf(action.toUpperCase());
        } catch (Exception ex) {
            String message = "Action not supported " + action;
            throw new ResourceException(BAD_REQUEST, null, message);
        }
        switch (act) {
            case AQUIRE:
                ResourceEditLock resourceEditLock = lockController.aquire(session, resourceId);
                Gson gson = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();
                JsonElement lockJson = gson.toJsonTree(resourceEditLock);
                return lockJson;
            case RELEASE:
                return new JsonPrimitive(lockController.release(session, resourceId));
            case STATUS:
                return new JsonPrimitive(lockController.status(resourceId));
            default:
                String message = "Action not supported " + action;
                throw new ResourceException(BAD_REQUEST, null, message);
        }
    }
}
