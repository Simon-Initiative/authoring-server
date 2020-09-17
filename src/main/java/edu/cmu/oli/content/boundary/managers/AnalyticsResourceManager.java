package edu.cmu.oli.content.boundary.managers;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.ResourceException;
import edu.cmu.oli.content.analytics.DatasetBuilder;
import edu.cmu.oli.content.configuration.DedicatedExecutor;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
    @DedicatedExecutor("analyticsresourceApiExecutor")
    ExecutorService es;

    @Inject
    @Secure
    AppSecurityController securityManager;

    public JsonElement getPackageDatasets(AppSecurityContext session, String packageGuid) {
        ContentPackage contentPackage = findContentPackage(packageGuid);
        authorizePackagePrivileges(session, contentPackage.getGuid());

        Gson gson = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();
        return gson.toJsonTree(findDatasets(contentPackage.getGuid()), new TypeToken<ArrayList<ContentPackage>>() {
        }.getType());
    }

    public JsonElement getDataset(AppSecurityContext session, String datasetGuid) {
        authorizeBasicPrivileges(session);
        Dataset dataset = findDataset(datasetGuid);
        authorizePackagePrivileges(session, dataset.getContentPackage().getGuid());

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
        ContentPackage contentPackage = findContentPackage(packageGuid);
        authorizePackagePrivileges(session, contentPackage.getGuid());

        // Return if already currently processing a dataset request for this packageGuid
        List<Dataset> processingDatasets = getProcessingDatasets(contentPackage.getGuid());
        if (!processingDatasets.isEmpty()) {
            return setDatasetInfo(processingDatasets.get(0), "Already processing dataset");
        }

        Dataset dataset = new Dataset();
        try {
            dataset.setContentPackage(contentPackage);
            contentPackage.setActiveDataset(dataset);
            em.flush();
            es.submit(() -> datasetBuilder.build(dataset.getGuid()));
        } catch (Throwable t) {
            log.info("Dataset creation failure: " + t.getMessage());
            dataset.setDatasetStatus(DatasetStatus.FAILED);
            dataset.setMessage(t.getMessage());
            dataset.setDateCompleted(new Date());
        }

        // Immediately return the guid of the dataset being processed
        return setDatasetInfo(dataset, "Creating new dataset");
    }

    public ByteArrayOutputStream exportDataset(AppSecurityContext session, String datasetGuid) {
        authorizeBasicPrivileges(session);
        Dataset dataset = findDataset(datasetGuid);
        authorizePackagePrivileges(session, dataset.getContentPackage().getGuid());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, String> entry : datasetJsonToTsv(dataset).entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey() + ".tsv");
                zos.putNextEntry(zipEntry);
                zos.write(entry.getValue().getBytes());
                zos.closeEntry();
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, datasetGuid, "Unable to export dataset");
        }

        return baos;
    }

    private JsonObject setDatasetInfo(Dataset dataset, String message) {
        JsonObject datasetInfo = new JsonObject();
        datasetInfo.addProperty("guid", dataset.getGuid());
        datasetInfo.addProperty("message", message);
        return datasetInfo;
    }

    private List<Dataset> getProcessingDatasets(String packageGuid) {
        TypedQuery<Dataset> q = em.createNamedQuery("Dataset.findProcessingByPackageGuid", Dataset.class);
        q.setParameter("packageGuid", packageGuid);
        return (List<Dataset>) q.getResultList();
    }

    // Three slices - byPart, bySkill, and byResource
    private Map<String, String> datasetJsonToTsv(Dataset dataset) {
        Map<String, String> output = new HashMap<>();
        JsonObject json = dataset.getDatasetBlob().getBodyJson().getAsJsonObject();
        output.put("byResource", byResourceToTsv(json.get("byResource").getAsJsonArray()));
        output.put("byPart", byPartToTsv(json.get("byPart").getAsJsonArray()));
        output.put("bySkill", bySkillToTsv(json.get("bySkill").getAsJsonArray()));
        return output;
    }

    private String byResourceToTsv(JsonArray dataRows) {
        List<String> titleHeaders = Arrays.asList("Resource", "Title");
        Set<String> titleKeys = new LinkedHashSet<String>();
        titleKeys.addAll(Arrays.asList("resource", "title"));

        return datasetToTsv(titleHeaders, titleKeys, dataRows);
    }

    private String byPartToTsv(JsonArray dataRows) {
        List<String> headers = Arrays.asList("ID", "Resource", "Title", "Revision", "Part", "Submit and Compare");
        Set<String> keys = new LinkedHashSet<String>();
        keys.addAll(Arrays.asList("id", "resourceId", "resourceTitle", "revision", "part", "submitAndCompare"));

        return datasetToTsv(headers, keys, dataRows);
    }

    private String bySkillToTsv(JsonArray dataRows) {
        List<String> headers = Arrays.asList("Skill", "Title");
        Set<String> keys = new LinkedHashSet<String>();
        keys.addAll(Arrays.asList("skill", "title"));

        return datasetToTsv(headers, keys, dataRows);
    }

    private String datasetToTsv(List<String> headers, Set<String> keys, JsonArray dataRows) {
        List<String> commonHeaders = Arrays.asList("Distinct Students", "Distinct Registrations", "Opportunities",
                "Practice", "Hints", "Errors", "Eventually Correct", "First Response Correct", "Utilization - Start",
                "Utilization - Finish", "Average Help Needed", "Average Number of Tries", "Completion Rate",
                "Accuracy Rate");
        List<String> allHeaders = new ArrayList<>();
        allHeaders.addAll(headers);
        allHeaders.addAll(commonHeaders);

        List<String> commonKeys = Arrays.asList("distinctStudents", "distinctRegistrations", "opportunities",
                "practice", "hints", "errors", "correct", "firstResponseCorrect", "utilizationStart",
                "utilizationFinish", "avgHelpNeeded", "avgNumberOfTries", "completionRate", "accuracyRate");
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(keys);
        allKeys.addAll(commonKeys);

        StringBuilder tsvDataset = new StringBuilder(tsvTitleRow(allHeaders));
        dataRows.forEach(row -> tsvDataRow(tsvDataset, (JsonObject) row, allKeys));

        return tsvDataset.toString();
    }

    private String tsvTitleRow(List<String> titles) {
        return IntStream.range(0, titles.size()).mapToObj(i -> titles.get(i)).collect(Collectors.joining("\t")) + "\n";
    }

    private StringBuilder tsvDataRow(StringBuilder sb, JsonObject row, Set<String> keys) {
        Iterator<String> iterator = keys.iterator();
        while (iterator.hasNext()) {
            sb.append(row.get(iterator.next())).append("\t");
        }

        return sb.append("\n");
    }

    private Dataset findDataset(String datasetGuid) {
        TypedQuery<Dataset> q = em.createNamedQuery("Dataset.findByGuid", Dataset.class);
        q.setParameter("guid", datasetGuid);
        List<Dataset> resultList = q.getResultList();
        if (resultList.isEmpty()) {
            final String message = "exportDataset error: can't find dataset with guid " + datasetGuid;
            throw new ResourceException(Response.Status.NOT_FOUND, datasetGuid, message);
        }
        return resultList.get(0);
    }

    private List<Dataset> findDatasets(String packageGuid) {
        TypedQuery<Dataset> q = em.createNamedQuery("Dataset.findByPackageGuid", Dataset.class);
        q.setParameter("packageGuid", packageGuid);
        List<Dataset> resultList = q.getResultList();
        if (resultList.isEmpty()) {
            final String message = "Get dataset error: can't find datasets for package guid " + packageGuid;
            throw new ResourceException(Response.Status.NOT_FOUND, packageGuid, message);
        }
        return resultList;
    }

    // packageIdentifier is db guid or packageId-version combo
    private ContentPackage findContentPackage(String packageIdOrGuid) {
        ContentPackage contentPackage = null;
        Boolean isIdAndVersion = packageIdOrGuid.contains("-");
        try {
            if (isIdAndVersion) {
                String pkgId = packageIdOrGuid.substring(0, packageIdOrGuid.lastIndexOf("-"));
                String version = packageIdOrGuid.substring(packageIdOrGuid.lastIndexOf("-") + 1);
                TypedQuery<ContentPackage> q = em
                        .createNamedQuery("ContentPackage.findByIdAndVersion", ContentPackage.class)
                        .setParameter("id", pkgId).setParameter("version", version);

                contentPackage = q.getResultList().isEmpty() ? null : q.getResultList().get(0);
            } else {
                String packageGuid = packageIdOrGuid;
                contentPackage = em.find(ContentPackage.class, packageGuid);
            }

            if (contentPackage == null) {
                String message = "Error: package requested was not found " + packageIdOrGuid;
                log.error(message);
                throw new ResourceException(Response.Status.NOT_FOUND, packageIdOrGuid, message);
            }

        } catch (IllegalArgumentException e) {
            String message = "Server Error while locating package " + packageIdOrGuid;
            log.error(message);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, packageIdOrGuid, message);
        }
        return contentPackage;
    }

    // Verify a user has access to the package
    private void authorizePackagePrivileges(AppSecurityContext session, String packageGuid) {
        securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), packageGuid, "name=" + packageGuid,
                Arrays.asList(Scopes.VIEW_MATERIAL_ACTION));
    }

    // Verify basic authentication before executing any code
    private void authorizeBasicPrivileges(AppSecurityContext session) {
        securityManager.authorize(session, Arrays.asList(ADMIN, CONTENT_DEVELOPER), null, null, null);
    }
}
