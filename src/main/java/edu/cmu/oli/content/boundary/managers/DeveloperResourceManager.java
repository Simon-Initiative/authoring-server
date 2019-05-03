package edu.cmu.oli.content.boundary.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import edu.cmu.oli.content.ResourceException;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import edu.cmu.oli.content.security.*;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.ws.rs.core.Response;
import java.util.*;

import static edu.cmu.oli.content.security.Roles.ADMIN;
import static edu.cmu.oli.content.security.Roles.CONTENT_DEVELOPER;

/**
 * @author Raphael Gachuhi
 */
@Stateless
public class DeveloperResourceManager {

    @Inject
    @Logging
    Logger log;

    @PersistenceContext
    EntityManager em;

    @Inject
    @Secure
    AppSecurityController securityManager;

    public JsonElement all(AppSecurityContext session, String packageId) {
        securityManager.authorize(session,
                Arrays.asList(ADMIN, CONTENT_DEVELOPER),
                packageId, "name=" + packageId, Arrays.asList(Scopes.VIEW_MATERIAL_ACTION));
        List<UserInfo> allUsers = securityManager.getAllUsers();
        allUsers.sort((o1, o2) -> {
            Map<String, List<String>> attributes = o1.getAttributes();
            Boolean isDev01 = attributes != null && attributes.containsKey(packageId);
            attributes = o2.getAttributes();
            Boolean isDev02 = attributes != null && attributes.containsKey(packageId);
            return isDev02.compareTo(isDev01);
        });
        JsonArray users = new JsonArray();
        for (UserInfo ur : allUsers) {
            if (ur.getServiceAccountClientId() != null || ur.getUsername().equalsIgnoreCase("manager")) {
                continue;
            }
            Map<String, List<String>> attributes = ur.getAttributes();
            JsonObject userOb = new JsonObject();
            userOb.addProperty("userName", ur.getUsername());
            userOb.addProperty("firstName", ur.getFirstName());
            userOb.addProperty("lastName", ur.getLastName());
            userOb.addProperty("email", ur.getEmail());
            userOb.addProperty("isDeveloper", attributes != null && attributes.containsKey(packageId));
            users.add(userOb);
        }
        return users;
    }

    public JsonElement registration(AppSecurityContext session, String packageId, String action, JsonArray users) {
        securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER),
                packageId, "name=" + packageId, Arrays.asList(Scopes.EDIT_MATERIAL_ACTION));
        ContentPackage contentPackage = findContentPackage(packageId);

        Map<String, List<String>> userPermission = new HashMap<>();
        userPermission.put(contentPackage.getGuid(), Arrays.asList(Scopes.VIEW_MATERIAL_ACTION.toString(),
                Scopes.EDIT_MATERIAL_ACTION.toString(), "ContentPackage", contentPackage.getTitle()));

        Set<String> userNames = new HashSet<>();
        users.forEach(val -> userNames.add(val.getAsString()));

        Set<UserInfo> userRepresentations;

        if (action.equalsIgnoreCase("add")) {
            userNames.forEach(val -> securityManager.addUserRoles(val, new HashSet<>(Arrays.asList(CONTENT_DEVELOPER))));
            userRepresentations = securityManager.updateUsersAttributes(userNames, userPermission, null);
        } else {
            userRepresentations = securityManager.updateUsersAttributes(userNames, null, userPermission.keySet());
        }
        List<UserInfo> userList = new ArrayList<>(userRepresentations);
        userList.sort((o1, o2) -> {
            Map<String, List<String>> attributes = o1.getAttributes();
            Boolean isDev01 = attributes != null && attributes.containsKey(packageId);
            attributes = o2.getAttributes();
            Boolean isDev02 = attributes != null && attributes.containsKey(packageId);
            return isDev02.compareTo(isDev01);
        });
        JsonArray registeredUsers = new JsonArray();
        for (UserInfo ur : userList) {
            if (ur.getServiceAccountClientId() != null || ur.getUsername().equalsIgnoreCase("manager")) {
                continue;
            }
            Map<String, List<String>> attributes = ur.getAttributes();
            JsonObject userOb = new JsonObject();
            userOb.addProperty("userName", ur.getUsername());
            userOb.addProperty("firstName", ur.getFirstName());
            userOb.addProperty("lastName", ur.getLastName());
            userOb.addProperty("email", ur.getEmail());
            userOb.addProperty("isDeveloper", attributes != null && attributes.containsKey(packageId));
            registeredUsers.add(userOb);
        }
        return registeredUsers;
    }

    private ContentPackage findContentPackage(String packageId) {
        ContentPackage contentPackage = null;
        try {
            contentPackage = em.find(ContentPackage.class, packageId);
            if (contentPackage == null) {
                String message = "Error: package requested was not found " + packageId;
                log.error(message);
                throw new ResourceException(Response.Status.NOT_FOUND, packageId, message);
            }
        } catch (IllegalArgumentException e) {
            String message = "Server Error while locating package " + packageId;
            log.error(message);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, packageId, message);
        }
        return contentPackage;
    }

}
