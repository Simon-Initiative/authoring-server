/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018. Carnegie Mellon University
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package edu.cmu.oli.workbookpage.validators;

import com.google.gson.JsonObject;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.*;
import edu.cmu.oli.content.resource.validators.BaseResourceValidator;
import edu.cmu.oli.content.resource.validators.ResourceValidator;
import edu.cmu.oli.workbookpage.IdentifiableElement;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Raphael Gachuhi
 */
public class WorkbookPageValidator implements ResourceValidator {

    private static Map<String, Namespace> workbookNamespaces = new HashMap<>();

    static {
        workbookNamespaces.put("wb", Namespace.getNamespace("wb", "http://oli.web.cmu.edu/activity/workbook/"));
        workbookNamespaces.put("bib", Namespace.getNamespace("bib", "http://bibtexml.sf.net/"));
        workbookNamespaces.put("cmd", Namespace.getNamespace("cmd", "http://oli.web.cmu.edu/content/metadata/2.1/"));
        workbookNamespaces.put("m", Namespace.getNamespace("m", "http://www.w3.org/1998/Math/MathML"));
        workbookNamespaces.put("theme", Namespace.getNamespace("theme", "http://oli.web.cmu.edu/presentation/"));
    }

    private static final Logger log = LoggerFactory.getLogger(WorkbookPageValidator.class);
    BaseResourceValidator baseResourceValidator;
    // Set of page elements which are identifiable
    private Set<IdentifiableElement> identElmnts;

    // Cross references, validated during post-processing
    private Map<IdentifiableElement, Resource> xrefToRsrc;


    protected Resource rsrc;
    protected Document doc;
    protected boolean throwErrors;

    private XPathFactory xFactory = XPathFactory.instance();

    public WorkbookPageValidator() {
        this.identElmnts = new HashSet<>();
        this.xrefToRsrc = new HashMap<>();
    }

    @Override
    public void initValidator(Resource rsrc, Document doc, boolean throwErrors) {
        this.rsrc = rsrc;
        this.doc = doc;
        this.throwErrors = throwErrors;
        this.baseResourceValidator = new BaseResourceValidator();
        this.baseResourceValidator.initValidator(rsrc, doc, throwErrors);
    }

    @Override
    public void validate() {

        // Validate the root element
        Element rootElmnt = doc.getRootElement();

        if (!"workbook_page".equals(rootElmnt.getName())) {
            final StringBuilder message = new StringBuilder();
            message.append("Invalid workbook page. Root element must ");
            message.append("be named 'workbook_page'");
            recordError(message.toString(), ErrorLevel.ERROR);
        }

        // Body of document should not be empty
        Element bodyElmnt = rootElmnt.getChild("body");

        if (bodyElmnt.getChildren().isEmpty()) {
            final StringBuilder message = new StringBuilder();
            message.append("Body is empty, document contains no content");
            recordError(message.toString(), ErrorLevel.ERROR);
        }

        // Parse the document title
        Element headElmnt = rootElmnt.getChild("head");
        Element titleElmnt = headElmnt.getChild("title");
        if (!titleElmnt.getChildren().isEmpty()) {
            final StringBuilder message = new StringBuilder();
            message.append("Document title may contain only text, XML was ");
            message.append("found within title");
            recordError(message.toString(), ErrorLevel.ERROR);
        }

        // Parse the short title, if specified
        Element shortElmnt = headElmnt.getChild("short_title");
        if (shortElmnt != null) {
            if (!shortElmnt.getChildren().isEmpty()) {
                final StringBuilder message = new StringBuilder();
                message.append("Short title may contain only text, XML was ");
                message.append("found within title");
                recordError(message.toString(), ErrorLevel.ERROR);
            }
        }

        // Validate workbook page specific markup
        validateWbDynamicPaths();
        //validateFlashTutor(filePath, rsrc, doc);
        validateSectionBodies();

        // :TODO: The following items could be validated, but are not currently.
        // - cite/@entry must reference /workbook_page/bib:file/bib:entry/@id
        // - line/@speaker must reference ../speaker/@id or ../../speakers/speaker/@id

        // Index identifiable page elements
        indexIdentifiableElements();

        // Create resource edges
        createLinkEdges();
        createXRefEdges();
        createInlineEdges();
        validateCommands();

    }

