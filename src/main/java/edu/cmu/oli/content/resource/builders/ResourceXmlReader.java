package edu.cmu.oli.content.resource.builders;

import com.google.gson.JsonObject;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.Resource;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Raphael Gachuhi
 */
public class ResourceXmlReader {

    private static final Logger log = LoggerFactory.getLogger(ResourceXmlReader.class);

    public static void documentToResource(Resource rsrc, Document doc) {
        // Parse common resource markup
        Element rootElmnt = doc.getRootElement();
        String id = rootElmnt.getAttributeValue("id");
        rsrc.setId(id);
        String version = rootElmnt.getAttributeValue("version");
        if (version != null && !version.isEmpty()) {
            JsonObject metadata = new JsonObject();
            metadata.addProperty("version", version);
            rsrc.setMetadata(new JsonWrapper(metadata));
        }
        parseResourceElement(rsrc, rootElmnt);
    }

    private static void parseResourceElement(Resource rsrc, Element rscsElmnt) {
        XPathFactory xFactory = XPathFactory.instance();
        XPathExpression<Element> xexpression = xFactory.compile("//title", Filters.element());
        List<Element> kids = xexpression.evaluate(rscsElmnt);
        if (kids != null && !kids.isEmpty()) {
            String title = kids.get(0).getTextNormalize();
            rsrc.setTitle(title);
        }
        xexpression = xFactory.compile("//short_title", Filters.element());
        kids = xexpression.evaluate(rscsElmnt);
        if (kids != null && !kids.isEmpty()) {
            String shortTitle = kids.get(0).getTextNormalize();
            if (shortTitle != null && shortTitle.length() > 30) {
                shortTitle = shortTitle.substring(0, 26) + "...";
            }
            rsrc.setShortTitle(shortTitle);
        }

    }
}
