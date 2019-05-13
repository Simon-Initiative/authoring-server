package edu.cmu.oli.content.boundary.managers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.DirectoryUtils;
import edu.cmu.oli.content.ResourceException;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.controllers.EdgesController;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.PaginatedResponse;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import edu.cmu.oli.content.models.persistance.entities.FileNode;
import edu.cmu.oli.content.models.persistance.entities.WebContent;
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
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static edu.cmu.oli.content.security.Roles.ADMIN;
import static edu.cmu.oli.content.security.Roles.CONTENT_DEVELOPER;

/**
 * @author Raphael Gachuhi
 */
@Stateless
public class WebResourceManager {

    @Inject
    @Logging
    Logger log;

    @PersistenceContext
    EntityManager em;

    @Inject
    @Secure
    AppSecurityController securityManager;

    @Inject
    EdgesController edgesController;

    @Inject
    DirectoryUtils directoryUtils;

    @Inject
    @ConfigurationCache
    Instance<Configurations> config;

    public JsonElement webcontents(AppSecurityContext session, String packageId, int offset, int limit,
                                   String order, String orderBy, String guidFilter, String mimeFilter,
                                   String pathFilter, String searchText) {
        // verify that the session is authorized for this operation
        securityManager.authorize(session,
                Arrays.asList(ADMIN, CONTENT_DEVELOPER),
                packageId, "name=" + packageId, Arrays.asList(Scopes.VIEW_MATERIAL_ACTION));

        CriteriaBuilder cb = em.getCriteriaBuilder();

        // get the count of total results
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<WebContent> from = countQuery.from(WebContent.class);
        CriteriaQuery<Long> countSelect = countQuery.select(cb.count(from));
        countSelect
                .where(
                        cb.and(
                                cb.equal(from.get("contentPackage").get("guid"), packageId),
                                cb.like(from.get("guid"), guidFilter + "%"),
                                cb.like(from.get("fileNode").get("mimeType"), mimeFilter + "%"),
                                cb.like(from.get("fileNode").get("pathTo"), "%" + pathFilter + "%"),
                                cb.like(from.get("fileNode").get("fileName"), "%" + searchText + "%")
                        )
                );
        TypedQuery<Long> typedQuery = em.createQuery(countSelect);
        Long totalResults = typedQuery.getSingleResult();

        // get the paginated results
        CriteriaQuery<WebContent> webContentQuery = cb.createQuery(WebContent.class);
        Root<WebContent> webContentQueryRoot = webContentQuery.from(WebContent.class);
        CriteriaQuery<WebContent> webContentSelect = webContentQuery.select(webContentQueryRoot)
                // all records that belong to content package with packageId
                .where(
                        cb.and(
                                cb.equal(webContentQueryRoot.get("contentPackage").get("guid"), packageId),
                                cb.like(from.get("guid"), guidFilter + "%"),
                                cb.like(webContentQueryRoot.get("fileNode").get("mimeType"), mimeFilter + "%"),
                                cb.like(from.get("fileNode").get("pathTo"), "%" + pathFilter + "%"),
                                cb.like(webContentQueryRoot.get("fileNode").get("fileName"), "%" + searchText + "%")
                        )
                )
                // set sort ordering accordingly
                .orderBy(order.toLowerCase().equals("desc")
                        ? cb.desc(webContentQueryRoot.get("fileNode").get(orderBy))
                        : cb.asc(webContentQueryRoot.get("fileNode").get(orderBy)));
        TypedQuery<WebContent> query = em.createQuery(webContentSelect);
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        List<WebContent> webContents = query.getResultList();

        // return serialized paginated response
        Gson gson = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();

        return gson.toJsonTree(new PaginatedResponse(offset, limit, order, orderBy, totalResults, webContents));
    }

