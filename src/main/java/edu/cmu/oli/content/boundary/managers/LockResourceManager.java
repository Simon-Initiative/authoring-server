package edu.cmu.oli.content.boundary.managers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.ResourceException;
import edu.cmu.oli.content.controllers.LockController;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.ResourceEditLock;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import edu.cmu.oli.content.security.AppSecurityContext;
import edu.cmu.oli.content.security.AppSecurityController;
import edu.cmu.oli.content.security.Scopes;
import edu.cmu.oli.content.security.Secure;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

import java.util.Arrays;

import static edu.cmu.oli.content.security.Roles.ADMIN;
import static edu.cmu.oli.content.security.Roles.CONTENT_DEVELOPER;
import javax.ws.rs.core.Response;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

/**
 * @author Raphael Gachuhi
 */
@Stateless
public class LockResourceManager {

    enum LockActions {
        AQUIRE, RELEASE, STATUS
    }

    @Inject
    @Logging
    Logger log;

    @PersistenceContext
    EntityManager em;

    @Inject
    @Secure
    AppSecurityController securityManager;

    @Inject
    LockController lockController;

    public JsonElement lock(AppSecurityContext session, String packageIdOrGuid, String resourceId, String action) {
        ContentPackage contentPackage = findContentPackage(packageIdOrGuid);
        securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), contentPackage.getGuid(),
                "name=" + contentPackage.getGuid(), Arrays.asList(Scopes.VIEW_MATERIAL_ACTION));
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

    // packageIdentifier is db guid or packageId-version combo
    private ContentPackage findContentPackage(String packageIdOrGuid) {
        ContentPackage contentPackage = null;
        Boolean isIdAndVersion = packageIdOrGuid.contains("-");
        try {
            if (isIdAndVersion) {
                String pkgId = packageIdOrGuid.substring(0, packageIdOrGuid.lastIndexOf("-"));
                String version = packageIdOrGuid.substring(packageIdOrGuid.lastIndexOf("-") + 1);
                TypedQuery<ContentPackage> q = em
                        .createNamedQuery("ContentPackage.findByIdAndVersion", ContentPackage.class)
                        .setParameter("id", pkgId).setParameter("version", version);

                contentPackage = q.getResultList().isEmpty() ? null : q.getResultList().get(0);
            } else {
                String packageGuid = packageIdOrGuid;
                contentPackage = em.find(ContentPackage.class, packageGuid);
            }

            if (contentPackage == null) {
                String message = "Error: package requested was not found " + packageIdOrGuid;
                log.error(message);
                throw new ResourceException(Response.Status.NOT_FOUND, packageIdOrGuid, message);
            }

        } catch (IllegalArgumentException e) {
            String message = "Server Error while locating package " + packageIdOrGuid;
            log.error(message);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, packageIdOrGuid, message);
        }
        return contentPackage;
    }
}
