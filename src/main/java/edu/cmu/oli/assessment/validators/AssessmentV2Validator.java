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
import edu.cmu.oli.assessment.InteractionStyle;
import edu.cmu.oli.assessment.ScoringMethod;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.*;
import edu.cmu.oli.content.resource.validators.BaseResourceValidator;
import edu.cmu.oli.content.resource.validators.ResourceValidator;
import org.jdom2.*;
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
public class AssessmentV2Validator implements ResourceValidator {

    private static final Logger log = LoggerFactory.getLogger(AssessmentV2Validator.class);

    protected Resource rsrc;
    protected Document doc;
    protected boolean throwErrors;

    private XPathFactory xFactory = XPathFactory.instance();
    private XMLOutputter outputter = new XMLOutputter(Format.getCompactFormat());

    BaseResourceValidator baseResourceValidator;

    public AssessmentV2Validator() {
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
        Gson gson = AppUtils.gsonBuilder().serializeNulls().create();
        if ("assessment".equals(rootElmnt.getName())) {
            JsonObject assessment = parseAssessment(rootElmnt);
        }

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

    public JsonObject parseAssessment(Element quizElem) {
        // Assessment
        String assessmentId = quizElem.getAttributeValue("id");
        JsonObject assessment = new JsonObject();

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

        // Number of Attempts Recommended

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

        // Pages
        List<Element> pages = quizElem.getChildren("page");
        JsonArray pagesArray = new JsonArray();
        if (pages.isEmpty()) {
            JsonObject p = new JsonObject();
            parsePageNodes(quizElem, p);
            pagesArray.add(p);
            p.addProperty("number", pagesArray.size());
        } else {
            for (Element pElem : pages) {
                JsonObject p = parsePage(pElem);
                pagesArray.add(p);
                p.addProperty("number", pagesArray.size());
            }
        }
        assessment.add("pages", pagesArray);
        return assessment;
    }

    private JsonObject parsePage(Element pageElem) {

        // Page
        JsonObject page = new JsonObject();

        // ID
        page.addProperty("pageId", pageElem.getAttributeValue("id"));

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
        JsonArray pageNodes = new JsonArray();
        for (Element child : pageElem.getChildren()) {
            if ("title".equals(child.getName()) || "skillref".equals(child.getName())) {
                continue;
            }
            Optional<JsonElement> sequencible = parseSequenceable(child);
            pageNodes.add(sequencible.isPresent() ? sequencible.get() : JsonNull.INSTANCE);
        }
        page.add("nodes", pageNodes);
    }

    private Optional<JsonElement> parseSequenceable(Element nodeElem) {
        String name = nodeElem.getName();
        JsonElement sequencible = null;
        switch (name) {
            case "question":
                sequencible = new JsonObject();
                ((JsonObject) sequencible).add("question", parseQuestion(nodeElem));
                //sequencible = parseQuestion(nodeElem);
                break;
            case "content":
                sequencible = new JsonObject();
                ((JsonObject) sequencible).add("content", parseContentBlock(nodeElem));
                //sequencible = parseContentBlock(nodeElem);
                break;
            case "section":
                sequencible = new JsonObject();
                ((JsonObject) sequencible).add("section", parseSection(nodeElem));
                //sequencible = parseSection(nodeElem);
                break;
            case "selection":
                sequencible = new JsonObject();
                ((JsonObject) sequencible).add("selection", parseSelection(nodeElem));
                //sequencible = parseSelection(nodeElem);
                break;
            case "question_bank_ref":
                sequencible = new JsonObject();
                ((JsonObject) sequencible).add("question_bank_ref", parseQuestionBankRef(nodeElem));
                //sequencible = parseQuestionBankRef(nodeElem);
                break;
            default:
                recordError("unexpected page content: " + name, ErrorLevel.ERROR);
                break;
        }
        return Optional.ofNullable(sequencible);
    }

    private JsonObject materialFromElement(Element element) {
        JsonObject material = new JsonObject();
        String s1 = outputter.outputElementContentString(element);
        s1 = s1.trim().replaceAll("\n", " ").replaceAll("\t", "").replaceAll("\r", "").replaceAll(" +", " ").replaceAll("&amp;", "&");
        material.addProperty("material", s1);
        material.addProperty("dir", element.getAttributeValue("dir"));
        material.addProperty("lang", element.getAttributeValue("lang"));
        return material;
    }

    private JsonElement parseQuestion(Element qElem) {

        // Question ID
        String id = qElem.getAttributeValue("id");

        // Body
        Element bodyElem = qElem.getChild("body");

        JsonObject body = materialFromElement(bodyElem);

        // Question
        JsonObject question = new JsonObject();
        question.addProperty("id", id);
        question.add("body", body);

        // Title
        Element titleElem = qElem.getChild("title");
        if (titleElem != null) {
            question.addProperty("title", titleElem.getTextTrim());
        }

        // Scoring Method
        String gradingAttrVal = qElem.getAttributeValue("grading");
        if (gradingAttrVal != null) {
            String method = gradingAttrVal.trim().toUpperCase();
            question.addProperty("scoringMethod", ScoringMethod.valueOf(method).toString());
        }

        // Ordered
        String orderedAttrVal = qElem.getAttributeValue("ordered");
        if (orderedAttrVal != null) {
            question.addProperty("ordered", "true".equals(orderedAttrVal));
        }

        JsonArray hints = new JsonArray();
        question.add("hints", hints);
        JsonArray solutions = new JsonArray();
        question.add("solutions", solutions);
        JsonArray interactions = new JsonArray();
        question.add("interactions", interactions);
        JsonArray parts = new JsonArray();
        question.add("parts", parts);

        // Interactions, Parts, Hints, Explanation, Solutions
        for (Element childElem : qElem.getChildren()) {
            String name = childElem.getName();

            if ("multiple_choice".equals(name)) {
                parseMultipleChoice(childElem, question, InteractionStyle.MULTIPLE_CHOICE);
            } else if ("fill_in_the_blank".equals(name)) {
                parseMultipleChoice(childElem, question, InteractionStyle.FILL_IN_THE_BLANK);
            } else if ("numeric".equals(name)) {
                parseNumeric(childElem, question);
            } else if ("text".equals(name)) {
                parseText(childElem, question, InteractionStyle.TEXT);
            } else if ("short_answer".equals(name)) {
                parseText(childElem, question, InteractionStyle.SHORT_ANSWER);
            } else if ("essay".equals(name)) {
                parseText(childElem, question, InteractionStyle.ESSAY);
            } else if ("image_hotspot".equals(name)) {
                parseImageHotspot(childElem, question);
            } else if ("ordering".equals(name)) {
                parseOrdering(childElem, question);
            } else if ("part".equals(name)) {
                parsePart(childElem, question);
            } else if ("hint".equals(name)) {
                hints.add(parseHint(childElem, question));
            } else if ("explanation".equals(name)) {
                JsonObject explanation = materialFromElement(bodyElem);
                question.add("explanation", explanation);
            } else if ("solution".equals(name)) {
                solutions.add(parseSolution(childElem, question));
            }
        }

        // Validate input references in question body
        validateInputRefs(question, qElem);
        validateChoiceRefs(question, qElem);

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

        // Scan question for input references outside body
        xexpression = xFactory.compile("descendant::input_ref[not(ancestor::body)]", Filters.element());
        List<Element> invalidRefs = xexpression.evaluate(qElem);
        if (!invalidRefs.isEmpty()) {
            String message = "input_ref only valid within question body: question=" + q.get("id").getAsString();
            recordError(message, ErrorLevel.ERROR);
        }
    }


    private void validateChoiceRefs(JsonObject q, Element qElem) {

        // Scan question body for input references
        Element bodyElem = qElem.getChild("body");
        XPathExpression<Element> xexpression = xFactory.compile("descendant::choice_ref", Filters.element());
        List<Element> inputRefs = xexpression.evaluate(bodyElem);

        // For each input reference...
        for (Object o : inputRefs) {
            Element inputElem = (Element) o;
            final String input = inputElem.getAttributeValue("input");
            final String choice = inputElem.getAttributeValue("choice");
            // Referenced interaction must exist
            JsonObject i = (JsonObject) findInteractionByLocalId(q, input);
            if (i == null) {
                String message = "referenced input does not exist: question=" + q.get("id").getAsString() + ", input=" + input + ", choice=" + choice;
                recordError(message, ErrorLevel.ERROR);
            }
            if (i.get("style").getAsString().equals(InteractionStyle.MULTIPLE_CHOICE.toString())) {
                if (!i.get("labelChoices").getAsBoolean()) {
                    String message = "choice_ref only valid if labels are enabled: question=" + q.get("id").getAsString() + ", input=" + input + ", choice=" + choice;
                    recordError(message, ErrorLevel.ERROR);
                }
                JsonArray choices = i.get("choices").getAsJsonArray();
                boolean choiceLocated = false;
                Iterator<JsonElement> iterator = choices.iterator();
                while (iterator.hasNext()) {
                    JsonObject j = (JsonObject) iterator.next();
                    if (j.get("id").getAsString().equals(choice)) {
                        choiceLocated = true;
                        break;
                    }
                }
                if (!choiceLocated) {
                    String message = "referenced choice does not exist: question=" + q.get("id").getAsString() + ", input=" + input + ", choice=" + choice;
                    recordError(message, ErrorLevel.ERROR);
                }
            } else {
                String message = "choice_ref only valid for multiple choice inputs: question=" + q.get("id").getAsString() + ", input=" + input + ", choice=" + choice;
                recordError(message, ErrorLevel.ERROR);
            }
        }
    }

    private void parseMultipleChoice(Element inputElem, JsonObject q, InteractionStyle style) {

        // Interaction ID
        String id = inputElem.getAttributeValue("id");
        if (id == null) {
            // :FIXME: verify that part identifier is unique
            id = "i" + (q.getAsJsonArray("interactions").size() + 1);
        }

        // Multiple Choice Interaction
        JsonObject i = new JsonObject();
        i.addProperty("id", id);
        i.addProperty("questionId", q.get("id").getAsString());
        i.addProperty("style", style.toString());
        addInteractionToQuestion(i, q);

        // Display Name
        i.addProperty("displayName", inputElem.getAttributeValue("name"));

        // Selection Cardinality
        String selectAttrVal = inputElem.getAttributeValue("select");
        if (selectAttrVal != null) {
            i.addProperty("multipleSelect", "multiple".equals(selectAttrVal));
        }

        // Choice Labels
        String labelsAttrVal = inputElem.getAttributeValue("labels");
        if (labelsAttrVal != null) {
            i.addProperty("labelChoices", "true".equals(labelsAttrVal));
        }

        // Shuffle
        String shuffleAttrVal = inputElem.getAttributeValue("shuffle");
        if (shuffleAttrVal != null) {
            i.addProperty("shuffleChoices", "true".equals(shuffleAttrVal));
        }

        JsonArray choices = i.getAsJsonArray("choices");
        if (choices == null) {
            choices = new JsonArray();
            i.add("choices", choices);
        }
        // Choices
        for (Element c : inputElem.getChildren("choice")) {
            JsonObject choice = parseChoice((Element) c);
            choices.forEach(ch -> {
                if (((JsonObject) ch).get("value").getAsString().equals(choice.get("value").getAsString())) {
                    String message = "duplicate choice identifier: question=" + q.get("id").getAsString() + ", choice=" + choice.get("value").getAsString();
                    recordError(message, ErrorLevel.ERROR);
                }
            });

            choices.add(choice);
        }
    }

    private void parseNumeric(Element inputElem, JsonObject q) {

        // Interaction ID
        String id = inputElem.getAttributeValue("id");
        if (id == null) {
            // :FIXME: verify that part identifier is unique
            id = "i" + (q.get("interactions").getAsJsonArray().size() + 1);
        }

        // Numeric Interaction
        JsonObject i = new JsonObject();
        i.addProperty("id", id);
        i.addProperty("questionId", q.get("id").getAsString());
        i.addProperty("style", InteractionStyle.NUMERIC.toString());
        addInteractionToQuestion(i, q);

        // Display Name
        i.addProperty("displayName", inputElem.getAttributeValue("name"));

        // Notation
        String notationAttrVal = inputElem.getAttributeValue("notation");
        if (notationAttrVal != null) {
            String notationId = notationAttrVal.trim().toUpperCase();
            i.addProperty("notation", notationId);
        }

        // Size
        String sizeAttrVal = inputElem.getAttributeValue("size");
        if (sizeAttrVal != null) {
            String size = sizeAttrVal.trim().toUpperCase();
            i.addProperty("size", size);
        }
    }

    private void parseText(Element inputElem, JsonObject q, InteractionStyle style) {

        // Interaction ID
        String id = inputElem.getAttributeValue("id");
        if (id == null) {
            // :FIXME: verify that part identifier is unique
            id = "i" + (q.get("interactions").getAsJsonArray().size() + 1);
        }

        // Text Interaction
        JsonObject i = new JsonObject();
        i.addProperty("id", id);
        i.addProperty("questionId", q.get("id").getAsString());
        i.addProperty("style", style.toString());

        addInteractionToQuestion(i, q);

        // Display Name
        i.addProperty("displayName", inputElem.getAttributeValue("name"));

        // Case Sensitive
        String caseSensitiveAttrVal = inputElem.getAttributeValue("case_sensitive");
        if (caseSensitiveAttrVal != null) {
            i.addProperty("caseSensitive", "true".equals(caseSensitiveAttrVal));
        }

        // Whitespace Strategy
        String whitespaceAttrVal = inputElem.getAttributeValue("whitespace");
        if (whitespaceAttrVal != null) {
            String whitespace = whitespaceAttrVal.trim().toUpperCase();
            i.addProperty("whitespaceStrategy", whitespace);
        }

        // Size
        String sizeAttrVal = inputElem.getAttributeValue("size");
        if (sizeAttrVal != null) {
            String size = sizeAttrVal.trim().toUpperCase();
            i.addProperty("size", size);
        }
    }

    private void parseImageHotspot(Element inputElem, JsonObject q) {

        // Interaction ID
        String id = inputElem.getAttributeValue("id");
        if (id == null) {
            // :FIXME: verify that part identifier is unique
            id = "i" + (q.get("interactions").getAsJsonArray().size() + 1);
        }

        // Image
        String src = inputElem.getAttributeValue("src");

        int height = 0;
        try {
            height = inputElem.getAttribute("height").getIntValue();
        } catch (DataConversionException e) {
            recordError("height is not a positive integer", ErrorLevel.ERROR);
        }
        if (height < 1) {
            recordError("height is not a positive integer", ErrorLevel.ERROR);
        }

        int width = 0;
        try {
            width = inputElem.getAttribute("width").getIntValue();
        } catch (DataConversionException e) {
            recordError("width is not a positive integer", ErrorLevel.ERROR);
        }
        if (width < 1) {
            recordError("width is not a positive integer", ErrorLevel.ERROR);
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
        addInteractionToQuestion(i, q);

        // Display Name
        i.addProperty("displayName", inputElem.getAttributeValue("name"));

        // Selection Cardinality
        String selectAttrVal = inputElem.getAttributeValue("select");
        if (selectAttrVal != null) {
            i.addProperty("multipleSelect", "multiple".equals(selectAttrVal));
        }

        JsonArray hotspots = new JsonArray();
        i.add("hotspots", hotspots);
        // Hotspots
        for (Element h : inputElem.getChildren("hotspot")) {
            JsonObject hotspot = parseHotspot(h);
            hotspots.forEach(ht -> {
                if (((JsonObject) ht).get("value").getAsString().equals(hotspot.get("value").getAsString())) {
                    String message = "duplicate hotspot identifier: question=" + q.get("id").getAsString() + ", hotspot=" + hotspot.get("value").getAsString();
                    recordError(message, ErrorLevel.ERROR);
                }
            });
            hotspots.add(hotspot);
        }

    }

    private void parseOrdering(Element inputElem, JsonObject q) {

        // Interaction ID
        String id = inputElem.getAttributeValue("id");
        if (id == null) {
            // :FIXME: verify that part identifier is unique
            id = "i" + (q.get("interactions").getAsJsonArray().size() + 1);
        }

        // // Ordering Interaction
        JsonObject i = new JsonObject();
        i.addProperty("id", id);
        i.addProperty("questionId", q.get("id").getAsString());
        i.addProperty("style", InteractionStyle.ORDERING.toString());
        addInteractionToQuestion(i, q);

        // Display Name
        i.addProperty("displayName", inputElem.getAttributeValue("name"));

        // Shuffle
        String shuffleAttrVal = inputElem.getAttributeValue("shuffle");
        if (shuffleAttrVal != null) {
            i.addProperty("shuffleChoices", "true".equals(shuffleAttrVal));
        }

        JsonArray choices = new JsonArray();
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
        i.add("choices", choices);

    }

    private void addInteractionToQuestion(JsonObject i, JsonObject q) {

        String id = i.get("id").getAsString();
        if (findInteractionByLocalId(q, id) != null) {
            recordError("duplicate input identifier: " + id, ErrorLevel.ERROR);
        }
        JsonArray interactions = q.getAsJsonArray("interactions");
        if (interactions == null) {
            interactions = new JsonArray();
            q.add("interactions", interactions);
        }
        interactions.add(i);
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

        String coords = hotspotElem.getAttributeValue("coords");

        JsonObject hotspot = new JsonObject();
        hotspot.addProperty("value", value);
        hotspot.addProperty("shapeId", shapeId);
        hotspot.addProperty("coords", coords);
        hotspot.addProperty("label", hotspotElem.getTextTrim());
        return hotspot;
    }

    private JsonObject parsePart(Element partElem, JsonObject q) {

        // Part ID
        String id = partElem.getAttributeValue("id");
        if (id == null) {
            // :FIXME: verify that part identifier is unique
            id = "p" + (q.get("parts").getAsJsonArray().size() + 1);
        }

        // Part
        JsonObject p = new JsonObject();
        p.addProperty("id", id);
        p.addProperty("questionId", q.get("id").getAsString());

        final String partId = id;
        JsonArray parts = null;
        if (q.has("parts")) {
            parts = q.get("parts").getAsJsonArray();
            parts.forEach(pt -> {
                if (((JsonObject) pt).get("id").getAsString().equals(partId)) {
                    recordError("duplicate part identifier: " + partId, ErrorLevel.ERROR);
                }
            });
        } else {
            parts = new JsonArray();
            q.add("parts", parts);
        }
        parts.add(p);

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
                recordError("correct is not an integer" + partId, ErrorLevel.ERROR);
            }
        }

        // Score Out Of
        Attribute scoreOutOfAttr = partElem.getAttribute("score_out_of");
        if (scoreOutOfAttr != null) {
            try {
                Integer scoreOutOf = scoreOutOfAttr.getIntValue();
                p.addProperty("scoreOutOfExplicit", scoreOutOf);
            } catch (DataConversionException e) {
                recordError("score_out_of is not an integer" + partId, ErrorLevel.ERROR);
            }
        }

        JsonArray explicitTargets = new JsonArray();
        if (p.get("explicitTargets") != null) {
            explicitTargets = p.get("explicitTargets").getAsJsonArray();
        } else {
            p.add("explicitTargets", explicitTargets);
        }

        // Target Interactions
        String targets = partElem.getAttributeValue("targets");
        if (targets != null && !"".equals(targets.trim())) {
            for (String t : targets.split(",")) {
                JsonObject i = (JsonObject) findInteractionByLocalId(q, t);
                // Does the referenced input exist within the question?
                if (i == null) {
                    String message = "referenced input does not exist: question=" + q.get("id").getAsString() + ", input=" + t;
                    recordError(message + id, ErrorLevel.ERROR);
                }
                explicitTargets.add(i);
            }
        }

        JsonArray responseConditions = new JsonArray();
        if (p.get("responseConditions") != null) {
            responseConditions = p.get("responseConditions").getAsJsonArray();
        } else {
            p.add("responseConditions", responseConditions);
        }

        JsonArray gradingCriterias = new JsonArray();
        if (p.get("gradingCriterias") != null) {
            gradingCriterias = p.get("gradingCriterias").getAsJsonArray();
        } else {
            p.add("gradingCriterias", gradingCriterias);
        }

        JsonArray hints = new JsonArray();
        if (p.get("hints") != null) {
            hints = p.get("hints").getAsJsonArray();
        } else {
            p.add("hints", hints);
        }

        // Process child elements
        for (Element childElem : partElem.getChildren()) {
            String childName = childElem.getName();

            if ("response".equals(childName)) {
                // Response Condition
                responseConditions.add(parseResponse(childElem, q));
            } else if ("response_mult".equals(childName)) {
                // Response Condition
                responseConditions.add(parseResponseMult(childElem, q));
            } else if ("no_response".equals(childName)) {
                // No Response
                p.add("noResponseOutcome", parseOutcome(childElem, q));
            } else if ("grading_criteria".equals(childName)) {
                // Grading Criteria
                gradingCriterias.add(parseGradingCriteria(childElem, q));
            } else if ("hint".equals(childName)) {
                // Hint
                hints.add(parseHint(childElem, q));
            } else if ("explanation".equals(childName)) {
                // Explanation
                JsonObject explanation = materialFromElement(childElem);
                p.add("explanation", explanation);

            }
        }

        return p;
    }

