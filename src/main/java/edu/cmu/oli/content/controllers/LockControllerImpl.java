package edu.cmu.oli.content.controllers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.ResourceException;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.ResourceEditLock;
import edu.cmu.oli.content.security.AppSecurityContext;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static javax.ws.rs.core.Response.Status.FORBIDDEN;

/**
 * @author Raphael Gachuhi
 */
@ApplicationScoped
public class LockControllerImpl implements LockController {

    @Inject
    @Logging
    Logger log;

    @Inject
    @ConfigurationCache
    Instance<Configurations> configuration;

    private Map<String, ResourceEditLock> resourceLocks;

    @PostConstruct
    void init() {
        resourceLocks = new ConcurrentHashMap<>();
    }

    @Override
    public ResourceEditLock aquire(AppSecurityContext session, String resourceId) {
        
        ResourceEditLock resourceEditLock = resourceLocks.get(resourceId);

        // Update the lock if either:
        //
        // 1. One does not exist for this resource
        // 2. The user name of an existing lock matches the current user name
        // 3. The lock has expired
        if (resourceEditLock == null
            || resourceEditLock.getLockedBy().equalsIgnoreCase(session.getPreferredUsername())
            || System.currentTimeMillis() - resourceEditLock.getLockedAt() > configuration.get().getEditLockMaxDuration()) {

            resourceEditLock = new ResourceEditLock(resourceId, session.getPreferredUsername(), System.currentTimeMillis());
            resourceLocks.put(resourceId, resourceEditLock);
        }

        return resourceEditLock;
    }

    @Override
    public String release(AppSecurityContext session, String resourceId) {
        ResourceEditLock resourceEditLock = resourceLocks.get(resourceId);
        if (resourceEditLock != null && resourceEditLock.getLockedBy().equalsIgnoreCase(session.getPreferredUsername())) {
            resourceLocks.remove(resourceId);
            return "ResourceEditLock Released";
        } else {
            return "ResourceEditLock does not exist";
        }
    }

    @Override
    public void removeLock(String resourceguid){
        resourceLocks.remove(resourceguid);
    }

    @Override
    public String status(String resourceId) {
        ResourceEditLock resourceEditLock = resourceLocks.get(resourceId);
        // if resourceEditLock has expired create new one
        if (resourceEditLock == null) {
            return "NONE";
        } else if (System.currentTimeMillis() - resourceEditLock.getLockedAt() > configuration.get().getEditLockMaxDuration()) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }

    @Override
    public ResourceEditLock getLockForResource(AppSecurityContext session, String resourceId, boolean doCreate) {
        ResourceEditLock resourceEditLock = resourceLocks.get(resourceId);
        // if resourceEditLock has expired create new one
        if (resourceEditLock == null || doCreate) {
            resourceEditLock = new ResourceEditLock(resourceId, session.getPreferredUsername(), System.currentTimeMillis());
            resourceLocks.put(resourceId, resourceEditLock);
        }
        return resourceEditLock;
    }

    @Override
    public JsonElement getJsonLockForResource(AppSecurityContext session, String resourceId, boolean doCreate) {
        ResourceEditLock resourceEditLock = getLockForResource(session, resourceId, doCreate);
        Gson gson = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();
        return gson.toJsonTree(resourceEditLock);
    }

    @Override
    public void checkLockPermission(AppSecurityContext session, String resourceId, boolean update) {
        ResourceEditLock resourceEditLock = resourceLocks.get(resourceId);
        // if resourceEditLock has expired create new one
        if (resourceEditLock == null) {
            String message = "Not Authorized - resourceEditLock does not exist";
            throw new ResourceException(FORBIDDEN, null, message);
        }
        if (!resourceEditLock.getLockedBy().equalsIgnoreCase(session.getPreferredUsername())) {
            String message = "Not Authorized - ResourceEditLock user not the same";
            throw new ResourceException(FORBIDDEN, null, message);
        }
        if (update) {
            resourceLocks.put(resourceId, resourceEditLock.withUpdatedLockedAt(System.currentTimeMillis()));
        }
    }

}
