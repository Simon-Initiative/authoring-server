package edu.cmu.oli.assessment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import edu.cmu.oli.assessment.evaluators.MatcherByType;
import edu.cmu.oli.assessment.evaluators.ResponseMatcher;
import edu.cmu.oli.assessment.validators.AssessmentV2Validator;
import edu.cmu.oli.common.xml.RelativePathRewriter;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.ContentServiceException;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.controllers.Delivery;
import edu.cmu.oli.content.controllers.MetadataController;
import edu.cmu.oli.content.controllers.ThinPreviewController;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.entities.Resource;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.output.XMLOutputter;
import org.jdom2.transform.JDOMSource;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.slf4j.Logger;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Raphael Gachuhi
 */
public class InlineAssessmentDelivery implements Delivery {
    private static final String assessmentMaterialDocType = "x-oli-assessment_materials";
    @Inject
    @Logging
    Logger log;

    @PersistenceContext
    EntityManager em;

    @Inject
    ThinPreviewController thinPreviewController;

    @Inject
    MetadataController metadataController;

    @Inject
    @ConfigurationCache
    Instance<Configurations> config;

    private XPathFactory xFactory = XPathFactory.instance();

    @Override
    public JsonElement deliver(Resource resource, Document document, String serverUrl, String themeId,
            JsonElement metaInfo) {
        int pageNumber = 1;
        String requestMode = "deliver";
        int attemptNumber = 1;
        if (metaInfo != null) {
            JsonObject deliveryFilters = metaInfo.getAsJsonObject();
            if (deliveryFilters.has("requestMode")) {
                requestMode = deliveryFilters.get("requestMode") != JsonNull.INSTANCE
                        ? deliveryFilters.get("requestMode").getAsString()
                        : requestMode;
            }
            if (deliveryFilters.has("pageNumber")) {
                pageNumber = deliveryFilters.get("pageNumber") != JsonNull.INSTANCE
                        ? deliveryFilters.get("pageNumber").getAsInt()
                        : pageNumber;
                if (pageNumber < 1) {
                    pageNumber = 1;
                }
            }

            if (deliveryFilters.has("attemptNumber")) {
                attemptNumber = deliveryFilters.get("pageNumber") != JsonNull.INSTANCE
                        && deliveryFilters.get("attemptNumber").getAsInt() > 0
                                ? deliveryFilters.get("attemptNumber").getAsInt()
                                : attemptNumber;
            }

        }
        AssessmentV2Validator assessmentV2Validator = new AssessmentV2Validator();
        assessmentV2Validator.initValidator(resource, document, false);
        JsonObject assessement = assessmentV2Validator.parseAssessment(document.getRootElement());
        // Gson gson =
        // AppUtils.gsonBuilder().setPrettyPrinting().serializeNulls().create();
        // log.info(gson.toJson(assessement));

        JsonObject payload = new JsonObject();
        payload.addProperty("id", resource.getId());
        payload.addProperty("title", resource.getTitle());
        payload.addProperty("shortTitle", resource.getShortTitle());
        payload.add("score", null);

        JsonArray pages = assessement.getAsJsonArray("pages");
        if (pageNumber > pages.size()) {
            pageNumber = pages.size();
        }
        JsonObject delivery = new JsonObject();
        delivery.addProperty("mode", requestMode);
        delivery.addProperty("attempt", attemptNumber);
        delivery.addProperty("userGuid", "previewer");
        delivery.addProperty("preface", false);
        delivery.add("duration", null);
        delivery.add("navigation", null);
        delivery.add("overallScore", null);
        delivery.addProperty("pageCount", pages.size());
        delivery.addProperty("currentPage", pageNumber);
        delivery.addProperty("forwardProgress", pageNumber - 1);
        delivery.add("deadline", null);
        delivery.add("timeRemaining", null);
        delivery.addProperty("previousPageAccessible", pageNumber > 0);
        delivery.addProperty("nextPageAccessible", pageNumber < pages.size());

        JsonArray progressForPages = new JsonArray();
        for (int x = 0; x < pages.size(); x++) {
            JsonObject page = new JsonObject();
            page.addProperty("pageNumber", x + 1);
            page.addProperty("progress", "NOT_STARTED");
            progressForPages.add(page);
        }
        delivery.add("progressForPages", progressForPages);

        payload.add("delivery", delivery);

        JsonObject options = new JsonObject();
        options.add("id", null);
        options.addProperty("hasPassword", false);
        options.addProperty("lateStart", false);
        options.add("timeLimit", null);
        options.addProperty("gracePeriod", 0);
        options.addProperty("lateMode", "accept_scored");
        options.addProperty("enableReview", true);
        options.addProperty("feedbackMode", "immediate");
        options.add("maxAttempts", null);
        options.addProperty("startAttempt", false);

        payload.add("options", options);

        payload.add("feebackList", null);

        JsonObject page = new JsonObject();
        JsonObject pageObject = pages.get(pageNumber - 1).getAsJsonObject();
        page.add("id", pageObject.get("pageId"));
        page.addProperty("number", pageObject.get("number").getAsNumber());
        page.add("title", pageObject.get("title"));

        payload.add("page", page);

        JsonArray assessmentNodes = new JsonArray();
        page.add("assessmentNodes", assessmentNodes);

        JsonArray nodes = pageObject.getAsJsonArray("nodes");

        JsonObject attemptNode = new JsonObject();
        attemptNode.addProperty("number", attemptNumber);
        attemptNode.addProperty("dateStarted", "05/11/2018 9:08 AM EDT");
        attemptNode.add("dateSubmitted", null);
        attemptNode.addProperty("submittedLate", false);
        attemptNode.add("accepted", null);
        attemptNode.add("dateProcessed", null);
        attemptNode.add("processedByUserId", null);
        attemptNode.add("score", null);
        attemptNode.add("userScoringRequired", null);
        attemptNode.add("deadline", null);
        attemptNode.add("timeRemaining", null);

        int questionNumber = 0;
        for (JsonElement n : nodes) {
            JsonObject node = n.getAsJsonObject();
            if (node.has("content")) {
                JsonObject content = node.getAsJsonObject("content");
                JsonObject contentNode = new JsonObject();
                assessmentNodes.add(contentNode);
                contentNode.addProperty("id", AppUtils.generateUID(5));
                contentNode.add("questionId", null);
                contentNode.addProperty("type", "content");
                contentNode.add("body", AppUtils.createJsonElement(transformBodyContent(resource,
                        content.getAsJsonObject("body").get("material").getAsString(), serverUrl, themeId)));
                contentNode.add("number", null);
                contentNode.add("scoreOutOf", null);
                contentNode.add("noResponseScore", null);
                contentNode.add("title", null);
                contentNode.add("ordered", null);
                contentNode.add("scoringMethod", null);
                contentNode.add("interactions", null);
                contentNode.add("parts", null);
                contentNode.add("hints", null);
                contentNode.add("assessmentNodes", null);
            } else if (node.has("question_bank_ref")) {

            } else if (node.has("section")) {

            } else if (node.has("selection")) {

            } else if (node.has("question")) {
                JsonObject question = node.getAsJsonObject("question");
                JsonObject questionNode = new JsonObject();
                assessmentNodes.add(questionNode);
                String genQuestionId = question.get("id").getAsString() + "_" + AppUtils.generateUID(5);
                questionNode.addProperty("id", genQuestionId);
                questionNode.add("questionId", question.get("id"));
                questionNode.addProperty("type", "question");
                JsonObject body = question.getAsJsonObject("body");
                questionNode.add("body", AppUtils.createJsonElement(
                        transformBodyContent(resource, body.get("material").getAsString(), serverUrl, themeId)));
                questionNode.addProperty("number", ++questionNumber);
                questionNode.add("scoreOutOf", null);
                questionNode.add("noResponseScore", null);
                questionNode.add("title", question.has("title") ? question.get("title") : null);
                questionNode.addProperty("ordered", false);
                questionNode.add("scoringMethod", null);

                JsonArray interactions = question.getAsJsonArray("interactions");

                JsonArray interactionsNode = new JsonArray();
                questionNode.add("interactions", interactionsNode);
                for (JsonElement i : interactions) {
                    JsonObject interaction = i.getAsJsonObject();
                    JsonObject interactionNode = new JsonObject();
                    interactionsNode.add(interactionNode);
                    String genInterationId = interaction.get("id").getAsString() + "_" + AppUtils.generateUID(5);
                    interactionNode.addProperty("id", genInterationId);
                    interactionNode.add("interactionRef", interaction.get("id"));
                    String type = interaction.get("style").getAsString();
                    interactionNode.addProperty("type", type.toLowerCase());
                    if (type.equalsIgnoreCase("image_hotspot")) {
                        interactionNode.addProperty("select", "multiple");
                        interactionNode.addProperty("inputComponent", "checkbox");
                    } else {
                        interactionNode.addProperty("select",
                                interaction.has("multipleSelect") && interaction.get("multipleSelect").getAsBoolean()
                                        ? "multiple"
                                        : "single");
                        interactionNode.addProperty("inputComponent", "default");
                    }
                    interactionNode.add("displayName", interaction.get("displayName"));

                    JsonObject image = interaction.has("imageSrc") ? interaction.getAsJsonObject("imageSrc") : null;
                    interactionNode.add("imageSrc", image != null ? image.get("src") : null);
                    interactionNode.add("height", image != null ? image.get("height") : null);
                    interactionNode.add("width", image != null ? image.get("width") : null);

                    JsonArray hotspots = interaction.has("hotspots") ? interaction.getAsJsonArray("hotspots") : null;
                    if (hotspots != null) {
                        JsonArray hotspotsNodes = new JsonArray();
                        interactionNode.add("hotspots", hotspotsNodes);
                        hotspots.forEach(hs -> {
                            JsonObject hsO = hs.getAsJsonObject();
                            JsonObject hotspotNode = new JsonObject();
                            hotspotsNodes.add(hotspotNode);
                            hotspotNode.addProperty("values", hsO.get("value").getAsString());
                            hotspotNode.addProperty("shape", hsO.get("shapeId").getAsString());
                            hotspotNode.addProperty("cordinates", hsO.get("coords").getAsString());
                            hotspotNode.addProperty("label", hsO.get("label").getAsString());
                        });
                    }
                    interactionNode.add("labels", interaction.get("labelChoices"));

                    JsonArray choices = interaction.getAsJsonArray("choices");
                    if (choices != null) {
                        JsonArray choicesNode = new JsonArray();
                        interactionNode.add("choices", choicesNode);
                        for (JsonElement c : choices) {
                            JsonObject choice = c.getAsJsonObject();
                            JsonObject choiceNode = new JsonObject();
                            choicesNode.add(choiceNode);
                            choiceNode.add("value", choice.get("value"));
                            body = choice.getAsJsonObject("body");
                            choiceNode.add("color", body.get("color"));
                            choiceNode.add("body", AppUtils.createJsonElement(transformBodyContent(resource,
                                    body.get("material").getAsString(), serverUrl, themeId)));
                        }

                    } else {
                        interactionNode.add("choices", null);
                    }
                    interactionNode.add("size", interaction.get("size"));
                    interactionNode.add("notation", interaction.get("notation"));
                    interactionNode.add("caseSensitive", interaction.get("caseSensitive"));
                    interactionNode.add("overrideValue", interaction.get("overrideValue"));
                }

                // Emulate question attempt data
                JsonArray questionAttemptNodes = new JsonArray();
                attemptNode.add("questionAttempts", questionAttemptNodes);
                JsonObject questionAttemptNode = new JsonObject();
                questionAttemptNodes.add(questionAttemptNode);
                questionAttemptNode.addProperty("id", genQuestionId);
                questionAttemptNode.add("questionId", question.get("id"));
                questionAttemptNode.addProperty("attemptNumber", 1);
                questionAttemptNode.add("userScoringRequired", null);
                questionAttemptNode.addProperty("correct", false);
                questionAttemptNode.add("noResponse", null);
                questionAttemptNode.add("score", null);
                questionAttemptNode.addProperty("submitted", false);
                questionAttemptNode.add("responses", null);
                questionAttemptNode.add("solutions", null);
                questionAttemptNode.add("explanationBody", null);
                questionAttemptNode.add("lang", null);
                questionAttemptNode.add("pageProgress", null);

                JsonArray parts = question.getAsJsonArray("parts");
                JsonArray partsNode = new JsonArray();
                questionNode.add("parts", partsNode);
                int partPosition = -1;
                for (JsonElement p : parts) {
                    JsonObject part = p.getAsJsonObject();
                    JsonObject partNode = new JsonObject();
                    partsNode.add(partNode);
                    String partGenId = part.get("id").getAsString() + "_" + AppUtils.generateUID(5);
                    partNode.addProperty("id", partGenId);
                    partNode.add("partId", part.get("id"));
                    partNode.addProperty("position", ++partPosition);

                    JsonArray responseConditions = part.getAsJsonArray("responseConditions");

                    String inputRef = responseConditions.size() > 0 ? responseConditions.get(0).getAsJsonObject()
                            .getAsJsonObject("criteria").get("interactionId").getAsString() : null;
                    partNode.addProperty("targetInputs", inputRef);
                    partNode.addProperty("references", inputRef);

                    partNode.add("correctThreshold", null);
                    partNode.add("scoreOutOf", null);
                    partNode.add("noResponseScore", null);
                    partNode.add("title", part.get("title"));
                    partNode.add("gradingCriteriaList", null);

                    JsonArray partHints = part.getAsJsonArray("hints");
                    if (partHints.size() < 1)
                        continue;

                    JsonArray partHintsNode = new JsonArray();
                    partNode.add("hints", partHintsNode);
                    for (JsonElement ph : partHints) {
                        JsonObject partHint = ph.getAsJsonObject();
                        JsonObject partHintNode = new JsonObject();
                        partHintsNode.add(partHintNode);

                        partHintNode.addProperty("id", inputRef + "_" + AppUtils.generateUID(5));
                        partHintNode.addProperty("targetInputs", inputRef);
                        partHintNode.add("body", AppUtils.createJsonElement(transformBodyContent(resource,
                                partHint.getAsJsonObject("body").get("material").getAsString(), serverUrl, themeId)));

                    }

                    // Emulate part attempt data
                    JsonArray partAttemptNodes = new JsonArray();
                    questionAttemptNode.add("partAttempts", partAttemptNodes);
                    JsonObject partAttemptNode = new JsonObject();
                    partAttemptNodes.add(partAttemptNode);

                    partAttemptNode.addProperty("id", partGenId);
                    partAttemptNode.addProperty("partId", part.get("id").getAsString());
                    partAttemptNode.add("correct", null);
                    partAttemptNode.add("noResponse", null);
                    partAttemptNode.addProperty("targetInput", inputRef);
                    partAttemptNode.add("feedbacks", null);
                    partAttemptNode.addProperty("feedbackVisible", false);
                    partAttemptNode.add("score", null);
                    partAttemptNode.addProperty("attemptNumber", 1);
                    partAttemptNode.add("explanationBody", null);
                    partAttemptNode.add("lang", null);
                    partAttemptNode.add("hintRef", null);
                    partAttemptNode.addProperty("hintVisible", false);

                }

                questionNode.add("hints", null);
                questionNode.add("assessmentNodes", null);
            }
        }

        payload.add("feedbackList", null);
        payload.add("attempt", attemptNode);

        return payload;
    }

