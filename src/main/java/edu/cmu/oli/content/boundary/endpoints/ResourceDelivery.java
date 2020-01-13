package edu.cmu.oli.content.boundary.endpoints;

import edu.cmu.oli.content.configuration.DedicatedExecutor;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.boundary.ExceptionHandler;
import edu.cmu.oli.content.boundary.managers.ResourceDeliveryManager;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.security.AppSecurityContext;
import edu.cmu.oli.content.security.AppSecurityContextFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
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
public class ResourceDelivery {

        @Inject
        @Logging
        Logger log;

        @Inject
        private ResourceDeliveryManager pm;

        @Inject
        @DedicatedExecutor("resourcesApiExecutor")
        ExecutorService mes;

        @Inject
        @ConfigurationCache
        Instance<Configurations> configuration;

        @Context
        private HttpServletRequest httpServletRequest;

        @Inject
        AppSecurityContextFactory appSecurityContextFactory;

        @GET
        @Path("v1/{packageIdOrGuid}/themes/available")
        public void availableThemes(@Suspended AsyncResponse response,
                        @PathParam("packageIdOrGuid") String packageIdOrGuid) {
                AppSecurityContext appSecurityContext = appSecurityContextFactory
                                .extractSecurityContext(httpServletRequest);

                CompletableFuture.supplyAsync(() -> pm.availableThemes(appSecurityContext, packageIdOrGuid), mes)
                                .thenApply(this::toResponse).exceptionally(ExceptionHandler::handleExceptions)
                                .thenAccept(response::resume);
        }

        @Operation(summary = "Update package default theme", description = "Updates course content package default theme")
        @PUT
        @Path("v1/packages/{packageIdOrGuid}/theme")
        public void updatePackageTheme(@Suspended AsyncResponse response,
                        @Parameter(description = "GUID of package to update") @PathParam("packageIdOrGuid") String packageIdOrGuid,
                        @Parameter(description = "Updated package payload in JSON format") JsonObject themeId) {
                if (packageIdOrGuid == null || themeId == null) {
                        response.resume(ExceptionHandler.errorResponse("Parameters missing",
                                        Response.Status.BAD_REQUEST));
                        return;
                }
                JsonParser jsonParser = new JsonParser();
                com.google.gson.JsonObject jsonObject = jsonParser.parse(AppUtils.toString(themeId)).getAsJsonObject();
                AppSecurityContext appSecurityContext = appSecurityContextFactory
                                .extractSecurityContext(httpServletRequest);
                CompletableFuture
                                .supplyAsync(() -> pm.updatePackageTheme(appSecurityContext, packageIdOrGuid,
                                                jsonObject.get("theme").getAsString()), mes)
                                .thenApply(this::toResponse).exceptionally(ExceptionHandler::handleExceptions)
                                .thenAccept(response::resume);
        }

        @GET
        @Path("v1/{packageIdOrGuid}/resources/preview/{resourceId}")
        public void preview(@Suspended AsyncResponse response, @PathParam("packageIdOrGuid") String packageIdOrGuid,
                        @PathParam("resourceId") String resourceId, @QueryParam("server") String server,
                        @QueryParam("redeploy") String redeploy) {
                if (packageIdOrGuid == null || resourceId == null) {
                        response.resume(ExceptionHandler.errorResponse("Parameters missing",
                                        Response.Status.BAD_REQUEST));
                        return;
                }
                AppSecurityContext appSecurityContext = appSecurityContextFactory
                                .extractSecurityContext(httpServletRequest);
                CompletableFuture
                                .supplyAsync(() -> pm.previewResource(appSecurityContext, packageIdOrGuid, resourceId,
                                                redeploy != null ? Boolean.parseBoolean(redeploy) : false), mes)
                                .thenApply(this::toResponse).exceptionally(ExceptionHandler::handleExceptions)
                                .thenAccept(response::resume);
        }

        @GET
        @Path("{packageIdOrGuid}/resources/quick_preview/{resourceId}")
        public void quickPreview(@Suspended AsyncResponse response,
                        @PathParam("packageIdOrGuid") String packageIdOrGuid,
                        @PathParam("resourceId") String resourceId) {
                if (packageIdOrGuid == null || resourceId == null) {
                        response.resume(ExceptionHandler.errorResponse("Parameters missing",
                                        Response.Status.BAD_REQUEST));
                        return;
                }
                // AppSecurityContext appSecurityContext =
                // appSecurityContextFactory.extractSecurityContext(httpServletRequest);
                CompletableFuture.supplyAsync(() -> pm.quickPreview(null, packageIdOrGuid, resourceId), mes)
                                .thenApply(this::toThinPreviewResponse)
                                .exceptionally(ExceptionHandler::handleExceptions).thenAccept(response::resume);

                // NewCookie cookie = new NewCookie("session",
                // appSecurityContext.getTokenString(), "/", null, null, -1, true);
                //
                // String redirectUrl = httpServletRequest.getScheme() + "://" +
                // httpServletRequest.getServerName() + "/content-service/api/"
                // + packageIdOrGuid + "/resources/do_quick_preview/" + resourceId;
                //
                // CompletableFuture.supplyAsync(() -> redirectPreview(redirectUrl, cookie),
                // mes)
                // .exceptionally(ExceptionHandler::handleExceptions).thenAccept(response::resume);
        }

