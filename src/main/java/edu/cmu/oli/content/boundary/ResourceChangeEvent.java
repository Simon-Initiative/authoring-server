package edu.cmu.oli.content.boundary;

import com.google.gson.JsonElement;

import java.io.Serializable;

/**
 * @author Raphael Gachuhi
 */
public class ResourceChangeEvent implements Serializable {

    public enum ResourceEventType {
        RESOURCE_REQUESTED,
        RESOURCE_CREATED,
        RESOURCE_UPDATED,
        RESOURCE_DELETED
    }

    private String resourceId;

    private ResourceEventType changeInfo;

    private JsonElement eventPayload;

    public ResourceChangeEvent(String resourceId, ResourceEventType changeInfo) {
        this.resourceId = resourceId;
        this.changeInfo = changeInfo;
    }

    public ResourceChangeEvent(String resourceId, ResourceEventType changeInfo, JsonElement eventPayload) {
        this.resourceId = resourceId;
        this.changeInfo = changeInfo;
        this.eventPayload = eventPayload;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public ResourceEventType getChangeInfo() {
        return changeInfo;
    }

    public void setChangeInfo(ResourceEventType changeInfo) {
        this.changeInfo = changeInfo;
    }

    public JsonElement getEventPayload() {
        return eventPayload;
    }

    public void setEventPayload(JsonElement eventPayload) {
        this.eventPayload = eventPayload;
    }
}
