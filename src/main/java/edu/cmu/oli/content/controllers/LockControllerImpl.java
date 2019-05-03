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
import java.util.Timer;
import java.util.TimerTask;
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

    // :FIXME: avoid use of global state, not good for horizontal scaling. Switch to using a distributed cache
    private Map<String, ResourceEditLock> resourceLocks;

    @PostConstruct
    void init() {
        resourceLocks = new ConcurrentHashMap<>();
        long sleepTimeInMilli = 1000L * 60 * 5; // 5 minutes
        Timer timer = new Timer(true);
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                lockListCleanup();
            }
        };
        timer.scheduleAtFixedRate(timerTask, sleepTimeInMilli, sleepTimeInMilli);
    }

    @Override
    public ResourceEditLock aquire(AppSecurityContext session, String resourceId) {
        ResourceEditLock resourceEditLock = resourceLocks.get(resourceId);
        // if resourceEditLock has expired create new one
        if (resourceEditLock != null) {
            if (System.currentTimeMillis() - resourceEditLock.getLockedAt() > configuration.get().getEditLockMaxDuration()) {
                //Old ResourceEditLock has expired
                resourceEditLock = new ResourceEditLock(configuration.get().getEditLockMaxDuration(), resourceId, session.getPreferredUsername(), System.currentTimeMillis());
                resourceLocks.put(resourceId, resourceEditLock);
            }
        } else {
            resourceEditLock = new ResourceEditLock(configuration.get().getEditLockMaxDuration(), resourceId, session.getPreferredUsername(), System.currentTimeMillis());
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
            resourceEditLock = new ResourceEditLock(configuration.get().getEditLockMaxDuration(), resourceId, session.getPreferredUsername(), System.currentTimeMillis());
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

    private void lockListCleanup() {
        resourceLocks.forEach((key, value) -> {
            long timeDiff = System.currentTimeMillis() - value.getLockedAt();
            if (timeDiff > configuration.get().getEditLockMaxDuration()) {
                log.debug("lock cleanup time diff " + timeDiff + " lock" + value.toString());
                resourceLocks.remove(key, value);
            }
        });
    }
}
