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

package edu.cmu.oli.assessment.validators;

import com.google.gson.*;
import edu.cmu.oli.assessment.InputSize;
import edu.cmu.oli.assessment.InteractionStyle;
import edu.cmu.oli.assessment.ScoringMethod;
import edu.cmu.oli.assessment.evaluators.NumericNotation;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.*;
import edu.cmu.oli.content.resource.validators.BaseResourceValidator;
import edu.cmu.oli.content.resource.validators.ResourceValidator;
import org.jdom2.*;
import org.jdom2.filter.AbstractFilter;
import org.jdom2.filter.Filters;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Raphael Gachuhi
 */
public class AssessmentV1Validator implements ResourceValidator {

    private static final Logger log = LoggerFactory.getLogger(AssessmentV1Validator.class);

    private class ElementNameFilter extends AbstractFilter<Element> {
        private final Set<String> names = new HashSet<String>();

        public ElementNameFilter() {
        }

        public ElementNameFilter(ElementNameFilter f) {
            names.addAll(f.names);
        }

        public ElementNameFilter acceptElementName(String name) {
            names.add(name);
            return this;
        }

        @Override
        public Element filter(Object obj) {
            Element element = obj instanceof Element ? (Element) obj : null;

            if (element != null && names.contains(element.getName())) {
                return element;
            }
            return null;
        }
    }

    private final ElementNameFilter _QUESTION_FILTER = new ElementNameFilter()
            .acceptElementName("multiple_choice")
            .acceptElementName("text")
            .acceptElementName("fill_in_the_blank")
            .acceptElementName("numeric")
            .acceptElementName("essay")
            .acceptElementName("short_answer")
            .acceptElementName("image_hotspot")
            .acceptElementName("ordering")
            .acceptElementName("matrix");

    private final ElementNameFilter _ITEM_FILTER = new ElementNameFilter(_QUESTION_FILTER)
            .acceptElementName("question_bank_ref");


    private final ElementNameFilter _NODE_FILTER = new ElementNameFilter(_ITEM_FILTER)
            .acceptElementName("content")
            .acceptElementName("selection")
            .acceptElementName("section");

    private final ElementNameFilter _PART_FILTER = new ElementNameFilter()
            .acceptElementName("responses")            // version 2.0, deprecated
            .acceptElementName("part"); // version 2.1

    protected Resource rsrc;
    protected Document doc;
    protected boolean throwErrors;

    private XPathFactory xFactory = XPathFactory.instance();
    private XMLOutputter outputter = new XMLOutputter(Format.getCompactFormat());

    BaseResourceValidator baseResourceValidator;

    public AssessmentV1Validator() {
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
        if (!("assessment".equals(rootElmnt.getName()) || "pool".equals(rootElmnt.getName()))) {
            recordError("Invalid assessment activity. Root element must be named 'assessment or pool':", ErrorLevel.ERROR);
        }

        // Parse the document title
        Element titleElmnt = rootElmnt.getChild("title");
        if (!titleElmnt.getChildren().isEmpty()) {
            recordError("Document title may contain only text, XML was found within title: ", ErrorLevel.ERROR);
        }

        // Parse the short title, if specified
        Element shortElmnt = rootElmnt.getChild("short_title");
        if (shortElmnt != null) {
            if (!shortElmnt.getChildren().isEmpty()) {
                recordError("Short title may contain only text, XML was found within title", ErrorLevel.ERROR);
            }
        }

        validateImageInputs();

        if ("pool".equals(rootElmnt.getName())) {
            parsePool(rootElmnt);
        }

        if ("question_bank".equals(rootElmnt.getName())) {
            parseQuestionBank(rootElmnt);
        }

        createLinkEdges();
        createDependencies();
        createSkillsDependencies();
    }

    public JsonObject parseAssessment(Document doc) {

        if (doc == null) {
            throw (new NullPointerException("'doc' cannot be null"));
        }

        Element quizElem = doc.getRootElement();
        if (!"assessment".equals(quizElem.getName())) {
            final String message = "Document is not an assessment, wrong element: ";
            recordError(message + quizElem.getName(), ErrorLevel.ERROR);
        }

        return parseAssessment(quizElem);
    }

    public JsonObject parsePool(Document doc) {
        if (doc == null) {
            throw (new NullPointerException("'doc' cannot be null"));
        }

        Element poolElem = doc.getRootElement();
        if (!"pool".equals(poolElem.getName())) {
            final String message = "Document is not an assessment pool, wrong element: ";
            recordError(message + poolElem.getName(), ErrorLevel.ERROR);
        }

        return parsePool(poolElem);
    }

    public JsonObject parseQuestionBank(Document doc) {

        if (doc == null) {
            throw (new NullPointerException("'doc' cannot be null"));
        }

        Element qbElem = doc.getRootElement();
        if (!"question_bank".equals(qbElem.getName())) {
            final String message = "Document is not a question bank, wrong element: ";
            recordError(message + qbElem.getName(), ErrorLevel.ERROR);
        }

        return parseQuestionBank(qbElem);
    }

    private JsonObject parsePool(Element poolElem) {

        // Assessment
        String poolId = poolElem.getAttributeValue("id");
        JsonObject pool = new JsonObject();
        pool.addProperty("id", poolId);

        // Title
        Element titleElem = poolElem.getChild("title");
        if (titleElem != null) {
            pool.addProperty("title", titleElem.getTextTrim());
        }

        // Short Title
        Element shortElem = poolElem.getChild("short_title");
        if (shortElem != null) {
            pool.addProperty("shortTitle", shortElem.getTextTrim());
        }

        // :TODO: content metadata

        // Content
        Element contentElem = poolElem.getChild("content");
        if (contentElem != null) {
            pool.add("content", parseContentBlock(contentElem));
        }

        JsonArray poolEntries = new JsonArray();
        pool.add("entries", poolEntries);

        // Questions
        List<Element> children = poolElem.getChildren();
        for (Element childElem : children) {
            if ("section".equals(childElem.getName())) {
                poolEntries.add(parseSection(childElem));
            } else if (_QUESTION_FILTER.matches(childElem)) {
                poolEntries.add(parseQuestion(childElem));
            }
        }

        return pool;
    }

