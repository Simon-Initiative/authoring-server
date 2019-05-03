package edu.cmu.oli.content.controllers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.ContentServiceException;
import edu.cmu.oli.content.ResourceException;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.entities.Resource;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.ElementFilter;
import org.jdom2.input.SAXBuilder;
import org.jdom2.transform.JDOMSource;
import org.jdom2.util.IteratorIterable;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Raphael Gachuhi
 */
@ApplicationScoped
public class ThinPreviewControllerImpl implements ThinPreviewController {

    @Inject
    @Logging
    Logger log;

    @Inject
    @ConfigurationCache
    Instance<Configurations> configuration;

    @Inject
    MetadataController metadataController;


    private Map<String, ThemeTemplates> templatesCache;
    private Map<String, ThemeXsltStylesheets> stylesheetMap;


    @PostConstruct
    void init() {
        this.templatesCache = new ConcurrentHashMap<>();
        this.stylesheetMap = new ConcurrentHashMap<>();
        readThemesManifest();
    }

    @Override
    public String thinPreview(Resource resource, String themeId, String serverUrl) throws ContentServiceException {
        // Stylesheet
        Templates templates = fetchXSLTTemplates(resource.getType(), themeId);


        Path resourcePath = Paths.get(resource.getContentPackage().getSourceLocation()
                + File.separator + resource.getFileNode().getPathFrom());

        Document srcDoc = null;
        try {
            String resourceXml = new String(Files.readAllBytes(resourcePath), StandardCharsets.UTF_8);
            int i = resourceXml.indexOf("<?xml");
            if (i > 0) {
                resourceXml = resourceXml.substring(i);
            }
            SAXBuilder builder = AppUtils.validatingSaxBuilder();
            builder.setExpandEntities(false);
            srcDoc = builder.build(new StringReader(resourceXml));

        } catch (Exception ex) {
            log.error(ex.getLocalizedMessage(), ex);
            throw new ContentServiceException(ex.getLocalizedMessage());
        }

        try {
            Element metadata = metadataController.fetchMetadataForResource(resource, serverUrl, themeId);
            srcDoc.getRootElement().addContent(0, metadata);
            Element syllabusMetadata = metadataController.fetchSyllabusMetadata(resource, serverUrl, themeId);
            metadata.addContent(syllabusMetadata);

            Configurations serviceConfig = this.configuration.get();

            JsonObject resourceTypeDefinition = serviceConfig.getResourceTypeById(resource.getType());
            if (resourceTypeDefinition == null || !resourceTypeDefinition.has("delivery") ||
                    !resourceTypeDefinition.getAsJsonObject("delivery").has("deliveryClass")) {
                String message = resource.getType() + " not configured for delivery";
                throw new ContentServiceException(message);
            }
            JsonObject delivery = resourceTypeDefinition.getAsJsonObject("delivery");
            String deliveryClass = delivery.get("deliveryClass").getAsString();
            try {
                Class<?> aClass = Class.forName(deliveryClass);
                Delivery resourceDelivery = (Delivery) AppUtils.lookupCDIBean(aClass);
                resourceDelivery.deliver(resource, srcDoc, serverUrl, themeId, null);
            } catch (ClassNotFoundException e) {
                String message = resource.getType() + " not configured for delivery";
                throw new ContentServiceException(message);
            }
//            String metaDoc = new XMLOutputter(Format.getPrettyFormat()).outputString(srcDoc);
//            log.debug("Metadata Augmented Document:\n" + metaDoc);
        } catch (JDOMException e) {
            String message = "Thin previewResource for " + resourcePath.toString() + " and theme " + themeId + " failed";
            log.error(message, e);
            throw new ContentServiceException(message);
        } catch (IOException e) {
            String message = "Thin previewResource for " + resourcePath.toString() + " and theme " + themeId + " failed";
            log.error(message, e);
            throw new ContentServiceException(message);
        }

        // Source
        Source source = new JDOMSource(srcDoc);

        Map<String, String> parameters = new HashMap<>();
        log.info("BaseURL " + serverUrl);
        serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.lastIndexOf("/")) : serverUrl + "/";
        parameters.put("baseURL", serverUrl);

        // Transform
        String transform = new String(transform(source, templates, parameters), StandardCharsets.UTF_8);

        org.jsoup.nodes.Document doc = Jsoup.parse(transform);
        org.jsoup.nodes.Element devWidget = doc.getElementById("devWidget");
        if (devWidget != null) {
            devWidget.remove();
        }

        org.jsoup.nodes.Element divElement = doc.selectFirst("div.header");
        if (divElement != null) {
            Elements a = divElement.select("a");
            if (a != null) {
                String style = a.attr("style");
                a.attr("style", style == null || style.isEmpty() ? "pointer-events: none;" : style + "pointer-events: none;");
            }
        }
        divElement = doc.selectFirst("div.lessonHead");
        if (divElement != null) {
            String style = divElement.attr("style");
            divElement.attr("style", style == null || style.isEmpty() ? "pointer-events: none;" : style + "pointer-events: none;");
            Elements a = divElement.select("a");
            if (a != null) {
                style = a.attr("style");
                a.attr("style", style == null || style.isEmpty() ? "pointer-events: none;" : style + "pointer-events: none;");
            }
        }

//        org.jsoup.nodes.Element searchForm = doc.getElementById("searchForm");
//        if(searchForm != null){
//            Elements allElements = searchForm.children();
//            org.jsoup.nodes.Element fieldset = new org.jsoup.nodes.Element("fieldset");
//            fieldset.attr("disabled","disabled");
//            allElements.remove();
//            fieldset.insertChildren(0, allElements);
//            searchForm.appendChild(fieldset);
//        }
        transform = doc.html();