    public JsonElement evaluateResponses(Resource resource, Document document, String serverUrl, String themeId,
            String userGuid, int attemptNumber, String questionId, boolean start, JsonObject inputs) {

        AssessmentV2Validator assessmentV2Validator = new AssessmentV2Validator();
        assessmentV2Validator.initValidator(resource, document, false);
        JsonObject assessement = assessmentV2Validator.parseAssessment(document.getRootElement());

        // Gson gson =
        // AppUtils.gsonBuilder().setPrettyPrinting().serializeNulls().create();
        // log.info(gson.toJson(assessement));
        Map<String, String> questionToPage = new HashMap<>();
        JsonArray questions = new JsonArray();
        JsonArray pages = assessement.getAsJsonArray("pages");
        if (pages != null) {
            int pageIndex = 0;
            for (JsonElement p : pages) {
                pageIndex++;
                JsonArray nodes = p.getAsJsonObject().getAsJsonArray("nodes");
                for (JsonElement n : nodes) {
                    if (n.getAsJsonObject().has("question")) {
                        questions.add(n);
                        questionToPage.put(n.getAsJsonObject().getAsJsonObject("question").get("id").getAsString(),
                                String.valueOf(pageIndex));
                    }
                }
            }
        }

        JsonObject matchQuestion = null;

        for (JsonElement q : questions) {
            JsonObject question = q.getAsJsonObject().getAsJsonObject("question");
            if (questionId.startsWith(question.get("id").getAsString())) {
                matchQuestion = question;
                break;
            }
        }

        if (matchQuestion == null) {
            log.error("Question not found " + questionId);
            return new JsonObject();
        }

        // log.info(gson.toJson(matchQuestion));

        JsonArray parts = matchQuestion.getAsJsonArray("parts");
        JsonArray interactions = matchQuestion.getAsJsonArray("interactions");

        JsonArray responses = inputs.getAsJsonArray("input_value");

        JsonObject questionAttempt = new JsonObject();
        questionAttempt.addProperty("id", questionId);
        questionAttempt.addProperty("questionId", matchQuestion.get("id").getAsString());
        questionAttempt.addProperty("attemptNumber", attemptNumber);
        questionAttempt.add("userScoringRequired", null);
        questionAttempt.addProperty("correct", false);
        questionAttempt.add("noResponse", null);
        questionAttempt.add("score", null);
        questionAttempt.addProperty("submitted", false);
        JsonArray rs = new JsonArray();
        questionAttempt.add("responses", rs);

        questionAttempt.add("solutions", null);
        questionAttempt.add("explanationBody", null);
        questionAttempt.add("lang", null);
        JsonObject pageProgress = new JsonObject();
        questionAttempt.add("pageProgress", pageProgress);

        pageProgress.addProperty("pageNumber", questionToPage.get(matchQuestion.get("id").getAsString()));
        pageProgress.addProperty("progress", "STARTED");

        JsonArray partsAttempt = new JsonArray();

        questionAttempt.add("partAttempts", partsAttempt);
        responses.forEach(r -> {
            JsonObject rss = new JsonObject();
            rss.addProperty("inputRef", r.getAsJsonObject().get("input").getAsString());
            rss.addProperty("value", r.getAsJsonObject().get("value").getAsString());
            rs.add(rss);
            parts.forEach(p -> {
                JsonArray responseConditions = p.getAsJsonObject().getAsJsonArray("responseConditions");

                String id = p.getAsJsonObject().get("id").getAsString();
                String partGenId = id + "_" + AppUtils.generateUID(5);
                String inputRef = responseConditions.size() > 0
                        ? responseConditions.get(0).getAsJsonObject().getAsJsonObject("criteria").get("interactionId")
                                .getAsString()
                        : null;
                JsonObject partAttempt = new JsonObject();

                partAttempt.addProperty("id", partGenId);
                partAttempt.addProperty("partId", id);
                partAttempt.add("correct", null);
                partAttempt.add("noResponse", null);
                partAttempt.addProperty("targetInput", inputRef);
                partAttempt.add("feedbacks", null);
                partAttempt.addProperty("feedbackVisible", false);
                partAttempt.add("score", null);
                partAttempt.addProperty("attemptNumber", 1);
                partAttempt.add("explanationBody", null);
                partAttempt.add("lang", null);
                partAttempt.add("hintRef", null);
                partAttempt.addProperty("hintVisible", false);

                partsAttempt.add(partAttempt);
                AtomicBoolean alreadyCorrect = new AtomicBoolean(false);

                responseConditions.forEach(c -> {
                    JsonObject criteria = c.getAsJsonObject().getAsJsonObject("criteria");
                    JsonObject interaction = lookupInteraction(criteria.get("interactionId").getAsString(),
                            interactions);
                    if (evaluateCondition(criteria, interaction, r)) {

                        JsonObject outcome = c.getAsJsonObject().getAsJsonObject("outcome");
                        double scoreValue = outcome.has("score") ? outcome.get("score").getAsDouble() : 0;
                        boolean correct = scoreValue > 0;

                        boolean checkForHigherScore = false;
                        if (correct) {
                            if (alreadyCorrect.get()) {
                                checkForHigherScore = true;
                            }
                            alreadyCorrect.set(true);
                        } else {
                            if (alreadyCorrect.get()) {
                                return;
                            }
                        }

                        if (!checkForHigherScore || (partAttempt.get("score").getAsDouble() < scoreValue)) {

                            partAttempt.addProperty("correct", correct);
                            partAttempt.addProperty("noResponse", false);
                            JsonArray feedbacks = new JsonArray();
                            partAttempt.add("feedbacks", feedbacks);
                            if (outcome.has("feedbacks")) {
                                JsonArray oFeedbacks = outcome.getAsJsonArray("feedbacks");
                                oFeedbacks.forEach(f -> {
                                    JsonObject feedback = new JsonObject();
                                    feedback.addProperty("id", AppUtils.generateUID(5));
                                    JsonObject body = f.getAsJsonObject().getAsJsonObject("body");
                                    feedback.add("body", AppUtils.createJsonElement(transformBodyContent(resource,
                                            body.get("material").getAsString(), serverUrl, themeId)));
                                    feedback.add("lang", body.get("lang"));

                                    feedbacks.add(feedback);
                                });
                            }
                            partAttempt.addProperty("feedbackVisible", true);

                            JsonObject score = new JsonObject();
                            partAttempt.add("score", score);
                            score.add("scoreOutOf", null);
                            score.add("value", null);
                            score.add("percent", null);
                            score.add("aggregateScore", null);
                            score.add("percentAggregate", null);
                            score.add("overrideScore", null);
                            score.add("percentOverride", null);
                            score.add("pointsEvaluated", null);
                            score.add("percentEvaluated", null);
                            score.add("assignedBy", null);
                            score.add("explanation", null);
                            score.add("dateScored", null);
                            score.addProperty("correct", correct);
                            score.addProperty("noResponse", false);

                            JsonElement explanation = p.getAsJsonObject().get("explanation");
                            if (explanation != null && !explanation.isJsonNull()) {
                                partAttempt.add("explanationBody",
                                        AppUtils.createJsonElement(transformBodyContent(resource,
                                                explanation.getAsJsonObject().get("material").getAsString(), serverUrl,
                                                themeId)));
                                partAttempt.add("feedbacks", null);
                                partAttempt.addProperty("feedbackVisible", false);
                            }
                        }

                    }
                });

            });
        });

        return questionAttempt;
    }

