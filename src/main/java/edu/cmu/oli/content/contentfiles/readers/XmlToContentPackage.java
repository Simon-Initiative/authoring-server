package edu.cmu.oli.content.contentfiles.readers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import edu.cmu.oli.assessment.builders.Assessment2Transform;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.DirectoryUtils;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.contentfiles.writers.ResourceUtils;
import edu.cmu.oli.content.controllers.LDModelController;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.*;
import edu.cmu.oli.content.resource.builders.*;
import edu.cmu.oli.content.resource.validators.BaseResourceValidator;
import edu.cmu.oli.content.resource.validators.ResourceValidator;
import edu.cmu.oli.content.security.AppSecurityController;
import edu.cmu.oli.content.security.Scopes;
import edu.cmu.oli.content.security.Secure;
import org.jdom2.DocType;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;

import javax.ejb.Stateful;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.naming.InitialContext;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Raphael Gachuhi
 */
@Stateful
public class XmlToContentPackage {

    @Inject
    @Logging
    Logger log;

    @Inject
    @Secure
    AppSecurityController auth;

    @Inject
    DirectoryUtils directoryUtils;

    @Inject
    @ConfigurationCache
    Instance<Configurations> config;

    @Inject
    Assessment2Transform assessment2Transform;

    @Inject
    Xml2Json xml2Json;

    ContentPackage contentPackage;

    Map<String, String> oldResourceGuids = new HashMap<>();

    public void setContentPackage(ContentPackage contentPackage) {
        this.contentPackage = contentPackage;
    }

    public void setOldResourceGuids(Map<String, String> oldResourceGuids) {
        this.oldResourceGuids = oldResourceGuids;
    }

