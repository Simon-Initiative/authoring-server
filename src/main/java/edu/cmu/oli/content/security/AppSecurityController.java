package edu.cmu.oli.content.security;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Raphael Gachuhi
 */
public interface AppSecurityController {

    Set<String> authorize(AppSecurityContext session, List<Roles> roles, String packageId, String filter,
                          List<Scopes> scopes);

    void updateUserAttributes(String userId, Map<String, List<String>> addAttributes, Set<String> removeAttributes);

    List<UserInfo> getAllUsers();

    Set<UserInfo> updateUsersAttributes(Set<String> userIds, Map<String, List<String>> addAttributes,
                                        Set<String> removeAttributes);

    void updateAllUsersAttributes(Map<String, List<String>> addAttributes, Set<String> removeAttributes);

    void clearAllUsersAttributes(String searchString);

    void createUser(String userName, String firstName, String lastName, String email, String password,
                    Set<Roles> realmRoles);

    void addUserRoles(String userName, Set<Roles> realmRoles);

    void createResource(String name, String uri, String type, List<Scopes> scopes);

    void deleteResource(String name);
}
