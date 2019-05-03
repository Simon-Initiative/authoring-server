package edu.cmu.oli.content.resource.validators;

import edu.cmu.oli.content.models.persistance.entities.Resource;
import org.jdom2.Document;

/**
 * @author Raphael Gachuhi
 */
public interface ResourceValidator {

    void initValidator(Resource rsrc, Document doc, boolean throwErrors);

    void validate();

}
