package edu.cmu.oli.content.controllers;

import com.google.gson.JsonObject;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.*;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.naming.InitialContext;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Raphael Gachuhi
 */
@Stateless
public class EdgesController {

    @Inject
    @Logging
    Logger log;

    @PersistenceContext
    EntityManager em;

    @Inject
    @ConfigurationCache
    Instance<Configurations> config;

    public void validateAllEdges(ContentPackage pkg) {
        Set<Edge> edges = pkg.getEdges();
        JsonWrapper objectivesIndex = pkg.getObjectivesIndex();
        JsonWrapper skillsIndex = pkg.getSkillsIndex();
        List<Resource> resources = pkg.getResources();
        List<WebContent> webContents = pkg.getWebContents();
        Set<String> fileNodeFromPath = new HashSet<>();
        webContents.forEach(webContent -> {
            fileNodeFromPath.add(pkg.getId() + ":" + pkg.getVersion() + ":" + webContent.getFileNode().getPathFrom());
        });

        // For faster lookup
        Map<String, Resource> indexResourceById = new HashMap<>();
        resources.forEach(r -> {
            indexResourceById.put(r.getId(), r);
        });

        edges.forEach(edge -> {
            if (edge.getReferenceType().equals("objref") && objectivesIndex != null
                    && objectivesIndex.getJsonObject() != null) {
                JsonObject index = (JsonObject) objectivesIndex.getJsonObject();
                String destinationId = edge.getDestinationId();
                if (index.has(destinationId.substring(destinationId.lastIndexOf(":") + 1))) {
                    edge.setStatus(EdgeStatus.DESTINATION_PRESENT);
                }
            } else if ((edge.getReferenceType().equals("skillref") || edge.getReferenceType().equals("concept")) && skillsIndex != null
                    && skillsIndex.getJsonObject() != null) {
                JsonObject index = (JsonObject) skillsIndex.getJsonObject();
                String destinationId = edge.getDestinationId();
                if (index.has(destinationId.substring(destinationId.lastIndexOf(":") + 1))) {
                    edge.setStatus(EdgeStatus.DESTINATION_PRESENT);
                }
            } else if (fileNodeFromPath.contains(edge.getDestinationId())) {
                edge.setStatus(EdgeStatus.DESTINATION_PRESENT);
            }
            String destinationId = edge.getDestinationId();
            String sourceId = edge.getSourceId();
            Resource destinationResource = indexResourceById.get(destinationId.substring(destinationId.lastIndexOf(":") + 1));
            Resource sourceResource = indexResourceById.get(sourceId.substring(sourceId.lastIndexOf(":") + 1));
            if (destinationResource != null) {
                edge.setStatus(EdgeStatus.DESTINATION_PRESENT);
                edge.setDestinationType(destinationResource.getType());
                // Store resource guid in edge
                JsonObject metadata = (JsonObject) edge.getMetadata().getJsonObject();
                metadata.addProperty("destinationGuid", destinationResource.getGuid());
            }
            if (sourceResource != null) {
                // Update edge with current resource guid
                JsonObject metadata = (JsonObject) edge.getMetadata().getJsonObject();
                metadata.addProperty("sourceGuid", sourceResource.getGuid());
            }
        });
    }

    public void validateEdgesAsync(String pkgGuid) {
        doValidateAsync(pkgGuid, 0);
    }