    private JsonObject parseAssessment(Element quizElem) {

        // Assessment
        String assessmentId = quizElem.getAttributeValue("id");
        JsonObject assessment = new JsonObject();
        assessment.addProperty("id", assessmentId);

        // Title
        Element titleElem = quizElem.getChild("title");
        if (titleElem != null) {
            assessment.addProperty("title", titleElem.getTextTrim());
        }

        // Short Title
        Element shortElem = quizElem.getChild("short_title");
        if (shortElem != null) {
            assessment.addProperty("shortTitle", shortElem.getTextTrim());
        }

        // :TODO: content metadata

        Attribute recommendedAttemptsAttr = quizElem.getAttribute("recommended_attempts");
        Integer recommendedAttempts = null;
        if (recommendedAttemptsAttr == null) {
            assessment.add("attemptsRecommended", null);
        } else {
            try {
                recommendedAttempts = recommendedAttemptsAttr.getIntValue();
            } catch (DataConversionException e) {
                recordError("recommended_attempts is not a positive integer", ErrorLevel.ERROR);
            }
            if (recommendedAttempts < 0) {
                recordError("recommended_attempts is not a positive integer", ErrorLevel.ERROR);
            }
            assessment.addProperty("attemptsRecommended", recommendedAttempts);
        }

        // Number of Attempts Possible
        Attribute maxAttemptsAttr = quizElem.getAttribute("max_attempts");
        Integer maxAttempts = null;
        if (maxAttemptsAttr == null) {
            maxAttempts = 1;
            assessment.addProperty("attemptsMaxPossible", maxAttempts);
        } else {
            if ("unlimited".equals(maxAttemptsAttr.getValue())) {
                assessment.add("attemptsMaxPossible", null);
            } else {

                try {
                    maxAttempts = maxAttemptsAttr.getIntValue();
                } catch (DataConversionException e) {
                    recordError("max_attempts is not a positive integer", ErrorLevel.ERROR);
                }
                if (maxAttempts < 0) {
                    recordError("max_attempts is not a positive integer", ErrorLevel.ERROR);
                }
                assessment.addProperty("attemptsMaxPossible", maxAttempts);
            }
        }

        // Attempts recommended cannot exceed attempts possible
        if (maxAttempts != null) {
            if (recommendedAttempts != null) {
                if (recommendedAttempts > maxAttempts) {
                    recordError("recommended_attempts cannot exceed max_attempts", ErrorLevel.ERROR);
                }
            }
        }

        // Introduction
        Element introElem = quizElem.getChild("introduction");
        // For backwards compatibility with versions 2.0 and 2.1
        if (introElem == null) {
            introElem = quizElem.getChild("preface");
        }
        if (introElem != null) {
            assessment.add("introduction", materialFromElement(introElem));
        }

        JsonArray assessmentPages = new JsonArray();
        assessment.add("pages", assessmentPages);

        // Pages
        List<Element> pages = quizElem.getChildren("page");
        if (pages.isEmpty()) {
            JsonObject p = new JsonObject();
            parsePageNodes(quizElem, p);
            assessmentPages.add(p);
            p.addProperty("number", assessmentPages.size());
        } else {
            for (Element o : pages) {
                JsonObject p = parsePage(o);
                assessmentPages.add(p);
                p.addProperty("number", assessmentPages.size());
            }
        }

        // Conclusion
        Element conclElem = quizElem.getChild("conclusion");
        if (conclElem != null) {
            assessment.add("conclusion", materialFromElement(conclElem));
        }

        return assessment;
    }

    private JsonObject materialFromElement(Element element) {
        JsonObject material = new JsonObject();
        String s1 = outputter.outputElementContentString(element);
        s1 = s1.trim().replaceAll("\n", " ").replaceAll("\t", "").replaceAll("\r", "").replaceAll(" +", " ");
        material.addProperty("material", s1);
        material.addProperty("dir", element.getAttributeValue("dir"));
        material.addProperty("lang", element.getAttributeValue("lang"));
        return material;
    }

    private JsonObject parsePage(Element pageElem) {

        // Page
        JsonObject page = new JsonObject();

        // ID
        page.addProperty("id", pageElem.getAttributeValue("id"));

        // Title
        Element titleElem = pageElem.getChild("title");
        if (titleElem != null) {
            page.addProperty("title", titleElem.getTextTrim());
        }

        // Nodes
        parsePageNodes(pageElem, page);

        return page;
    }

    private void parsePageNodes(Element pageElem, JsonObject page) {
        JsonArray nodes = page.getAsJsonArray("nodes");
        if (nodes == null) {
            nodes = new JsonArray();
            page.add("nodes", nodes);
        }


        for (Element nodeElem : pageElem.getContent(_NODE_FILTER)) {
            String name = nodeElem.getName();

            if (_QUESTION_FILTER.matches(nodeElem)) {
                nodes.add(parseQuestion(nodeElem));
            } else if (name.equals("content")) {
                nodes.add(parseContentBlock(nodeElem));
            } else if (name.equals("section")) {
                nodes.add(parseSection(nodeElem));
            } else if (name.equals("selection")) {
                nodes.add(parseSelection(nodeElem));
            } else if (name.equals("question_bank_ref")) {
                nodes.add(parseQuestionBankRef(nodeElem));
            } else {
                final String message = "Unexpected page content: ";
                recordError(message + name, ErrorLevel.ERROR);
            }
        }
    }

    private JsonObject parseQuestionBank(Element qbElem) {

        // Question Bank
        String questionBankId = qbElem.getAttributeValue("id");
        JsonObject qb = new JsonObject();
        qb.addProperty("id", questionBankId);

        // Title
        Element titleElem = qbElem.getChild("title");
        if (titleElem != null) {
            qb.addProperty("title", titleElem.getTextTrim());
        }

        // Short Title
        Element shortElem = qbElem.getChild("short_title");
        if (shortElem != null) {
            qb.addProperty("shortTitle", shortElem.getTextTrim());
        }

        // :TODO: content metadata

        // Questions
        List<Element> children = qbElem.getContent(_QUESTION_FILTER);
        JsonArray questions = new JsonArray();
        //List<Question> questions = new ArrayList<Question>(children.size());
        for (Element o : children) {
            questions.add(parseQuestion(o));
        }
        qb.add("questions", questions);

        return qb;
    }

