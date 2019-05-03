package edu.cmu.oli.content.controllers;

import edu.cmu.oli.content.boundary.ResourceChangeEvent;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.ChangePayload;
import org.slf4j.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Raphael Gachuhi
 */
@ApplicationScoped
public class LongPollController {

    @Inject
    @Logging
    Logger log;

    private Map<String, ChangePayload> changePayloadHashMap = new ConcurrentHashMap<>();

    public void onFileChange(@Observes ResourceChangeEvent resourceChangeEvent) {
        ChangePayload changePayload = changePayloadHashMap.get(resourceChangeEvent.getResourceId());
        if (changePayload == null) {
            return;
        }
        changePayload.changeInfo = resourceChangeEvent.getChangeInfo();
        changePayload.payload = resourceChangeEvent.getEventPayload();
        synchronized (changePayload) {
            changePayload.notifyAll();
        }
    }

    public void registerPayloadChangePayload(ChangePayload cp) {
        changePayloadHashMap.put(cp.resourceId, cp);
    }

    public void unRegisterPayloadChangePayload(ChangePayload cp) {
        changePayloadHashMap.remove(cp);
    }
}
