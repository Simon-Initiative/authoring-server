package edu.cmu.oli.content.contentfiles.writers;

import org.junit.Test;

import org.jdom2.Document;
import java.util.List;
import java.util.ArrayList;
import edu.cmu.oli.content.contentfiles.utils.ResourceTestPipeline;
import edu.cmu.oli.assessment.builders.Assessment2Transform;

import static org.junit.Assert.assertTrue;

/**
 * Tests the prefixing of ordinal-only choice values and their corresponding
 * response or match element attributes.
 */
public class ChoicePrefixingTest {

    @Test
    public void testPrefixing() throws Exception {

        final Assessment2Transform transformer = new Assessment2Transform();
        final List<Boolean> errors = new ArrayList<>();
        final ResourceTestPipeline pipeline = new ResourceTestPipeline(
                ChoicePrefixingTest.class.getResource("response_mult.xml"), "x-oli-assessment2") {

            public Document afterStringToDoc(final Document doc) {
                transformer.transformToUnified(doc.getRootElement());
                return doc;
            }

            public Document afterJsonToDoc(final Document doc) {
                transformer.transformFromUnified(doc.getRootElement());
                return doc;
            }

            public String afterDocToString(final String str) {
                // There should not be any match attributes of match elements
                // that contain "0" as they all should have been updated to "v0"
                if (str.indexOf("match=\"0\"") >= 0) {
                    errors.add(true);
                }
                return str;
            }
        };

        pipeline.execute();
        assertTrue(errors.isEmpty());
    }

}