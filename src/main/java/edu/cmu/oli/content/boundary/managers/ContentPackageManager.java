package edu.cmu.oli.content.boundary.managers;

import com.airhacks.porcupine.execution.boundary.Dedicated;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.ContentServiceException;
import edu.cmu.oli.content.DirectoryUtils;
import edu.cmu.oli.content.ResourceException;
import edu.cmu.oli.content.boundary.ResourceChangeEvent;
import edu.cmu.oli.content.boundary.ResourceChangeEvent.ResourceEventType;
import edu.cmu.oli.content.boundary.endpoints.ContentPackageResource;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.contentfiles.readers.XmlToContentPackage;
import edu.cmu.oli.content.controllers.*;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.DeployStage;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.*;
import edu.cmu.oli.content.resource.builders.BuildException;
import edu.cmu.oli.content.resource.builders.ContentPkgJsonReader;
import edu.cmu.oli.content.resource.builders.ContentPkgXmlReader;
import edu.cmu.oli.content.security.*;
import org.hibernate.jpa.QueryHints;
import org.jboss.ejb3.annotation.TransactionTimeout;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.naming.InitialContext;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static edu.cmu.oli.content.AppUtils.generateUID;
import static edu.cmu.oli.content.security.Roles.ADMIN;
import static edu.cmu.oli.content.security.Roles.CONTENT_DEVELOPER;

/**
 * @author Raphael Gachuhi
 */
@Stateless
public class ContentPackageManager {

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
    PackageFileController packageFileController;

    @Inject
    SVNImportController svnImportController;

    @Inject
    SVNSyncController svnSyncController;

    @Inject
    EdgesController edgesController;

    @Inject
    @ConfigurationCache
    Instance<Configurations> config;

    @Inject
    Event<ResourceChangeEvent> resourceChange;

    @Inject
    DirectoryUtils directoryUtils;

    @Inject
    XmlToContentPackage xmlToContentPackage;

    @Inject
    @Dedicated("svnExecutor")
    ExecutorService svnExecutor;

    @Inject
    @Dedicated("versionBatchExec")
    ExecutorService versionExec;

    @Inject
    DeployController deployController;

    public JsonElement all(AppSecurityContext session) {
        securityManager.authorize(session, Collections.singletonList(ADMIN), null, null, null);

        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findAll", ContentPackage.class);
        List<ContentPackage> resultList = q.getResultList();

        resultList.addAll(SVNImportController.importPending.values());

        Gson gson = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();
        return gson.toJsonTree(resultList, new TypeToken<ArrayList<ContentPackage>>() {
        }.getType());
    }

    public JsonElement editablePackages(AppSecurityContext session) {
        List<ContentPackage> resultList = new ArrayList<>();
        try {
            Set<String> permittedPkgs = securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER),
                    null, "type=" + ContentPackageResource.resourceType, Arrays.asList(Scopes.VIEW_MATERIAL_ACTION));
            if (permittedPkgs.contains("all")) {
                TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findAll", ContentPackage.class);
                q.setHint(QueryHints.HINT_READONLY, true);
                resultList = q.getResultList();
            } else {
                resultList = fetchPackagesByGuids(permittedPkgs, true);
            }
            resultList = resultList.stream().filter(cp -> {
                return (cp.getVisible() == null || cp.getVisible()) && !cp.getBuildStatus().equals(BuildStatus.FAILED);
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        resultList.addAll(SVNImportController.importPending.values());

        Gson gson = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();

        JsonElement jsonElement = gson.toJsonTree(resultList, new TypeToken<ArrayList<ContentPackage>>() {
        }.getType());

        return jsonElement;
    }

    private List<ContentPackage> fetchPackagesByGuids(Set<String> packageGuids, boolean readOnly) {

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<ContentPackage> criteria = cb.createQuery(ContentPackage.class);
        Root<ContentPackage> contentPackageRoot = criteria.from(ContentPackage.class);

        criteria.select(contentPackageRoot).where(contentPackageRoot.get("guid").in(packageGuids));
        TypedQuery<ContentPackage> query = em.createQuery(criteria);
        if (readOnly) {
            query.setHint(QueryHints.HINT_READONLY, true);
        }
        return query.getResultList();
    }

    public JsonElement setPackageEditable(AppSecurityContext session, boolean editable, JsonArray packageGuids) {
        securityManager.authorize(session, Collections.singletonList(ADMIN), null, null, null);

        Set<String> packageGuidSet = new HashSet<>();
        packageGuids.forEach(id -> {
            if (!id.isJsonNull()) {
                packageGuidSet.add(id.getAsString());
            }
        });

        fetchPackagesByGuids(packageGuidSet, false).forEach(contentPackage -> contentPackage.setEditable(editable));

        JsonObject locked = new JsonObject();
        locked.addProperty("editable", editable);
        locked.add("packages", packageGuids);

        return locked;
    }

    public JsonElement setPackageVisible(AppSecurityContext session, boolean visible, JsonArray packageGuids) {
        securityManager.authorize(session, Collections.singletonList(ADMIN), null, null, null);

        Set<String> packageGuidSet = new HashSet<>();
        packageGuids.forEach(id -> {
            if (!id.isJsonNull()) {
                packageGuidSet.add(id.getAsString());
            }
        });

        fetchPackagesByGuids(packageGuidSet, false).forEach(contentPackage -> contentPackage.setVisible(visible));

        JsonObject hidden = new JsonObject();
        hidden.addProperty("visible", visible);
        hidden.add("packages", packageGuids);

        return hidden;
    }

    public JsonElement loadPackage(AppSecurityContext session, String packageIdOrGuid) {
        ContentPackage contentPackage = findContentPackage(packageIdOrGuid);
        securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), contentPackage.getGuid(),
                "name=" + contentPackage.getGuid(), Collections.singletonList(Scopes.VIEW_MATERIAL_ACTION));

