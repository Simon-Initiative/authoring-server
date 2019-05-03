package edu.cmu.oli.content.resource.validators;

import com.google.gson.JsonObject;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.*;
import edu.cmu.oli.content.resource.builders.ContentBuildUtils;
import org.jdom2.*;
import org.jdom2.filter.Filters;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Raphael Gachuhi
 */
public class BaseResourceValidator implements ResourceValidator {
    private static final Logger log = LoggerFactory.getLogger(BaseResourceValidator.class);
    protected Resource rsrc;
    protected Document doc;
    protected boolean throwErrors;

    private XPathFactory xFactory = XPathFactory.instance();

    public BaseResourceValidator() {
    }

    @Override
    public void initValidator(Resource rsrc, Document doc, boolean throwErrors) {
        this.rsrc = rsrc;
        this.doc = doc;
        this.throwErrors = throwErrors;
    }

    @Override
    public void validate() {
        validateActivity();
    }

    private void validateActivity() {
        if (rsrc.getType().equalsIgnoreCase("x-oli-assessment2") ||
                rsrc.getType().equalsIgnoreCase("x-oli-inline-assessment") ||
                rsrc.getType().equalsIgnoreCase("x-oli-assessment2-pool")) {
            fixEmptyPart();
        }
        fixEmptyElements();
        validateActivity(false);
    }

    private void fixEmptyPart() {
        XPathExpression<Element> xexpression = xFactory.compile("//part", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);
        kids.forEach(element -> {
            if (!element.hasAttributes() && element.getContent().isEmpty()) {
                String s = (UUID.randomUUID()).toString();
                element.setAttribute("id", s.substring(s.lastIndexOf("-")));
            }
        });
    }

    private void fixEmptyElements() {
        XPathExpression<Element> xexpression = xFactory.compile("//p | //em | //th | //td | //sub | //sup " +
                "| //li | //var | //bdo | //cite | //code | //formula | //ipa | //quote | //foreign", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);
        kids.forEach(element -> {
            if (!element.hasAttributes() && element.getContent().isEmpty()) {
                element.setAttribute("empty", "true");
            }
        });
    }

    // :TODO: The following items could be validated, but are not currently.
    // - cite/@entry must reference /workbook_page/bib:file/bib:entry/@id
    // - line/@speaker must reference ../speaker/@id or ../../speakers/speaker/@id
    public void validateActivity(boolean extendedValidation) {

        // Validate standardized activity markup
        validateDocType();
        validateParagraphs();
        validateTitlesAndCaptions();
        validateImages();
        validateAudio();
        validateDirector();
        validateFlash();
        validateMathematica();
        validateUnity();
        validateVideo();
        validateLinks();
        validatePopouts();
        validateMiscAudio();
        validateDynamicPaths();
        validateAlternatives();

        indexObjectives();
        indexSkills();

        // Extended checks for spec. compliance
        if (extendedValidation) {
            validateDefinitions();
            validateMixedContent();
            validateSpeakers();
        }

        createWebContentRefs();
        createLearningObjectiveDependencies();
    }


    public void validateDocType() {

        // Validate document type definition
        DocType docType = doc.getDocType();
        if (docType != null && docType.getPublicID() == null) {
            final StringBuilder message = new StringBuilder();
            message.append("Document does not contain PUBLIC document ");
            message.append("type declaration");
            recordError(message.toString(), ErrorLevel.ERROR);
        }
    }

