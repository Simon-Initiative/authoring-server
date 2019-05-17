package edu.cmu.oli.content.boundary.managers;

import com.airhacks.porcupine.execution.boundary.Dedicated;
import com.google.common.collect.Iterables;
import com.google.gson.*;
import edu.cmu.oli.assessment.builders.Assessment2Transform;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.DirectoryUtils;
import edu.cmu.oli.content.ResourceException;
import edu.cmu.oli.content.boundary.ResourceChangeEvent;
import edu.cmu.oli.content.boundary.ResourceChangeEvent.ResourceEventType;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.contentfiles.writers.ResourceToXml;
import edu.cmu.oli.content.controllers.EdgesController;
import edu.cmu.oli.content.controllers.LockController;
import edu.cmu.oli.content.controllers.SVNSyncController;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.*;
import edu.cmu.oli.content.resource.builders.Json2Xml;
import edu.cmu.oli.content.resource.builders.ResourceJsonReader;
import edu.cmu.oli.content.resource.builders.ResourceXmlReader;
import edu.cmu.oli.content.resource.builders.Xml2Json;
import edu.cmu.oli.content.resource.validators.BaseResourceValidator;
import edu.cmu.oli.content.resource.validators.ResourceValidator;
import edu.cmu.oli.content.security.AppSecurityContext;
import edu.cmu.oli.content.security.AppSecurityController;
import edu.cmu.oli.content.security.Scopes;
import edu.cmu.oli.content.security.Secure;
import org.apache.commons.text.StringEscapeUtils;
import org.jdom2.DocType;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.ws.rs.core.Response;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

import static edu.cmu.oli.content.AppUtils.generateUID;
import static edu.cmu.oli.content.security.Roles.ADMIN;
import static edu.cmu.oli.content.security.Roles.CONTENT_DEVELOPER;

/**
 * @author Raphael Gachuhi
 */
@Stateless
public class ContentResourceManager {

    public static final String JSON_CAPABLE = "jsonCapable";
    public static final String OLD_DYNA_DROP_SRC_FILENAME = "DynaDropHTML-1.0.js";
    public static final String DYNA_DROP_SRC_FILENAME = "DynaDropHTML.js";

    @Inject
    @Logging
    Logger log;

    @PersistenceContext
    EntityManager em;

    @Inject
    @Secure
    AppSecurityController securityManager;

    @Inject
    LockController lockController;

    @Inject
    @ConfigurationCache
    Instance<Configurations> configuration;

    @Inject
    Event<ResourceChangeEvent> resourceChange;

    @Inject
    DirectoryUtils directoryUtils;

    @Inject
    SVNSyncController svnSyncController;

    @Inject
    EdgesController edgesController;

    @Inject
    @Dedicated("svnExecutor")
    ExecutorService svnExecutor;

