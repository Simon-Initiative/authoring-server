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

package edu.cmu.oli.feedback.validators;

import com.google.gson.JsonObject;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.*;
import edu.cmu.oli.content.resource.validators.BaseResourceValidator;
import edu.cmu.oli.content.resource.validators.ResourceValidator;
import org.jdom2.Attribute;
import org.jdom2.DataConversionException;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Zach Bluedorn
 */
public class FeedbackValidator implements ResourceValidator {

    private final Logger log = LoggerFactory.getLogger(FeedbackValidator.class);

    protected Resource resource;
    protected Document doc;
    protected boolean throwErrors;

    BaseResourceValidator baseResourceValidator;

    public FeedbackValidator() {
    }

    @Override
    public void initValidator(Resource resource, Document doc, boolean throwErrors) {
        this.resource = resource;
        this.doc = doc;
        this.throwErrors = throwErrors;
        this.baseResourceValidator = new BaseResourceValidator();
        this.baseResourceValidator.initValidator(resource, doc, throwErrors);
    }

    @Override
    public void validate() {
        validateRoot();
        validateBody();
        validateAndParseTitle();
        validateAndParseShortTitle();
        validatePromptsAndLabels();
        validateLikertScales();

        // Create resource edges and dependencies
        createLinkEdges();
    }

    // Validate the root element
    private void validateRoot() {
        Element root = doc.getRootElement();
        if (!"feedback".equals(root.getName())) {
            recordError("Invalid feedback document. Root element must be named 'feedback'.", ErrorLevel.ERROR);
        }
    }

    // Body of document should not be empty
    private void validateBody() {
        Element root = doc.getRootElement();
        Element bodyElmnt = root.getChild("description");

        if (bodyElmnt.getChildren().isEmpty()) {
            recordError("Description is empty.", ErrorLevel.ERROR);
        }
    }

    private void validateAndParseTitle() {
        Element root = doc.getRootElement();
        Element title = root.getChild("title");
        // validate
        if (!title.getChildren().isEmpty()) {
            recordError("Title may contain only text.", ErrorLevel.ERROR);
        }
        // parse
        resource.setTitle(title.getTextNormalize());
    }

    // Parse the short title, if specified
    private void validateAndParseShortTitle() {
        Element root = doc.getRootElement();
        Element shortTitle = root.getChild("short_title");
        // validate
        if (shortTitle != null) {
            if (!shortTitle.getChildren().isEmpty()) {
                recordError("Short title may contain only text.", ErrorLevel.ERROR);
            }
            // parse
            resource.setShortTitle(shortTitle.getTextNormalize());
        }
    }

    // Check for empty prompts and/or labels
    private void validatePromptsAndLabels() {
        XPathFactory xFactory = XPathFactory.instance();
        XPathExpression<Element> xexpression = xFactory.compile("//prompt | //label", Filters.element());
        List<Element> children = xexpression.evaluate(doc);

        for (Element child : children) {
            String text = child.getTextNormalize();
            boolean hasChildren = !child.getChildren().isEmpty();

            // Does element have children or contain text?
            if (!hasChildren && ((text == null) || "".equals(text))) {
                final StringBuilder message = new StringBuilder();
                message.append("Document contains empty ").append(child.getName());
                recordError(message.toString(), ErrorLevel.WARN);
                break;
            }
        }
    }

    private void validateLikertScales() {
        XPathFactory xFactory = XPathFactory.instance();
        XPathExpression<Element> xexpression = xFactory.compile("//likert_scale", Filters.element());
        List<Element> scales = xexpression.evaluate(doc);

        // For each likert scale...
        for (Element scale : scales) {
            int size = 0, center = 0;
            size = parseSize(scale);
            center = parseCenter(scale);
            validateLabels(scale, center, size);
        }
    }

    // Size attribute must be an odd integer
    private int parseSize(Element scale) {
        Attribute sizeAttr = scale.getAttribute("size");
        try {
            int size = sizeAttr.getIntValue();
            if ((size % 2) == 0) {
                recordError("Size of likert scale must be an odd integer.", ErrorLevel.ERROR);
            }
            return size;
        } catch (DataConversionException e) {
            throw new RuntimeException("Size of likert scale must be an odd integer.");
        }
    }

