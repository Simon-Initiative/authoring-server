package edu.cmu.oli.content.boundary.managers;

import com.airhacks.porcupine.execution.boundary.Dedicated;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.ResourceException;
import edu.cmu.oli.content.analytics.DatasetBuilder;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.entities.*;
import edu.cmu.oli.content.security.*;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.ws.rs.core.Response;

import java.util.*;
import java.util.concurrent.ExecutorService;

import static edu.cmu.oli.content.security.Roles.ADMIN;
import static edu.cmu.oli.content.security.Roles.CONTENT_DEVELOPER;

/**
 * @author Raphael Gachuhi
 */
@Stateless
public class AnalyticsResourceManager {

    @Inject
    @Logging
    Logger log;

    @PersistenceContext
    EntityManager em;

    @Inject
    DatasetBuilder datasetBuilder;

    @Inject
    @Dedicated("analyticsresourceApiExecutor")
    ExecutorService es;

    @Inject
    @Secure
    AppSecurityController securityManager;

    public JsonElement getPackageDatasets(AppSecurityContext session, String packageGuid) {
        securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), packageGuid, "name=" + packageGuid,
                Arrays.asList(Scopes.VIEW_MATERIAL_ACTION));

        TypedQuery<Dataset> q = em.createNamedQuery("Dataset.findByPackageGuid", Dataset.class);
        q.setParameter("packageGuid", packageGuid);
        List<Dataset> resultList = q.getResultList();

        if (resultList.isEmpty()) {
            final String message = "Get dataset error: can't find datasets for package guid " + packageGuid;
            throw new ResourceException(Response.Status.NOT_FOUND, packageGuid, message);
        }

        Gson gson = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();
        return gson.toJsonTree(resultList, new TypeToken<ArrayList<ContentPackage>>() {
        }.getType());
    }

    public JsonElement getDataset(AppSecurityContext session, String datasetGuid) {
        // Verify basic authentication before executing any code
        securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), null, null, null);

        TypedQuery<Dataset> q = em.createNamedQuery("Dataset.findByGuid", Dataset.class);
        q.setParameter("guid", datasetGuid);
        List<Dataset> resultList = q.getResultList();
        if (resultList.isEmpty()) {
            final String message = "getDataset error: can't find dataset with guid " + datasetGuid;
            throw new ResourceException(Response.Status.NOT_FOUND, datasetGuid, message);
        }
        Dataset dataset = q.getResultList().get(0);

        ContentPackage contentPackage = dataset.getContentPackage();

        // Authorize users with access to the package the dataset is tied to
        securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), contentPackage.getGuid(),
                "name=" + contentPackage.getGuid(), Arrays.asList(Scopes.VIEW_MATERIAL_ACTION));

        Gson gson = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();
        JsonElement result = gson.toJsonTree(dataset, new TypeToken<Dataset>() {
        }.getType());

        DatasetBlob datasetBlob = dataset.getDatasetBlob();
        if (datasetBlob != null) {
            result.getAsJsonObject().add("datasetBlob", gson.toJsonTree(datasetBlob.getBodyJson()));
        }

        return result;
    }

    public JsonElement createDataset(AppSecurityContext session, final String packageGuid) {
        securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), packageGuid, "name=" + packageGuid,
                Arrays.asList(Scopes.VIEW_MATERIAL_ACTION));

        JsonObject datasetInfo = new JsonObject();

        // Return if already currently processing a dataset request for this packageGuid
        TypedQuery<Dataset> q = em.createNamedQuery("Dataset.findProcessingByPackageGuid", Dataset.class);
        q.setParameter("packageGuid", packageGuid);
        List<Dataset> processingDatasets = q.getResultList();
        if (!processingDatasets.isEmpty()) {
            String guid = processingDatasets.get(0).getGuid();
            datasetInfo.addProperty("guid", guid);
            datasetInfo.addProperty("message", "Already processing dataset");
            return datasetInfo;
        }

        Dataset dataset = new Dataset();
        ContentPackage contentPackage = findContentPackage(packageGuid);
        dataset.setContentPackage(contentPackage);
        contentPackage.setActiveDataset(dataset);
        em.flush();

        es.submit(() -> datasetBuilder.build(dataset.getGuid()));

        // Immediately return the guid of the dataset being processed
        datasetInfo.addProperty("guid", dataset.getGuid());
        datasetInfo.addProperty("message", "Creating new dataset");
        return datasetInfo;
    }

    private ContentPackage findContentPackage(String packageGuid) {
        ContentPackage contentPackage = null;
        try {
            contentPackage = em.find(ContentPackage.class, packageGuid);
            if (contentPackage == null) {
                String message = "Error: package requested was not found " + packageGuid;
                log.error(message);
                throw new ResourceException(Response.Status.NOT_FOUND, packageGuid, message);
            }
        } catch (IllegalArgumentException e) {
            String message = "Server Error while locating package " + packageGuid;
            log.error(message);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, packageGuid, message);
        }
        return contentPackage;
    }
}
