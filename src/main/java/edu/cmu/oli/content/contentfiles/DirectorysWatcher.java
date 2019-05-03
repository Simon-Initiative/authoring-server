/*
 * @(#)DirectoryWatcher.java $Date: 2016/00/24
 *
 * Copyright (c) 2016 Carnegie Mellon University.
 */
package edu.cmu.oli.content.contentfiles;

import com.airhacks.porcupine.execution.boundary.Dedicated;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.logging.Logging;
import org.slf4j.Logger;

import javax.ejb.Singleton;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Destroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.naming.InitialContext;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Raphael Gachuhi
 */
@Singleton
@ApplicationScoped
public class DirectorysWatcher {

    @Inject
    @Logging
    Logger log;

    @Inject
    @Dedicated("contentFilesProcessor")
    ExecutorService rscExec;

    @Inject
    @ConfigurationCache
    Instance<Configurations> config;

    Map<PollKey, Path> keys;

    public void init(@Observes @Initialized(ApplicationScoped.class) Object init) {
        try {
            this.keys = new HashMap<>();
            Path dir = FileSystems.getDefault().getPath(config.get().getContentSourceXml());
            log.debug("Directory Watcher init {0}", dir.toString());
            register(dir);
        } catch (Exception ex) {
            log.error("Directory Watcher init failure", ex);
            throw new RuntimeException(ex);
        }
        long sleepTimeInMilli = 1000L * 5; // 5 seconds
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleWithFixedDelay(this::pollDirectories, sleepTimeInMilli, sleepTimeInMilli, TimeUnit.MILLISECONDS);
    }

    private void pollDirectories() {
        Path migrateFlagFile = FileSystems.getDefault().getPath(config.get().getContentSourceXml()).resolve("migrate.do");
        if (Files.exists(migrateFlagFile)) {
            try {
                Files.delete(migrateFlagFile);
                migration();
            } catch (IOException e) {
            }
        }

        Path revisionMigrateFlagFile = FileSystems.getDefault().getPath(config.get().getContentSourceXml()).resolve("revise.do");
        if (Files.exists(revisionMigrateFlagFile)) {
            try {
                Files.delete(revisionMigrateFlagFile);
                revise();
            } catch (IOException e) {
            }
        }

        Set<Map.Entry<PollKey, Path>> entries = keys.entrySet();
        for (Map.Entry<PollKey, Path> entry : entries) {
            Path dir = entry.getValue();
            PollKey key = entry.getKey();
            if (Files.exists(dir)) {
                File[] directFolders = dir.toFile().listFiles(pathname -> pathname.isDirectory());
                if (directFolders == null) {
                    continue;
                }
                for (File directory : directFolders) {
                    Path commandFlag = directory.toPath().resolve("deploy.do");
                    if (Files.exists(commandFlag)) {
                        try {
                            Files.delete(commandFlag);
                            key.addPath(new Directive("deploy", directory.toPath()));
                        } catch (IOException e) {
                        }
                    }
                    commandFlag = directory.toPath().resolve("redeploy.do");
                    if (Files.exists(commandFlag)) {
                        try {
                            Files.delete(commandFlag);
                            key.addPath(new Directive("redeploy", directory.toPath()));
                        } catch (IOException e) {
                        }
                    }
                    commandFlag = directory.toPath().resolve("ldimport.do");
                    if (Files.exists(commandFlag)) {
                        try {
                            Files.delete(commandFlag);
                            key.addPath(new Directive("ldimport", directory.toPath()));
                        } catch (IOException e) {
                        }
                    }
                }
            }
        }
        processEvents();
    }

    private void migration() {
        try {
            MigrationProcessor migrationProcessor = (MigrationProcessor)
                    new InitialContext().lookup("java:global/content-service/MigrationProcessor");
            migrationProcessor.migrate();
        } catch (Throwable e) {
            String message = "Error while migrating all content package ";
            log.error(message, e);
        }
    }

    private void revise() {
        try {
            RevisionMigrationProcessor revisionMigrationProcessor = (RevisionMigrationProcessor)
                    new InitialContext().lookup("java:global/content-service/RevisionMigrationProcessor");
            revisionMigrationProcessor.revise();
        } catch (Throwable e) {
            String message = "Error while migrating revisions in all content packages ";
            log.error(message, e);
        }
    }

    public void destroy(@Observes @Destroyed(ApplicationScoped.class) Object destroy) {
        keys.clear();
    }

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) {
        PollKey pollKey = new PollKey(dir);
        keys.put(pollKey, dir);
    }

    private void processEvents() {
        if (keys.isEmpty()) {
            return;
        }
        Set<Map.Entry<PollKey, Path>> entries = keys.entrySet();
        entries.forEach(e -> {
            PollKey key = e.getKey();
            key.pathStream().forEach(p -> {
                Path pkgXml = p.path.resolve("content/package.xml");
                if (Files.exists(pkgXml)) {
                    // Assume presence of package.xml file indicates this is a valid course content folder
                    this.rscExec.submit(() -> processPackage(p.path, pkgXml, p.instruction));
                }
            });
            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);
            }
        });
    }

    private void processPackage(Path packageFolder, Path pkgXml, String instruction) {

        try {
            PackageProcessor packageProcessor = (PackageProcessor)
                    new InitialContext().lookup("java:global/content-service/PackageProcessor");
            if (instruction.equals("ldimport")) {
                packageProcessor.processLDModelImport(packageFolder, pkgXml);
                return;
            }
            packageProcessor.processPackage(packageFolder, pkgXml, instruction);
        } catch (Throwable e) {
            String message = "Error while processing course content package import " + packageFolder.toString();
            log.error(message, e);
        }
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
