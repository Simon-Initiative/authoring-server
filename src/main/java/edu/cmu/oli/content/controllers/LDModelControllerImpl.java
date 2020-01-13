package edu.cmu.oli.content.controllers;

import edu.cmu.oli.content.configuration.DedicatedExecutor;
import com.google.gson.*;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.ContentServiceException;
import edu.cmu.oli.content.DirectoryUtils;
import edu.cmu.oli.content.ResourceException;
import edu.cmu.oli.content.boundary.managers.ContentResourceManager;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import edu.cmu.oli.content.models.persistance.entities.Edge;
import edu.cmu.oli.content.models.persistance.entities.Resource;
import org.jboss.ejb3.annotation.TransactionTimeout;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Raphael Gachuhi
 */
@Stateless
public class LDModelControllerImpl implements LDModelController {

    private static final String ID_PATTERN = "[a-zA-Z_][a-zA-Z_0-9\\-\\.]{0,249}";

    @Inject
    @Logging
    Logger log;

    @PersistenceContext
    EntityManager em;

    @Inject
    ContentResourceManager contentResourceManager;

    @Inject
    DirectoryUtils directoryUtils;

    @Inject
    EdgesController edgesController;

    @Inject
    @ConfigurationCache
    Instance<Configurations> configuration;

    @Inject
    @DedicatedExecutor("svnExecutor")
    ExecutorService svnExecutor;

    @Override
    public List<String> importLDModel(String pkgGuid, Optional<String> skillsModel, Optional<String> problemsModel,
                                      Optional<String> losModel) throws ContentServiceException {
        ContentPackage aPackage = findPackage(pkgGuid, 0);
        return importLDModel(aPackage, skillsModel, problemsModel, losModel);
    }

    private ContentPackage findPackage(final String pkgGuid, int retryCnt) throws ContentServiceException {
        // Because the package may not yet have been flushed into the database, retry 5
        // times with a 2sec delay between trials
        TypedQuery<ContentPackage> q = em.createNamedQuery("ContentPackage.findByGuid", ContentPackage.class);
        q.setParameter("guid", pkgGuid);
        List<ContentPackage> resultList = q.getResultList();
        if (!resultList.isEmpty()) {
            return resultList.get(0);
        }
        if (retryCnt > 10) {
            throw new ContentServiceException("ContentPackage missing [" + pkgGuid + "]");
        }
        synchronized (pkgGuid) {
            try {
                pkgGuid.wait(3000);
            } catch (InterruptedException e) {
            }
        }
        return findPackage(pkgGuid, ++retryCnt);
    }

    @Override
    @TransactionTimeout(unit = TimeUnit.MINUTES, value = 50L)
    public List<String> importLDModel(ContentPackage pkg, Optional<String> skillsModel, Optional<String> problemsModel,
                                      Optional<String> losModel) throws ContentServiceException {

        Path ldmodelTracker = Paths
                .get(pkg.getSourceLocation() + File.separator + "ldmodel" + File.separator + "ldhash.json");
        JsonObject ldHash = new JsonObject();
        if (Files.exists(ldmodelTracker)) {
            try {
                ldHash = new JsonParser().parse(new String(Files.readAllBytes(ldmodelTracker), StandardCharsets.UTF_8))
                        .getAsJsonObject();
            } catch (IOException e) {
            }
        }
        boolean imSkills = false;
        boolean imLos = false;
        boolean imProblems = false;
        List<String> messages = new ArrayList<>();

        if (skillsModel.isPresent()) {
            try {
                String hash = AppUtils.hashDigest(skillsModel.get());
                if (!ldHash.has("skills") || !ldHash.get("skills").getAsString().equals(hash)) {
                    imSkills = true;
                }
                ldHash.addProperty("skills", hash);
            } catch (NoSuchAlgorithmException e) {
            }
        }

        if (losModel.isPresent()) {
            try {
                String hash = AppUtils.hashDigest(losModel.get());
                if (!ldHash.has("los") || !ldHash.get("los").getAsString().equals(hash)) {
                    imLos = true;
                }
                ldHash.addProperty("los", hash);
            } catch (NoSuchAlgorithmException e) {
            }
        }

        if (problemsModel.isPresent()) {
            try {
                String hash = AppUtils.hashDigest(problemsModel.get());
                if (!ldHash.has("problems") || !ldHash.get("problems").getAsString().equals(hash)) {
                    imProblems = true;
                }
                ldHash.addProperty("problems", hash);
            } catch (NoSuchAlgorithmException e) {
            }
        }

        log.info("im files - imLos=" + imLos + " imProblems=" + imProblems + " imSkills=" + imSkills);

        if (imSkills) {
            importSkills(pkg, "skills.tsv", skillsModel.get());
        }

        if (imLos) {
            messages.addAll(importLOsSkillsMap(pkg, "los.tsv", losModel.get()));
        }

        if (imProblems) {
            messages.addAll(importProblemsSkillsMap(pkg, "problems.tsv", problemsModel.get()));
        }

        if (imSkills || imLos || imProblems) {
            try {
                Files.write(ldmodelTracker, AppUtils.gsonBuilder().create().toJson(ldHash).getBytes());
            } catch (IOException e) {
            }

            edgesController.validateEdgesAsync(pkg.getGuid());

            svnExecutor.submit(() -> {
                try {
                    SVNSyncController svnSyncController = (SVNSyncController)
                            new InitialContext().lookup("java:global/content-service/SVNSyncController");
                    Map<String, List<File>> changedFiles = svnSyncController.updateSvnRepo(
                            FileSystems.getDefault().getPath(pkg.getSourceLocation()).toFile(), pkg.getGuid());
                } catch (NamingException e) {
                    log.error(e.getExplanation(), e);
                }
            });
        }

        return messages;
    }