    private JsonObject parseQuestion(Element qElem) {

        String name = qElem.getName();

        // Question ID
        String id = qElem.getAttributeValue("id");

        // Body
        Element bodyElem = qElem.getChild("body");
        JsonObject body = materialFromElement(bodyElem);

        // Question
        JsonObject question = new JsonObject();
        question.addProperty("id", id);
        question.add("body", body);

        JsonArray hints = new JsonArray();
        JsonArray solutions = new JsonArray();
        question.add("hints", hints);
        question.add("solutions", solutions);

        JsonArray interactions = new JsonArray();
        question.add("interactions", interactions);
        JsonArray parts = new JsonArray();
        question.add("parts", parts);

        // Title
        Element titleElem = qElem.getChild("title");
        if (titleElem != null) {
            question.addProperty("title", titleElem.getTextTrim());
        }

        // :TODO: concept metadata

        // Automatic Grading
        String gradingAttrValue = qElem.getAttributeValue("grading");
        if (gradingAttrValue == null) {
            String autoGradeAttrVal = qElem.getAttributeValue("automatic_grading");
            if ("true".equals(autoGradeAttrVal)) {
                question.addProperty("scoringMethod", ScoringMethod.AUTOMATIC.toString());
            } else if ("false".equals(autoGradeAttrVal)) {
                question.addProperty("scoringMethod", ScoringMethod.INSTRUCTOR.toString());
            } else if ("essay".equals(name)) {
                // Default essay questions to instructor scored
                question.addProperty("scoringMethod", ScoringMethod.INSTRUCTOR.toString());
            } else {
                // Default all other question types to automatic
                question.addProperty("scoringMethod", ScoringMethod.AUTOMATIC.toString());
            }
        } else {
            String method = gradingAttrValue.trim().toUpperCase();
            question.addProperty("scoringMethod", ScoringMethod.valueOf(method).toString());
        }

        // Interactions
        if ("multiple_choice".equals(name)) {
            InteractionStyle style = InteractionStyle.MULTIPLE_CHOICE;
            parseMultipleChoiceInteractions(question, qElem, style);
        } else if ("fill_in_the_blank".equals(name)) {
            InteractionStyle style = InteractionStyle.FILL_IN_THE_BLANK;
            parseMultipleChoiceInteractions(question, qElem, style);
        } else if ("text".equals(name)) {
            InteractionStyle style = InteractionStyle.TEXT;
            parseTextInteractions(question, qElem, style);
        } else if ("short_answer".equals(name)) {
            InteractionStyle style = InteractionStyle.SHORT_ANSWER;
            parseTextInteractions(question, qElem, style);
        } else if ("numeric".equals(name)) {
            parseNumericInteractions(question, qElem);
        } else if ("essay".equals(name)) {
            parseEssayQuestion(question, qElem);
        } else if ("image_hotspot".equals(name)) {
            parseImageHotspotInteractions(question, qElem);
        } else if ("ordering".equals(name)) {
            parseOrderingInteractions(question, qElem);
// :TODO: matrix question
//		} else if ("matrix".equals(name)) {
//			return parseMatrixInteractions(question, qElem);
        } else {
            final String message = "Unsupported question type: ";
            recordError(message + name, ErrorLevel.ERROR);
        }

        // Parts
        if (!"essay".equals(name)) {
            parseQuestionParts(question, qElem);
        }

        // Hints and Solutions
        // Hint
        // Solution
        qElem.getChildren().forEach(childElem -> {
            String childName = childElem.getName();
            if ("hint".equals(childName)) {
                // Hint
                hints.add(parseHint(childElem, question));
            } else if ("sample".equals(childName)) {
                // Solution
                solutions.add(parseSolution(childElem, question));
            }
        });

        return question;
    }

    private void validateInputRefs(JsonObject q, Element qElem) {
        // Scan question body for input references
        Element bodyElem = qElem.getChild("body");
        XPathExpression<Element> xexpression = xFactory.compile("descendant::input_ref", Filters.element());
        List<Element> inputRefs = xexpression.evaluate(bodyElem);

        // For each input reference...
        Set<String> referenced = new HashSet<String>();
        for (Element inputElem : inputRefs) {
            String id = inputElem.getAttributeValue("input");
            // Referenced interaction must exist
            if (findInteractionByLocalId(q, id) == null) {
                String message = "referenced input does not exist: question=" + q.get("id").getAsString() + ", input_ref=" + id;
                recordError(message, ErrorLevel.ERROR);
            }
            // Each interaction may be referneced at most once
            if (referenced.contains(id)) {
                String message = "input referenced more than once: question=" + q.get("id").getAsString() + ", input_ref=" + id;
                recordError(message, ErrorLevel.ERROR);
            }
            referenced.add(id);
        }

    }

    private void parseQuestionParts(JsonObject q, Element qElem) {

        // Correct Threshold
        Integer correctThreshold = null;
        Attribute correctAttr = qElem.getAttribute("correct");
        if (correctAttr != null) {
            try {
                correctThreshold = correctAttr.getIntValue();
            } catch (DataConversionException e) {
                recordError("correct is not an integer", ErrorLevel.ERROR);
            }
        }

        // Score Out Of
        Integer scoreOutOf = null;
        Attribute scoreOutOfAttr = qElem.getAttribute("score_out_of");
        if (scoreOutOfAttr != null) {
            try {
                scoreOutOf = scoreOutOfAttr.getIntValue();
            } catch (DataConversionException e) {
                recordError("correct is not an integer", ErrorLevel.ERROR);
            }
        }

        JsonArray parts = q.getAsJsonArray("parts");
        if (parts == null) {
            parts = new JsonArray();
            q.add("parts", parts);
        }

        // Parts
        for (Element o : qElem.getContent(_PART_FILTER)) {
            JsonObject p = parsePart(o, q);

            // Assign question default for scoring, if not overridden
            if (!p.has("correctThresholdExplicit")) {
                p.addProperty("correctThresholdExplicit", correctThreshold);
            }
            if (!p.has("scoreOutOfExplicit")) {
                p.addProperty("scoreOutOfExplicit", scoreOutOf);
            }

            parts.add(p);
        }

        // All questions must have at least one part
        if (parts.size() == 0) {
            String partId = "p" + +1;
            JsonObject p = new JsonObject();
            p.addProperty("id", partId);
            p.addProperty("questionId", q.get("id").getAsString());
            p.addProperty("correctThresholdExplicit", correctThreshold);
            p.addProperty("scoreOutOfExplicit", scoreOutOf);
            parts.add(p);
        }
    }

    private void parseMultipleChoiceInteractions(JsonObject q, Element qElem, InteractionStyle style) {

        // Selection Cardinality
        Boolean multipleSelect = null;
        String selectAttrVal = qElem.getAttributeValue("select");
        if (selectAttrVal != null) {
            multipleSelect = "multiple".equals(selectAttrVal);
        }

        JsonArray interactions = q.getAsJsonArray("interactions");
        if (interactions == null) {
            interactions = new JsonArray();
            q.add("interactions", interactions);
        }

        // Interactions
        for (Element inputElem : qElem.getChildren("input")) {

            // Interaction ID
            String id = inputElem.getAttributeValue("id");
            if (id == null) {
                id = "i" + (interactions.size() + 1);
            }

            // Multiple Choice Interaction
            JsonObject i = new JsonObject();
            i.addProperty("id", id);
            i.addProperty("questionId", q.get("id").getAsString());
            i.addProperty("style", style.toString());
            addInteractionToQuestion(style.toString(), i, q);

            // Multiple Select
            if (multipleSelect != null) {
                i.addProperty("multipleSelect", multipleSelect);
            }

            // Display Name
            i.addProperty("displayName", inputElem.getAttributeValue("name"));

            // Labels
            Attribute labelsAttr = inputElem.getAttribute("labels");
            if (labelsAttr != null) {
                String value = labelsAttr.getValue();
                if ("true".equals(value) || "yes".equals(value)) {
                    i.addProperty("labelChoices", true);
                } else {
                    i.addProperty("labelChoices", false);
                }
            }

            // Shuffle
            Attribute shuffleAttr = inputElem.getAttribute("shuffle");
            if (shuffleAttr != null) {
                String value = shuffleAttr.getValue();
                if ("true".equals(value) || "yes".equals(value)) {
                    i.addProperty("shuffleChoices", true);
                } else {
                    i.addProperty("shuffleChoices", false);
                }
            }

            JsonArray choices = i.getAsJsonArray("choices");
            if (choices == null) {
                choices = new JsonArray();
                i.add("choices", choices);
            }

            // Choices
            for (Element c : inputElem.getChildren("choice")) {
                JsonObject choice = parseChoice(c);
                choices.forEach(ch -> {
                    if (((JsonObject) ch).get("value").getAsString().equals(choice.get("value").getAsString())) {
                        String message = "duplicate choice identifier: question=" + q.get("id").getAsString() + ", choice=" + choice.get("value").getAsString();
                        recordError(message, ErrorLevel.ERROR);
                    }
                });

                choices.add(choice);
            }
        }

        // Validate input references in question body
        validateInputRefs(q, qElem);
    }

