package edu.cmu.oli.content.security;

import edu.cmu.oli.content.ResourceException;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.authorization.client.resource.ProtectedResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.keycloak.representations.idm.authorization.ScopeRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.InitialContext;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;

import static edu.cmu.oli.content.security.Roles.ADMIN;

/**
 * @author Raphael Gachuhi
 */
public class KeyCloakSecurityProxy implements AppSecurityController {

    final String REALM = "oli_security";

    Logger log = LoggerFactory.getLogger(KeyCloakSecurityProxy.class);

    AuthzClient authzClient;
    RealmResource realmResource;

    @Override
    public Set<String> authorize(AppSecurityContext session, List<Roles> authRoles, String packageId, String filter, List<Scopes> authScopes) {
        Set<String> roles = new HashSet<>();
        if (authRoles != null) {
            authRoles.forEach(e -> {
                roles.add(e.toString());
            });
        }
        Set<String> scopes = null;
        if (authScopes != null) {
            scopes = new HashSet<>();
            for (Scopes scope : authScopes) {
                scopes.add(scope.toString());
            }
        }
        String accessToken = session == null ? null : session.getTokenString();
        Set<String> realmRoles = session == null ? null : session.getRealmRoles();
        if (accessToken == null || realmRoles == null) {
            String message = "Not Logged In";
            throw new ResourceException(Response.Status.FORBIDDEN, null, message);
        }
        if (Collections.disjoint(realmRoles, roles)) {
            String message = "Not Authorized";
            throw new ResourceException(Response.Status.FORBIDDEN, null, message);
        }

        // Assume Role based authorization (RBA) is desired if scopes or filters not supplied
        if (scopes == null || scopes.isEmpty() || filter == null) {
            log.info("role based");
            // Don't trust roles from browser, do a direct fetch from Keycloak server
            boolean byRole = authorizeByRole(session.getPreferredUsername(), roles);
            if (!byRole) {
                String message = "Not authorized";
                throw new ResourceException(Response.Status.FORBIDDEN, null, message);
            } else {
                return new HashSet<>();
            }
        }
        // If requested package is not permitted
        Set<String> permittedPkgs = permissionsByUserAttribs(session.getPreferredUsername(), scopes);
        if (permittedPkgs.isEmpty()) {
            String message = "Not authorized";
            throw new ResourceException(Response.Status.FORBIDDEN, null, message);
        }
        if (permittedPkgs.contains("all")) {
            return permittedPkgs;
        }

        if (packageId != null) {
            log.info("Auth with packageId");
            // Check if resource is edit locked
            if (scopes.contains(Scopes.EDIT_MATERIAL_ACTION.toString())) {
                log.info("Auth with packageId 2");
                boolean lockedUp = false;
                try {
                    ContentPackageLockLookup contentPackageLockLookup = (ContentPackageLockLookup)
                            new InitialContext().lookup("java:global/content-service/ContentPackageLockLookup");
                    lockedUp = contentPackageLockLookup.lockLookup(packageId);
                    log.info("Package Locked=" + lockedUp);

                } catch (Throwable e) {
                }
                if (lockedUp) {
                    String message = "Not authorized";
                    throw new ResourceException(Response.Status.FORBIDDEN, null, message);
                }
            }
            boolean permitted = false;
            for (String permittedPkg : permittedPkgs) {
                if (permittedPkg.contains(packageId)) {
                    permitted = true;
                    break;
                }
            }
            if (!permitted) {
                String message = "Not authorized";
                throw new ResourceException(Response.Status.FORBIDDEN, null, message);
            }
        }
        return permittedPkgs;
    }

    @Override
    public void deleteResource(String name) {
        AuthzClient authzClient = getAuthzClient();
        ProtectedResource resourceClient = authzClient.protection().resource();
        ResourceRepresentation existingResource = resourceClient.findByName(name);
        resourceClient.delete(existingResource.getId());
    }

    @Override
    public void createResource(String name, String uri, String type, List<Scopes> scopes) {
        AuthzClient authzClient = getAuthzClient();
        // create a new resource representation with the information we want
        ResourceRepresentation newResource = new ResourceRepresentation();

        newResource.setName(name);
        newResource.setType(type);
        newResource.setUris(Collections.singleton(uri));

        Set<ScopeRepresentation> collect = scopes.stream().map(scope -> new ScopeRepresentation(scope.toString())).collect(Collectors.toSet());
        newResource.setScopes(collect);

        ProtectedResource resourceClient = authzClient.protection().resource();
        ResourceRepresentation existingResource = resourceClient.findByName(newResource.getName());
        try {
            resourceClient.delete(existingResource.getId());
        } catch (Exception ex) {
        }

        // create the resource on the server
        resourceClient.create(newResource);
    }