    protected Optional<JsonElement> importSkills(ContentPackage pkg, String fileName, String value)
            throws ContentServiceException {
        JsonWrapper skillsIndexWrapper = pkg.getSkillsIndex();

        if (skillsIndexWrapper == null) {
            skillsIndexWrapper = new JsonWrapper(new JsonObject());
        }
        JsonObject skillsIndex = (JsonObject) skillsIndexWrapper.getJsonObject();

        String[] lines = value.split("\n");

        if (lines.length == 0) {
            String message = "line 1: " + fileName + " file is empty, no records specified";
            log.error(message);
            throw new ContentServiceException(message);
        }
        String resourceId = "learning_model_import";

        Map<String, ResourceJson> resourceMapById = new HashMap<>();

        ResourceJson resourceById = resourceMapById.get(resourceId);

        if (resourceById == null) {
            resourceById = getResourceJson(pkg, resourceMapById, resourceId);
        }

        Map<String, JsonObject> idToSkill = new HashMap<>();
        JsonObject object = null;
        JsonArray skillArray = null;
        if (resourceById == null) {
            object = new JsonObject();
            JsonObject resourceJson = new JsonObject();
            resourceJson.addProperty("@id", resourceId);
            object.add("skills", resourceJson);

            JsonObject skillsTitle = new JsonObject();
            JsonObject skillsText = new JsonObject();
            skillsText.addProperty("#text", "Imported Skills");
            skillsTitle.add("title", skillsText);
            skillArray = new JsonArray();
            skillArray.add(skillsTitle);
            resourceJson.add("#array", skillArray);
        } else {
            object = resourceById.json;

            skillArray = rebuildSkillList(object.getAsJsonObject("skills").getAsJsonArray("#array"), idToSkill);
            object.getAsJsonObject("skills").add("#array", skillArray);
        }

        // Skip column headings
        String line;
        int n = 1, m = 0;
        for (int x = n; lines.length > x; x++) {
            line = lines[x];
            // Increment line number
            n++;
            // Split record into fields
            String[] fields = line.split("\\t");

            // Is the records a complete record?
            if (fields.length < 1) {
                String message = "line " + n + ": " + fileName + ": incomplete record, contains " + fields.length
                        + " of 1 required fields";
                log.error(message);
                throw new ContentServiceException(message);
            }

            // Is the skill ID a valid ID?
            String skillId = fields[0].trim();
            if (!skillId.matches(ID_PATTERN)) {
                String message = "line " + n + ": " + fileName + ": invalid skill ID, does not match /" + ID_PATTERN
                        + "/: " + skillId;
                log.error(message);
                throw new ContentServiceException(message);
            }

            log.debug("SkillId: " + skillId);
            // Does the skill already exist in model?
            if (skillsIndex.has(skillId)) {
                continue;
            }

            // Parse the skill title
            String title = null;
            if (fields.length > 1) {
                title = fields[1].trim();
                if ("".equals(title)) {
                    title = null;
                }
            }

            JsonObject skill = idToSkill.get(skillId);

            // Create and persist the skill
            if (skill == null) {
                skill = new JsonObject();
                skill.addProperty("@id", skillId);
                JsonObject ob = new JsonObject();
                ob.add("skill", skill);
                skillArray.add(ob);
            }

            // Parse skill specific parameters
            parseSkillParameters(skill, fileName, n, 1, fields);

            JsonArray details = new JsonArray();
            JsonObject text = new JsonObject();
            text.addProperty("#text", title);
            details.add(text);
            skill.add("#array", details);

            m++;

            log.debug("import Skill: line " + n + " " + AppUtils.gsonBuilder().create().toJsonTree(skill));
        }

        if (skillArray.size() > 1) {
            // log.info("Imported Skills Json\n" +
            // AppUtils.gsonBuilder().create().toJson(object));
            JsonArray doc = new JsonArray();
            doc.add(object);
            JsonObject res = new JsonObject();
            res.add("doc", doc);

            String lockId = AppUtils.generateGUID();

            if (resourceById == null) {
                contentResourceManager.doCreate(pkg.getGuid(), "x-oli-skills_model", res, "LDModel", lockId, true);
            } else {
                contentResourceManager.doUpdate(pkg.getGuid(), resourceById.resource, res, "LDModel", lockId, null, true);
            }

            log.debug("Imported SkillModel: created model id=" + resourceId + " with " + m + " skills");

            return Optional.of(res);
        }
        return Optional.empty();
    }

    private JsonArray rebuildSkillList(JsonArray skillArray, Map<String, JsonObject> idToSkill) {
        JsonArray skills = new JsonArray();
        for (JsonElement skillOb : skillArray) {
            JsonObject skill = skillOb.getAsJsonObject().getAsJsonObject("skill");
            if (skill != null) {
                // Eliminates duplicates
                String skillId = skill.get("@id").getAsString();
                if (!idToSkill.containsKey(skillId)) {
                    idToSkill.put(skillId, skill);
                    skills.add(skillOb);
                }
            } else {
                skills.add(skillOb);
            }
        }
        return skills;
    }