    private JsonObject parseResponse(Element respElem, JsonObject q) {

        // Response Condition
        JsonObject rc = new JsonObject();

        // Match Criteria
        rc.add("criteria", parseResponseCriteria(respElem, q));

        // Outcome
        rc.add("outcome", parseOutcome(respElem, q));

        return rc;
    }

    private JsonObject parseResponseMult(Element respElem, JsonObject q) {

        // Response Condition
        JsonObject rc = new JsonObject();

        // Matching Strategy
        String matchStyleAttrVal = respElem.getAttributeValue("match_style");
        if (matchStyleAttrVal != null) {
            String id = matchStyleAttrVal.trim().toUpperCase();
            rc.addProperty("matchingStrategy", id);
        }

        JsonArray criterias = new JsonArray();
        rc.add("criterias", criterias);
        // Match Criteria
        for (Element matchElem : respElem.getChildren("match")) {
            criterias.add(parseResponseCriteria(matchElem, q));
        }

        // Outcome
        rc.add("outcome", parseOutcome(respElem, q));

        return rc;
    }

    private JsonObject parseResponseCriteria(Element matchElem, JsonObject q) {

        // Interaction
        JsonObject i = resolveInputReference(matchElem, q);

        // Match Criteria
        String match = matchElem.getAttributeValue("match");

        JsonObject responseCriteria = new JsonObject();
        // interactionId maybe null for cases where x-oli-inline-assessment is custom dnd activity
        if(i != null) {
            responseCriteria.addProperty("interactionId", i.get("id").getAsString());
        }
        responseCriteria.addProperty("match", match);
//:FIXME: find way to validate match criteria
//        try {
//            return (new ResponseCriteria(i, match));
//        } catch (PatternFormatException ex) {
//            throw (new InvalidDocumentException(ex));
//        }
        return responseCriteria;
    }

