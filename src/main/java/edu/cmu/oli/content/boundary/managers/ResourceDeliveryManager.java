package edu.cmu.oli.content.boundary.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import edu.cmu.oli.assessment.InlineAssessmentDelivery;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.ContentServiceException;
import edu.cmu.oli.content.ResourceException;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.controllers.DeployController;
import edu.cmu.oli.content.controllers.SVNSyncController;
import edu.cmu.oli.content.controllers.ThinPreviewController;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.ServerName;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import edu.cmu.oli.content.models.persistance.entities.Resource;
import edu.cmu.oli.content.security.AppSecurityContext;
import edu.cmu.oli.content.security.AppSecurityController;
import edu.cmu.oli.content.security.Scopes;
import edu.cmu.oli.content.security.Secure;
import org.jdom2.Document;
import org.jdom2.input.SAXBuilder;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static edu.cmu.oli.content.security.Roles.ADMIN;
import static edu.cmu.oli.content.security.Roles.CONTENT_DEVELOPER;

/**
 * @author Raphael Gachuhi
 */
@Stateless
public class ResourceDeliveryManager {

    @Inject
    @Logging
    Logger log;

    @PersistenceContext
    EntityManager em;

    @Inject
    @Secure
    AppSecurityController securityManager;

    @Inject
    @ConfigurationCache
    Instance<Configurations> configuration;

    @Inject
    SVNSyncController svnSyncController;

    @Inject
    DeployController deployController;

    @Inject
    ThinPreviewController thinPreviewController;

