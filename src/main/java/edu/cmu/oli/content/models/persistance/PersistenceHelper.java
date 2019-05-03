package edu.cmu.oli.content.models.persistance;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * @author Raphael Gachuhi
 */
@ApplicationScoped
public class PersistenceHelper {

    @PersistenceContext
    private EntityManager em;

    @Produces
    public EntityManager getEntityManager() {
        return em;
    }
}