    public JsonElement deleteWebcontent(AppSecurityContext session, String packageId, String webcontentId) {
        securityManager.authorize(session,
                Arrays.asList(ADMIN, CONTENT_DEVELOPER),
                packageId, "name=" + packageId, Arrays.asList(Scopes.EDIT_MATERIAL_ACTION));
        TypedQuery<WebContent> q = em.createNamedQuery("WebContent.findByGuid", WebContent.class);
        q.setParameter("guid", webcontentId);
        WebContent webcontent = null;
        try {
            webcontent = q.getSingleResult();
        } catch (NoResultException e) {
            String message = "Webcontent not found " + webcontentId;
            throw new ResourceException(Response.Status.NOT_FOUND, webcontentId, message);
        }

        ContentPackage contentPackage = webcontent.getContentPackage();

        FileNode fileNode = webcontent.getFileNode();

        contentPackage.removeWebContent(webcontent);
        edgesController.processWebContentDelete(contentPackage, fileNode);

        // Delete old file
        String p = contentPackage.getWebContentVolume() + File.separator + fileNode.getPathTo();
        Path oldPathToWebContentFile = FileSystems.getDefault().getPath(p);

        try {
            Files.deleteIfExists(oldPathToWebContentFile);
        } catch (IOException e) {
            log.debug(String.format("Error deleting webcontent file %s", oldPathToWebContentFile));
        }

        p = contentPackage.getSourceLocation() + File.separator + fileNode.getPathFrom();
        oldPathToWebContentFile = FileSystems.getDefault().getPath(p);

        try {
            // :TODO: Do svn sync
            Files.deleteIfExists(oldPathToWebContentFile);
        } catch (IOException e) {
            log.debug(String.format("Error deleting webcontent file %s", oldPathToWebContentFile));
        }


        JsonObject je = new JsonObject();
        je.addProperty("deleted", "webcontent");
        je.addProperty("guid", webcontent.getGuid());
        je.addProperty("path", webcontent.getFileNode().getPathTo());
        return je;

    }