    public JsonElement previewResource(AppSecurityContext session, String packageIdOrGuid, String resourceId,
            boolean redeploy) {
        ContentPackage contentPackage = findContentPackage(packageIdOrGuid);
        this.securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), contentPackage.getGuid(),
                "name=" + contentPackage.getGuid(), Collections.singletonList(Scopes.VIEW_MATERIAL_ACTION));

        String serverurl = System.getenv().get("SERVER_URL");
        ServerName previewServer;
        if (serverurl.contains("dev.local")) {
            previewServer = ServerName.dev;
        } else {
            previewServer = ServerName.prod;
        }

        return deployController.deployPackage(session, resourceId, previewServer, redeploy);
    }

    public String quickPreview(AppSecurityContext session, String packageIdOrGuid, String resourceId) {
        // ContentPackage contentPackage = findContentPackage(packageIdOrGuid);
        // this.securityManager.authorize(session,
        // Arrays.asList(ADMIN, CONTENT_DEVELOPER),
        // contentPackage.getGuid(), "name=" + contentPackage.getGuid(),
        // Collections.singletonList(Scopes.VIEW_MATERIAL_ACTION));

        TypedQuery<Resource> q = em.createNamedQuery("Resource.findByGuid", Resource.class);
        q.setParameter("guid", resourceId);
        List<Resource> resultList = q.getResultList();

        if (resultList.isEmpty()) {
            String message = "ContentResource not found " + resourceId;
            throw new ResourceException(Response.Status.NOT_FOUND, resourceId, message);
        }

        Resource resource = resultList.get(0);
        String pkgTheme = resource.getContentPackage().getTheme();

        if (pkgTheme == null) {
            pkgTheme = "none";
        }
        boolean pkgThemeMissing = true;
        String defaulTheme = null;
        Configurations configurations = configuration.get();
        for (JsonElement jsonElement : configurations.getThemes()) {
            JsonObject theme = jsonElement.getAsJsonObject();
            String id = theme.get("id").getAsString();
            if (theme.has("default") && theme.get("default").getAsBoolean()) {
                defaulTheme = id;
            }
            if (id.equalsIgnoreCase(pkgTheme)) {
                pkgThemeMissing = false;
            }
        }

        if (pkgThemeMissing) {
            pkgTheme = defaulTheme;
            resource.getContentPackage().setTheme(pkgTheme);
        }

        String serverurl = System.getenv().get("SERVER_URL") + "/";
        try {
            return thinPreviewController.thinPreview(resource, pkgTheme, serverurl);
        } catch (ContentServiceException e) {
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, null, e.getLocalizedMessage());
        }

    }

    public JsonElement inlineAssessmentBulkPageContext(String userGuid, JsonObject pageContext, String serverUrl) {
        JsonArray contextGuids = pageContext.get("contextGuid").getAsJsonArray();
        List<String> resourceGuids = new ArrayList<>();
        contextGuids.forEach(item -> {
            resourceGuids.add(item.getAsString());
        });

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Resource> criteria = cb.createQuery(Resource.class);
        Root<Resource> resourceRoot = criteria.from(Resource.class);
        criteria.select(resourceRoot).where(resourceRoot.get("guid").in(resourceGuids));

        List<Resource> resourceList = em.createQuery(criteria).getResultList();
        InlineAssessmentDelivery inlineAssessmentDelivery = AppUtils.lookupCDIBean(InlineAssessmentDelivery.class);

        JsonObject bulkJson = new JsonObject();
        resourceList.forEach(resource -> {
            Path resourcePath = Paths.get(resource.getContentPackage().getSourceLocation() + File.separator
                    + resource.getFileNode().getPathFrom());

            Document srcDoc = null;
            try {
                String resourceXml = new String(Files.readAllBytes(resourcePath), StandardCharsets.UTF_8);
                SAXBuilder builder = AppUtils.validatingSaxBuilder();
                builder.setExpandEntities(false);
                srcDoc = builder.build(new StringReader(resourceXml));

            } catch (Exception ex) {
                log.error(ex.getLocalizedMessage(), ex);
                throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, null, ex.getLocalizedMessage());
            }

            if (resource.getContentPackage().getTheme() == null) {
                Configurations configurations = configuration.get();
                configurations.getThemes().forEach(e -> {
                    JsonObject theme = e.getAsJsonObject();
                    if (theme.has("default") && theme.get("default").getAsBoolean()) {
                        resource.getContentPackage().setTheme(theme.get("id").getAsString());
                        return;
                    }
                });
            }

            JsonObject metaInfo = new JsonObject();
            metaInfo.addProperty("pageNumber", 1);
            JsonElement deliver = inlineAssessmentDelivery.deliver(resource, srcDoc, serverUrl,
                    resource.getContentPackage().getTheme(), metaInfo);
            bulkJson.add(resource.getGuid(), deliver);
        });

        JsonObject bulkList = new JsonObject();
        bulkList.add("bulkAssessmentList", bulkJson);
        return bulkList;
    }

    public JsonElement inlineAssessmentNextPageContext(String resourceId, String userGuid, int attemptNumber,
            int pageNumber, String requestMode, boolean start, String serverUrl) {

        TypedQuery<Resource> q = em.createNamedQuery("Resource.findByGuid", Resource.class);
        q.setParameter("guid", resourceId);
        List<Resource> resultList = q.getResultList();

        if (resultList.isEmpty()) {
            String message = "ContentResource not found " + resourceId;
            throw new ResourceException(Response.Status.NOT_FOUND, resourceId, message);
        }
        Resource resource = resultList.get(0);
        InlineAssessmentDelivery inlineAssessmentDelivery = AppUtils.lookupCDIBean(InlineAssessmentDelivery.class);

        Path resourcePath = Paths.get(resource.getContentPackage().getSourceLocation() + File.separator
                + resource.getFileNode().getPathFrom());

        Document srcDoc = null;
        try {
            String resourceXml = new String(Files.readAllBytes(resourcePath), StandardCharsets.UTF_8);
            SAXBuilder builder = AppUtils.validatingSaxBuilder();
            builder.setExpandEntities(false);
            srcDoc = builder.build(new StringReader(resourceXml));

        } catch (Exception ex) {
            log.error(ex.getLocalizedMessage(), ex);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, null, ex.getLocalizedMessage());
        }

        if (resource.getContentPackage().getTheme() == null) {
            Configurations configurations = configuration.get();
            configurations.getThemes().forEach(e -> {
                JsonObject theme = e.getAsJsonObject();
                if (theme.has("default") && theme.get("default").getAsBoolean()) {
                    resource.getContentPackage().setTheme(theme.get("id").getAsString());
                    return;
                }
            });
        }

        JsonObject metaInfo = new JsonObject();
        metaInfo.addProperty("pageNumber", pageNumber);
        metaInfo.addProperty("requestMode", requestMode);
        metaInfo.addProperty("attemptNumber", attemptNumber);
        metaInfo.addProperty("start", start);
        JsonElement deliver = inlineAssessmentDelivery.deliver(resource, srcDoc, serverUrl,
                resource.getContentPackage().getTheme(), metaInfo);

        return deliver;
    }

    public JsonElement inlineAssessmentResponsesContext(String resourceId, String userGuid, int attemptNumber,
            String questionId, boolean start, JsonObject responses, String serverUrl) {
        TypedQuery<Resource> q = em.createNamedQuery("Resource.findByGuid", Resource.class);
        q.setParameter("guid", resourceId);
        List<Resource> resultList = q.getResultList();

        if (resultList.isEmpty()) {
            String message = "ContentResource not found " + resourceId;
            throw new ResourceException(Response.Status.NOT_FOUND, resourceId, message);
        }
        Resource resource = resultList.get(0);
        InlineAssessmentDelivery inlineAssessmentDelivery = AppUtils.lookupCDIBean(InlineAssessmentDelivery.class);

        Path resourcePath = Paths.get(resource.getContentPackage().getSourceLocation() + File.separator
                + resource.getFileNode().getPathFrom());

        Document srcDoc = null;
        try {
            String resourceXml = new String(Files.readAllBytes(resourcePath), StandardCharsets.UTF_8);
            SAXBuilder builder = AppUtils.validatingSaxBuilder();
            builder.setExpandEntities(false);
            srcDoc = builder.build(new StringReader(resourceXml));

        } catch (Exception ex) {
            log.error(ex.getLocalizedMessage(), ex);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, null, ex.getLocalizedMessage());
        }

        if (resource.getContentPackage().getTheme() == null) {
            Configurations configurations = configuration.get();
            configurations.getThemes().forEach(e -> {
                JsonObject theme = e.getAsJsonObject();
                if (theme.has("default") && theme.get("default").getAsBoolean()) {
                    resource.getContentPackage().setTheme(theme.get("id").getAsString());
                    return;
                }
            });
        }
        JsonElement evalResponse = inlineAssessmentDelivery.evaluateResponses(resource, srcDoc, serverUrl,
                resource.getContentPackage().getTheme(), userGuid, attemptNumber, questionId, start, responses);
        return evalResponse;
    }

    public JsonElement availableThemes(AppSecurityContext session, String packageIdOrGuid) {
        ContentPackage contentPackage = findContentPackage(packageIdOrGuid);
        this.securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), contentPackage.getGuid(),
                "name=" + contentPackage.getGuid(), Collections.singletonList(Scopes.VIEW_MATERIAL_ACTION));

        return this.configuration.get().getThemes();
    }

    public JsonElement updatePackageTheme(AppSecurityContext appSecurityContext, String packageIdOrGuid,
            String themeId) {
        String themeFound = null;
        JsonArray themes = configuration.get().getThemes();
        Iterator<JsonElement> it = themes.iterator();
        while (it.hasNext()) {
            String theme = it.next().getAsJsonObject().get("id").getAsString();
            if (theme.equalsIgnoreCase(themeId)) {
                themeFound = theme;
                break;
            }
        }
        if (themeFound == null) {
            String message = "Unable to locate theme " + themeId;
            log.error(message);
            throw new ResourceException(Response.Status.NOT_FOUND, packageIdOrGuid, message);
        }

        ContentPackage contentPackage = findContentPackage(packageIdOrGuid);
        contentPackage.setTheme(themeFound);
        em.merge(contentPackage);
        return new JsonPrimitive("Theme updated successfully");
    }

    // packageIdentifier is db guid or packageId-version combo
    private ContentPackage findContentPackage(String packageIdOrGuid) {
        ContentPackage contentPackage = null;
        Boolean isIdAndVersion = packageIdOrGuid.contains("-");
        try {
            if (isIdAndVersion) {
                String pkgId = packageIdOrGuid.substring(0, packageIdOrGuid.lastIndexOf("-"));
                String version = packageIdOrGuid.substring(packageIdOrGuid.lastIndexOf("-") + 1);
                TypedQuery<ContentPackage> q = em
                        .createNamedQuery("ContentPackage.findByIdAndVersion", ContentPackage.class)
                        .setParameter("id", pkgId).setParameter("version", version);

                contentPackage = q.getResultList().isEmpty() ? null : q.getResultList().get(0);
            } else {
                String packageGuid = packageIdOrGuid;
                contentPackage = em.find(ContentPackage.class, packageGuid);
            }

            if (contentPackage == null) {
                String message = "Error: package requested was not found " + packageIdOrGuid;
                log.error(message);
                throw new ResourceException(Response.Status.NOT_FOUND, packageIdOrGuid, message);
            }

        } catch (IllegalArgumentException e) {
            String message = "Server Error while locating package " + packageIdOrGuid;
            log.error(message);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, packageIdOrGuid, message);
        }
        return contentPackage;
    }
}
