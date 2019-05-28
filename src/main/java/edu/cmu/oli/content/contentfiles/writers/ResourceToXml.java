package edu.cmu.oli.content.contentfiles.writers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.cmu.oli.assessment.builders.Assessment2Transform;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.resource.builders.Json2Xml;
import org.apache.commons.text.StringEscapeUtils;
import org.jdom2.*;
import org.jdom2.filter.ElementFilter;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.output.Format;
import org.jdom2.output.Format.TextMode;
import org.jdom2.output.XMLOutputter;
import org.jdom2.util.IteratorIterable;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author Raphael Gachuhi
 */
public class ResourceToXml {

    Logger log = LoggerFactory.getLogger(ResourceToXml.class);

    private Map<String, Namespace> namespaceMap = new HashMap<>();

    Configurations config;
    String webcontentVolume;

    SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
    XMLOutputter xmlOut = new XMLOutputter(Format.getPrettyFormat());

    Assessment2Transform assessment2Transform = new Assessment2Transform();

    public void setConfig(Configurations config) {
        builder.setExpandEntities(false); // Retain Entities
        builder.setReuseParser(true);
        Format format = xmlOut.getFormat();
        format.setIndent("\t");
        format.setTextMode(TextMode.PRESERVE);
        this.config = config;
        JsonObject namespaces = config.getNamespaces();
        namespaces.entrySet().forEach(jsonEntry -> {
            namespaceMap.put(jsonEntry.getKey(),
                    Namespace.getNamespace(jsonEntry.getKey(), jsonEntry.getValue().getAsString()));
        });
    }

    public String resourceToXml(String type, String s) {
        JsonObject resourceTypeDef = this.config.getResourceTypeById(type).getAsJsonObject();

        JsonParser parser = new JsonParser();
        JsonElement parse = parser.parse(s);
        if (!parse.isJsonObject()) {
            throw new RuntimeException("Error: Not a Json Object " + s);
        }
        Document document = new Json2Xml().jsonToXml((JsonObject) parse, this.namespaceMap);
        Set<Map.Entry<String, JsonElement>> entries = ((JsonObject) parse).entrySet();
        if (entries.isEmpty() || entries.size() > 1) {
            throw new RuntimeException("only one entry expected " + s);
        }
        Map.Entry<String, JsonElement> next = entries.iterator().next();
        String key = next.getKey();
        if (resourceTypeDef.has("PUBLIC_ID") && resourceTypeDef.has("SYSTEM_ID")) {
            // Document type
            DocType docType = new DocType(key, resourceTypeDef.get("PUBLIC_ID").getAsString(),
                    resourceTypeDef.get("SYSTEM_ID").getAsString());
            document.setDocType(docType);
        }
        final Element root = document.getRootElement();// new Element(key);
        JsonArray namespacePrefixes = resourceTypeDef.getAsJsonArray("namespacePrefixes");
        if (namespacePrefixes != null) {
            namespacePrefixes.forEach(jsonElement -> {
                if (this.namespaceMap.containsKey(jsonElement.getAsString())) {
                    root.addNamespaceDeclaration(this.namespaceMap.get(jsonElement.getAsString()));
                }
            });
        }

        if (type.equals("x-oli-assessment2") || type.equals("x-oli-assessment2-pool")) {
            // reverse assessment unification
            assessment2Transform.transformFromUnified(document.getRootElement());
        }

        if (type.equals("x-oli-learning_objectives")) {
            cleanupObjectiveModel(document);
        }
        if (type.equals("x-oli-organization")) {
            cleanupOrganization(document);
        }

        if (type.equals("x-oli-inline-assessment")) {
            cleanupInlineAssessment(document);
        }

        if (type.equals("x-oli-assessment2") || type.equals("x-oli-assessment2-pool")) {
            cleanupAssessment2(document);
        }

        cleanupGeneral(document);

        String data = xmlOut.outputString(document);

        String[] split = data.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String str : split) {
            str = StringEscapeUtils.unescapeXml(str);
            str = str.replaceAll("&(?!.{2,4};)", "&amp;").replaceAll("<m:math.*><!\\[CDATA\\[", "")
                    .replaceAll("\\]\\]>.*</m:math>", "");
            sb.append(str);
            sb.append("\n");
        }
        data = sb.toString();

