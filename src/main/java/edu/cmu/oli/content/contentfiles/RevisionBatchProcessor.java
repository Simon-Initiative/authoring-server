/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018. Carnegie Mellon University
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package edu.cmu.oli.content.contentfiles;

import com.google.gson.JsonParser;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.*;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;

import static edu.cmu.oli.content.boundary.managers.ContentResourceManager.JSON_CAPABLE;

/**
 * @author Raphael Gachuhi
 */
@Stateless
public class RevisionBatchProcessor {

    @Inject
    @Logging
    Logger log;

    @Inject
    @ConfigurationCache
    Instance<Configurations> config;

    @PersistenceContext
    EntityManager em;

    public void batchProcessResource(@NotNull Set<String> resourceGuid) {
        log.info("batchProcessResource");
        if (resourceGuid.isEmpty()) {
            return;
        }
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Resource> criteria = cb.createQuery(Resource.class);
        Root<Resource> resourceRoot = criteria.from(Resource.class);
        criteria.select(resourceRoot).where(resourceRoot.get("guid").in(resourceGuid));

        List<Resource> resources = em.createQuery(criteria).getResultList();
        resources.forEach(resource -> {
            // ignore previously revision transformed resources
            resource.setBuildStatus(BuildStatus.READY);
            if (resource.getLastRevision() != null) {
                resource.getRevisions().forEach(revision -> {
                    if (revision.getGuid().equals(revision.getPreviousRevision())) {
                        revision.setPreviousRevision(null);
                    }
                });
                return;
            }
            boolean jsonCapable = config.get().getResourceTypeById(resource.getType()).get(JSON_CAPABLE).getAsBoolean();
            RevisionBlob revisionBlob = null;
            String fileLocation = resource.getFileNode().getVolumeLocation() + File.separator + resource.getFileNode().getPathTo();
            Path path = Paths.get(fileLocation);
            try {
                String s = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                if (jsonCapable) {
                    revisionBlob = new RevisionBlob(new JsonWrapper(new JsonParser().parse(s)));
                } else {
                    revisionBlob = new RevisionBlob(s);
                }
                Revision revision = new Revision(resource, null, revisionBlob, "Migration");
                revision.setRevisionType(Revision.RevisionType.SYSTEM);
                resource.addRevision(revision);
                resource.setLastRevision(revision);
                log.info("Resource revise " + resource.getId() + " " + revision);
            } catch (IOException e) {
                log.error("Package(id=" + resource.getContentPackage().getId() + " version=" + resource.getContentPackage().getVersion() + ") " + e.getLocalizedMessage(), e);
            }
        });
    }

    public void batchProcessWebContent(@NotNull Set<String> webContentGuids) {
        log.info("batchProcessWebContent");
        if (webContentGuids.isEmpty()) {
            return;
        }
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<WebContent> criteria = cb.createQuery(WebContent.class);
        Root<WebContent> webContentRoot = criteria.from(WebContent.class);
        criteria.select(webContentRoot).where(webContentRoot.get("guid").in(webContentGuids));

        List<WebContent> webContents = em.createQuery(criteria).getResultList();
        webContents.forEach(webContent -> {
            if (webContent.getMd5() == null) {
                FileNode fileNode = webContent.getFileNode();
                String webContentVolume = webContent.getContentPackage().getWebContentVolume();
                fileNode.setVolumeLocation(webContentVolume);
                String filepath = webContentVolume + File.separator + fileNode.getPathTo();
                try (FileInputStream inputStream = new FileInputStream(filepath)) {
                    MessageDigest digest = MessageDigest.getInstance("MD5");

                    byte[] bytesBuffer = new byte[1024];
                    int bytesRead = -1;

                    while ((bytesRead = inputStream.read(bytesBuffer)) != -1) {
                        digest.update(bytesBuffer, 0, bytesRead);
                    }

                    webContent.setMd5(AppUtils.convertByteArrayToHexString(digest.digest()));
                } catch (NoSuchAlgorithmException | IOException ex) {
                    log.error("Package(id=" + webContent.getContentPackage().getId() + " version=" + webContent.getContentPackage().getVersion() + ") " + "Could not generate md5 from file", ex);
                }
            }
        });
    }
}