    private void parseTextInteractions(JsonObject q, Element qElem, InteractionStyle style) {

        // Case Sensitive
        Boolean caseSensitive = null;
        Attribute caseSensitiveAttr = qElem.getAttribute("case_sensitive");
        if (caseSensitiveAttr != null) {
            caseSensitive = "true".equals(caseSensitiveAttr.getValue());
        }
        // Whitespace Strategy
        //WhitespaceStrategy whitespaceStrategy = null;
        String whitespaceStrategy = qElem.getAttributeValue("whitespace");
//        if (whitespaceAttrVal != null) {
//            String id = whitespaceAttrVal.trim().toUpperCase();
//           // whitespaceStrategy = WhitespaceStrategy.valueOf(id);
//        }

        // Interactions
        // Define interaction for each explicit input
        for (Element inputElem : qElem.getChildren("input")) {

            String id = inputElem.getAttributeValue("id");
            if (id == null) {
                id = "i" + (q.getAsJsonArray("interactions").size() + 1);
            }
            JsonObject i = new JsonObject();
            i.addProperty("id", id);
            i.addProperty("questionId", q.get("id").getAsString());

            // Style
            i.addProperty("style", style.toString());

            // Display Name
            i.addProperty("displayName", inputElem.getAttributeValue("name"));

            // Size
            String sizeAttrVal = inputElem.getAttributeValue("size");
            if (sizeAttrVal != null) {
                String size = sizeAttrVal.trim().toUpperCase();
                i.addProperty("size", InputSize.valueOf(size).toString());
            }

            // Case Sensitive
            if (caseSensitive != null) {
                i.addProperty("caseSensitive", caseSensitive);
            }

            // Whitespace
            if (whitespaceStrategy != null) {
                i.addProperty("whitespaceStrategy", whitespaceStrategy);
            }

            addInteractionToQuestion(style.toString(), i, q);
        }

        // Scan question body for input references
        Element bodyElem = qElem.getChild("body");
        XPathExpression<Element> xexpression = xFactory.compile("descendant::input_ref", Filters.element());
        List<Element> inputRefs = xexpression.evaluate(bodyElem);

        JsonArray interactions = q.getAsJsonArray("interactions");
        if (interactions == null) {
            interactions = new JsonArray();
            q.add("interactions", interactions);
        }

        // Define interaction for each implicit input, if not already added
        Set<String> referenced = new HashSet<String>();
        for (Element inputElem : inputRefs) {
            String id = inputElem.getAttributeValue("input");

            // Each interaction may be referenced at most once
            if (referenced.contains(id)) {
                String message = "input referenced more than once: question=" + q + ", input_ref=" + id;
                recordError(message, ErrorLevel.ERROR);
            }
            referenced.add(id);

            // Referenced interaction must exist
            if (findInteractionByLocalId(q, id) == null) {
                JsonObject i = new JsonObject();
                i.addProperty("id", id);
                i.addProperty("questionId", q.get("id").getAsString());

                // Style
                i.addProperty("style", style.toString());

                // Case Sensitive
                if (caseSensitive != null) {
                    i.addProperty("caseSensitive", caseSensitive);
                }

                // Whitespace
                if (whitespaceStrategy != null) {
                    i.addProperty("whitespaceStrategy", whitespaceStrategy);
                }

                interactions.add(i);
            }
        }

        // If no interactions found, define single interaction
        if (interactions.size() == 0) {
            String id = "i" + 1;
            JsonObject i = new JsonObject();
            i.addProperty("id", id);
            i.addProperty("questionId", q.get("id").getAsString());
            i.addProperty("style", style.toString());
            if (caseSensitive != null) {
                i.addProperty("caseSensitive", caseSensitive);
            }
            if (whitespaceStrategy != null) {
                i.addProperty("whitespaceStrategy", whitespaceStrategy);
            }
            interactions.add(i);
        }
    }

    private void parseNumericInteractions(JsonObject q, Element qElem) {

        // Notation
        NumericNotation notation = null;
        Attribute notationAttr = qElem.getAttribute("notation");
        if (notationAttr != null) {
            String notationId = notationAttr.getValue().toUpperCase();
            notation = NumericNotation.valueOf(notationId);
        }

        // Interactions
        // Define interaction for each explicit input
        for (Element inputElem : qElem.getChildren("input")) {

            String id = inputElem.getAttributeValue("id");
            if (id == null) {
                id = "i" + (q.getAsJsonArray("interactions").size() + 1);
            }
            JsonObject i = new JsonObject();
            i.addProperty("id", id);
            i.addProperty("questionId", q.get("id").getAsString());
            i.addProperty("style", InteractionStyle.NUMERIC.toString());

            // Display Name
            i.addProperty("displayName", inputElem.getAttributeValue("name"));

            // Size
            String sizeAttrVal = inputElem.getAttributeValue("size");
            if (sizeAttrVal != null) {
                String size = sizeAttrVal.trim().toUpperCase();
                i.addProperty("size", InputSize.valueOf(size).toString());
            }

            // Notation
            if (notation != null) {
                i.addProperty("notation", notation.toString());
            }

            addInteractionToQuestion(InteractionStyle.NUMERIC.toString(), i, q);
        }

        JsonArray interactions = q.getAsJsonArray("interactions");
        if (interactions == null) {
            interactions = new JsonArray();
            q.add("interactions", interactions);
        }

        // Scan question body for input references
        Element bodyElem = qElem.getChild("body");
        XPathExpression<Element> xexpression = xFactory.compile("descendant::input_ref", Filters.element());
        List<Element> inputRefs = xexpression.evaluate(bodyElem);

        // Define interaction for each implicit input, if not already added
        Set<String> referenced = new HashSet<String>();
        for (Element inputElem : inputRefs) {
            String id = inputElem.getAttributeValue("input");

            // Each interaction may be referenced at most once
            if (referenced.contains(id)) {
                String message = "input referenced more than once: question=" + q.get("id").getAsString() + ", input_ref=" + id;
                recordError(message, ErrorLevel.ERROR);
            }
            referenced.add(id);

            // Referenced interaction must exist
            if (findInteractionByLocalId(q, id) == null) {
                JsonObject i = new JsonObject();
                i.addProperty("id", id);
                i.addProperty("questionId", q.get("id").getAsString());
                i.addProperty("style", InteractionStyle.NUMERIC.toString());
                // Notation
                if (notation != null) {
                    i.addProperty("notation", notation.toString());
                }

                interactions.add(i);
            }
        }

        // If no interactions found, define single interaction

        if (interactions.size() == 0) {
            String id = "i" + 1;
            JsonObject i = new JsonObject();
            i.addProperty("id", id);
            i.addProperty("questionId", q.get("id").getAsString());
            i.addProperty("style", InteractionStyle.NUMERIC.toString());
            if (notation != null) {
                i.addProperty("notation", notation.toString());
            }

            interactions.add(i);
        }
    }