    public JsonElement uploadWebcontent(AppSecurityContext session, List<InputPart> inParts, String packageId) {
        securityManager.authorize(session,
                Arrays.asList(ADMIN, CONTENT_DEVELOPER),
                packageId, "name=" + packageId, Arrays.asList(Scopes.EDIT_MATERIAL_ACTION));
        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findByGuid", ContentPackage.class);
        q.setParameter("guid", packageId);
        ContentPackage contentPackage = null;
        contentPackage = getContentPackage(packageId, q);

        List<WebContent> webContents = new ArrayList<>();
        try {
            for (InputPart part : inParts) {
                Optional<String> fileNameOption = getFileName(part.getHeaders());
                if (!fileNameOption.isPresent()) {
                    String message = "Error uploading file(s) to course package " +
                            contentPackage.getId() + "_" + contentPackage.getVersion() + " " + part.getHeaders().getFirst("content-disposition");
                    log.error(message);
                    throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, packageId, message);
                }
                String fileName = fileNameOption.get();
                fileName = fileName.replaceAll(" ", "_");
                String substring = fileName.substring(0, fileName.lastIndexOf('.'));
                substring = substring.replaceAll("\\.", "");
                fileName = substring + fileName.substring(fileName.lastIndexOf('.'));

                // Force upload into webcontent directory
                if (!fileName.contains("webcontent")) {
                    fileName = "webcontent" + File.separator + fileName;
                }
                String uploadLocation = contentPackage.getWebContentVolume() + File.separator + fileName;
                Path uploadPath = FileSystems.getDefault().getPath(uploadLocation);
                if (uploadPath.toFile().exists()) {
                    removeWebContent(contentPackage, fileName);
                }
                directoryUtils.createDirectories(uploadLocation);
                InputStream inputStream = part.getBody(InputStream.class, null);
                saveFile(inputStream, uploadPath);

                String uploadToSourceLocation = contentPackage.getSourceLocation() + File.separator + "content" + File.separator + fileName;
                directoryUtils.createDirectories(uploadToSourceLocation);

                // Saves the file to local working copy
                saveFile(Files.newInputStream(uploadPath), FileSystems.getDefault().getPath(uploadToSourceLocation));

                WebContent webContent = new WebContent();
                webContent.setContentPackage(contentPackage);

                long size = 0L;
                size = AppUtils.getSize(uploadPath, size);

                String contentType = "undetermined";
                contentType = AppUtils.getFileType(uploadPath, contentType);

                FileNode resourceNode = new FileNode(contentPackage.getWebContentVolume(), "content" + File.separator + fileName, fileName, contentType);
                resourceNode.setFileSize(size);
                webContent.setFileNode(resourceNode);
                contentPackage.addWebContent(webContent);
                webContents.add(webContent);
            }
        } catch (IOException e) {
            String message = "Error uploading file(s) to course package " + contentPackage.getId() + "_" + contentPackage.getVersion();
            log.error(message);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, packageId, message);
        }
        em.merge(contentPackage);
        em.flush();
        Gson gson = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();

        JsonElement webContentsJson = gson.toJsonTree(webContents, new TypeToken<ArrayList<WebContent>>() {
        }.getType());
        return webContentsJson;

    }

    private void removeWebContent(ContentPackage contentPackage, String fileName) {
        TypedQuery<FileNode> query = em.createQuery("select r from FileNode r where r.volumeLocation = :volumeLocation and r.pathTo = :toPath", FileNode.class);
        query.setParameter("volumeLocation", contentPackage.getWebContentVolume());
        query.setParameter("toPath", fileName);
        List<FileNode> fileNodes = query.getResultList();
        if (fileNodes.isEmpty()) {
            String message = "FileNode not found " + contentPackage.getId() + " : " + fileName.toString();
            log.error(message);
            return;
        }

        fileNodes.forEach(fn -> {
            TypedQuery<WebContent> q2 = em.createNamedQuery("WebContent.findByFileNode", WebContent.class);
            q2.setParameter("fileNode", fn);
            List<WebContent> webContents = q2.getResultList();
            if (webContents.isEmpty()) {
                String message = "deleteWebContent Webcontent not found " + fileName.toString();
                log.error(message);
                return;
            }
            FileNode fileNode = webContents.get(0).getFileNode();

            edgesController.processWebContentDelete(contentPackage, fileNode);
            contentPackage.removeWebContent(webContents.get(0));
        });
    }

    public File getWebcontentFile(AppSecurityContext session, String packageId, String filePath) {
        securityManager.authorize(session,
                Arrays.asList(ADMIN, CONTENT_DEVELOPER),
                packageId, "name=" + packageId, Arrays.asList(Scopes.VIEW_MATERIAL_ACTION));

        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findByGuid", ContentPackage.class);
        q.setParameter("guid", packageId);
        ContentPackage contentPackage = null;
        contentPackage = getContentPackage(packageId, q);

        String uploadPath = contentPackage.getWebContentVolume() + File.separator + filePath;
        Path path = FileSystems.getDefault().getPath(uploadPath);
        if (!path.toFile().exists()) {
            String message = "File does not exist: " + filePath;
            log.info(message);
            throw new ResourceException(Response.Status.BAD_REQUEST, null, message);
        }
        File file = path.toFile();
        return file;
    }

    private ContentPackage getContentPackage(String packageId, TypedQuery<ContentPackage> q) {
        ContentPackage contentPackage;
        try {
            contentPackage = q.getSingleResult();
        } catch (NoResultException e) {
            String message = "Content Package not found " + packageId;
            log.error(message);
            throw new ResourceException(Response.Status.NOT_FOUND, packageId, message);
        }
        return contentPackage;
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
     * @param inputStream
     * @param webcontentLocation
     */
    private void saveFile(InputStream inputStream, Path webcontentLocation) {
        try (OutputStream outpuStream = Files.newOutputStream(webcontentLocation)) {
            int read = 0;
            byte[] bytes = new byte[1024];
            while ((read = inputStream.read(bytes)) != -1) {
                outpuStream.write(bytes, 0, read);
            }
        } catch (IOException e) {
            final String message = "Error saving webcontent file " + webcontentLocation.toString();
            log.error(message + e.getMessage(), e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, "webcontentfile", message);
        }
    }
}