        // private Response redirectPreview(String redirectUrl, NewCookie cookie) {
        // try {
        // return Response.seeOther(new
        // URI(redirectUrl)).cookie(cookie).type(MediaType.APPLICATION_JSON).build();
        // } catch (URISyntaxException e) {
        // throw new RuntimeException(e);
        // }
        // }
        //
        // @GET
        // @Path("{packageIdOrGuid}/resources/do_quick_preview/{resourceId}")
        // public void doQuickPreview(@Suspended AsyncResponse response,
        // @CookieParam("session") String authToken,
        // @PathParam("packageIdOrGuid") String packageIdOrGuid,
        // @PathParam("resourceId") String resourceId) {
        //
        //
        // String serverUrl = httpServletRequest.getScheme() + "://" +
        // httpServletRequest.getServerName() + "/";
        // CompletableFuture.supplyAsync(() -> pm.quickPreview(null, packageIdOrGuid,
        // resourceId, serverUrl), mes)
        // .thenApply(this::toThinPreviewResponse).exceptionally(ExceptionHandler::handleExceptions).thenAccept(response::resume);
        // }

        @POST
        @Path("jcourse/a2/rest/{context}/{user}/{attempt}/bulk_pages_context")
        public void inlineAssessmentBulkPageContext(@Suspended AsyncResponse response,
                        @CookieParam("session") String authToken, @PathParam("user") String userGuid,
                        JsonObject pageContext) {
                // AppSecurityContext appSecurityContext =
                // appSecurityContextFactory.extractSecurityContext(httpServletRequest);
                String serverUrl = httpServletRequest.getScheme() + "://" + httpServletRequest.getServerName() + "/";
                JsonParser jsonParser = new JsonParser();
                com.google.gson.JsonObject pageContextJson = jsonParser.parse(AppUtils.toString(pageContext))
                                .getAsJsonObject();
                CompletableFuture
                                .supplyAsync(() -> pm.inlineAssessmentBulkPageContext(userGuid, pageContextJson,
                                                serverUrl), mes)
                                .thenApply(this::toResponse).exceptionally(ExceptionHandler::handleExceptions)
                                .thenAccept(response::resume);
        }

        @POST
        @Path("jcourse/a2/rest/{context}/{user}/{attempt}/next_attempt")
        public void inlineAssessmentNextAttempt(@Suspended AsyncResponse response,
                        @CookieParam("session") String authToken, @PathParam("context") String contextGuid,
                        @PathParam("user") String userGuid, @PathParam("attempt") int attemptNumber,
                        @QueryParam("mode") String requestMode, String password) {
                // AppSecurityContext appSecurityContext =
                // appSecurityContextFactory.extractSecurityContext(httpServletRequest);
                String serverUrl = httpServletRequest.getScheme() + "://" + httpServletRequest.getServerName() + "/";
                CompletableFuture
                                .supplyAsync(() -> pm.inlineAssessmentNextPageContext(contextGuid, userGuid,
                                                attemptNumber, 1, requestMode, true, serverUrl), mes)
                                .thenApply(this::toResponse).exceptionally(ExceptionHandler::handleExceptions)
                                .thenAccept(response::resume);
        }
        // @Path("/{context}/{user}/{attempt}")

        @GET
        @Path("jcourse/a2/rest/{context}/{user}/{attempt}/pages_context/{page}")
        public void inlineAssessmentNextPage(@Suspended AsyncResponse response,
                        @CookieParam("session") String authToken, @PathParam("context") String contextGuid,
                        @PathParam("user") String userGuid, @PathParam("attempt") int attemptNumber,
                        @PathParam("page") int pageNumber, @QueryParam("mode") String requestMode,
                        @QueryParam("start") boolean start) {
                // AppSecurityContext appSecurityContext =
                // appSecurityContextFactory.extractSecurityContext(httpServletRequest);
                String serverUrl = httpServletRequest.getScheme() + "://" + httpServletRequest.getServerName() + "/";

                CompletableFuture
                                .supplyAsync(() -> pm.inlineAssessmentNextPageContext(contextGuid, userGuid,
                                                attemptNumber, pageNumber, requestMode, start, serverUrl), mes)
                                .thenApply(this::toResponse).exceptionally(ExceptionHandler::handleExceptions)
                                .thenAccept(response::resume);
        }

        @POST
        @Path("jcourse/a2/rest/{context}/{user}/{attempt}/responses_context/{questionId}")
        public void inlineAssessmentResponsesContext(@Suspended AsyncResponse response,
                        @CookieParam("session") String authToken, @PathParam("context") String contextGuid,
                        @PathParam("user") String userGuid, @PathParam("attempt") int attemptNumber,
                        @PathParam("questionId") String questionId, @QueryParam("start") boolean start,
                        JsonObject responses) {
                // AppSecurityContext appSecurityContext =
                // appSecurityContextFactory.extractSecurityContext(httpServletRequest);
                String serverUrl = httpServletRequest.getScheme() + "://" + httpServletRequest.getServerName() + "/";
                JsonParser jsonParser = new JsonParser();
                com.google.gson.JsonObject responsesJson = jsonParser.parse(AppUtils.toString(responses))
                                .getAsJsonObject();
                CompletableFuture
                                .supplyAsync(() -> pm.inlineAssessmentResponsesContext(contextGuid, userGuid,
                                                attemptNumber, questionId, start, responsesJson, serverUrl), mes)
                                .thenApply(this::toResponse).exceptionally(ExceptionHandler::handleExceptions)
                                .thenAccept(response::resume);
        }

        private Response toThinPreviewResponse(String content) {
                return Response.status(Response.Status.OK).entity(content).type(MediaType.TEXT_HTML).build();
        }

        private Response toResponse(JsonElement resourceJson) {
                Gson gson = AppUtils.gsonBuilder().serializeNulls().create();
                return Response.status(Response.Status.OK).entity(gson.toJson(resourceJson))
                                .type(MediaType.APPLICATION_JSON).build();
        }
}