    private void parseEssayQuestion(JsonObject q, Element qElem) {

        // Implicit Interaction
        JsonObject i = new JsonObject();
        i.addProperty("id", "i1");
        i.addProperty("questionId", q.get("id").getAsString());
        i.addProperty("style", InteractionStyle.ESSAY.toString());

        JsonArray interactions = q.getAsJsonArray("interactions");
        if (interactions == null) {
            interactions = new JsonArray();
            q.add("interactions", interactions);
        }
        interactions.add(i);

        // Correct Threshold
        Integer correctThreshold = null;
        Attribute correctAttr = qElem.getAttribute("correct");
        if (correctAttr != null) {
            try {
                correctThreshold = correctAttr.getIntValue();
            } catch (DataConversionException e) {
                recordError("correct is not an integer: " + e.getMessage(), ErrorLevel.ERROR);
            }
        }

        // Score Out Of
        Integer scoreOutOf = null;
        Attribute scoreOutOfAttr = qElem.getAttribute("score_out_of");
        if (scoreOutOfAttr != null) {
            try {
                scoreOutOf = scoreOutOfAttr.getIntValue();
            } catch (DataConversionException e) {
                recordError("correct is not an integer: " + e.getMessage(), ErrorLevel.ERROR);
            }
        }

        JsonArray parts = q.getAsJsonArray("parts");
        if (parts == null) {
            parts = new JsonArray();
            q.add("parts", parts);
        }

        // Parts
        for (Element o : qElem.getContent(_PART_FILTER)) {
            JsonObject p = parsePart(o, q);

            // Assign question default for scoring, if not overridden
            if (!p.has("correctThresholdExplicit")) {
                p.addProperty("correctThresholdExplicit", correctThreshold);
            }
            if (!p.has("scoreOutOfExplicit")) {
                p.addProperty("scoreOutOfExplicit", scoreOutOf);
            }

            parts.add(p);
        }

        // All questions must have at least one part
        if (parts.size() == 0) {
            // Implicit Part
            JsonObject p = new JsonObject();
            p.addProperty("id", "p1");
            p.addProperty("questionId", q.get("id").getAsString());
            p.addProperty("correctThresholdExplicit", correctThreshold);
            p.addProperty("scoreOutOfExplicit", scoreOutOf);
            parts.add(p);

            // For backwards compatibility with version 2.0
            // No Response
            Element noRespElem = qElem.getChild("no_response");
            if (noRespElem != null) {
                p.add("noResponseOutcome", parseOutcome(noRespElem));
            }

            JsonArray gradingCriterias = p.getAsJsonArray("gradingCriterias");
            if (gradingCriterias == null) {
                gradingCriterias = new JsonArray();
                p.add("gradingCriterias", gradingCriterias);
            }

            // Grading Criteria
            for (Element o : qElem.getChildren("grading_criteria")) {
                gradingCriterias.add(parseGradingCriteria(o));
            }
        }

        // Validate input references in question body
        validateInputRefs(q, qElem);
    }

    private void parseImageHotspotInteractions(JsonObject q, Element qElem) {

        // Selection Cardinality
        Boolean multipleSelect = null;
        String selectAttrVal = qElem.getAttributeValue("select");
        if (selectAttrVal != null) {
            multipleSelect = "multiple".equals(selectAttrVal);
        }

        // Interactions
        for (Element inputElem : qElem.getChildren("image_input")) {

            // Interaction ID
            String id = inputElem.getAttributeValue("id");
            if (id == null) {
                id = "i" + (q.getAsJsonArray("interactions").size() + 1);
            }

            // Image
            String src = inputElem.getAttributeValue("src");

            int height = 0;
            try {
                height = inputElem.getAttribute("height").getIntValue();
            } catch (DataConversionException e) {
                recordError("height is not an integer: " + e.getMessage(), ErrorLevel.ERROR);
            }

            int width = 0;
            try {
                width = inputElem.getAttribute("width").getIntValue();
            } catch (DataConversionException e) {
                recordError("width is not an integer: " + e.getMessage(), ErrorLevel.ERROR);
            }

            JsonObject image = new JsonObject();
            image.addProperty("src", src);
            image.addProperty("height", height);
            image.addProperty("width", width);

            // Image Hotspot Interaction
            JsonObject i = new JsonObject();
            i.addProperty("id", id);
            i.addProperty("questionId", q.get("id").getAsString());
            i.addProperty("style", InteractionStyle.IMAGE_HOTSPOT.toString());
            i.add("imageSrc", image);
            addInteractionToQuestion(InteractionStyle.IMAGE_HOTSPOT.toString(), i, q);

            // Display Name
            i.addProperty("displayName", inputElem.getAttributeValue("name"));

            // Multiple Select
            if (multipleSelect != null) {
                i.addProperty("multipleSelect", multipleSelect);
            }

            JsonArray hotspots = new JsonArray();
            i.add("hotspots", hotspots);

            // Hotspots
            for (Element h : inputElem.getChildren("hotspot")) {
                JsonObject hotspot = parseHotspot((Element) h);
                hotspots.forEach(e -> {
                    if (((JsonObject) e).get("value").getAsString().equals(hotspot.get("value").getAsString())) {
                        String message = "duplicate hotspot identifier: question=" + q.get("id").getAsString() + ", hotspot=" + hotspot.get("value").getAsString();
                        recordError(message, ErrorLevel.ERROR);
                    }
                });
                hotspots.add(hotspot);
            }
        }

        // Validate input references in question body
        validateInputRefs(q, qElem);
    }

    private void parseOrderingInteractions(JsonObject q, Element qElem) {

        // Interactions
        for (Element inputElem : qElem.getChildren("input")) {

            // Interaction ID
            String id = inputElem.getAttributeValue("id");
            if (id == null) {
                id = "i" + (q.getAsJsonArray("interactions").size() + 1);
            }

            // Ordering Interaction
            JsonObject i = new JsonObject();
            i.addProperty("id", id);
            i.addProperty("questionId", q.get("id").getAsString());
            i.addProperty("style", InteractionStyle.ORDERING.toString());
            addInteractionToQuestion(InteractionStyle.ORDERING.toString(), i, q);

            // Display Name
            i.addProperty("displayName", inputElem.getAttributeValue("name"));

            // Shuffle
            String shuffleAttrVal = inputElem.getAttributeValue("shuffle");
            if (shuffleAttrVal != null) {
                i.addProperty("shuffleChoices", "true".equals(shuffleAttrVal));
            }

            JsonArray choices = new JsonArray();
            i.add("choices", choices);
            // Choices
            for (Element c : inputElem.getChildren("choice")) {
                JsonObject choice = parseChoice(c);
                choices.forEach(e -> {

                    if (((JsonObject) e).get("value").getAsString().equals(choice.get("value").getAsString())) {
                        String message = "duplicate choice identifier: question=" + q.get("id").getAsString() + ", choice=" + choice.get("value").getAsString();
                        recordError(message, ErrorLevel.ERROR);
                    }
                });
                choices.add(choice);
            }
        }

        // Validate input references in question body
        validateInputRefs(q, qElem);
    }

