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

import edu.cmu.oli.content.configuration.DedicatedExecutor;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import edu.cmu.oli.content.models.persistance.entities.Resource;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.naming.InitialContext;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.stream.IntStream;

/**
 * @author Raphael Gachuhi
 */
@Stateless
public class RevisionMigrationProcessor {

    @Inject
    @Logging
    Logger log;

    @Inject
    @DedicatedExecutor("revMigrationExec")
    ExecutorService rscExec;

    @Inject
    @ConfigurationCache
    Instance<Configurations> config;

    @PersistenceContext
    EntityManager em;

    private static int BATCH_SIZE = 10;

    class TypeValue {
        ValueType type;
        String id;

        public TypeValue(ValueType type, String id) {
            this.type = type;
            this.id = id;
        }

        @Override
        public String toString() {
            return "TypeValue{" +
                    "type=" + type +
                    ", id='" + id + '\'' +
                    '}';
        }
    }

    enum ValueType {
        RESOURCE,
        WEBRESOURCE,
    }

    public void revise() {
        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findAll", ContentPackage.class);
        List<ContentPackage> contentPackages = q.getResultList();

        Queue<TypeValue> migrationQueue = new ConcurrentLinkedQueue<>();

        contentPackages.forEach(contentPackage -> {
            if (contentPackage.getDeploymentStatus() == null) {
                contentPackage.setDeploymentStatus(ContentPackage.DeploymentStatus.DEVELOPMENT);
            }
            if (contentPackage.getPackageFamily() == null) {
                contentPackage.setPackageFamily(contentPackage.getId());
            }

            List<Resource> resources = contentPackage.getResources();
            resources.forEach(resource -> {
                migrationQueue.add(new TypeValue(ValueType.RESOURCE, resource.getGuid()));
            });
            contentPackage.getWebContents().forEach(webContent -> {
                if (webContent.getMd5() == null)
                    migrationQueue.add(new TypeValue(ValueType.RESOURCE, webContent.getGuid()));
            });
        });
        em.flush();
        em.clear();
        while (!migrationQueue.isEmpty()) {
            Set<String> resourcesToProcess = new HashSet<>();
            Set<String> webcontentsToProcess = new HashSet<>();
            IntStream.range(0, BATCH_SIZE).forEach(i -> {
                if (!migrationQueue.isEmpty()) {
                    TypeValue poll = migrationQueue.poll();
                    log.info(poll.toString());
                    switch (poll.type) {
                        case RESOURCE:
                            resourcesToProcess.add(poll.id);
                        case WEBRESOURCE:
                            webcontentsToProcess.add(poll.id);
                    }
                }
            });

            rscExec.submit(() -> {
                try {
                    RevisionBatchProcessor revisionMigrationProcessor = (RevisionBatchProcessor)
                            new InitialContext().lookup("java:global/content-service/RevisionBatchProcessor");
                    revisionMigrationProcessor.batchProcessResource(resourcesToProcess);
                    revisionMigrationProcessor.batchProcessWebContent(webcontentsToProcess);
                } catch (Throwable e) {
                    String message = "Error while migrating revisions in all content packages ";
                    log.error(message, e);
                }
            });
        }
        log.info("Revision Migration");
    }
}
