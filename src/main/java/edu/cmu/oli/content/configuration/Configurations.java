package edu.cmu.oli.content.configuration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Class which holds the configuration options for content-service
 *
 * @author Raphael Gachuhi
 */
public class Configurations {

    private Map<String, JsonObject> resourceTypes = new HashMap<>();
    private boolean contentServiceDebugEnabled;
    private String contentSourceXml;
    private String contentVolume;
    private String webContentVolume;
    private String themesRepository;
    private int transactionRetrys = 3;
    private int editLockMaxDuration = 300000;
    private JsonObject developerAdmin;
    private JsonObject namespaces;
    private JsonArray themes;
    private JsonArray previewServers;
    private String emailServer;
    private Set<String> deployRequestEmails = new HashSet<>();
    private String emailServerToken;
    private String emailFrom;

    public Configurations() {
    }

    public Map<String, JsonObject> getResourceTypes() {
        return resourceTypes;
    }

    public void setResourceTypes(Map<String, JsonObject> resourceTypes) {
        this.resourceTypes = resourceTypes;
    }

    public String getContentSourceXml() {
        return contentSourceXml;
    }

    public void setContentSourceXml(String contentSourceXml) {
        this.contentSourceXml = contentSourceXml;
    }

    public String getContentVolume() {
        return contentVolume;
    }

    public void setContentVolume(String contentVolume) {
        this.contentVolume = contentVolume;
    }

    public String getWebContentVolume() {
        return webContentVolume;
    }

    public void setWebContentVolume(String webContentVolume) {
        this.webContentVolume = webContentVolume;
    }

    public int getTransactionRetrys() {
        return transactionRetrys;
    }

    public void setTransactionRetrys(int transactionRetrys) {
        this.transactionRetrys = transactionRetrys;
    }

    public int getEditLockMaxDuration() {
        return editLockMaxDuration;
    }

    public void setEditLockMaxDuration(int editLockMaxDuration) {
        this.editLockMaxDuration = editLockMaxDuration;
    }

    public JsonObject getDeveloperAdmin() {
        return developerAdmin;
    }

    public void setDeveloperAdmin(JsonObject developerAdmin) {
        this.developerAdmin = developerAdmin;
    }

    public void addResourceType(String id, JsonObject resourceType) {
        resourceTypes.put(id, resourceType);
    }

    public JsonObject getResourceTypeById(String id) {
        return resourceTypes.get(id);
    }

    public JsonObject getNamespaces() {
        return namespaces;
    }

    public void setNamespaces(JsonObject namespaces) {
        this.namespaces = namespaces;
    }

    public boolean isContentServiceDebugEnabled() {
        return contentServiceDebugEnabled;
    }

    public void setContentServiceDebugEnabled(boolean contentServiceDebugEnabled) {
        this.contentServiceDebugEnabled = contentServiceDebugEnabled;
    }

    public String getThemesRepository() {
        return themesRepository;
    }

    public void setThemesRepository(String themesRepository) {
        this.themesRepository = themesRepository;
    }

    public JsonArray getThemes() {
        return themes;
    }

    public void setThemes(JsonArray themes) {
        this.themes = themes;
    }

    public JsonArray getPreviewServers() {
        return previewServers;
    }

    public void setPreviewServers(JsonArray previewServers) {
        this.previewServers = previewServers;
    }

    public String getEmailServer() {
        return emailServer;
    }

    public void setEmailServer(String emailServer) {
        this.emailServer = emailServer;
    }

    public Set<String> getDeployRequestEmails() {
        return deployRequestEmails;
    }

    public void setDeployRequestEmails(Set<String> deployRequestEmails) {
        this.deployRequestEmails = deployRequestEmails;
    }

    public void addDeployRequestEmail(String email) {
        this.deployRequestEmails.add(email);
    }

    public void setEmailServerToken(String token) {
        this.emailServerToken = token;
    }

    public void setEmailFrom(String fromEmail) {
        this.emailFrom = fromEmail;
    }
    public String getEmailServerToken() {
        return emailServerToken;
    }

    public String getEmailFrom() {
        return emailFrom;
    }
}
