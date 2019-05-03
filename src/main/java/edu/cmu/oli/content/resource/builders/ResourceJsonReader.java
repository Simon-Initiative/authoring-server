package edu.cmu.oli.content.resource.builders;

import com.google.gson.*;
import edu.cmu.oli.content.ResourceException;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Set;

/**
 * @author Raphael Gachuhi
 */
public class ResourceJsonReader {

    private static final Logger log = LoggerFactory.getLogger(ResourceJsonReader.class);

    //, Optional<Path> filePath
    public static void parseResourceJson(Resource rsrc, JsonObject jsonObject) {
        // Parse common resource markup
        Set<Map.Entry<String, JsonElement>> entries = jsonObject.entrySet();
        jsonObject = (JsonObject) entries.iterator().next().getValue();
        String id = jsonObject.has("@id") ? jsonObject.get("@id").getAsString() : null;
        if (id == null || id.isEmpty()) {
            String message = "ContentResource Json must contain a valid resource id  ";
            log.error(message);
            throw new ResourceException(Response.Status.BAD_REQUEST, "resource", message);
        }
        rsrc.setId(id.replaceAll("[^a-zA-Z0-9-_\\.]", ""));
        String version = jsonObject.has("@version") ? jsonObject.get("@version").getAsString() : null;
        if (version != null && !version.isEmpty()) {
            if (version != null && !version.isEmpty()) {
                JsonObject metadata = new JsonObject();
                metadata.addProperty("version", version);
                rsrc.setMetadata(new JsonWrapper(metadata));
            }
        }
        Resource rs = new Resource();
        lookFor(rs, jsonObject);
        rsrc.setTitle(rs.getTitle());
        String shortTitle = rs.getShortTitle();
        if (shortTitle != null && shortTitle.length() > 30) {
            shortTitle = shortTitle.substring(0, 26) + "...";
        }
        rsrc.setShortTitle(shortTitle);
        jsonObject.addProperty("@id", rsrc.getId());
        log.info(rsrc.getTitle());
        if (rsrc.getTitle() == null || rsrc.getTitle().isEmpty()) {
            String message = "ContentResource Json must contain a valid title  ";
            log.error(message);
            throw new ResourceException(Response.Status.BAD_REQUEST, "resource json", message);
        }
    }

    private static void lookFor(Resource rsrc, JsonElement jsonObject) {
        if (!jsonObject.isJsonObject()) {
            return;
        }
        Set<Map.Entry<String, JsonElement>> entries = jsonObject.getAsJsonObject().entrySet();
        for (Map.Entry<String, JsonElement> entry : entries) {
            if (entry.getValue() instanceof JsonArray) {
                JsonArray asJsonArray = entry.getValue().getAsJsonArray();
                for (JsonElement anAsJsonArray : asJsonArray) {
                    lookFor(rsrc, anAsJsonArray);
                }
                continue;
            }
            switch (entry.getKey()) {
                case "@id":
                    String asString = entry.getValue().getAsString();
                    asString = asString.replaceAll("[^a-zA-Z0-9-_\\.]", "");
                    entry.setValue(new JsonPrimitive(asString));
                    if (rsrc.getId() == null) {
                        rsrc.setId(entry.getValue().getAsString());
                    }
                    break;
                case "title":
                    if (rsrc.getTitle() == null) {
                        StringBuffer sb = new StringBuffer();
                        extractTitle(entry.getValue(), sb);
                        rsrc.setTitle(sb.toString());
                    }
                    break;
                case "short_title":
                    if (rsrc.getShortTitle() == null) {
                        StringBuffer sb = new StringBuffer();
                        extractTitle(entry.getValue(), sb);
                        rsrc.setShortTitle(sb.toString());
                    }
                    break;
            }
            if (entry.getValue() instanceof JsonObject) {
                lookFor(rsrc, entry.getValue());
            }
        }
    }

    private static void extractTitle(JsonElement ob, StringBuffer sb) {
        if (ob.isJsonArray()) {
            ob.getAsJsonArray().forEach(e -> {
                extractTitle(e, sb);
            });
        } else if (ob.isJsonObject()) {
            ob.getAsJsonObject().entrySet().forEach(en -> {
                if (en.getKey().equalsIgnoreCase("#text")) {
                    sb.append(en.getValue().getAsString());
                } else {
                    extractTitle(en.getValue(), sb);
                }
            });
        } else if (ob.isJsonPrimitive()) {
            sb.append(ob.getAsString());
        } else {
            String s = new Gson().toJson(ob);
            log.error("should not happen when extracting title " + s);
            sb.append(s);
        }
    }
}
