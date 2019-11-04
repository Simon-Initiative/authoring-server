/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2019. Carnegie Mellon University
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

package edu.cmu.oli.content.analytics;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import edu.cmu.oli.content.models.persistance.entities.Dataset;
import edu.cmu.oli.content.models.persistance.entities.DatasetBlob;
import edu.cmu.oli.content.models.persistance.entities.DatasetStatus;
import edu.cmu.oli.content.reportingdb.DbConnector;

import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import edu.cmu.oli.content.ResourceException;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.Date;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Stateless
public class DatasetBuilder {

    @Inject
    @Logging
    Logger log;

    @PersistenceContext
    EntityManager em;

    @Inject
    DbConnector db;

    private static Integer MAXIMUM_STUDENTS = 5000;
    private static Integer MINIMUM_STUDENTS = 100;
    private static String SQL_QUERIES_FILE = "dataset/sql_queries.json";

    // Sql scripts read in from local files
    private static Map<String, String> datasetCreationQueries = new HashMap<>();
    private static String findPackagesQuery;
    private static String findSectionsQuery;

    static {
        InputStream resourceAsStream = DatasetBuilder.class.getClassLoader().getResourceAsStream(SQL_QUERIES_FILE);
        JsonObject queries = (new Gson()).fromJson(new InputStreamReader(resourceAsStream), JsonObject.class);

        findPackagesQuery = readScript(queries.get("findPackages").getAsString());
        findSectionsQuery = readScript(queries.get("findSections").getAsString());
        for (Map.Entry<String, JsonElement> query : queries.get("createDataset").getAsJsonObject().entrySet()) {
            datasetCreationQueries.put(query.getKey(), readScript(query.getValue().getAsString()));
        }
    }

    /**
     * Handle the request to create a dataset from the frontend API call
     */
    public void build(final String datasetGuid) {
        Dataset dataset = findDataset(datasetGuid);
        ContentPackage contentPackage = dataset.getContentPackage();

        try {
            List<String> packageGuids = getPackages(dataset);
            List<String> sectionGuids = getSections(packageGuids, dataset);

            String modelId = contentPackage.getId() + "-" + contentPackage.getVersion();

            if (packageGuids.size() < 1 || sectionGuids.size() < 1) {
                throw new ResourceException(Response.Status.BAD_REQUEST, contentPackage.getGuid(),
                        "Not enough course data to create an analytics dataset. A course must be published with data for at least ten students before a dataset can be created.");
            }

            log.info("Processing dataset request with sections: " + sectionGuids.toString());

            Map<String, String> variableReplacements = new HashMap<>();

            variableReplacements.put("modelId", toSqlString(modelId));
            variableReplacements.put("sectionGuids", toSqlString(sectionGuids));
            variableReplacements.put("packageGuids", toSqlString(packageGuids));

            Map<String, String> queries = new HashMap<>();
            for (Map.Entry<String, String> query : datasetCreationQueries.entrySet()) {
                queries.put(query.getKey(), parseQuery(query.getValue(), variableReplacements));
            }

            // Build the dataset
            JsonObject blob = new JsonObject();
            for (Map.Entry<String, String> query : queries.entrySet()) {
                JsonArray results = db.readDatabase(query.getValue());
                // postProcess mutates the result set to add calculated statistics
                postProcess(results);
                blob.add(query.getKey(), results);
            }
            log.info("Dataset creation completed");

            dataset.setDatasetStatus(DatasetStatus.DONE);
            dataset.setMessage("");
            dataset.setDateCompleted(new Date());
            dataset.setDatasetBlob(new DatasetBlob(new JsonWrapper(blob)));
        } catch (Throwable t) {
            log.info("Dataset creation failure: " + t.getMessage());
            dataset.setDatasetStatus(DatasetStatus.FAILED);
            dataset.setMessage(t.getMessage());
            dataset.setDateCompleted(new Date());
        }
    }

    /**
     * Returns all the packages in reverse-chronological order by the creation date
     * that we want to query against for creating the dataset.
     * <p>
     * We include the original package triggered by the API request, previous
     * versions of that package, and "parents" of the package if it was cloned.
     */
    private List<String> getPackages(final Dataset dataset) throws SQLException {
        ContentPackage datasetPackage = dataset.getContentPackage();
        List<ContentPackage> packagesToQuery = new ArrayList<>();
        packagesToQuery.add(datasetPackage);

        // If this course has been cloned, there may be no previous versions of the
        // course with data, so we also consider package parents created before the
        // date this course was cloned.
        String parentGuid = datasetPackage.getParentPackage();
        while (parentGuid != null) {
            ContentPackage parent = findContentPackage(parentGuid);
            packagesToQuery.add(parent);
            parentGuid = parent.getParentPackage();
        }
        // Sort packages from newest to oldest
        packagesToQuery.sort((ContentPackage a, ContentPackage b) -> b.getDateCreated().compareTo(a.getDateCreated()));

        List<String> packageGuids = new ArrayList<String>();

        for (ContentPackage contentPackage : packagesToQuery) {
            Map<String, String> variableReplacements = new HashMap<>();
            variableReplacements.put("packageId", toSqlString(contentPackage.getId()));
            variableReplacements.put("packageGuid", toSqlString(contentPackage.getGuid()));
            String query = parseQuery(findPackagesQuery, variableReplacements);

            // DB queries run synchronously
            JsonArray results = db.readDatabase(query);
            log.info("Received packages from db: " + results.toString());

            // Query returns package versions ordered from newest to oldest. Results are in
            // the form [{ "guid": "234587878753827" }]
            for (JsonElement result : results) {
                packageGuids.add(result.getAsJsonObject().get("guid").getAsString());
            }
        }

        return packageGuids;
    }

