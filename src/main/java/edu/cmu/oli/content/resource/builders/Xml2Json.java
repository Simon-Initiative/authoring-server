package edu.cmu.oli.content.resource.builders;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jdom2.Attribute;
import org.jdom2.Content;
import org.jdom2.Element;
import org.jdom2.EntityRef;
import org.jdom2.Text;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.enterprise.inject.Default;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class that converts xml content to a JsonObject
 */
@Default
public class Xml2Json {

    private final Logger log = LoggerFactory.getLogger(Xml2Json.class);

    private XMLOutputter outputter = new XMLOutputter(Format.getCompactFormat());

    public JsonObject toJson(final Content xml, final boolean preformat) {
        if (xml.getCType() == Content.CType.Element) {
            Element el = (Element) xml;
            JsonObject elementJson = new JsonObject();

            // get element attributes
            if (el.hasAttributes()) {
                for (Attribute attr : el.getAttributes()) {
                    if (attr.getName().equalsIgnoreCase("empty")) {
                        continue;
                    }
                    elementJson.addProperty("@" + attr.getName(), attr.getValue());
                }
            }

            // get element children
            List<Content> children = el.getContent();

            // create preformat flag for children recursive calls
            boolean childrenPreformat = preformat
                    || el.getQualifiedName().equalsIgnoreCase("m:math")
                    || el.getQualifiedName().equalsIgnoreCase("codeblock");

            // process children of the current element (processChildren recursively calls toJson)
            List<JsonObject> processedChildren = processChildren(children, childrenPreformat);

            // convert processed children to a JsonArray
            JsonArray childrenArray = new JsonArray();
            for (JsonObject o : processedChildren) {
                childrenArray.add(o);
            }

            // check if object needs a array for multiple children
            if (childrenArray.size() > 1) {
                // add children array to current element
                elementJson.add("#array", childrenArray);
            } else if (childrenArray.size() == 1) {
                // single child, merge the child's properties with current element
                JsonObject onlyChild = processedChildren.get(0);
                onlyChild.keySet().stream().forEach(key -> elementJson.add(key, onlyChild.get(key)));
            }

            // create element container for current element
            // (json object with single property key'd by element name)
            JsonObject obj = new JsonObject();
            if (el.getQualifiedName().equalsIgnoreCase("m:math")) {
                String s1 = outputter.outputString(el);
                s1 = processText(s1);

                obj.addProperty("#math", s1);
            }
            else {
                obj.add(el.getQualifiedName(), elementJson);
            }

            return obj;
        }
        else if (xml.getCType() == Content.CType.Text) {
            String text = ((Text) xml).getText();

            // only process text if preformat flag has not been set
            if (!preformat) {
                text = processText(text);
            }

            JsonObject obj = new JsonObject();
            obj.addProperty("#text", text);
            return obj;
        }
        else if (xml.getCType() == Content.CType.CDATA) {
            String cdata = xml.getValue();

            if (!cdata.trim().isEmpty()) {
              JsonObject obj = new JsonObject();
              obj.addProperty("#cdata", cdata);
              return obj;
            }

            return null;
        }
        else if (xml.getCType() == Content.CType.EntityRef) {
            try (StringWriter stWriter = new StringWriter()) {
                outputter.output((EntityRef) xml, stWriter);
                String s = stWriter.toString();
                if (!s.isEmpty()) {
                    JsonObject jb = new JsonObject();
                    s = processText(s);

                    JsonObject obj = new JsonObject();
                    obj.addProperty("#text", s);
                    return obj;
                }

                return null;
            } catch (IOException e) {
                log.error("Error writing xml to json for EntityRef. Proceeding without it...");
                return null;
            }
        }
        else if (
          xml.getCType() == Content.CType.Comment
          || xml.getCType() == Content.CType.ProcessingInstruction) {
            // ignore these ctypes
            return null;
        }
        else {
            log.error("Error converting to Json. Unhandled type '" + xml.getCType() + "'. Proceeding without it...");
            return null;
        }
    }

    private List<JsonObject> processChildren(final List<Content> children, final boolean preformat) {
        final List<JsonObject> processedChildren = children.stream()
            .map(child -> toJson(child, preformat))
            // filter out null items and empty text items
            .filter(c -> c != null && !(
                c.keySet().size() == 1
                && c.has("#text")
                && c.get("#text").getAsString().trim().isEmpty()
            )).collect(Collectors.toList());

        if (processedChildren.size() > 0) {
            JsonObject firstChild = processedChildren.get(0);
          if (!preformat && firstChild.has("#text")) {
              // trim leading spaces
              firstChild.addProperty("#text", firstChild.get("#text").getAsString()
                  .replaceFirst("^\\s+", ""));
          }
        }

        return processedChildren;
    }

    private String processText(final String text) {
        return text
            .replaceAll("\t", "")           // remove tabs
            .replaceAll("\r", "")           // remove carriage returns
            .replaceAll("\\s*\n", " ")      // replace newline with a single space (possibly preceeded by whitespace)
            .replaceAll("\\s+", " ");       // replace one or more whitespace with single space
    }

}