    public void validateParagraphs() {
        // Locate all paragraphs in document
        XPathExpression<Element> xexpression = xFactory.compile("//p", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each paragraph...
        for (Element childElmnt : kids) {
            // Does element have children or contain text?
            if (isEmptyElement(childElmnt)) {
                final StringBuilder message = new StringBuilder();
                message.append("Document contains empty paragraph(s): ");
                recordError(message.toString(), ErrorLevel.WARN);
            }
        }
    }

    public void validateTitlesAndCaptions() {

        // Locate all titles and captions in document
        XPathExpression<Element> xexpression = xFactory.compile("//title | //caption", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each title or caption...
        for (Element childElmnt : kids) {

            // :FIXME: temporary work-around to skip workbook page
            // objectives (remove once overview activity is complete)
            Element parentElmnt = (Element) childElmnt.getParent();
            if ("objective".equals(parentElmnt.getName())) {
                continue;
            }

            // Does element have children or contain text?
            if (isEmptyElement(childElmnt)) {
                final StringBuilder message = new StringBuilder();
                message.append("Document contains empty titles or captions: ");
                recordError(message.toString(), ErrorLevel.WARN);
            }
        }
    }

    public void validateDynamicPaths() {
        // Locate dynamic path elements in document
        XPathExpression<Element> xexpression = xFactory.compile("//path", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each dynamic path...
        for (Element childElmnt : kids) {
            String name = childElmnt.getName();
            String href = childElmnt.getAttributeValue("href");

            // Does the destination file exist?
            validateFileSource(name, href, null);
        }
    }

    public void validateImages() {

        // Locate image elements in document
        XPathExpression<Element> xexpression = xFactory.compile("//image", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        boolean altTextwarn = false;

        // For each image...
        for (Element childElmnt : kids) {

            // Validate the image
            validateImage(childElmnt);

            // Is alternate text specified for image?
            String alt = childElmnt.getAttributeValue("alt");

            if (!altTextwarn && ((alt == null) || "".equals(alt.trim()))) {
                final StringBuilder message = new StringBuilder();
                message.append("Alternate text not specified for image(s): ");
                message.append("imagehref=").append(childElmnt.getAttributeValue("src"));
                recordError(message.toString(), ErrorLevel.WARN);
                altTextwarn = true;
            }
        }
    }

    public void validateImage(Element childElmnt) {
        if (childElmnt == null) {
            throw (new NullPointerException("'childElmnt' cannot be null"));
        }

        String name = childElmnt.getName();
        String src = childElmnt.getAttributeValue("src");

        if (src == null) {
            src = childElmnt.getText();
        }

        // Does the image reference web content?
        validateFileSource(name, src, null);

        // Are height and width specified? Are they valid?
        validateHeightWidth(childElmnt, false, src);

        // :TODO: check image MIME type against list on config
    }

    public void validateAudio() {

        // Locate audio elements in document
        XPathExpression<Element> xexpression = xFactory.compile("//audio", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each audio...
        for (Element childElmnt : kids) {
            String name = childElmnt.getName();
            String src = childElmnt.getAttributeValue("src");
            String type = childElmnt.getAttributeValue("type");

            // Does the path reference web content?
            validateFileSource(name, src, type);
        }
    }

    public void validateFlash() {

        // Locate flash elements in document

        XPathExpression<Element> xexpression = xFactory.compile("//flash", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each flash movie...
        for (Element childElmnt : kids) {
            validateFlash(childElmnt);
        }
    }

    public void validateFlash(Element childElmnt) {
        if (childElmnt == null) {
            throw (new NullPointerException("'childElmnt' cannot be null"));
        }

        String name = childElmnt.getName();
        String src = childElmnt.getAttributeValue("src");
        String type = "application/x-shockwave-flash";

        // Does the path reference web content?
        validateFileSource(name, src, type);

        // Are height and width specified? Are they valid?
        validateHeightWidth(childElmnt, true, src);

        // Was a problem or question file specified?

        XPathExpression<Element> xexpression = xFactory.compile("params/param[@name='question_file']", Filters.element());
        Element paramElmnt = xexpression.evaluateFirst(childElmnt);

        if (paramElmnt != null) {
            String paramName = paramElmnt.getAttributeValue("name");
            String paramPath = paramElmnt.getText();

            // Does the path reference web content?
            validateFileSource(paramName, paramPath, "text/xml");
        }
    }

    public void validateDirector() {
        // Locate director elements in document

        XPathExpression<Element> xexpression = xFactory.compile("//director", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each movie...
        for (Element childElmnt : kids) {
            String name = childElmnt.getName();
            String src = childElmnt.getAttributeValue("src");
            String type = "application/x-director";

            // Does the path reference web content?
            validateFileSource(name, src, type);

            // Are height and width specified? Are they valid?
            validateHeightWidth(childElmnt, true, src);
        }
    }

    public void validateMathematica() {
        // Locate mathematica elements in document

        XPathExpression<Element> xexpression = xFactory.compile("//mathematica", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each video...
        for (Element childElmnt : kids) {
            String name = childElmnt.getName();
            String src = childElmnt.getAttributeValue("src");

            // Does the path reference web content?
            validateFileSource(name, src, null);

            // Are height and width specified? Are they valid?
            validateHeightWidth(childElmnt, true, src);
        }
    }

    public void validateUnity() {

        // Locate unity elements in document

        XPathExpression<Element> xexpression = xFactory.compile("//unity", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each unity...
        for (Element childElmnt : kids) {
            String name = childElmnt.getName();
            String src = childElmnt.getAttributeValue("src");
            String type = "application/vnd.unity";

            // Does the path reference web content?
            validateFileSource(name, src, type);

            // Are height and width specified? Are they valid?
            validateHeightWidth(childElmnt, true, src);
        }
    }

    public void validateVideo() {
        // Locate video elements in document

        XPathExpression<Element> xexpression = xFactory.compile("//video", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each video...
        for (Element childElmnt : kids) {
            String name = childElmnt.getName();
            String src = childElmnt.getAttributeValue("src");
            String type = childElmnt.getAttributeValue("type");

            if (src != null) {
                // Does the path reference web content?
                validateFileSource(name, src, type);
            } else {
                for (Element oneLevelDeep : (childElmnt.getChildren())) {
                    String name2 = oneLevelDeep.getName();
                    String src2 = oneLevelDeep.getAttributeValue("src");
                    if (src == null) {
                        src = src2;
                    }
                    String type2 = oneLevelDeep.getAttributeValue("type");
                    // Does the path reference web content?
                    validateFileSource(name2, src2, type2);
                }
            }

            // Are height and width specified? Are they valid?
            validateHeightWidth(childElmnt, true, src);

            // Does the video include an HREF (i.e. poster frame)?
            String href = childElmnt.getAttributeValue("href");
            if (href != null) {
                validateFileSource(name, href, type);
            }
        }
    }

    public void validateLinks() {

        // Locate link elements in document

        XPathExpression<Element> xexpression = xFactory.compile("//link", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each link...
        for (Element childElmnt : kids) {
            String name = childElmnt.getName();
            String href = childElmnt.getAttributeValue("href");

            // Email links are not supported
            if (href.startsWith("mailto:")) {
                final StringBuilder message = new StringBuilder();
                message.append("Email links are not recommended: ");
                message.append(name).append("=").append(href);
                recordError(message.toString(), ErrorLevel.WARN);
                // Otherwise, validate the file source
            } else {
                validateFileSource(name, href, null);
            }

            // Link must contain anchor text
            if (isEmptyElement(childElmnt)) {
                final StringBuilder message = new StringBuilder();
                message.append("No title provided for link: ");
                message.append(name).append("=").append(href);
                recordError(message.toString(), ErrorLevel.WARN);
            }
        }
    }

    public void validateSpeakers() {

        // Locate speaker elements in document

        XPathExpression<Element> xexpression = xFactory.compile("//speaker", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each speaker...
        for (Element childElmnt : kids) {
            String name = childElmnt.getName();
            String childId = childElmnt.getAttributeValue("id");
            String src = childElmnt.getAttributeValue("src");

            // Is an image provided for the speaker?
            if (src != null) {
                // Does the speaker reference web content?
                validateFileSource(name, src, null);
            }

            // Was an image provided?
            Element imgElmnt = childElmnt.getChild("image");
            if (imgElmnt != null) {
                // If an image is provided, it must be the first child
                if (childElmnt.indexOf(imgElmnt) > 0) {
                    final StringBuilder message = new StringBuilder();
                    message.append("Image must be first child of speaker: ");
                    message.append("speaker=").append(childId);
                    recordError(message.toString(), ErrorLevel.ERROR);
                }

                // Only a single image may be provided...
                if (childElmnt.getChildren("image").size() > 1) {
                    final StringBuilder message = new StringBuilder();
                    message.append("Speaker may only contain a single image: ");
                    message.append("speaker=").append(childId);
                    recordError(message.toString(), ErrorLevel.ERROR);
                }
            }
        }
    }

    public void validateMiscAudio() {

        // Locate audio elements in document

        XPathExpression<Element> xexpression = xFactory.compile("//pronunciation | //conjugate", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each audio...
        for (Element childElmnt : kids) {
            String name = childElmnt.getName();
            String src = childElmnt.getAttributeValue("src");
            String type = childElmnt.getAttributeValue("type");

            // Was an audio file provided for element?
            if (src != null && type != null) {
                // Does the path reference web content?
                validateFileSource(name, src, type);
            } else if (src == null && type == null) {
                // Audio file not specified

            } else if (src == null || type == null) {
                final StringBuilder message = new StringBuilder();
                message.append("Source or MIME type not specified: ");
                message.append("element=").append(name);
                recordError(message.toString(), ErrorLevel.WARN);
            }
        }
    }

    public void validateDefinitions() {
        // Locate all definitions in document

        XPathExpression<Element> xexpression = xFactory.compile("//definition", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each definition...
        for (Element childElmnt : kids) {
            String name = childElmnt.getName();
            Element termElmnt = childElmnt.getChild("term");

            // Definition must include the term being defined
            if (termElmnt != null) {
                if (isEmptyElement(termElmnt)) {
                    final StringBuilder message = new StringBuilder();
                    message.append("Definition must specify the term being defined: ");
                    message.append("element=").append(name);
                    recordError(message.toString(), ErrorLevel.ERROR);
                }
            } else {
                String term = childElmnt.getAttributeValue("term");
                if (term == null || "".equals(term.trim())) {
                    final StringBuilder message = new StringBuilder();
                    message.append("Definition must specify the term being defined: ");
                    message.append("element=").append(name);
                    recordError(message.toString(), ErrorLevel.ERROR);
                }
            }
        }
    }

    public void validateMixedContent() {
        // Locate all mixed content in document

        XPathExpression<Element> xexpression = xFactory.compile("//code | //formula | //quote", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each mixed content element...
        for (Element childElmnt : kids) {
            String name = childElmnt.getName();

            // Was a title provided?
            Element titleElmnt = childElmnt.getChild("title");
            if (titleElmnt != null) {
                // If a title is provided, it must be the first child
                int idx = -1;
                for (Iterator j = childElmnt.getChildren().iterator(); j.hasNext(); ) {
                    idx++;
                    if (j.next().equals(titleElmnt)) {
                        break;
                    }
                }
                if (idx != 0) {
                    final StringBuilder message = new StringBuilder();
                    message.append("Title element must be the first child: ");
                    message.append("element=").append(name);
                    recordError(message.toString(), ErrorLevel.ERROR);
                }

                // Only a single title may be provided...
                if (childElmnt.getChildren("title").size() > 1) {
                    final StringBuilder message = new StringBuilder();
                    message.append("Element may only contain a single title: ");
                    message.append("element=").append(name);
                    recordError(message.toString(), ErrorLevel.ERROR);
                }
            }
        }
    }

    public void validatePopouts() {
        // Locate all popout directives in document

        XPathExpression<Element> xexpression = xFactory.compile("//popout", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each popout element...
        for (Element childElmnt : kids) {
            Element parentElmnt = childElmnt.getParentElement();
            String parentName = parentElmnt.getName();

            // Validate height and width
            String src;
            if ("applet".equals(parentName)) {
                src = parentElmnt.getAttributeValue("code");
            } else {
                src = parentElmnt.getAttributeValue("src");
            }
            validateHeightWidth(childElmnt, false, src);

            // ID must be provided for non-image pop-outs
            String enable = childElmnt.getAttributeValue("enable");
            if (!"image".equals(parentName) && "true".equals(enable)) {
                if (parentElmnt.getAttributeValue("id") == null) {
                    final StringBuilder message = new StringBuilder();
                    message.append("When popout is enabled, ").append(parentName);
                    message.append(" must specify an ID: ");
                    message.append("src=").append(src);
                    recordError(message.toString(), ErrorLevel.ERROR);
                }
            }
        }
    }

    public String validateIdentifier(Element elmnt, String attr) {

        if (elmnt == null) {
            throw (new NullPointerException("'elmnt' cannot be null"));
        } else if (attr == null) {
            throw (new NullPointerException("'attr' cannot be null"));
        }

        String id = elmnt.getAttributeValue(attr);

        // Is the element ID a valid identifier?
        if (!ContentBuildUtils.isValidIdentifier(id)) {
            final StringBuilder message = new StringBuilder();
            message.append("Element has an invalid identifier: ");
            message.append("element=").append(elmnt.getName()).append(", ");
            message.append(attr).append("=").append(id);
            recordError(message.toString(), ErrorLevel.ERROR);

//		// Is the element ID a good identifier?
//		} else if (ContentBuildUtils.isMixedCase(id)) {
//			final StringBuilder message = new StringBuilder();
//			message.append("Identifier mixes upper and lower case letters: ");
//			message.append("element=").append(elmnt.getDisplayName()).append(", ");
//			message.append(attr).append("=").append(id).append(", ");
//			message.append("resource=").append(rsrc.getId()).append(", ");
//			message.append("href=").append(rsrc.getHref());
//			log.warn(message);
        } else if (id.length() < 3) {
            final StringBuilder message = new StringBuilder();
            message.append("Identifier contains less than 3 characters: ");
            message.append("element=").append(elmnt.getName()).append(", ");
            message.append(attr).append("=").append(id);
            recordError(message.toString(), ErrorLevel.WARN);

        } else if (id.length() > 50) {
            final StringBuilder message = new StringBuilder();
            message.append("Identifier contains more than 50 characters: ");
            message.append("element=").append(elmnt.getName()).append(", ");
            message.append(attr).append("=").append(id);
            recordError(message.toString(), ErrorLevel.WARN);
        }

        return id;
    }

    public void validateFileSource(String name, String src, String type) {

        if (name == null) {
            throw (new NullPointerException("'name' cannot be null"));
        }

        // Was a file source specified?
        if (src == null || "".equals(src.trim())) {
            final StringBuilder message = new StringBuilder();
            message.append("File path not specified for ");
            message.append(name);
            recordError(message.toString(), ErrorLevel.ERROR);

            // Does the path references a remote server
        } else if (src.contains("://")) {
            if (!"link".equals(name)) {
                final StringBuilder message = new StringBuilder();
                message.append("File is loaded from a remote server: ");
                message.append(name).append("=").append(src);
                recordError(message.toString(), ErrorLevel.WARN);
            }

            // Is the path an absolute path?
        } else if (src.charAt(0) == '/') {
            final StringBuilder message = new StringBuilder();
            message.append("Absolute paths not supported. Web content ");
            message.append("paths should be relative to the resource: ");
            message.append(name).append("=").append(src);
            recordError(message.toString(), ErrorLevel.WARN);

            // Path references web content
        } else {
            // Does the path contain parameters?
            if (src.contains("?")) {
                final StringBuilder message = new StringBuilder();
                message.append("Web content path contains parameters: ");
                message.append(name).append("=").append(src);
                recordError(message.toString(), ErrorLevel.ERROR);
            }

            // Does the path contain an anchor?
            if (src.contains("#")) {
                final StringBuilder message = new StringBuilder();
                message.append("Web content path contains an anchor: ");
                message.append(name).append("=").append(src);
                recordError(message.toString(), ErrorLevel.ERROR);
            }
        }
    }

    public void validateHeightWidth(Element element, boolean required, String src) {

        if (element == null) {
            throw (new NullPointerException("'element' cannot be null"));
        } else if (src == null) {
            throw (new NullPointerException("'src' cannot be null"));
        }

        String name = element.getName();

        // If height is specified, is it a positive integer?
        Attribute heightAttr = element.getAttribute("height");

        if (heightAttr != null) {
            try {
                int height = heightAttr.getIntValue();

                // Is the value a positive integer?
                if (height < 0) {
                    final StringBuilder message = new StringBuilder();
                    message.append("Height must be a positive integer: ");
                    message.append("height=").append(height).append(", ");
                    message.append(name).append("=").append(src);
                    recordError(message.toString(), ErrorLevel.ERROR);
                }

                // Is height too large for most displays?
                if (height > 500) {
                    final StringBuilder message = new StringBuilder();
                    message.append("Height too large for most displays: ");
                    message.append("height=").append(height).append(", ");
                    message.append(name).append("=").append(src);
                    recordError(message.toString(), ErrorLevel.WARN);
                }
            } catch (DataConversionException e) {
                final StringBuilder message = new StringBuilder();
                message.append("Height must be a positive integer: ");
                message.append("height=").append(heightAttr.getValue()).append(", ");
                message.append(name).append("=").append(src);
                recordError(message.toString(), ErrorLevel.ERROR);
            }
        } else if (required) {
            final StringBuilder message = new StringBuilder();
            message.append("Height not specified: ");
            message.append(name).append("=").append(src);
            recordError(message.toString(), ErrorLevel.ERROR);
        }

        // If width is specified, is it a positive integer?
        Attribute widthAttr = element.getAttribute("width");

        if (widthAttr != null) {
            try {
                int width = widthAttr.getIntValue();

                // Is the value a positive integer?
                if (width < 0) {
                    final StringBuilder message = new StringBuilder();
                    message.append("Width must be a positive integer: ");
                    message.append("width=").append(width).append(", ");
                    message.append(name).append("=").append(src);
                    recordError(message.toString(), ErrorLevel.ERROR);
                }

                // Is width too large for most displays?
                if (width > 700) {
                    final StringBuilder message = new StringBuilder();
                    message.append("Width too large for most displays: ");
                    message.append("width=").append(width).append(", ");
                    message.append(name).append("=").append(src);
                    recordError(message.toString(), ErrorLevel.WARN);
                }
            } catch (DataConversionException e) {
                final StringBuilder message = new StringBuilder();
                message.append("Width must be a positive integer: ");
                message.append("width=").append(widthAttr.getValue()).append(", ");
                message.append(name).append("=").append(src);
                recordError(message.toString(), ErrorLevel.ERROR);
            }
        } else if (required) {
            final StringBuilder message = new StringBuilder();
            message.append("Width not specified: ");
            message.append(name).append("=").append(src);
            recordError(message.toString(), ErrorLevel.ERROR);
        }
    }

    public void validateAlternatives() {

        // Locate alternatives elements in document

        XPathExpression<Element> xexpression = xFactory.compile("//alternatives", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each link...
        for (Element tabsElmnt : kids) {
            Collection tabElmnts = tabsElmnt.getChildren("alternate");
            Set<String> values = new HashSet<>();
            for (Iterator j = tabElmnts.iterator(); j.hasNext(); ) {
                Element tabElmnt = (Element) j.next();
                String value = tabElmnt.getAttributeValue("value");
                if (value == null || "".equals(value.trim())) {
                    final StringBuilder message = new StringBuilder();
                    message.append("Alternative value not specified: ");
                    recordError(message.toString(), ErrorLevel.ERROR);
                } else if (!values.add(value)) {
                    final StringBuilder message = new StringBuilder();
                    message.append("Values must be unique within a alternatives group: ");
                    message.append("value=").append(value);
                    recordError(message.toString(), ErrorLevel.ERROR);
                }
            }
        }
    }

    public void recordError(String errorString, ErrorLevel errorLevel) {
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

    public void indexObjectives() {
        // Locate objective elements in document

        XPathExpression<Element> xexpression = xFactory.compile("//objective", Filters.element());
        List<Element> objectives = xexpression.evaluate(doc);

        ContentPackage pkg = rsrc.getContentPackage();
        Set<Edge> filteredEdges = pkg.getEdges().stream().filter(e -> e.getSourceId()
                .equalsIgnoreCase(pkg.getId() + ":" + pkg.getVersion() + ":" + rsrc.getId())
                && e.getReferenceType().equals("objective"))
                .collect(Collectors.toSet());

        JsonObject objectiveIndexTemp = new JsonObject();
        if (pkg.getObjectivesIndex() != null) {
            objectiveIndexTemp = (JsonObject) pkg.getObjectivesIndex().getJsonObject();
        }
        JsonObject objectiveIndex = objectiveIndexTemp;

        Set<String> previousObjectivesIds = objectiveIndex.entrySet().stream()
                .filter(e -> ((JsonObject) e.getValue()).get("resourceId").getAsString().equals(rsrc.getId()))
                .map(e -> e.getKey()).collect(Collectors.toSet());

        // For each objective
        for (Element objectiveElem : objectives) {
            String id = objectiveElem.getAttributeValue("id");

            JsonObject objective = new JsonObject();
            objective.addProperty("objectiveText", new XMLOutputter(Format.getCompactFormat()).outputString(objectiveElem.getContent()));
            objective.addProperty("resourceId", rsrc.getId());

            JsonObject params = new JsonObject();
            objectiveElem.getAttributes().forEach(attribute -> {
                if (!attribute.getName().equalsIgnoreCase("id")) {
                    params.addProperty(attribute.getName(), attribute.getValue());
                }
            });
            objective.add("parameters", params);

            objectiveIndex.add(id, objective);

            previousObjectivesIds.remove(id);

            if (rsrc.getType().equalsIgnoreCase("x-oli-workbook_page")) {
                Edge edge = new Edge(EdgeType.SUPPORTS,
                        pkg.getId() + ":" + pkg.getVersion() + ":" + rsrc.getId(),
                        pkg.getId() + ":" + pkg.getVersion() + ":" + objectiveElem.getAttributeValue("id"),
                        rsrc.getType(), "x-oli-objective", objectiveElem.getName());
                edge.setContentPackage(pkg);
                JsonObject metadata = new JsonObject();
                JsonObject path = new JsonObject();
                AppUtils.pathToRoot(objectiveElem, path);
                metadata.add("pathInfo", path);
                edge.setMetadata(new JsonWrapper(metadata));
                pkg.addEdge(edge);

                filteredEdges.remove(edge);
            }
        }

        // Cleanup objective indices that are no longer defined in the resource
        previousObjectivesIds.forEach(e -> objectiveIndex.remove(e));

        // Cleanup objective edges that are no longer defined in a workbook page
        filteredEdges.forEach(e -> pkg.removeEdge(e));

        if (!objectiveIndex.entrySet().isEmpty()) {
            pkg.setObjectivesIndex(new JsonWrapper(objectiveIndex));
        }
    }

    public void indexSkills() {
        // Locate Skills elements in document
        XPathExpression<Element> xexpression = xFactory.compile("//skill", Filters.element());
        List<Element> objectives = xexpression.evaluate(doc);

        ContentPackage pkg = rsrc.getContentPackage();

        JsonObject skillIndexTemp = new JsonObject();
        if (pkg.getSkillsIndex() != null) {
            skillIndexTemp = (JsonObject) pkg.getSkillsIndex().getJsonObject();
        }
        JsonObject skillIndex = skillIndexTemp;
        Set<String> previousSkills = skillIndex.entrySet().stream()
                .filter(e -> ((JsonObject) e.getValue()).get("resourceId").getAsString().equals(rsrc.getId()))
                .map(e -> e.getKey()).collect(Collectors.toSet());

        // For each objective
        for (Element skillElem : objectives) {
            String id = skillElem.getAttributeValue("id");

            JsonObject skill = new JsonObject();
            skill.addProperty("skillText", skillElem.getText());
            skill.addProperty("resourceId", rsrc.getId());
            JsonObject params = new JsonObject();
            skillElem.getAttributes().forEach(attribute -> {
                if (!attribute.getName().equalsIgnoreCase("id")) {
                    params.addProperty(attribute.getName(), attribute.getValue());
                }
            });
            skill.add("parameters", params);

            skillIndex.add(id, skill);

            previousSkills.remove(id);
        }

        // Cleanup skill indices that are no longer defined in by resource
        previousSkills.forEach(e -> skillIndex.remove(e));

        if (!skillIndex.entrySet().isEmpty()) {
            pkg.setSkillsIndex(new JsonWrapper(skillIndex));
        }
    }

    private void createLearningObjectiveDependencies() {
        // Locate Objective references elements in document
        XPathExpression<Element> xexpression = xFactory.compile("//objref", Filters.element());
        List<Element> objrefs = xexpression.evaluate(doc);

        ContentPackage pkg = rsrc.getContentPackage();
        Set<Edge> filteredEdges = pkg.getEdges().stream().filter(e -> e.getSourceId()
                .equalsIgnoreCase(pkg.getId() + ":" + pkg.getVersion() + ":" + rsrc.getId())
                && e.getReferenceType().equals("objref"))
                .collect(Collectors.toSet());

        for (Element objrefElem : objrefs) {
            Edge edge = new Edge(EdgeType.SUPPORTS,
                    pkg.getId() + ":" + pkg.getVersion() + ":" + rsrc.getId(),
                    pkg.getId() + ":" + pkg.getVersion() + ":" + objrefElem.getAttributeValue("idref"),
                    rsrc.getType(), "x-oli-objective", objrefElem.getName());
            JsonObject metadata = new JsonObject();
            JsonObject path = new JsonObject();
            AppUtils.pathToRoot(objrefElem, path);
            metadata.add("pathInfo", path);
            edge.setMetadata(new JsonWrapper(metadata));
            edge.setContentPackage(pkg);
            pkg.addEdge(edge);

            filteredEdges.remove(edge);
        }

        // Cleanup objective edges that are no longer referenced in resource
        filteredEdges.forEach(e -> pkg.removeEdge(e));
    }

    private void createWebContentRefs() {
        ContentPackage pkg = rsrc.getContentPackage();
        Set<Edge> filteredEdges = pkg.getEdges().stream().filter(e -> e.getSourceId()
                .equalsIgnoreCase(pkg.getId() + ":" + pkg.getVersion() + ":" + rsrc.getId())
                && e.getDestinationType() != null && e.getDestinationType().equals("x-oli-webcontent"))
                .collect(Collectors.toSet());

        XPathExpression<Element> xexpression = xFactory.compile("//*[contains(@src, 'webcontent/')] | //*[contains(text(),'webcontent/')]", Filters.element());
        List<Element> elements = xexpression.evaluate(doc);

        for (Element childElem : elements) {
            List<Attribute> ats = childElem.getAttributes();
            String value = null;
            for (Attribute a : ats) {
                if (a.getValue().contains("webcontent/")) {
                    value = a.getValue();
                    break;
                }
            }
            if (value == null) {
                value = childElem.getText();
            }
            if (!value.contains("://") && !value.startsWith("/")
                    && !value.contains("#") && !value.contains("?")) {
                if (value.startsWith("../")) {
                    Path path = Paths.get(pkg.getSourceLocation() + File.separator + rsrc.getFileNode().getPathFrom());
                    value = Paths.get(pkg.getSourceLocation()).relativize(path.getParent().resolve(value).normalize()).toString();
                } else if (value.startsWith("webcontent/")) {
                    value = "content" + File.separator + value;
                } else {
                    continue;
                }

                // Create the resource edge
                Edge edge = new Edge(EdgeType.LINKS,
                        pkg.getId() + ":" + pkg.getVersion() + ":" + rsrc.getId(),
                        pkg.getId() + ":" + pkg.getVersion() + ":" + value,
                        rsrc.getType(), "x-oli-webcontent", childElem.getName());

                JsonObject metadata = new JsonObject();
                JsonObject path = new JsonObject();
                AppUtils.pathToRoot(childElem, path);
                metadata.add("pathInfo", path);
                edge.setMetadata(new JsonWrapper(metadata));
                edge.setContentPackage(pkg);

                pkg.addEdge(edge);

                filteredEdges.remove(edge);
            }
        }

        // Cleanup webcontent edges that are no longer referenced by a resource
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
}
