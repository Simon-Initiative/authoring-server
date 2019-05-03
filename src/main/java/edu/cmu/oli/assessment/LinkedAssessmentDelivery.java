package edu.cmu.oli.assessment;

import com.google.gson.JsonElement;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.controllers.Delivery;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.entities.Resource;
import org.jdom2.Document;
import org.slf4j.Logger;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * @author Raphael Gachuhi
 */
public class LinkedAssessmentDelivery implements Delivery {

    @Inject
    @Logging
    Logger log;

    @PersistenceContext
    EntityManager em;

    @Inject
    @ConfigurationCache
    Instance<Configurations> config;

    @Override
    public JsonElement deliver(Resource resource, Document document, String serverUrl, String themeId, JsonElement metaInfo) {

        return null;
    }

}