        return data;
    }

    private void cleanupObjectiveModel(Document document) {
        String query = "//*[@expanded] | //*[@parent] | //objective[@category]";
        XPathExpression<Element> xexpression = XPathFactory.instance().compile(query, Filters.element());
        List<Element> kids = xexpression.evaluate(document.getRootElement());
        for (Element el : kids) {
            el.removeAttribute("parent");
            el.removeAttribute("expanded");
            if (el.getAttribute("category") != null
                    && el.getAttribute("category").getValue().equalsIgnoreCase("unassigned")) {
                el.setAttribute("category", "domain_specific");
            }
        }
    }

    private void cleanupInlineAssessment(Document document) {
        String query = "//*[@id] | //*[@max_attempts] | //*[@recommended_attempts] | //grading_criteria | //essay "
                + "| //short_title | //content | //question | //page | //short_answer | //concept | //input_ref | //choice"
                + "| //part | //explanation | //*[@color]";
        XPathExpression<Element> xexpression = XPathFactory.instance().compile(query, Filters.element());
        Element rootElement = document.getRootElement();
        rootElement.removeAttribute("recommendedAttempts");
        rootElement.removeAttribute("maxAttempts");
        List<Element> kids = xexpression.evaluate(rootElement);
        Map<String, String> fixMatches = new HashMap();
        for (Element el : kids) {
            if (el.getAttribute("max_attempts") != null) {
                el.removeAttribute("max_attempts");
            }
            if (el.getAttribute("recommended_attempts") != null) {
                el.removeAttribute("recommended_attempts");
            }
            if (el.getName().equalsIgnoreCase("grading_criteria")) {
                el.detach();
            }
            if (el.getName().equalsIgnoreCase("essay")) {
                el.setName("short_answer");
            }

            Attribute color = el.getAttribute("color");
            if (color != null && color.getValue().isEmpty()) {
                color.detach();
            }
            if (el.getName().equalsIgnoreCase("content")) {
                Attribute availability = el.getAttribute("availability");
                if (availability != null) {
                    String value = availability.getValue();
                    el.removeAttribute(availability);
                    el.setAttribute("available", value);
                }
            }
            if (el.getName().equalsIgnoreCase("question")) {
                el.removeAttribute("grading");

                // Move any hints (if any) found at the question level into the very first question part
                // Assumes question level hints not supported
                IteratorIterable<Element> part = el.getDescendants(new ElementFilter("part"));
                List<Element> hints = el.getChildren("hint");

                List<Element> removal = new ArrayList<>();
                removal.addAll(hints);
                if (part.hasNext()) {
                    Element next = part.next();
                    for (Element h : removal) {
                        next.addContent(h.detach());
                    }
                }
            }
            if (el.getName().equalsIgnoreCase("page")) {
                List<Element> title = el.getChildren("title");
                List<Element> removal = new ArrayList<>();
                if (title.size() > 1) {
                    for (int x = 1; x < title.size(); x++) {
                        removal.add(title.get(x));
                    }
                }
                removal.forEach(elm -> {
                    elm.detach();
                });

                Element shortTitle = el.getChild("short_title");
                if (shortTitle != null) {
                    shortTitle.detach();
                    // el.getParent().addContent(1, shortTitle);
                }
                if ((el.getContentSize() < 1)) {
                    el.detach();
                }
                if (el.getChildren("question").isEmpty()) {
                    el.detach();
                }
            }
            if (el.getName().equalsIgnoreCase("short_answer")) {
                el.removeAttribute("size");
            }
            if (el.getName().equalsIgnoreCase("concept")) {
                if (el.getContent().isEmpty()) {
                    el.detach();
                } else {
                    Element part = el.getParentElement().getChild("part");
                    el.detach();
                    part.addContent(1, el);
                    el.setNamespace(Namespace.getNamespace("cmd", "http://oli.web.cmu.edu/content/metadata/2.1/"));
                }
            }

            if (el.getName().equalsIgnoreCase("input_ref")) {
                List<Content> content = el.getContent();
                int x = 0;
                for (Content con : content) {
                    x++;
                    con.detach();
                    el.getParent().addContent(el.getParent().indexOf(el) + x, con);
                }
                Attribute input = el.getAttribute("input");
                if (input != null && (input.getValue().isEmpty() || Character.isDigit(input.getValue().charAt(0)))) {
                    el.setAttribute("input", "i" + input.getValue());
                }
                if (input != null) {
                    el.setAttribute("input", input.getValue());
                }
            }
            if (el.getName().equalsIgnoreCase("choice")) {
                Attribute value = el.getAttribute("value");
                if (value != null && (value.getValue().isEmpty() || Character.isDigit(value.getValue().charAt(0)))) {
                    String s = "v" + value.getValue();
                    if (!value.getValue().isEmpty()) {
                        fixMatches.put(value.getValue(), s);
                    }
                    value.setValue(s);
                }
            }
            if (el.getName().equalsIgnoreCase("part")) {
                Attribute at = el.getAttribute("correct");
                if (at != null && at.getValue().isEmpty()) {
                    at.detach();
                }
                at = el.getAttribute("score_out_of");
                if (at != null && at.getValue().isEmpty()) {
                    at.detach();
                }
                at = el.getAttribute("targets");
                if (at != null && at.getValue().isEmpty()) {
                    at.detach();
                }
            }
            if (el.getName().equalsIgnoreCase("explanation")) {
                if (el.getValue().isEmpty()) {
                    el.detach();
                } else {
                    Parent parent = el.getParent();
                    if (parent.indexOf(el) != parent.getContentSize() - 1) {
                        el.detach();
                        parent.addContent(el);
                    }
                }

            }
        }
        query = "//response | //match";
        xexpression = XPathFactory.instance().compile(query, Filters.element());
        kids = xexpression.evaluate(rootElement);
        for (Element el : kids) {
            Attribute match = el.getAttribute("match");
            if (match != null && !match.getValue().isEmpty()) {
                String mvalue = match.getValue();
                if (fixMatches.containsKey(mvalue)) {
                    el.setAttribute("match", match.getValue().replaceAll(mvalue, fixMatches.get(mvalue)));
                }
            }
            Attribute input = el.getAttribute("input");
            if (input != null && (input.getValue().isEmpty() || Character.isDigit(input.getValue().charAt(0)))) {
                el.setAttribute("input", "i" + input.getValue());
            }
        }
    }

    private void cleanupAssessment2(Document document) {
        String query = "//*[@id] | //content | //short_title | //page | //*[@select] | //part"
                + "| //concept | //criteria | //explanation | //choice | //input | //input_ref | //selection";
        XPathExpression<Element> xexpression = XPathFactory.instance().compile(query, Filters.element());
        Element rootElement = document.getRootElement();

        Attribute attrib = rootElement.getAttribute("recommendedAttempts");
        if (attrib != null) {
            rootElement.removeAttribute(attrib);
            rootElement.setAttribute("recommended_attempts", attrib.getValue());
        }

        attrib = rootElement.getAttribute("maxAttempts");
        if (attrib != null) {
            attrib.detach();
            rootElement.setAttribute("max_attempts", attrib.getValue());
        }

        List<Element> kids = xexpression.evaluate(rootElement);
        Map<String, String> fixMatches = new HashMap<>();
        for (Element el : kids) {
            if (el.getName().equalsIgnoreCase("content")) {
                Attribute availability = el.getAttribute("availability");
                if (availability != null) {
                    String value = availability.getValue();
                    el.removeAttribute(availability);
                    el.setAttribute("available", value);
                }
            }
            if (el.getName().equalsIgnoreCase("page")) {
                List<Element> title = el.getChildren("title");
                List<Element> removal = new ArrayList<>();
                if (title.size() > 1) {
                    for (int x = 1; x < title.size(); x++) {
                        removal.add(title.get(x));

                    }
                }
                removal.forEach(elm -> {
                    elm.detach();
                });

                Element shortTitle = el.getChild("short_title");
                if (shortTitle != null) {
                    shortTitle.detach();
                }

            }
            if (el.getName().equalsIgnoreCase("concept")) {
                el.setNamespace(Namespace.getNamespace("cmd", "http://oli.web.cmu.edu/content/metadata/2.1/"));
            }
            // The front end maps all feedback to explanation as part of the "unified
            // model."
            // This is correct for inline assessments. However, graded assessments store
            // feedback
            // in the feedback attribute in "response" nested under "part", so we reparent
            // the explanation
            // content under the feedback element here.
            if (el.getName().equalsIgnoreCase("explanation")) {
                String text = el.getText();
                if (text != null && !text.isEmpty()) {
                    if (el.getParentElement().getName().equals("part")) {
                        List<Element> responses = el.getParentElement().getChildren("response");
                        if (responses.size() > 0) {
                            List<Element> feedbacks = responses.get(0).getChildren("feedback");
                            if (feedbacks.size() > 0) {
                                feedbacks.get(0).removeContent();
                                feedbacks.get(0).addContent(el.removeContent());
                            }
                        }
                    }
                }
                el.detach();
            }

            if (el.getName().equalsIgnoreCase("choice")) {
                el.removeAttribute("color");
                Attribute value = el.getAttribute("value");
                if (value != null && (value.getValue().isEmpty() || Character.isDigit(value.getValue().charAt(0)))) {
                    String s = "v" + value.getValue();
                    if (!value.getValue().isEmpty()) {
                        fixMatches.put(value.getValue(), s);
                    }
                    value.setValue(s);
                }
            }
            Attribute select = el.getAttribute("select");
            if (select != null) {
                if (select.getValue().isEmpty()) {
                    select.detach();
                }
            }

            if (el.getName().equalsIgnoreCase("input_ref")) {
                List<Content> content = el.getContent();
                int x = 0;
                for (Content con : content) {
                    x++;
                    con.detach();
                    el.getParent().addContent(el.getParent().indexOf(el) + x, con);
                }
                Attribute input = el.getAttribute("input");
                if (input != null && (input.getValue().isEmpty() || Character.isDigit(input.getValue().charAt(0)))) {
                    el.setAttribute("input", "i" + input.getValue());
                }
                if (input != null) {
                    el.setAttribute("input", input.getValue());
                }
            }
            if (el.getName().equalsIgnoreCase("input")) {
                Attribute whitespace = el.getAttribute("whitespace");
                Attribute caseSensitive = el.getAttribute("case_sensitive");
                Attribute select2 = el.getAttribute("select");
                if (whitespace != null) {
                    whitespace.detach();
                    if (!whitespace.getValue().isEmpty()) {
                        ((Element) el.getParent()).setAttribute(whitespace);
                    }
                }
                if (caseSensitive != null) {
                    caseSensitive.detach();
                    if (!caseSensitive.getValue().isEmpty()) {
                        ((Element) el.getParent()).setAttribute(caseSensitive);
                    }
                }
                if (select2 != null) {
                    select2.detach();
                    if (!select2.getValue().isEmpty()) {
                        ((Element) el.getParent()).setAttribute(select2);
                    }
                }
                el.removeAttribute("notation");
            }
            if (el.getName().equalsIgnoreCase("selection")) {
                el.removeAttribute("id");
            }
            if (el.getName().equalsIgnoreCase("part")) {
                Attribute at = el.getAttribute("correct");
                if (at != null && at.getValue().isEmpty()) {
                    at.detach();
                }
                at = el.getAttribute("score_out_of");
                if (at != null && at.getValue().isEmpty()) {
                    at.detach();
                }
                at = el.getAttribute("targets");
                if (at != null && at.getValue().isEmpty()) {
                    at.detach();
                }
            }
        }
        query = "//response | //match";
        xexpression = XPathFactory.instance().compile(query, Filters.element());
        kids = xexpression.evaluate(rootElement);
        for (Element el : kids) {
            Attribute match = el.getAttribute("match");
            if (match != null && !match.getValue().isEmpty()) {
                String mvalue = match.getValue();
                if (fixMatches.containsKey(mvalue)) {
                    el.setAttribute("match", match.getValue().replaceAll(mvalue, fixMatches.get(mvalue)));
                }
            }
            Attribute input = el.getAttribute("input");
            if (input != null && (input.getValue().isEmpty() || Character.isDigit(input.getValue().charAt(0)))) {
                el.setAttribute("input", "i" + input.getValue());
            }
        }
    }

    private void cleanupOrganization(Document document) {
        String query = "//*[@expanded] | //description | //audience";
        XPathExpression<Element> xexpression = XPathFactory.instance().compile(query, Filters.element());
        List<Element> kids = xexpression.evaluate(document.getRootElement());
        for (Element el : kids) {
            if (el.getAttribute("expanded") != null) {
                el.removeAttribute("expanded");
            }
            if (el.getName().equalsIgnoreCase("description") && el.getValue().isEmpty()) {
                el.setText("Sample organization template. " + document.getRootElement().getAttribute("id"));
            }
            if (el.getName().equalsIgnoreCase("audience") && el.getValue().isEmpty()) {
                el.setText("This organization is intended as an example for OLI content authors.");
            }
        }
    }

    private void cleanupGeneral(Document document) {
        String query = "//*[@id] | //*[@lang] | //*[@src] | //*[@title] | //objref[@idref] | //*[@name] | //*[@alt] | //table "
                + "| //*[@orient] | //section | //video | //audio | //cite | //*[@targets] | //title | //content "
                + "| //pullout | //example | //codeblock | //iframe | //youtube | //definition | //math | //link | //alternate"
                + "| //short_title | //sym";
        XPathExpression<Element> xexpression = XPathFactory.instance().compile(query, Filters.element());
        List<Element> kids = xexpression.evaluate(document.getRootElement());
        for (Element el : kids) {
            if (!el.isRootElement()) {
                Attribute id = el.getAttribute("id");
                if (id != null && id.getValue().isEmpty()) {
                    id.setValue("i" + UUID.randomUUID().toString().replaceAll("-", ""));
                }
                if (id != null && Character.isDigit(id.getValue().charAt(0))) {
                    id.setValue("i" + id.getValue());
                }
            }
            Attribute name = el.getAttribute("name");
            if (name != null) {
                if (name.getValue().isEmpty()) {
                    name.detach();
                }
            }

            if (el.getAttribute("lang") != null) {
                el.getAttribute("lang").setNamespace(Namespace.XML_NAMESPACE);
            }

            if (el.getAttribute("src") != null) {
                if (el.getAttribute("src").getValue().isEmpty()) {
                    el.removeAttribute("src");
                } else {
                    Attribute src = el.getAttribute("src");
                    String s = StringEscapeUtils.unescapeXml(src.getValue());
                    URL validURL = toURL(s);
                    if (validURL != null) {
                        String encoded = StringEscapeUtils.escapeXml10(s);
                        el.setAttribute("src", encoded);

                    } else {
                        Path resolve = Paths.get(webcontentVolume + File.separator + s);
                        if (resolve.toFile().exists() && !s.contains("webcontent")) {
                            el.setAttribute("src", ".." + File.separator + "webcontent" + File.separator + s);
                        }
                    }
                }
            }
            if (el.getAttribute("height") != null) {
                if (el.getAttribute("height").getValue().isEmpty()) {
                    el.removeAttribute("height");
                } else {
                    el.setAttribute("height", el.getAttributeValue("height").replaceAll("px", ""));
                }
            }
            if (el.getAttribute("width") != null) {
                if (el.getAttribute("width").getValue().isEmpty()) {
                    el.removeAttribute("width");
                } else {
                    el.setAttribute("width", el.getAttributeValue("width").replaceAll("px", ""));
                }
            }
            if (el.getAttribute("title") != null && el.getAttribute("title").getValue().isEmpty()) {
                el.removeAttribute("title");
            }
            if (el.getAttribute("orient") != null) {
                el.removeAttribute("orient");
            }
            if (el.getAttribute("idref") != null) {
                Attribute idref = el.getAttribute("idref");
                if (idref.getValue().isEmpty()) {
                    el.detach();
                } else {
                    if (Character.isDigit(idref.getValue().charAt(0))) {
                        idref.setValue("i" + idref.getValue());
                    }
                }
            }
            if (el.getAttribute("targets") != null) {
                Attribute targets = el.getAttribute("targets");
                if (targets.getValue().isEmpty()) {
                    targets.detach();
                } else if (Character.isDigit(targets.getValue().charAt(0))) {
                    el.setAttribute("targets", "i" + targets.getValue());
                }
            }

            if (el.getAttribute("alt") != null) {
                Attribute alt = el.getAttribute("alt");
                alt.setValue(StringEscapeUtils.escapeXml10(alt.getValue()));
            }

            if (el.getName().equalsIgnoreCase("short_title")) {
                String value = el.getText();
                if (value != null && value.length() > 30) {
                    value = value.substring(0, 26) + "...";
                    el.setText(value);
                }
            }

            if (el.getName().equalsIgnoreCase("section")) {
                if (el.getAttribute("purpose") != null && el.getAttribute("purpose").getValue().isEmpty()) {
                    el.removeAttribute("purpose");
                }
                if (el.getChild("title") == null) {
                    Element sectionTitle = new Element("title");
                    sectionTitle.setText(" ");
                    el.addContent(0, sectionTitle);
                } else if (el.getChild("title").getText().isEmpty()) {
                    el.getChild("title").setText(" ");
                }
            }
            if (el.getName().equalsIgnoreCase("video") || el.getName().equalsIgnoreCase("audio")
                    || el.getName().equals("youtube") || el.getName().equals("iframe")
                    || el.getName().equals("image")) {
                el.removeAttribute("href");
                el.removeAttribute("valign");

                Attribute type = el.getAttribute("type");
                // Do not remove 'type' from video and audio as it is a valid attribute for
                // these elements
                if (!(el.getName().equalsIgnoreCase("video") || el.getName().equalsIgnoreCase("audio"))) {
                    el.removeAttribute("type");
                }

                IteratorIterable<Content> descendants = el.getDescendants();
                descendants.forEach(content -> {
                    Content.CType cType = content.getCType();
                    if (cType.equals(Content.CType.Element)) {
                        Element elm = (Element) content;

                        if (elm.getAttribute("entry") != null && elm.getAttribute("entry").getValue().isEmpty()) {
                            elm.removeAttribute("entry");
                        }
                        if (elm.getName().equalsIgnoreCase("source") && type != null) {
                            elm.setAttribute("type", type.getValue());
                        }
                    }

                });
            }
            if (el.getName().equalsIgnoreCase("iframe")) {
                Attribute src = el.getAttribute("src");
                if (src == null || src.getValue().isEmpty()) {
                    el.setAttribute("src", "https://www.google.com/");
                }
            }

            if (el.getName().equalsIgnoreCase("audio")) {
                IteratorIterable<Element> source = el.getDescendants(new ElementFilter("source"));
                source.forEach(s -> {
                    Attribute src = s.getAttribute("src");
                    if (src == null || src.getValue().isEmpty()) {
                        s.setAttribute("src", "https://oli.cmu.edu");
                    }
                    Attribute type = s.getAttribute("type");
                    if (type == null || type.getValue().isEmpty()) {
                        s.setAttribute("type", "audio/mp3");
                    }
                });

                Attribute src = el.getAttribute("src");
                if (src == null || src.getValue().isEmpty()) {
                    if (source.hasNext()) {
                        Element next = source.next();
                        el.setAttribute("src", next.getAttributeValue("src"));
                    } else {
                        el.setAttribute("src", "https://oli.cmu.edu");
                    }
                }
                Attribute type = el.getAttribute("type");
                if (type == null || type.getValue().isEmpty()) {
                    if (source.hasNext()) {
                        Element next = source.next();
                        el.setAttribute("type", next.getAttributeValue("type"));
                    } else {
                        el.setAttribute("type", "audio/mp3");
                    }
                }
            }

            if (el.getName().equalsIgnoreCase("video")) {
                IteratorIterable<Element> source = el.getDescendants(new ElementFilter("source"));
                source.forEach(s -> {
                    Attribute src = s.getAttribute("src");
                    if (src == null || src.getValue().isEmpty()) {
                        s.setAttribute("src", "https://oli.cmu.edu");
                    }
                    Attribute type = s.getAttribute("type");
                    if (type == null || type.getValue().isEmpty()) {
                        s.setAttribute("type", "video/mpeg");
                    }
                });

                Attribute src = el.getAttribute("src");
                if (src == null || src.getValue().isEmpty()) {
                    if (source.hasNext()) {
                        Element next = source.next();
                        el.setAttribute("src", next.getAttributeValue("src"));
                    } else {
                        el.setAttribute("src", "https://oli.cmu.edu");
                    }
                }
                Attribute type = el.getAttribute("type");
                if (type == null || type.getValue().isEmpty()) {
                    if (source.hasNext()) {
                        Element next = source.next();
                        el.setAttribute("type", next.getAttributeValue("type"));
                    } else {
                        el.setAttribute("type", "video/mpeg");
                    }
                }
            }

            if (el.getName().equalsIgnoreCase("image")) {
                Attribute src = el.getAttribute("src");
                if (src == null || src.getValue().isEmpty()) {
                    el.setAttribute("src", "https://oli.cmu.edu/img/Logo_OpenLearningInitiative.png");
                }
            }

            if (el.getName().equalsIgnoreCase("cite")) {
                el.setAttribute("id", "i" + UUID.randomUUID().toString().replaceAll("-", ""));
                if (el.getAttribute("entry") != null && el.getAttribute("entry").getValue().isEmpty()) {
                    el.removeAttribute("entry");
                }
            }

            if (el.getName().equalsIgnoreCase("definition")) {
                List<Element> ps = el.getChildren("p");
                StringBuilder sb = new StringBuilder();
                List<Element> removal = new ArrayList<>();
                ps.forEach(elm -> {
                    sb.append(elm.getText());
                    sb.append(" ");
                    removal.add(elm);
                });

                removal.forEach(elm -> {
                    elm.detach();
                });

                if (el.getChild("meaning") == null) {
                    Element meaning = new Element("meaning");
                    el.addContent(meaning);
                    Element material = new Element("material");
                    meaning.addContent(material);
                    Element p = new Element("p");
                    material.addContent(p);
                    p.addContent(sb.toString().trim());
                }
            }

            if (el.getName().equalsIgnoreCase("pullout")) {
                Element title1 = el.getChild("title");
                if (title1 != null) {
                    Element p = title1.getChild("p");
                    if (p != null) {
                        String text = p.getText();
                        p.getParent().removeContent(p);
                        title1.addContent(text);
                    }
                }
            }
            if (el.getName().equalsIgnoreCase("example")) {
                Element title1 = el.getChild("title");
                if (title1 != null) {
                    if (title1.getChild("p") != null) {
                        Element p = title1.getChild("p");
                        String text = p.getText();
                        p.getParent().removeContent(p);
                        title1.addContent(text);
                    }
                }
            }
            if (el.getName().equalsIgnoreCase("codeblock")) {
                Element texts = el.getChild("text");
                if (texts != null) {
                    String text = texts.getText();
                    texts.getParent().removeContent(texts);
                    el.addContent(text);
                }
            }
            if (el.getName().equalsIgnoreCase("table")) {
                if (el.getContentSize() == 0) {
                    el.detach();
                }
            }
            if (el.getName().equalsIgnoreCase("link")) {
                if (el.getChild("p") != null) {
                    Element p = el.getChild("p");
                    List<Content> pc = p.getContent();
                    p.detach();
                    pc.forEach(e -> {
                        e.detach();
                    });
                    el.addContent(pc);
                }
            }
            if (el.getName().equalsIgnoreCase("alternate")) {
                Attribute idref = el.getAttribute("idref");
                if (idref != null && idref.getValue().isEmpty()) {
                    el.detach();
                }
            }
            if (el.getName().equalsIgnoreCase("title")) {
                if ((el.getValue().isEmpty())) {
                    el.detach();
                }
            }
            if (el.getName().equalsIgnoreCase("content")) {
                if ((el.getValue().isEmpty()) && el.getChildren().size() == 0) {
                    el.detach();
                }
            }

            if (el.getName().equalsIgnoreCase("sym")) {
                if (!el.getValue().isEmpty()) {
                    String value = el.getValue();
                    Parent parent = el.getParent();
                    parent.addContent(parent.indexOf(el), new Text(value));
                    el.detach();

                }
            }

        }

        IteratorIterable<Element> descendants = document.getRootElement().getDescendants(new ElementFilter("math"));
        descendants.forEach(el -> {

            if (!el.getContent().isEmpty() && el.getContent().get(0).getCType().equals(Content.CType.CDATA)) {
                CDATA elm = (CDATA) el.getContent().get(0);
                String text = elm.getText();
                String[] split = text.split("<");
                StringBuilder sb = new StringBuilder();
                for (int x = 1; x < split.length; x++) {
                    if (split[x].startsWith("/")) {
                        split[x] = split[x].replaceFirst("/", "</m:");
                    } else {
                        split[x] = "<m:" + split[x];
                    }
                    split[x] = split[x].replaceAll("xmlns=", "xmlns:m=");
                    sb.append(split[x]);
                }

                elm.setText(sb.toString());
            }
        });
    }

    private URL toURL(String urlString) {
        try {
            return new URL(urlString);
        } catch (MalformedURLException e) {
        }
        return null;
    }

}