    /**
     * Returns all the section guids needed to create the dataset.
     * <p>
     * Performs a series of queries against the reporting db to decide how many
     * sections to use in the analytics dataset. Only uses as many sections as are
     * required to meet a "sufficient data" threshold in order to keep the analytics
     * data fresh without being drowned out by data too far in the past.
     * 
     * @param packageGuids
     * @param dataset
     * @return The section guids for the dataset query.
     */
    private List<String> getSections(final List<String> packageGuids, Dataset dataset) throws SQLException {
        List<String> sectionGuids = new ArrayList<>();
        Integer userCount = 0;

        outside: for (String packageGuid : packageGuids) {
            Map<String, String> variableReplacements = new HashMap<>();
            variableReplacements.put("packageGuid", toSqlString(packageGuid));
            String query = parseQuery(findSectionsQuery, variableReplacements);

            // Blocks until db returns results
            JsonArray results = db.readDatabase(query);
            log.info("Received sections from db: " + results.toString());

            // Sections query returns results in the form:
            // [{ section_guid : String, count : Int }]
            for (JsonElement result : results) {
                JsonObject obj = result.getAsJsonObject();
                sectionGuids.add(obj.get("section_guid").getAsString());

                userCount += obj.get("students").getAsInt();

                Boolean hasTooManyStudents = userCount >= MAXIMUM_STUDENTS;
                Boolean hasEnoughSectionsAndStudents = sectionGuids.size() >= 10 && userCount >= MINIMUM_STUDENTS;
                Boolean hasTooManySections = sectionGuids.size() > 20;
                if (hasEnoughSectionsAndStudents || hasTooManyStudents || hasTooManySections) {
                    break outside;
                }
            }
        }

        return sectionGuids;
    }

    private void postProcess(JsonArray results) {
        for (int i = 0; i < results.size(); i++) {
            JsonObject result = results.get(i).getAsJsonObject();

            camelcase(result);

            addUtilizationStart(result);
            addUtilizationFinish(result);
            addAverageHelpNeeded(result);
            addAverageNumberOfTries(result);
            addCompletionRate(result);
            addAccuracyRate(result);

            results.set(i, (JsonElement) result);
        }
    }

    private void camelcase(JsonObject result) {
        camelcaseReplace("part_uid", "id", result);
        camelcaseReplace("part_id", "part", result);
        camelcaseReplace("resource_id", "resourceId", result);
        camelcaseReplace("resource_title", "resourceTitle", result);
        camelcaseReplace("question_id", "questionId", result);
        camelcaseReplace("submit_and_compare", "submitAndCompare", result);
        camelcaseReplace("distinct_students", "distinctStudents", result);
        camelcaseReplace("distinct_registrations", "distinctRegistrations", result);
        camelcaseReplace("eventually_correct", "eventuallyCorrect", result);
        camelcaseReplace("first_response_correct", "firstResponseCorrect", result);
    }

    private void camelcaseReplace(String key, String replacement, JsonObject object) {
        if (object.get(key) != null) {
            object.addProperty(replacement, object.get(key).getAsString());
            object.remove(key);
        }
    }

    private void addUtilizationStart(JsonObject result) {
        // This represents the percentage of students registered for a course who
        // attempt the problem
        // Utilization Start = practice / (distinct registrations * opportunities)
        Double practice = result.get("practice").getAsDouble();
        Double distinctRegistrations = result.get("distinctRegistrations").getAsDouble();
        Double opportunities = result.get("opportunities").getAsDouble();
        Double utilizationStart = 0.0;

        if (!Double.isNaN(distinctRegistrations) && !Double.isNaN(opportunities) && !Double.isNaN(practice)
                && distinctRegistrations * opportunities > 0) {
            utilizationStart = practice / (distinctRegistrations * opportunities);
        }
        result.addProperty("utilizationStart", utilizationStart);
    }

