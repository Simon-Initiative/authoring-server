package edu.cmu.oli.content.boundary.endpoints;

import com.airhacks.porcupine.execution.boundary.Dedicated;
import com.google.gson.Gson;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.boundary.ExceptionHandler;
import edu.cmu.oli.content.controllers.LongPollController;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.ChangePayload;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * @author Raphael Gachuhi
 */

@Stateless
@Path("/")
public class LongPollResource {

    @Inject
    @Logging
    Logger log;

    @Inject
    private LongPollController longPollController;

    @Inject
    @Dedicated("pollsResourceApiExecutor")
    ExecutorService executor;

    @POST
    @Path("v1/polls")
    public void longPoll(@Suspended AsyncResponse response, @QueryParam("timeout") long timeout,
            @QueryParam("include_docs") boolean includeDocs, @QueryParam("since") String since,
            @QueryParam("filter") String filter, JsonObject body) {
        CompletableFuture.supplyAsync(() -> {
            ChangePayload changePayload = new ChangePayload(body.getJsonArray("doc_ids").get(0).toString());
            this.longPollController.registerPayloadChangePayload(changePayload);
            synchronized (changePayload) {
                try {
                    changePayload.wait(timeout);
                } catch (InterruptedException e) {
                }
            }
            this.longPollController.unRegisterPayloadChangePayload(changePayload);
            Gson gson2 = AppUtils.gsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();
            return Response.status(Response.Status.OK).entity(gson2.toJson(changePayload))
                    .type(MediaType.APPLICATION_JSON).build();
        }, executor).exceptionally(ExceptionHandler::handleExceptions).thenAccept(response::resume);
    }

    @GET
    @Path("v1/polls/server/time")
    public Response serverTime() {
        com.google.gson.JsonObject serverTime = new com.google.gson.JsonObject();
        serverTime.addProperty("serverTime", System.currentTimeMillis());
        return Response.status(Response.Status.OK).entity(AppUtils.gsonBuilder().create().toJson(serverTime))
                .type(MediaType.APPLICATION_JSON).build();
    }
}
