package edu.cmu.oli.content.boundary.endpoints;

import com.airhacks.porcupine.execution.boundary.Dedicated;
import com.google.gson.JsonElement;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.boundary.ExceptionHandler;
import edu.cmu.oli.content.boundary.managers.LockResourceManager;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.security.AppSecurityContext;
import edu.cmu.oli.content.security.AppSecurityContextFactory;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
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
public class LockResource {
    @Inject
    @Logging
    Logger log;

    @Inject
    private LockResourceManager lm;

    @Inject
    @Dedicated("locksResourceApiExecutor")
    ExecutorService mes;

    @Context
    private HttpServletRequest httpServletRequest;

    @Inject
    AppSecurityContextFactory appSecurityContextFactory;

    @GET
    @Path("v1/{packageIdOrGuid}/locks")
    public void lock(@Suspended AsyncResponse response, @PathParam("packageIdOrGuid") String packageIdOrGuid,
            @QueryParam("resourceId") String resourceId, @QueryParam("action") String action) {
        if (packageIdOrGuid == null || resourceId == null || action == null) {
            String message = "Parameters missing";
            response.resume(ExceptionHandler.errorResponse(message, Response.Status.BAD_REQUEST));
            return;
        }
        AppSecurityContext appSecurityContext = appSecurityContextFactory.extractSecurityContext(httpServletRequest);
        CompletableFuture.supplyAsync(() -> lm.lock(appSecurityContext, packageIdOrGuid, resourceId, action), mes)
                .thenApply(this::lock).exceptionally(ExceptionHandler::handleExceptions).thenAccept(response::resume);
    }

    Response lock(JsonElement element) {
        return Response.status(Response.Status.OK)
                .entity(AppUtils.gsonBuilder().serializeNulls().create().toJson(element))
                .type(MediaType.APPLICATION_JSON).build();
    }
}
