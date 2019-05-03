package edu.cmu.oli.content.controllers;

import com.google.gson.JsonElement;
import edu.cmu.oli.content.models.ResourceEditLock;
import edu.cmu.oli.content.security.AppSecurityContext;

/**
 * @author Raphael Gachuhi
 */
public interface LockController {
    ResourceEditLock aquire(AppSecurityContext session, String resourceId);

    void removeLock(String resourceguid);

    String release(AppSecurityContext session, String resourceId);

    String status(String resourceId);

    ResourceEditLock getLockForResource(AppSecurityContext session, String resourceId, boolean doCreate);

    JsonElement getJsonLockForResource(AppSecurityContext session, String resourceId, boolean doCreate);

    void checkLockPermission(AppSecurityContext session, String resourceId, boolean update);
}