    private void addInteractionToQuestion(String type, JsonObject i, JsonObject q) {
        String id = i.get("id").getAsString();
        if (findInteractionByLocalId(q, id) != null) {
            recordError("duplicate input identifier: " + id, ErrorLevel.ERROR);
        }
        JsonArray interactions = q.getAsJsonArray("interactions");
        if (interactions == null) {
            interactions = new JsonArray();
            q.add("interactions", interactions);
        }
        JsonObject inter = new JsonObject();
        inter.add(type, i);
        interactions.add(inter);
        i.addProperty("position", (interactions.size() - 1));

    }

    private JsonObject parseChoice(Element choiceElem) {
        // Choice
        String value = choiceElem.getAttributeValue("value");
        JsonObject body = materialFromElement(choiceElem);
        body.addProperty("color", choiceElem.getAttributeValue("color"));
        JsonObject choice = new JsonObject();
        choice.addProperty("value", value);
        choice.add("body", body);
        return choice;
    }

    private JsonObject parseHotspot(Element hotspotElem) {
        // Hotspot
        String value = hotspotElem.getAttributeValue("value");
        String shapeId = hotspotElem.getAttributeValue("shape");
        //HotspotShape shape = HotspotShape.valueOf(shapeId.toUpperCase());
        String coords = hotspotElem.getAttributeValue("coords");

        JsonObject hotspot = new JsonObject();
        hotspot.addProperty("value", value);
        hotspot.addProperty("shapeId", shapeId);
        hotspot.addProperty("coords", coords.trim());
        hotspot.addProperty("label", hotspotElem.getTextTrim());
        return hotspot;
    }

    private JsonObject parseHint(Element hintElem, JsonObject q) {

        // Body
        JsonObject body = materialFromElement(hintElem);

        // Hint
        JsonObject hint = new JsonObject();
        hint.add("body", body);

        // Target Interactions
        String targets = hintElem.getAttributeValue("targets");
        if (targets != null && !"".equals(targets.trim())) {
            JsonArray explicitTargets = new JsonArray();
            hint.add("explicitTargets", explicitTargets);
            for (String t : targets.split(",")) {
                JsonObject i = (JsonObject) findInteractionByLocalId(q, t);
                // Does the referenced input exist within the question?
                if (i == null) {
                    String message = "referenced input does not exist: question=" + q.get("id").getAsString() + ", input=" + t;
                    recordError(message, ErrorLevel.ERROR);
                }

                explicitTargets.add(i);
            }
        }

        return hint;
    }

    private JsonObject parseSolution(Element sElem, JsonObject q) {
        // Solution
        JsonObject s = new JsonObject();
        JsonArray inputValues = new JsonArray();
        s.add("inputValues", inputValues);
        // Interaction Values
        for (Element valueElem : sElem.getChildren("input_value")) {
            JsonObject i = resolveInputReference(valueElem, q);
            String id = null;
            if (i.has("id")) {
                id = i.get("id").getAsString();
            } else if (!i.entrySet().isEmpty()) {
                JsonObject in = i.entrySet().iterator().next().getValue().getAsJsonObject();
                id = in.get("id").getAsString();
            }
            String value = valueElem.getText();
            // :FIXME: validate input value
            JsonObject val = new JsonObject();
            val.addProperty("interactionId", id);
            val.addProperty("value", value);
            inputValues.add(val);
        }

        return s;
    }

    private JsonObject parsePart(Element partElem, JsonObject q) {

        // Part ID
        String id = partElem.getAttributeValue("id");
        if (id == null) {
            id = partElem.getAttributeValue("part_id");        // For version 2.0 compatibility
            if (id == null) {
                id = "p" + (q.getAsJsonArray("parts").size() + 1);
            }
        }
        // Part
        JsonObject p = new JsonObject();
        p.addProperty("id", id);
        p.addProperty("questionId", q.get("id").getAsString());
        JsonArray parts = q.getAsJsonArray("parts");
        parts.forEach(e -> {
            JsonObject o = (JsonObject) e;
            if (o.get("id").getAsString().equals(p.get("id").getAsString())) {
                recordError("duplicate part identifier: " + p.get("id").getAsString(), ErrorLevel.ERROR);
            }
        });

        // Title
        Element titleElem = partElem.getChild("title");
        if (titleElem != null) {
            p.addProperty("title", titleElem.getTextTrim());
        }

        // Correct Threshold
        Attribute correctAttr = partElem.getAttribute("correct");
        if (correctAttr != null) {
            try {
                Integer correct = correctAttr.getIntValue();
                p.addProperty("correctThresholdExplicit", correct);
            } catch (DataConversionException e) {
                recordError("correct is not an integer: " + e.getMessage(), ErrorLevel.ERROR);
            }
        }

        // Score Out Of
        Attribute scoreOutOfAttr = partElem.getAttribute("score_out_of");
        if (scoreOutOfAttr != null) {
            try {
                Integer scoreOutOf = scoreOutOfAttr.getIntValue();
                p.addProperty("scoreOutOfExplicit", scoreOutOf);
            } catch (DataConversionException e) {
                recordError("score_out_of is not an integer: " + e.getMessage(), ErrorLevel.ERROR);
            }
        }

        // Target Interactions
        String targets = partElem.getAttributeValue("targets");
        if (targets == null) {
            targets = partElem.getAttributeValue("target_inputs");        // For v2.0 compatibility
        }
        if (targets != null && !"".equals(targets.trim())) {
            JsonArray explicitTargets = new JsonArray();
            p.add("explicitTargets", explicitTargets);
            for (String t : targets.split(",")) {
                JsonObject i = (JsonObject) findInteractionByLocalId(q, t);
                explicitTargets.add(i);
            }
        }

        JsonArray responseConditions = new JsonArray();
        JsonArray noResponseOutcomes = new JsonArray();
        JsonArray gradingCriterias = new JsonArray();
        JsonArray hints = new JsonArray();
        p.add("responseConditions", responseConditions);
        p.add("noResponseOutcomes", noResponseOutcomes);
        p.add("gradingCriterias", gradingCriterias);
        p.add("hints", hints);

        // Process child elements
        for (Element childElem : partElem.getChildren()) {
            String childName = childElem.getName();

            // :TODO: concept metadata

            switch (childName) {
                case "response":
                    // Response Condition
                    responseConditions.add(parseResponse(childElem, q));
                    break;
                case "response_mult":
                    // Response Condition
                    responseConditions.add(parseResponseMult(childElem, q));
                    break;
                case "no_response":
                    // No Response
                    responseConditions.add(parseOutcome(childElem));
                    break;
                case "grading_criteria":
                    // Grading Criteria
                    responseConditions.add(parseGradingCriteria(childElem));
                    break;
                case "hint":
                    // Hint
                    responseConditions.add(parseHint(childElem, q));
                    break;
            }
        }

        return p;
    }

    private JsonObject parseResponse(Element respElem,
                                     JsonObject q) {

        // Response Condition
        JsonObject rc = new JsonObject();

        // Match Criteria
        rc.add("criteria", parseResponseCriteria(respElem, q));

        // Outcome
        rc.add("outcome", parseOutcome(respElem));

        return rc;
    }

