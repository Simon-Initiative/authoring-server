package edu.cmu.oli.content.boundary.endpoints;

import edu.cmu.oli.content.configuration.DedicatedExecutor;
import com.google.gson.JsonElement;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.boundary.ExceptionHandler;
import edu.cmu.oli.content.boundary.managers.WebResourceManager;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.PaginatedResponse;
import edu.cmu.oli.content.security.AppSecurityContext;
import edu.cmu.oli.content.security.AppSecurityContextFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * @author Raphael Gachuhi
 */

@Stateless
@Path("/")
public class WebResource {

        static final String PARAMETERS_MISSING = "Parameters missing";

        @Inject
        @Logging
        Logger log;

        @Inject
        WebResourceManager wcm;

        @Inject
        @DedicatedExecutor("webcontentsApiExecutor")
        ExecutorService mes;

        @Inject
        @ConfigurationCache
        Instance<Configurations> config;

        @Context
        private HttpServletRequest httpServletRequest;

        @Inject
        AppSecurityContextFactory appSecurityContextFactory;

        @Operation(summary = "Get a list of paginated WebContent items", description = "Get a list of paginated WebContent items filtered by the request's parameter values", responses = {
                        @ApiResponse(responseCode = "200", description = "Successful response for the webcontents request", content = {
                                        @Content(mediaType = "application/json", schema = @Schema(implementation = PaginatedResponse.class)) }),
                        @ApiResponse(responseCode = "400", description = "Invalid request information supplied") })
        @GET
        @Path("v1/{packageIdOrGuid}/webcontents")
        public void listWebcontent(@Suspended AsyncResponse response,
                        @PathParam("packageIdOrGuid") String packageIdOrGuid, @QueryParam("offset") int offset,
                        @QueryParam("limit") @DefaultValue("10") int limit,
                        @QueryParam("order") @DefaultValue("desc") String order,
                        @QueryParam("orderBy") @DefaultValue("dateCreated") String orderBy,
                        @QueryParam("guid") @DefaultValue("") String guidFilter,
                        @QueryParam("mimeFilter") @DefaultValue("") String mimeFilter,
                        @QueryParam("pathFilter") @DefaultValue("") String pathFilter,
                        @QueryParam("searchText") @DefaultValue("") String searchText) {
                if (packageIdOrGuid == null) {
                        response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING,
                                        Response.Status.BAD_REQUEST));
                        return;
                }
                AppSecurityContext appSecurityContext = appSecurityContextFactory
                                .extractSecurityContext(httpServletRequest);
                CompletableFuture
                                .supplyAsync(() -> wcm.webcontents(appSecurityContext, packageIdOrGuid, offset, limit,
                                                order, orderBy, guidFilter, mimeFilter, pathFilter, searchText), mes)
                                .thenApply(this::toResponse).exceptionally(ExceptionHandler::handleExceptions)
                                .thenAccept(response::resume);
        }

        @DELETE
        @Path("v1/{packageIdOrGuid}/webcontents/{webcontentId}/delete")
        public void deleteWebcontent(@Suspended AsyncResponse response,
                        @PathParam("packageIdOrGuid") String packageIdOrGuid,
                        @PathParam("webcontentId") String webcontentId) {
                if (packageIdOrGuid == null || webcontentId == null) {
                        response.resume(ExceptionHandler.errorResponse(PARAMETERS_MISSING,
                                        Response.Status.BAD_REQUEST));
                        return;
                }
                AppSecurityContext appSecurityContext = appSecurityContextFactory
                                .extractSecurityContext(httpServletRequest);
                CompletableFuture
                                .supplyAsync(() -> wcm.deleteWebcontent(appSecurityContext, packageIdOrGuid,
                                                webcontentId), mes)
                                .thenApply(this::toResponse).exceptionally(ExceptionHandler::handleExceptions)
                                .thenAccept(response::resume);
        }

        @POST
        @Path("v1/{packageIdOrGuid}/webcontents/upload")
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        public Response uploadWebcontent(@PathParam("packageIdOrGuid") String packageIdOrGuid,
                        MultipartFormDataInput multipart) {
                if (packageIdOrGuid == null) {
                        return ExceptionHandler.errorResponse(PARAMETERS_MISSING, Response.Status.BAD_REQUEST);
                }
                Map<String, List<InputPart>> formParts = multipart.getFormDataMap();
                List<InputPart> files = formParts.get("file");
                JsonElement webContentsJson = wcm.uploadWebcontent(
                                appSecurityContextFactory.extractSecurityContext(httpServletRequest), files,
                                packageIdOrGuid);
                return Response.status(Response.Status.OK)
                                .entity(AppUtils.gsonBuilder().create().toJson((webContentsJson)))
                                .type(MediaType.APPLICATION_JSON).build();
        }

        @GET
        @Path("v1/{packageIdOrGuid}/webcontents/{filePath}")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        public Response getWebContentFile(@PathParam("packageIdOrGuid") String packageIdOrGuid,
                        @PathParam("filePath") String filePath) {
                if (packageIdOrGuid == null || filePath == null) {
                        return ExceptionHandler.errorResponse(PARAMETERS_MISSING, Response.Status.BAD_REQUEST);
                }

                File webcontentFile = wcm.getWebcontentFile(
                                appSecurityContextFactory.extractSecurityContext(httpServletRequest), packageIdOrGuid,
                                filePath);
                return Response.ok(webcontentFile, MediaType.APPLICATION_OCTET_STREAM)
                                .header("Content-Disposition",
                                                "attachment; filename=\"" + webcontentFile.getName() + "\"") // optional
                                .build();
        }

        private Response toResponse(JsonElement element) {
                return Response.status(Response.Status.OK).entity(AppUtils.gsonBuilder().create().toJson(element))
                                .type(MediaType.APPLICATION_JSON).build();
        }

}
