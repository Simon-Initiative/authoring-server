package edu.cmu.oli.workbookpage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import edu.cmu.oli.common.xml.RelativePathRewriter;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.controllers.Delivery;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import edu.cmu.oli.content.models.persistance.entities.Edge;
import edu.cmu.oli.content.models.persistance.entities.Resource;
import edu.cmu.oli.workbookpage.nodes.ActivityNode;
import edu.cmu.oli.workbookpage.nodes.InlineNode;
import org.jdom2.*;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.slf4j.Logger;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.ws.rs.ProcessingException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Raphael Gachuhi
 */
public class WorkbookPageDelivery implements Delivery {

    @Inject
    @Logging
    Logger log;

    @PersistenceContext
    EntityManager em;

    @Inject
    @ConfigurationCache
    Instance<Configurations> config;

    @Override
    public JsonElement deliver(Resource resource, Document document, String serverUrl, String themeId, JsonElement metaInfo) {

        Configurations serviceConfig = this.config.get();

        ContentPackage contentPackage = resource.getContentPackage();
        String edgeResourceId = contentPackage.getId() + ":" + contentPackage.getVersion() + ":" + resource.getId();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Edge> criteria = cb.createQuery(Edge.class);
        Root<Edge> edgeRoot = criteria.from(Edge.class);
        criteria.select(edgeRoot).where(cb.and(cb.equal(edgeRoot.get("sourceId"), edgeResourceId),
                cb.equal(edgeRoot.get("contentPackage").get("guid"), contentPackage.getGuid())));

        List<Edge> edges = em.createQuery(criteria).getResultList();

        List<Resource> destResources = new ArrayList<>();

        List<String> destResourceIds = edges.stream().map(edge -> {
            String destinationId = edge.getDestinationId();
            destinationId = destinationId.substring(destinationId.lastIndexOf(":") + 1);
            return destinationId;
        }).collect(Collectors.toList());

        if (!destResourceIds.isEmpty()) {
            CriteriaQuery<Resource> c = cb.createQuery(Resource.class);
            Root<Resource> resourceRoot = c.from(Resource.class);
            c.select(resourceRoot).where(cb.and(resourceRoot.get("id").in(destResourceIds),
                    cb.equal(resourceRoot.get("contentPackage").get("guid"), contentPackage.getGuid())));

            destResources = em.createQuery(c).getResultList();
        }

        String query = "//*[@idref]";
        XPathExpression<Element> xexpression = XPathFactory.instance().compile(query, Filters.element());
        List<Element> activityRefs = xexpression.evaluate(document.getRootElement());

        String webContentLocation = serverUrl + "webcontents/" + resource.getContentPackage().getGuid() + "/webcontent/";
        destResources.forEach(res -> {
            for (Element element : activityRefs) {
                if (element.getAttributeValue("idref").equals(res.getId())) {
                    JsonObject resourceTypeDefinition = serviceConfig.getResourceTypeById(res.getType());
                    if (resourceTypeDefinition == null || !resourceTypeDefinition.has("delivery")) {
                        continue;
                    }
                    JsonObject delivery = resourceTypeDefinition.getAsJsonObject("delivery");
                    if (delivery.has("inline")) {
                        InlineNode inlineNode = new InlineNode();
                        Element inline = inlineNode.process(element, res, webContentLocation, delivery.getAsJsonObject("inline"));
                        Parent parent = element.getParent();
                        parent.addContent(parent.indexOf(element), inline);
                        element.detach();
                    } else {
                        ActivityNode activityNode = new ActivityNode();
                        Element linked = activityNode.process(element);
                        Parent parent = element.getParent();
                        parent.addContent(parent.indexOf(element), linked);
                        element.detach();
                    }
                }
            }
        });
        xexpression = XPathFactory.instance().compile("//metadata[1]", Filters.element());
        List<Element> metadataElements = xexpression.evaluate(document.getRootElement());
        if (!metadataElements.isEmpty()) {
            try {
                metadataElements.get(0).addContent(workbookPageMetadata(resource));
            } catch (JDOMException e) {
                throw new ProcessingException(e.getLocalizedMessage());
            } catch (IOException e) {
                throw new ProcessingException(e.getLocalizedMessage());
            }
        }
        xexpression = XPathFactory.instance().compile("//objref", Filters.element());
        List<Element> objrefs = xexpression.evaluate(document);
        Element head = document.getRootElement().getChild("head");
        Element objectives = head.getChild("objectives");
        if (objectives == null) {
            objectives = new Element("objectives");
            if (!objrefs.isEmpty()) {
                head.addContent(objectives);
            }
        }
        JsonWrapper objectivesIndex = contentPackage.getObjectivesIndex();
        if (objectivesIndex != null) {
            JsonObject objectivesList = objectivesIndex.getJsonObject().getAsJsonObject();
            SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
            builder.setExpandEntities(false);
            builder.setReuseParser(true);
            for (Element o : objrefs) {
                String idref = o.getAttributeValue("idref");
                if (objectivesList.has(idref)) {
                    JsonObject objective = objectivesList.getAsJsonObject(idref);
                    Element obj = new Element("objective");
                    obj.setAttribute("id", idref);
                    String objectiveText = objective.get("objectiveText").getAsString();
                    Document ob = null;
                    try {
                        ob = builder.build(new StringReader("<content>" + objectiveText + "</content>"));
                    } catch (JDOMException e) {

                    } catch (IOException e) {
                    }
                    if (ob != null) {
                        List<Content> contents = ob.getRootElement().getContent();
                        contents.forEach(e -> {
                            obj.addContent(e.detach());
                        });

                    } else {
                        obj.addContent(objectiveText);
                    }
                    objectives.addContent(obj);
                }
            }
        }

        String pathFrom = resource.getFileNode().getPathFrom();
        if (pathFrom.startsWith("content/")) {
            pathFrom = pathFrom.replaceFirst("content", "");
        }
        String rootPath = serverUrl + "webcontents/" + resource.getContentPackage().getGuid() + pathFrom;
        RelativePathRewriter r = new RelativePathRewriter(rootPath);
        r.convertToAbsolutePaths(document);
        return null;
    }

    private Element workbookPageMetadata(Resource resource) throws JDOMException, IOException {
        String wbMeta = "        <wb:metadata xmlns:wb=\"http://oli.web.cmu.edu/activity/workbook/\">\n" +
                "            <wb:request page_activity_guid=\"" + resource.getGuid() + "\" page_context_guid=\"" + resource.getGuid() + "\" standalone=\"true\" supplement=\"false\" />\n" +
                "            <wb:params />\n" +
                "            <wb:messages />\n" +
                "        </wb:metadata>";
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        builder.setExpandEntities(false);
        Document metadataDoc = builder.build(new StringReader(wbMeta));

        return metadataDoc.getRootElement().detach();
    }
}
