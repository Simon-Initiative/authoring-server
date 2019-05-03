package edu.cmu.oli.content.security;

import java.util.List;
import java.util.Map;

/**
 * @author Raphael Gachuhi
 */
public class UserInfo {

    protected String id;
    protected String username;
    protected Boolean enabled;
    protected Boolean emailVerified;
    protected String firstName;
    protected String lastName;
    protected String email;
    protected String serviceAccountClientId;

    public UserInfo() {
    }

    public UserInfo(String id, String username, Boolean enabled, Boolean emailVerified, String firstName,
                    String lastName, String email, String serviceAccountClientId, Map<String, List<String>> attributes) {
        this.id = id;
        this.username = username;
        this.enabled = enabled;
        this.emailVerified = emailVerified;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.serviceAccountClientId = serviceAccountClientId;
        this.attributes = attributes;
    }

    protected Map<String, List<String>> attributes;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getServiceAccountClientId() {
        return serviceAccountClientId;
    }

    public void setServiceAccountClientId(String serviceAccountClientId) {
        this.serviceAccountClientId = serviceAccountClientId;
    }

    public Map<String, List<String>> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, List<String>> attributes) {
        this.attributes = attributes;
    }
}