    private JsonObject lookupInteraction(String id, JsonArray interactions) {
        for (JsonElement i : interactions) {
            if (i.getAsJsonObject().get("id").getAsString().equals(id)) {
                return i.getAsJsonObject();
            }
        }
        return null;
    }

    private boolean evaluateCondition(JsonObject criteria, JsonObject interaction, JsonElement response) {
        String input = response.getAsJsonObject().get("input").getAsString();
        if (!criteria.get("interactionId").getAsString().equals(input)) {
            return false;
        }
        String inputValue = response.getAsJsonObject().get("value").getAsString();
        if (!criteria.has("match") || criteria.get("match").isJsonNull()) {
            return true;
        }
        String pattern = criteria.get("match").getAsString();

        MatcherByType matcherByType = new MatcherByType(interaction);
        try {
            if (inputValue != null && !inputValue.isEmpty()) {
                inputValue = URLDecoder.decode(inputValue, "UTF-8");
            }
        } catch (Exception ex) {
        }

        try {

            ResponseMatcher<Object> m = matcherByType.parseMatchPattern(pattern);
            // Parse input value
            Object value = matcherByType.parseInputValue(inputValue);

            // Does the input match the pattern?
            return m.matches(value);

        } catch (PatternFormatException e) {
            log.error(e.getLocalizedMessage(), e);
        } catch (InputFormatException e) {
            log.error(e.getLocalizedMessage(), e);
        }

        return false;
    }