    protected List<String> importLOsSkillsMap(ContentPackage pkg, String fileName, String value)
            throws ContentServiceException {
        log.info("importLOsSkillsMap " + fileName);
        JsonWrapper skillsIndexWrapper = pkg.getSkillsIndex();
        if (skillsIndexWrapper == null || ((JsonObject) skillsIndexWrapper.getJsonObject()).entrySet().isEmpty()) {
            String message = "Error loading Learning Objective skill map. Package " + pkg.getId() + "_"
                    + pkg.getVersion() + " contains zero skills";
            log.error(message);
            throw new ContentServiceException(message);
        }

        JsonWrapper objectivesIndexWrapper = pkg.getObjectivesIndex();
        if (objectivesIndexWrapper == null
                || ((JsonObject) objectivesIndexWrapper.getJsonObject()).entrySet().isEmpty()) {
            String message = "Error loading Learning Objective skill map. Package " + pkg.getId() + "_"
                    + pkg.getVersion() + " contains zero objectives";
            log.error(message);
            throw new ContentServiceException(message);
        }

        JsonObject objectsIndex = (JsonObject) objectivesIndexWrapper.getJsonObject();
        JsonObject skillsIndex = (JsonObject) skillsIndexWrapper.getJsonObject();

        String[] lines = value.split("\n");

        if (lines.length == 0) {
            String message = fileName + " file is empty, no records specified";
            log.error("line 1: " + message);
            throw new ContentServiceException(message);
        }

        String line1 = lines[0];
        String[] pkgInfo = line1.split("\t");
        if (pkgInfo.length < 2) {
            String message = fileName + " incomplete record, package ID and version required";
            log.error("line 1: " + message);
            throw new ContentServiceException(message);
        }

        if (!pkgInfo[0].equals(pkg.getId()) || !pkgInfo[1].equals(pkg.getVersion())) {
            String message = "line 1: " + fileName + " no such content package: " + pkgInfo[0] + ", " + pkgInfo[1];
            log.error(message);
            throw new ContentServiceException(message);
        }

        Map<String, ResourceJson> resourceMapById = new HashMap<>();
        List<String> errors = new ArrayList<>();
        String line;
        int n = 2, m = 0;
        for (int x = n; lines.length > x; x++) {
            line = lines[x];
            // Increment line number
            n++;

            // Split record into fields
            String[] fields = line.split("\\t");

            // Is the records a complete record?
            if (fields.length < 5) {
                String message = "line " + n + ": " + fileName + " incomplete record, contains " + fields.length
                        + " of 5 required fields";
                log.error(message);
                errors.add(message);
                continue;
            }

            // Parse the learning objective ID
            String objref = fields[0].trim();
            if ("".equals(objref)) {
                String message = "line " + n + ": " + fileName + " learning objective not specified";
                log.error(message);
                errors.add(message);
                continue;
            }

            // Locate the learning objective
            JsonObject lo = objectsIndex.getAsJsonObject(objref);
            if (lo == null) {
                String message = "line " + n + ": " + fileName + " learning objective does not exist: " + objref;
                log.error(message);
                errors.add(message);
                continue;
            }
            String objectiveResourceId = lo.get("resourceId").getAsString();
            ResourceJson resourceById = resourceMapById.get(objectiveResourceId);

            if (resourceById == null) {
                resourceById = getResourceJson(pkg, resourceMapById, objectiveResourceId);
            }

            if (resourceById == null) {
                log.error("missing ld resource " + objectiveResourceId);
                continue;
            }

            // low opportunity
            Boolean lowOpportunity = null;
            String field1 = fields[1].trim().toLowerCase();
            if ("x".equals(field1) || "y".equals(field1) || "yes".equals(field1) || "true".equals(field1)) {
                lowOpportunity = Boolean.TRUE;
            } else if ("".equals(field1) || "n".equals(field1) || "no".equals(field1) || "false".equals(field1)) {
                lowOpportunity = Boolean.FALSE;
            } else {
                String message = "line " + n + ": " + fileName + " Parameter low opportunity is not boolean: " + field1;
                log.error(message);
                errors.add(message);
                continue;
            }

            // min
            Integer minPractice = null;
            String field2 = fields[2].trim();
            if (!"".equals(field2)) {
                try {
                    minPractice = new Integer(field2);
                } catch (NumberFormatException e) {
                    String message = "line " + n + ": " + fileName + " Parameter min is not an integer: " + field2;
                    log.error(message);
                    errors.add(message);
                    continue;
                }
            }

            // medium
            BigDecimal mediumMastery = null;
            String field3 = fields[3].trim();
            if (!"".equals(field3)) {
                try {
                    mediumMastery = new BigDecimal(field3);
                } catch (NumberFormatException e) {
                    String message = "line " + n + ": " + fileName + " Parameter medium is not a number: " + field3;
                    log.error(message);
                    errors.add(message);
                    continue;
                }
            }

            // high
            BigDecimal highMastery = null;
            String field4 = fields[4].trim();
            if (!"".equals(field4)) {
                try {
                    highMastery = new BigDecimal(field4);
                } catch (NumberFormatException e) {
                    String message = "line " + n + ": " + fileName + " Parameter high is not a number: " + field4;
                    log.error(message);
                    errors.add(message);
                    continue;
                }
            }

            JsonObject parameters = new JsonObject();
            if (lo.has("parameters")) {
                parameters = lo.getAsJsonObject("parameters");
            }
            parameters.addProperty("low_opportunity", lowOpportunity);
            parameters.addProperty("min_practice", minPractice);
            parameters.addProperty("medium_mastery", mediumMastery);
            parameters.addProperty("high_mastery", highMastery);
            lo.add("parameters", parameters);

            JsonObject resourceJson = resourceById.json.getAsJsonObject("objectives");
            JsonArray objectivesAndMappingList = resourceJson.getAsJsonArray("#array");
            JsonObject objSkillsList = null;
            for (JsonElement e : objectivesAndMappingList) {
                JsonObject e1 = (JsonObject) e;
                if (e1.has("objective_skills")) {
                    JsonObject objectiveSkills = e1.getAsJsonObject("objective_skills");
                    if (objectiveSkills.get("@idref").getAsString().equalsIgnoreCase(objref)) {
                        objSkillsList = objectiveSkills;
                        break;
                    }
                } else if (e1.has("objective")) {
                    JsonObject objective = e1.getAsJsonObject("objective");
                    if (objective.get("@id").getAsString().equalsIgnoreCase(objref)) {
                        objective.addProperty("@low_opportunity", lowOpportunity);
                        objective.addProperty("@min_practice", minPractice);
                        objective.addProperty("@medium_mastery", mediumMastery);
                        objective.addProperty("@high_mastery", highMastery);
                    }
                }
            }

            JsonArray skillsArray = new JsonArray();
            if (objSkillsList != null && objSkillsList.has("#array")) {
                skillsArray = objSkillsList.getAsJsonArray("#array");
            }

            // For each skill, create a mapping...
            for (int i = 5; i < fields.length; i++) {
                // Parse the skill ID
                String skillId = fields[i].trim();
                if ("".equals(skillId)) {
                    continue;
                }

                // Locate the skill
                JsonObject skill = skillsIndex.getAsJsonObject(skillId);
                if (skill == null) {
                    String message = "line " + n + ": " + fileName + " no such skill in model: " + skillId;
                    log.error(message);
                    errors.add(message);
                    continue;
                }
                boolean doesNotExist = true;
                for (JsonElement e : skillsArray) {
                    JsonObject e1 = (JsonObject) e;
                    if (e1.getAsJsonObject("skillref").get("@idref").getAsString().equals(skillId)) {
                        doesNotExist = false;
                    }
                }

                if (doesNotExist) {
                    JsonObject ob = new JsonObject();
                    JsonObject skillRef = new JsonObject();
                    skillRef.addProperty("@idref", skillId);
                    ob.add("skillref", skillRef);
                    skillsArray.add(ob);
                    log.trace("importLOForModel(): line " + n + ": added LO mapping: " + objref + " to " + skillId);
                    m++;
                } else {
                    // Skip duplicate mapping
                    log.warn("line " + n + ": skipping duplicate LO mapping: " + objref + " to " + skillId);
                }
            }

            if (skillsArray.size() > 0 && objSkillsList == null) {
                objSkillsList = new JsonObject();
                objSkillsList.addProperty("@idref", objref);
                objSkillsList.add("#array", skillsArray);
                JsonObject ob = new JsonObject();
                ob.add("objective_skills", objSkillsList);
                objectivesAndMappingList.add(ob);
            }
        }

        resourceMapById.entrySet().forEach(e -> {
            JsonObject wrapper = new JsonObject();
            JsonArray doc = new JsonArray();
            doc.add(e.getValue().json);
            wrapper.add("doc", doc);

            String lockId = UUID.randomUUID().toString().replaceAll("-", "");
            contentResourceManager.doUpdate(pkg.getGuid(), e.getValue().resource, wrapper, "LDModel", lockId, null, true);
        });
        return errors;
    }

    private ResourceJson getResourceJson(ContentPackage pkg, Map<String, ResourceJson> resourceMapById,
                                         String objectiveResourceId) {
        ResourceJson resourceById = null;
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Resource> criteria = cb.createQuery(Resource.class);
        Root<Resource> edgeRoot = criteria.from(Resource.class);

        criteria.select(edgeRoot).where(cb.and(cb.equal(edgeRoot.get("id"), objectiveResourceId),
                cb.equal(edgeRoot.get("contentPackage").get("guid"), pkg.getGuid())));
        List<Resource> resultList = em.createQuery(criteria).getResultList();
        for (Resource rsc : resultList) {
            ResourceJson resourceJson = new ResourceJson();
            if (rsc.getLastRevision() != null) {
                resourceJson.json = rsc.getLastRevision().getBody().getJsonPayload().getJsonObject().getAsJsonObject();
            } else {
                String path = rsc.getFileNode().getVolumeLocation() + File.separator + rsc.getFileNode().getPathTo();
                try (FileReader fileReader = new FileReader(path)) {
                    JsonParser parser = new JsonParser();
                    JsonElement jsonElement = parser.parse(fileReader);
                    resourceJson.json = jsonElement.getAsJsonObject();
                } catch (Exception ex) {
                    log.error(ex.getLocalizedMessage());
                    throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, null, ex.getLocalizedMessage());
                }
            }
            resourceJson.resource = rsc;
            resourceById = resourceJson;
            resourceMapById.put(rsc.getId(), resourceJson);
            break;
        }

