package edu.cmu.oli.content.models;

import com.google.gson.JsonElement;
import com.google.gson.annotations.Expose;

import java.io.Serializable;

import static edu.cmu.oli.content.boundary.ResourceChangeEvent.ResourceEventType;

/**
 * @author Raphael Gachuhi
 */
public class ChangePayload implements Serializable {

    @Expose()
    public String resourceId;
    @Expose()
    public ResourceEventType changeInfo;
    @Expose()
    public JsonElement payload;

    public ChangePayload() {
    }

    public ChangePayload(String resourceId) {
        this.resourceId = resourceId;
    }

}
