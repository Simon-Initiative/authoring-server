package edu.cmu.oli.content.contentfiles;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.DirectoryUtils;
import edu.cmu.oli.content.boundary.endpoints.ContentPackageResource;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.contentfiles.readers.XmlToContentPackage;
import edu.cmu.oli.content.controllers.EdgesController;
import edu.cmu.oli.content.controllers.LDModelController;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.entities.BuildStatus;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import edu.cmu.oli.content.security.AppSecurityController;
import edu.cmu.oli.content.security.Scopes;
import edu.cmu.oli.content.security.Secure;
import edu.cmu.oli.content.security.UserInfo;
import org.jboss.ejb3.annotation.TransactionTimeout;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.naming.InitialContext;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Raphael Gachuhi
 */
@Stateless
@TransactionTimeout(value = 60L, unit = TimeUnit.MINUTES)
public class PackageProcessor {

    @Inject
    @Logging
    Logger log;

    @PersistenceContext
    EntityManager em;

    @Inject
    EdgesController edgesController;

    @Inject
    DirectoryUtils directoryUtils;

    @Inject
    @Secure
    AppSecurityController auth;

    @Inject
    @ConfigurationCache
    Instance<Configurations> config;

    @Inject
    XmlToContentPackage xmlToContentPackage;

    public void processPackage(Path packageFolder, Path pkgXml, String instruction) {
        ContentPackage contentPackage = new ContentPackage();
        Configurations configurations = config.get();
        for (JsonElement jsonElement : configurations.getThemes()) {
            JsonObject theme = jsonElement.getAsJsonObject();
            if (theme.has("default") && theme.get("default").getAsBoolean()) {
                contentPackage.setTheme(theme.get("id").getAsString());
                continue;
            }
        }

        try {
            xmlToContentPackage.setContentPackage(contentPackage);
            xmlToContentPackage.processPackage(packageFolder, pkgXml);

            contentPackage.setBuildStatus(BuildStatus.PROCESSING);
            String id = contentPackage.getId();
            String version = contentPackage.getVersion();
            contentPackage.setPackageFamily(id);
            TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findByIdAndVersion", ContentPackage.class);
            q.setParameter("id", id);
            q.setParameter("version", version);

            ContentPackage existingPkg = q.getResultList().isEmpty() ? null : q.getResultList().get(0);
            Set<String> existingPkgDevelopers = new HashSet<>();
            if (existingPkg != null) {
                if (instruction.equals("redeploy")) {
//                // :FIXME: very inefficient. Need to filter users by role
                    List<UserInfo> allUsers = auth.getAllUsers();

                    for (UserInfo ur : allUsers) {
                        if (ur.getServiceAccountClientId() != null || ur.getUsername().equalsIgnoreCase("manager")) {
                            continue;
                        }
                        Map<String, List<String>> attributes = ur.getAttributes();
                        boolean isDeveloper = attributes != null && attributes.containsKey(existingPkg.getGuid());
                        if (isDeveloper) {
                            existingPkgDevelopers.add(ur.getUsername());
                        }
                    }
                    contentPackage.setGuid(existingPkg.getGuid());
                    Map<String, String> oldResourceGuids = new HashMap<>();
                    existingPkg.getResources().forEach(resource -> oldResourceGuids.put(id+":"+version+":"+resource.getId(), resource.getGuid()));
                    xmlToContentPackage.setOldResourceGuids(oldResourceGuids);
                    removeExistingPackage(existingPkg);
                } else {
                    existingPkg.setDoc(contentPackage.getDoc());
                    if (existingPkg.getPackageFamily() == null) {
                        existingPkg.setPackageFamily(existingPkg.getId());
                    }
                    existingPkg.setBuildStatus(BuildStatus.PROCESSING);
                    contentPackage = existingPkg;
                    xmlToContentPackage.setContentPackage(contentPackage);
                }

            }

            if (instruction.equals("redeploy") || existingPkg == null) {

                em.persist(contentPackage);
                // Issue flush to get the Guid for next steps
                em.flush();

                auth.createResource(contentPackage.getGuid(), "/packages/" + contentPackage.getGuid(),
                        ContentPackageResource.resourceType,
                        Arrays.asList(Scopes.VIEW_MATERIAL_ACTION, Scopes.EDIT_MATERIAL_ACTION));
            }

            xmlToContentPackage.walkContentFolder(packageFolder);

            xmlToContentPackage.parseContent(packageFolder);

            xmlToContentPackage.finalizePackageImport();

            contentPackage.setBuildStatus(BuildStatus.READY);
            em.merge(contentPackage);
            em.flush();

            if (instruction.equals("redeploy") && existingPkg != null) {
                xmlToContentPackage.restorUserPackagePermissions(existingPkgDevelopers, contentPackage.getGuid(), contentPackage.getTitle());
            }

            edgesController.validateAllEdges(contentPackage);
            em.merge(contentPackage);

        } catch (Throwable e) {
            final String message = "Error while processing course content package import " + packageFolder.toString();
            log.error(message, e);
            if (xmlToContentPackage != null && contentPackage != null && em != null) {
                contentPackage.setBuildStatus(BuildStatus.FAILED);
                em.merge(contentPackage);
            }
        }
    }

    private void removeExistingPackage(ContentPackage existingPkg) {
        if (existingPkg != null) {
            log.debug("Removing existing package: id " + existingPkg.getId() + " version " + existingPkg.getVersion());
            em.remove(existingPkg);
            em.flush();

            // Delete resource from Keycloak
            try {
                auth.deleteResource(existingPkg.getGuid());
            } catch (Exception e) {
                log.error(e.getLocalizedMessage(), e);
            }

            Path path = FileSystems.getDefault().getPath(existingPkg.getWebContentVolume());
            directoryUtils.deleteDirectory(path);

            path = FileSystems.getDefault().getPath(existingPkg.getVolumeLocation());
            directoryUtils.deleteDirectory(path);
        }
    }

    public void processLDModelImport(Path packageFolder, Path pkgXml) {

        SAXBuilder builder = AppUtils.validatingSaxBuilder();
        builder.setExpandEntities(false);
        Document document = null;
        try {
            document = builder.build(pkgXml.toFile());
        } catch (JDOMException | IOException e) {
            final String message = "error while parsing package.xml file from " + packageFolder.toString();
            log.error(message, e);
            return;
        }
        String pkgId = document.getRootElement().getAttributeValue("id");
        String version = document.getRootElement().getAttributeValue("version");
        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findByIdAndVersion", ContentPackage.class);
        q.setParameter("id", pkgId);
        q.setParameter("version", version);

        ContentPackage existingPkg = q.getResultList().isEmpty() ? null : q.getResultList().get(0);
        if (existingPkg == null) {
            return;
        }

        Path ldFolder = Paths.get(existingPkg.getSourceLocation()).resolve("ldmodel");
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

        try {
            LDModelController ldModelController = (LDModelController)
                    new InitialContext().lookup("java:global/content-service/LDModelControllerImpl");
            ldModelController.importLDModel(existingPkg, skillsModel, problemsModel, losModel);
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }
    }

}
