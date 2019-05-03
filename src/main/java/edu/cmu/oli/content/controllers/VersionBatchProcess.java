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

package edu.cmu.oli.content.controllers;

import edu.cmu.oli.content.DirectoryUtils;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.entities.*;
import edu.cmu.oli.content.security.AppSecurityController;
import edu.cmu.oli.content.security.Secure;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author Raphael Gachuhi
 */
@Stateless
public class VersionBatchProcess {

    @Inject
    @Logging
    Logger log;

    @Inject
    DirectoryUtils directoryUtils;

    @Inject
    @Secure
    AppSecurityController securityController;

    @Inject
    @ConfigurationCache
    Instance<Configurations> config;

    @PersistenceContext
    EntityManager em;

    public void versionProcess(String pkgGuid, Map<String, String> revsMap, Set<String> jobIds, String jobId) {
        log.info("versionProcess");
        try {
            if (revsMap.isEmpty()) {
                return;
            }

            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Resource> criteria = cb.createQuery(Resource.class);
            Root<Resource> resourceRoot = criteria.from(Resource.class);
            criteria.select(resourceRoot).where(resourceRoot.get("guid").in(revsMap.values()));
            List<Resource> resourceList = em.createQuery(criteria).getResultList();

            cb = em.getCriteriaBuilder();
            CriteriaQuery<Revision> rcriteria = cb.createQuery(Revision.class);
            Root<Revision> revisionRoot = rcriteria.from(Revision.class);
            rcriteria.select(revisionRoot).where(revisionRoot.get("guid").in(revsMap.keySet()));

            List<Revision> revisions = em.createQuery(rcriteria).getResultList();
            revisions.forEach(rev -> {
                RevisionBlob revisionBlob = rev.getBody().cloneVersion();

                String resourceGuid = revsMap.get(rev.getGuid());
                Optional<Resource> first = resourceList.stream().filter(r -> r.getGuid().equals(resourceGuid)).findAny();
                Resource resource = first.isPresent() ? first.get() : em.find(Resource.class, resourceGuid);
                Revision revision = new Revision(resource, rev, revisionBlob, "Versioning");
                revision.setRevisionType(Revision.RevisionType.SYSTEM);
                resource.addRevision(revision);
                resource.setLastRevision(revision);
                resource.setBuildStatus(BuildStatus.READY);
                em.persist(revision);
                log.debug("Resource revise " + resource.getId() + " " + revision);
            });
        } finally {
            jobIds.remove(jobId);
            if (jobIds.isEmpty()) {
                ContentPackage contentPackage = em.find(ContentPackage.class, pkgGuid);
                contentPackage.setBuildStatus(BuildStatus.READY);
                log.info("Done processing version and clone tasks");
            }
        }
    }

    public void removeExistingPackage(String pkgGuid) {
        ContentPackage existingPkg = em.find(ContentPackage.class, pkgGuid);
        if (existingPkg != null) {
            log.debug("Removing existing package: id " + existingPkg.getId() + " version " + existingPkg.getVersion());
            try {
                em.remove(existingPkg);
                em.flush();
            } catch (Throwable t) {
            }

            // Delete resource from Keycloak
            try {
                securityController.deleteResource(existingPkg.getGuid());
            } catch (Exception e) {
                log.error(e.getLocalizedMessage(), e);
            }

            Path path = FileSystems.getDefault().getPath(existingPkg.getWebContentVolume());
            directoryUtils.deleteDirectory(path);

            path = FileSystems.getDefault().getPath(existingPkg.getVolumeLocation());
            directoryUtils.deleteDirectory(path);

            path = FileSystems.getDefault().getPath(existingPkg.getSourceLocation());
            directoryUtils.deleteDirectory(path);
        }
    }


}