    public JsonElement fetchResource(AppSecurityContext session, String packageId, String resourceId) {
        securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), packageId, "name=" + packageId,
                Collections.singletonList(Scopes.VIEW_MATERIAL_ACTION));
        TypedQuery<Resource> q = em.createNamedQuery("Resource.findByGuid", Resource.class);
        q.setParameter("guid", resourceId);
        List<Resource> resultList = q.getResultList();
        if (resultList.isEmpty() || resultList.get(0).getResourceState() == ResourceState.DELETED) {
            String message = "ContentResource not found " + resourceId;
            throw new ResourceException(Response.Status.NOT_FOUND, resourceId, message);
        }
        Resource resource = resultList.get(0);

        JsonObject resourceJson = serializeEditResource(resource, session);

        resourceChange.fire(new ResourceChangeEvent(resourceId, ResourceEventType.RESOURCE_REQUESTED));
        return resourceJson;
    }

    private JsonObject serializeEditResource(Resource resource, AppSecurityContext session) {
        Gson gson = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();
        JsonObject resourceJson = (JsonObject) gson.toJsonTree(resource);
        boolean jsonCapable = configuration.get().getResourceTypeById(resource.getType()).get(JSON_CAPABLE)
                .getAsBoolean();
        Revision lastRevision = resource.getLastRevision();
        if (lastRevision != null) {
            jsonCapable = lastRevision.getBody().getXmlPayload() == null;
        }
        String path = resource.getFileNode().getVolumeLocation() + File.separator + resource.getFileNode().getPathTo();
        if (jsonCapable) {
            JsonObject jsonObject = null;
            if (lastRevision != null) {
                jsonObject = lastRevision.getBody().getJsonPayload().getJsonObject().getAsJsonObject();
            } else {
                try (FileReader fileReader = new FileReader(path)) {
                    JsonParser parser = new JsonParser();
                    JsonElement jsonElement = parser.parse(fileReader);
                    jsonObject = jsonElement.getAsJsonObject();
                } catch (Exception ex) {
                    log.error(ex.getLocalizedMessage());
                    throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, null, ex.getLocalizedMessage());
                }
            }
            // check if the object is an assessment to inject drag and drop layouts
            if (jsonObject.has("assessment")) {
                JsonArray assessmentItems = jsonObject.get("assessment").getAsJsonObject().get("#array")
                        .getAsJsonArray();

                // check if the assessment has pages
                boolean hasPages = false;
                for (JsonElement item : assessmentItems) {
                    if (item.getAsJsonObject().has("page")) {
                        hasPages = true;
                        break;
                    }
                }

                if (hasPages) {
                    // inject drag and drop layouts for each page
                    for (JsonElement page : assessmentItems) {
                        if (page.getAsJsonObject().has("page")) {
                            JsonArray pageItems = new JsonArray();
                            ;
                            if (page.getAsJsonObject().get("page").getAsJsonObject().has("#array")) {
                                pageItems = page.getAsJsonObject().get("page").getAsJsonObject().get("#array")
                                        .getAsJsonArray();
                            } else {
                                pageItems.add(page.getAsJsonObject().get("page").getAsJsonObject());
                            }

                            // inject drag and drop layouts for assessment
                            try {
                                this.injectDragAndDropLayouts(resource, pageItems);
                            } catch (Throwable t) {
                                log.error(t.toString());
                            }
                        }
                    }
                } else {
                    // inject drag and drop layouts for assessment
                    try {
                        this.injectDragAndDropLayouts(resource, assessmentItems);
                    } catch (Throwable t) {
                        log.error(t.toString());
                    }
                }
            }

            resourceJson.add("doc", jsonObject);
        } else {
            String s = null;
            if (lastRevision != null) {
                s = lastRevision.getBody().getXmlPayload();
            } else {
                try {
                    s = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    log.error(ex.getLocalizedMessage());
                    throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, null, ex.getLocalizedMessage());
                }
            }
            resourceJson.add("doc", new JsonPrimitive(s));
        }
        resourceJson.add("lock",
                gson.toJsonTree(this.lockController.getLockForResource(session, resource.getGuid(), false)));

        return resourceJson;
    }

    public boolean isSupportedDynaDropSrcFile(String filename) {
        return filename.substring(filename.length() - DYNA_DROP_SRC_FILENAME.length()).equals(DYNA_DROP_SRC_FILENAME)
                || filename.substring(filename.length() - OLD_DYNA_DROP_SRC_FILENAME.length())
                        .equals(OLD_DYNA_DROP_SRC_FILENAME);
    }

    public void injectDragAndDropLayouts(Resource resource, JsonArray items) {
        for (JsonElement item : Iterables.skip(items, 1)) {
            boolean isQuestion = item.getAsJsonObject().has("question");

            if (isQuestion) {
                JsonArray questionItems = item.getAsJsonObject().get("question").getAsJsonObject().get("#array")
                        .getAsJsonArray();

                JsonObject questionBody = null;
                for (JsonElement qItem : questionItems) {
                    if (qItem.getAsJsonObject().has("body")) {
                        questionBody = qItem.getAsJsonObject().get("body").getAsJsonObject();
                    }
                }

                if (questionBody == null || !questionBody.has("#array")) {
                    return;
                }

                JsonArray contentItems = questionBody.get("#array").getAsJsonArray();

                for (JsonElement contentItem : contentItems) {
                    boolean isCustom = contentItem.getAsJsonObject().has("custom");
                    if (isCustom) {
                        JsonObject custom = contentItem.getAsJsonObject().get("custom").getAsJsonObject();

                        if (!custom.has("@src"))
                            return;

                        String src = custom.get("@src").getAsString();

                        boolean isDynaDrop = isSupportedDynaDropSrcFile(src);

                        if (!isDynaDrop)
                            return;

                        String layoutPath = contentItem.getAsJsonObject().get("custom").getAsJsonObject().get("@layout")
                                .getAsString();

                        Path resourcePath = Paths.get(resource.getContentPackage().getSourceLocation() + File.separator
                                + resource.getFileNode().getPathFrom()).normalize();

                        Path relativeLayoutPath = resourcePath.resolveSibling(Paths.get(layoutPath)).normalize();

                        try {
                            String layoutXml = new String(Files.readAllBytes(relativeLayoutPath),
                                    StandardCharsets.UTF_8);

                            layoutXml = StringEscapeUtils.unescapeXml(layoutXml);

                            SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
                            builder.setExpandEntities(false);
                            Document document = builder.build(new StringReader(layoutXml));

                            JsonElement layoutElement = new Xml2Json().toJson(document.getRootElement(),
                                    false);

                            contentItem.getAsJsonObject().get("custom").getAsJsonObject().getAsJsonObject()
                                    .add("layoutData", layoutElement);

                        } catch (Exception ex) {
                            log.error(ex.getLocalizedMessage());
                            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, null,
                                    ex.getLocalizedMessage());
                        }

                    }
                }
            }
        }
    }

    private void extractDragAndDropLayouts(Resource resource, JsonArray items) {
        for (JsonElement item : Iterables.skip(items, 1)) {
            if (processDragAndDropLayouts(resource, item))
                return;
        }
    }

    private boolean processDragAndDropLayouts(Resource resource, JsonElement item) {
        boolean isQuestion = item.isJsonObject() && item.getAsJsonObject().has("question");

        if (isQuestion) {
            JsonObject questionBody = item.getAsJsonObject().get("question").getAsJsonObject().get("#array")
                    .getAsJsonArray()
                    // Get body of question, first item in array
                    .get(0).getAsJsonObject().get("body").getAsJsonObject();

            if (!questionBody.has("#array")) {
                return true;
            }

            JsonArray contentItems = questionBody.get("#array").getAsJsonArray();
            ;

            for (JsonElement contentItem : contentItems) {
                boolean isCustom = contentItem.getAsJsonObject().has("custom");
                if (isCustom) {
                    String src = contentItem.getAsJsonObject().get("custom").getAsJsonObject().get("@src")
                            .getAsString();

                    boolean isDynaDrop = isSupportedDynaDropSrcFile(src);
                    if (!isDynaDrop) {
                        return true;
                    }

                    try {
                        JsonObject custom = contentItem.getAsJsonObject().get("custom").getAsJsonObject();

                        JsonObject layoutData = custom.get("layoutData").getAsJsonObject();

                        Path sourceDirPath = Paths.get(resource.getContentPackage().getSourceLocation() + File.separator
                                + resource.getFileNode().getPathFrom() + File.separator + "..").normalize();
                        Path sourceContentPath = Paths
                                .get(resource.getContentPackage().getSourceLocation() + File.separator + "content");

                        // check if source path indicates a new drag and drop question
                        if (src.equals(DYNA_DROP_SRC_FILENAME)) {
                            String relativeSrcLocation = "webcontent" + File.separator + "DynaDrop" + File.separator
                                    + DYNA_DROP_SRC_FILENAME;

                            // read DynaDropHTML.js file to string
                            InputStream dynaDropJsStream = Thread.currentThread().getContextClassLoader()
                                    .getResourceAsStream(DYNA_DROP_SRC_FILENAME);
                            BufferedReader br = null;
                            StringBuilder sb = new StringBuilder();
                            br = new BufferedReader(new InputStreamReader(dynaDropJsStream));
                            String line;
                            while ((line = br.readLine()) != null) {
                                sb.append(line + System.getProperty("line.separator"));
                            }
                            String dynaDropJs = sb.toString();

                            // write DynaDropHTML.js to file to versioned source directory
                            Path fullSrcSourcePath = sourceContentPath.resolve(relativeSrcLocation);
                            directoryUtils.createDirectories(fullSrcSourcePath.toString());
                            this.updateFileContent(resource, fullSrcSourcePath, dynaDropJs);

                            Path relativizedSrcPath = sourceDirPath.relativize(fullSrcSourcePath);
                            custom.addProperty("@src", relativizedSrcPath.toString());

                            String relativeLayoutLocation = "webcontent" + File.separator + "DynaDrop" + File.separator
                                    + custom.get("@id").getAsString() + "_layout.xml";

                            // create a new layout file in the source directory
                            Path fullLayoutSourcePath = sourceContentPath.resolve(relativeLayoutLocation);
                            directoryUtils.createDirectories(fullLayoutSourcePath.toString());
                            this.updateFileContent(resource, fullLayoutSourcePath, "");

                            Path relativizedLayoutPath = sourceDirPath.relativize(fullLayoutSourcePath);
                            custom.addProperty("@layout", relativizedLayoutPath.toString());
                        }

                        String layoutPath = custom.get("@layout").getAsString();

                        Document layoutDoc = new Json2Xml().jsonToXml(layoutData, null);

                        DocType docType = new DocType("dragdrop",
                                "-//Carnegie Mellon University//DTD Dynadrop Layout 3.8//EN",
                                "http://oli.cmu.edu/dtd/oli_dynadrop_layout_1_0.dtd");

                        layoutDoc.setDocType(docType);

                        String layoutXml = new XMLOutputter(Format.getPrettyFormat()).outputString(layoutDoc);

                        updateLayoutXMLFile(resource.getContentPackage(), resource, layoutPath, layoutXml);

                        custom.remove("layoutData");
                    } catch (Exception ex) {
                        log.error(ex.getLocalizedMessage());
                        throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, null,
                                ex.getLocalizedMessage());
                    }
                }
            }
        }
        return false;
    }

    public JsonElement createResource(AppSecurityContext session, String packageId, String resourceTypeId,
            JsonElement resourceContent) {
        securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), packageId, "name=" + packageId,
                Collections.singletonList(Scopes.EDIT_MATERIAL_ACTION));

        Resource resource = doCreate(packageId, resourceTypeId, resourceContent, session.getPreferredUsername(),
                session.getTokenString(), true);

        Gson gson = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();
        JsonObject resourceJson = (JsonObject) gson.toJsonTree(resource);
        resourceJson.add("lock",
                gson.toJsonTree(this.lockController.getLockForResource(session, resource.getGuid(), true)));
        resourceJson.add("doc", resourceContent.getAsJsonObject().get("doc"));

        resourceChange.fire(new ResourceChangeEvent(resource.getGuid(), ResourceEventType.RESOURCE_CREATED));
        return resourceJson;
    }

    public Resource doCreate(String packageId, String resourceTypeId, JsonElement resourceContent, String author,
                             String lockId, boolean throwErrors) {
        Configurations serviceConfig = this.configuration.get();
        JsonObject resourceTypeDefinition = serviceConfig.getResourceTypeById(resourceTypeId);
        if (resourceTypeDefinition == null) {
            String message = "ContentResource type not supported  " + resourceTypeId;
            log.error(message);
            throw new ResourceException(Response.Status.BAD_REQUEST, packageId, message);
        }

        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findByGuid", ContentPackage.class);
        q.setParameter("guid", packageId);
        List<ContentPackage> resultList = q.getResultList();
        if (resultList.isEmpty()) {
            String message = "Content Package not found " + packageId;
            log.error(message);
            throw new ResourceException(Response.Status.NOT_FOUND, packageId, message);
        }

        ContentPackage contentPackage = resultList.get(0);
        boolean jsonCapable = resourceTypeDefinition.get(JSON_CAPABLE).getAsBoolean();
        Resource resource = new Resource();
        resource.setType(resourceTypeDefinition.get("id").getAsString());
        if (jsonCapable) {
            resourceContent = parseIncomingJsonContent(resourceContent, resource);
        } else {
            resourceContent = parseIncomingXmlContent(resourceContent, resource);
        }

        String pathFrom = "content/" + resourceTypeId + "/" + resource.getId() + ".xml";

        if (resourceTypeId.equalsIgnoreCase("x-oli-organization")) {
            String random = generateUID(8);
            String id = contentPackage.getId() + "-" + contentPackage.getVersion() + "_" + random;
            JsonObject organization = (JsonObject) ((JsonObject) resourceContent).get("organization");
            organization.remove("@id");
            organization.addProperty("@id", id);
            resource.setId(id);
            JsonObject metadata = new JsonObject();
            metadata.addProperty("version", "1.0");
            resource.setMetadata(new JsonWrapper(metadata));
            pathFrom = "organizations/" + random + "/" + "organization.xml";
        }

        TypedQuery<Resource> query = em.createQuery(
                "select r from Resource r where r.contentPackage.guid = :pkgGuid and r.id = :id", Resource.class);
        query.setParameter("id", resource.getId());
        query.setParameter("pkgGuid", packageId);
        if (!query.getResultList().isEmpty()) {
            String message = "ContentResource Id already used in package " + contentPackage.getId() + " Id: "
                    + resource.getId();
            log.error(message);
            throw new ResourceException(Response.Status.PRECONDITION_FAILED, packageId, message);
        }

        // Parse update payload into final xml and json documents
        Map<String, String> contentValues = contentValues(resourceContent, resource, jsonCapable);

        String pathTo = jsonCapable ? pathFrom.substring(0, pathFrom.lastIndexOf(".xml")) + ".json" : pathFrom;

        FileNode fileNode = new FileNode(contentPackage.getVolumeLocation(), pathFrom, pathTo,
                jsonCapable ? "application/json" : "text/xml");
        resource.setFileNode(fileNode);
        resource.setContentPackage(contentPackage);
        validateXmlContent(contentPackage.getId(), resource, contentValues.get("xmlContent"), throwErrors);

        RevisionBlob revisionBlob = jsonCapable
                ? new RevisionBlob(new JsonWrapper(new JsonParser().parse(contentValues.get("content"))))
                : new RevisionBlob(contentValues.get("xmlContent"));
        Revision revision = new Revision(resource, resource.getLastRevision(), revisionBlob, author);
        revision.setRevisionType(Revision.RevisionType.SYSTEM);
        resource.addRevision(revision);
        resource.setLastRevision(revision);

        resource.setBuildStatus(BuildStatus.READY);
        em.persist(resource);

        updateXMLFile(contentPackage, resource, contentValues.get("xmlContent"), resource.getType(), Optional.empty(),
                true);

        edgesController.validateNonValidatedEdges(contentPackage.getGuid());

        return resource;
    }

    public JsonElement updateResource(AppSecurityContext session, String packageId, String resourceId,
            JsonElement resourceContent) {
        securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), packageId, "name=" + packageId,
                Collections.singletonList(Scopes.EDIT_MATERIAL_ACTION));

        this.lockController.checkLockPermission(session, resourceId, true);

        Resource resource = findContentResource(resourceId);
        if (resource.getResourceState() == ResourceState.DELETED) {
            String message = "ContentResource not found " + resourceId;
            throw new ResourceException(Response.Status.NOT_FOUND, resourceId, message);
        }

        String lockId = this.lockController.getLockForResource(session, resourceId, false).getLockId();
        doUpdate(packageId, resource, resourceContent, session.getPreferredUsername(), lockId, null, true);

        Gson gson = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();

        JsonElement resourceJson = gson.toJsonTree(resource);
        ((JsonObject) resourceJson).add("lock",
                gson.toJsonTree(this.lockController.getLockForResource(session, resource.getGuid(), false)));
        ((JsonObject) resourceJson).add("doc", resourceContent);
        ResourceChangeEvent resourceChangeEvent = new ResourceChangeEvent(resource.getGuid(),
                ResourceEventType.RESOURCE_UPDATED);
        resourceChangeEvent.setEventPayload((JsonObject) resourceJson);
        this.resourceChange.fire(resourceChangeEvent);

        return resourceJson;
    }

    public JsonElement updateResource(AppSecurityContext session, String packageId, String resourceId,
            String baseRevision, String nextRevision, JsonElement resourceContent) {
        securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), packageId, "name=" + packageId,
                Collections.singletonList(Scopes.EDIT_MATERIAL_ACTION));

        Resource resource = findContentResource(resourceId);
        if (resource.getResourceState() == ResourceState.DELETED) {
            String message = "ContentResource not found " + resourceId;
            throw new ResourceException(Response.Status.NOT_FOUND, resourceId, message);
        }
        System.err.println("Last revision: " + resource.getLastRevision().getGuid());
        System.err.println("Supplied revision: " + baseRevision);

        if (resource.getLastRevision().getGuid().equals(baseRevision)) {
            doUpdate(packageId, resource, resourceContent, session.getPreferredUsername(), null, nextRevision, true);
        } else {
            String message = "Conflict detected " + resourceId;
            throw new ResourceException(Response.Status.CONFLICT, resourceId, message);
        }

        Gson gson = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();

        JsonElement resourceJson = gson.toJsonTree(resource);
        ((JsonObject) resourceJson).add("lock",
                gson.toJsonTree(this.lockController.getLockForResource(session, resource.getGuid(), false)));
        ((JsonObject) resourceJson).add("doc", resourceContent);
        ResourceChangeEvent resourceChangeEvent = new ResourceChangeEvent(resource.getGuid(),
                ResourceEventType.RESOURCE_UPDATED);
        resourceChangeEvent.setEventPayload((JsonObject) resourceJson);
        this.resourceChange.fire(resourceChangeEvent);

        return resourceJson;
    }

    public Resource doUpdate(String packageId, Resource resource, JsonElement resourceContent, String author,
                             String lockId, String nextRevision, boolean throwErrors) {
        JsonObject resourceTypeDefinition = null;
        if (((JsonObject) resourceContent).has("type")) {
            String type = ((JsonObject) resourceContent).get("type").getAsString();
            Configurations serviceConfig = this.configuration.get();
            resourceTypeDefinition = serviceConfig.getResourceTypeById(type);
        }
        resource.getContentPackage().getEdges();

        ContentPackage contentPackage = resource.getContentPackage();

        String p = contentPackage.getSourceLocation() + File.separator + resource.getFileNode().getPathFrom();
        Path oldPathFromResourceFile = FileSystems.getDefault().getPath(p);

        boolean jsonCapable = configuration.get().getResourceTypeById(resource.getType()).get(JSON_CAPABLE)
                .getAsBoolean();
        if (jsonCapable) {
            resourceContent = parseIncomingJsonContent(resourceContent, resource);
        } else {
            resourceContent = parseIncomingXmlContent(resourceContent, resource);
        }

        // check if the object is an assessment to extract drag and drop layouts
        if (resourceContent.getAsJsonObject().has("assessment")) {
            JsonArray assessmentItems = resourceContent.getAsJsonObject().get("assessment").getAsJsonObject()
                    .get("#array").getAsJsonArray();

            // check if the assessment has pages
            boolean hasPages = false;
            for (JsonElement item : assessmentItems) {
                if (item.getAsJsonObject().has("page")) {
                    hasPages = true;
                    break;
                }
            }

            if (hasPages) {
                // extract drag and drop layouts for each page
                for (JsonElement item : assessmentItems) {
                    if (item.getAsJsonObject().has("page")) {
                        JsonObject page = item.getAsJsonObject().getAsJsonObject("page");
                        JsonElement pageItems = page.has("#array") ? page.get("#array").getAsJsonArray()
                                : page.entrySet().iterator().next().getValue();
                        if (pageItems.isJsonArray()) {
                            try {
                                this.extractDragAndDropLayouts(resource, pageItems.getAsJsonArray());
                            } catch (Throwable t) {
                                log.error(t.toString());
                            }
                        } else {
                            processDragAndDropLayouts(resource, pageItems);
                        }
                    }
                }
            } else {
                // extract drag and drop layouts for assessment.
                try {
                    this.extractDragAndDropLayouts(resource, assessmentItems);
                } catch (Throwable t) {
                    log.error(t.toString());
                }
            }

        }

        String oldType = resource.getType();

        if (resourceTypeDefinition != null) {
            resource.setType(resourceTypeDefinition.get("id").getAsString());
        }

        // Parse update payload into final xml and json documents
        Map<String, String> contentValues = contentValues(resourceContent, resource, jsonCapable);

        Document document = validateXmlContent(packageId, resource, contentValues.get("xmlContent"), throwErrors);

        Gson gson = AppUtils.gsonBuilder().setPrettyPrinting().create();
        if (jsonCapable && document != null && !resource.getType().equalsIgnoreCase("x-oli-learning_objectives")
                && !resource.getType().equalsIgnoreCase("x-oli-skills_model")) {
            // Transform assessment2 and assessment2-pool models to inline-assessment model
            if (resource.getType().equalsIgnoreCase("x-oli-assessment2")
                    || resource.getType().equalsIgnoreCase("x-oli-assessment2-pool")) {
                Assessment2Transform assessment2Transform = new Assessment2Transform();
                assessment2Transform.transformToUnified(document.getRootElement());
            }
        }

        String pathFrom = resource.getFileNode().getPathFrom();
        pathFrom = pathFrom.replaceAll(oldType, resource.getType());

        String pathTo = jsonCapable ? pathFrom.substring(0, pathFrom.lastIndexOf(".xml")) + ".json" : pathFrom;
        FileNode packageNode = new FileNode(contentPackage.getVolumeLocation(), pathFrom, pathTo,
                jsonCapable ? "application/json" : "text/xml");
        resource.setFileNode(packageNode);

        if (resource.getLastSession() != null && resource.getLastSession().equals(lockId)) {
            // still in an editing session, modify the existing revision blob
            RevisionBlob revisionBlob = resource.getLastRevision().getBody();
            if (jsonCapable) {
                revisionBlob.setJsonPayload(new JsonWrapper(new JsonParser().parse(contentValues.get("content"))));
            } else {
                revisionBlob.setXmlPayload(contentValues.get("xmlContent"));
            }

            resource.getLastRevision().setBody(revisionBlob);
        } else {
            // different editing session, create a new revision and blob
            RevisionBlob revisionBlob = jsonCapable
                    ? new RevisionBlob(new JsonWrapper(new JsonParser().parse(contentValues.get("content"))))
                    : new RevisionBlob(contentValues.get("xmlContent"));

            // The client can specify a revision guid to use
            Revision revision = nextRevision != null
                    ? new Revision(resource, nextRevision, resource.getLastRevision(), revisionBlob, author)
                    : new Revision(resource, resource.getLastRevision(), revisionBlob, author);
            revision.setRevisionType(Revision.RevisionType.SYSTEM);
            resource.addRevision(revision);
            resource.setLastRevision(revision);
            resource.setLastSession(lockId);
        }

        em.merge(resource);

        boolean ldModelUpdate = author != null && author.equalsIgnoreCase("LDModel");
        updateXMLFile(contentPackage, resource, contentValues.get("xmlContent"), oldType,
                oldType.equals(resource.getType()) ? Optional.empty() : Optional.of(oldPathFromResourceFile),
                ldModelUpdate ? false : true);

        if (!ldModelUpdate) {
            edgesController.validateNonValidatedEdges(contentPackage.getGuid());
        }

        return resource;
    }

    public JsonElement softDelete(AppSecurityContext session, String packageId, String resourceId) {
        this.lockController.checkLockPermission(session, resourceId, false);
        this.securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), packageId, "name=" + packageId,
                Collections.singletonList(Scopes.EDIT_MATERIAL_ACTION));

        TypedQuery<Resource> q = em.createNamedQuery("Resource.findByGuid", Resource.class);
        q.setParameter("guid", resourceId);
        List<Resource> resultList = q.getResultList();

        if (resultList.isEmpty()) {
            String message = "ContentResource not found " + resourceId;
            throw new ResourceException(Response.Status.NOT_FOUND, resourceId, message);
        }
        Resource resource = resultList.get(0);
        ContentPackage contentPackage = resource.getContentPackage();
        Path xmlSourceFilePath = FileSystems.getDefault()
                .getPath(contentPackage.getSourceLocation() + File.separator + resource.getFileNode().getPathFrom());

        resource.setResourceState(ResourceState.DELETED);
        em.merge(resource);

        edgesController.processResourceDelete(contentPackage, resource);

        // Delete xml source file
        final Map<String, Set<Path>> paths = new HashMap<>();
        paths.put("files", new HashSet<>());
        paths.put("directories", new HashSet<>());
        try {
            if (resource.getType().equalsIgnoreCase("x-oli-organization")) {
                directoryUtils.directoryPaths(xmlSourceFilePath.getParent(), paths);
                paths.get("files").forEach(path -> {
                    svnSyncController.unlockFile(
                            FileSystems.getDefault().getPath(contentPackage.getSourceLocation()).toFile(), path);
                });
                svnSyncController.svnDelete(
                        FileSystems.getDefault().getPath(contentPackage.getSourceLocation()).toFile(),
                        xmlSourceFilePath.getParent());
                directoryUtils.deleteDirectory(xmlSourceFilePath.getParent());
            } else {
                svnSyncController.svnDelete(
                        FileSystems.getDefault().getPath(contentPackage.getSourceLocation()).toFile(),
                        xmlSourceFilePath);
                Files.deleteIfExists(xmlSourceFilePath);
            }
        } catch (IOException e) {
            log.debug("Error deleting file " + xmlSourceFilePath);
        }

        Gson gson = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();
        JsonElement resourceJson = gson.toJsonTree(resource);
        ResourceChangeEvent resourceChangeEvent = new ResourceChangeEvent(resource.getGuid(),
                ResourceEventType.RESOURCE_DELETED);
        this.resourceChange.fire(resourceChangeEvent);
        svnExecutor.submit(() -> {
            svnSyncController.updateSvnRepo(
                    FileSystems.getDefault().getPath(contentPackage.getSourceLocation()).toFile(),
                    contentPackage.getGuid());
        });
        return resourceJson;
    }

    public JsonElement fetchResourcesByFilter(AppSecurityContext session, String packageId, String action,
            JsonArray items) {
        if (!action.equalsIgnoreCase("byIds") && !action.equalsIgnoreCase("byTypes")) {
            String message = "Wrong action parameter; value should be either 'byIds' or 'byTypes' " + action;
            throw new ResourceException(Response.Status.BAD_REQUEST, null, message);
        }
        securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), packageId, "name=" + packageId,
                Collections.singletonList(Scopes.VIEW_MATERIAL_ACTION));
        // Query q;
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Resource> criteria = cb.createQuery(Resource.class);
        Root<Resource> resourceRoot = criteria.from(Resource.class);
        if (action.equalsIgnoreCase("byTypes")) {
            Set<String> types = new HashSet<>();
            items.forEach(val -> types.add(val.getAsString()));

            if (types.isEmpty()) {
                String message = "ContentResource Types cannot be empty";
                log.error(message);
                throw new ResourceException(Response.Status.BAD_REQUEST, null, message);
            }

            criteria.select(resourceRoot)
                    .where(cb.and(cb.equal(resourceRoot.get("contentPackage").get("guid"), packageId),
                            resourceRoot.get("type").in(types)));

        } else {
            Set<String> guids = new HashSet<>();
            items.forEach((val) -> guids.add(val.getAsString()));

            if (guids.isEmpty()) {
                String message = "ContentResource guid's cannot be empty";
                log.error(message);
                throw new ResourceException(Response.Status.BAD_REQUEST, null, message);
            }
            criteria.select(resourceRoot)
                    .where(cb.and(cb.equal(resourceRoot.get("contentPackage").get("guid"), packageId),
                            resourceRoot.get("guid").in(guids)));

        }

        List<Resource> resultList = em.createQuery(criteria).getResultList();

        JsonArray resourcesArray = new JsonArray();
        buildBulkResponse(resultList, resourcesArray);
        return resourcesArray;
    }

    public JsonElement fetchResourceEdges(AppSecurityContext session, String packageId, String resourceId) {
        this.securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), packageId, "name=" + packageId,
                Collections.singletonList(Scopes.EDIT_MATERIAL_ACTION));

        TypedQuery<Resource> q = em.createNamedQuery("Resource.findByGuid", Resource.class);
        q.setParameter("guid", resourceId);
        List<Resource> resultList = q.getResultList();

        if (resultList.isEmpty()) {
            String message = "ContentResource not found " + resourceId;
            throw new ResourceException(Response.Status.NOT_FOUND, resourceId, message);
        }
        Resource resource = resultList.get(0);
        ContentPackage contentPackage = resource.getContentPackage();

        List<Edge> edges = edgesController.edgesForResource(contentPackage, resource, false, true);

        if (!edges.isEmpty()) {

            Map<String, Edge> sourceIds = edges.stream().collect(Collectors.toMap(e -> {
                String[] split = e.getSourceId().split(":");
                return split[2];
            }, Function.identity()));

            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Resource> criteria = cb.createQuery(Resource.class);
            Root<Resource> resourceRoot = criteria.from(Resource.class);

            criteria.select(resourceRoot)
                    .where(cb.and(cb.equal(resourceRoot.get("contentPackage").get("guid"), packageId),
                            resourceRoot.get("id").in(sourceIds.keySet())));

            List<Resource> sources = em.createQuery(criteria).getResultList();
            sources.forEach(e -> {
                sourceIds.get(e.getId()).setSourceGuid(e.getGuid());
            });
        }

        Gson gson = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();
        JsonElement edgesJson = gson.toJsonTree(edges);
        return edgesJson;
    }

    private void updateXMLFile(ContentPackage contentPackage, Resource resource, String xmlContent, String oldType,
            Optional<Path> oldPathFromResourceFile, boolean svnSync) {
        if (oldPathFromResourceFile.isPresent()) {
            // Delete old file
            try {
                Files.deleteIfExists(oldPathFromResourceFile.get());
            } catch (IOException e) {
                log.error("Error deleting file " + oldPathFromResourceFile);
            }
        }

        String pathFrom = resource.getFileNode().getPathFrom();
        pathFrom = pathFrom.replaceAll(oldType, resource.getType());

        pathFrom = contentPackage.getSourceLocation() + File.separator + pathFrom;
        // Update XML files
        directoryUtils.createDirectories(pathFrom);

        Path pathFromResourceFile = FileSystems.getDefault().getPath(pathFrom);
        this.updateFileContent(resource, pathFromResourceFile, xmlContent);

        if (svnSync) {
            svnExecutor.submit(() -> {
                Map<String, List<File>> changedFiles = svnSyncController.updateSvnRepo(
                        FileSystems.getDefault().getPath(contentPackage.getSourceLocation()).toFile(),
                        contentPackage.getGuid());

                // check if there were any conflicts
                for (String key : changedFiles.keySet()) {
                    if (key.equals("C")) {
                        throw new ResourceException(Response.Status.CONFLICT, resource.getId(), "svn update conflict");
                    }
                }
            });
        }

    }

    private void updateLayoutXMLFile(ContentPackage contentPackage, Resource resource, String layoutPath,
            String xmlContent) {
        Path resourcePath = Paths.get(resource.getContentPackage().getSourceLocation() + File.separator
                + resource.getFileNode().getPathFrom());

        Path relativeLayoutPath = resourcePath.resolveSibling(Paths.get(layoutPath)).normalize();

        this.updateFileContent(resource, relativeLayoutPath, xmlContent);

        svnExecutor.submit(() -> {
            Map<String, List<File>> changedFiles = svnSyncController.updateSvnRepo(
                    FileSystems.getDefault().getPath(contentPackage.getSourceLocation()).toFile(),
                    contentPackage.getGuid());

            // check if there were any conflicts
            for (String key : changedFiles.keySet()) {
                if (key.equals("C")) {
                    throw new ResourceException(Response.Status.CONFLICT, resource.getId(), "svn update conflict");
                }
            }
        });
    }

    private void updateFileContent(Resource resource, Path pathFromResourceFile, String content) {
        try {
            Files.write(pathFromResourceFile, content.getBytes());
        } catch (IOException e) {
            final String message = "Error: unable to write content file located at - " + pathFromResourceFile;
            log.error(message, e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, resource.getId(), message);
        }
    }

    private JsonElement parseIncomingJsonContent(JsonElement resourceContent, Resource resource) {
        JsonArray docs = ((JsonObject) resourceContent).getAsJsonArray("doc");
        resourceContent = docs.get(0);
        ResourceJsonReader.parseResourceJson(resource, (JsonObject) resourceContent);
        return resourceContent;
    }

    private JsonElement parseIncomingXmlContent(JsonElement resourceContent, Resource resource) {
        JsonArray docs = ((JsonObject) resourceContent).getAsJsonArray("doc");
        JsonPrimitive asJsonPrimitive = docs.get(0).getAsJsonPrimitive();
        resourceContent = asJsonPrimitive;
        SAXBuilder builder = AppUtils.validatingSaxBuilder();
        builder.setExpandEntities(false);
        Document document = null;
        try {
            document = builder.build(new StringReader(asJsonPrimitive.getAsString()));
        } catch (JDOMException | IOException e) {
            String message = "Unable to update resource " + e.getMessage();
            log.error(message);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, resource.getGuid(), message);
        }

        ResourceXmlReader.documentToResource(resource, document);

        return resourceContent;
    }

    private Document validateXmlContent(String packageId, Resource resource, String xmlContent, boolean throwErrors) {
        // Validate xmlContent
        SAXBuilder builder = AppUtils.validatingSaxBuilder();
        builder.setExpandEntities(false);
        try {

            resetResourceErrors(resource);

            Document build = builder.build(new StringReader(xmlContent));
            if (configuration.get().isContentServiceDebugEnabled()) {
                log.info("resource.getType() " + resource.getType());
                // log.info("XML Content \n" + xmlContent);
            }
            ResourceValidator validator = new BaseResourceValidator();
            validator.initValidator(resource, build, throwErrors);
            validator.validate();
            Configurations serviceConfig = this.configuration.get();
            JsonObject resourceTypeDefinition = serviceConfig.getResourceTypeById(resource.getType());
            if (resourceTypeDefinition == null || !resourceTypeDefinition.has("validatorClass")) {
                String message = resource.getType() + " has no validator class";
                log.error(message);
            } else {
                String validatorClass = resourceTypeDefinition.get("validatorClass").getAsString();
                try {
                    Class<?> aClass = Class.forName(validatorClass);
                    ResourceValidator resourceValidator = (ResourceValidator) AppUtils.lookupCDIBean(aClass);
                    resourceValidator.initValidator(resource, build, throwErrors);
                    resourceValidator.validate();
                } catch (ClassNotFoundException e) {
                    String message = resource.getType() + " validator class not found";
                    log.error(message);
                }
            }

            return build;
        } catch (JDOMException | RuntimeException e) {
            log.error("\n\nValidation issues file=" + resource.getFileNode().getPathFrom() + "\n" + e.getMessage()
                    + "\n" + xmlContent, e);
            throw new ResourceException(Response.Status.BAD_REQUEST, packageId, e.getMessage());
        } catch (Throwable e) {
            log.error("Validation issues file=" + resource.getFileNode().getPathFrom() + "\n" + e.getMessage(), e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, packageId, e.getMessage());
        }
    }

    private void resetResourceErrors(Resource resource) {
        JsonObject errors = new JsonObject();
        errors.addProperty("resourceErrors", resource.getId() + " Title: " + resource.getTitle());
        JsonArray errorList = new JsonArray();
        errors.add("errorList", errorList);
        resource.setErrors(new JsonWrapper(errors));
    }

    private void buildBulkResponse(List<Resource> resultList, JsonArray resourcesArray) {
        Gson gson = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();
        for (Resource resource : resultList) {
            JsonElement resourceJson = gson.toJsonTree(resource);
            boolean jsonCapable = configuration.get().getResourceTypeById(resource.getType()).get(JSON_CAPABLE)
                    .getAsBoolean();
            if (resource.getLastRevision() != null) {
                jsonCapable = resource.getLastRevision().getBody().getXmlPayload() == null;
            }
            String path = resource.getFileNode().getVolumeLocation() + File.separator
                    + resource.getFileNode().getPathTo();
            if (jsonCapable) {
                if (resource.getLastRevision() != null) {
                    ((JsonObject) resourceJson).add("doc",
                            resource.getLastRevision().getBody().getJsonPayload().getJsonObject());
                    resourcesArray.add(resourceJson);
                } else {
                    try (FileReader fileReader = new FileReader(path)) {
                        JsonParser parser = new JsonParser();
                        JsonElement jsonElement = parser.parse(fileReader);
                        JsonObject jsonObject = jsonElement.getAsJsonObject();
                        ((JsonObject) resourceJson).add("doc", jsonObject);
                        resourcesArray.add(resourceJson);
                    } catch (Exception ex) {
                        log.error(ex.getLocalizedMessage());
                        throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, null,
                                ex.getLocalizedMessage());
                    }
                }
            } else {
                if (resource.getLastRevision() != null) {
                    ((JsonObject) resourceJson).add("doc",
                            new JsonPrimitive(resource.getLastRevision().getBody().getXmlPayload()));
                    resourcesArray.add(resourceJson);
                } else {
                    try {
                        String s = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
                        ((JsonObject) resourceJson).add("doc", new JsonPrimitive(s));
                        resourcesArray.add(resourceJson);
                    } catch (IOException ex) {
                        log.error(ex.getLocalizedMessage());
                        throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, null,
                                ex.getLocalizedMessage());
                    }
                }
            }
        }
    }

    private Map<String, String> contentValues(JsonElement resourceContent, Resource resource, boolean jsonCapable) {
        Map<String, String> values = new HashMap<>();
        String content;
        String xmlContent;

        if (jsonCapable) {
            Gson gson = AppUtils.gsonBuilder().setPrettyPrinting().create();
            content = gson.toJson(resourceContent);
            ResourceToXml resourceToXml = new ResourceToXml();
            resourceToXml.setConfig(configuration.get());
            xmlContent = resourceToXml.resourceToXml(resource.getType(), content);
        } else {
            content = resourceContent.getAsString();
            xmlContent = AppUtils.escapeAmpersand(content);
        }
        values.put("content", content);
        values.put("xmlContent", xmlContent);
        return values;
    }

    private Resource findContentResource(String resourceGuid) {
        Resource resource = null;
        try {
            resource = em.find(Resource.class, resourceGuid);
            if (resource == null) {
                String message = "Error: resource requested was not found " + resourceGuid;
                log.error(message);
                throw new ResourceException(Response.Status.NOT_FOUND, resourceGuid, message);
            }
        } catch (IllegalArgumentException e) {
            String message = "Server Error while locating resource " + resourceGuid;
            log.error(message);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, resourceGuid, message);
        }
        return resource;
    }
}