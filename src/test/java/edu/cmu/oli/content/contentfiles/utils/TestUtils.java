package edu.cmu.oli.content.contentfiles.utils;

import java.util.Iterator;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.configuration.Configurator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import edu.cmu.oli.content.contentfiles.writers.ResourceToXml;
import edu.cmu.oli.content.contentfiles.writers.ResourceToXmlTest;
import edu.cmu.oli.content.resource.validators.ResourceValidator;
import edu.cmu.oli.content.resource.validators.BaseResourceValidator;
import edu.cmu.oli.content.models.persistance.entities.Resource;
import edu.cmu.oli.content.AppUtils;
import org.jdom2.Document;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import java.net.URL;
import edu.cmu.oli.content.resource.builders.Xml2Json;
import java.io.StringReader;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import edu.cmu.oli.content.models.persistance.entities.FileNode;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;

/**
 * Unit test utility functions.
 */
public final class TestUtils {

  private TestUtils() {
  }

  /**
   * Load a configuration.
   */
  public static Configurations loadConfiguration() throws IOException {
    Path path = Paths.get(ResourceToXmlTest.class.getResource("config.json").getFile());

    JsonElement jsonElement = new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
    JsonObject jsonObject = jsonElement.getAsJsonObject();
    return Configurator.createConfiguration(jsonObject);
  }

  /**
   * XML Document to JSON converter.
   */
  public static JsonElement docToJson(final Document document) throws Exception {

    Xml2Json xml2Json = new Xml2Json();
    return xml2Json.toJson(document.getRootElement(), false);
  }

  public static String jsonToString(final JsonElement element) throws Exception {
    return element.toString();
  }

  /**
   * Convert from JSON to XML.
   */
  public static Document jsonToDoc(final JsonElement e, final String resourceType) throws Exception {

    final ResourceToXml converter = new ResourceToXml();
    final Configurations config = loadConfiguration();

    converter.setConfig(config);
    final String xml = converter.resourceToXml(resourceType, e.toString());

    final StringReader st = new StringReader(xml);
    final SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
    builder.setExpandEntities(false);
    return builder.build(st);
  }

  /**
   * Determines whether a resource has errors that should fail a unit test.
   */
  private static boolean hasErrors(final Resource resource) {

    if (resource.getErrors() == null) {
      return false;
    }
    JsonObject asJsonObject = resource.getErrors().getJsonObject().getAsJsonObject();
    JsonArray errorList = asJsonObject.getAsJsonArray("errorList");
    boolean hasError = false;

    Iterator<JsonElement> it = errorList.iterator();
    while (it.hasNext()) {
      JsonElement el = it.next();
      final String level = el.getAsJsonObject().get("level").getAsString();
      if (level.equals("ERROR") || level.equals("FATAL")) {
        hasError = true;
        break;
      }
    }

    return hasError;
  }

  /**
   * We need a resource to do validation, so create a mock that has just enough
   * attributes populated.
   */
  private static Resource createMockResource(final String resourceType) {

    Resource resource = new Resource();
    resource.setType(resourceType);
    resource.setId("fakeId");
    FileNode node = new FileNode();
    node.setPathFrom(("fakePath"));
    resource.setFileNode(node);
    ContentPackage pack = new ContentPackage();
    pack.setSourceLocation("/some/location");

    resource.setContentPackage(pack);

    return resource;
  }

  /**
   * Run the validation process on the xml content.
   * 
   * @param xmlContent   the string content
   * @param resourceType the string resource type
   * @param resource     our mock resource used to capture errors
   * @throws Exception
   */
  private static Document validateXml(final String xmlContent, final String resourceType, final Resource resource)
      throws Exception {

    // Validate xmlContent
    SAXBuilder builder = AppUtils.validatingSaxBuilder();
    builder.setExpandEntities(false);

    Document doc = builder.build(new StringReader(xmlContent));

    ResourceValidator validator = new BaseResourceValidator();
    validator.initValidator(resource, doc, true);
    validator.validate();

    ResourceValidator secondaryValidator = null;
    if (resourceType == "x-oli-assessment2-pool" || resourceType == "x-oli-assessment2") {
      secondaryValidator = new edu.cmu.oli.assessment.validators.AssessmentV1Validator();
    }
    if (resourceType == "x-oli-inline-assessment") {
      secondaryValidator = new edu.cmu.oli.assessment.validators.AssessmentV2Validator();
    }

    if (secondaryValidator != null) {
      secondaryValidator.initValidator(resource, doc, true);
    }

    return doc;
  }

  /**
   * Determines whether this XML represents a valid OLI document. Does more than
   * simply validate the XML against the DTD.
   */
  public static boolean isValidXml(final String xmlContent, final String resourceType) throws Exception {

    final Resource resource = createMockResource(resourceType);
    validateXml(xmlContent, resourceType, resource);
    return !hasErrors(resource);

  }

  /**
   * Read contents of a file given a URL that points to that file.
   */
  public static String readFromResourceURL(final URL fileUrl) throws Exception {
    final Path path = Paths.get(fileUrl.getFile());
    return new String(Files.readAllBytes(path));
  }

  /**
   * Read XML input file and pass it thru the A2 transformer to generate xml
   * string.
   */
  public static Document stringToXmlDocument(final String xml, final String resourceType) throws Exception {
    final Resource resource = createMockResource(resourceType);
    return validateXml(xml, resourceType, resource);
  }

  /**
   * Convert XML document to string.
   */
  public static String docToString(final Document document) throws Exception {

    final XMLOutputter xmlOut = new XMLOutputter(Format.getRawFormat());
    return xmlOut.outputString(document);
  }

}