    // =======================================================================
    // Private instance methods
    // =======================================================================
    private void validateSectionBodies() {
        // Locate all section bodies in document
        XPathExpression<Element> xexpression = xFactory.compile("//section/body", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each section body...
        for (Element childElmnt : kids) {
            String sectionId = childElmnt.getAttributeValue("id");

            // Is the section body empty?
            if (childElmnt.getChildren().isEmpty()) {
                final StringBuilder message = new StringBuilder();
                message.append("Empty section body, section contains no content:");
                if (sectionId != null) {
                    message.append("section=").append(sectionId);
                }
                recordError(message.toString(), ErrorLevel.WARN);
            }
        }
    }

    private void validateWbDynamicPaths() {
        // Locate dynamic path elements in document
        XPathExpression<Element> xexpression = xFactory.compile("//wb:path", Filters.element(), null, workbookNamespaces.values());
        List<Element> kids = xexpression.evaluate(doc);

        // For each dynamic path...
        for (Element childElmnt : kids) {
            String name = childElmnt.getName();
            String href = childElmnt.getAttributeValue("href");

            // Does the destination file exist?
            baseResourceValidator.validateFileSource(name, href, null);
        }
    }

    protected void indexIdentifiableElements() {
        // Locate identifiable body elements
        XPathExpression<Element> xexpression = xFactory.compile("/descendant::*[@id]", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each identifiable body element...
        for (Element childElmnt : kids) {
            // :FIXME: ID validation breaks for workbook assessments, id of
            // choice is not required to be unique or well-formed. Validation
            // also results in a large number of warnings for logic.
            // String id = validateIdentifier(childElmnt, "id", rsrc);

            String id = childElmnt.getAttributeValue("id");
            identElmnts.add(new IdentifiableElement(rsrc.getId(), id));
        }
    }

    protected void createLinkEdges() {

        // Locate all activity links in document
        XPathExpression<Element> xexpression = xFactory.compile("//activity | //wb:activity | //activity_link " +
                "| //alternate | //feedback ", Filters.element(), null, workbookNamespaces.values());
        List<Element> kids = xexpression.evaluate(doc);

        ContentPackage pkg = rsrc.getContentPackage();
        Set<Edge> filteredEdges = pkg.getEdges().stream().filter(e -> e.getSourceId()
                .equalsIgnoreCase(pkg.getId() + ":" + pkg.getVersion() + ":" + rsrc.getId())
                && (e.getReferenceType().equals("activity") || e.getReferenceType().equals("activity_link")
                || e.getReferenceType().equals("alternate")) || (e.getReferenceType().equals("feedback")))
                .collect(Collectors.toSet());

        // For each activity link...
        for (Element childElmnt : kids) {
            String name = childElmnt.getName();
            String idref = childElmnt.getAttributeValue("idref");

            // :FIXME: Deprecated, required to support the version 1.0 DTD
            if (idref == null) {
                idref = childElmnt.getAttributeValue("id");
            }

            // Activity links must supply a link title...
            if ("activity_link".equals(name)) {
                if (isEmptyElement(childElmnt)) {
                    final StringBuilder message = new StringBuilder();
                    message.append("No title specified for ").append(name).append(": ");
                    message.append("idref=").append(idref);
                    recordError(message.toString(), ErrorLevel.ERROR);
                }
            }

            // Images used within links must provide alternate text
            Collection imgKids = childElmnt.getChildren("image");
            for (Iterator j = imgKids.iterator(); j.hasNext(); ) {
                Element imgKidElmnt = (Element) j.next();
                String src = imgKidElmnt.getAttributeValue("src");
                String alt = imgKidElmnt.getAttributeValue("alt");

                if (alt == null || "".equals(alt.trim())) {
                    final StringBuilder message = new StringBuilder();
                    message.append("Alternate text must be provided for linked images: ");
                    message.append("image=").append(src);
                    recordError(message.toString(), ErrorLevel.ERROR);
                }
            }


            // Create the resource edge
            Edge edge = new Edge(EdgeType.LINKS,
                    pkg.getId() + ":" + pkg.getVersion() + ":" + rsrc.getId(),
                    pkg.getId() + ":" + pkg.getVersion() + ":" + idref,
                    rsrc.getType(), null, childElmnt.getName());
            JsonObject metadata = new JsonObject();
            JsonObject path = new JsonObject();
            AppUtils.pathToRoot(childElmnt, path);
            metadata.add("pathInfo", path);
            edge.setMetadata(new JsonWrapper(metadata));
            edge.setContentPackage(pkg);
            pkg.addEdge(edge);

            filteredEdges.remove(edge);

            // Some link types have a default purpose
            if ("alternate".equals(name)) {
                edge.setPurpose(PurposeType.alternate);

                // :FIXME: Deprecated, required to support the version 2.x DTD
            } else if ("feedback".equals(name)) {
                edge.setPurpose(PurposeType.myresponse);
            }

            // Assign purpose to edge, if specified
            String purpose = childElmnt.getAttributeValue("purpose");
            // :FIXME: deprecated, required to support the version 1.0 DTD
            if (purpose == null) {
                purpose = childElmnt.getAttributeValue("objective");
            }
            if (purpose != null) {
                try {
                    edge.setPurpose(PurposeType.valueOf(purpose));
                } catch (IllegalArgumentException e) {
                    final StringBuilder message = new StringBuilder();
                    message.append("Unsupported link purpose specified: ");
                    message.append("purpose=").append(purpose).append(", ");
                    message.append(name).append("=").append(idref);
                    recordError(message.toString(), ErrorLevel.ERROR);
                }
            }
        }
        // Cleanup edges that are no longer referenced in resource
        filteredEdges.forEach(e -> pkg.removeEdge(e));
    }

    private void createXRefEdges() {

        // Locate all cross references in document
        XPathExpression<Element> xexpression = xFactory.compile("//xref", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        ContentPackage pkg = rsrc.getContentPackage();

        Set<Edge> filteredEdges = pkg.getEdges().stream().filter(e -> e.getSourceId()
                .equalsIgnoreCase(pkg.getId() + ":" + pkg.getVersion() + ":" + rsrc.getId())
                && e.getReferenceType().equals("xref"))
                .collect(Collectors.toSet());

        // For each cross reference link...
        for (Element childElmnt : kids) {
            String page = childElmnt.getAttributeValue("page");
            if (page == null) {
                page = rsrc.getId();
            }
            String idref = childElmnt.getAttributeValue("idref");

            // Cross reference links must specify a title...
            if (isEmptyElement(childElmnt)) {
                final StringBuilder message = new StringBuilder();
                message.append("No title specified for cross reference: ");
                message.append("xref=").append(page).append(".").append(idref);
                recordError(message.toString(), ErrorLevel.ERROR);
            }

            // Create the resource edge
            Edge edge = new Edge(EdgeType.REFERENCES,
                    pkg.getId() + ":" + pkg.getVersion() + ":" + rsrc.getId(),
                    pkg.getId() + ":" + pkg.getVersion() + ":" + page,
                    rsrc.getType(), null, childElmnt.getName());
            JsonObject metadata = new JsonObject();
            JsonObject path = new JsonObject();
            AppUtils.pathToRoot(childElmnt, path);
            metadata.add("pathInfo", path);
            edge.setMetadata(new JsonWrapper(metadata));
            edge.setContentPackage(pkg);
            pkg.addEdge(edge);

            filteredEdges.remove(edge);

//            // Destination must be a workbook page
//            edgeProto.setDestinationType(getResourceType().getId());

            // Map cross reference for validation
            xrefToRsrc.put(new IdentifiableElement(page, idref), rsrc);
        }
        // Cleanup edges that are no longer referenced in resource
        filteredEdges.forEach(e -> pkg.removeEdge(e));
    }

    private void createInlineEdges() {

        // Locate all inline content in document
        XPathExpression<Element> xexpression = xFactory.compile("//wb:inline | //activity_report", Filters.element(), null, workbookNamespaces.values());
        List<Element> kids = xexpression.evaluate(doc);

        ContentPackage pkg = rsrc.getContentPackage();
        Set<Edge> filteredEdges = pkg.getEdges().stream().filter(e -> e.getSourceId()
                .equalsIgnoreCase(pkg.getId() + ":" + pkg.getVersion() + ":" + rsrc.getId())
                && e.getReferenceType().equals("inline"))
                .collect(Collectors.toSet());

        // For each inline element...
        if (!kids.isEmpty()) {
            Set<String> inlinedIdrefs = new HashSet<String>();
            for (Element childElmnt : kids) {
                String name = childElmnt.getName();
                String idref = childElmnt.getAttributeValue("idref");
                String src = childElmnt.getAttributeValue("src");

                // Is an alternate client specified? Is the path valid?
                if (src != null) {
                    baseResourceValidator.validateFileSource(name, src, null);
                }

                // Are height and width specified? Are they valid?
                baseResourceValidator.validateHeightWidth(childElmnt, false, idref);

                // Was the activity already inlined on this page?
                if (inlinedIdrefs.contains(idref)) {
                    final StringBuilder message = new StringBuilder();
                    message.append("Resource cannot be inlined more than once: ");
                    message.append("idref=").append(idref);
                    recordError(message.toString(), ErrorLevel.ERROR);
                }
                inlinedIdrefs.add(idref);


                // Create the resource edge
                Edge edge = new Edge(EdgeType.INLINES,
                        pkg.getId() + ":" + pkg.getVersion() + ":" + rsrc.getId(),
                        pkg.getId() + ":" + pkg.getVersion() + ":" + idref,
                        rsrc.getType(), null, childElmnt.getName());
                JsonObject metadata = new JsonObject();
                JsonObject path = new JsonObject();
                AppUtils.pathToRoot(childElmnt, path);
                metadata.add("pathInfo", path);
                edge.setMetadata(new JsonWrapper(metadata));
                edge.setContentPackage(pkg);
                pkg.addEdge(edge);

                filteredEdges.remove(edge);

                // Assign purpose to edge, if specified
                String purpose = childElmnt.getAttributeValue("purpose");
                if (purpose != null) {
                    try {
                        edge.setPurpose(PurposeType.valueOf(purpose));
                    } catch (IllegalArgumentException e) {
                        final StringBuilder message = new StringBuilder();
                        message.append("Unsupported link purpose specified: ");
                        message.append("purpose=").append(purpose).append(", ");
                        message.append(name).append("=").append(idref);
                        recordError(message.toString(), ErrorLevel.ERROR);
                    }
                }

                // When processed, inline content is identifiable
                identElmnts.add(new IdentifiableElement(rsrc.getId(), idref));
            }
        }
        // Cleanup edges that are no longer referenced in resource
        filteredEdges.forEach(e -> pkg.removeEdge(e));
    }

    private void validateCommands() {

        // Locate command elements in document
        XPathExpression<Element> xexpression = xFactory.compile("//command[@type != 'broadcast']", Filters.element(), null, workbookNamespaces.values());
        List<Element> kids = xexpression.evaluate(doc);

        // For each command element...
        for (Element childElmnt : kids) {
            String target = childElmnt.getAttributeValue("target");
            IdentifiableElement ie = new IdentifiableElement(rsrc.getId(), target);

            if (!identElmnts.contains(ie)) {
                final StringBuilder message = new StringBuilder();
                message.append("Target of command does not exist: ");
                message.append("target=").append(target);
                recordError(message.toString(), ErrorLevel.ERROR);
            }
        }
    }

    protected boolean isEmptyElement(Element childElmnt) {
        if (childElmnt == null) {
            throw new NullPointerException("'childElmnt' cannot be null");
        }

        String text = childElmnt.getTextNormalize();
        boolean hasChildren = !childElmnt.getChildren().isEmpty();

        // Does element have children or contain text?
        return (!hasChildren && ((text == null) || "".equals(text)));
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
