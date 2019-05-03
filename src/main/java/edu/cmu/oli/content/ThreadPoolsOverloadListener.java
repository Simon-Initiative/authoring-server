package edu.cmu.oli.content;

import com.airhacks.porcupine.execution.entity.Rejection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.event.Observes;

/**
 * @author Raphael Gachuhi
 */
public class ThreadPoolsOverloadListener {

    Logger log = LoggerFactory.getLogger(ThreadPoolsOverloadListener.class);

    public void onOverload(@Observes Rejection rejection) {
        log.error("rejection= " + rejection);
    }
}
