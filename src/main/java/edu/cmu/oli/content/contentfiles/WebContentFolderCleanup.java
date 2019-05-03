package edu.cmu.oli.content.contentfiles;

import edu.cmu.oli.content.DirectoryUtils;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

/**
 * @author Raphael Gachuhi
 */
@Stateless
public class WebContentFolderCleanup {

    @Inject
    @Logging
    Logger log;

    @PersistenceContext
    EntityManager em;

    @Inject
    DirectoryUtils directoryUtils;

    @Inject
    @ConfigurationCache
    Instance<Configurations> config;

    public void cleanupWebContentVolume() {
        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findAll", ContentPackage.class);
        List<ContentPackage> resultList = q.getResultList();

        // Cleanup webcontent folder
        Path path1 = FileSystems.getDefault().getPath(config.get().getWebContentVolume());
        File[] directs = path1.toFile().listFiles(pathname -> pathname.isDirectory());
        if (directs != null) {
            for (File pi : directs) {
                boolean exists = false;
                for (ContentPackage pk : resultList) {
                    if (pk.getGuid().equalsIgnoreCase(pi.getName())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    directoryUtils.deleteDirectory(pi.toPath());
                }
            }
        }
    }

}
