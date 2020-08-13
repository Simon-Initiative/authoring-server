package edu.cmu.oli.content.controllers;

import com.google.gson.*;
import edu.cmu.oli.assessment.builders.Assessment2Transform;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.ContentServiceException;
import edu.cmu.oli.content.DirectoryUtils;
import edu.cmu.oli.content.ResourceException;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.contentfiles.writers.ResourceToXml;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.*;
import edu.cmu.oli.content.resource.builders.*;
import edu.cmu.oli.content.resource.validators.BaseResourceValidator;
import edu.cmu.oli.content.resource.validators.ResourceValidator;
import edu.cmu.oli.content.svnmanager.SVNManager;
import org.jboss.ejb3.annotation.TransactionTimeout;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

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
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Raphael Gachuhi
 */

@Stateless
public class SVNSyncController {

    public static final String JSON_CAPABLE = "jsonCapable";

    @Inject
    @Logging
    Logger log;

    @PersistenceContext
    EntityManager em;

    @Inject
    @ConfigurationCache
    Instance<Configurations> configuration;

    @Inject
    Assessment2Transform assessment2Transform;

    @Inject
    PackageFileController packageFileController;

    @Inject
    EdgesController edgesController;

    @Inject
    LDModelController ldModelController;

    @Inject
    Xml2Json xml2Json;

    @Inject
    DirectoryUtils directoryUtils;

    @Inject
    Instance<SVNManager> svnManagerInstance;

    @Inject
    LockController lockController;

    // :FIXME: avoid use of global state, not good for horizontal scaling. Distributed cache is a better option
    static final Map<String, Boolean> repos = new ConcurrentHashMap<>();

    public void cloneRemoteTemplateSvnRepo(String repo) {
        try {
            String fromURL = System.getenv().get("svn_template");
            String repoURL = System.getenv().get("svn_projects") + repo;
            String commitMessage = "Publisher: copying repositories " + fromURL + " to " + repoURL;
            log.info("Creating SVN repository: " + repoURL);
            svnManagerInstance.get().copy(fromURL, repoURL, commitMessage);
        } catch (SVNException e) {
            log.error("SVN Repository Copy Error: ", e);
        }
    }

    public String cloneRemoteAndOwn(String fromURL, String id, String version) {
        Map<String, String> svnLocations = cloneSvnLocation(fromURL, id, version);
        try {
            String commitMessage = "Publisher: copying repositories " + fromURL + " to " + svnLocations.get("branch");
            log.info("Creating SVN repository: " + svnLocations.get("branch"));
            svnManagerInstance.get().copy(fromURL, svnLocations.get("branch"), commitMessage);
            svnManagerInstance.get().doMkDir(svnLocations.entrySet().stream().filter(e -> !e.getKey().equals("branch"))
                    .map(e -> e.getValue()).collect(Collectors.toSet()), "Created trunk and tags for clone");
        } catch (Exception e) {
            log.error("SVN Repository Copy Error: ", e);
        }
        return svnLocations.get("branch");
    }

    private Map<String, String> cloneSvnLocation(String oldRepoUrl, String id, String version) {
        if (oldRepoUrl.endsWith("/")) {
            oldRepoUrl = oldRepoUrl.substring(0, oldRepoUrl.length() - 1);
        }

        Map<String, String> svnLocations = new HashMap<>();
        String serverurl = System.getenv().get("SERVER_URL");
        String serverName = serverurl.substring(serverurl.indexOf("/") + 2, serverurl.indexOf("."));
        String vString = version.replaceAll("\\.", "_");

        if (oldRepoUrl.endsWith("/trunk")) {
            svnLocations.put("branch", oldRepoUrl.substring(0, oldRepoUrl.lastIndexOf("/trunk")) + "/adaptations/" + id + "/branches/v_" + vString + "-" + serverName);
            svnLocations.put("trunk", oldRepoUrl.substring(0, oldRepoUrl.lastIndexOf("/trunk")) + "/adaptations/" + id + "/trunk");
            svnLocations.put("tags", oldRepoUrl.substring(0, oldRepoUrl.lastIndexOf("/trunk")) + "/adaptations/" + id + "/tags");
        } else if (oldRepoUrl.contains("/branches/")) {
            svnLocations.put("branch", oldRepoUrl.substring(0, oldRepoUrl.lastIndexOf("/branches/")) + "/adaptations/" + id + "/branches/v_" + vString + "-" + serverName);
            svnLocations.put("trunk", oldRepoUrl.substring(0, oldRepoUrl.lastIndexOf("/branches/")) + "/adaptations/" + id + "/trunk");
            svnLocations.put("tags", oldRepoUrl.substring(0, oldRepoUrl.lastIndexOf("/branches/")) + "/adaptations/" + id + "/tags");
        } else if (oldRepoUrl.contains("/tags/")) {
            svnLocations.put("branch", oldRepoUrl.substring(0, oldRepoUrl.lastIndexOf("/tags/")) + "/adaptations/" + id + "/branches/v_" + vString + "-" + serverName);
            svnLocations.put("trunk", oldRepoUrl.substring(0, oldRepoUrl.lastIndexOf("/tags/")) + "/adaptations/" + id + "/trunk");
            svnLocations.put("tags", oldRepoUrl.substring(0, oldRepoUrl.lastIndexOf("/tags/")) + "/adaptations/" + id + "/tags");
        }

        return svnLocations;
    }


