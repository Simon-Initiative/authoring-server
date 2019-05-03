package edu.cmu.oli.content.boundary.managers;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.DirectoryUtils;
import edu.cmu.oli.content.ResourceException;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.controllers.EdgesController;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.entities.*;
import edu.cmu.oli.content.security.AppSecurityContext;
import edu.cmu.oli.content.security.AppSecurityController;
import edu.cmu.oli.content.security.Scopes;
import edu.cmu.oli.content.security.Secure;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.ws.rs.core.Response;
import java.util.*;

import static edu.cmu.oli.content.security.Roles.ADMIN;
import static edu.cmu.oli.content.security.Roles.CONTENT_DEVELOPER;

/**
 * @author Raphael Gachuhi
 */
@Stateless
public class EdgeResourceManager {

    @Inject
    @Logging
    Logger log;

    @PersistenceContext
    EntityManager em;

    @Inject
    @Secure
    AppSecurityController securityManager;

    @Inject
    EdgesController edgesController;

    @Inject
    DirectoryUtils directoryUtils;

    @Inject
    @ConfigurationCache
    Instance<Configurations> config;

    public JsonElement listEdges(AppSecurityContext session, String packageId,
                                 String relationship,
                                 String purpose,
                                 List<String> sourceIds,
                                 String sourceType,
                                 List<String> destinationIds,
                                 String destinationType,
                                 String referenceType,
                                 String status) {
        securityManager.authorize(session,
                Arrays.asList(ADMIN, CONTENT_DEVELOPER),
                packageId, "name=" + packageId, Arrays.asList(Scopes.VIEW_MATERIAL_ACTION));

        ContentPackage pkg = findContentPackage(packageId);

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Edge> criteria = cb.createQuery(Edge.class);
        Root<Edge> edgeRoot = criteria.from(Edge.class);

        Set<Predicate> predicates = new HashSet<>();
        predicates.add(cb.equal(edgeRoot.get("contentPackage").get("guid"), packageId));

        if (relationship != null) {
            EdgeType enumType = EdgeType.valueOf(relationship.toUpperCase());
            if (enumType == null) {
                String message = "Parameter 'relationship' value [" + relationship + "] did not match expected ENUM type";
                log.error(message);
                throw new ResourceException(Response.Status.BAD_REQUEST, packageId, message);
            }
            predicates.add(cb.equal(edgeRoot.get("relationship"), enumType));
        }
        if (purpose != null) {
            PurposeType enumType = PurposeType.valueOf(relationship.toUpperCase());
            if (enumType == null) {
                String message = "Parameter 'purpose' value [" + purpose + "] did not match expected ENUM type";
                log.error(message);
                throw new ResourceException(Response.Status.BAD_REQUEST, packageId, message);
            }
            predicates.add(cb.equal(edgeRoot.get("purpose"), enumType));
        }
        if (sourceIds != null) {
            // create an "or" predicate of all source ids
            final Predicate sourceIdsPredicate = sourceIds.stream()
                .map(id -> pkg.getId() + ":" + pkg.getVersion() + ":" + id)
                .reduce(
                    cb.or(),
                    (acc, id) -> cb.or(acc, cb.equal(edgeRoot.get("sourceId"), id)),
                    (a, b) -> cb.or(a, b));
              
            predicates.add(sourceIdsPredicate);
        }
        if (sourceType != null) {
            predicates.add(cb.equal(edgeRoot.get("sourceType"), sourceType));
        }
        if (destinationIds != null) {
            // create an "or" predicate of all destination ids
            final Predicate destinationIdsPredicate = destinationIds.stream()
                .map(id -> pkg.getId() + ":" + pkg.getVersion() + ":" + id)
                .reduce(
                    cb.or(),
                    (acc, id) -> cb.or(acc, cb.equal(edgeRoot.get("destinationId"), id)),
                    (a, b) -> cb.or(a, b));
              
            predicates.add(destinationIdsPredicate);
        }
        if (destinationType != null) {
            predicates.add(cb.equal(edgeRoot.get("destinationType"), destinationType));
        }
        if (referenceType != null) {
            predicates.add(cb.equal(edgeRoot.get("referenceType"), referenceType));
        }
        if (status != null) {
            EdgeStatus enumType = EdgeStatus.valueOf(relationship.toUpperCase());
            if (enumType == null) {
                String message = "Parameter 'status' value [" + status + "] did not match expected ENUM type";
                log.error(message);
                throw new ResourceException(Response.Status.BAD_REQUEST, packageId, message);
            }
            predicates.add(cb.equal(edgeRoot.get("status"), enumType));
        }

        criteria.select(edgeRoot).where(cb.and(predicates.toArray(new Predicate[predicates.size()])));

        List<Edge> edges = em.createQuery(criteria).getResultList();

        JsonElement edgesJson = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls()
                .create().toJsonTree(edges, new TypeToken<ArrayList<Edge>>() {
                }.getType());
        return edgesJson;
    }

    private ContentPackage findContentPackage(String packageId) {
        ContentPackage contentPackage = null;
        try {
            contentPackage = em.find(ContentPackage.class, packageId);
            if (contentPackage == null) {
                String message = "Error: package requested was not found " + packageId;
                log.error(message);
                throw new ResourceException(Response.Status.NOT_FOUND, packageId, message);
            }
        } catch (IllegalArgumentException e) {
            String message = "Server Error while locating package " + packageId;
            log.error(message);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, packageId, message);
        }
        return contentPackage;
    }
}