    private JsonObject resolveInputReference(Element matchElem, JsonObject q) {

        JsonObject i = null;

        // Attempt to resolve the referenced input
        String input = matchElem.getAttributeValue("input");
        if (input == null) {
            if (q.get("interactions") != null && q.get("interactions").getAsJsonArray().size() > 1) {
                String message = "input IDs must be specified if question contains more than one input: ";
                recordError(message + "question id=" + q.get("id").getAsString(), ErrorLevel.ERROR);
            } else if (q.get("interactions") != null) {
                i = (JsonObject) q.get("interactions").getAsJsonArray().get(0);
            }
        } else {
            i = (JsonObject) findInteractionByLocalId(q, input);
        }

        // Does the referenced input exist within the question?
        if (i == null) {
            String message = "referenced input does not exist: question=" + q.get("id").getAsString() + ", input=" + input;
            recordError(message + q.get("id").getAsString(), ErrorLevel.ERROR);
        }

        return i;
    }

    private JsonObject parseOutcome(Element outElem, JsonObject q) {

        // Outcome
        JsonObject outcome = new JsonObject();

        // Score
        Attribute scoreAttr = outElem.getAttribute("score");
        if (scoreAttr != null) {
            try {
                outcome.addProperty("score", scoreAttr.getIntValue());
            } catch (DataConversionException e) {
                recordError("score is not an integer", ErrorLevel.ERROR);
            }
        }

        // Name
        outcome.addProperty("displayName", outElem.getAttributeValue("name"));

        JsonArray feedbacks = new JsonArray();
        outcome.add("feedbacks", feedbacks);
        // Feedback
        for (Element o : outElem.getChildren("feedback")) {
            feedbacks.add(parseFeedback(o, q));
        }

        return outcome;
    }