        return resourceById;
    }

    protected List<String> importProblemsSkillsMap(ContentPackage pkg, String fileName, String value)
            throws ContentServiceException {
        JsonWrapper skillsIndexWrapper = pkg.getSkillsIndex();
        if (skillsIndexWrapper == null || ((JsonObject) skillsIndexWrapper.getJsonObject()).entrySet().isEmpty()) {
            String message = "Error loading Learning Objective skill map. Package " + pkg.getId() + "_"
                    + pkg.getVersion() + " contains zero skills";
            log.error(message);
            throw new ContentServiceException(message);
        }

        JsonObject skillsIndex = (JsonObject) skillsIndexWrapper.getJsonObject();

        String[] lines = value.split("\n");

        if (lines.length == 0) {
            String message = "line 1: " + fileName + " file is empty, no records specified";
            log.error(message);
            throw new ContentServiceException(message);
        }

        String line1 = lines[0];
        String[] pkgInfo = line1.split("\t");
        if (pkgInfo.length < 2) {
            String message = "line 1: " + fileName + " incomplete record, package ID and version required";
            log.error(message);
            throw new ContentServiceException(message);
        }

        if (!pkgInfo[0].equals(pkg.getId()) || !pkgInfo[1].equals(pkg.getVersion())) {
            String message = "line 1: " + fileName + " no such content package: " + pkgInfo[0] + ", " + pkgInfo[1];
            log.error(message);
            throw new ContentServiceException(message);
        }

        JsonArray customTags = new JsonArray();

        Map<String, ResourceJson> resourceMapById = new HashMap<>();
        List<String> errors = new ArrayList<>();
        String line;
        int n = 2, m = 0;
        loop:
        for (int x = n; lines.length > x; x++) {
            line = lines[x];
            // Increment line number
            n++;

            // Split record into fields
            String[] fields = line.split("\\t");

            // Is the records a complete record?
            if (fields.length < 3) {
                String message = "line " + n + ": " + fileName + " incomplete record, contains " + fields.length
                        + " of 3 required fields";
                log.error(message);
                errors.add(message);
                continue;
            }

            // Parse the resource ID
            String resourceId = fields[0].trim();
            if ("".equals(resourceId)) {
                String message = "line " + n + ": " + fileName + " content resource not specified";
                log.error(message);
                errors.add(message);
                continue;
            }

            // Locate the content resource
            ResourceJson resourceById = resourceMapById.get(resourceId);
            if (resourceById == null) {
                try {
                    resourceById = getResourceJson(pkg, fileName, resourceMapById, resourceId, null);
                } catch (ContentServiceException e) {
                    errors.add(e.getMessage());
                    continue loop;
                }
            }

            if (resourceById == null) {
                String message = "line " + n + ": " + fileName + " no such resource in package: " + resourceId;
                log.error(message);
                errors.add(message);
                continue;
            }

            // Parse the problem ID
            String problemId = fields[1].trim();
            if ("".equals(problemId)) {
                problemId = null;
            }

            // Parse the step ID
            String stepId = fields[2].trim();
            if ("".equals(stepId)) {
                stepId = null;
            }
            Set<String> filter = new HashSet<>(
                    Arrays.asList("x-oli-assessment2", "x-oli-assessment2-pool", "x-oli-inline-assessment"));
            if (!filter.contains(resourceById.resource.getType())) {
                JsonObject resourceTag = new JsonObject();
                customTags.add(resourceTag);
                resourceTag.addProperty("resource", resourceId);
                resourceTag.addProperty("problem", problemId == null ? "" : problemId);
                resourceTag.addProperty("step", stepId == null ? "" : stepId);
                JsonArray skillsTags = new JsonArray();
                resourceTag.add("skills", skillsTags);
                int skillCnt = 0;
                for (int i = 3; i < fields.length; i++) {
                    // Parse the skill ID
                    String skillId = fields[i].trim();
                    if (!skillId.isEmpty()) {
                        JsonObject skillTag = new JsonObject();
                        skillTag.addProperty("skill" + (++skillCnt), skillId);
                        skillsTags.add(skillTag);
                    }
                }
                continue;
            }

            // For each skill, create a tag...
            for (int i = 3; i < fields.length; i++) {
                // Parse the skill ID
                String skillId = fields[i].trim();
                if ("".equals(skillId)) {
                    continue;
                }

                // Locate the skill
                JsonObject skill = skillsIndex.getAsJsonObject(skillId);
                if (skill == null) {
                    String message = "line " + n + ": " + fileName + " no such skill in model: " + skillId;
                    log.error(message);
                    errors.add(message);
                    continue;
                }

                addSkillTag(pkg, fileName, resourceMapById, errors, n, resourceById, problemId, stepId, skillId);

                // Create the skill reference
                m++;
            }
        }

        if (customTags.size() > 0) {
            String customSkillTags = pkg.getSourceLocation() + File.separator + "custom_skill_tags" + File.separator
                    + "custom_activity_skill_tags.json";
            Path path = Paths.get(customSkillTags);
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.error(e.getLocalizedMessage());
            }
            directoryUtils.createDirectories(customSkillTags);
            Gson gson = AppUtils.gsonBuilder().setPrettyPrinting().create();
            try {
                Files.write(path, gson.toJson(customTags).getBytes());
            } catch (IOException e) {
                log.error(e.getLocalizedMessage());
            }
        }

        resourceMapById.entrySet().forEach(e -> {
            if (e.getValue().json != null) {
                JsonObject wrapper = new JsonObject();
                JsonArray doc = new JsonArray();
                doc.add(e.getValue().json);
                wrapper.add("doc", doc);

                String lockId = AppUtils.generateUID(32);
                contentResourceManager.doUpdate(pkg.getGuid(), e.getValue().resource, wrapper, "LDModel", lockId, null, false);
            }
        });

        return errors;
    }

    private void addSkillTag(ContentPackage pkg, String fileName, Map<String, ResourceJson> resourceMapById,
                             List<String> errors, int n, ResourceJson resourceById, String problemId, String stepId, String skillId) {
        JsonElement activity = resourceById.json.entrySet().iterator().next().getValue();
        JsonArray contentArray = activity.getAsJsonObject().getAsJsonArray("#array");
        if (problemId == null) {
            if (resourceById.resource.getType().equals("x-oli-assessment2-pool")) {
                String message = "line " + n + ": " + fileName
                        + " skill tagging at activity level not supported by x-oli-assessment2-pool " + skillId;
                log.error(message);
                errors.add(message);
                return;
            }
            List<JsonElement> allParts = new ArrayList<>();
            findAllParts(contentArray, allParts);
            if (allParts.isEmpty()) {
                int index = -1;
                int gIndex = 0;
                boolean skillRefAlreadyExists = false;
                for (JsonElement it : contentArray) {
                    // Skip title and short_title elements if present
                    JsonObject ob = it.getAsJsonObject();
                    if (ob.has("title") || ob.has("short_title")) {
                        index = gIndex;
                    }
                    // Skip to after last skillref
                    if (ob.has("skillref")) {
                        index = gIndex;
                        // If skillref already set, break loop
                        if (ob.get("skillref").getAsJsonObject().get("@idref").getAsString().equals(skillId)) {
                            skillRefAlreadyExists = true;
                            break;
                        }
                    }
                    gIndex++;
                }

                tagWithSkill(skillId, activity, contentArray, index, skillRefAlreadyExists);
            } else {
                // Remove all activity level tagging
                List<JsonElement> aTags = new ArrayList<>();
                contentArray.forEach(e -> {
                    if (e.getAsJsonObject().has("skillref")) {
                        aTags.add(e.getAsJsonObject());
                    }
                });
                aTags.forEach(e -> contentArray.remove(e));

                // Apply tagging to the part level
                allParts.forEach(part -> {
                    tagPartWithSkill(skillId, part);
                });
            }

        } else {
            List<JsonElement> problemList = new ArrayList<>();
            findProblemById(contentArray, problemId, problemList);
            if (problemList.isEmpty()) {
                if (!resourceById.resource.getType().equals("x-oli-assessment2-pool")) {
                    log.info("Problem=" + problemId + " not found within activity="
                            + activity.getAsJsonObject().get("@id").getAsString() + " resource type="
                            + resourceById.resource.getType());
                }
                // Problem (question) not found within this activity, look for it within pools
                // used by this activity
                // log.debug("problem " + problemId + " not found. Looking in question pools");
                Set<String> poolRefs = new HashSet<>();
                findPoolRefs(contentArray, poolRefs);
                if (!poolRefs.isEmpty()) {
                    poolRefs.forEach(e -> {
                        ResourceJson resById = resourceMapById.get(e);
                        if (resById == null) {
                            try {
                                Set<String> filter = new HashSet<>(Arrays.asList("x-oli-assessment2",
                                        "x-oli-assessment2-pool", "x-oli-inline-assessment"));
                                resById = getResourceJson(pkg, fileName, resourceMapById, e, filter);
                            } catch (ContentServiceException ex) {
                                errors.add(ex.getMessage());
                            }
                        }

                        if (resById == null) {
                            String message = "line " + n + ": " + fileName + " no such resource in package: " + e;
                            log.error(message);
                            errors.add(message);
                        } else {
                            addSkillTag(pkg, fileName, resourceMapById, errors, n, resById, problemId, stepId, skillId);
                        }

                    });
                }
            } else {
                JsonElement problemBody = problemList.get(0);
                JsonArray problemArray = problemBody.getAsJsonObject().getAsJsonArray("#array");
                if (problemArray == null) {
                    log.error("problem " + problemId + " not found. " + fileName + "\nMalformed Problem? "
                            + AppUtils.gsonBuilder().create().toJson(problemBody));
                    return;
                }

                List<JsonElement> allParts = new ArrayList<>();
                findAllParts(problemArray, allParts);
                if (allParts.isEmpty()) {
                    int index = -1;
                    int gIndex = -1;
                    boolean bodyElementFound = false;
                    boolean skillRefAlreadyExists = false;
                    for (JsonElement it : problemArray) {
                        JsonObject ob = it.getAsJsonObject();
                        // Skip to after body element
                        // Note: Assumes skillref always inserted after question body (consistent with
                        // current dtd)
                        if (!ob.has("body") && !bodyElementFound) {
                            index = gIndex;
                            bodyElementFound = true;
                        }

                        // Skip to after last skillref
                        if (ob.has("skillref")) {
                            index = gIndex;
                            // log.info("origin " + skillId +" vs Skill "
                            // +ob.get("skillref").getAsJsonObject().get("@idref").getAsString());
                            // If skillref already exists, set flag and break loop
                            if (ob.get("skillref").getAsJsonObject().get("@idref").getAsString().equals(skillId)) {
                                // log.info("skill found");
                                skillRefAlreadyExists = true;
                                break;
                            }
                        }
                        gIndex++;
                    }
                    tagWithSkill(skillId, problemBody, problemArray, index, skillRefAlreadyExists);
                } else {
                    // Remove all question level tagging
                    List<JsonElement> qTags = new ArrayList<>();
                    problemArray.forEach(e -> {
                        if (e.getAsJsonObject().has("skillref")) {
                            qTags.add(e.getAsJsonObject());
                        }
                    });
                    qTags.forEach(e -> problemArray.remove(e));

                    // Apply all tagging at the part level
                    assignPartIdIfAbsent(problemBody.getAsJsonObject(), problemId);
                    Optional<JsonElement> partByStepId = findPartByStepId(allParts, problemId, stepId);
                    if (partByStepId.isPresent()) {
                        tagPartWithSkill(skillId, partByStepId.get());
                    } else {
                        allParts.forEach(part -> {
                            tagPartWithSkill(skillId, part);
                        });
                    }
                }
            }
        }
    }

    private void tagWithSkill(String skillId, JsonElement problemBody, JsonArray problemArray, int index, boolean skillRefAlreadyExists) {
        if (skillRefAlreadyExists) {
            return;
        }

        JsonObject wrapper = new JsonObject();
        JsonObject skillTag = new JsonObject();
        skillTag.addProperty("@idref", skillId);
        wrapper.add("skillref", skillTag);
        JsonArray insert = insert(index, wrapper, problemArray);
        problemBody.getAsJsonObject().add("#array", insert);
    }

    private Optional<JsonElement> findPartByStepId(List<JsonElement> allParts, String problemId, String stepId) {
        if (stepId == null) {
            return Optional.empty();
        }
        for (JsonElement el : allParts) {
            final JsonObject part = el.getAsJsonObject();
            if (part.has("@id") && (part.get("@id").getAsString().equalsIgnoreCase(stepId) ||
                    part.get("@id").getAsString().equalsIgnoreCase(problemId + "_" + stepId))) {
                return Optional.of(part);
            }
        }

        return Optional.empty();
    }

    private void tagPartWithSkill(String skillId, JsonElement part) {

        final JsonObject partAsJsonObject = part.getAsJsonObject();
        if (!partAsJsonObject.has("@id")) {
            partAsJsonObject.addProperty("@id", UUID.randomUUID().toString().replaceAll("-", ""));
        }
        if (!partAsJsonObject.has("#array")) {
            log.error("part before -- " + AppUtils.gsonBuilder().create().toJson(part));
            JsonArray stepArray = new JsonArray();

            Iterator<Map.Entry<String, JsonElement>> it = partAsJsonObject.entrySet().iterator();
            Set<String> keySet = new HashSet<>();
            keySet.addAll(partAsJsonObject.keySet());
            keySet.forEach(key -> {
                if (!key.equals("@id")) {
                    JsonElement remove = partAsJsonObject.remove(key);
                    JsonObject ob = new JsonObject();
                    ob.add(key, remove);
                }
            });
            partAsJsonObject.add("#array", stepArray);

            log.error("part after -- " + AppUtils.gsonBuilder().create().toJson(part));
        }
        JsonArray stepArray = partAsJsonObject.getAsJsonArray("#array");

        boolean skillRefAlreadyExists = false;
        try {
            for (JsonElement next : stepArray) {
                if (next.isJsonObject() && next.getAsJsonObject().has("skillref") && next.getAsJsonObject()
                        .get("skillref").getAsJsonObject().get("@idref").getAsString().equals(skillId)) {
                    skillRefAlreadyExists = true;
                    break;
                }
            }
        } catch (Exception e) {
            log.error(AppUtils.gsonBuilder().create().toJson(part));
            throw e;
        }
        tagWithSkill(skillId, part, stepArray, -1, skillRefAlreadyExists);
    }

    private void findAllParts(JsonElement content, List<JsonElement> parts) {
        if (content.isJsonArray()) {
            for (JsonElement e : content.getAsJsonArray()) {
                findAllParts(e, parts);
            }
        } else if (content.isJsonObject()) {
            if (content.getAsJsonObject().has("part")) {
                parts.add(content.getAsJsonObject().get("part"));
            } else {
                for (Map.Entry<String, JsonElement> e : content.getAsJsonObject().entrySet()) {
                    findAllParts(e.getValue(), parts);
                }
            }
        }
    }

    private void findProblemById(JsonElement content, String problemId, List<JsonElement> problemList) {
        if (content.isJsonArray()) {
            for (JsonElement e : content.getAsJsonArray()) {
                findProblemById(e, problemId, problemList);
            }
        } else if (content.isJsonObject()) {
            if (content.getAsJsonObject().has("question")) {
                JsonObject question = content.getAsJsonObject().get("question").getAsJsonObject();
                if (question.has("@id") && question.get("@id").getAsString().equalsIgnoreCase(problemId)) {
                    problemList.add(question);
                    return;
                }
            } else {
                for (Map.Entry<String, JsonElement> e : content.getAsJsonObject().entrySet()) {
                    findProblemById(e.getValue(), problemId, problemList);
                }
            }
        }
    }

    private void findFirstResponseFromPart(JsonElement content, List<JsonElement> response) {
        if (content.isJsonArray()) {
            for (JsonElement e : content.getAsJsonArray()) {
                findFirstResponseFromPart(e, response);
            }
        } else if (content.isJsonObject()) {
            if (content.getAsJsonObject().has("response")) {
                response.add(content.getAsJsonObject().get("response"));
                return;
            } else {
                for (Map.Entry<String, JsonElement> e : content.getAsJsonObject().entrySet()) {
                    findFirstResponseFromPart(e.getValue(), response);
                }
            }
        }
    }

    private void assignPartIdIfAbsent(JsonObject problemBody, String problemId) {
        JsonArray problemElements = problemBody.get("#array").getAsJsonArray();
        int p = 0;
        for (JsonElement el : problemElements) {
            if (el.getAsJsonObject().has("part")) {
                final JsonObject part = el.getAsJsonObject().get("part").getAsJsonObject();
                if (!part.has("@id")) {
                    part.addProperty("@id", problemId + "_p" + (++p));
                }
            }
        }
    }

    private void findPoolRefs(JsonElement content, Set<String> poolRefs) {
        if (content.isJsonArray()) {
            content.getAsJsonArray().forEach(e -> {
                findPoolRefs(e, poolRefs);
            });
        } else if (content.isJsonObject()) {
            JsonObject ob = content.getAsJsonObject();
            if (ob.has("pool_ref")) {
                poolRefs.add(ob.get("pool_ref").getAsJsonObject().get("@idref").getAsString());
                return;
            }
            ob.entrySet().forEach(e -> {
                findPoolRefs(e.getValue(), poolRefs);
            });
        }
    }

    private JsonArray insert(int index, JsonElement val, JsonArray currentArray) {
        JsonArray newArray = new JsonArray();
        int i = 0;
        for (; i <= index; i++) {
            newArray.add(currentArray.get(i));
        }
        newArray.add(val);

        for (; i < currentArray.size(); i++) {
            newArray.add(currentArray.get(i));
        }
        return newArray;
    }

    @Override
    public Map<String, String> extractTabSeparatedModel(ContentPackage pkg) {
        JsonWrapper skillsIndexWrapper = pkg.getSkillsIndex();
        String skillsTabSeparated = null;
        if (skillsIndexWrapper != null) {
            skillsTabSeparated = skillsTabSeparated((JsonObject) skillsIndexWrapper.getJsonObject());
        }
        List<Edge> skillEdges = skillEdges(pkg);

        JsonWrapper objectivesIndexWrapper = pkg.getObjectivesIndex();
        String objectivesTabSeparated = null;
        if (objectivesIndexWrapper != null) {
            objectivesTabSeparated = objectivesTabSeparated(pkg,
                    objectivesIndexWrapper.getJsonObject().getAsJsonObject(), skillEdges);
        }

        String problemsTabSeparated = problemsTabSeparated(pkg, skillEdges);

        Map<String, String> models = new HashMap<>();
        if (skillsTabSeparated != null) {
            models.put(pkg.getId() + "_" + pkg.getVersion().replaceAll("\\.", "_") + "-skills.tsv", skillsTabSeparated);
        }

        if (objectivesTabSeparated != null) {
            models.put(pkg.getId() + "_" + pkg.getVersion().replaceAll("\\.", "_") + "-los.tsv", objectivesTabSeparated);
        }

        models.put(pkg.getId() + "_" + pkg.getVersion().replaceAll("\\.", "_") + "-problems.tsv", problemsTabSeparated);

        return models;
    }

    @Override
    public ByteArrayOutputStream exportLDModel(ContentPackage pkg) throws ContentServiceException {

        Map<String, String> models = extractTabSeparatedModel(pkg);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            if (models.containsKey(pkg.getId() + "_" + pkg.getVersion().replaceAll("\\.", "_") + "-skills.tsv")) {
                ZipEntry entry = new ZipEntry(
                        pkg.getId() + "_" + pkg.getVersion().replaceAll("\\.", "_") + "-skills.tsv");
                zos.putNextEntry(entry);
                zos.write(models.get(pkg.getId() + "_" + pkg.getVersion().replaceAll("\\.", "_") + "-skills.tsv").getBytes());
                zos.closeEntry();
            }
            if (models.containsKey(pkg.getId() + "_" + pkg.getVersion().replaceAll("\\.", "_") + "-los.tsv")) {
                ZipEntry entry = new ZipEntry(pkg.getId() + "_" + pkg.getVersion().replaceAll("\\.", "_") + "-los.tsv");
                zos.putNextEntry(entry);
                zos.write(models.get(pkg.getId() + "_" + pkg.getVersion().replaceAll("\\.", "_") + "-los.tsv").getBytes());
                zos.closeEntry();
            }

            ZipEntry entry = new ZipEntry(
                    pkg.getId() + "_" + pkg.getVersion().replaceAll("\\.", "_") + "-problems.tsv");
            zos.putNextEntry(entry);
            zos.write(models.get(pkg.getId() + "_" + pkg.getVersion().replaceAll("\\.", "_") + "-problems.tsv").getBytes());
            zos.closeEntry();

            return baos;

        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new ContentServiceException("Unable export LDModel");
        }
    }

    private String problemsTabSeparated(ContentPackage pkg, List<Edge> skillEdges) {
        List<Edge> poolRefEdges = poolRefEdges(pkg);
        Map<String, Set<String>> poolRefs = new HashMap<>();
        poolRefEdges.forEach(edge -> {
            String dest = edge.getDestinationId().split(":")[2];
            String src = edge.getSourceId().split(":")[2];
            Set<String> srcs = poolRefs.get(dest);
            if (srcs == null) {
                srcs = new HashSet<>();
                poolRefs.put(dest, srcs);
            }
            srcs.add(src);
        });

        Map<SkillTagLevel, List<Edge>> probRefs = new HashMap<>();
        skillEdges.stream().forEach(e -> {
            if (e.getSourceType().equals("x-oli-inline-assessment") || e.getSourceType().equals("x-oli-assessment2")
                    || e.getSourceType().equals("x-oli-assessment2-pool")) {
                String[] split = e.getSourceId().split(":");
                String resourceId = split[split.length - 1];
                String problem = null;
                String step = null;
                JsonObject pathInfo = e.getMetadata().getJsonObject().getAsJsonObject().get("pathInfo")
                        .getAsJsonObject();
                JsonObject parent = pathInfo.get("parent").getAsJsonObject();
                String name = parent.get("name").getAsString();
                if (name.equals("question") || name.equals("multiple_choice") || name.equals("text")
                        || name.equals("fill_in_the_blank") || name.equals("numeric") || name.equals("essay")
                        || name.equals("short_answer") || name.equals("image_hotspot") || name.equals("ordering")
                        || name.equals("matrix")) {
                    problem = parent.get("@id").getAsString();
                }
                if (name.equals("part") || name.equals("input")) {
                    step = parent.get("@id").getAsString();
                    problem = parent.get("parent").getAsJsonObject().get("@id").getAsString();
                }
                if (problem == null && step == null) {
//                    problem = AppUtils.gsonBuilder().create().toJson(parent);
                }
                if (e.getSourceType().equals("x-oli-assessment2-pool")) {
                    Set<String> srcResourceId = poolRefs.get(resourceId);
                    if (srcResourceId != null) {
                        for (String s : srcResourceId) {
                            skillModelTag(probRefs, e, s, problem, step);
                        }
                    } else {
                        log.info("Pool Reference for pool=" + resourceId + " not found ");
                        skillModelTag(probRefs, e, resourceId, problem, step);
                    }
                } else {
                    skillModelTag(probRefs, e, resourceId, problem, step);
                }
            }
        });

        List<Map<String, String>> problemTags = new ArrayList<>();
        probRefs.entrySet().stream().forEach(e -> {
            Map<String, String> tags = new HashMap<>();
            problemTags.add(tags);
            tags.put("resource", e.getKey().resource);
            tags.put("problem", e.getKey().problem == null ? "" : e.getKey().problem);
            tags.put("step", e.getKey().step == null ? "" : e.getKey().step);
            int sIndex = 0;
            for (Edge edge : e.getValue()) {
                String[] split = edge.getDestinationId().split(":");
                tags.put("skill" + (sIndex++), split[split.length - 1]);
            }
        });
        String customSkillTags = pkg.getSourceLocation() + File.separator + "custom_skill_tags" + File.separator
                + "custom_activity_skill_tags.json";
        Path path = Paths.get(customSkillTags);
        if (Files.exists(path)) {
            try {
                JsonArray customSkillsTags = new JsonParser()
                        .parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).getAsJsonArray();
                customSkillsTags.forEach(e -> {
                    JsonObject skillTag = e.getAsJsonObject();
                    Map<String, String> tags = new HashMap();
                    problemTags.add(tags);
                    tags.put("resource", skillTag.get("resource").getAsString());
                    tags.put("problem", skillTag.get("problem").getAsString());
                    tags.put("step", skillTag.get("step").getAsString());
                    JsonArray skills = skillTag.getAsJsonArray("skills");
                    skills.forEach(s -> {
                        JsonObject sk = s.getAsJsonObject();
                        int sIndex = 0;
                        for (Map.Entry<String, JsonElement> en : sk.entrySet()) {
                            tags.put("skill" + (sIndex++), en.getValue().getAsString());
                        }
                    });
                });

            } catch (Exception e) {
                log.error(e.getLocalizedMessage(), e);
            }
        }

        int maxSkills = 0;
        for (Map<String, String> tags : problemTags) {
            if (tags.size() - 3 > maxSkills) {
                maxSkills = tags.size() - 3;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(pkg.getId()).append("\t").append(pkg.getVersion()).append("\n");
        sb.append("Resource").append("\t").append("Problem").append("\t").append("Step");
        for (int x = 0; x < maxSkills; x++) {
            sb.append("\t").append("Skill" + (x + 1));
        }
        sb.append("\n");

        JsonWrapper skillsIndexWrapper = pkg.getSkillsIndex();
        JsonObject skillsIndex = skillsIndexWrapper == null ? new JsonObject()
                : skillsIndexWrapper.getJsonObject().getAsJsonObject();

        for (Map<String, String> tags : problemTags) {
            sb.append(tags.get("resource")).append("\t");
            sb.append(tags.get("problem")).append("\t");
            sb.append(tags.get("step")).append("\t");

            Set<Map.Entry<String, String>> entries = tags.entrySet();
            for (Map.Entry<String, String> t : entries) {
                if (t.getKey().startsWith("skill") && skillsIndex.has(t.getValue())) {
                    sb.append(t.getValue()).append("\t");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private void skillModelTag(Map<SkillTagLevel, List<Edge>> probRefs, Edge e, String resourceId, String problem,
                               String step) {
        SkillTagLevel skillTagLevel = new SkillTagLevel(resourceId, problem, step);
        List<Edge> edges = probRefs.get(skillTagLevel);
        if (edges == null) {
            edges = new ArrayList<>();
            probRefs.put(skillTagLevel, edges);
        }
        edges.add(e);
    }

    class SkillTagLevel {
        String resource;
        String problem;
        String step;

        public SkillTagLevel(String resource, String problem, String step) {
            this.resource = resource;
            this.problem = problem;
            this.step = step;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            SkillTagLevel that = (SkillTagLevel) o;
            return Objects.equals(resource, that.resource) && Objects.equals(problem, that.problem)
                    && Objects.equals(step, that.step);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resource, problem, step);
        }
    }

    private String objectivesTabSeparated(ContentPackage pkg, JsonObject objectivesIndex, List<Edge> skillEdges) {
        Set<Map.Entry<String, JsonElement>> objectiveEntries = objectivesIndex.entrySet();
        StringBuilder sb = new StringBuilder();
        sb.append(pkg.getId()).append("\t").append(pkg.getVersion()).append("\n");

        List<ObSkill> obLoad = new ArrayList<>();
        objectiveEntries.stream().forEach(e -> {
            List<Edge> skillsRef = new ArrayList<>();
            skillEdges.stream().forEach(s -> {
                if (!s.getSourceType().equals("x-oli-objective")) {
                    return;
                }
                JsonObject obMeta = s.getMetadata().getJsonObject().getAsJsonObject();
                if (obMeta.has("obIdref") && obMeta.get("obIdref").getAsString().equals(e.getKey())) {
                    skillsRef.add(s);
                }
            });
            if(!skillsRef.isEmpty()) {
                obLoad.add(new ObSkill(e, skillsRef));
            }
        });

        int maxSkills1 = 0;
        for (ObSkill os : obLoad) {
            if (os.skillsRef.size() > maxSkills1) {
                maxSkills1 = os.skillsRef.size();
            }
        }
        final int maxSkills = maxSkills1;
        sb.append("Learning Objective").append("\t").append("Low Opportunity").append("\t").append("Min. Practice")
                .append("\t").append("Low Cutoff").append("\t").append("Moderate Cutoff");
        for (int x = 0; x < maxSkills; x++) {
            sb.append("\t").append("Skill" + (x + 1));
        }
        sb.append("\n");

        JsonWrapper skillsIndexWrapper = pkg.getSkillsIndex();
        JsonObject skillsIndex = skillsIndexWrapper == null ? new JsonObject()
                : skillsIndexWrapper.getJsonObject().getAsJsonObject();

        obLoad.stream().forEach(e -> {
            JsonObject objective = e.objectivePayload.getValue().getAsJsonObject();
            String highMastery = "2.50";
            String minPractice = "2";
            String mediumMastery = "1.50";
            String lowOpportunity = "false";
            if (objective.has("parameters")) {
                JsonObject parameters = objective.get("parameters").getAsJsonObject();
                highMastery = parameters.has("high_mastery") ? parameters.get("high_mastery").getAsString() : "2.50";
                minPractice = parameters.has("min_practice") ? parameters.get("min_practice").getAsString() : "2";
                mediumMastery = parameters.has("medium_mastery") ? parameters.get("medium_mastery").getAsString()
                        : "1.50";
                lowOpportunity = parameters.has("low_opportunity") ? parameters.get("low_opportunity").getAsString()
                        : "false";
            }
            sb.append(e.objectivePayload.getKey()).append("\t").append(lowOpportunity).append("\t").append(minPractice)
                    .append("\t").append(mediumMastery).append("\t").append(highMastery);
            int skillCnt = 0;
            for (Edge edge : e.skillsRef) {
                String[] split = edge.getDestinationId().split(":");
                String skill = split[split.length - 1];
                if (skillsIndex.has(skill)) {
                    skillCnt++;
                    sb.append("\t").append(skill);
                }
            }
            for (int x = skillCnt; x < maxSkills; x++) {
                sb.append("\t").append(" ");
            }
            sb.append("\n");
        });
        return sb.toString();
    }

    class ObSkill {
        Map.Entry<String, JsonElement> objectivePayload;
        List<Edge> skillsRef;

        ObSkill(Map.Entry<String, JsonElement> objectivePayload, List<Edge> skillsRef) {
            this.objectivePayload = objectivePayload;
            this.skillsRef = skillsRef;
        }
    }

    private List<Edge> skillEdges(ContentPackage pkg) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Edge> criteria = cb.createQuery(Edge.class);
        Root<Edge> edgeRoot = criteria.from(Edge.class);
        criteria.select(edgeRoot).where(cb.and(cb.equal(edgeRoot.get("destinationType"), "x-oli-skill"),
                cb.equal(edgeRoot.get("contentPackage").get("guid"), pkg.getGuid())));
        return em.createQuery(criteria).getResultList();
    }

    private List<Edge> poolRefEdges(ContentPackage pkg) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Edge> criteria = cb.createQuery(Edge.class);
        Root<Edge> edgeRoot = criteria.from(Edge.class);
        criteria.select(edgeRoot).where(cb.and(cb.equal(edgeRoot.get("referenceType"), "pool_ref"),
                cb.equal(edgeRoot.get("contentPackage").get("guid"), pkg.getGuid())));
        return em.createQuery(criteria).getResultList();
    }

    private String skillsTabSeparated(JsonObject skillsIndex) {

        Set<Map.Entry<String, JsonElement>> skillEntries = skillsIndex.entrySet();
        StringBuilder sb = new StringBuilder();
        sb.append("Skill").append("\t").append("Title").append("\t").append("p").append("\t").append("gamma0")
                .append("\t").append("gamma1").append("\t").append("lambda0").append("\n");
        skillEntries.stream().forEach(e -> {
            JsonObject skillPayload = e.getValue().getAsJsonObject();
            String p = "0.70";
            String gamma0 = "0.70";
            String gamma1 = "0.70";
            String lambda0 = "1.00";

            if (skillPayload.has("parameters")) {
                JsonObject parameters = skillPayload.get("parameters").getAsJsonObject();
                p = parameters.has("p") ? parameters.get("p").getAsString() : "0.70";
                gamma0 = parameters.has("gamma0") ? parameters.get("gamma0").getAsString() : "0.70";
                gamma1 = parameters.has("gamma1") ? parameters.get("gamma1").getAsString() : "0.70";
                lambda0 = parameters.has("lambda0") ? parameters.get("lambda0").getAsString() : "1.00";
            }

            sb.append(e.getKey()).append("\t").append(skillPayload.get("skillText").getAsString()).append("\t")
                    .append(p).append("\t").append(gamma0).append("\t").append(gamma1).append("\t").append(lambda0)
                    .append("\n");
        });
        return sb.toString();
    }

    private ResourceJson getResourceJson(ContentPackage pkg, String fileName, Map<String, ResourceJson> resourceMapById,
                                         String resourceId, Set<String> rscTypeFilter) throws ContentServiceException {
        ResourceJson resourceById = null;
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Resource> criteria = cb.createQuery(Resource.class);
        Root<Resource> edgeRoot = criteria.from(Resource.class);
        criteria.select(edgeRoot).where(cb.and(cb.equal(edgeRoot.get("id"), resourceId),
                cb.equal(edgeRoot.get("contentPackage").get("guid"), pkg.getGuid())));
        List<Resource> resultList = em.createQuery(criteria).getResultList();

        for (Resource e : resultList) {
            if (rscTypeFilter == null || rscTypeFilter.contains(e.getType())) {
                ResourceJson resourceJson = new ResourceJson();
                String path = e.getFileNode().getVolumeLocation() + File.separator + e.getFileNode().getPathTo();
                boolean jsonCapable = configuration.get().getResourceTypeById(e.getType()).get("jsonCapable")
                        .getAsBoolean();
                if (jsonCapable) {
                    if (e.getLastRevision() != null) {
                        resourceJson.json = e.getLastRevision().getBody().getJsonPayload().getJsonObject()
                                .getAsJsonObject();
                    } else {
                        try (FileReader fileReader = new FileReader(path)) {
                            JsonParser parser = new JsonParser();
                            JsonElement jsonElement = parser.parse(fileReader);
                            resourceJson.json = jsonElement.getAsJsonObject();
                        } catch (Exception ex) {
                            log.error(ex.getLocalizedMessage());
                            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, null,
                                    ex.getLocalizedMessage());
                        }
                    }
                } else {
                    if (e.getLastRevision() != null) {
                        resourceJson.xml = e.getLastRevision().getBody().getXmlPayload();
                    } else {
                        try {
                            resourceJson.xml = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
                        } catch (IOException e1) {
                            log.error(e1.getLocalizedMessage());
                            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, null,
                                    e1.getLocalizedMessage());
                        }
                    }
                }
                resourceJson.resource = e;
                resourceById = resourceJson;
                resourceMapById.put(e.getId(), resourceJson);
                break;
            } else {
                String message = fileName + " content resource type '" + e.getType()
                        + "' does yet support skill skill tagging";
                log.error(message);
                throw new ContentServiceException(message);

            }
        }
        return resourceById;
    }

    private void parseSkillParameters(JsonObject params, String fileName, int n, int start, String[] fields)
            throws ContentServiceException {
        // p
        String field1 = fields[start + 1].trim();
        if (!"".equals(field1)) {
            try {
                params.addProperty("@p", new BigDecimal(field1));
            } catch (NumberFormatException x) {
                String message = fileName + ": Parameter p is not a number: " + field1 + " line " + n;
                log.error("line " + n + ": " + message);
                throw new ContentServiceException(message, x);
            }
        }

        // gamma0
        String field2 = fields[start + 2].trim();
        if (!"".equals(field2)) {
            try {
                params.addProperty("@gamma0", new BigDecimal(field2));
            } catch (NumberFormatException x) {
                String message = fileName + ": Parameter gamma0 is not a number: " + field2;
                log.error("line " + n + ": " + message);
                throw new ContentServiceException(message, x);
            }
        }

        // gamma1
        String field3 = fields[start + 3].trim();
        if (!"".equals(field3)) {
            try {
                params.addProperty("@gamma1", new BigDecimal(field3));
            } catch (NumberFormatException x) {
                String message = fileName + ": Parameter gamma1 is not a number: " + field3;
                log.error("line " + n + ": " + message);
                throw new ContentServiceException(message, x);
            }
        }

        // lambda0
        String field4 = fields[start + 4].trim();
        if (!"".equals(field4)) {
            try {
                params.addProperty("@lambda0", new BigDecimal(field4));
            } catch (NumberFormatException x) {
                String message = fileName + ": Parameter lambda0 is not a number: " + field4;
                log.error("line " + n + ": " + message);
                throw new ContentServiceException(message, x);
            }
        }
    }

    private Optional<JsonElement> locateParentObjectById(JsonElement content, String id) {
        if (content.isJsonArray()) {
            for (JsonElement e : content.getAsJsonArray()) {
                Optional<JsonElement> found = locateParentObjectById(e, id);
                if (found.isPresent()) {
                    return found;
                }
            }
        } else if (content.isJsonObject()) {
            if (content.getAsJsonObject().has("@id") && content.getAsJsonObject().get("@id").getAsString().equals(id)) {
                return Optional.of(content);
            } else {
                for (Map.Entry<String, JsonElement> e : content.getAsJsonObject().entrySet()) {
                    Optional<JsonElement> found = locateParentObjectById(e.getValue(), id);
                    if (found.isPresent()) {
                        return found;
                    }
                }
            }
        }
        return Optional.empty();
    }

    class ResourceJson {
        Resource resource;
        JsonObject json;
        String xml;
    }
}
