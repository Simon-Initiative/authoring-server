package edu.cmu.oli.content.boundary.endpoints;

import edu.cmu.oli.content.configuration.DedicatedExecutor;
import com.google.gson.JsonElement;

import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.boundary.ExceptionHandler;
import edu.cmu.oli.content.boundary.managers.EdgeResourceManager;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.entities.Edge;
import edu.cmu.oli.content.security.AppSecurityContext;
import edu.cmu.oli.content.security.AppSecurityContextFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * @author Raphael Gachuhi
 */

@Stateless
@Path("/")
public class EdgeResource {

    public static final String PARAMETERS_MISSING = "Parameters missing";

    @Inject
    @Logging
    Logger log;

    @Inject
    EdgeResourceManager wcm;

    @Inject
    @DedicatedExecutor("edgesApiExecutor")
    ExecutorService mes;

    @Inject
    @ConfigurationCache
    Instance<Configurations> config;

    @Context
    private HttpServletRequest httpServletRequest;

    @Inject
    AppSecurityContextFactory appSecurityContextFactory;

    @Operation(summary = "Get a list of filtered edges", description = "Get a list of edges filtered by the request's parameter values", responses = {
            @ApiResponse(responseCode = "200", description = "Successful response for the edges request", content = {
                    @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Edge.class))) }),
            @ApiResponse(responseCode = "400", description = "Invalid request information supplied"),
            @ApiResponse(responseCode = "404", description = "Package not found"),
            @ApiResponse(responseCode = "404", description = "Package not found") })
    @GET
    @Path("v1/{packageIdOrGuid}/edges")
    public void listEdges(@Suspended AsyncResponse response, @PathParam("packageIdOrGuid") String packageIdOrGuid,
            @QueryParam("relationship") String relationship, @QueryParam("purpose") String purpose,
            @QueryParam("sourceId") String sourceId, @QueryParam("sourceType") String sourceType,
            @QueryParam("destinationId") String destinationId, @QueryParam("destinationType") String destinationType,
            @QueryParam("referenceType") String referenceType, @QueryParam("status") String status) {
        if (packageIdOrGuid == null) {
            response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING, Response.Status.BAD_REQUEST));
            return;
        }

        // convert sourceId and destinationId to collections if they are present
        List<String> sourceIds = null;
        if (sourceId != null) {
            sourceIds = new ArrayList<String>();
            sourceIds.add(sourceId);
        }
        List<String> destinationIds = null;
        if (destinationId != null) {
            destinationIds = new ArrayList<String>();
            destinationIds.add(destinationId);
        }

        // variables must be final for lambda use
        final List<String> finalSourceIds = sourceIds;
        final List<String> finalDestinationIds = destinationIds;

        AppSecurityContext appSecurityContext = appSecurityContextFactory.extractSecurityContext(httpServletRequest);
        CompletableFuture
                .supplyAsync(() -> wcm.listEdges(appSecurityContext, packageIdOrGuid, relationship, purpose,
                        finalSourceIds, sourceType, finalDestinationIds, destinationType, referenceType, status), mes)
                .thenApply(this::toResponse).exceptionally(ExceptionHandler::handleExceptions)
                .thenAccept(response::resume);
    }

    public class EdgesByIdsRequestBody {
        List<String> sourceIds;
        List<String> destinationIds;
    }

    @Operation(summary = "Get a list of filtered edges using multiple ids", description = "Get a list of edges filtered by the request's parameter values", responses = {
            @ApiResponse(responseCode = "200", description = "Successful response for the edges request", content = {
                    @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Edge.class))) }),
            @ApiResponse(responseCode = "400", description = "Invalid request information supplied"),
            @ApiResponse(responseCode = "404", description = "Package not found"),
            @ApiResponse(responseCode = "404", description = "Package not found") })
    @POST
    @Path("v1/{packageIdOrGuid}/edges/by-ids")
    @Consumes("application/json")
    public void edgesByIds(@Suspended AsyncResponse response, @PathParam("packageIdOrGuid") String packageIdOrGuid,
            @QueryParam("relationship") String relationship, @QueryParam("purpose") String purpose,
            @QueryParam("sourceType") String sourceType, @QueryParam("destinationType") String destinationType,
            @QueryParam("referenceType") String referenceType, @QueryParam("status") String status,
            @Parameter(description = "Json payload example {'sourceIds': ['id'], destinationIds: ['id'] }") JsonObject payload) {

        EdgesByIdsRequestBody body = AppUtils.toClassObject(payload, EdgesByIdsRequestBody.class);

        if (packageIdOrGuid == null) {
            response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING, Response.Status.BAD_REQUEST));
            return;
        }
        AppSecurityContext appSecurityContext = appSecurityContextFactory.extractSecurityContext(httpServletRequest);
        CompletableFuture
                .supplyAsync(() -> wcm.listEdges(appSecurityContext, packageIdOrGuid, relationship, purpose,
                        body.sourceIds, sourceType, body.destinationIds, destinationType, referenceType, status), mes)
                .thenApply(this::toResponse).exceptionally(ExceptionHandler::handleExceptions)
                .thenAccept(response::resume);
    }

    private Response toResponse(JsonElement element) {
        return Response.status(Response.Status.OK).entity(AppUtils.gsonBuilder().create().toJson(element))
                .type(MediaType.APPLICATION_JSON).build();
    }
}
