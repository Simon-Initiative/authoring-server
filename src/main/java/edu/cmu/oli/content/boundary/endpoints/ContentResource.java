package edu.cmu.oli.content.boundary.endpoints;

import com.airhacks.porcupine.execution.boundary.Dedicated;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.boundary.ExceptionHandler;
import edu.cmu.oli.content.boundary.managers.ContentResourceManager;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.security.AppSecurityContext;
import edu.cmu.oli.content.security.AppSecurityContextFactory;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * @author Raphael Gachuhi
 */

@Stateless
@Path("/")
public class ContentResource {

    public static final String PARAMETERS_MISSING = "Parameters missing";

    @Inject
    @Logging
    Logger log;

    @Inject
    private ContentResourceManager pm;

    @Inject
    @Dedicated("resourcesApiExecutor")
    ExecutorService mes;

    @Inject
    @ConfigurationCache
    Instance<Configurations> configuration;

    @Context
    private HttpServletRequest httpServletRequest;

    @Inject
    AppSecurityContextFactory appSecurityContextFactory;

    @GET
    @Path("v1/{packageIdOrGuid}/resources/{resourceId}")
    public void fetchResource(@Suspended AsyncResponse response, @PathParam("packageIdOrGuid") String packageIdOrGuid,
            @PathParam("resourceId") String resourceId) {
        if (packageIdOrGuid == null || resourceId == null) {
            response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING, Response.Status.BAD_REQUEST));
            return;
        }
        AppSecurityContext appSecurityContext = appSecurityContextFactory.extractSecurityContext(httpServletRequest);
        CompletableFuture.supplyAsync(() -> pm.fetchResource(appSecurityContext, packageIdOrGuid, resourceId), mes)
                .thenApply(this::toResponse).exceptionally(ExceptionHandler::handleExceptions)
                .thenAccept(response::resume);
    }

    @POST
    @Path("v1/{packageIdOrGuid}/resources")
    public void createResource(@Suspended AsyncResponse response, @PathParam("packageIdOrGuid") String packageIdOrGuid,
            @QueryParam("resourceType") String resourceType, JsonObject content) {
        if (packageIdOrGuid == null || resourceType == null || content == null) {
            response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING, Response.Status.BAD_REQUEST));
            return;
        }
        AppSecurityContext appSecurityContext = appSecurityContextFactory.extractSecurityContext(httpServletRequest);
        JsonParser jsonParser = new JsonParser();
        com.google.gson.JsonObject jsonObject = jsonParser.parse(AppUtils.toString(content)).getAsJsonObject();
        CompletableFuture
                .supplyAsync(() -> createResource(appSecurityContext, packageIdOrGuid, resourceType, jsonObject, 0),
                        mes)
                .thenAccept(response::resume);
    }

    @POST
    @Path("v1/{packageIdOrGuid}/resources/bulk")
    public void fetchResourcesByFilter(@Suspended AsyncResponse response,
            @PathParam("packageIdOrGuid") String packageIdOrGuid, @QueryParam("action") String action, JsonArray body) {
        if (packageIdOrGuid == null || action == null || body == null) {
            response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING, Response.Status.BAD_REQUEST));
            return;
        }
        AppSecurityContext appSecurityContext = appSecurityContextFactory.extractSecurityContext(httpServletRequest);
        JsonParser jsonParser = new JsonParser();
        CompletableFuture
                .supplyAsync(() -> pm.fetchResourcesByFilter(appSecurityContext, packageIdOrGuid, action,
                        jsonParser.parse(AppUtils.toString(body)).getAsJsonArray()), mes)
                .thenApply(this::toResponse).exceptionally(ExceptionHandler::handleExceptions)
                .thenAccept(response::resume);
    }

    @PUT
    @Path("v1/{packageIdOrGuid}/resources/{resourceId}")
    public void updateResource(@Suspended AsyncResponse response, @PathParam("packageIdOrGuid") String packageIdOrGuid,
            @PathParam("resourceId") String resourceId, JsonObject body) {
        if (packageIdOrGuid == null || resourceId == null || body == null) {
            response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING, Response.Status.BAD_REQUEST));
            return;
        }
        AppSecurityContext appSecurityContext = appSecurityContextFactory.extractSecurityContext(httpServletRequest);
        JsonParser jsonParser = new JsonParser();
        com.google.gson.JsonObject jsonObject = jsonParser.parse(AppUtils.toString(body)).getAsJsonObject();
        CompletableFuture
                .supplyAsync(() -> updateResource(appSecurityContext, packageIdOrGuid, resourceId, jsonObject, 0), mes)
                .thenAccept(response::resume);
    }

    @PUT
    @Path("v1/{packageIdOrGuid}/resources/{resourceId}/{baseRevisionId}/{nextRevisionId}")
    public void updateResource(@Suspended AsyncResponse response, @PathParam("packageIdOrGuid") String packageIdOrGuid,
            @PathParam("resourceId") String resourceId, @PathParam("baseRevisionId") String baseRevisionId,
            @PathParam("nextRevisionId") String nextRevisionId, JsonObject body) {
        if (packageIdOrGuid == null || resourceId == null || baseRevisionId == null || nextRevisionId == null
                || body == null) {
            response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING, Response.Status.BAD_REQUEST));
            return;
        }
        AppSecurityContext appSecurityContext = appSecurityContextFactory.extractSecurityContext(httpServletRequest);
        JsonParser jsonParser = new JsonParser();
        com.google.gson.JsonObject jsonObject = jsonParser.parse(AppUtils.toString(body)).getAsJsonObject();
        CompletableFuture.supplyAsync(() -> updateRevisionBasedResource(appSecurityContext, packageIdOrGuid, resourceId,
                baseRevisionId, nextRevisionId, jsonObject, 0), mes).thenAccept(response::resume);
    }

    @DELETE
    @Path("v1/{packageIdOrGuid}/resources/{resourceId}")
    public void deleteResource(@Suspended AsyncResponse response, @PathParam("packageIdOrGuid") String packageIdOrGuid,
            @PathParam("resourceId") String resourceId) {
        if (packageIdOrGuid == null || resourceId == null) {
            response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING, Response.Status.BAD_REQUEST));
            return;
        }
        AppSecurityContext appSecurityContext = appSecurityContextFactory.extractSecurityContext(httpServletRequest);
        CompletableFuture.supplyAsync(() -> softDelete(appSecurityContext, packageIdOrGuid, resourceId, 0), mes)
                .thenAccept(response::resume);
    }

    @GET
    @Path("v1/{packageIdOrGuid}/resources/edges/{resourceId}")
    public void fetchResourceEdges(@Suspended AsyncResponse response,
            @PathParam("packageIdOrGuid") String packageIdOrGuid, @PathParam("resourceId") String resourceId) {
        if (packageIdOrGuid == null || resourceId == null) {
            response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING, Response.Status.BAD_REQUEST));
            return;
        }
        AppSecurityContext appSecurityContext = appSecurityContextFactory.extractSecurityContext(httpServletRequest);
        CompletableFuture.supplyAsync(() -> pm.fetchResourceEdges(appSecurityContext, packageIdOrGuid, resourceId), mes)
                .thenApply(this::toResponse).exceptionally(ExceptionHandler::handleExceptions)
                .thenAccept(response::resume);
    }

    private Response toResponse(JsonElement resourceJson) {
        Gson gson = AppUtils.gsonBuilder().serializeNulls().create();
        return Response.status(Response.Status.OK).entity(gson.toJson(resourceJson)).type(MediaType.APPLICATION_JSON)
                .build();
    }

    private Response createResource(AppSecurityContext appSecurityContext, String packageIdOrGuid, String resourceType,
            com.google.gson.JsonObject jsonObject, int retryCounter) {
        try {
            JsonElement resource = pm.createResource(appSecurityContext, packageIdOrGuid, resourceType, jsonObject);
            Gson gson = AppUtils.gsonBuilder().serializeNulls().create();
            return Response.status(Response.Status.OK).entity(gson.toJson(resource)).type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (Throwable t) {
            if (ExceptionHandler.checkForDBLockExceptions(t)
                    && retryCounter < configuration.get().getTransactionRetrys()) {
                retryCounter++;
                log.error("LockResource acquisition exception detected on resource create; retrying transaction");
                return createResource(appSecurityContext, packageIdOrGuid, resourceType, jsonObject, retryCounter);
            }
            return ExceptionHandler.handleExceptions(t);
        }
    }

    private Response updateResource(AppSecurityContext appSecurityContext, String packageIdOrGuid, String resourceId,
            com.google.gson.JsonObject jsonObject, int retryCounter) {
        try {
            JsonElement resourceJson = pm.updateResource(appSecurityContext, packageIdOrGuid, resourceId, jsonObject);
            Gson gson = AppUtils.gsonBuilder().serializeNulls().create();
            return Response.status(Response.Status.OK).entity(gson.toJson(resourceJson))
                    .type(MediaType.APPLICATION_JSON).build();
        } catch (Throwable t) {
            if (ExceptionHandler.checkForDBLockExceptions(t)
                    && retryCounter < configuration.get().getTransactionRetrys()) {
                log.error("LockResource acquisition exception detected on resource update; retrying transaction");
                return updateResource(appSecurityContext, packageIdOrGuid, resourceId, jsonObject, ++retryCounter);
            }
            return ExceptionHandler.handleExceptions(t);
        }
    }

    private Response updateRevisionBasedResource(AppSecurityContext appSecurityContext, String packageIdOrGuid,
            String resourceId, String baseRevisionId, String nextRevision, com.google.gson.JsonObject jsonObject,
            int retryCounter) {
        try {
            JsonElement resourceJson = pm.updateResource(appSecurityContext, packageIdOrGuid, resourceId,
                    baseRevisionId, nextRevision, jsonObject);
            Gson gson = AppUtils.gsonBuilder().serializeNulls().create();
            return Response.status(Response.Status.OK).entity(gson.toJson(resourceJson))
                    .type(MediaType.APPLICATION_JSON).build();
        } catch (Throwable t) {
            if (ExceptionHandler.checkForDBLockExceptions(t)
                    && retryCounter < configuration.get().getTransactionRetrys()) {
                log.error("LockResource acquisition exception detected on resource update; retrying transaction");
                return updateRevisionBasedResource(appSecurityContext, packageIdOrGuid, resourceId, baseRevisionId,
                        nextRevision, jsonObject, ++retryCounter);
            }
            return ExceptionHandler.handleExceptions(t);
        }
    }

    private Response softDelete(AppSecurityContext appSecurityContext, String packageIdOrGuid, String resourceId,
            int retryCounter) {
        try {
            JsonElement resourceJson = pm.softDelete(appSecurityContext, packageIdOrGuid, resourceId);
            Gson gson = AppUtils.gsonBuilder().serializeNulls().create();
            return Response.status(Response.Status.OK).entity(gson.toJson(resourceJson))
                    .type(MediaType.APPLICATION_JSON).build();
        } catch (Throwable t) {
            if (ExceptionHandler.checkForDBLockExceptions(t)
                    && retryCounter < configuration.get().getTransactionRetrys()) {
                log.error("LockResource acquisition exception detected on resource delete; retrying transaction");
                return softDelete(appSecurityContext, packageIdOrGuid, resourceId, ++retryCounter);
            }
            return ExceptionHandler.handleExceptions(t);
        }
    }

    // private <T> CompletableFuture<T> withRetry(Supplier<CompletableFuture<T>>
    // asyncRequest) {
    // CompletableFuture<T> result = new CompletableFuture<>();
    // attemptOperation(asyncRequest, result);
    // return result;
    // }
    //
    // private <T> void attemptOperation(Supplier<CompletableFuture<T>>
    // asyncRequest, CompletableFuture<T> result) {
    // CompletableFuture<T> f = asyncRequest.get();
    // f.handleAsync((val, throwable) -> {
    // if (throwable != null) {
    // if (ExceptionHandler.checkForDBLockExceptions(throwable)) {
    // log.trace("Retrying");
    // attemptOperation(asyncRequest, result);
    // } else {
    // result.completeExceptionally(throwable);
    // }
    // } else
    // result.complete(val);
    // return null;
    // }, mes);
    // }

}