    private JsonObject parseResponseMult(Element respElem,
                                         JsonObject q) {

        // Response Condition
        JsonObject rc = new JsonObject();

        // Matching Strategy
        String matchStyleAttrVal = respElem.getAttributeValue("match_style");
        if (matchStyleAttrVal != null) {
            String id = matchStyleAttrVal.trim().toUpperCase();
            rc.addProperty("matchingStrategyId", id);
        }

        // Match Criteria
        for (Object o : respElem.getChildren("match")) {
            Element matchElem = (Element) o;
            rc.add("criteria", parseResponseCriteria(matchElem, q));
        }

        // Outcome
        rc.add("outcome", parseOutcome(respElem));

        return rc;
    }

    private JsonObject parseResponseCriteria(Element matchElem,
                                             JsonObject q) {

        // Interaction
        JsonObject i = resolveInputReference(matchElem, q);
        //log.info(AppUtils.gsonBuilder().create().toJson(i));
        String id = null;
        if (i.has("id")) {
            id = i.get("id").getAsString();
        } else if (!i.entrySet().isEmpty()) {
            JsonObject in = i.entrySet().iterator().next().getValue().getAsJsonObject();
            id = in.get("id").getAsString();
        }

        // Match Criteria
        String match = matchElem.getAttributeValue("match");

        JsonObject responseCriteria = new JsonObject();
        responseCriteria.addProperty("interactionId", id);
        responseCriteria.addProperty("match", match);
        return responseCriteria;
    }

    private JsonObject resolveInputReference(Element matchElem,
                                             JsonObject q) {

        JsonObject i = null;

        // Attempt to resolve the referenced input
        String input = matchElem.getAttributeValue("input");
        if (input == null) {
            if (q.getAsJsonArray("interactions").size() > 1) {
                String message = "input IDs must be specified if question contains more than one input: ";
                recordError(message + q.get("id").getAsString(), ErrorLevel.ERROR);
            } else {
                i = (JsonObject) q.getAsJsonArray("interactions").get(0);
            }
        } else {
            i = (JsonObject) findInteractionByLocalId(q, input);
        }

        // Does the referenced input exist within the question?
        if (i == null) {
            String message = "referenced input does not exist: question=" + q.get("id").getAsString() + ", input=" + input;
            recordError(message, ErrorLevel.ERROR);
        }

        return i;
    }

    private JsonObject parseOutcome(Element outElem) {

        // Outcome
        JsonObject outcome = new JsonObject();

        // :TODO: concept metadata

        // Score
        Attribute scoreAttr = outElem.getAttribute("score");
        if (scoreAttr != null) {
            try {
                outcome.addProperty("score", scoreAttr.getIntValue());
            } catch (DataConversionException e) {
                recordError("score is not an integer " + e.getMessage(), ErrorLevel.ERROR);
            }
        }

        // Name
        outcome.addProperty("displayName", outElem.getAttributeValue("name"));

        JsonArray feedbacks = new JsonArray();
        outcome.add("feedbacks", feedbacks);
        // Feedback
        for (Element o : outElem.getChildren("feedback")) {
            feedbacks.add(parseFeedback(o));
        }

        return outcome;
    }

    private JsonObject parseFeedback(Element fbkElem) {

        // Body
        JsonObject body = materialFromElement(fbkElem);

        // Feedback
        JsonObject feedback = new JsonObject();
        feedback.add("body", body);

        return feedback;
    }

    private JsonObject parseGradingCriteria(Element critElem) {

        // Body
        JsonObject body = new JsonObject();

        // Add content to body, exclude feedback
        List<Content> content = critElem.getContent();
        Element bodyElement = new Element("body");
        content.forEach(c -> {
            if (c instanceof Element) {
                String name = ((Element) c).getName();
                if ("feedback".equals(name)) {
                    return;
                }
            }
            bodyElement.addContent(c.clone());
        });

        String s1 = outputter.outputElementContentString(bodyElement);
        s1 = s1.trim().replaceAll("\n", " ").replaceAll("\t", "").
                replaceAll("\r", "").replaceAll(" +", " ").replaceAll("&amp;", "&");

        body.addProperty("xmlContent", s1);

        // Outcome
        JsonObject outcome = parseOutcome(critElem);

        JsonObject gradingCriteria = new JsonObject();
        gradingCriteria.add("body", body);
        gradingCriteria.add("outcome", outcome);

        // Grading Criteria
        return gradingCriteria;
    }

    private JsonObject parseContentBlock(Element nodeElem) {
        JsonObject body = materialFromElement(nodeElem);
        JsonObject content = new JsonObject();
        content.add("body", body);

        // Availability
        String availableAttrVal = nodeElem.getAttributeValue("available");
        if (availableAttrVal != null) {
            String id = availableAttrVal.trim().toUpperCase();
            content.addProperty("availabilityId", id);
        }

        return content;
    }

    private JsonObject parseSection(Element sectElem) {

        // Section
        JsonObject section = new JsonObject();

        // Section ID
        section.addProperty("id", sectElem.getAttributeValue("id"));

        // Title
        Element titleElem = sectElem.getChild("title");
        if (titleElem != null) {
            section.addProperty("title", titleElem.getTextTrim());
        }

        JsonArray contentBlocks = new JsonArray();
        JsonArray questions = new JsonArray();
        JsonArray questionBankRefs = new JsonArray();
        section.add("contentBlocks", contentBlocks);
        section.add("questions", questions);
        section.add("questionBankRefs", questionBankRefs);
        // Section Nodes
        for (Element childElem : sectElem.getChildren()) {
            String childName = childElem.getName();

            if ("content".equals(childName)) {
                // Content
                contentBlocks.add(parseContentBlock(childElem));
            } else if (_QUESTION_FILTER.matches(childElem)) {
                // Question
                questions.add(parseQuestion(childElem));
            } else if ("question_bank_ref".equals(childName)) {
                // Question Bank Reference
                questionBankRefs.add(parseQuestionBankRef(childElem));
            }
        }

        return section;
    }

    private JsonObject parseQuestionBankRef(Element refElem) {
        String idref = refElem.getAttributeValue("idref");
        JsonObject questionBankRef = new JsonObject();
        questionBankRef.addProperty("idref", idref);
        return questionBankRef;
    }