    private String transformBodyContent(Resource resource, String content, String serverUrl, String themeId) {
        Element root = new Element("materials");
        Document d = new Document(root);
        String themeURL = serverUrl + "repository/presentation/" + themeId + "/";
        root.setAttribute("themeURL", themeURL);

        try {
            Element element = metadataController.fetchMetadataForResource(resource, serverUrl, themeId);
            root.addContent(element);
        } catch (JDOMException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {

            SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
            builder.setExpandEntities(true);
            content = AppUtils.escapeAmpersand(content);
            Document srcDoc = builder.build(new StringReader("<body unique_ref=\"m0\">" + content + "</body>"));
            root.addContent(srcDoc.getRootElement().detach());
        } catch (Exception ex) {
            log.error(ex.getLocalizedMessage() + "\n" + resource.getFileNode().getPathFrom() + "\n" + content, ex);
            throw new RuntimeException(ex.getLocalizedMessage());
        }

        String pathFrom = resource.getFileNode().getPathFrom();
        if (pathFrom.startsWith("content/")) {
            pathFrom = pathFrom.replaceFirst("content", "");
        }
        String rootPath = serverUrl + "webcontents/" + resource.getContentPackage().getGuid() + pathFrom;
        RelativePathRewriter r = new RelativePathRewriter(rootPath);
        r.convertToAbsolutePaths(d);

        // log.debug(new XMLOutputter(Format.getPrettyFormat()).outputString(d));

        Templates templates = null;
        try {
            templates = thinPreviewController.fetchXSLTTemplates(assessmentMaterialDocType, themeId);
        } catch (ContentServiceException e) {
            throw new RuntimeException(e);
        }

        // Source
        Source s = new JDOMSource(d);

        ByteArrayOutputStream byteOut = new ByteArrayOutputStream(32768);
        StreamResult result = new StreamResult(byteOut);

        // Transform the source document
        try {
            Transformer transformer = templates.newTransformer();

            transformer.transform(s, result);
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }

        String fromTransform = new String(byteOut.toByteArray(), StandardCharsets.UTF_8);
        // log.debug("From Transformation: " + fromTransform);

        String transformedContent;
        try {

            SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
            builder.setExpandEntities(true);
            Document srcDoc = builder.build(new StringReader(fromTransform));
            XPathExpression<Element> xexpression = xFactory.compile("//uid", Filters.element());
            List<Element> elementList = xexpression.evaluate(srcDoc);
            transformedContent = elementList.isEmpty() ? null
                    : new XMLOutputter().outputString(elementList.get(0).getContent());
        } catch (Exception ex) {
            log.error(ex.getLocalizedMessage(), ex);
            throw new RuntimeException(ex.getLocalizedMessage());
        }
        // log.debug("String transformed: " + transformedContent);
        return transformedContent;
    }

}
