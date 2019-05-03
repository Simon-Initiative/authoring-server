package edu.cmu.oli.content.contentfiles.readers;

import org.junit.Test;
import org.jdom2.Document;

import edu.cmu.oli.content.contentfiles.utils.ResourceTestPipeline;
import edu.cmu.oli.assessment.builders.Assessment2Transform;

/**
 * Tests conversion of A2 documents to JSON, back to XML, ensuring that the
 * final XML is valid according to the DTD.
 */
public class A2ToJsonTest {

  @Test
  public void testConvertingResponses() throws Exception {

    final Assessment2Transform transformer = new Assessment2Transform();

    final ResourceTestPipeline pipeline = new ResourceTestPipeline(A2ToJsonTest.class.getResource("responses-pool.xml"),
        "x-oli-assessment2-pool") {

      public Document afterStringToDoc(final Document doc) {
        transformer.transformToUnified(doc.getRootElement());
        return doc;
      }

      public Document afterJsonToDoc(final Document doc) {
        transformer.transformFromUnified(doc.getRootElement());
        return doc;
      }
    };

    pipeline.execute();

  }

}