    // Center attribute must be an integer
    private int parseCenter(Element scale) {
        Attribute centerAttr = scale.getAttribute("center");
        try {
            int center = centerAttr.getIntValue();
            return center;
        } catch (DataConversionException e) {
            throw new RuntimeException("Center of likert scale must be an integer.");
        }
    }

    private void validateLabels(Element scale, int center, int size) {
        // Validate the value labels
        Collection<Element> labels = scale.getChildren("label");
        int min = center - (size / 2);
        int max = center + (size / 2);

        for (Element label : labels) {
            int value = 0;

            // Is value attribute an interger?
            Attribute valueAttr = label.getAttribute("value");
            try {
                value = valueAttr.getIntValue();

                // Is the value on the scale?
                if (value < min || value > max) {
                    recordError("Label value not within bounds of likert scale.", ErrorLevel.ERROR);
                }
            } catch (DataConversionException e) {
                recordError("Value of likert scale label must be an integer.", ErrorLevel.ERROR);
            }
        }
    }

    // Replicated from AssessmentV2Validator
    protected void createLinkEdges() {
        // Locate all activity links in document
        XPathFactory xFactory = XPathFactory.instance();
        XPathExpression<Element> xexpression = xFactory.compile("//activity | //activity_link | //alternate ",
                Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        ContentPackage pkg = resource.getContentPackage();

        Set<Edge> filteredEdges = pkg.getEdges().stream().filter(
                e -> e.getSourceId().equalsIgnoreCase(pkg.getId() + ":" + pkg.getVersion() + ":" + resource.getId())
                        && (e.getReferenceType().equals("activity") || e.getReferenceType().equals("activity_link")
                        || e.getReferenceType().equals("alternate")))
                .collect(Collectors.toSet());

        // For each activity link...
        for (Element childElmnt : kids) {
            String name = childElmnt.getName();
            String idref = childElmnt.getAttributeValue("idref");

            if (idref == null) {
                idref = childElmnt.getAttributeValue("id");
            }

            // Activity links must supply a link title...
            if ("activity_link".equals(name)) {
                if (isEmptyElement(childElmnt)) {
                    recordError("No title specified for: idref=" + idref, ErrorLevel.ERROR);
                }
            }

            // Images used within links must provide alternate text
            List<Element> images = childElmnt.getChildren("image");
            for (Element image : images) {
                String src = image.getAttributeValue("src");
                String alt = image.getAttributeValue("alt");

                if (alt == null || "".equals(alt.trim())) {
                    recordError("Alternate text must be provided for linked images: image=" + src, ErrorLevel.ERROR);
                }
            }

            // Create the resource edge
            Edge edge = new Edge(EdgeType.LINKS, pkg.getId() + ":" + pkg.getVersion() + ":" + resource.getId(),
                    pkg.getId() + ":" + pkg.getVersion() + ":" + idref, resource.getType(), null, childElmnt.getName());
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

            } else if ("feedback".equals(name)) {
                edge.setPurpose(PurposeType.myresponse);
            }

            // Assign purpose to edge, if specified
            String purpose = childElmnt.getAttributeValue("purpose");
            if (purpose == null) {
                purpose = childElmnt.getAttributeValue("objective");
            }
            if (purpose != null) {
                try {
                    edge.setPurpose(PurposeType.valueOf(purpose));
                } catch (IllegalArgumentException e) {
                    recordError("Unsupported link purpose specified: purpose=" + purpose, ErrorLevel.ERROR);
                }
            }
        }
        // Cleanup edges that are no longer referenced in resource
        filteredEdges.forEach(e -> pkg.removeEdge(e));
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
        message.append("resource=").append(resource.getId()).append(", ");
        message.append("href=").append(resource.getFileNode().getPathFrom());
        AppUtils.addToResourceError(resource, message.toString(), resource.getId(), errorLevel);
        log.error(message.toString());
        if (throwErrors) {
            throw new RuntimeException(message.toString());
        }
    }
}
