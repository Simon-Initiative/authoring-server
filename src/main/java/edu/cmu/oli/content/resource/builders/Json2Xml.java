package edu.cmu.oli.content.resource.builders;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import edu.cmu.oli.content.AppUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jdom2.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Raphael Gachuhi
 */
public class Json2Xml {

    public Document jsonToXml(JsonObject json, Map<String, Namespace> namespaceMap) {
        Document document = new Document();

        Set<Map.Entry<String, JsonElement>> entries = json.entrySet();
        if (entries.isEmpty() || entries.size() > 1) {
            throw new RuntimeException("only one entry expected ");
        }

        Map.Entry<String, JsonElement> next = entries.iterator().next();
        String key = next.getKey();
        Element root = new Element(key);
        document.setRootElement(root);

        Set<Map.Entry<String, JsonElement>> rootChildren = next.getValue().getAsJsonObject().entrySet();
        rootChildren.forEach((e) -> {
            doJsonToXml(e.getKey(), e.getValue(), root, namespaceMap);
        });

        return document;
    }

    private void doJsonToXml(String key, JsonElement jsonTree, Content xmlParent, Map<String, Namespace> namespaceMap) {
        if (jsonTree.isJsonObject()) {
            key = key.replaceAll("#", "");
            if (key.isEmpty()) {
                throw new IllegalArgumentException("Json Key/ XML Element cannot be empty " + AppUtils.gsonBuilder().create().toJson(jsonTree));
            }
            Element elem = null;
            if (key.contains(":")) {
                String[] split = key.split(":");
                String prefix = split[0];
                Namespace namespace = Namespace.getNamespace(prefix, "someNamespace");
                if (namespaceMap != null && namespaceMap.containsKey(prefix)) {
                    namespace = namespaceMap.get(prefix);
                }
                if (split.length > 1 && split[1].isEmpty()) {
                    elem = new Element(split[0]);
                } else {
                    elem = new Element(split[1], namespace);
                }
            } else {
                elem = new Element(key);
            }
            final Element element = elem;

            ((Element) xmlParent).addContent(element);
            Set<Map.Entry<String, JsonElement>> entries = jsonTree.getAsJsonObject().entrySet();
            entries.forEach((en) -> {
                doJsonToXml(en.getKey(), en.getValue(), element, namespaceMap);
            });
        } else if (jsonTree.isJsonArray()) {

            JsonArray array = jsonTree.getAsJsonArray();
            final String keyF = key;
            array.forEach((e) -> {
                if (e.isJsonPrimitive()) {
                    JsonObject mkOb = new JsonObject();
                    JsonObject jb = new JsonObject();
                    jb.addProperty("@idref", e.getAsString());
                    mkOb.add(keyF.equals("#annotations") ? "skill" : "replace", jb);
                    e = mkOb;
                    return;
                }
                Set<Map.Entry<String, JsonElement>> entries = e.getAsJsonObject().entrySet();
                boolean postPro = false;
                List<Map.Entry<String, JsonElement>> postProcess = new ArrayList<>();
                Map.Entry<String, JsonElement> proOb = null;
                for (Map.Entry<String, JsonElement> et : entries) {
                    if (et.getValue().isJsonPrimitive()) {
                        postProcess.add(et);
                        postPro = true;
                    } else {
                        proOb = et;
                    }

                }
                if (postPro && proOb != null) {
                    for (Map.Entry<String, JsonElement> en : postProcess) {
                        e.getAsJsonObject().remove(en.getKey());
                        proOb.getValue().getAsJsonObject().add(en.getKey(), en.getValue());
                    }
                }
                entries = e.getAsJsonObject().entrySet();
                entries.forEach((en) -> {
                    doJsonToXml(en.getKey(), en.getValue(), xmlParent, namespaceMap);
                });
            });
        } else if (jsonTree.isJsonPrimitive()) {

            if (key.startsWith("@")) {
                String value = jsonTree.getAsString();
                if (key.equals("@id") && value != null &&
                        !value.isEmpty() && Character.isDigit(value.charAt(0))) {
                    value = "i" + value;
                }
                ((Element) xmlParent).setAttribute(key.substring(key.indexOf("@") + 1), StringEscapeUtils.escapeXml10(value));
            } else if (key.equalsIgnoreCase("#math")) {
                ((Element) xmlParent).addContent(jsonTree.getAsString());
            } else if (key.equalsIgnoreCase("#cdata")) {
                ((Element) xmlParent).addContent(new CDATA(jsonTree.getAsString()));
            } else {
                // Wraps with cdata any codeblock content not already wrapped in cdata
                if (((Element)xmlParent).getName().equalsIgnoreCase("codeblock")) {
                    ((Element) xmlParent).addContent(new CDATA(jsonTree.getAsString()));
                }else {
                    String s = StringEscapeUtils.escapeXml10(jsonTree.getAsString());
                    ((Element) xmlParent).addContent(s);
                }

            }
        } else if (jsonTree.isJsonNull()) {
            throw new RuntimeException("Json Object is null");
        } else {
            throw new RuntimeException("Unknown Json Object type");
        }
    }
}