    public void processPackage(Path packageFolder, Path pkgXml) {
        log.debug("processPackage: " + packageFolder.toString() + "\n" + pkgXml.toString());
        SAXBuilder builder = AppUtils.validatingSaxBuilder();
        builder.setExpandEntities(false);
        Document document = null;
        try {
            document = builder.build(pkgXml.toFile());
        } catch (JDOMException | IOException e) {
            final String message = "error while parsing package.xml file from " + packageFolder.toString();
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
        try {
            long pkgFileSize = 0L;
            try {
                pkgFileSize = Files.size(pkgXml);
            } catch (IOException e) {
            }
            JsonObject packageJson = ContentPkgXmlReader.documentToSimpleManifest(document);
            contentPackage.setBuildStatus(BuildStatus.PROCESSING);
            ContentPkgJsonReader.parsePackageJson(contentPackage, packageJson, false);
            contentPackage.setDoc(new JsonWrapper(packageJson));

            //----------------------
            // Cleanup course-content-xml folder; remove duplicates
            cleanupXMLFolder(packageFolder, contentPackage.getId(), contentPackage.getVersion());

            String volumeLocation = this.config.get().getContentVolume();
            volumeLocation = volumeLocation + File.separator + contentPackage.getId() + "_" + AppUtils.generateUID(12);
            while (Files.exists(Paths.get(volumeLocation))) {
                volumeLocation = volumeLocation + File.separator + contentPackage.getId() + "_" + AppUtils.generateUID(12);
            }
            contentPackage.setVolumeLocation(volumeLocation);
            contentPackage.setSourceLocation(packageFolder.toString());

            String webContentVolume = this.config.get().getWebContentVolume();
            String s = (UUID.randomUUID()).toString();
            webContentVolume = webContentVolume + File.separator + s;
            contentPackage.setWebContentVolume(webContentVolume);

            final URI uri = packageFolder.toUri();
            String pathFrom = uri.relativize(pkgXml.toUri()).getPath();
            String pathTo = pathFrom.substring(0, pathFrom.lastIndexOf(".xml")) + ".json";

            FileNode packageNode = new FileNode(volumeLocation, pathFrom, pathTo, "application/json");
            packageNode.setFileSize(pkgFileSize);
            contentPackage.setFileNode(packageNode);

        } catch (BuildException e) {
            final String message = "Error: unable to parse package.xml file located at - " + packageFolder.toString();
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    private void cleanupXMLFolder(Path packageFolder, String id, String version) {
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        builder.setExpandEntities(false);
        Path path = FileSystems.getDefault().getPath(config.get().getContentSourceXml());
        File[] directFolders = path.toFile().listFiles(pathname -> pathname.isDirectory());
        if (directFolders == null) {
            return;
        }
        for (File pi : directFolders) {
            boolean delete = false;
            Path path1 = pi.toPath();
            if (path1.equals(packageFolder)) {
                continue;
            }
            Path pkgXml = path1.resolve("content/package.xml");
            if (!pkgXml.toFile().exists()) {
                //If pkgXml absent, assume not a valid content folder, so delete it
                directoryUtils.deleteDirectory(path1);
                continue;
            }
            Document document = null;
            try {
                document = builder.build(pkgXml.toFile());
            } catch (Throwable e) {
                final String message = "Error during cleanupXMLFolder " + pi.getPath();
                log.error(message, e);
                continue;
            }
            Element rootElement = document.getRootElement();

            if (!"package".equals(rootElement.getName()) || (rootElement.getAttribute("id").getValue().equalsIgnoreCase(id) &&
                    rootElement.getAttribute("version").getValue().equalsIgnoreCase(version))) {
                delete = true;
            }
            if (delete) {
                directoryUtils.deleteDirectory(path1);
            }
        }
    }

    public void walkContentFolder(final Path packageFolder) {
        log.debug("Content folder processing started packageId=" + contentPackage.getId() + " PackageFolder="+packageFolder.toString());
        try {
            Files.walkFileTree(packageFolder, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (super.visitFile(file, attrs).equals(FileVisitResult.CONTINUE)) {
                        if (Files.isHidden(file) || file.toString().contains(".idea")) {
                            return FileVisitResult.CONTINUE;
                        }
                        String pathString = file.toString();
                        if (pathString.contains(packageFolder.toString()+"/content/")) {
                            if(pathString.contains("/webcontent/")) {
                                processWebcontentFile(packageFolder, file);
                                return FileVisitResult.CONTINUE;
                            }
                            Configurations configurations = config.get();
                            final Set<String> resourceTypesIds = configurations.getResourceTypes().keySet();
                            resourceTypesIds.stream().filter(id -> pathString.contains("/" + id + "/")).map(
                                    configurations::getResourceTypeById).findFirst().ifPresent(resourceType -> processResourceFile(
                                    packageFolder, file, resourceType));
                            return FileVisitResult.CONTINUE;
                        }

                        if (pathString.contains(packageFolder.toString()+"/organizations/")) {
                            Configurations configurations = config.get();
                            processResourceFile(packageFolder, file, configurations.getResourceTypeById("x-oli-organization"));
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

        } catch (IOException e) {
            final String message = "Error while processing course content files from folder " + packageFolder.toString();
            log.error(message, e);
        }

    }

    private void processResourceFile(final Path packageFolder, final Path file, JsonObject resourceType) {
        Resource rsrc = new Resource();
        rsrc.setBuildStatus(BuildStatus.PROCESSING);
        // Use filename as the default resource id
        String name = file.getFileName().toString();
        if (!name.endsWith(".xml")) {
            // :FIXME: Not an OLI resource type, what to do with it? process as webcontent and move it to webcontent?
            processWebcontentFile(packageFolder, file);
            return;
        }

        boolean jsonCapable = resourceType.get("jsonCapable").getAsBoolean();
        String id = name.substring(0, name.lastIndexOf(".xml"));
        if (id.equalsIgnoreCase("organization")) {
            SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
            builder.setExpandEntities(false);
            Document document = null;
            try {
                document = builder.build(new StringReader(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)));
                id = document.getRootElement().getAttributeValue("id");
            } catch (JDOMException | IOException e) {
                log.error(e.getLocalizedMessage(), e);
            }
        }
        String oldGuid = oldResourceGuids.get(contentPackage.getId() + ":" + contentPackage.getVersion() + ":" + id);
        if(oldGuid != null){
            rsrc.setGuid(oldGuid);
        }
        rsrc.setId(id);
        Optional<Resource> exists = resourceById(rsrc.getId());
        rsrc = exists.isPresent() ? exists.get() : rsrc;

        rsrc.setType(resourceType.get("id").getAsString());
        rsrc.setContentPackage(contentPackage);

        final URI uri = packageFolder.toUri();
        String path = uri.relativize(file.toUri()).getPath();
        String jpath = jsonCapable ? path.substring(0, path.lastIndexOf(".xml")) + ".json" : path;
        FileNode resourceNode = new FileNode(contentPackage.getVolumeLocation(), path, jpath, jsonCapable ? "application/json" : "text/xml");

        rsrc.setFileNode(resourceNode);
        if (!exists.isPresent()) {
            contentPackage.addResource(rsrc);
        }
    }

    private Optional<Resource> resourceById(String id) {
        return contentPackage.getResources().stream().filter(resource -> resource.getId().equals(id)).findFirst();
    }

    private void processWebcontentFile(final Path packageFolder, final Path file) {
        WebContent webContent = new WebContent();
        Path relativized = packageFolder.relativize(file);
        String path = relativized.toString();
        try {
            byte[] bytes = Files.readAllBytes(file);
            MessageDigest digest = MessageDigest.getInstance("MD5");
            String md5 = AppUtils.convertByteArrayToHexString(digest.digest(bytes));
            webContent.setMd5(md5);
        } catch (IOException | NoSuchAlgorithmException e) {
        }
        List<WebContent> webContents = webContentByMd5(webContent.getMd5());

        Optional<WebContent> exists = webContents.stream().filter(w -> w.getFileNode().getPathFrom().equals(path)).findFirst();

        if (exists.isPresent()) {
            webContent = exists.get();
        } else {
            webContent.setContentPackage(contentPackage);
        }

        String contentType = AppUtils.getFileType(file, "undetermined");

        // Remove the leading "content/"
        String toPath = path.substring(path.indexOf("/") + 1);
        FileNode resourceNode = new FileNode(contentPackage.getWebContentVolume(), path, toPath, contentType);
        webContent.setFileNode(resourceNode);
        if (!exists.isPresent()) {
            contentPackage.addWebContent(webContent);
        }
    }

    private List<WebContent> webContentByMd5(String id) {
        if (id == null) {
            return new ArrayList<>();
        }
        return contentPackage.getWebContents().stream().filter(webContent -> webContent.getMd5() != null && webContent.getMd5().equals(id)).collect(Collectors.toList());
    }

    public void clearVolume() {
        Path path = FileSystems.getDefault().getPath(contentPackage.getVolumeLocation());
        if (path.toFile().exists()) {
            int status = directoryUtils.deleteDirectory(path);
            if (status < 0) {
                final String message = "Error deleting old volume " + contentPackage.getVolumeLocation();
                log.error(message);
            }
        }
    }

    public void parseContent(Path packageFolder) {
        log.debug("parseContent " + contentPackage.getVolumeLocation());
        clearVolume();

        Set<FNode> fileFNodes = new HashSet<>();
        fileFNodes.add(new FNode(contentPackage, contentPackage.getFileNode()));
        if (contentPackage.getIcon() != null) {
            fileFNodes.add(new FNode(contentPackage, contentPackage.getIcon().getFileNode()));
        }
        for (Resource resource : contentPackage.getResources()) {
            fileFNodes.add(new FNode(resource, resource.getFileNode()));
        }
        for (WebContent webContent : contentPackage.getWebContents()) {
            fileFNodes.add(new FNode(webContent, webContent.getFileNode()));
        }
        for (FNode fileFNode : fileFNodes) {
            String pathString = fileFNode.fileNode.getPathFrom();
            final Path fullPathPkgFolder = packageFolder.resolve(pathString);
            if (fullPathPkgFolder.toString().contains(packageFolder.toString()+"/content/")) {
                if (pathString.contains("/webcontent/")) {
                    log.info(fullPathPkgFolder.toString());
                    parseWebContentFile(contentPackage, fileFNode, fullPathPkgFolder);
                    continue;
                }
                Configurations configurations = config.get();
                final Set<String> resourceTypesIds = configurations.getResourceTypes().keySet();
                resourceTypesIds.stream().filter(id -> pathString.contains("/" + id + "/")).map(
                        configurations::getResourceTypeById).findFirst().ifPresent(resourceType -> parseResourceFile(contentPackage, fileFNode, fullPathPkgFolder));
                continue;
            }

            if (fullPathPkgFolder.toString().contains(packageFolder.toString()+"/organizations/")) {
                parseResourceFile(contentPackage, fileFNode, fullPathPkgFolder);
            }
        }

        log.info("Done parsing content files for package=" + contentPackage.getId() + " version=" + contentPackage.getVersion() + " location=" + contentPackage.getSourceLocation());
    }

    public Set<FNode> fileNodes() {
        Set<FNode> fileFNodes = new HashSet<>();
        fileFNodes.add(new FNode(contentPackage, contentPackage.getFileNode()));
        if (contentPackage.getIcon() != null) {
            fileFNodes.add(new FNode(contentPackage, contentPackage.getIcon().getFileNode()));
        }
        for (Resource resource : contentPackage.getResources()) {
            fileFNodes.add(new FNode(resource, resource.getFileNode()));
        }
        for (WebContent webContent : contentPackage.getWebContents()) {
            fileFNodes.add(new FNode(webContent, webContent.getFileNode()));
        }
        return fileFNodes;
    }

    private void parseWebContentFile(ContentPackage contentPackage, FNode fileFNode, Path file) {
        log.debug("parseWebContentFile " + file.toString());
        String p = contentPackage.getWebContentVolume() + File.separator + fileFNode.fileNode.getPathTo();
        final Path dirs = FileSystems.getDefault().getPath(p.substring(0, p.lastIndexOf(File.separator)));
        Path pathTo = FileSystems.getDefault().getPath(p);
        try {
            Files.createDirectories(dirs);
        } catch (IOException e) {
            final String message = "error while saving" + p + " from content package" + contentPackage.getId() + "_" + contentPackage.getVersion()
                    + "\n " + e.getMessage();
            log.error(message, e);
        }
        if (!Files.exists(file)) {
            WebContent webContent = (WebContent) fileFNode.parent;
            webContent.setResourceState(ResourceState.DELETED);
            // File has been deleted remove
            // :FIXME: mark deleted instead of remove
//            contentPackage.getWebContents().remove(fileFNode.parent);
//            try {
//                Files.delete(pathTo);
//            } catch (IOException e) {
//            }
        } else {
            try {
                if (!Files.exists(pathTo)) {
                    Files.copy(file, pathTo);
                }
            } catch (IOException e) {
                final String message = "error while saving" + p + " from content package" + contentPackage.getId() + "_" + contentPackage.getVersion()
                        + "\n " + e.getMessage();
                log.error(message, e);
            }
        }
    }

    private void parseResourceFile(ContentPackage contentPackage, FNode resourceFNode, Path file) {
        log.debug("parseResourceFile " + file.toString());
        if (!(resourceFNode.parent instanceof Resource)) {
            log.error("File is not an instance of Resource" + resourceFNode.parent + " Path=" + file.toString());
            return;
        }
        Resource rsrc = (Resource) resourceFNode.parent;
        if (!Files.exists(file)) {
            // File no longer exists
            // Mark resource as deleted
            rsrc.setResourceState(ResourceState.DELETED);
            return;
        }
        String fileString = null;
        try {
            fileString = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        JsonObject resourceTypeDef = this.config.get().getResourceTypeById(rsrc.getType());
        boolean jsonCapable = resourceTypeDef.get("jsonCapable").getAsBoolean();

        // This is a hack to fix pool doctype definition error
        if(rsrc.getType().equalsIgnoreCase("x-oli-assessment2-pool")) {
            if (resourceTypeDef.has("PUBLIC_ID") && resourceTypeDef.has("SYSTEM_ID")) {
                SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);;
                builder.setExpandEntities(false);
                try {
                    Document document = builder.build(new StringReader(fileString.trim()));
                    if(document.getDocType().getPublicID().contains("Pool 2.4")) {
                        // Document type
                        DocType docType = new DocType("pool", resourceTypeDef.get("PUBLIC_ID").getAsString(),
                                resourceTypeDef.get("SYSTEM_ID").getAsString());
                        document.setDocType(docType);
                        Format format = Format.getPrettyFormat();
                        format.setIndent("\t");
                        format.setTextMode(Format.TextMode.PRESERVE);
                        fileString = new XMLOutputter(format).outputString(document);
                        Files.write(file, fileString.getBytes());
                    }
                } catch (Exception e) {}

            }
        }
        // End of hack

        SAXBuilder builder = AppUtils.validatingSaxBuilder();
        builder.setExpandEntities(false);
        int i = fileString.indexOf("<?xml");
        if (i > 0) {
            fileString = fileString.substring(i);
        }
        Document document;
        try {
                document = builder.build(new StringReader(fileString.trim()));
        } catch (JDOMException | IOException e) {
            final String message = "error while parsing" + file.toString() + " from content package" + contentPackage.getId() + "_" + contentPackage.getVersion()
                    + "\n " + e.getMessage() + "\n" + fileString.trim();
            log.error(message, e);
            AppUtils.addToPackageError(contentPackage, message.toString(), rsrc.getId(), ErrorLevel.WARN);
            rsrc.setBuildStatus(BuildStatus.FAILED);
            return;
        }

        String name = file.getFileName().toString();
        if (!(name.equals("organization.xml")) && !name.equals(rsrc.getId() + ".xml")) {
            final StringBuilder message = new StringBuilder();
            message.append("Resource ID does not match filename: ");
            message.append("id=").append(rsrc.getId()).append(", ");
            message.append("filename=").append(name);
            AppUtils.addToPackageError(rsrc.getContentPackage(), message.toString(), rsrc.getId(), ErrorLevel.ERROR);
            log.debug(message.toString());
        }

        ResourceUtils.adjustNestedBlocks(document);
        ResourceXmlReader.documentToResource(rsrc, document);

        ResourceValidator validator = new BaseResourceValidator();
        validator.initValidator(rsrc, document, false);
        validator.validate();
        Configurations serviceConfig = this.config.get();
        JsonObject resourceTypeDefinition = serviceConfig.getResourceTypeById(rsrc.getType());
        if (resourceTypeDefinition == null || !resourceTypeDefinition.has("validatorClass")) {
            String message = rsrc.getType() + " has no validator class";
            log.error(message);
        } else {
            String validatorClass = resourceTypeDefinition.get("validatorClass").getAsString();
            try {
                Class<?> aClass = Class.forName(validatorClass);
                ResourceValidator resourceValidator = (ResourceValidator) AppUtils.lookupCDIBean(aClass);
                resourceValidator.initValidator(rsrc, document, false);
                resourceValidator.validate();
            } catch (ClassNotFoundException e) {
                String message = rsrc.getType() + " validator class not found";
                log.error(message);
            }
        }
        Format format = Format.getPrettyFormat();
        format.setIndent("\t");
        format.setTextMode(Format.TextMode.PRESERVE);
        XMLOutputter xmlOut = new XMLOutputter(format);

        if (jsonCapable) {
            // Transform assessment2 and assessment2-pool models to inline-assessment model
            if (rsrc.getType().equalsIgnoreCase("x-oli-assessment2") || rsrc.getType().equalsIgnoreCase("x-oli-assessment2-pool")) {
                assessment2Transform.transformToUnified(document.getRootElement());
            }
            JsonElement jsonElement = xml2Json.toJson(document.getRootElement(), false);
            RevisionBlob revisionBlob = new RevisionBlob(new JsonWrapper(jsonElement));
            Revision revision = new Revision(rsrc, rsrc.getLastRevision(), revisionBlob, "Import");
            revision.setRevisionType(Revision.RevisionType.SYSTEM);
            rsrc.addRevision(revision);
            rsrc.setLastRevision(revision);

        } else {
            RevisionBlob revisionBlob = new RevisionBlob(xmlOut.outputString(document));
            Revision revision = new Revision(rsrc, rsrc.getLastRevision(), revisionBlob, "Import");
            revision.setRevisionType(Revision.RevisionType.SYSTEM);
            rsrc.addRevision(revision);
            rsrc.setLastRevision(revision);
        }

        rsrc.setBuildStatus(BuildStatus.READY);
    }

    public void restorUserPackagePermissions(Set<String> existingPkgDevelopers, String packageGuid, String packageTitle) {
        // Restore developer permissions.
        for (String developer : existingPkgDevelopers) {
            Map<String, List<String>> userPermission = new HashMap<>();
            userPermission.put(packageGuid, Arrays.asList(Scopes.VIEW_MATERIAL_ACTION.toString(),
                    Scopes.EDIT_MATERIAL_ACTION.toString(), "ContentPackage", packageTitle));
            auth.updateUserAttributes(developer, userPermission, null);
        }
    }

    public void finalizePackageImport() {
        try {
            String oliWebcontentVolume = contentPackage.getWebContentVolume();
            String webContentVolume = this.config.get().getWebContentVolume();
            final String guid = contentPackage.getGuid();
            webContentVolume = webContentVolume + File.separator + guid;
            contentPackage.setWebContentVolume(webContentVolume);
            contentPackage.getWebContents().forEach(webContent -> {
                webContent.getFileNode().setVolumeLocation(contentPackage.getWebContentVolume());
            });

//            Path path = FileSystems.getDefault().getPath(contentPackage.getVolumeLocation());
//            directoryUtils.deleteDirectory(path);
//            Files.move(Paths.get(contentPackage.getVolumeLocation() + "-temp"), Paths.get(contentPackage.getVolumeLocation()), StandardCopyOption.REPLACE_EXISTING);
            Path oldWebContent = Paths.get(oliWebcontentVolume);
            if (Files.exists(oldWebContent) && !oliWebcontentVolume.equals(webContentVolume)) {
                Files.move(oldWebContent, Paths.get(webContentVolume));
            }
            final String sourceLocation = contentPackage.getSourceLocation();

            Executors.newSingleThreadScheduledExecutor().schedule(() -> doProcessLDmodelFiles(guid, sourceLocation), 2, TimeUnit.SECONDS);

            log.info("Done processing content import for package=" + contentPackage.getId() + " version=" + contentPackage.getVersion() + " location=" + contentPackage.getSourceLocation());
        } catch (IOException e) {
            final String message = "Error: unable to persist content package- " + contentPackage.toString();
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    private void doProcessLDmodelFiles(String packageGuid, String sourceLocation) {
        if (packageGuid == null) {
            log.error("Unable to import ld model");
            return;
        }

        Path ldFolder = Paths.get(sourceLocation).resolve("ldmodel");
        if (!Files.exists(ldFolder)) {
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

        doLDImport(packageGuid, skillsModel, problemsModel, losModel, new AtomicInteger(1));
    }

    private void doLDImport(String packageGuid, Optional<String> skillsModel, Optional<String> problemsModel, Optional<String> losModel, AtomicInteger retry) {
        try {
            LDModelController ldModelController = (LDModelController)
                    new InitialContext().lookup("java:global/content-service/LDModelControllerImpl");
            ldModelController.importLDModel(packageGuid, skillsModel, problemsModel, losModel);
            return;
        } catch (Throwable e) {
            int cnt = retry.getAndIncrement();
            log.error(e.getMessage() + "Retrying " + cnt, e);
            if(cnt > 4){
                return;
            }
        }
        Executors.newSingleThreadScheduledExecutor().schedule(() -> doLDImport(packageGuid, skillsModel, problemsModel, losModel, retry), 5, TimeUnit.SECONDS);
    }

    public class FNode {
        Object parent;
        FileNode fileNode;

        public FNode(Object parent, FileNode fileNode) {
            this.parent = parent;
            this.fileNode = fileNode;
        }
    }
}
