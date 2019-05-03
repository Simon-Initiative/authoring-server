package edu.cmu.oli.content.controllers;

import edu.cmu.oli.content.models.persistance.entities.Resource;
import org.jdom2.Element;
import org.jdom2.JDOMException;

import java.io.IOException;

/**
 * @author Raphael Gachuhi
 */
public interface MetadataController {

    Element fetchMetadataForResource(Resource resource, String serverUrl, String themeId) throws JDOMException, IOException;

    Element fetchSyllabusMetadata(Resource resource, String serverUrl, String themeId) throws JDOMException, IOException;
}
