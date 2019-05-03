/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2019. Carnegie Mellon University
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package edu.cmu.oli.content.contentfiles.readers;

import edu.cmu.oli.content.contentfiles.utils.ResourceTestPipeline;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Tests preservation of character entities such as &#967;, &#968; etc when XML is converted to and from Json, ensuring that the
 * final XML is valid according to the DTD.
 */
public class PreserveCharacterEntitiesTest {

    @Test
    public void testConvertingResponses() throws Exception {

        final List<Boolean> errors = new ArrayList<>();

        final ResourceTestPipeline pipeline = new ResourceTestPipeline(PreserveCharacterEntitiesTest.class.getResource("sent_meta_completeness.xml"),
                "x-oli-workbook_page") {

            public String afterDocToString(final String str) {
                if (!str.contains("ψ") || !str.contains("Γ")) {
                    errors.add(new Boolean(true));
                }
                return str;
            }

        };
        pipeline.execute();

        assertTrue(errors.isEmpty());
    }
}