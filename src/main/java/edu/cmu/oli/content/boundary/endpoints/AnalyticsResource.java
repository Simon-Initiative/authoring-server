package edu.cmu.oli.content.boundary.endpoints;

import com.airhacks.porcupine.execution.boundary.Dedicated;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.boundary.ExceptionHandler;
import edu.cmu.oli.content.boundary.managers.AnalyticsResourceManager;
import edu.cmu.oli.content.security.AppSecurityContext;
import edu.cmu.oli.content.security.AppSecurityContextFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * @author Zach Bluedorn
 */
@Stateless
@Path("/")
public class AnalyticsResource {

    @Inject
    AnalyticsResourceManager manager;

    @Context
    private HttpServletRequest httpServletRequest;

    @Inject
    AppSecurityContextFactory appSecurityContextFactory;

    @Inject
    @Dedicated("AnalyticsResourceApiExecutor")
    ExecutorService executor;

    @GET
    @Path("v1/analytics/{packageGuid}")
    @Operation(summary = "Fetch datasets by package GUID", responses = {
            @ApiResponse(responseCode = "200", description = "Successful response including all of a package's datasets", content = {
                    @Content(mediaType = "application/json") }),
            @ApiResponse(responseCode = "400", description = "Invalid request information supplied"),
            @ApiResponse(responseCode = "404", description = "No datasets found for package guid"),
            @ApiResponse(responseCode = "403", description = "Request not authorized") })
    public void getPackageDatasets(@Suspended AsyncResponse response, @PathParam("packageGuid") String packageGuid) {
        if (packageGuid == null) {
            response.resume(ExceptionHandler.errorResponse("Parameters missing", Response.Status.BAD_REQUEST));
            return;
        }
        AppSecurityContext appSecurityContext = appSecurityContextFactory.extractSecurityContext(httpServletRequest);
        CompletableFuture.supplyAsync(() -> manager.getPackageDatasets(appSecurityContext, packageGuid), executor)
                .thenApply(this::serializeResponse).exceptionally(ExceptionHandler::handleExceptions)
                .thenAccept(response::resume);
    }

    @GET
    @Path("v1/analytics/dataset/{datasetGuid}")
    @Operation(summary = "Find dataset by GUID", responses = {
            @ApiResponse(responseCode = "200", description = "Successful response, full dataset for the given GUID", content = {
                    @Content(mediaType = "application/json") }),
            @ApiResponse(responseCode = "400", description = "No datasetGuid supplied"),
            @ApiResponse(responseCode = "404", description = "No dataset found with that guid") })
    public void getDataset(@Suspended AsyncResponse response, @PathParam("datasetGuid") String datasetGuid) {
        if (datasetGuid == null) {
            response.resume(ExceptionHandler.errorResponse("Parameters missing", Response.Status.BAD_REQUEST));
            return;
        }
        AppSecurityContext appSecurityContext = appSecurityContextFactory.extractSecurityContext(httpServletRequest);
        CompletableFuture.supplyAsync(() -> manager.getDataset(appSecurityContext, datasetGuid), executor)
                .thenApply(this::serializeResponse).exceptionally(ExceptionHandler::handleExceptions)
                .thenAccept(response::resume);
    }

    @POST
    @Path("v1/analytics/dataset/{packageGuid}")
    @Operation(summary = "Create dataset for a package GUID", responses = {
            @ApiResponse(responseCode = "200", description = "Successful response, includes the GUID for the to-be-created dataset", content = {
                    @Content(mediaType = "application/json", schema = @Schema(example = "{guid: 2c92808a685bb18b01685c6782a500c1, message: 'dataset status'}")) }),
            @ApiResponse(responseCode = "400", description = "No packageGuid supplied or no data available for the course package"),
            @ApiResponse(responseCode = "404", description = "No package found with that guid") })
    public void createDataset(@Suspended AsyncResponse response, @PathParam("packageGuid") String packageGuid) {
        if (packageGuid == null) {
            response.resume(ExceptionHandler.errorResponse("Parameters missing", Response.Status.BAD_REQUEST));
            return;
        }
        AppSecurityContext appSecurityContext = appSecurityContextFactory.extractSecurityContext(httpServletRequest);
        CompletableFuture.supplyAsync(() -> manager.createDataset(appSecurityContext, packageGuid), executor)
                .thenApply(this::serializeResponse).exceptionally(ExceptionHandler::handleExceptions)
                .thenAccept(response::resume);
    }

    private Response serializeResponse(JsonElement response) {
        Gson gson = AppUtils.gsonBuilder().serializeNulls().create();
        return Response.status(Response.Status.OK).entity(gson.toJson(response)).type(MediaType.APPLICATION_JSON)
                .build();
    }
}