    private boolean authorizeByRole(String userId, Set<String> roles) {
        List<UserRepresentation> users = getRealmResource().users().search(userId);
        if (users.isEmpty()) {
            String message = "Not authorized";
            throw new ResourceException(Response.Status.FORBIDDEN, null, message);
        }
        // Should always be a single user
        UserRepresentation userRepresentation = users.get(0);

        UserResource userResource = getRealmResource().users().get(userRepresentation.getId());
        RoleScopeResource roleScopeResource = userResource.roles().realmLevel();
        List<RoleRepresentation> roleRepresentations = roleScopeResource.listAll();
        for (RoleRepresentation rReps : roleRepresentations) {
            if (roles.contains(rReps.getName())) {
                return true;
            }
        }
        return false;
    }

    private Set<String> permissionsByUserAttribs(String userId, Set<String> scopes) {
        List<UserRepresentation> users = getRealmResource().users().search(userId);
        if (users.isEmpty()) {
            String message = "Not authorized";
            throw new ResourceException(Response.Status.FORBIDDEN, null, message);
        }
        // Should always be a single user
        UserRepresentation userRepresentation = users.get(0);

        UserResource userResource = getRealmResource().users().get(userRepresentation.getId());
        RoleScopeResource roleScopeResource = userResource.roles().realmLevel();
        List<RoleRepresentation> roleRepresentations = roleScopeResource.listAll();

        final boolean[] realmAdmin = {false};
        roleRepresentations.forEach((e) -> {
            if (e.getName().equalsIgnoreCase(ADMIN.toString())) {
                realmAdmin[0] = true;
            }
        });
        // Grant all permissions for 'admin' role
        if (realmAdmin[0]) {
            //  For efficiency's sake use shorthand 'all' as a flag to represent 'all permissions have been grant'
            return new HashSet<>(Arrays.asList("all"));
        }
        Set<String> resourceNames = new HashSet<>();
        Map<String, List<String>> attributes = userRepresentation.getAttributes();
        if (attributes == null) {
            return resourceNames;
        }

        for (String granted : attributes.keySet()) {
            if (!Collections.disjoint(attributes.get(granted), scopes)) {
                resourceNames.add(granted);
            }
        }
        return resourceNames;
    }

    private AuthzClient getAuthzClient() {
        if (authzClient == null) {
            try {

                String serverurl = System.getenv().get("SERVER_URL");
                String keycloakrealm = System.getenv().get("keycloakrealm");
                String keycloakresource = System.getenv().get("keycloakresource");
                String keycloaksecret = System.getenv().get("keycloaksecret");
                Map<String, Object> credentials = new HashMap<>();
                credentials.put("secret", keycloaksecret);
                Configuration configuration = new Configuration(serverurl + "/auth", keycloakrealm,
                        keycloakresource, credentials, null);
                authzClient = AuthzClient.create(configuration);
            } catch (Exception e) {
                throw new RuntimeException("Could not create authorization client.", e);
            }
        }
        return authzClient;
    }

    RealmResource getRealmResource() {
        if (realmResource == null) {
            String serverurl = System.getenv().get("SERVER_URL");
            Keycloak kc = KeycloakBuilder.builder()
                    .serverUrl(serverurl + "/auth")
                    .realm("master")
                    .username(System.getenv().get("keycloakadmin"))
                    .password(System.getenv().get("keycloakpass"))
                    .clientId("admin-cli")
                    .resteasyClient(new ResteasyClientBuilder().connectionPoolSize(10).register(new CustomJacksonProvider()).build())
                    .build();
            realmResource = kc.realm(REALM);
        }

        return realmResource;
    }

    public class CustomJacksonProvider extends ResteasyJackson2Provider {

    }

    @Override
    public void createUser(String userName, String firstName, String lastName, String email, String password,
                           Set<Roles> realmRoles) {
        List<UserRepresentation> users = getRealmResource().users().search(userName);
        if (!users.isEmpty()) {
            return;
        }

        UserRepresentation user = new UserRepresentation();
        user.setUsername(userName);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setEnabled(true);

        getRealmResource().users().create(user);

        users = getRealmResource().users().search(userName);

        UserResource userResource = getRealmResource().users().get(users.get(0).getId());

        doAddUserRoles(realmRoles, userResource);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);

