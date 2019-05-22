package edu.cmu.oli.content.contentfiles.writers;

import org.jdom2.JDOMException;
import org.junit.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.configuration.Configurator;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Tests for resource conversion to XML.
 */
public class ResourceToXmlTest {

  private Configurations loadConfiguration() throws IOException {
    Path path = Paths.get(ResourceToXmlTest.class.getResource("config.json").getFile());

    JsonElement jsonElement = new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
    JsonObject jsonObject = jsonElement.getAsJsonObject();
    return Configurator.createConfiguration(jsonObject);
  }

  @Test
  public void testLessThanEqualTo() throws JDOMException, IOException {

    Path path = Paths.get(ResourceToXmlTest.class.getResource("./test.json").getFile());
    String json = new String(Files.readAllBytes(path));

    final ResourceToXml toXml = new ResourceToXml();
    final Configurations config = loadConfiguration();

    toXml.setConfig(config);
    final String xml = toXml.resourceToXml("x-oli-inline-assessment", json);

    // Make sure that the the < in the JSON escapes correctly to &lt;
    assertTrue(xml.indexOf("match=\"&lt;") > -1);
  }

  /**
   * This test ensures that a JSON workbook page that specifies a video whose mime
   * 'type' attribute isn't 'video/mpeg' is preserved.
   */
  @Test
  public void testPreservationOfVideoType() throws JDOMException, IOException {

    Path path = Paths.get(ResourceToXmlTest.class.getResource("./video-workbook.json").getFile());
    String json = new String(Files.readAllBytes(path));

    final ResourceToXml toXml = new ResourceToXml();
    final Configurations config = loadConfiguration();

    toXml.setConfig(config);
    final String xml = toXml.resourceToXml("x-oli-workbook_page", json);

    // Make sure that there are no referenes to video/mpeg
    assertTrue(xml.indexOf("type=\"video/mpeg") == -1);
  }

  /**
   * This test ensures that a JSON workbook page that specifies an audio element
   * whose mime 'type' attribute is non-standard is preserved.
   */
  @Test
  public void testPreservationOfAudioType() throws JDOMException, IOException {

    Path path = Paths.get(ResourceToXmlTest.class.getResource("./audio-workbook.json").getFile());
    String json = new String(Files.readAllBytes(path));

    final ResourceToXml toXml = new ResourceToXml();
    final Configurations config = loadConfiguration();

    toXml.setConfig(config);
    final String xml = toXml.resourceToXml("x-oli-workbook_page", json);

    // Make sure that there are no referenes to audio/mpeg
    assertTrue(xml.indexOf("type=\"audio/mpeg") == -1);
  }

  /**
   * This test ensures that responses are correctly aligned to their inputs
   * when a multipart question is transformed from JSON to XML.
   */
  @Test
  public void testPreservationOfResponseInputsOrder() throws JDOMException, IOException {

    Path path = Paths.get(ResourceToXmlTest.class.getResource("./multi-part-assessment.json").getFile());
    String json = new String(Files.readAllBytes(path));

    final ResourceToXml toXml = new ResourceToXml();
    final Configurations config = loadConfiguration();

    toXml.setConfig(config);
    String xml = toXml.resourceToXml("x-oli-inline-assessment", json);
    xml = xml.replaceAll("\n", "");
//    System.out.println(xml);

    // Verify correct alignment between match and input
    assertTrue(xml.indexOf("match=\"sm_under\" score=\"1\" input=\"s5\"") > 1);
  }

}