    private JsonObject parseFeedback(Element fbkElem, JsonObject q) {

        // Body
        JsonObject body = materialFromElement(fbkElem);


        // Feedback
        JsonObject feedback = new JsonObject();
        feedback.add("body", body);

        JsonArray explicitTargets = new JsonArray();
        feedback.add("explicitTargets", explicitTargets);

        // Target Interactions
        String targets = fbkElem.getAttributeValue("targets");
        if (targets != null && !"".equals(targets.trim())) {
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
        return feedback;
    }

    private JsonObject parseGradingCriteria(Element critElem, JsonObject q) {
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
        JsonObject outcome = parseOutcome(critElem, q);

        // Grading Criteria
        JsonObject gradingCriteria = new JsonObject();
        gradingCriteria.add("body", body);
        gradingCriteria.add("outcome", outcome);
        return gradingCriteria;
    }

    private JsonObject parseHint(Element hintElem, JsonObject q) {

        // Body
        JsonObject body = materialFromElement(hintElem);
        // Hint
        JsonObject hint = new JsonObject();
        hint.add("body", body);

        JsonArray explicitTargets = new JsonArray();
        hint.add("explicitTargets", explicitTargets);
        // Target Interactions
        String targets = hintElem.getAttributeValue("targets");
        if (targets != null && !"".equals(targets.trim())) {
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

    private JsonObject parseSolution(Element solutionElem, JsonObject q) {

        JsonObject solution = new JsonObject();

        // Title
        Element titleElem = solutionElem.getChild("title");
        if (titleElem != null) {
            solution.addProperty("title", titleElem.getTextTrim());
        }

        JsonArray inputValues = new JsonArray();
        solution.add("inputValues", inputValues);
        // Input Values
        for (Element valueElem : solutionElem.getChildren("input_value")) {
            JsonObject i = resolveInputReference(valueElem, q);
            String value = valueElem.getText();
            inputValues.add(value);
            //:FIXME: way to validate value
//            try {
//                solution.setInputValue(i, value);
//            } catch (InputFormatException ex) {
//                String message = "invalid input for solution: quesiton=" + q.getQuestionId() + ", value=" + value;
//                throw (new InvalidDocumentException(message, ex));
//            }
        }

        return solution;
    }

    private JsonObject parseContentBlock(Element nodeElem) {
        // Content block
        JsonObject body = materialFromElement(nodeElem);

        JsonObject content = new JsonObject();
        content.add("body", body);

        // Availability
        String availableAttrVal = nodeElem.getAttributeValue("available");
        if (availableAttrVal != null) {
            String id = availableAttrVal.trim().toUpperCase();
            content.addProperty("availability", id);
            //content.setAvailability(ContentBlock.Availability.valueOf(id));
        }

        return content;
    }

    private JsonObject parseSection(Element sectElem) {

        // Section
        JsonObject section = new JsonObject();

        // Section ID
        section.addProperty("sectionId", sectElem.getAttributeValue("id"));

        // Title
        Element titleElem = sectElem.getChild("title");
        if (titleElem != null) {
            section.addProperty("title", titleElem.getText());
        }

        JsonArray nodes = new JsonArray();
        section.add("nodes", nodes);

        // Section Nodes
        for (Element child : sectElem.getChildren()) {
            if ("title".equals(child.getName()) || "skillref".equals(child.getName())) {
                continue;
            }
            Optional<JsonElement> sequencible = parseSequenceable(child);
            nodes.add(sequencible.isPresent() ? sequencible.get() : JsonNull.INSTANCE);
        }

        return section;
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
                spec.addProperty("number", count);
            }
        }

        // Selection Strategy
        String strategyAttrVal = selElem.getAttributeValue("strategy");
        if (strategyAttrVal != null) {
            String id = strategyAttrVal.trim().toUpperCase();
            spec.addProperty("selectionStrategy", id);
        }

        // Exhaustion Policy
        String exhaustionAttrVal = selElem.getAttributeValue("exhaustion");
        if (exhaustionAttrVal != null) {
            String id = exhaustionAttrVal.trim().toUpperCase();
            //spec.setExhaustionPolicy(ExhaustionPolicy.valueOf(id));
            spec.addProperty("exhaustionPolicy", id);
        }

        // Selection Scope
        String scopeAttrVal = selElem.getAttributeValue("scope");
        if (scopeAttrVal != null) {
            String id = scopeAttrVal.trim().toUpperCase();
            //spec.setSelectionScope(SelectionScope.valueOf(id));
            spec.addProperty("selectionScope", id);
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

        // Content
        Element contentElem = poolElem.getChild("content");
        if (contentElem != null) {
            pool.add("content", parseContentBlock(contentElem));
        }

        JsonArray poolEntries = new JsonArray();
        pool.add("entrys", poolEntries);
        // Questions
        List<Element> children = poolElem.getChildren();
        for (Element childElem : children) {
            String name = childElem.getName();
            if ("section".equals(name)) {
                poolEntries.add(parseSection(childElem));
            } else if ("question".equals(name)) {
                poolEntries.add(parseQuestion(childElem));
            }
        }

        return pool;
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

        // Questions
        List<Element> children = qbElem.getChildren("question");
        JsonArray questions = new JsonArray();
        for (Object o : children) {
            Element childElem = (Element) o;
            questions.add(parseQuestion(childElem));
        }
        qb.add("questions", questions);

        return qb;
    }

    private JsonObject parseQuestionBankRef(Element refElem) {
        String idref = refElem.getAttributeValue("idref");
        JsonObject questionBankRef = new JsonObject();
        questionBankRef.addProperty("idref", idref);
        return questionBankRef;
    }

    private void validateImageInputs() {
        // Locate image elements in document
        XPathFactory xFactory = XPathFactory.instance();
        XPathExpression<Element> xexpression = xFactory.compile("//image_input", Filters.element());
        List<Element> kids = xexpression.evaluate(doc);

        // For each image...
        for (Element imageElem : kids) {
            baseResourceValidator.validateImage(imageElem);
        }
    }

    protected void createLinkEdges() {
        // Locate all activity links in document
        XPathFactory xFactory = XPathFactory.instance();
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
            List<Element> imgKids = childElmnt.getChildren("image");
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
        for (JsonElement i : interactions) {
            if (id.equals(((JsonObject) i).get("id").getAsString())) return i;
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
}
