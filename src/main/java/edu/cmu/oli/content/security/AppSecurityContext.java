package edu.cmu.oli.content.security;

import java.util.Set;

/**
 * @author Raphael Gachuhi
 */
public class AppSecurityContext {

    private String tokenString;
    private String preferredUsername;
    private String email;
    private String firstName;
    private String lastName;
    private Set<String> realmRoles;

    public AppSecurityContext(String tokenString, String preferredUsername, String firstName, String lastName,
                              String email, Set<String> realmRoles) {
        this.tokenString = tokenString;
        this.preferredUsername = preferredUsername;
        this.realmRoles = realmRoles;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    public Set<String> getRealmRoles() {
        return realmRoles;
    }

    public String getTokenString() {
        return tokenString;
    }


    public String getPreferredUsername() {
        return preferredUsername;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }
}
