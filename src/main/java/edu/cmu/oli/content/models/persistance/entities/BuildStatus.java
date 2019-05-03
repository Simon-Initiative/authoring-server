package edu.cmu.oli.content.models.persistance.entities;

/**
 * Enumeration of OLI resource processing state. This class allows for async
 * tracking on the progress of OLI content resource processing/loading
 *
 * @author Raphael Gachuhi
 */
public enum BuildStatus {
    PROCESSING,
    READY,
    FAILED
}
