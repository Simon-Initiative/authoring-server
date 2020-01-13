package edu.cmu.oli.content.boundary.endpoints;

import edu.cmu.oli.content.configuration.DedicatedExecutor;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.boundary.ExceptionHandler;
import edu.cmu.oli.content.boundary.managers.ContentPackageManager;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.DeployStage;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
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
import java.util.concurrent.TimeUnit;

/**
 * @author Raphael Gachuhi
 */

@Stateless
@Path("/")
public class ContentPackageResource {

        public static final String resourceType = "urn:content-service:resources:content_package";
        public static final String PARAMETERS_MISSING = "Parameters missing";

        @Inject
        @Logging
        Logger log;

        @Inject
        ContentPackageManager cm;

        @Inject
        @ConfigurationCache
        Instance<Configurations> configuration;

        @Inject
        @DedicatedExecutor("packagesResourceApiExecutor")
        ExecutorService executor;

        @Context
        private HttpServletRequest httpServletRequest;

        @Inject
        AppSecurityContextFactory appSecurityContextFactory;

        @Operation(summary = "List all packages", description = "List all course content packages", responses = {
                        @ApiResponse(responseCode = "200", description = "Successful response for the content packages requested", content = {
                                        @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = ContentPackage.class))) }),

                        @ApiResponse(responseCode = "400", description = "Invalid request information supplied"),
                        @ApiResponse(responseCode = "404", description = "Package not found"),
                        @ApiResponse(responseCode = "403", description = "Request not authorized") })
        @GET
        @Path("v1/packages")
        public void all(@Suspended AsyncResponse response) {
                response.setTimeout(2, TimeUnit.SECONDS);
                response.setTimeoutHandler(this::onTimeout);
                AppSecurityContext appSecurityContext = appSecurityContextFactory
                                .extractSecurityContext(httpServletRequest);
                CompletableFuture.supplyAsync(() -> cm.all(appSecurityContext), executor)
                                .thenApply(this::serializeResponse).exceptionally(ExceptionHandler::handleExceptions)
                                .thenAccept(response::resume);
        }

        @Operation(summary = "List all editable packages", description = "List all course content packages editable by current user", responses = {
                        @ApiResponse(responseCode = "200", description = "Successful response for the content packages requested", content = {
                                        @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = ContentPackage.class))) }),

                        @ApiResponse(responseCode = "400", description = "Invalid request information supplied"),
                        @ApiResponse(responseCode = "404", description = "Package not found"),
                        @ApiResponse(responseCode = "403", description = "Request not authorized") })
        @GET
        @Path("v1/packages/editable")
        public void editablePackages(@Suspended AsyncResponse response) {
                AppSecurityContext appSecurityContext = appSecurityContextFactory
                                .extractSecurityContext(httpServletRequest);
                CompletableFuture.supplyAsync(() -> cm.editablePackages(appSecurityContext), executor)
                                .thenApply(this::serializeResponse).exceptionally(ExceptionHandler::handleExceptions)
                                .thenAccept(response::resume);
        }

        @GET
        @Path("v1/packages/{packageIdOrGuid}/details")
        @Operation(summary = "Fetch package details by GUID", description = "Returns details for a single course content package (includes summary info for resources and webcontent)", responses = {
                        @ApiResponse(responseCode = "200", description = "Successful response for the content package details requested", content = {
                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ContentPackage.class)) }),

                        @ApiResponse(responseCode = "400", description = "Invalid request information supplied"),
                        @ApiResponse(responseCode = "404", description = "Package not found"),
                        @ApiResponse(responseCode = "403", description = "Request not authorized") })
        public void packageDetails(@Suspended AsyncResponse response,
                        @Parameter(description = "Id-Vers/Guid of package details to return") @PathParam("packageIdOrGuid") String packageIdOrGuid) {
                if (packageIdOrGuid == null) {
                        response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING,
                                        Response.Status.BAD_REQUEST));
                        return;
                }

                AppSecurityContext appSecurityContext = appSecurityContextFactory
                                .extractSecurityContext(httpServletRequest);
                CompletableFuture.supplyAsync(() -> cm.loadPackage(appSecurityContext, packageIdOrGuid), executor)
                                .thenApply(this::serializeResponse).exceptionally(ExceptionHandler::handleExceptions)
                                .thenAccept(response::resume);
        }

        @Operation(summary = "Update package by GUID", description = "Updates course content package")
        @PUT
        @Path("v1/packages/{packageIdOrGuid}")
        public void updatePackage(@Suspended AsyncResponse response,
                        @Parameter(description = "ID-Vers/GUID of package to update") @PathParam("packageIdOrGuid") String packageIdOrGuid,
                        @Parameter(description = "Updated package payload in JSON format") JsonObject payload) {
                if (packageIdOrGuid == null || payload == null) {
                        response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING,
                                        Response.Status.BAD_REQUEST));
                        return;
                }
                AppSecurityContext appSecurityContext = appSecurityContextFactory
                                .extractSecurityContext(httpServletRequest);
                JsonParser jsonParser = new JsonParser();
                com.google.gson.JsonObject updatePayload = jsonParser.parse(AppUtils.toString(payload))
                                .getAsJsonObject();
                CompletableFuture
                                .supplyAsync(() -> updatePackage(appSecurityContext, packageIdOrGuid, updatePayload, 0),
                                                executor)
                                .thenAccept(response::resume);
        }

        private Response updatePackage(AppSecurityContext appSecurityContext, String packageIdOrGuid,
                        com.google.gson.JsonObject updatePayload, int retryCounter) {
                try {
                        JsonElement contentPkgJson = cm.updatePackage(appSecurityContext, packageIdOrGuid,
                                        updatePayload);
                        Gson gson = AppUtils.gsonBuilder().serializeNulls().create();
                        return Response.status(Response.Status.OK).entity(gson.toJson(contentPkgJson))
                                        .type(MediaType.APPLICATION_JSON).build();
                } catch (Throwable t) {
                        if (ExceptionHandler.checkForDBLockExceptions(t)
                                        && retryCounter < configuration.get().getTransactionRetrys()) {
                                log.error("LockResource acquisition exception detected on package update; retrying transaction");
                                return updatePackage(appSecurityContext, packageIdOrGuid, updatePayload,
                                                ++retryCounter);
                        }
                        return ExceptionHandler.handleExceptions(t);
                }
        }

        @Operation(summary = "Create new package", description = "Creates new course content package")
        @POST
        @Path("v1/packages")
        public void createPackage(@Suspended AsyncResponse response,
                        @Parameter(description = "Payload for new package (JSON format)") JsonObject body) {
                if (body == null) {
                        response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING,
                                        Response.Status.BAD_REQUEST));
                        return;
                }
                AppSecurityContext appSecurityContext = appSecurityContextFactory
                                .extractSecurityContext(httpServletRequest);
                JsonParser jsonParser = new JsonParser();
                com.google.gson.JsonObject jsonObject = jsonParser.parse(AppUtils.toString(body)).getAsJsonObject();
                CompletableFuture.supplyAsync(() -> cm.createPackage(appSecurityContext, jsonObject), executor)
                                .thenApply(this::serializeResponse).exceptionally(ExceptionHandler::handleExceptions)
                                .thenAccept(response::resume);
        }

        @Operation(summary = "Import new package via SVN URL", description = "Creates new course content package")
        @POST
        @Path("v1/packages/import")
        public void importPackage(@Suspended AsyncResponse response,
                        @Parameter(description = "SVN URL") JsonObject packageInfo) {
                if (packageInfo == null) {
                        response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING,
                                        Response.Status.BAD_REQUEST));
                        return;
                }
                AppSecurityContext appSecurityContext = appSecurityContextFactory
                                .extractSecurityContext(httpServletRequest);
                JsonParser jsonParser = new JsonParser();
                com.google.gson.JsonObject jsonObject = jsonParser.parse(AppUtils.toString(packageInfo))
                                .getAsJsonObject();
                if (!jsonObject.has("repositoryUrl")) {
                        response.resume(ExceptionHandler.errorResponse(
                                        "Validation Error: 'repositoryUrl' value missing",
                                        Response.Status.BAD_REQUEST));
                        return;
                }

                CompletableFuture.supplyAsync(() -> cm.importPackage(appSecurityContext, jsonObject), executor)
                                .thenApply(obj -> serializeResponse(obj))
                                .exceptionally(ex -> ExceptionHandler.handleExceptions(ex))
                                .thenAccept(response::resume);
        }

        @Operation(summary = "Lockdown packages editing", description = "Locks down the ability to edit packages supplied in the request")
        @POST
        @Path("v1/packages/set/editable")
        public void packageEditable(@Suspended AsyncResponse response, @QueryParam("editable") String editable,
                        JsonArray body) {
                if (body == null || editable == null) {
                        response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING,
                                        Response.Status.BAD_REQUEST));
                        return;
                }
                AppSecurityContext appSecurityContext = appSecurityContextFactory
                                .extractSecurityContext(httpServletRequest);

                CompletableFuture.supplyAsync(
                                () -> cm.setPackageEditable(appSecurityContext, Boolean.parseBoolean(editable),
                                                new JsonParser().parse(AppUtils.toString(body)).getAsJsonArray()),
                                executor).thenApply(this::serializeResponse)
                                .exceptionally(ExceptionHandler::handleExceptions).thenAccept(response::resume);
        }

        @Operation(summary = "Hide packages", description = "Hide packages from editor listing")
        @POST
        @Path("v1/packages/set/visible")
        public void packageVisible(@Suspended AsyncResponse response, @QueryParam("visible") String visible,
                        JsonArray body) {

                if (body == null || visible == null) {
                        response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING,
                                        Response.Status.BAD_REQUEST));
                        return;
                }
                AppSecurityContext appSecurityContext = appSecurityContextFactory
                                .extractSecurityContext(httpServletRequest);
                CompletableFuture.supplyAsync(
                                () -> cm.setPackageVisible(appSecurityContext, Boolean.parseBoolean(visible),
                                                new JsonParser().parse(AppUtils.toString(body)).getAsJsonArray()),
                                executor).thenApply(this::serializeResponse)
                                .exceptionally(ExceptionHandler::handleExceptions).thenAccept(response::resume);
        }

        @Operation(summary = "Deploy content package", description = "Request a deploy of the content package to delivery system such as 'QA', 'Production' etc ")
        @POST
        @Path("v1/packages/{packageIdOrGuid}/deploy")
        public void deployPkg(@Suspended AsyncResponse response,
                        @Parameter(description = "ID-Vers/GUID of package to deploy") @PathParam("packageIdOrGuid") String packageIdOrGuid,
                        @Parameter(description = "Json payload example {'stage':'qa', 'redeploy':'true'}") JsonObject payload) {

                if (payload == null || !payload.containsKey("stage") || !payload.containsKey("redeploy")) {
                        response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING,
                                        Response.Status.BAD_REQUEST));
                        return;
                }
                DeployStage deployStage;
                try {
                        deployStage = DeployStage.valueOf(payload.getString("stage"));
                } catch (Exception e) {
                        response.resume(ExceptionHandler.errorResponse(payload.getString("stage")
                                        + "is not a valid deployment stage it must be one of 'qa' or 'prod'",
                                        Response.Status.BAD_REQUEST));
                        return;
                }
                AppSecurityContext appSecurityContext = appSecurityContextFactory
                                .extractSecurityContext(httpServletRequest);
                CompletableFuture
                                .supplyAsync(() -> cm.deployPkg(appSecurityContext, packageIdOrGuid, deployStage,
                                                payload.getBoolean("redeploy")), executor)
                                .thenApply(this::serializeResponse).exceptionally(ExceptionHandler::handleExceptions)
                                .thenAccept(response::resume);
        }

        @Operation(summary = "Version a package", description = "Creates a new version of the content package")
        @POST
        @Path("v1/packages/{packageIdOrGuid}/new/version")
        public void newVersion(@Suspended AsyncResponse response,
                        @Parameter(description = "ID-Vers/GUID of package to update") @PathParam("packageIdOrGuid") String packageIdOrGuid,
                        @Parameter(description = "Json payload example: {'version':'3.4'}") JsonObject payload) {
                if (payload == null || !payload.containsKey("version")) {
                        response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING,
                                        Response.Status.BAD_REQUEST));
                        return;
                }
                String version = payload.getString("version");
                // validate version format
                String[] split = version.split("\\.");
                if (split.length < 2 || split.length > 3) {
                        response.resume(ExceptionHandler.errorResponse(
                                        "Version string supplied not valid: "
                                                        + "example of a valid string is '3.4' or '3.4.0'",
                                        Response.Status.BAD_REQUEST));
                        return;
                }
                try {
                        for (String s : split) {
                                Integer.parseInt(s);
                        }
                } catch (NumberFormatException e) {
                        response.resume(ExceptionHandler.errorResponse(
                                        "Version string major, minor and mod values should be numeric:"
                                                        + " example of a valid string is '3.4' or '3.4.0'",
                                        Response.Status.BAD_REQUEST));
                        return;
                }

                AppSecurityContext appSecurityContext = appSecurityContextFactory
                                .extractSecurityContext(httpServletRequest);
                CompletableFuture
                                .supplyAsync(() -> cm.newVersionOrClone(appSecurityContext, packageIdOrGuid, null,
                                                version), executor)
                                .thenApply(this::serializeResponse).exceptionally(ExceptionHandler::handleExceptions)
                                .thenAccept(response::resume);
        }

        @Operation(summary = "Clone and Own a package", description = "Creates a new clone of an existing package ")
        @POST
        @Path("v1/packages/{packageIdOrGuid}/new/clone")
        public void cloneAndOwn(@Suspended AsyncResponse response,
                        @Parameter(description = "GUID of package to clone") @PathParam("packageIdOrGuid") String packageIdOrGuid,
                        @Parameter(description = "Json payload example {'id':'value'}") JsonObject payload) {

                if (payload == null || !payload.containsKey("id")) {
                        response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING,
                                        Response.Status.BAD_REQUEST));
                        return;
                }

                AppSecurityContext appSecurityContext = appSecurityContextFactory
                                .extractSecurityContext(httpServletRequest);
                CompletableFuture
                                .supplyAsync(() -> cm.newVersionOrClone(appSecurityContext, packageIdOrGuid,
                                                payload.getString("id"), null), executor)
                                .thenApply(this::serializeResponse).exceptionally(ExceptionHandler::handleExceptions)
                                .thenAccept(response::resume);
        }

        private Response serializeResponse(JsonElement response) {
                Gson gson = AppUtils.gsonBuilder().serializeNulls().create();
                return Response.status(Response.Status.OK).entity(gson.toJson(response))
                                .type(MediaType.APPLICATION_JSON).build();
        }

        private void onTimeout(AsyncResponse asyncResponse) {
                Response response = Response.status(Response.Status.SERVICE_UNAVAILABLE).header("reason", "overloaded")
                                .build();
                asyncResponse.resume(response);
        }

        @Operation(summary = "Update a package deployment status", description = "Management interface for updating a package deployment status")
        @PUT
        @Path("v1/packages/{packageIdOrGuid}/status/{status}")
        public void updateDeploymentStatus(@Suspended AsyncResponse response,
                        @PathParam("packageIdOrGuid") String packageIdOrGuid,
                        @Parameter(description = "Status") @PathParam("status") String status) {

                try {
                        AppSecurityContext appSecurityContext = appSecurityContextFactory
                                        .extractSecurityContext(httpServletRequest);
                        ContentPackage.DeploymentStatus newStatus = ContentPackage.DeploymentStatus
                                        .valueOf(status.trim().toUpperCase());
                        CompletableFuture
                                        .supplyAsync(() -> cm.updateDeploymentStatus(appSecurityContext,
                                                        packageIdOrGuid, newStatus), executor)
                                        .thenAccept(response::resume);
                } catch (Exception e) {
                        response.resume(ExceptionHandler.errorResponse("Invalid status parameter",
                                        Response.Status.BAD_REQUEST));
                }
        }

}
