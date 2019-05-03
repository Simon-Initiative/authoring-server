package edu.cmu.oli.content.contentfiles.utils;

import com.google.gson.JsonElement;
import static org.junit.Assert.assertTrue;
import org.jdom2.Document;
import java.net.URL;

/**
 * Resource test pipeline that can be used to simulate a resource being
 * imported, edited by the front-end code, and converted back to XML.
 * 
 * Given a URL to an XML resource, this class will execute the following
 * operations:
 * 
 * <ol>
 * <li>Read the contents of the resource from disk into a string</li>
 * <li>Convert the string to an XML Document</li>
 * <li>Convert the XML Document to a JSONElement</li>
 * <li>Convert the JSONElement back to an XML Document</li>
 * <li>Convert the XML Document back to a string</li>
 * </ul>
 * 
 * Unit tests that use this class can hook into each of the above operations to
 * execute arbitrary test code. This allows the simulation of front-end edits
 * (by manipulating the JSON) and asserting the validity of XML to JSON
 * convertion and vice versa.
 * 
 * Out of the box, the only assertion that this pipeline provides is a final
 * assertion on the validity of the XML document after the pipleline executes.
 * For many tests, this is suffienct. For others, specific asserts will be
 * needed in the various lifecycle methods.
 * 
 */
public class ResourceTestPipeline {

  private final URL resourceUrl;
  private final String resourceType;

  public ResourceTestPipeline(final URL resourceUrl, final String resourceType) {
    this.resourceUrl = resourceUrl;
    this.resourceType = resourceType;
  }

  public void execute() throws Exception {
    final String xml = this.afterReadXml(TestUtils.readFromResourceURL(this.resourceUrl));
    final Document doc = this.afterStringToDoc(TestUtils.stringToXmlDocument(xml, this.resourceType));
    final JsonElement json = this.afterToJson(TestUtils.docToJson(doc));
    final Document updatedDoc = this.afterJsonToDoc(TestUtils.jsonToDoc(json, this.resourceType));
    final String updatedStr = this.afterDocToString(TestUtils.docToString(updatedDoc));

    // Now do the validation and fail on any errors
    assertTrue(TestUtils.isValidXml(updatedStr, this.resourceType));
  }

  // The lifecycle methods. Override any of these to customize your unit test.

  /**
   * Called after the XML file is read from disk, but before it is converted to an
   * XML Document. Override this to make string-based textual changes in the XML
   * 
   * @param str the XML
   * @return the updated string
   */
  public String afterReadXml(final String str) {
    return str;
  }

  /**
   * Called after the XML string contents has been converted to an XML Document.
   * Override this method if you want to make DOM based mutations to the document.
   * A common use case of overriding this method is to apply the assessment model
   * conversion to unified model.
   */
  public Document afterStringToDoc(final Document doc) {
    return doc;
  }

  /**
   * Called after the XML document has been converted to a JSONElement. Override
   * this method to simulate front-end mutations, whether by directly modifying
   * the JSON object hierarchy, or by first converting to and operating on
   * strings, then converting back to JSONElement.
   */
  public JsonElement afterToJson(final JsonElement e) {
    return e;
  }

  /**
   * Called after the JSONElement has been converted back to an XML Document.
   * Override this method to verify changes made to JSON appear correctly in the
   * XML Dom.
   */
  public Document afterJsonToDoc(final Document doc) {
    return doc;
  }

  /**
   * Called after the XML Document has been converted back to a string. Override
   * this method to do string-based verification and assertion.
   */
  public String afterDocToString(final String str) {
    return str;
  }

}