    private void addUtilizationFinish(JsonObject result) {
        // This represents the percentage of students registered for a course who
        // complete the problem
        // Utilization Finish = correct / (distinct registrations * opportunities)
        Double correct = result.get("correct").getAsDouble();
        Double distinctRegistrations = result.get("distinctRegistrations").getAsDouble();
        Double opportunities = result.get("opportunities").getAsDouble();
        Double utilizationFinish = 0.0;

        if (!Double.isNaN(correct) && !Double.isNaN(distinctRegistrations) && !Double.isNaN(opportunities)
                && distinctRegistrations * opportunities > 0)
            utilizationFinish = correct / (distinctRegistrations * opportunities);
        result.addProperty("utilizationFinish", utilizationFinish);
    }

    private void addAverageHelpNeeded(JsonObject result) {
        // Average help needed = (hints + errors) / practice
        Double hints = result.get("hints").getAsDouble();
        Double errors = result.get("errors").getAsDouble();
        Double practice = result.get("practice").getAsDouble();
        Double averageHelpNeeded = 0.0;

        if (!Double.isNaN(hints) && !Double.isNaN(errors) && !Double.isNaN(practice) && practice > 0) {
            averageHelpNeeded = (hints + errors) / practice;
        }
        result.addProperty("avgHelpNeeded", averageHelpNeeded);
    }

    private void addAverageNumberOfTries(JsonObject result) {
        // Average number of tries = (errors + correct) / practice
        Double errors = result.get("errors").getAsDouble();
        Double correct = result.get("correct").getAsDouble();
        Double practice = result.get("practice").getAsDouble();
        Double averageNumberOfTries = 0.0;

        if (!Double.isNaN(errors) && !Double.isNaN(correct) && !Double.isNaN(practice) && practice > 0) {
            averageNumberOfTries = (errors + correct) / practice;
        }
        result.addProperty("avgNumberOfTries", averageNumberOfTries);
    }

    private void addCompletionRate(JsonObject result) {
        // Completion rate = correct / practice
        Double correct = result.get("correct").getAsDouble();
        Double practice = result.get("practice").getAsDouble();
        Double completionRate = 0.0;

        if (!Double.isNaN(correct) && !Double.isNaN(practice) && practice > 0) {
            completionRate = correct / practice;
        }
        result.addProperty("completionRate", completionRate);
    }

    private void addAccuracyRate(JsonObject result) {
        // Accuracy rate = first response correct / practice
        Double firstResponseCorrect = result.get("firstResponseCorrect").getAsDouble();
        Double practice = result.get("practice").getAsDouble();
        Double accuracyRate = 0.0;

        if (!Double.isNaN(firstResponseCorrect) && !Double.isNaN(practice) && practice > 0) {
            accuracyRate = firstResponseCorrect / practice;
        }
        result.addProperty("accuracyRate", accuracyRate);
    }

    /**
     * Replaces all variable values in a SQL file with a stringified version so that
     * it can be executed.
     */
    private String parseQuery(final String query, final Map<String, String> variableReplacements) {
        StringBuilder queryBuilder = new StringBuilder();
        Scanner scanner = new Scanner(query);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();

            // Ignore comments
            if (line.startsWith("#")) {
                continue;
            }

            // Find all ':' instances to replace in the sql query
            Pattern pattern = Pattern.compile(":(\\w+)");
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                line = matcher.replaceAll(variableReplacements.get(matcher.group(1)));
            }
            queryBuilder.append(line).append("\n");
        }
        scanner.close();

        return queryBuilder.toString();
    }

    private static String readScript(final String scriptLocation) {
        try {
            InputStream stream = DatasetBuilder.class.getClassLoader().getResourceAsStream(scriptLocation);
            return readFile(stream);
        } catch (IOException ex) {
            final String message = "An unexpected error has occurred while reading dataset creation queries from file: sql_queries.json";
            // log.error(message, ex);
            throw new RuntimeException(message, ex);
        }
    }

    private static String readFile(InputStream inputStream) throws IOException {
        byte[] buffer;
        int length;
        try {
            buffer = new byte[inputStream.available()];
            length = inputStream.read(buffer);
        } finally {
            inputStream.close();
        }
        return new String(buffer, 0, length, "UTF-8");
    }

    private String toSqlString(final String x) {
        return "'" + x + "'";
    }

    private String toSqlString(final List<String> xs) {
        if (xs.size() == 1) {
            return toSqlString(xs.get(0));
        }

        final StringBuilder builder = new StringBuilder();
        builder.append("'");
        xs.forEach(x -> builder.append(x).append("','"));
        builder.replace(builder.lastIndexOf(",") - 1, builder.lastIndexOf("'"), "");
        return builder.toString();
    }

    private Dataset findDataset(String datasetGuid) {
        Dataset dataset = null;
        try {
            dataset = em.find(Dataset.class, datasetGuid);
            if (dataset == null) {
                String message = "Error: dataset requested was not found " + datasetGuid;
                log.error(message);
                throw new ResourceException(Response.Status.NOT_FOUND, datasetGuid, message);
            }
        } catch (IllegalArgumentException e) {
            String message = "Server Error while locating package " + datasetGuid;
            log.error(message);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, datasetGuid, message);
        }
        return dataset;
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