    private JsonObject parseSelection(Element selElem) {

        // Selection Spec
        JsonObject spec = new JsonObject();

        // Number
        Attribute countAttr = selElem.getAttribute("count");
        if (countAttr != null) {
            if ("*".equals(countAttr.getValue())) {
                spec.add("number", JsonNull.INSTANCE);
            } else {
                int count = 0;
                try {
                    count = countAttr.getIntValue();
                } catch (DataConversionException e) {
                    recordError("count attribute is not a positive integer", ErrorLevel.ERROR);
                }

                if (count < 0) {
                    recordError("count attribute is not a positive integer", ErrorLevel.ERROR);
                }

                spec.add("number", new JsonPrimitive(count));
            }
        }

        // Selection Strategy
        String strategyAttrVal = selElem.getAttributeValue("strategy");
        if (strategyAttrVal != null) {
            String id = strategyAttrVal.trim().toUpperCase();
            spec.addProperty("selectionStrategyId", id);
            //spec.setSelectionStrategy(SelectionStrategy.valueOf(id));
        }

        // Exhaustion Policy
        String exhaustionAttrVal = selElem.getAttributeValue("exhaustion");
        if (exhaustionAttrVal != null) {
            String id = exhaustionAttrVal.trim().toUpperCase();
            spec.addProperty("exhaustionPolicyId", id);
            //spec.setExhaustionPolicy(ExhaustionPolicy.valueOf(id));
        }

        // Selection Scope
        String scopeAttrVal = selElem.getAttributeValue("scope");
        if (scopeAttrVal != null) {
            String id = scopeAttrVal.trim().toUpperCase();
            spec.addProperty("selectionScopeId", id);
            //spec.setSelectionScope(SelectionScope.valueOf(id));
        }

        // Is this a selection or an inline pool?
        Element poolRefElem = selElem.getChild("pool_ref");
        if (poolRefElem != null) {
            // Selection
            String idref = poolRefElem.getAttributeValue("idref");
            JsonObject selection = new JsonObject();
            selection.addProperty("idref", idref);
            selection.add("spec", spec);
            return selection;
        } else {
            // Inline Pool
            JsonObject pool = parsePool(selElem.getChild("pool"));
            JsonObject inlinePool = new JsonObject();
            inlinePool.add("pool", pool);
            inlinePool.add("spec", spec);
            return inlinePool;
        }
    }

    private void recordError(String errorString, ErrorLevel errorLevel) {
        final StringBuilder message = new StringBuilder();
        message.append(errorString).append(", ");
        message.append("resource=").append(rsrc.getId()).append(", ");
        message.append("href=").append(rsrc.getFileNode().getPathFrom());
        AppUtils.addToResourceError(rsrc, message.toString(), rsrc.getId(), errorLevel);
        log.error(message.toString());
        if (throwErrors) {
            throw new RuntimeException(message.toString());
        }
    }

    private JsonElement findInteractionByLocalId(JsonObject question, String id) {
        JsonArray interactions = question.getAsJsonArray("interactions");
        if (interactions == null) {
            interactions = new JsonArray();
            question.add("interactions", interactions);
        }
        for (JsonElement in : interactions) {
            JsonObject i = in.getAsJsonObject();
            String intId = null;
            if (i.has("id")) {
                intId = i.get("id").getAsString();
            } else if (!i.entrySet().isEmpty()) {
                i = i.entrySet().iterator().next().getValue().getAsJsonObject();
                intId = i.get("id").getAsString();
            }
            if (id.equals(intId)) return i;
        }
        return null;
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

    private void validateImageInputs() {
        // Locate image elements in document
        XPathExpression<Element> xexpression = xFactory.compile("//image_input", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each image...
        for (Element imageElem : kids) {
            baseResourceValidator.validateImage(imageElem);
        }
    }

    protected void createLinkEdges() {

        // Locate all activity links in document
        XPathExpression<Element> xexpression = xFactory.compile("//activity | //activity_link | //alternate ", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        ContentPackage pkg = rsrc.getContentPackage();
        Set<Edge> filteredEdges = pkg.getEdges().stream().filter(e -> e.getSourceId()
                .equalsIgnoreCase(pkg.getId() + ":" + pkg.getVersion() + ":" + rsrc.getId())
                && (e.getReferenceType().equals("activity") || e.getReferenceType().equals("activity_link")
                || e.getReferenceType().equals("alternate")))
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
                    recordError("No title specified for: idref=" + idref, ErrorLevel.ERROR);
                }
            }

            // Images used within links must provide alternate text
            Collection imgKids = childElmnt.getChildren("image");
            for (Iterator j = imgKids.iterator(); j.hasNext(); ) {
                Element imgKidElmnt = (Element) j.next();
                String src = imgKidElmnt.getAttributeValue("src");
                String alt = imgKidElmnt.getAttributeValue("alt");

                if (alt == null || "".equals(alt.trim())) {
                    recordError("Alternate text must be provided for linked images: image=" + src, ErrorLevel.ERROR);
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
                    recordError("Unsupported link purpose specified: purpose=" + purpose, ErrorLevel.ERROR);
                }
            }
        }
        // Cleanup edges that are no longer referenced in resource
        filteredEdges.forEach(e -> pkg.removeEdge(e));
    }

    private void createDependencies() {
        // Assessment Pools and Question Banks
        XPathExpression<Element> xexpression = xFactory.compile("//pool_ref | //question_bank_ref ", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        ContentPackage pkg = rsrc.getContentPackage();
        Set<Edge> filteredEdges = pkg.getEdges().stream().filter(e -> e.getSourceId()
                .equalsIgnoreCase(pkg.getId() + ":" + pkg.getVersion() + ":" + rsrc.getId())
                && (e.getReferenceType().equals("pool_ref") || e.getReferenceType().equals("question_bank_ref")))
                .collect(Collectors.toSet());

        for (Element childElem : kids) {
            String idref = childElem.getAttributeValue("idref");

            // Create the resource edge
            Edge edge = new Edge(EdgeType.UTILIZES,
                    pkg.getId() + ":" + pkg.getVersion() + ":" + rsrc.getId(),
                    pkg.getId() + ":" + pkg.getVersion() + ":" + idref,
                    rsrc.getType(), null, childElem.getName());
            JsonObject metadata = new JsonObject();
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

    private void createSkillsDependencies() {
        // Locate Skill references elements in document
        XPathExpression<Element> xexpression = xFactory.compile("//cmd:concept | //skillref", Filters.element(), null,
                Namespace.getNamespace("cmd", "http://oli.web.cmu.edu/content/metadata/2.1/"));
        List<Element> skillrefs = xexpression.evaluate(doc);

        ContentPackage pkg = rsrc.getContentPackage();
        Set<Edge> filteredEdges = pkg.getEdges().stream().filter(e -> e.getSourceId()
                .equalsIgnoreCase(pkg.getId() + ":" + pkg.getVersion() + ":" + rsrc.getId())
                && (e.getReferenceType().equals("concept") || e.getReferenceType().equals("skillref")))
                .collect(Collectors.toSet());

        for (Element skillRefElem : skillrefs) {
            Edge edge = new Edge(EdgeType.SUPPORTS,
                    pkg.getId() + ":" + pkg.getVersion() + ":" + rsrc.getId(),
                    pkg.getId() + ":" + pkg.getVersion() + ":" + (skillRefElem.getAttributeValue("idref") == null ? skillRefElem.getText() : skillRefElem.getAttributeValue("idref")),
                    rsrc.getType(), "x-oli-skill", skillRefElem.getName());
            JsonObject metadata = new JsonObject();
            JsonObject path = new JsonObject();
            AppUtils.pathToRoot(skillRefElem, path, Optional.of(path));
            metadata.add("pathInfo", path);
            edge.setMetadata(new JsonWrapper(metadata));
            edge.setContentPackage(pkg);
            pkg.addEdge(edge);

            filteredEdges.remove(edge);
        }

        // Cleanup skill edges that are no longer referenced in resource
        filteredEdges.forEach(e -> pkg.removeEdge(e));

    }
}