        JsonObject contentPkgJson = (JsonObject) detailedPackage(contentPackage);

        contentPkgJson.add("lock",
                this.lockController.getJsonLockForResource(session, contentPackage.getGuid(), false));

        fireResourceChangeEvent(contentPackage.getGuid(), ResourceEventType.RESOURCE_REQUESTED, null);

        return contentPkgJson;
    }

    public JsonElement updatePackage(AppSecurityContext session, String packageIdOrGuid, JsonObject body) {
        ContentPackage contentPackage = findContentPackage(packageIdOrGuid);
        securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), contentPackage.getGuid(),
                "name=" + contentPackage.getGuid(), Collections.singletonList(Scopes.EDIT_MATERIAL_ACTION));
        this.lockController.checkLockPermission(session, contentPackage.getGuid(), true);

        JsonArray docs = body.getAsJsonArray("doc");
        body = ((JsonObject) docs.get(0)).getAsJsonObject("package");

        // Update ContentPackage
        ContentPkgJsonReader.parsePackageJson(contentPackage, body, true);
        contentPackage.setDoc(new JsonWrapper(body));
        em.merge(contentPackage);

        // packageFileController.updatePackgeJson(packageId, contentPackage);

        packageFileController.updatePackgeXmlFile(contentPackage);

        svnExecutor.submit(() -> svnSyncController.updateSvnRepo(
                FileSystems.getDefault().getPath(contentPackage.getSourceLocation()).toFile(),
                contentPackage.getGuid()));

        Gson gson = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();

        JsonElement jsonElement = gson.toJsonTree(contentPackage);

        fireResourceChangeEvent(contentPackage.getGuid(), ResourceEventType.RESOURCE_UPDATED, jsonElement);

        JsonObject contentPkgJson = (JsonObject) detailedPackage(contentPackage);
        contentPkgJson.add("lock",
                this.lockController.getJsonLockForResource(session, contentPackage.getGuid(), false));

        return contentPkgJson;
    }

    public JsonElement createPackage(AppSecurityContext session, JsonObject body) {
        securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), null, null, null);

        JsonArray docs = body.getAsJsonArray("doc");
        body = ((JsonObject) docs.get(0)).getAsJsonObject("package");

        if (!body.has("@id") || !body.has("@version")) {
            String message = "Package creation data must contain '@id' and '@version' information";
            log.error(message);
            throw new ResourceException(Response.Status.BAD_REQUEST, "package", message);
        }
        String id = body.get("@id").getAsString();
        id = id.substring(0, id.lastIndexOf("-"));
        id = generateUniquePackageId(id, body.get("@version").getAsString());
        body.addProperty("@id", id);

        ContentPackage contentPackage = new ContentPackage();
        xmlToContentPackage.setContentPackage(contentPackage);
        contentPackage.setBuildStatus(BuildStatus.PROCESSING);
        ContentPkgJsonReader.parsePackageJson(contentPackage, body, false);

        // The contentPackage id should be limited to alphanumeric, underscore or dash
        // characters only
        id = contentPackage.getId().replaceAll("[^a-zA-Z0-9-_]", "");
        if (id.startsWith("-") || id.startsWith("_")) {
            id = id.substring(1);
        }
        if (Character.isDigit(id.charAt(0))) {
            id = "n" + id;
        }
        contentPackage.setId(id);
        contentPackage.setPackageFamily(id);
        contentPackage.setDoc(new JsonWrapper(body));

        Configurations configurations = config.get();
        configurations.getThemes().forEach(e -> {
            JsonObject theme = e.getAsJsonObject();
            if (theme.has("default") && theme.get("default").getAsBoolean()) {
                contentPackage.setTheme(theme.get("id").getAsString());
                return;
            }
        });

        boolean alreadyExist = validatePackageDoesNotAlreadyExist(contentPackage.getId(), contentPackage.getVersion());
        if (alreadyExist) {
            String message = "Error creating new course package: a package with ID " + id + " and version "
                    + contentPackage.getVersion() + " already exists.";
            log.error(message);
            throw new ResourceException(Response.Status.BAD_REQUEST, id + "_" + contentPackage.getVersion(), message);
        }

        em.persist(contentPackage);
        // Force flush to get autogenerated guid
        em.flush();

        securityManager.createResource(contentPackage.getGuid(), "/packages/" + contentPackage.getGuid(),
                ContentPackageResource.resourceType,
                Arrays.asList(Scopes.VIEW_MATERIAL_ACTION, Scopes.EDIT_MATERIAL_ACTION));

        // Clone new package from an existing template
        Path newSourceDir = cloneTemplate(contentPackage);

        Path pkgXml = newSourceDir.resolve("content/package.xml");
        Path orgXml = newSourceDir.resolve("organizations/default/organization.xml");

        if (!packageFileController.fileExists(orgXml)) {
            String message = "Error: default/organization.xml does not exist " + orgXml.toString();
            log.error(message);
            throw new ResourceException(Response.Status.BAD_REQUEST,
                    contentPackage.getId() + "_" + contentPackage.getVersion(), message);
        }

        svnSyncController.cloneRemoteTemplateSvnRepo(contentPackage.getId());
        String repoURL = System.getenv().get("svn_projects") + contentPackage.getId() + "/trunk";

        try {
            repoURL = svnImportController.createEditorBranch(repoURL, contentPackage.getVersion());
        } catch (ContentServiceException e) {
            // No-op
        }

        svnSyncController.switchSvnRepo(newSourceDir.toFile(), repoURL);

        // Replace cloned package and organization ids with corresponding values from
        // new package
        packageFileController.updatePackageXmlIdAndVersion(pkgXml, contentPackage.getId(), contentPackage.getVersion(),
                contentPackage.getTitle());
        packageFileController.updateOrganizationXmlIdAndVersion(orgXml,
                contentPackage.getId() + "-" + contentPackage.getVersion() + "_default", "1.0");

        buildContentPackage(contentPackage, newSourceDir, pkgXml);

        Map<String, List<String>> userPermission = new HashMap<>();
        userPermission.put(contentPackage.getGuid(), Arrays.asList(Scopes.VIEW_MATERIAL_ACTION.toString(),
                Scopes.EDIT_MATERIAL_ACTION.toString(), "ContentPackage", contentPackage.getTitle()));
        securityManager.updateUserAttributes(session.getPreferredUsername(), userPermission, null);

        String packageGuid = contentPackage.getGuid();
        edgesController.validateAllEdges(contentPackage);

        JsonObject contentPkgJson = (JsonObject) detailedPackage(contentPackage);
        contentPkgJson.add("lock", this.lockController.getJsonLockForResource(session, contentPackage.getGuid(), true));

        syncWithSVN(session, contentPackage, newSourceDir, packageGuid);

        return contentPkgJson;
    }

    private void buildContentPackage(ContentPackage contentPackage, Path newSourceDir, Path pkgXml) {
        try {
            xmlToContentPackage.processPackage(newSourceDir, pkgXml);
            xmlToContentPackage.walkContentFolder(newSourceDir);
            xmlToContentPackage.parseContent(newSourceDir);
            xmlToContentPackage.finalizePackageImport();
            contentPackage.setBuildStatus(BuildStatus.READY);

            em.merge(contentPackage);
        } catch (Throwable e) {
            final String message = "Error while processing course content package import " + newSourceDir.toString();
            log.error(message, e);
            if (xmlToContentPackage != null && contentPackage != null) {
                contentPackage.setBuildStatus(BuildStatus.FAILED);
                em.merge(contentPackage);
            }
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR,
                    contentPackage.getId() + "_" + contentPackage.getVersion(), message);
        }
    }

    private void syncWithSVN(AppSecurityContext session, ContentPackage contentPackage, Path newSourceDir,
            String packageGuid) {
        svnExecutor.submit(() -> {
            svnSyncController.updateSvnRepo(newSourceDir.toFile(), packageGuid);
            Optional<String> svnRepositoryUrl = svnSyncController.svnRepositoryUrl(newSourceDir.toFile());
            if (svnRepositoryUrl.isPresent()) {
                String previewSetupUrl = System.getenv().get("preview_setup_url");
                if (previewSetupUrl == null) {
                    String message = "Development server not configured";
                    log.error(message);
                    return;
                }
                WebTarget target = ClientBuilder.newClient().target(previewSetupUrl);
                JsonObject previewSetupInfo = new JsonObject();
                previewSetupInfo.addProperty("svnUrl", svnRepositoryUrl.get());
                previewSetupInfo.addProperty("packageId", contentPackage.getId());
                previewSetupInfo.addProperty("packageVersion", contentPackage.getVersion());
                JsonObject userInfo = new JsonObject();
                userInfo.addProperty("firstName", session.getFirstName());
                userInfo.addProperty("lastName", session.getLastName());
                userInfo.addProperty("email", session.getEmail());
                userInfo.addProperty("userName", session.getPreferredUsername());
                previewSetupInfo.add("userInfo", userInfo);
                Response response = target.request(MediaType.APPLICATION_JSON)
                        .post(Entity.json(AppUtils.gsonBuilder().create().toJson(previewSetupInfo)));
                javax.json.JsonObject jsonObject = response.readEntity(javax.json.JsonObject.class);
                JsonParser jsonParser = new JsonParser();
                String s = AppUtils.toString(jsonObject);
                log.debug("Json returned from development server 'previewResource/setup' call " + s);
            }
        });
    }

    private Path cloneTemplate(ContentPackage contentPackage) {
        String sourceLocation = this.config.get().getContentSourceXml();

        Path sourceDir = Paths.get(sourceLocation + File.separator + "template");
        if (!packageFileController.fileExists(sourceDir)) {
            String message = "Error: Template package does not exist " + sourceDir.toString();
            log.error(message);
            throw new ResourceException(Response.Status.BAD_REQUEST,
                    contentPackage.getId() + "_" + contentPackage.getVersion(), message);
        }

        Path targetDir = Paths
                .get(sourceLocation + File.separator + contentPackage.getId() + "_" + AppUtils.generateUID(12));
        while (packageFileController.fileExists(targetDir)) {
            targetDir = Paths
                    .get(sourceLocation + File.separator + contentPackage.getId() + "_" + AppUtils.generateUID(12));
        }

        Path newSourceDir = targetDir;
        try {
            directoryUtils.copyDirectory(sourceDir, newSourceDir, false);
        } catch (IOException e) {
            final String message = "Unable to copy  " + sourceDir.toString();
            log.error(message, e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR,
                    contentPackage.getId() + "_" + contentPackage.getVersion(), message);
        }
        return newSourceDir;
    }

    public JsonElement importPackage(AppSecurityContext session, JsonObject packageInfo) {
        securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), null, null, null);

        String repositoryUrl = packageInfo.get("repositoryUrl").getAsString();

        Optional<String> packageXmlFile = svnImportController.fetchRemotePackageXmlFile(repositoryUrl);

        if (!packageXmlFile.isPresent()) {
            String message = "Error importing package: No course package exists at '" + repositoryUrl + "'.";
            log.error(message);
            throw new ResourceException(Response.Status.BAD_REQUEST, repositoryUrl, message);
        }
        log.debug("Remote Package XML file: " + packageXmlFile.get());

        SAXBuilder builder = AppUtils.validatingSaxBuilder();
        builder.setExpandEntities(false);
        Document document = null;
        try {
            document = builder.build(new StringReader(packageXmlFile.get()));
        } catch (JDOMException | IOException e) {
            String message = "Error importing package: There's a problem with the 'package.xml' file for this course.";
            log.error(message, e);
            throw new ResourceException(Response.Status.BAD_REQUEST, repositoryUrl, message);
        }

        final ContentPackage contentPackage = new ContentPackage();
        contentPackage.setBuildStatus(BuildStatus.PROCESSING);

        Configurations configurations = config.get();
        configurations.getThemes().forEach(e -> {
            JsonObject theme = e.getAsJsonObject();
            if (theme.has("default") && theme.get("default").getAsBoolean()) {
                contentPackage.setTheme(theme.get("id").getAsString());
                return;
            }
        });

        try {
            JsonObject packageJson = ContentPkgXmlReader.documentToSimpleManifest(document);
            ContentPkgJsonReader.parsePackageJson(contentPackage, packageJson, false);
            JsonWrapper jsonWrapper = new JsonWrapper(packageJson);
            contentPackage.setDoc(jsonWrapper);
            contentPackage.setPackageFamily(contentPackage.getId());
        } catch (BuildException e) {
            String message = "Error importing package: There's a problem with the 'package.xml' file for this course.";
            log.error(message, e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, repositoryUrl, message);
        }

        String shallowId = contentPackage.getId() + "-" + contentPackage.getVersion();
        if (SVNImportController.importPending.containsKey(shallowId)) {
            String message = "Error importing package: There's another import in progress for the same package ("
                    + contentPackage.getId() + ", version " + contentPackage.getVersion() + ").";
            log.error(message);
            throw new ResourceException(Response.Status.BAD_REQUEST,
                    contentPackage.getId() + "_" + contentPackage.getVersion(), message);
        }

        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findByIdAndVersion", ContentPackage.class);
        q.setParameter("id", contentPackage.getId());
        q.setParameter("version", contentPackage.getVersion());
        List<ContentPackage> resultList = q.getResultList();
        if (!resultList.isEmpty()) {
            ContentPackage existingPackage = resultList.get(0);
            if (existingPackage.getBuildStatus() != BuildStatus.FAILED) {
                String message = "Error creating new Content Package, it already exists " + contentPackage.getId() + "_"
                        + contentPackage.getVersion();
                log.error(message);
                throw new ResourceException(Response.Status.BAD_REQUEST,
                        contentPackage.getId() + "_" + contentPackage.getVersion(), message);

            }
            // Delete package only if it has status of 'FAILED'
            doDeletePackage(existingPackage.getGuid());
            SVNImportController.importPending.remove(shallowId);
        }

        syncImportWithSVN(session, repositoryUrl, contentPackage);

        JsonObject message = new JsonObject();
        message.addProperty("message", "Import in progress: " + repositoryUrl);
        return message;

    }

    public JsonElement deployPkg(AppSecurityContext session, String packageIdOrGuid, DeployStage deployStage,
            boolean redeploy) {
        ContentPackage contentPackage = findContentPackage(packageIdOrGuid);
        this.securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), contentPackage.getGuid(),
                "name=" + contentPackage.getGuid(), Collections.singletonList(Scopes.VIEW_MATERIAL_ACTION));
        if (contentPackage.getDeploymentStatus() == null) {
            contentPackage.setDeploymentStatus(ContentPackage.DeploymentStatus.DEVELOPMENT);
        }
        ContentPackage.DeploymentStatus currentStatus = contentPackage.getDeploymentStatus();
        String deployAction = "update";
        switch (deployStage) {
        case qa:
            if (currentStatus == ContentPackage.DeploymentStatus.REQUESTING_QA) {
                // Push to QA already in progress; do nothing
                break;
            }
            deployAction = redeploy ? "update" : "deploy";
            Optional<String> svnRepositoryUrl = svnSyncController
                    .svnRepositoryUrl(Paths.get(contentPackage.getSourceLocation()).toFile());
            String svnLocation = null;
            if (svnRepositoryUrl.isPresent()) {
                svnLocation = svnRepositoryUrl.get();
                try {
                    svnLocation = svnImportController.createSvnTag(svnLocation, contentPackage.getVersion(),
                            "qa-" + deployAction);
                } catch (ContentServiceException e) {
                }
            }

            deployController.sendRequestDeployEmail(session, contentPackage, deployAction, "QA", svnLocation);
            contentPackage.setDeploymentStatus(ContentPackage.DeploymentStatus.REQUESTING_QA);
            break;
        case prod:
            if (currentStatus == ContentPackage.DeploymentStatus.REQUESTING_PRODUCTION
                    || currentStatus == ContentPackage.DeploymentStatus.DEVELOPMENT) {
                // Prevent request to production if on development or if push to Production
                // already in progress; do nothing
                break;
            }
            if (currentStatus == ContentPackage.DeploymentStatus.QA) {
                deployAction = "deploy";
            }
            Optional<String> svnRepositoryUrlProd = svnSyncController
                    .svnRepositoryUrl(Paths.get(contentPackage.getSourceLocation()).toFile());
            String svnLocationProd = null;
            if (svnRepositoryUrlProd.isPresent()) {
                svnLocationProd = svnRepositoryUrlProd.get();
                try {
                    svnLocationProd = svnImportController.createSvnTag(svnLocationProd, contentPackage.getVersion(),
                            "prod-" + deployAction);
                } catch (ContentServiceException e) {
                }
            }
            deployController.sendRequestDeployEmail(session, contentPackage, deployAction, "Production",
                    svnLocationProd);
            contentPackage.setDeploymentStatus(ContentPackage.DeploymentStatus.REQUESTING_PRODUCTION);
            break;
        }
        JsonObject deployStatus = new JsonObject();
        deployStatus.addProperty("deployStatus", contentPackage.getDeploymentStatus().toString());
        return deployStatus;
    }

    @TransactionTimeout(unit = TimeUnit.MINUTES, value = 50L)
    public JsonElement newVersionOrClone(AppSecurityContext session, String packageIdOrGuid, String newPkgId,
            String version) {
        ContentPackage contentPackage = findContentPackage(packageIdOrGuid);
        securityManager.authorize(session, Collections.singletonList(ADMIN), null, null, null);
        // Assume clone and own if newPkgId is not null;
        String pkgId = contentPackage.getId();
        if (newPkgId != null) {
            pkgId = newPkgId.replaceAll("[^a-zA-Z0-9-_\\-]", "");
            if (pkgId.length() > 50) {
                pkgId = pkgId.substring(0, 50);
            }
        }

        version = version == null ? contentPackage.getVersion() : version;
        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findByIdAndVersion", ContentPackage.class);
        q.setParameter("id", pkgId);
        q.setParameter("version", version);
        List<ContentPackage> resultList = q.getResultList();
        if (!resultList.isEmpty()) {
            String message = "Error creating new package version: Version " + version.toString()
                    + " already exists for package ID " + pkgId;
            log.error(message);
            throw new ResourceException(Response.Status.BAD_REQUEST, pkgId + "_" + version, message);
        }

        Path xmlSrcFolder = Paths.get(contentPackage.getSourceLocation());
        String sourceLocation = this.config.get().getContentSourceXml();

        Path targetDir = Paths.get(sourceLocation + File.separator + pkgId + "_" + AppUtils.generateUID(12));
        while (packageFileController.fileExists(targetDir)) {
            targetDir = Paths.get(sourceLocation + File.separator + pkgId + "_" + AppUtils.generateUID(12));
        }

        String volumeLocation = this.config.get().getContentVolume();
        volumeLocation = volumeLocation + File.separator + pkgId + "_" + AppUtils.generateUID(12);
        while (Files.exists(Paths.get(volumeLocation))) {
            volumeLocation = volumeLocation + File.separator + pkgId + "_" + AppUtils.generateUID(12);
        }
        ContentPackage cloneVersion = null;
        try {
            cloneVersion = contentPackage.cloneVersion(pkgId, version, targetDir.toAbsolutePath().toString(),
                    volumeLocation, null);
            em.persist(cloneVersion);
            em.flush();
        } catch (Exception e) {
            final String message = "Package cloning error: " + e.getMessage();
            log.error(message, e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR,
                    pkgId + "_" + contentPackage.getVersion(), message);
        }
        log.info("newVersionOrClone: clone pkg created");

        String webContentVolume = this.config.get().getWebContentVolume();
        webContentVolume = webContentVolume + File.separator + cloneVersion.getGuid();
        cloneVersion.setWebContentVolume(webContentVolume);
        for (WebContent webContent : cloneVersion.getWebContents()) {
            webContent.getFileNode().setVolumeLocation(cloneVersion.getWebContentVolume());
        }

        for (Resource resource : cloneVersion.getResources()) {
            if (resource.getType().equalsIgnoreCase("x-oli-organization")) {
                FileNode fileNode = resource.getFileNode();
                String srcPath = cloneVersion.getSourceLocation() + File.separator + fileNode.getPathFrom();
                String[] split = srcPath.split(File.separator);
                String orgId = cloneVersion.getId() + "-" + cloneVersion.getVersion() + "_" + split[split.length - 2];
                resource.setId(orgId);
            }
            em.merge(resource);
        }
        em.flush();
        em.clear();
        log.info("newVersionOrClone: Resources Merged");

        securityManager.createResource(cloneVersion.getGuid(), "/packages/" + cloneVersion.getGuid(),
                ContentPackageResource.resourceType,
                Arrays.asList(Scopes.VIEW_MATERIAL_ACTION, Scopes.EDIT_MATERIAL_ACTION));

        Map<String, List<String>> userPermission = new HashMap<>();
        userPermission.put(cloneVersion.getGuid(), Arrays.asList(Scopes.VIEW_MATERIAL_ACTION.toString(),
                Scopes.EDIT_MATERIAL_ACTION.toString(), "ContentPackage", cloneVersion.getTitle()));
        securityManager.updateUserAttributes(session.getPreferredUsername(), userPermission, null);

        final ContentPackage cloneVersionFinal = cloneVersion;
        svnExecutor.submit(() -> {
            try {
                directoryUtils.copyDirectory(xmlSrcFolder, Paths.get(cloneVersionFinal.getSourceLocation()), false);
                Path pkgWebContent = Paths.get(contentPackage.getWebContentVolume());
                if (!Files.exists(pkgWebContent)) {
                    Files.createDirectory(pkgWebContent);
                }
                directoryUtils.copyDirectory(pkgWebContent, Paths.get(cloneVersionFinal.getWebContentVolume()), false);
            } catch (IOException e) {
                removeExistingPackage(cloneVersionFinal.getGuid());
                final String message = "Unable to copy  " + xmlSrcFolder.toString();
                log.error(message, e);
                return;
            }
            Optional<String> svnRepositoryUrl = svnSyncController.svnRepositoryUrl(xmlSrcFolder.toFile());
            if (svnRepositoryUrl.isPresent()) {
                try {
                    String editorBranch = null;
                    if (newPkgId != null) {
                        editorBranch = svnSyncController.cloneRemoteAndOwn(svnRepositoryUrl.get(),
                                cloneVersionFinal.getId(), cloneVersionFinal.getVersion());
                    } else {
                        editorBranch = svnImportController.createEditorBranch(svnRepositoryUrl.get(),
                                cloneVersionFinal.getVersion());
                    }
                    svnSyncController.switchSvnRepo(Paths.get(cloneVersionFinal.getSourceLocation()).toFile(),
                            editorBranch);
                } catch (ContentServiceException e) {
                    // No-op
                }
            }

            postProcessNewVersion(cloneVersionFinal);

            svnSyncController.updateSvnRepo(
                    FileSystems.getDefault().getPath(cloneVersionFinal.getSourceLocation()).toFile(),
                    cloneVersionFinal.getGuid());
        });

        JsonObject message = new JsonObject();
        message.addProperty("message", "New version creation in progress: packageId=" + cloneVersion.getId()
                + " version=" + cloneVersion.getVersion());
        return message;
    }

    public boolean updateDeploymentStatus(AppSecurityContext session, String packageIdOrGuid,
            ContentPackage.DeploymentStatus newStatus) {
        // Admin only action
        securityManager.authorize(session, Collections.singletonList(ADMIN), null, null, null);

        ContentPackage pkg = findContentPackage(packageIdOrGuid);
        pkg.setDeploymentStatus(newStatus);

        String serverUrl = System.getenv().get("SERVER_URL");
        String serverName = serverUrl.substring(serverUrl.indexOf("/") + 2);

        String server = null;

        switch (newStatus) {
        case QA:
            server = "QA";
            break;
        case PRODUCTION:
            server = "Production";
            break;
        default:
            server = null;
        }

        if (server != null) {
            String subject = (serverName.contains("dev.local") ? "TESTING: PLEASE IGNORE THIS - " : "") + "OLI package "
                    + pkg.getTitle() + " has been pushed to " + server;

            StringBuilder sb = new StringBuilder();
            sb.append("The user ").append(session.getFirstName()).append(" ").append(session.getLastName()).append(" (")
                    .append(session.getEmail()).append(") has published the course content package (id=")
                    .append(pkg.getId()).append(" and version=").append(pkg.getVersion()).append(") to ")
                    .append(server);
            deployController.sendStateTransitionEmail(pkg, subject, sb.toString());
        }
        return true;
    }

    private void postProcessNewVersion(ContentPackage contentPackage) {
        try {
            Path pkgXmlPath = Paths.get(
                    contentPackage.getSourceLocation() + File.separator + contentPackage.getFileNode().getPathFrom());
            packageFileController.updatePackageXmlIdAndVersion(pkgXmlPath, contentPackage.getId(),
                    contentPackage.getVersion(), contentPackage.getTitle());

            Map<String, Map<String, String>> jobs = new HashMap<>();
            Map<String, String> revsMap = new HashMap<>();
            jobs.put(UUID.randomUUID().toString(), revsMap);
            for (Resource resource : contentPackage.getResources()) {
                if (resource.getType().equalsIgnoreCase("x-oli-organization")) {
                    FileNode fileNode = resource.getFileNode();
                    String srcPath = contentPackage.getSourceLocation() + File.separator + fileNode.getPathFrom();
                    Path orgXmlPath = Paths.get(srcPath);
                    packageFileController.updateOrganizationXmlIdAndVersion(orgXmlPath, resource.getId(), "1.0");
                }
                if (revsMap.size() >= 10) {
                    revsMap = new HashMap<>();
                    jobs.put(UUID.randomUUID().toString(), revsMap);
                }
                revsMap.put(resource.getTransientRev(), resource.getGuid());
            }

            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.schedule(() -> {
                Set<String> jobIds = new HashSet<>(jobs.keySet());

                jobs.forEach((key, value) -> versionExec.submit(() -> {
                    try {
                        VersionBatchProcess versionBatchProcess = (VersionBatchProcess) new InitialContext()
                                .lookup("java:global/content-service/VersionBatchProcess");
                        versionBatchProcess.versionProcess(contentPackage.getGuid(), value, jobIds, key);
                    } catch (Throwable e) {
                        String message = "Error while creating revisions in " + contentPackage.toString();
                        log.error(message, e);
                    }
                }));

            }, 1, TimeUnit.SECONDS);

        } catch (Throwable e) {
            removeExistingPackage(contentPackage.getGuid());
        }
    }

    private void removeExistingPackage(String pkgGuid) {
        try {
            VersionBatchProcess versionBatchProcess = (VersionBatchProcess) new InitialContext()
                    .lookup("java:global/content-service/VersionBatchProcess");
            versionBatchProcess.removeExistingPackage(pkgGuid);
        } catch (Throwable e) {
            log.error(e.getLocalizedMessage(), e);
        }
    }

    private void syncImportWithSVN(AppSecurityContext session, String oldRepositoryUrl, ContentPackage contentPackage) {
        ContentPackage shallowClone = contentPackage.shallowClone();
        shallowClone.setBuildStatus(BuildStatus.PROCESSING);
        String shallowId = shallowClone.getId() + "-" + shallowClone.getVersion();
        SVNImportController.importPending.put(shallowId, shallowClone);

        svnExecutor.submit(() -> {
            String repositoryUrl = oldRepositoryUrl;
            try {
                repositoryUrl = svnImportController.processImport(repositoryUrl, contentPackage,
                        session.getPreferredUsername());
            } catch (ContentServiceException e) {
                log.error(e.getMessage(), e);
                return;
            }

            if (repositoryUrl != null) {
                String previewSetupUrl = System.getenv().get("preview_setup_url");
                if (previewSetupUrl == null) {
                    String message = "Development server not configured";
                    log.error(message);
                    return;
                }
                WebTarget target = ClientBuilder.newClient().target(previewSetupUrl);
                JsonObject previewSetupInfo = new JsonObject();
                previewSetupInfo.addProperty("svnUrl", repositoryUrl);
                previewSetupInfo.addProperty("packageId", contentPackage.getId());
                previewSetupInfo.addProperty("packageVersion", contentPackage.getVersion());
                JsonObject userInfo = new JsonObject();
                userInfo.addProperty("firstName", session.getFirstName());
                userInfo.addProperty("lastName", session.getLastName());
                userInfo.addProperty("email", session.getEmail());
                userInfo.addProperty("userName", session.getPreferredUsername());
                previewSetupInfo.add("userInfo", userInfo);
                Response response = target.request(MediaType.APPLICATION_JSON)
                        .post(Entity.json(AppUtils.gsonBuilder().create().toJson(previewSetupInfo)));
                javax.json.JsonObject jsonObject = response.readEntity(javax.json.JsonObject.class);
                String s = AppUtils.toString(jsonObject);
                log.debug("Json from previewResource/setup call " + s);
            }

        });
    }

    private void fireResourceChangeEvent(String resourceId, ResourceEventType eventType, JsonElement payload) {
        resourceChange.fire(new ResourceChangeEvent(resourceId, eventType, payload));
    }

    private void doDeletePackage(String packageGuid) {
        ContentPackage contentPackage = findContentPackage(packageGuid);

        em.remove(contentPackage);

        // Delete resource from Keycloak
        securityManager.deleteResource(contentPackage.getGuid());

        if (contentPackage.getVolumeLocation() != null) {
            directoryUtils.deleteDirectory(FileSystems.getDefault().getPath(contentPackage.getVolumeLocation()));
        }
        if (contentPackage.getWebContentVolume() != null) {
            directoryUtils.deleteDirectory(FileSystems.getDefault().getPath(contentPackage.getWebContentVolume()));
        }
        if (contentPackage.getSourceLocation() != null) {
            directoryUtils.deleteDirectory(FileSystems.getDefault().getPath(contentPackage.getSourceLocation()));
        }

    }

    private String generateUniquePackageId(String id, String version) {
        String random = generateUID(8);
        while (validatePackageDoesNotAlreadyExist(id + "-" + random, version)) {
            random = generateUID(8);
        }
        return id + "-" + random;
    }

    private boolean validatePackageDoesNotAlreadyExist(String id, String version) {
        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findByIdAndVersion", ContentPackage.class);
        q.setParameter("id", id);
        q.setParameter("version", version);
        List<ContentPackage> resultList = q.getResultList();
        return !resultList.isEmpty();

    }

    private JsonElement detailedPackage(ContentPackage contentPackage) {
        JsonObject contentPkgJson = (JsonObject) pkgLoad(contentPackage);
        Path pkgSrc = Paths.get(contentPackage.getSourceLocation());
        Optional<String> svnRepositoryUrl = svnSyncController.svnRepositoryUrl(pkgSrc.toFile());
        if (svnRepositoryUrl.isPresent()) {
            contentPkgJson.addProperty("svnLocation", svnRepositoryUrl.get());
        }
        List<UserInfo> allUsers = securityManager.getAllUsers();
        allUsers.sort((o1, o2) -> {
            Map<String, List<String>> attributes = o1.getAttributes();
            Boolean isDev01 = attributes != null && attributes.containsKey(contentPackage.getGuid());
            attributes = o2.getAttributes();
            Boolean isDev02 = attributes != null && attributes.containsKey(contentPackage.getGuid());
            return isDev02.compareTo(isDev01);
        });
        JsonArray users = new JsonArray();

        for (UserInfo ur : allUsers) {
            if (ur.getServiceAccountClientId() != null || ur.getUsername().equalsIgnoreCase("manager")) {
                continue;
            }
            Map<String, List<String>> attributes = ur.getAttributes();
            JsonObject userOb = new JsonObject();
            userOb.addProperty("userName", ur.getUsername());
            userOb.addProperty("firstName", ur.getFirstName());
            userOb.addProperty("lastName", ur.getLastName());
            userOb.addProperty("email", ur.getEmail());
            userOb.addProperty("isDeveloper", attributes != null && attributes.containsKey(contentPackage.getGuid()));
            users.add(userOb);
        }
        contentPkgJson.add("developers", users);
        return contentPkgJson;
    }

    private JsonElement pkgLoad(ContentPackage contentPackage) {
        Gson gson = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();
        JsonElement contentPkgJson = gson.toJsonTree(contentPackage);
        Collection<Resource> resources = contentPackage.getResources();
        JsonElement resourceArray = gson.toJsonTree(resources, new TypeToken<ArrayList<Resource>>() {
        }.getType());
        ((JsonObject) contentPkgJson).add("resources", resourceArray);

        // identify embed activity types
        HashMap<String, String> embedActivityTypes = identifyEmbedActivityTypes(resources);
        ((JsonObject) contentPkgJson).add("embedActivityTypes", gson.toJsonTree(embedActivityTypes));

        Collection<WebContent> webContents = contentPackage.getWebContents();
        JsonElement webContentArray = gson.toJsonTree(webContents, new TypeToken<ArrayList<WebContent>>() {
        }.getType());
        ((JsonObject) contentPkgJson).add("webContents", webContentArray);

        return contentPkgJson;
    }

    private HashMap<String, String> identifyEmbedActivityTypes(Collection<Resource> resources) {
        Collection<Resource> embedActivityResources = resources.stream()
            .filter(r -> r.getType().equalsIgnoreCase("x-oli-embed-activity"))
            .collect(Collectors.toList());
            
        HashMap<String, String> embedActivityTypes = new HashMap<String, String>();
        for (Resource embedActivityResource : embedActivityResources) {
                Revision lastestRev = embedActivityResource.getLastRevision();
                JsonWrapper jsonPayload = lastestRev.getBody().getJsonPayload();

                // infer type based on content
                if (jsonPayload != null) {
                    JsonObject embedActivityJson = jsonPayload.getJsonObject().getAsJsonObject()
                        .get("embed_activity").getAsJsonObject();

                    for (JsonElement item : embedActivityJson.get("#array").getAsJsonArray()) {
                        JsonObject itemObj = item.getAsJsonObject();
                        if (itemObj.has("assets")) {
                            JsonArray assets = itemObj.get("assets").getAsJsonObject().get("#array").getAsJsonArray();

                            for (JsonElement asset : assets) {
                                JsonObject assetObj = asset.getAsJsonObject();
                                if (assetObj.get("asset").getAsJsonObject().get("@name").getAsString().equals("jsrepl")) {
                                    embedActivityTypes.put(embedActivityResource.getId(), "REPL");
                                }
                            }
                        }
                    }
                } else {
                    embedActivityTypes.put(embedActivityResource.getId(), "UNKNOWN");
                }
        }

        return embedActivityTypes;
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
