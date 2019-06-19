package edu.cmu.oli.content.boundary.endpoints;

import com.airhacks.porcupine.execution.boundary.Dedicated;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.boundary.ExceptionHandler;
import edu.cmu.oli.content.boundary.managers.DeveloperResourceManager;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.security.AppSecurityContext;
import edu.cmu.oli.content.security.AppSecurityContextFactory;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.json.JsonArray;
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
public class DeveloperResource {

    @Inject
    @Logging
    Logger log;

    @Inject
    private DeveloperResourceManager pm;

    @Inject
    @Dedicated("developersResourceApiExecutor")
    ExecutorService mes;

    @Context
    private HttpServletRequest httpServletRequest;

    @Inject
    AppSecurityContextFactory appSecurityContextFactory;

    @GET
    @Path("v1/{packageIdOrGuid}/developers")
    public void all(@Suspended AsyncResponse response, @PathParam("packageIdOrGuid") String packageIdOrGuid) {
        if (packageIdOrGuid == null) {
            String message = "Parameters missing";
            response.resume(ExceptionHandler.errorResponse(message, Response.Status.BAD_REQUEST));
            return;
        }
        AppSecurityContext appSecurityContext = appSecurityContextFactory.extractSecurityContext(httpServletRequest);
        CompletableFuture.supplyAsync(() -> pm.all(appSecurityContext, packageIdOrGuid), mes).thenApply(this::all)
                .exceptionally(ExceptionHandler::handleExceptions).thenAccept(response::resume);
    }

    private Response all(JsonElement users) {
        return Response.status(Response.Status.OK)
                .entity(AppUtils.gsonBuilder().serializeNulls().create().toJson(users)).type(MediaType.APPLICATION_JSON)
                .build();
    }

    @POST
    @Path("v1/{packageIdOrGuidOrGuid}/developers/registration")
    public void registration(@Suspended AsyncResponse response,
            @PathParam("packageIdOrGuidOrGuid") String packageIdOrGuidOrGuid, @QueryParam("action") String action,
            JsonArray users) {

        if (packageIdOrGuidOrGuid == null || users == null || action == null) {
            String message = "Parameters missing";
            response.resume(ExceptionHandler.errorResponse(message, Response.Status.BAD_REQUEST));
            return;
        }
        if (!action.equalsIgnoreCase("add") && !action.equalsIgnoreCase("remove")) {
            String message = "Wrong action parameter; value should be either 'add' or 'remove' " + action;
            response.resume(ExceptionHandler.errorResponse(message, Response.Status.BAD_REQUEST));
            return;
        }
        AppSecurityContext appSecurityContext = appSecurityContextFactory.extractSecurityContext(httpServletRequest);
        JsonParser jsonParser = new JsonParser();
        CompletableFuture
                .supplyAsync(() -> pm.registration(appSecurityContext, packageIdOrGuidOrGuid, action,
                        jsonParser.parse(AppUtils.toString(users)).getAsJsonArray()), mes)
                .thenApply(this::registration).exceptionally(ExceptionHandler::handleExceptions)
                .thenAccept(response::resume);
    }

    private Response registration(JsonElement registeredUsers) {
        return Response.status(Response.Status.OK)
                .entity(AppUtils.gsonBuilder().serializeNulls().create().toJson(registeredUsers))
                .type(MediaType.APPLICATION_JSON).build();
    }
}

// ${configuration.baseUrl}/${courseId}/developers/registration?action=add
// ${configuration.baseUrl}/${courseId}/developers/registration?action=remove