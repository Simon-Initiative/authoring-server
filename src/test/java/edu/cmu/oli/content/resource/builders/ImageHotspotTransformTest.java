package edu.cmu.oli.content.resource.builders;

import edu.cmu.oli.assessment.builders.Assessment2Transform;
import edu.cmu.oli.content.contentfiles.utils.ResourceTestPipeline;
import org.jdom2.Document;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * @author Raphael Gachuhi
 */
public class ImageHotspotTransformTest {

    private Assessment2Transform cut;

    @Before
    public void setUp() {
        cut = new Assessment2Transform();
    }

    @Test
    public void transformToUnified() throws Exception {
        final List<Boolean> errors = new ArrayList<>();
        final ResourceTestPipeline pipeline = new ResourceTestPipeline(
                ImageHotspotTransformTest.class.getResource("test-resources/_m4_assess.xml"),
                "x-oli-assessment2") {

            @Override
            public Document afterStringToDoc(final Document doc) {
                cut.transformToUnified(doc.getRootElement());
                return super.afterStringToDoc(doc);
            }

            @Override
            public Document afterJsonToDoc(Document doc) {
                cut.transformFromUnified(doc.getRootElement());
                return super.afterJsonToDoc(doc);
            }
        };

        pipeline.execute();

        assertTrue(errors.isEmpty());
    }
}