        return transform;
    }

    private byte[] transform(Source source, Templates templates, Map<String, String> parameters) {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream(32768);
        StreamResult result = new StreamResult(byteOut);

        // Transform the source document
        try {
            Transformer transformer = templates.newTransformer();

            if (parameters != null) {
                for (Iterator i = parameters.entrySet().iterator(); i.hasNext(); ) {
                    Map.Entry entry = (Map.Entry) i.next();
                    String name = (String) entry.getKey();
                    Object value = entry.getValue();
                    log.trace("transform(): setting parameter: name=" + name + ", value=" + value);
                    transformer.setParameter(name, entry.getValue());
                }
            }

            transformer.transform(source, result);
        } catch (TransformerException e) {
            log.error(e.getLocalizedMessage(), e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, null, "Preview Error");
        }

        return byteOut.toByteArray();
    }

    @Override
    public Templates fetchXSLTTemplates(String type, String themeId) throws ContentServiceException {
        if (!this.stylesheetMap.containsKey(themeId)) {
            throw new ContentServiceException("Theme '" + themeId + "' not yet installed");
        }
        ThemeXsltStylesheets themeXsltStylesheets = this.stylesheetMap.get(themeId);
        if (!themeXsltStylesheets.resourceTypeToUri.containsKey(type)) {
            throw new ContentServiceException("Theming for resource type '" + type + "' not supported");
        }
        ThemeTemplates themeTemplates = this.templatesCache.get(themeId);
        if (themeTemplates == null) {
            themeTemplates = new ThemeTemplates();
            this.templatesCache.put(themeId, themeTemplates);
        }
        Templates templates = themeTemplates.resourceTypeTemplates.get(type);
        if (templates == null) {
            templates = this.loadTemplates(themeId, themeXsltStylesheets.resourceTypeToUri.get(type));
            //themeTemplates.resourceTypeTemplates.put(type, templates);
        }
        return templates;
    }

    private void readThemesManifest() {
        log.info("readThemesManifest");
        JsonArray themes = configuration.get().getThemes();
        themes.forEach(e -> {
            JsonObject theme = e.getAsJsonObject();
            String id = theme.get("id").getAsString();
            String location = theme.get("location").getAsString();
            log.info("Theme id " + id);
            ThemeXsltStylesheets themeXsltStylesheets = new ThemeXsltStylesheets();
            try {
                byte[] bytes = Files.readAllBytes(Paths.get(location).resolve("manifest.xml"));
                String manifestXml = new String(bytes, StandardCharsets.UTF_8);
                SAXBuilder builder = AppUtils.validatingSaxBuilder();
                builder.setExpandEntities(false);
                Document document = builder.build(new StringReader(manifestXml));
                IteratorIterable<Element> stylesheets = document.getDescendants(new ElementFilter("stylesheet"));
                stylesheets.forEach(s -> {
                    String uriVal = s.getAttributeValue("uri");
                    if (uriVal.startsWith("/")) {
                        uriVal = uriVal.substring(1, uriVal.length());
                    }
                    final String uri = uriVal;
                    IteratorIterable<Element> subThemedViews = s.getDescendants(new ElementFilter("themed_view"));
                    Map<String, String> resourceTypeToUri = themeXsltStylesheets.resourceTypeToUri;
                    subThemedViews.forEach(te -> {
                        String documentType = te.getAttributeValue("document_type");
                        resourceTypeToUri.put(documentType, uri);
                    });
                });
                this.stylesheetMap.put(id, themeXsltStylesheets);
            } catch (IOException e1) {
                log.error(e1.getLocalizedMessage(), e1);
            } catch (JDOMException e1) {
                log.error(e1.getLocalizedMessage(), e1);
            }
        });
    }

    /**
     * <p>Loads the XSLT templates for the specified stylesheet out of the theme repository.</p>
     */
    private Templates loadTemplates(String themeId, String templateLocation) throws ContentServiceException {
        // Construct URL to the primary XSLT template
        Path themesPath = Paths.get(configuration.get().getThemesRepository());
        Path templatePath = themesPath.toAbsolutePath().resolve(themeId).resolve(templateLocation);
        File templateFile = templatePath.toFile();

        StreamSource source = new StreamSource(templateFile);
        source.setSystemId(templateFile);

        // Load and compile the templates
        try {
            return TransformerFactory.newInstance().newTemplates(source);
        } catch (TransformerException ex) {
            log.error(ex.getLocalizedMessage());
            throw new ContentServiceException("Unable to load xslt templates for  '" + themeId + "' located at : " + templateLocation);
        }
    }
}