    public void unlockFile(File base, Path file) {
        SVNManager svnManager = svnManagerInstance.get();
        if (!svnManager.isVersioned(base)) {
            log.error("SVN base is not versioned " + base.getName());
            return;
        }
        try {
            svnManager.unlock(file.toFile(), true);
        } catch (SVNException e) {
            log.error("SVN Repository unlock Error: ", e);
        }
    }

    public void cleanup(File base, Path file) {
        SVNManager svnManager = svnManagerInstance.get();
        if (!svnManager.isVersioned(base)) {
            log.error("SVN base is not versioned " + base.getName());
            return;
        }
        try {
            svnManager.cleanup(base);
        } catch (SVNException e) {
            log.error("SVN Repository unlock Error: ", e);
        }
    }

    public void svnDelete(File base, Path file) {
        SVNManager svnManager = svnManagerInstance.get();
        if (!svnManager.isVersioned(base)) {
            log.error("SVN base is not versioned " + base.getName());
            return;
        }
        try {
            svnManager.delete(file.toFile(), true);
        } catch (SVNException e) {
            log.error("SVN Repository Delete Error: ", e);
        }
    }

    public void switchSvnRepo(File base, String repo) {
        SVNManager svnManager = svnManagerInstance.get();
        if (!svnManager.isVersioned(base)) {
            log.error("SVN base is not versioned " + base.getName());
            return;
        }

        try {
            log.info("Switching to SVN repository: " + repo);
            svnManager.doSwitch(base, repo);
        } catch (SVNException e) {
            log.error("SVN Repository Switching Error: ", e);
        }
    }

    @TransactionTimeout(unit = TimeUnit.MINUTES, value = 40L)
    public Map<String, List<File>> updateSvnRepo(File base, String packageGuid) {
        Boolean syncTrack = repos.get(base.getAbsolutePath());
        if (syncTrack != null) {
            repos.put(base.getAbsolutePath(), Boolean.TRUE);
            return null;
        }
        repos.put(base.getAbsolutePath(), Boolean.FALSE);
        return doUpdateSvnRepo(base, packageGuid);
    }

    public Optional<String> svnRepositoryUrl(File base) {
        SVNManager svnManager = svnManagerInstance.get();
        if (!svnManager.isVersioned(base)) {
            log.error("SVN base is not versioned " + base.getName());
            return Optional.empty();
        }
        final List<String> svnUrl = new ArrayList<>();

        try {
            svnManager.showInfo(base, false, svnInfo -> {
                SVNURL url = svnInfo.getURL();
                String svnLocation = url.getProtocol() + "://" + url.getHost() + url.getPath();
                svnUrl.add(svnLocation);
                log.debug("SVN URL " + svnLocation);
            });

        } catch (SVNException e) {
            log.error("SVN Update Error: ", e);
        }
        return svnUrl.isEmpty() ? Optional.empty() : Optional.of(svnUrl.get(0));
    }

