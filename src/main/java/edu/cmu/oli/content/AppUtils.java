package edu.cmu.oli.content;

import com.google.gson.*;
import edu.cmu.oli.JsonWrapperSerializer;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import edu.cmu.oli.content.models.persistance.entities.ErrorLevel;
import edu.cmu.oli.content.models.persistance.entities.Resource;
import org.apache.tika.Tika;
import org.apache.xml.resolver.tools.CatalogResolver;
import org.jdom2.Attribute;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.json.Json;
import javax.json.JsonValue;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import edu.cmu.oli.content.resource.builders.Xml2Json;

/**
 * @author Raphael Gachuhi
 */
public class AppUtils {

    static final Logger log = LoggerFactory.getLogger(AppUtils.class);

    static final Gson gson = new Gson();

    public static String toString(javax.json.JsonValue jsonValue) {
        StringWriter stWriter = new StringWriter();
        try (javax.json.JsonWriter jsonWriter = Json.createWriter(stWriter)) {
            if (jsonValue.getValueType().equals(JsonValue.ValueType.ARRAY)) {
                jsonWriter.writeArray((javax.json.JsonArray) jsonValue);
            } else {
                jsonWriter.writeObject((javax.json.JsonObject) jsonValue);
            }
        }
        return stWriter.toString();
    }

    public static <T> T toClassObject(javax.json.JsonValue jsonValue, Class<T> classOfT) {
        return gson.fromJson(toString(jsonValue), classOfT);
    }