        userResource.resetPassword(credential);
    }

    private void doAddUserRoles(Set<Roles> realmRoles, UserResource userResource) {
        RoleScopeResource roleScopeResource = userResource.roles().realmLevel();
        List<RoleRepresentation> roleRepresentations = roleScopeResource.listAll();
        for (RoleRepresentation rReps : roleRepresentations) {
            // Filter out roles that have already been assigned to user
            try {
                Roles roles = Roles.valueOf(rReps.getName().toUpperCase());
                if (roles != null) {
                    realmRoles.remove(roles);
                }
            } catch (Exception e) {
            }
        }

        List<RoleRepresentation> roles = getRealmResource().roles().list();
        List<RoleRepresentation> addRoles = new ArrayList<>();
        roles.forEach((r) -> {
            try {
                Roles role = Roles.valueOf(r.getName().toUpperCase());
                if (realmRoles.contains(role)) {
                    addRoles.add(r);
                }
            } catch (Exception e) {
            }

        });

        if (addRoles.isEmpty()) {
            return;
        }
        userResource.roles().realmLevel().add(addRoles);
    }

    @Override
    public void addUserRoles(String userName, Set<Roles> realmRoles) {
        List<UserRepresentation> users = getRealmResource().users().search(userName);
        if (users.isEmpty()) {
            return;
        }

        UserResource userResource = getRealmResource().users().get(users.get(0).getId());
        doAddUserRoles(realmRoles, userResource);
    }

    @Override
    public void updateUserAttributes(String userId, Map<String, List<String>> addAttributes, Set<String> removeAttributes) {
        List<UserRepresentation> users = getRealmResource().users().search(userId);
        if (users.isEmpty()) {
            return;
        }
        // Should always be a single user
        UserRepresentation userRepresentation = users.get(0);
        setUserAttributes(userRepresentation, addAttributes, removeAttributes);
    }

    @Override
    public List<UserInfo> getAllUsers() {
        UsersResource consumerUsers = getRealmResource().users();
        List<UserRepresentation> users = consumerUsers.search("", 0, consumerUsers.count());
        return users.stream().map(uRep -> {
            return new UserInfo(uRep.getId(), uRep.getUsername(), uRep.isEnabled(), uRep.isEmailVerified(), uRep.getFirstName(),
                    uRep.getLastName(), uRep.getEmail(), uRep.getServiceAccountClientId(), uRep.getAttributes());
        }).collect(Collectors.toList());
    }

    @Override
    public Set<UserInfo> updateUsersAttributes(Set<String> userIds, Map<String, List<String>> addAttributes, Set<String> removeAttributes) {
        UsersResource consumerUsers = getRealmResource().users();
        List<UserRepresentation> users = consumerUsers.search("", 0, consumerUsers.count());
        Set<UserInfo> usersProcessed = new HashSet<>();
        if (users.isEmpty()) {
            return usersProcessed;
        }

        for (UserRepresentation uRep : users) {
            if (userIds.contains(uRep.getUsername())) {
                setUserAttributes(uRep, addAttributes, removeAttributes);
                usersProcessed.add(new UserInfo(uRep.getId(), uRep.getUsername(), uRep.isEnabled(), uRep.isEmailVerified(), uRep.getFirstName(),
                        uRep.getLastName(), uRep.getEmail(), uRep.getServiceAccountClientId(), uRep.getAttributes()));
            }
        }
        return usersProcessed;
    }

    private void setUserAttributes(UserRepresentation userRepresentation, Map<String, List<String>> addAttributes, Set<String> removeAttributes) {
        Map<String, List<String>> attributes = userRepresentation.getAttributes();
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        if (addAttributes != null) {
            attributes.putAll(addAttributes);
        }
        if (removeAttributes != null) {
            for (String att : removeAttributes) {
                attributes.remove(att);
            }
        }
        userRepresentation.setAttributes(attributes);
        UserResource userResource = realmResource.users().get(userRepresentation.getId());
        userResource.update(userRepresentation);
    }

    private void removeUserAttributes(UserRepresentation userRepresentation, String searchString) {
        log.debug("Keycloak removeUserAttributes " + userRepresentation.getEmail());
        Map<String, List<String>> attributes = userRepresentation.getAttributes();
        Map<String, List<String>> attributes2 = new HashMap<>();
        if (attributes != null) {
            attributes2.putAll(attributes);
            Set<Map.Entry<String, List<String>>> entries = attributes.entrySet();
            Iterator<Map.Entry<String, List<String>>> it = entries.iterator();
            while (it.hasNext()) {
                Map.Entry<String, List<String>> next = it.next();
                next.getValue().forEach((e) -> {
                    if (e.contains(searchString)) {
                        attributes2.remove(next.getKey());
                        return;
                    }
                });
            }
        }

        userRepresentation.setAttributes(attributes2);
        UserResource userResource = realmResource.users().get(userRepresentation.getId());
        userResource.update(userRepresentation);
    }

    @Override
    public void updateAllUsersAttributes(Map<String, List<String>> addAttributes, Set<String> removeAttributes) {
        UsersResource consumerUsers = getRealmResource().users();
        List<UserRepresentation> users = consumerUsers.search("", 0, consumerUsers.count());
        for (UserRepresentation userRepresentation : users) {
            setUserAttributes(userRepresentation, addAttributes, removeAttributes);
        }
    }

    @Override
    public void clearAllUsersAttributes(String searchString) {
        UsersResource consumerUsers = getRealmResource().users();
        List<UserRepresentation> users = consumerUsers.search("", 0, consumerUsers.count());
        for (UserRepresentation userRepresentation : users) {
            removeUserAttributes(userRepresentation, searchString);
        }
    }
}
