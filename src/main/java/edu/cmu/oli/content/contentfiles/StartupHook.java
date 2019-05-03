package edu.cmu.oli.content.contentfiles;

import javax.ejb.Singleton;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import org.slf4j.Logger;

import edu.cmu.oli.content.logging.Logging;

@Singleton
@ApplicationScoped
public class StartupHook {

    @Inject
    @Logging
    Logger log;

    @PersistenceContext
    EntityManager em;

    // Reset any datasets that are in the PROCESSING state to the FAILED state.
    private void resetDatasets() {
        final Query q = em.createNamedQuery("Dataset.resetProcessing");
        q.executeUpdate();
        log.info("Reset PROCESSING datasets to FAILED");
    }

    public void init(@Observes @Initialized(ApplicationScoped.class) Object init) {
        this.resetDatasets();
    }

}