    public static String escapeAmpersand(String data) {
        String[] split = data.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < split.length; x++) {
            String str = split[x];
            str = str.replaceAll("&(?!.{2,4};)", "&amp;");
            sb.append(str);
            if ((x + 1) < split.length) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public static JsonElement createJsonElement(String value) {
        return (JsonElement) (value == null ? JsonNull.INSTANCE : new JsonPrimitive(value));
    }

    public static JsonElement cloneJson(JsonElement json) {
        Gson gson = AppUtils.gsonBuilder().serializeNulls().create();
        return new JsonParser().parse(gson.toJson(json));
    }

    public static String inputStreamToString(InputStream input) throws IOException {
        return new BufferedReader(new InputStreamReader(input)).lines().collect(Collectors.joining("\n"));
    }

    public static void addToPackageError(ContentPackage contentPackage, String message, String source,
            ErrorLevel level) {
        if (contentPackage.getErrors() == null) {
            JsonObject errors = new JsonObject();
            errors.addProperty("contentPackageErrors", contentPackage.getId() + "_" + contentPackage.getVersion());
            JsonArray errorList = new JsonArray();
            errors.add("errorList", errorList);
            contentPackage.setErrors(new JsonWrapper(errors));
        }
        JsonElement jsonElement = contentPackage.getErrors().getJsonObject();
        JsonObject asJsonObject = jsonElement.getAsJsonObject();
        JsonArray errorList = asJsonObject.getAsJsonArray("errorList");
        JsonObject err = new com.google.gson.JsonObject();
        err.addProperty("source", source);
        err.addProperty("level", level.toString());
        err.addProperty("message", message);
        errorList.add(err);
    }

    public static void addToResourceError(Resource resource, String message, String source, ErrorLevel level) {
        if (resource.getErrors() == null) {
            JsonObject errors = new JsonObject();
            errors.addProperty("resourceErrors", resource.getId() + " Title: " + resource.getTitle());
            JsonArray errorList = new JsonArray();
            errors.add("errorList", errorList);
            resource.setErrors(new JsonWrapper(errors));
        }
        JsonObject asJsonObject = resource.getErrors().getJsonObject().getAsJsonObject();
        JsonArray errorList = asJsonObject.getAsJsonArray("errorList");
        JsonObject err = new com.google.gson.JsonObject();
        err.addProperty("source", source);
        err.addProperty("level", level.toString());
        err.addProperty("message", message);
        errorList.add(err);
    }

    private static boolean isQuestionWithParts(Element element) {
        if (element.getName().equals("multiple_choice")
            || element.getName().equals("ordering")
            || element.getName().equals("short_answer")
            || element.getName().equals("essay")
            || element.getName().equals("numeric")
            || element.getName().equals("text")
            || element.getName().equals("fill_in_the_blank")
            || element.getName().equals("question")) {
            return true;
        }

        return false;
    }

    private static boolean containsDynaDropCustom(JsonObject body) {
        if (body.get("#array") != null && body.get("#array").isJsonArray()) {
            for (JsonElement el : body.get("#array").getAsJsonArray()) {
                if (el.isJsonObject() && el.getAsJsonObject().has("custom")) {
                    return true;
                }
            }
        }

        return false;
    }

    private static String getQuestionLabel(Element question) {
        // get body element, count items, get contentType and select type
        JsonObject body = null;
        for (Element el : question.getChildren()) {
            if (el.getName().equals("body")) {
                body = new Xml2Json().toJson(el, false);
            }
        }

        if (body != null && containsDynaDropCustom(body)) {
            return "Drag and Drop";
        }

        switch (question.getName()) {
            case "multiple_choice":
            if (question.getAttribute("select") != null
                && question.getAttribute("select").getValue().equals("single")) {
                    return "Multiple Choice";
                }
                return "Check All That Apply";
            case "ordering":
                return "Ordering";
            case "essay":
                return "Essay";
            case "ShortAnswer":
                return "Short Answer";
            case "text":
            case "numeric":
            case "fill_in_the_blank":
                return "Input";
            case "image_hotspot":
                return "Image Hotspot";
            default:
                return "Question";
        }
    }

    private static void addQuestionMetadata(Element question, JsonObject p) {
        if (!isQuestionWithParts(question))
            throw new Error("AppUtils.getQuestionParts: first argument must be a question element with parts");

        Gson gson = new Gson();
        ArrayList<JsonObject> parts = new ArrayList<JsonObject>();
        Optional<JsonObject> maybeBody = Optional.empty();
        for (Element el : question.getChildren()) {
            if (el.getName() == "body") {
                maybeBody = Optional.of(new Xml2Json().toJson(el, false));
            }
            if (el.getName() == "part") {
                parts.add(new Xml2Json().toJson(el, false));
            }
        }

        maybeBody.ifPresent(body -> {
            p.add("body", body.get("body"));
        });
        p.add("parts", gson.toJsonTree(parts));
        p.addProperty("label", getQuestionLabel(question));
    }

    public static int countPoolQuestions(Element element) {
        int count = 0;
        if (element.getName().equals("pool")) {
            for (Element c : element.getChildren()) {
                switch (c.getName()) {
                case "multiple_choice":
                case "ordering":
                case "short_answer":
                case "essay":
                case "numeric":
                case "text":
                case "fill_in_the_blank":
                case "question":
                    count = count + 1;
                default:
                }
            }
        }

        return count;
    }

    public static void pathToRoot(Element element, JsonObject p) {
        pathToRoot(element, p, Optional.empty());
    }

    public static void pathToRoot(Element element, JsonObject p, Optional<JsonObject> original) {
        p.addProperty("name", element.getName());
        List<Attribute> attributes = element.getAttributes();
        attributes.forEach(a -> {
            p.addProperty("@" + a.getName(), a.getValue());
        });

        // add question count metadata if the element is a pool
        if (element.getName().equals("pool")) {
            p.addProperty("questionCount", countPoolQuestions(element));
        }

        if (isQuestionWithParts(element)) {
            addQuestionMetadata(element, p);
        }

        if (original.isPresent()) {
            if (!p.has("title")) {
                for (Element c : element.getChildren()) {
                    String title = null;
                    if (c.getName().equals("title")) {
                        title = c.getValue();
                    }
                    if (c.getName().equals("body")) {
                        title = c.getValue();
                    }
                    if (title != null && !title.trim().isEmpty()) {
                        p.addProperty("title", title);
                        break;
                    }
                }
            }
        }

        if (element.getParentElement() != null) {
            JsonObject parent = new JsonObject();
            p.add("parent", parent);
            pathToRoot(element.getParentElement(), parent, original);
        }
    }

    public static String getFileType(Path filePath, String contentType) {
        Tika tika = new Tika();
        try {
            contentType = tika.detect(filePath);
        } catch (IOException e) {
            log.debug("Error getting file size " + filePath);
        }
        return contentType;
    }

    public static long getSize(Path filePath, long size) {
        try {
            size = Files.size(filePath);
        } catch (IOException e) {
            log.debug("Error getting file size " + filePath);
        }
        return size;
    }

    public static String generateUID(int length) {
        String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toLowerCase();
        Random r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        while (length > 0) {
            length--;
            sb.append(ALPHABET.charAt(r.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public static String generateGUID() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    public static BeanManager getBeanManager() {
        try {
            return (BeanManager) new InitialContext().lookup("java:comp/BeanManager");
        } catch (NamingException e) {
            log.error("Couldn't get BeanManager through JNDI");
            return null;
        }
    }

    public static <T> T lookupCDIBean(Class<T> type) {
        BeanManager bm = getBeanManager();
        log.info("Type Information " + type.toString());
        Bean<T> bean = (Bean<T>) bm.getBeans(type).iterator().next();
        CreationalContext<T> ctx = bm.createCreationalContext(bean);
        T dao = (T) bm.getReference(bean, type, ctx);
        return dao;
    }

    public static String hashDigest(String s) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        digest.update(s.getBytes());
        return AppUtils.convertByteArrayToHexString(digest.digest());
    }

    public static String convertByteArrayToHexString(byte[] arrayBytes) {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < arrayBytes.length; i++) {
            stringBuffer.append(Integer.toString((arrayBytes[i] & 0xff) + 0x100, 16).substring(1));
        }
        return stringBuffer.toString();
    }

    public static SAXBuilder validatingSaxBuilder() {
        SAXBuilder builder = new SAXBuilder(XMLReaders.DTDVALIDATING);
        builder.setExpandEntities(false);
        CatalogResolver cr = new CatalogResolver();
        builder.setEntityResolver(cr);
        return builder;
    }

    public static GsonBuilder gsonBuilder() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(JsonWrapper.class, new JsonWrapperSerializer());
        return gsonBuilder;
    }
}