    private Map<String, List<File>> doUpdateSvnRepo(File base, String packageGuid) {

        try {
            SVNManager svnManager = svnManagerInstance.get();

            if (!svnManager.isVersioned(base)) {
                log.error("SVN base is not versioned " + base.getName());
                return null;
            }

            try {
                svnManager.cleanup(base);
            } catch (SVNException e) {
                log.error("SVN Cleanup Error: ", e);
            }

            final List<String> svnUrl = new ArrayList<>();

            try {
                svnManager.showInfo(base, false, svnInfo -> {
                    SVNURL url = svnInfo.getURL();
                    String svnLocation = url.getProtocol() + "://" + url.getHost() + url.getPath();
                    svnUrl.add(svnLocation);
                    log.debug("SVN URL " + svnLocation);
                });

            } catch (SVNException e) {
                log.error("SVN Update Error: ", e);
            }

            try {
                List<File> addedFiles = svnManager.listAddedFiles(base);
                for (File e : addedFiles) {
                    log.debug("Added File " + e.getAbsolutePath());
                    svnManager.addEntry(e);
                }

            } catch (SVNException e) {
                log.error("SVN Add Entry Error: ", e);
                JsonObject alert = new JsonObject();
                alert.addProperty("text", System.getenv().get("SERVER_URL")
                        + " SVN Add Entry Error: working copy name:- " + base.getName() +
                        " \nerror message " + e.getLocalizedMessage() +" \nsvn url:- " +svnUrl.get(0));
                AppUtils.sendSlackAlert(alert);
            }

            Map<String, Path> repoFiles = new HashMap<>();
            Set<String> directories = new HashSet<>();
            Map<String, List<File>> update = null;

            try {
                Files.walkFileTree(base.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (super.visitFile(file, attrs).equals(FileVisitResult.CONTINUE)) {
                            if (Files.isHidden(file) || file.toString().contains(".idea") || file.toString().contains(".svn")) {
                                return FileVisitResult.CONTINUE;
                            }
                            repoFiles.put(file.toAbsolutePath().toString(), file);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        directories.add(dir.toAbsolutePath().toString());
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                log.error("SVN error visiting files ", e);
            }

            try {
                log.debug("Updating changes from svn " + base.getName());
                update = svnManager.update(base);

                update.entrySet().forEach(e -> {
                    log.debug("All files that have changed");
                    List<File> value = e.getValue();
                    value.forEach(f -> {
                        log.debug(e.getKey() + " " + f.getAbsolutePath());
                        Path path = f.toPath();
                        if (e.getKey().toUpperCase().equals("D") && directories.contains(f.getAbsolutePath())) {
                            Set<Map.Entry<String, Path>> entries = repoFiles.entrySet();
                            entries.forEach((en) -> {
                                if (en.getKey().startsWith(f.getAbsolutePath())) {
                                    parseResource(base, e.getKey(), packageGuid, en.getValue());
                                }
                            });
                        } else {
                            parseResource(base, e.getKey(), packageGuid, path);
                        }
                    });

                });

                doProcessLDmodelFiles(packageGuid, base.toPath().resolve("ldmodel"));
                if (!update.isEmpty()) {
                    edgesController.validateEdgesAsync(packageGuid);
                }

            } catch (SVNException ex) {
                log.error("SVN Update Error: ", ex);
                JsonObject alert = new JsonObject();
                alert.addProperty("text", System.getenv().get("SERVER_URL")
                        + " SVN Update Error: working copy name:- " + base.getName() +
                        " \nerror message " + ex.getLocalizedMessage() +" \nsvn url:- " +svnUrl.get(0));
                AppUtils.sendSlackAlert(alert);
            }

            try {
                svnCommit(base, svnManager);
            } catch (SVNException ex) {
                log.error("SVN Commit Error: ", ex);
                JsonObject alert = new JsonObject();
                alert.addProperty("text", System.getenv().get("SERVER_URL")
                        +" SVN Commit Error: working copy name:- " + base.getName() +
                        " \nerror message " + ex.getLocalizedMessage() +" \nsvn url:- " +svnUrl.get(0));

                AppUtils.sendSlackAlert(alert);
            }

            log.info("Done Committing changes to svn " + base.getName());
            return update;
        } finally {
            Boolean removed = repos.remove(base.getAbsolutePath());
            if (removed != null && removed) {
                updateSvnRepo(base, packageGuid);
            }
        }
    }

    private void svnCommit(File base, SVNManager svnManager) throws SVNException {
        String commitMessage = "Publisher: committing changes to svn repository " + base.getName();
        log.info("Committing changes to svn " + base.getName());
        repos.put(base.getAbsolutePath(), Boolean.FALSE);
        svnManager.commit(base, commitMessage);
    }

    private void parseResource(File base, String crud, String packageId, Path file) {
        String pathString = file.toString();
        try {
            if (Files.isHidden(file)) {
                return;
            }
        } catch (IOException e) {
        }

        if (pathString.endsWith("package.xml")) {
            processPackageFile(crud, packageId, file);
        } else if (pathString.matches(".+/webcontent/.+")) {
            processWebcontentFile(crud, packageId, file);
        } else if (pathString.matches(".+/organizations/.+")) {
            Configurations configurations = configuration.get();
            processResourceFile(crud, packageId, file, configurations.getResourceTypeById("x-oli-organization"));
        } else if (pathString.matches(".+/ldmodel/.+")) {
            processLDmodelFiles(crud, file);
        } else {
            String name = file.getFileName().toString();
            if (Files.isDirectory(file)) {
                return;
            }
            if (!name.endsWith(".xml")) {
                // :FIXME: Not an OLI resource type, what to do with it? process as webcontent and move it to webcontent?
                processWebcontentFile(crud, packageId, file);
                return;
            }

            Configurations configurations = configuration.get();
            final Set<String> resourceTypesIds = configurations.getResourceTypes().keySet();
            resourceTypesIds.stream().filter(id -> pathString.matches(".+/" + id + "/.+")).map(
                    configurations::getResourceTypeById).findFirst().ifPresent(resourceType -> processResourceFile(
                    crud, packageId, file, resourceType));
        }
    }

    private void processLDmodelFiles(String crud, Path file) {
        if (crud.equalsIgnoreCase("D") || crud.equalsIgnoreCase("C")) {
            return;
        }
        Path ldFolder = Files.isDirectory(file) ? file : file.getParent();
        Path flag = ldFolder.resolve("ldprocessing.json");
        if (Files.exists(flag)) {
            return;
        }
        try {
            Files.write(flag, "importing ld model".getBytes());
        } catch (IOException e) {
        }
    }

    private void doProcessLDmodelFiles(String packageId, Path ldFolder) {
        if (!Files.exists(ldFolder)) {
            return;
        }
        Path flag = ldFolder.resolve("ldprocessing.json");
        if (!Files.exists(flag)) {
            return;
        }
        try {
            TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findByGuid", ContentPackage.class);
            q.setParameter("guid", packageId);
            List<ContentPackage> resultList = q.getResultList();
            if (resultList.isEmpty()) {
                String message = "Content Package not found " + packageId;
                log.error(message);
                return;
            }

            Optional<String> skillsModel = Optional.empty();
            Optional<String> problemsModel = Optional.empty();
            Optional<String> losModel = Optional.empty();
            int sizeCnt = 0;
            try (Stream<Path> paths = Files.list(ldFolder).filter(Files::isRegularFile)) {
                List<Path> ldFiles = paths.collect(Collectors.toList());


                for (Path p : ldFiles) {
                    if (sizeCnt >= 3) {
                        break;
                    }

                    String s = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);

                    String[] lines = s.split("\n");
                    if (lines.length > 2) {
                        for (int x = 0; x < 3; x++) {
                            String line = lines[x];
                            if (line.toLowerCase().contains("gamma0")) {
                                skillsModel = Optional.of(s);
                                sizeCnt++;
                            } else if (line.toLowerCase().contains("resource")) {
                                problemsModel = Optional.of(s);
                                sizeCnt++;
                            } else if (line.toLowerCase().contains("low opportunity")) {
                                losModel = Optional.of(s);
                                sizeCnt++;
                            }
                        }
                    }
                }
            } catch (IOException e) {
            }

            if (sizeCnt < 3) {
                log.info("Unable to process LD model files. Insufficient LD model data supplied");
                return;
            }

            try {
                ldModelController.importLDModel(resultList.get(0), skillsModel, problemsModel, losModel);
            } catch (ContentServiceException e) {
                log.error(e.getMessage(), e);
            }
        } finally {
            try {
                Files.deleteIfExists(flag);
            } catch (IOException e) {

            }
        }
    }

    private void processPackageFile(String crud, String packageId, Path packageXMLFile) {
        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findByGuid", ContentPackage.class);
        q.setParameter("guid", packageId);
        List<ContentPackage> resultList = q.getResultList();
        if (resultList.isEmpty()) {
            String message = "Content Package not found " + packageId;
            log.error(message);
            return;
        }

        ContentPackage contentPackage = resultList.get(0);
        String fileString = null;
        try {
            fileString = new String(Files.readAllBytes(packageXMLFile), StandardCharsets.UTF_8);
            int i = fileString.indexOf("<?xml");
            if (i > 0) {
                fileString = fileString.substring(i);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        SAXBuilder builder = AppUtils.validatingSaxBuilder();
        builder.setExpandEntities(false);
        Document document;
        try {
            document = builder.build(new StringReader(fileString.trim()));
        } catch (JDOMException | IOException e) {
            final String message = "error while parsing" + packageXMLFile.toFile().getAbsolutePath()
                    + "\n " + e.getMessage() + "\n" + fileString.trim();
            log.error(message, e);
            return;
        }

        try {
            JsonObject packageJson = ContentPkgXmlReader.documentToSimpleManifest(document);
            ContentPkgJsonReader.parsePackageJson(contentPackage, packageJson, false);
            contentPackage.setDoc(new JsonWrapper(packageJson));
        } catch (BuildException e) {
            final String message = "error while calling ContentPkgXmlReader.documentToSimpleManifest: " + packageXMLFile.toFile().getAbsolutePath()
                    + "\n " + e.getMessage() + "\n" + fileString.trim();
            log.error(message, e);
            return;
        }
        em.merge(contentPackage);
    }

    private void processResourceFile(String crud, String packageId, Path resourceFile, JsonObject resourceTypeDefinition) {
        switch (crud.toUpperCase()) {
            case "A":
                createResource(packageId, resourceFile, resourceTypeDefinition);
                break;
            case "U":
            case "G":
                updateResource(packageId, resourceFile, resourceTypeDefinition, false);
                break;
            case "D":
                deleteResource(packageId, resourceFile);
                break;
            case "C":
                // Bias towards svn sourced changes
                updateResource(packageId, resourceFile, resourceTypeDefinition, true);
                break;
            default:
                log.error("SVN pathChangeType not supported: " + crud);
        }
    }

    private void processWebcontentFile(String crud, String packageId, Path webContentFile) {
        switch (crud.toUpperCase()) {
            case "A":
                insertWebContent(packageId, webContentFile);
                break;
            case "U":
            case "G":
                updateWebContent(packageId, webContentFile);
                break;
            case "D":
                deleteWebContent(packageId, webContentFile);
                break;
            case "C":
                updateWebContent(packageId, webContentFile);
                break;
            default:
                log.error("SVN pathChangeType not supported: " + crud);
        }
    }

    private void createResource(String packageId, Path resourceFile, JsonObject resourceTypeDefinition) {
        if(Files.isDirectory(resourceFile)){
            return;
        }
        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findByGuid", ContentPackage.class);
        q.setParameter("guid", packageId);
        List<ContentPackage> resultList = q.getResultList();
        if (resultList.isEmpty()) {
            String message = "Content Package not found " + packageId;
            log.error(message);
            return;
        }

        ContentPackage contentPackage = resultList.get(0);
        boolean jsonCapable = resourceTypeDefinition.get(JSON_CAPABLE).getAsBoolean();
        Resource resource = new Resource();
        resource.setType(resourceTypeDefinition.get("id").getAsString());

        String fileString = null;
        try {
            fileString = new String(Files.readAllBytes(resourceFile), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        int i = fileString.indexOf("<?xml");
        if (i > 0) {
            fileString = fileString.substring(i);
        }

        SAXBuilder builder = AppUtils.validatingSaxBuilder();
        builder.setExpandEntities(false);
        Document document;
        try {
            if (jsonCapable) {
                document = builder.build(new StringReader(fileString.trim()));
                //.replaceAll("&", "&amp;")));
            } else {
                document = builder.build(new StringReader(fileString.trim()));
            }
        } catch (JDOMException | IOException e) {
            final String message = "error while parsing" + resourceFile.toString() + " from content package" + contentPackage.getId() + "_" + contentPackage.getVersion()
                    + "\n " + e.getMessage() + "\n" + fileString.trim();
            log.error(message, e);
            AppUtils.addToPackageError(contentPackage, message.toString(), contentPackage.getId(), ErrorLevel.WARN);
            resource.setBuildStatus(BuildStatus.FAILED);
            return;
        }

        ResourceXmlReader.documentToResource(resource, document);

        TypedQuery<Resource> query = em.createQuery("select r from Resource r where r.contentPackage.guid = :pkgGuid and r.id = :id", Resource.class);
        query.setParameter("pkgGuid", packageId);
        query.setParameter("id", resource.getId());
        if (!query.getResultList().isEmpty()) {
            String message = "ContentResource Id already used in package " + contentPackage.getId() + " Id: " + resource.getId();
            log.error(message);
            return;
        }

        String content = null;
        String xmlContent = null;
        if (jsonCapable) {
            JsonElement jsonElement = xml2Json.toJson(document.getRootElement(), false);
            Gson gson = AppUtils.gsonBuilder().setPrettyPrinting().create();
            content = gson.toJson(jsonElement);
            //log.debug("Json Content " + content);
            ResourceToXml resourceToXml = new ResourceToXml();
            resourceToXml.setConfig(configuration.get());
            xmlContent = resourceToXml.resourceToXml(resource.getType(), content);
        } else {
            Format format = Format.getPrettyFormat();
            format.setIndent("\t");
            format.setTextMode(Format.TextMode.PRESERVE);
            XMLOutputter xmlOut = new XMLOutputter(format);
            content = xmlOut.outputString(document);
            xmlContent = AppUtils.escapeAmpersand(content);
        }

        resource.setContentPackage(contentPackage);
        contentPackage.getEdges();

        createFile(contentPackage, resource, resourceFile, document, xmlContent, content, resourceTypeDefinition);
        resource.setBuildStatus(BuildStatus.READY);
        em.persist(resource);
        em.flush();
        log.info("Done createResource " + resourceFile.toString());
    }

    private void updateResource(String packageId, Path resourceFile, JsonObject resourceTypeDefinition, boolean conflict) {
        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findByGuid", ContentPackage.class);
        q.setParameter("guid", packageId);
        List<ContentPackage> resultList = q.getResultList();
        if (resultList.isEmpty()) {
            String message = "Content Package not found " + packageId;
            log.error(message);
            return;
        }

        ContentPackage contentPackage = resultList.get(0);
        boolean jsonCapable = resourceTypeDefinition.get(JSON_CAPABLE).getAsBoolean();
        Resource template = new Resource();
        template.setType(resourceTypeDefinition.get("id").getAsString());

        String fileString = null;
        try {
            fileString = new String(Files.readAllBytes(resourceFile), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        int i = fileString.indexOf("<?xml");
        if (i > 0) {
            fileString = fileString.substring(i);
        }

        SAXBuilder builder = AppUtils.validatingSaxBuilder();
        builder.setExpandEntities(false);
        Document document = null;
        try {
            if (jsonCapable) {
                document = builder.build(new StringReader(fileString.trim()));
                //.replaceAll("&", "&amp;")));
            } else {
                document = builder.build(new StringReader(fileString.trim()));
            }
        } catch (JDOMException | IOException e) {
            final String message = "error while parsing" + resourceFile.toString() + " from content package" + contentPackage.getId() + "_" + contentPackage.getVersion()
                    + "\n " + e.getMessage() + "\n" + fileString.trim();
            log.error(message, e);
            AppUtils.addToPackageError(contentPackage, message.toString(), contentPackage.getId(), ErrorLevel.WARN);
            template.setBuildStatus(BuildStatus.FAILED);
            return;
        }

        ResourceXmlReader.documentToResource(template, document);

        TypedQuery<Resource> query = em.createQuery("select r from Resource r where r.contentPackage.guid = :pkgGuid and r.id = :id", Resource.class);
        query.setParameter("pkgGuid", packageId);
        query.setParameter("id", template.getId());
        List<Resource> resources = query.getResultList();
        if (resources.isEmpty()) {
            String message = "ContentResource not found " + contentPackage.getId() + " Id: " + template.getId();
            log.error(message);
            return;
        }

        Resource resource = resources.get(0);
        lockController.removeLock(resource.getGuid());
        String oldType = resource.getType();
        String p = contentPackage.getVolumeLocation() + File.separator + resource.getFileNode().getPathTo();
        Path oldPathToResourceFile = FileSystems.getDefault().getPath(p);

        resource.setType(template.getType());
        resource.setTitle(template.getTitle());
        resource.setShortTitle(template.getShortTitle());
        resource.setBuildStatus(template.getBuildStatus());
        resource.getContentPackage().getEdges();

        String content = null;
        String xmlContent = null;

        if (jsonCapable) {
            JsonElement jsonElement = xml2Json.toJson(document.getRootElement(), false);
            Gson gson = AppUtils.gsonBuilder().setPrettyPrinting().create();
            content = gson.toJson(jsonElement);
            //log.debug("Json Content " + content);
            ResourceToXml resourceToXml = new ResourceToXml();
            resourceToXml.setConfig(configuration.get());
            xmlContent = resourceToXml.resourceToXml(resource.getType(), content);
        } else {
            Format format = Format.getPrettyFormat();
            format.setIndent("\t");
            format.setTextMode(Format.TextMode.PRESERVE);
            XMLOutputter xmlOut = new XMLOutputter(format);
            content = xmlOut.outputString(document);
            xmlContent = AppUtils.escapeAmpersand(content);
        }
        contentPackage.getEdges();
        Optional<Document> validatedDoc = validateXmlContent(resource, xmlContent);
        document = validatedDoc.isPresent() ? validatedDoc.get() : null;

        if(conflict) {
            JsonArray errorList = resource.getErrors().getJsonObject().getAsJsonObject().get("errorList").getAsJsonArray();
            JsonObject conflictError = new JsonObject();
            conflictError.addProperty("svnConflict", new SimpleDateFormat("MM/dd/yyyy h:mm:ss a zz").format(new Date()));
            errorList.add(conflictError);
        }

        Gson gson = AppUtils.gsonBuilder().setPrettyPrinting().create();
        if (jsonCapable && document != null && !resource.getType().equalsIgnoreCase("x-oli-learning_objectives")
                && !resource.getType().equalsIgnoreCase("x-oli-skills_model")) {
            // Transform assessment2 and assessment2-pool models to inline-assessment model
            if (resource.getType().equalsIgnoreCase("x-oli-assessment2") || resource.getType().equalsIgnoreCase("x-oli-assessment2-pool")) {
                assessment2Transform.transformToUnified(document.getRootElement());
            }

            JsonElement jsonElement = xml2Json.toJson(document.getRootElement(), false);
            content = gson.toJson(jsonElement);
        }

        RevisionBlob revisionBlob = jsonCapable ? new RevisionBlob(new JsonWrapper(new JsonParser().parse(content)))
                : new RevisionBlob(xmlContent);
        Revision revision = new Revision(resource, resource.getLastRevision(), revisionBlob, "SVNSync");
        revision.setRevisionType(Revision.RevisionType.SYSTEM);
        resource.addRevision(revision);
        resource.setLastRevision(revision);

        resource.setBuildStatus(BuildStatus.READY);
        em.merge(resource);
    }

    private void deleteResource(String packageId, Path resourceFile) {
        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findByGuid", ContentPackage.class);
        q.setParameter("guid", packageId);
        List<ContentPackage> resultList = q.getResultList();
        if (resultList.isEmpty()) {
            String message = "Content Package not found " + packageId;
            log.error(message);
            return;
        }

        ContentPackage contentPackage = resultList.get(0);
        String id = resourceFile.toAbsolutePath().toString();
        id = id.substring(id.lastIndexOf("/") + 1, id.lastIndexOf("."));
        log.info("ContentResource to be deleted ID: " + id);

        TypedQuery<Resource> query = em.createQuery("select r from Resource r where r.contentPackage.guid = :pkgGuid and r.id = :id", Resource.class);
        query.setParameter("pkgGuid", packageId);
        query.setParameter("id", id);
        List<Resource> resources = query.getResultList();
        if (resources.isEmpty()) {
            String message = "ContentResource not found " + contentPackage.getId() + " Id: " + id;
            log.error(message);
            return;
        }

        Resource resource = resources.get(0);
        String p = contentPackage.getVolumeLocation() + File.separator + resource.getFileNode().getPathTo();
        Path oldPathToResourceFile = FileSystems.getDefault().getPath(p);

        if (resource.getLastRevision() == null) {
            // Delete old file
            try {
                Files.deleteIfExists(oldPathToResourceFile);
            } catch (IOException e) {
                log.error("Error deleting file " + oldPathToResourceFile);
            }
        }

        // Soft Delete
        resource.setResourceState(ResourceState.DELETED);
        edgesController.processResourceDelete(contentPackage, resource);
//        contentPackage.getResources().remove(resource);
    }

    private void insertWebContent(String packageId, Path webContentFile) {
        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findByGuid", ContentPackage.class);
        q.setParameter("guid", packageId);
        List<ContentPackage> resultList = q.getResultList();
        if (resultList.isEmpty()) {
            String message = "Content Package not found " + packageId;
            log.error(message);
            return;
        }

        ContentPackage contentPackage = resultList.get(0);
        Path path = FileSystems.getDefault().getPath(contentPackage.getSourceLocation());
        String relativize = path.relativize(webContentFile).toString();
        relativize = relativize.substring(relativize.indexOf("/") + 1);
        path = FileSystems.getDefault().getPath(contentPackage.getWebContentVolume());
        Path uploadPath = path.resolve(relativize);
        log.debug("insertWebContent upload path " + uploadPath.toAbsolutePath());

        directoryUtils.createDirectories(uploadPath.toAbsolutePath().getParent());

        saveFile(webContentFile, uploadPath);

        TypedQuery<FileNode> query = em.createQuery("select r from FileNode r where r.volumeLocation = :volumeLocation and r.pathFrom = :fromPath", FileNode.class);
        query.setParameter("volumeLocation", contentPackage.getWebContentVolume());
        query.setParameter("fromPath", relativize);
        List<FileNode> fileNodes = query.getResultList();
        if (!fileNodes.isEmpty()) {
            String message = "insertWebContent FileNode already exists found " + contentPackage.getId() + " : " + webContentFile.toString();
            log.error(message);
            return;
        }

        WebContent webContent = new WebContent();
        webContent.setContentPackage(contentPackage);

        long size = 0L;
        size = AppUtils.getSize(uploadPath, size);

        String contentType = "undetermined";
        contentType = AppUtils.getFileType(uploadPath, contentType);

        FileNode resourceNode = new FileNode(contentPackage.getWebContentVolume(), "content" + File.separator + relativize, relativize, contentType);
        resourceNode.setFileSize(size);
        webContent.setFileNode(resourceNode);
        try {
            contentPackage.addWebContent(webContent);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        em.persist(webContent);
        em.flush();
        log.info("Done inserting WebContent path " + uploadPath.toFile().getAbsolutePath());
    }

    private void updateWebContent(String packageId, Path webContentFile) {
        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findByGuid", ContentPackage.class);
        q.setParameter("guid", packageId);
        List<ContentPackage> resultList = q.getResultList();
        if (resultList.isEmpty()) {
            String message = "Content Package not found " + packageId;
            log.error(message);
            return;
        }

        ContentPackage contentPackage = resultList.get(0);
        Path path = FileSystems.getDefault().getPath(contentPackage.getSourceLocation());
        String relativize = path.relativize(webContentFile).toString();
        relativize = relativize.substring(relativize.indexOf("/") + 1);
        path = FileSystems.getDefault().getPath(contentPackage.getWebContentVolume());
        Path uploadPath = path.resolve(relativize);

        directoryUtils.createDirectories(uploadPath.toFile().getAbsolutePath());

        saveFile(webContentFile, uploadPath);
    }

    private void deleteWebContent(String packageId, Path webContentFile) {
        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findByGuid", ContentPackage.class);
        q.setParameter("guid", packageId);
        List<ContentPackage> resultList = q.getResultList();
        if (resultList.isEmpty()) {
            String message = "Content Package not found " + packageId;
            log.error(message);
            return;
        }

        ContentPackage contentPackage = resultList.get(0);
        Path path = FileSystems.getDefault().getPath(contentPackage.getSourceLocation());
        String relativize = path.relativize(webContentFile).toString();
        relativize = relativize.substring(relativize.indexOf("/") + 1);
        path = FileSystems.getDefault().getPath(contentPackage.getWebContentVolume());
        Path uploadPath = path.resolve(relativize);

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<FileNode> criteria = cb.createQuery(FileNode.class);
        Root<FileNode> fileNodeRoot = criteria.from(FileNode.class);
        criteria.select(fileNodeRoot).where(cb.and(cb.equal(fileNodeRoot.get("volumeLocation"),
                contentPackage.getWebContentVolume()), cb.equal(fileNodeRoot.get("pathTo"), relativize)));

        List<FileNode> fileNodes = em.createQuery(criteria).getResultList();
        if (fileNodes.isEmpty()) {
            String message = "FileNode not found volume=" + contentPackage.getWebContentVolume() + " package=" + contentPackage.getId() + " : " + webContentFile.toString() + " : pathTo=" + relativize;
            log.error(message);
            return;
        }

        fileNodes.forEach(fn -> {
            TypedQuery<WebContent> q2 = em.createNamedQuery("WebContent.findByFileNode", WebContent.class);
            q2.setParameter("fileNode", fn);
            List<WebContent> webContents = q2.getResultList();
            if (webContents.isEmpty()) {
                String message = "deleteWebContent Webcontent not found " + webContentFile.toString();
                log.error(message);
                return;
            }
            FileNode fileNode = webContents.get(0).getFileNode();
            edgesController.processWebContentDelete(contentPackage, fileNode);
            contentPackage.removeWebContent(webContents.get(0));
        });

        // Delete old file
        try {
            Files.deleteIfExists(uploadPath);
        } catch (IOException e) {
            log.error(String.format("Error deleting webcontent file %s", uploadPath.toAbsolutePath().toString()));
        }
    }

    private void web(String volumeLocation, String pathTo) {


    }

    private void createFile(ContentPackage contentPackage, Resource resource, Path resourceFile, Document document, String xmlContent, String content,
                            JsonObject resourceTypeDefinition) {
        boolean jsonCapable = resourceTypeDefinition.get(JSON_CAPABLE).getAsBoolean();
        String resourceTypeId = resourceTypeDefinition.get("id").getAsString();

        Path packageFolder = Paths.get(contentPackage.getSourceLocation());
        //String pathFrom = "content/" + resourceTypeId + "/" + resource.getId() + ".xml";

        String pathFrom = packageFolder.relativize(resourceFile).toString();

        if (resourceTypeId.equalsIgnoreCase("x-oli-organization")) {
            String orgFolder = resource.getId().substring(resource.getId().lastIndexOf('_') + 1);
            if (orgFolder == null || orgFolder.isEmpty()) {
                String message = "Organization id not well formatted  " + resource.getId();
                log.error(message);
                throw new ResourceException(Response.Status.BAD_REQUEST, contentPackage.getId(), message);
            }
            pathFrom = "organizations/" + resource.getId().substring(resource.getId().lastIndexOf('_') + 1) + "/" + "organization.xml";
        }

        String pathTo = jsonCapable ? pathFrom.substring(0, pathFrom.lastIndexOf(".xml")) + ".json" : pathFrom;

        FileNode packageNode = new FileNode(contentPackage.getVolumeLocation(), pathFrom, pathTo, jsonCapable ? "application/json" : "text/xml");
        resource.setFileNode(packageNode);

        validateXmlContent(resource, xmlContent);

        if (jsonCapable && document != null && !resource.getType().equalsIgnoreCase("x-oli-learning_objectives")
                && !resource.getType().equalsIgnoreCase("x-oli-skills_model")) {
            // Transform assessment2 and assessment2-pool models to inline-assessment model
            if (resource.getType().equalsIgnoreCase("x-oli-assessment2") || resource.getType().equalsIgnoreCase("x-oli-assessment2-pool")) {
                assessment2Transform.transformToUnified(document.getRootElement());
            }

            JsonElement jsonElement = new Xml2Json().toJson(document.getRootElement(), false);
            content = AppUtils.gsonBuilder().setPrettyPrinting().create().toJson(jsonElement);
        }

        // Create ContentResource file
        RevisionBlob revisionBlob = jsonCapable ? new RevisionBlob(new JsonWrapper(new JsonParser().parse(content)))
                : new RevisionBlob(xmlContent);
        Revision revision = new Revision(resource, resource.getLastRevision(), revisionBlob, "SVNSync");
        revision.setRevisionType(Revision.RevisionType.SYSTEM);
        resource.addRevision(revision);
        resource.setLastRevision(revision);
    }

    private Optional<Document> validateXmlContent(Resource resource, String xmlContent) {
        // Validate xmlContent
        int i = xmlContent.indexOf("<?xml");
        if (i > 0) {
            xmlContent = xmlContent.substring(i);
        }
        SAXBuilder builder = AppUtils.validatingSaxBuilder();
        builder.setExpandEntities(false);
        try {

            resetResourceErrors(resource);

            Document document = builder.build(new StringReader(xmlContent));
            log.debug("resource.getType() " + resource.getType());
            //log.debug("XML Content \n" + xmlContent);
            ResourceValidator validator = new BaseResourceValidator();
            validator.initValidator(resource, document, false);
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
                    resourceValidator.initValidator(resource, document, false);
                    resourceValidator.validate();
                } catch (ClassNotFoundException e) {
                    String message = resource.getType() + " validator class not found";
                    log.error(message);
                }
            }
            return Optional.of(document);
        } catch (JDOMException | RuntimeException e) {
            log.error("\n\nValidation issues file=" + resource.getFileNode().getPathFrom() + "\n" + e.getMessage() + "\n" + xmlContent, e);
        } catch (IOException e) {
            log.error("Validation issues IOException " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    private void resetResourceErrors(Resource resource) {
        JsonObject errors = new JsonObject();
        errors.addProperty("resourceErrors", resource.getId() + " Title: " + resource.getTitle());
        JsonArray errorList = new JsonArray();
        errors.add("errorList", errorList);
        resource.setErrors(new JsonWrapper(errors));
    }

    private void updateJsonFile(ContentPackage contentPackage, Resource resource, String content, String oldType,
                                Path oldPathToResourceFile) {
        boolean jsonCapable = configuration.get().getResourceTypeById(resource.getType()).get(JSON_CAPABLE).getAsBoolean();

        // Delete old file
        try {
            Files.deleteIfExists(oldPathToResourceFile);
        } catch (IOException e) {
            log.error("Error deleting file " + oldPathToResourceFile);
        }

        String pathFrom = resource.getFileNode().getPathFrom();
        pathFrom = pathFrom.replaceAll(oldType, resource.getType());

        String pathTo = jsonCapable ? pathFrom.substring(0, pathFrom.lastIndexOf(".xml")) + ".json" : pathFrom;
        FileNode packageNode = new FileNode(contentPackage.getVolumeLocation(), pathFrom, pathTo, jsonCapable ? "application/json" : "text/xml");
        resource.setFileNode(packageNode);

        // Create ContentResource file
        String p = contentPackage.getVolumeLocation() + File.separator + resource.getFileNode().getPathTo();
        directoryUtils.createDirectories(p);

        Path pathToResourceFile = FileSystems.getDefault().getPath(p);
        try {
            Files.write(pathToResourceFile, content.getBytes());
        } catch (IOException e) {
            final String message = "Error: unable to write content file located at - ";
            log.error(message, e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, resource.getId(), message);
        }

        long pkgFileSize = 0L;
        try {
            pkgFileSize = Files.size(pathToResourceFile);
        } catch (IOException e) {
            log.debug("Error determining package file size " + pathToResourceFile);
        }
        packageNode.setFileSize(pkgFileSize);
    }

    /**
     * Save file to webcontent location
     *
     * @param webContentFile
     * @param uploadPath
     */
    private void saveFile(Path webContentFile, Path uploadPath) {
        try (InputStream inputStream = Files.newInputStream(webContentFile);
             OutputStream outpuStream = Files.newOutputStream(uploadPath)) {
            int read = 0;
            byte[] bytes = new byte[1024];
            while ((read = inputStream.read(bytes)) != -1) {
                outpuStream.write(bytes, 0, read);
            }
        } catch (IOException e) {
            final String message = "Error saving webcontent file";
            log.error(message, e);
        }
    }

}
