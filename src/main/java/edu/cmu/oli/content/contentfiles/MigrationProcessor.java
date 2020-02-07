package edu.cmu.oli.content.contentfiles;

import edu.cmu.oli.content.configuration.DedicatedExecutor;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.naming.InitialContext;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Raphael Gachuhi
 */
@Stateless
public class MigrationProcessor {

    @Inject
    @Logging
    Logger log;

    @Inject
    @DedicatedExecutor("migrationExec")
    ExecutorService rscExec;

    static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Inject
    @ConfigurationCache
    Instance<Configurations> config;

    @PersistenceContext
    EntityManager em;

    public void migrate() throws IOException {
        String sourceLocation = this.config.get().getContentSourceXml();
        Path migrationTracker = Paths.get(sourceLocation + File.separator + "migration_tracker.json");
        JsonObject migrationRecord = null;
        if (Files.exists(migrationTracker)) {
            String fileString = new String(Files.readAllBytes(migrationTracker), StandardCharsets.UTF_8);
            migrationRecord = new JsonParser().parse(fileString).getAsJsonObject();
        } else {
            migrationRecord = new JsonObject();
            outputTrackerFile(migrationTracker, migrationRecord);
        }

        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findAll", ContentPackage.class);
        List<ContentPackage> contentPackages = q.getResultList();
        log.debug("Migrating " + contentPackages.size() + " ContentPackages");
        // This processing will take long; no need for entities to remain attached to
        // session, so detach all
        em.clear();
        for (ContentPackage contentPackage : contentPackages) {
            try {
                final String pkgIdentifier = contentPackage.getId() + "_" + contentPackage.getVersion();

                if (contentPackage.getSourceLocation() == null) {
                    trackMigration(migrationTracker, migrationRecord, contentPackage, pkgIdentifier,
                            "missing source location");
                    continue;
                }
                Path p = Paths.get(contentPackage.getSourceLocation());
                Path pkgXml = Paths.get(contentPackage.getSourceLocation() + File.separator + "content" + File.separator
                        + "package.xml");
                log.debug(pkgIdentifier + " already migrated " + migrationRecord.has(pkgIdentifier));

                if (Files.exists(pkgXml) && !migrationRecord.has(pkgIdentifier)) {
                    log.debug("migration started for " + pkgIdentifier);

                    trackMigration(migrationTracker, migrationRecord, contentPackage, pkgIdentifier, "processing");

                    final boolean success = processPackage(p, pkgXml, pkgIdentifier);
                    trackMigration(migrationTracker, migrationRecord, contentPackage, pkgIdentifier,
                            success ? "success" : "failed");
                }

            } catch (Throwable t) {
                final String pkgIdentifier = contentPackage.getId() + "_" + contentPackage.getVersion();
                final String message = "Error while processing course content package import " + pkgIdentifier;
                log.error(message, t);

                trackMigration(migrationTracker, migrationRecord, contentPackage, pkgIdentifier, "failed");
            }

        }

        this.rscExec.submit(this::webContentFolderCleanup);
    }

    private static void outputTrackerFile(Path migrationTracker, JsonObject migrationRecord) throws IOException {
        Files.write(migrationTracker, AppUtils.gsonBuilder().create().toJson(migrationRecord).getBytes());
    }

    private void trackMigration(Path migrationTracker, JsonObject migrationRecord, ContentPackage contentPackage,
            String pkgIdentifier, String status) throws IOException {
        JsonObject pkgInfo = new JsonObject();
        pkgInfo.addProperty("id", contentPackage.getId());
        pkgInfo.addProperty("version", contentPackage.getVersion());
        pkgInfo.addProperty("location", contentPackage.getSourceLocation());
        pkgInfo.addProperty("status", status);
        migrationRecord.add(pkgIdentifier, pkgInfo);

        outputTrackerFile(migrationTracker, migrationRecord);
    }

    private boolean processPackage(Path packageFolder, Path pkgXml, String pkgIdentifier) {
        try {
            PackageProcessor packageProcessor = (PackageProcessor) new InitialContext()
                    .lookup("java:global/content-service/PackageProcessor");
            packageProcessor.processPackage(packageFolder, pkgXml, "migrate");
            return true;
        } catch (Throwable t) {
            String message = "Error while processing course content package import " + packageFolder.toString();
            log.error(message, t);
            return false;
        }
    }

    private void webContentFolderCleanup() {

        scheduler.schedule(() -> {
            WebContentFolderCleanup webContentFolderCleanup = null;
            try {
                webContentFolderCleanup = (WebContentFolderCleanup) new InitialContext()
                        .lookup("java:global/content-service/WebContentFolderCleanup");
                webContentFolderCleanup.cleanupWebContentVolume();
            } catch (Throwable e) {
                String message = "Error calling cleanupWebContentVolume";
                log.error(message, e);
            }

        }, 2, TimeUnit.SECONDS);
    }
}
