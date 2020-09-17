package edu.cmu.oli.content.models.persistance;

import com.google.gson.*;
import edu.cmu.oli.content.AppUtils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

/**
 * @author Raphael Gachuhi
 */
public class JsonWrapper implements Externalizable {

    private static final JsonParser jp = new JsonParser();
    private static final Gson gson = AppUtils.gsonBuilder().create();
    private String jsonString;

    public JsonWrapper() {
    }

    public JsonWrapper(String string) {
        this.jsonString = string;
    }

    public JsonWrapper(JsonElement jsonObject) {
        this.jsonString = gson.toJson(jsonObject);
    }

    public JsonElement getJsonObject() {
        return jp.parse(jsonString);
    }

    public JsonElement serializeJson() {
        return jp.parse(jsonString);
    }

    public void setJsonObject(JsonElement jsonObject) {
        this.jsonString = gson.toJson(jsonObject);
    }

    public String getAsString() {
        return jsonString;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        if (this.jsonString == null) {
            this.jsonString = gson.toJson(JsonNull.INSTANCE);
        }
        out.writeObject(jsonString);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        String jsonString = (String) in.readObject();
        this.jsonString = jsonString;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonWrapper that = (JsonWrapper) o;
        return Objects.equals(this.jsonString, that.jsonString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jsonString);
    }
}
