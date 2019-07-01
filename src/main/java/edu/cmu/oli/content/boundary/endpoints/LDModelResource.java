package edu.cmu.oli.content.boundary.endpoints;

import com.airhacks.porcupine.execution.boundary.Dedicated;
import com.google.gson.JsonElement;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.boundary.ExceptionHandler;
import edu.cmu.oli.content.boundary.managers.LDModelResourceManager;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.security.AppSecurityContextFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * @author Raphael Gachuhi
 */

@Stateless
@Path("/")
public class LDModelResource {

        static final String PARAMETERS_MISSING = "Parameters missing";

        @Inject
        @Logging
        Logger log;

        @Inject
        LDModelResourceManager ldModelResourceManager;

        @Inject
        @Dedicated("webcontentsApiExecutor")
        ExecutorService mes;

        @Inject
        @ConfigurationCache
        Instance<Configurations> config;

        @Context
        private HttpServletRequest httpServletRequest;

        @Inject
        AppSecurityContextFactory appSecurityContextFactory;

        @Operation(summary = "Import LD model zip file", description = "Import a zip file containing LD model files as payload", responses = {
                        @ApiResponse(responseCode = "200", description = "Successful LD model import", content = {
                                        @Content(mediaType = MediaType.APPLICATION_JSON) }),

                        @ApiResponse(responseCode = "400", description = "Invalid request information supplied"),
                        @ApiResponse(responseCode = "404", description = "Package not found"),
                        @ApiResponse(responseCode = "403", description = "Request not authorized") })
        @POST
        @Path("v1/{packageIdOrGuid}/ldmodel/import")
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        public Response importLDModel(@PathParam("packageIdOrGuid") String packageIdOrGuid,
                        MultipartFormDataInput multipart) {
                if (packageIdOrGuid == null) {
                        return ExceptionHandler.errorResponse(PARAMETERS_MISSING, Response.Status.BAD_REQUEST);
                }
                Map<String, List<InputPart>> formParts = multipart.getFormDataMap();

                List<InputPart> files = formParts.entrySet().stream().map(e -> e.getValue()).flatMap(e -> e.stream())
                                .collect(Collectors.toList()).stream().filter(e -> getFileName(e.getHeaders()))
                                .collect(Collectors.toList());
                if (files.isEmpty() || files.size() != 3) {
                        return ExceptionHandler.errorResponse(
                                        "Error: exactly 3 files should be included in the upload request.",
                                        Response.Status.BAD_REQUEST);
                }
                JsonElement webContentsJson = this.ldModelResourceManager.importLDModel(
                                appSecurityContextFactory.extractSecurityContext(httpServletRequest), files,
                                packageIdOrGuid);
                return Response.status(Response.Status.OK)
                                .entity(AppUtils.gsonBuilder().create().toJson((webContentsJson)))
                                .type(MediaType.APPLICATION_JSON).build();
        }

        @Operation(summary = "Export LD model zip file", description = "Export a zip file containing LD model files payload", responses = {
                        @ApiResponse(responseCode = "200", description = "Successful LD model export", content = {
                                        @Content(mediaType = "application/zip") }),

                        @ApiResponse(responseCode = "400", description = "Invalid request information supplied"),
                        @ApiResponse(responseCode = "404", description = "LDModel or Package not found"),
                        @ApiResponse(responseCode = "403", description = "Request not authorized") })
        @GET
        @Path("v1/{packageIdOrGuid}/ldmodel/export")
        @Produces("application/zip")
        public Response exportLDModel(@PathParam("packageIdOrGuid") String packageIdOrGuid) {
                if (packageIdOrGuid == null) {
                        return ExceptionHandler.errorResponse(PARAMETERS_MISSING, Response.Status.BAD_REQUEST);
                }

                ByteArrayOutputStream baos = this.ldModelResourceManager.exportLDModel(
                                appSecurityContextFactory.extractSecurityContext(httpServletRequest), packageIdOrGuid);
                return Response.ok(new ByteArrayInputStream(baos.toByteArray()))
                                .header("Content-Disposition", "attachment; filename=\"" + "learning-model.zip" + "\"")
                                .build();
        }

        private boolean getFileName(MultivaluedMap<String, String> headers) {
                String contentDisp = headers.getFirst("content-disposition");
                String[] tokens = contentDisp.split(";");
                for (String token : tokens) {
                        if (token.trim().startsWith("filename")) {
                                return true;
                        }
                }
                return false;
        }
}
