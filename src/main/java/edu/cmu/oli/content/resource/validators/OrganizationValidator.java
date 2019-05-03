package edu.cmu.oli.content.resource.validators;

import com.google.gson.JsonObject;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.*;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Raphael Gachuhi
 */
public class OrganizationValidator implements ResourceValidator {

    private static final Logger log = LoggerFactory.getLogger(OrganizationValidator.class);


    protected Resource rsrc;
    protected Document doc;
    protected boolean throwErrors;

    private XPathFactory xFactory = XPathFactory.instance();

    public OrganizationValidator() {
    }

    @Override
    public void initValidator(Resource rsrc, Document doc, boolean throwErrors) {
        this.rsrc = rsrc;
        this.doc = doc;
        this.throwErrors = throwErrors;
    }

    @Override
    public void validate() {
        Element rootElmnt = doc.getRootElement();
        if (!"organization".equals(rootElmnt.getName())) {
            final StringBuilder message = new StringBuilder();
            message.append("Invalid document. Root element must ");
            message.append("be named 'organization'");
            recordError(message.toString(), ErrorLevel.ERROR);
        }

        // Parse the document title
        Element titleElmnt = rootElmnt.getChild("title");
        if (!titleElmnt.getChildren().isEmpty()) {
            final StringBuilder message = new StringBuilder();
            message.append("Document title may contain only text, XML was ");
            message.append("found within title");
            recordError(message.toString(), ErrorLevel.ERROR);
        }

        createDependencies();
    }

    private void createDependencies() {
        XPathExpression<Element> xexpression = xFactory.compile("//resourceref", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        ContentPackage pkg = rsrc.getContentPackage();
        Set<Edge> filteredEdges = pkg.getEdges().stream().filter(e -> e.getSourceId()
                .equalsIgnoreCase(pkg.getId() + ":" + pkg.getVersion() + ":" + rsrc.getId())
                && e.getReferenceType().equals("resourceref"))
                .collect(Collectors.toSet());
        for (Element childElem : kids) {
            // Create the resource edge
            Edge edge = new Edge(EdgeType.CONTAINS,
                    pkg.getId() + ":" + pkg.getVersion() + ":" + rsrc.getId(),
                    pkg.getId() + ":" + pkg.getVersion() + ":" + childElem.getAttributeValue("idref"),
                    rsrc.getType(), null, childElem.getName());
            JsonObject metadata = new JsonObject();
            metadata.addProperty("version", doc.getRootElement().getAttributeValue("version"));
            JsonObject path = new JsonObject();
            AppUtils.pathToRoot(childElem, path);
            metadata.add("pathInfo", path);
            edge.setMetadata(new JsonWrapper(metadata));
            edge.setContentPackage(pkg);
            pkg.addEdge(edge);
            filteredEdges.remove(edge);
        }
        // Cleanup edges that are no longer referenced in resource
        filteredEdges.forEach(e -> pkg.removeEdge(e));
    }

    private void recordError(String errorString, ErrorLevel errorLevel) {
        final StringBuilder message = new StringBuilder();
        message.append(errorString).append(", ");
        message.append("resource=").append(rsrc.getId()).append(", ");
        message.append("href=").append(rsrc.getFileNode().getPathFrom());
        AppUtils.addToResourceError(rsrc, message.toString(), rsrc.getId(), errorLevel);
        if ((errorLevel == ErrorLevel.ERROR)) {
            log.error(message.toString());
        } else {
            log.warn(message.toString());
        }
        if (throwErrors && errorLevel == ErrorLevel.ERROR) {
            throw new RuntimeException(message.toString());
        }
    }

}