    private void doValidateAsync(String pkgGuid, Integer retryCnt) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> {
            try {
                EdgesController edgesController = (EdgesController)
                        new InitialContext().lookup("java:global/content-service/EdgesController");
                edgesController.validateNonValidatedEdges(pkgGuid);
                log.info("Edge validation successfull");
            } catch (Throwable e) {
                String message = "Error validating Edges Async for package " + pkgGuid;
                log.debug(message);
                // retry
                if (retryCnt < 20) {
                    log.debug("Retrying edge validation " + retryCnt);
                    doValidateAsync(pkgGuid, new Integer(retryCnt + 1));
                }
            }
        }, 2, TimeUnit.SECONDS);
    }

    public void validateNonValidatedEdges(String pkgGuid) {
        if (pkgGuid == null) {
            log.debug("validateNonValidatedEdges null pkgGuid " + pkgGuid);
            return;
        }
        ContentPackage pkg = em.find(ContentPackage.class, pkgGuid);
        if (pkg == null) {
            String message = "Content Package not found " + pkgGuid;
            log.debug(message);
            return;
        }

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Edge> criteria = cb.createQuery(Edge.class);
        Root<Edge> edgeRoot = criteria.from(Edge.class);
        criteria.select(edgeRoot).where(cb.and(cb.equal(edgeRoot.get("status"), EdgeStatus.NOT_VALIDATED), cb.equal(edgeRoot.get("contentPackage").get("guid"), pkgGuid)));

        List<Edge> notValidatedEdges = em.createQuery(criteria).getResultList();
        if (notValidatedEdges.isEmpty()) {
            return;
        }

        Map<String, List<Edge>> edgesByDestination = new HashMap<>();
        Map<String, List<Edge>> edgesBySource = new HashMap<>();

        notValidatedEdges.forEach(e -> {
            String id = e.getDestinationId();
            id = id.substring(id.lastIndexOf(":") + 1);
            List<Edge> edges = edgesByDestination.get(id);
            if (edges == null) {
                edgesByDestination.put(id, new ArrayList<>());
                edges = edgesByDestination.get(id);
            }
            edges.add(e);
            id = e.getSourceId();
            id = id.substring(id.lastIndexOf(":") + 1);
            edges = edgesBySource.get(id);
            if (edges == null) {
                edgesBySource.put(id, new ArrayList<>());
                edges = edgesBySource.get(id);
            }
            edges.add(e);
        });

        CriteriaQuery<Resource> resourceCriteria = cb.createQuery(Resource.class);
        Root<Resource> resourceRoot = resourceCriteria.from(Resource.class);

        // Fetch all destination resources for edgesByDestination
        resourceCriteria.select(resourceRoot).where(resourceRoot.get("id").in(edgesByDestination.keySet()));
        List<Resource> destinationResourceList = em.createQuery(resourceCriteria).getResultList();

        // Fetch all source resources for edgesBySource
        resourceCriteria.select(resourceRoot).where(resourceRoot.get("id").in(edgesBySource.keySet()));
        List<Resource> sourceResourceList = em.createQuery(resourceCriteria).getResultList();

        destinationResourceList.forEach(resource -> {
            List<Edge> edges = edgesByDestination.get(resource.getId());
            if (edges != null) {
                edges.forEach(edge -> {
                    edge.setStatus(EdgeStatus.DESTINATION_PRESENT);
                    edge.setDestinationType(resource.getType());
                    // Store resource guid in edge
                    JsonObject metadata = (JsonObject) edge.getMetadata().getJsonObject();
                    metadata.addProperty("destinationGuid", resource.getGuid());
                });
            }
        });

        sourceResourceList.forEach(resource -> {
            List<Edge> edges = edgesBySource.get(resource.getId());
            if (edges != null) {
                edges.forEach(edge -> {
                    // Update edge with current resource guid
                    JsonObject metadata = (JsonObject) edge.getMetadata().getJsonObject();
                    metadata.addProperty("sourceGuid", resource.getGuid());
                });
            }
        });

        CriteriaQuery<FileNode> fileNodeCriteria = cb.createQuery(FileNode.class);
        Root<FileNode> fileNodeRoot = fileNodeCriteria.from(FileNode.class);
        fileNodeCriteria.select(fileNodeRoot).where(fileNodeRoot.get("pathFrom").in(edgesByDestination.keySet()));
        List<FileNode> fileNodeList = em.createQuery(fileNodeCriteria).getResultList();
        fileNodeList.forEach(fileNode -> {
            List<Edge> edges = edgesByDestination.get(fileNode.getPathFrom());
            if (edges != null) {
                edges.forEach(edge -> {
                    edge.setStatus(EdgeStatus.DESTINATION_PRESENT);
                });
            }
        });

        JsonWrapper objectivesIndex = pkg.getObjectivesIndex();
        if (objectivesIndex != null && objectivesIndex.getJsonObject() != null) {
            JsonObject objIndex = (JsonObject) objectivesIndex.getJsonObject();
            objIndex.entrySet().forEach(e -> {
                List<Edge> edges = edgesByDestination.get(e.getKey());
                if (edges != null) {
                    edges.forEach(edge -> {
                        edge.setStatus(EdgeStatus.DESTINATION_PRESENT);
                    });
                }
            });
        }

        JsonWrapper skillsIndex = pkg.getSkillsIndex();
        if (skillsIndex != null && skillsIndex.getJsonObject() != null) {
            JsonObject skIndex = (JsonObject) skillsIndex.getJsonObject();
            skIndex.entrySet().forEach(e -> {
                List<Edge> edges = edgesByDestination.get(e.getKey());
                if (edges != null) {
                    edges.forEach(edge -> {
                        edge.setStatus(EdgeStatus.DESTINATION_PRESENT);
                    });
                }
            });
        }
    }

    public List<Edge> edgesForResource(ContentPackage contentPackage, Resource resource,
                                       boolean includeSource, boolean includeDestination) {
        String edgeResourceId = contentPackage.getId() + ":" + contentPackage.getVersion() + ":" + resource.getId();
        Set<String> objectiveIds = objectiveIds(contentPackage, resource);
        Set<String> skillIds = skillIds(contentPackage, resource);

        return fetchEdgesForResource(objectiveIds, skillIds, edgeResourceId, contentPackage.getGuid(), includeSource, includeDestination);
    }

    private List<Edge> fetchEdgesForResource(Set<String> objectiveIds, Set<String> skillIds, String edgeResourceId,
                                             String pkgGuid, boolean includeSource, boolean includeDestination) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Edge> criteria = cb.createQuery(Edge.class);
        Root<Edge> edgeRoot = criteria.from(Edge.class);
        List<Predicate> p = new ArrayList<>();
        if (includeSource) {
            p.add(cb.equal(edgeRoot.get("sourceId"), edgeResourceId));
        }
        if (includeDestination) {
            p.add(cb.equal(edgeRoot.get("destinationId"), edgeResourceId));
            if (!objectiveIds.isEmpty()) {
                p.add(edgeRoot.get("destinationId").in(objectiveIds));
            }
            if (!skillIds.isEmpty()) {
                p.add(edgeRoot.get("destinationId").in(skillIds));
            }
        }
        criteria.select(edgeRoot).where(cb.and(cb.or(p.toArray(new Predicate[p.size()])),
                cb.equal(edgeRoot.get("contentPackage").get("guid"), pkgGuid)));

        return em.createQuery(criteria).getResultList();
    }

    private Set<String> objectiveIds(ContentPackage contentPackage, Resource resource) {
        Set<String> objectiveIds = new HashSet<>();
        JsonWrapper objectivesIndex = contentPackage.getObjectivesIndex();
        if (objectivesIndex != null && objectivesIndex.getJsonObject() != null) {
            JsonObject index = (JsonObject) objectivesIndex.getJsonObject();
            index.entrySet().forEach(e -> {
                JsonObject details = (JsonObject) e.getValue();
                if (details.has("resourceId")
                        && details.get("resourceId").getAsString().equals(resource.getId())) {
                    objectiveIds.add(e.getKey());
                }
            });
        }
        return objectiveIds;
    }

    private Set<String> skillIds(ContentPackage contentPackage, Resource resource) {
        Set<String> skillIds = new HashSet<>();
        JsonWrapper skillsIndex = contentPackage.getSkillsIndex();
        if (skillsIndex != null && skillsIndex.getJsonObject() != null) {
            JsonObject index = (JsonObject) skillsIndex.getJsonObject();
            index.entrySet().forEach(e -> {
                JsonObject details = (JsonObject) e.getValue();
                if (details.has("resourceId")
                        && details.get("resourceId").getAsString().equals(resource.getId())) {
                    skillIds.add(e.getKey());
                }
            });
        }
        return skillIds;
    }

    public void processResourceDelete(ContentPackage contentPackage, Resource resource) {
        Set<String> objectiveIds = objectiveIds(contentPackage, resource);
        Set<String> skillIds = skillIds(contentPackage, resource);

        Set<Edge> edges = contentPackage.getEdges();
        Set<Edge> toRemove = new HashSet<>();
        edges.forEach(edge -> {
            if (edge.getSourceId().endsWith(":" + resource.getId())) {
                toRemove.add(edge);
            } else {
                String destinationId = edge.getDestinationId();
                destinationId = destinationId.substring(destinationId.lastIndexOf(":") + 1);
                if (destinationId.equals(resource.getId()) || objectiveIds.contains(destinationId)
                        || skillIds.contains(destinationId)) {
                    edge.setStatus(EdgeStatus.DESTINATION_MISSING);
                }
            }
        });
        toRemove.forEach(edge -> edges.remove(edge));
    }

    public void processWebContentDelete(ContentPackage contentPackage, FileNode fileNode) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Edge> criteria = cb.createQuery(Edge.class);
        Root<Edge> edgeRoot = criteria.from(Edge.class);
        criteria.select(edgeRoot).where(cb.and(cb.equal(edgeRoot.get("destinationId"), fileNode.getPathFrom()), cb.equal(edgeRoot.get("contentPackage").get("guid"), contentPackage.getGuid())));

        List<Edge> edges = em.createQuery(criteria).getResultList();
        edges.forEach(e -> {
            e.setStatus(EdgeStatus.DESTINATION_MISSING);
        });
    }
}
