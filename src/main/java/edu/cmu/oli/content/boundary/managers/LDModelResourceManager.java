package edu.cmu.oli.content.boundary.managers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.ContentServiceException;
import edu.cmu.oli.content.DirectoryUtils;
import edu.cmu.oli.content.ResourceException;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.controllers.LDModelController;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import edu.cmu.oli.content.security.AppSecurityContext;
import edu.cmu.oli.content.security.AppSecurityController;
import edu.cmu.oli.content.security.Scopes;
import edu.cmu.oli.content.security.Secure;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static edu.cmu.oli.content.security.Roles.ADMIN;
import static edu.cmu.oli.content.security.Roles.CONTENT_DEVELOPER;

/**
 * @author Raphael Gachuhi
 */
@Stateless
public class LDModelResourceManager {

    @Inject
    @Logging
    Logger log;

    @PersistenceContext
    EntityManager em;

    @Inject
    LDModelController ldModelController;

    @Inject
    @Secure
    AppSecurityController securityManager;

    @Inject
    DirectoryUtils directoryUtils;

    @Inject
    @ConfigurationCache
    Instance<Configurations> config;

    public JsonElement importLDModel(AppSecurityContext session, List<InputPart> inputParts, String packageId) {
        securityManager.authorize(session,
                Arrays.asList(ADMIN, CONTENT_DEVELOPER),
                packageId, "name=" + packageId, Arrays.asList(Scopes.EDIT_MATERIAL_ACTION));

        ContentPackage contentPackage = findContentPackage(packageId);

        Map<String, String> conts = new HashMap<>();

        try {
            for (InputPart part : inputParts) {
                Optional<String> fileNameOption = getFileName(part.getHeaders());
                if (!fileNameOption.isPresent()) {
                    String message = "Error uploading file(s) to course package " +
                            contentPackage.getId() + "_" + contentPackage.getVersion() + " " + part.getHeaders().getFirst("content-disposition");
                    log.error(message);
                    throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, packageId, message);
                }

                String fileName = fileNameOption.get();
                if (!(fileName.toLowerCase().endsWith("skills.tsv") || fileName.toLowerCase().endsWith("problems.tsv")
                        || fileName.toLowerCase().endsWith("los.tsv"))) {
                    String message = "Error: filenames must end with 'skills.tsv', 'problems.tsv' or 'los.tsv' ";
                    log.error(message);
                    throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, packageId, message);
                }
                fileName = fileName.replaceAll(" ", "_");

                InputStream inputStream = part.getBody(InputStream.class, null);

                String content = AppUtils.inputStreamToString(inputStream);

                conts.put(fileName, content);
                log.debug(content);
            }
        } catch (IOException e) {
            String message = "Error uploading file(s) to course package " + contentPackage.getId() + "_" + contentPackage.getVersion();
            log.error(message, e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, packageId, message);
        }


        try {
            List<String> messages = this.ldModelController.importLDModel(contentPackage, Optional.ofNullable(conts.get("skills.tsv")),
                    Optional.ofNullable(conts.get("problems.tsv")), Optional.ofNullable(conts.get("los.tsv")));

            conts.entrySet().forEach(e -> {
                try {

                    String fileName = e.getKey();
                    if (!fileName.contains("webcontent")) {
                        fileName = "webcontent" + File.separator + "ld_model" + File.separator + fileName;
                    }
                    String uploadLocation = contentPackage.getWebContentVolume() + File.separator + fileName;
                    Path uploadPath = FileSystems.getDefault().getPath(uploadLocation);

                    Files.deleteIfExists(uploadPath);

                    directoryUtils.createDirectories(uploadLocation);

                    saveFile(e.getValue(), uploadPath);

                    String uploadToSourceLocation = contentPackage.getSourceLocation() + File.separator + "content" + File.separator + fileName;

                    directoryUtils.createDirectories(uploadToSourceLocation);

                    // Saves the file to local working copy
                    saveFile(e.getValue(), FileSystems.getDefault().getPath(uploadToSourceLocation));

                } catch (IOException ex) {
                    String message = "Error uploading file(s) to course package " + contentPackage.getId() + "_" + contentPackage.getVersion();
                    log.error(message, e);
                    throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, packageId, message);
                }
            });

            JsonObject ldModelUpload = new JsonObject();
            ldModelUpload.addProperty("ld_import", "LDModel import processed");
            ldModelUpload.add("messages", AppUtils.gsonBuilder().create().toJsonTree(messages));
            return ldModelUpload;
        } catch (ContentServiceException e) {
            log.error(e.getMessage(), e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, packageId, e.getMessage());
        }
    }

    public ByteArrayOutputStream exportLDModel(AppSecurityContext session, String packageId) {
        securityManager.authorize(session,
                Arrays.asList(ADMIN, CONTENT_DEVELOPER),
                packageId, "name=" + packageId, Arrays.asList(Scopes.EDIT_MATERIAL_ACTION));

        ContentPackage pkg = findContentPackage(packageId);

        try {
            return this.ldModelController.exportLDModel(pkg);
        } catch (ContentServiceException e) {
            log.error(e.getMessage(), e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, packageId, e.getMessage());
        }

    }

    /**
     * Utility method to get file name from HTTP header content-disposition
     */
    private Optional<String> getFileName(MultivaluedMap<String, String> headers) {
        String contentDisp = headers.getFirst("content-disposition");
        String[] tokens = contentDisp.split(";");
        for (String token : tokens) {
            if (token.trim().startsWith("filename")) {
                return Optional.of(token.substring(token.indexOf('=') + 2, token.length() - 1));
            }
        }
        return Optional.empty();
    }

    /**
     * Save file to webcontent location
     *
     * @param data
     * @param webcontentLocation
     */
    private void saveFile(String data, Path webcontentLocation) throws IOException {
        Files.write(webcontentLocation, data.getBytes());
    }

    private ContentPackage findContentPackage(String packageId) {
        ContentPackage contentPackage = null;
        try {
            contentPackage = em.find(ContentPackage.class, packageId);
            if (contentPackage == null) {
                String message = "Error: package requested was not found " + packageId;
                log.error(message);
                throw new ResourceException(Response.Status.NOT_FOUND, packageId, message);
            }
        } catch (IllegalArgumentException e) {
            String message = "Server Error while locating package " + packageId;
            log.error(message);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, packageId, message);
        }
        return contentPackage;
    }
}
