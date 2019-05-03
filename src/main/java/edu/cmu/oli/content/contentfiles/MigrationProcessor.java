package edu.cmu.oli.content.contentfiles;

import com.airhacks.porcupine.execution.boundary.Dedicated;
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
    @Dedicated("migrationExec")
    ExecutorService rscExec;

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
            Files.write(migrationTracker, AppUtils.gsonBuilder().create().toJson(migrationRecord).getBytes());
        }

        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findAll", ContentPackage.class);
        List<ContentPackage> contentPackages = q.getResultList();
        log.debug("Migrating " + contentPackages.size() + " ContentPackages");
        // This processing will take long; no need for entities to remain attached to session, so detach all
        em.clear();
        for (ContentPackage contentPackage : contentPackages) {
            String pkgIdentifier = contentPackage.getId() + "_" + contentPackage.getVersion();
            if (contentPackage.getSourceLocation() == null) {
                trackMigration(migrationTracker, migrationRecord, contentPackage, pkgIdentifier, "missing source location");
                continue;
            }
            Path p = Paths.get(contentPackage.getSourceLocation());
            Path pkgXml = Paths.get(contentPackage.getSourceLocation() + File.separator + "content" + File.separator + "package.xml");
            log.debug(pkgIdentifier + " already migrated " + migrationRecord.has(pkgIdentifier));

            if (Files.exists(pkgXml) && !migrationRecord.has(pkgIdentifier)) {
                log.debug("migration started for " + pkgIdentifier);
                trackMigration(migrationTracker, migrationRecord, contentPackage, pkgIdentifier, "processing");
                this.rscExec.submit(() -> processPackage(p, pkgXml, pkgIdentifier));
            }
        }

        this.rscExec.submit(this::webContentFolderCleanup);
    }

    private void trackMigration(Path migrationTracker, JsonObject migrationRecord, ContentPackage contentPackage,
                                String pkgIdentifier, String status) throws IOException {
        JsonObject pkgInfo = new JsonObject();
        pkgInfo.addProperty("id", contentPackage.getId());
        pkgInfo.addProperty("version", contentPackage.getVersion());
        pkgInfo.addProperty("location", contentPackage.getSourceLocation());
        pkgInfo.addProperty("status", status);
        migrationRecord.add(pkgIdentifier, pkgInfo);
        Files.write(migrationTracker, AppUtils.gsonBuilder().create().toJson(migrationRecord).getBytes());
    }

    private void processPackage(Path packageFolder, Path pkgXml, String pkgIdentifier) {
        String status = "failed";
        try {
            PackageProcessor packageProcessor = (PackageProcessor)
                    new InitialContext().lookup("java:global/content-service/PackageProcessor");
            packageProcessor.processPackage(packageFolder, pkgXml, "migrate");
            status = "success";
        } catch (Throwable e) {
            String message = "Error while processing course content package import " + packageFolder.toString();
            log.error(message, e);
        } finally {
            String sourceLocation = this.config.get().getContentSourceXml();
            Path migrationTracker = Paths.get(sourceLocation + File.separator + "migration_tracker.json");
            if (Files.exists(migrationTracker)) {
                String fileString = null;
                try {
                    fileString = new String(Files.readAllBytes(migrationTracker), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    log.error("Error reading to migration_tracker file", e);
                    return;
                }
                JsonObject migrationRecord = new JsonParser().parse(fileString).getAsJsonObject();
                JsonObject pkgInfo = (JsonObject) migrationRecord.get(pkgIdentifier);
                pkgInfo.addProperty("status", status);
                try {
                    Files.write(migrationTracker, AppUtils.gsonBuilder().create().toJson(migrationRecord).getBytes());
                } catch (IOException e) {
                    log.error("Error writing to migration_tracker file", e);
                    return;
                }

            }
        }
    }

    private void webContentFolderCleanup() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> {
            WebContentFolderCleanup webContentFolderCleanup = null;
            try {
                webContentFolderCleanup = (WebContentFolderCleanup)
                        new InitialContext().lookup("java:global/content-service/WebContentFolderCleanup");
                webContentFolderCleanup.cleanupWebContentVolume();
            } catch (Throwable e) {
                String message = "Error calling cleanupWebContentVolume";
                log.error(message, e);
            }

        }, 2, TimeUnit.SECONDS);
    }
}
