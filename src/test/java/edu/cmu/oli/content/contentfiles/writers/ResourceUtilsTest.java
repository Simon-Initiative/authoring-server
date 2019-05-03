package edu.cmu.oli.content.contentfiles.writers;

import edu.cmu.oli.content.AppUtils;
import org.jdom2.Document;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


public class ResourceUtilsTest {

    private final String PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\"?> " +
            "<!DOCTYPE assessment PUBLIC \"-//Carnegie Mellon University//DTD Assessment 2.4//EN\" \"http://oli.cmu.edu/dtd/oli_assessment_2_4.dtd\"> " +
            "<assessment xmlns:cmd=\"http://oli.web.cmu.edu/content/metadata/2.1/\" id=\"pre_quiz\" max_attempts=\"1\"> " +
            "<title>Pre Quiz (Ungraded)</title> <content> ";

    private final String SUFFIX = "</content></assessment>";

    /**
     * Helper function to facilitate unit tests.
     */
    private String adjust(final String content) {

        final String xml = PREFIX + content + SUFFIX;

        final StringReader st = new StringReader(xml);
        final SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        builder.setExpandEntities(false);
        try {
            final Document document = builder.build(st);

            ResourceUtils.adjustNestedBlocks(document);

            final XMLOutputter xmlOut = new XMLOutputter(Format.getRawFormat());
            return xmlOut.outputString(document);

        } catch (Exception e) {
            fail(e.toString());
        }

        return "";
    }

    private String expected(final String content) {

        final String xml = PREFIX + content + SUFFIX;

        final StringReader st = new StringReader(xml);
        final SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        builder.setExpandEntities(false);
        try {
            final Document document = builder.build(st);

            final XMLOutputter xmlOut = new XMLOutputter(Format.getRawFormat());
            return xmlOut.outputString(document);

        } catch (Exception e) {
            fail(e.toString());
        }

        return "";
    }

    @Test
    public void testInMiddle() {

        final String adjusted = adjust("<p>one</p><p>Here is some text. <ul><li>one</li></ul> And some more</p><p>two</p>");
        final String expected = expected("<p>one</p><p>Here is some text. </p><ul><li>one</li></ul><p> And some more</p><p>two</p>");

        assertEquals(expected, adjusted);
    }


    @Test
    public void testOnlyChild() {

        final String adjusted = adjust("<p>one</p><p><ul><li>one</li></ul></p><p>two</p>");
        final String expected = expected("<p>one</p><ul><li>one</li></ul><p>two</p>");

        assertEquals(expected, adjusted);
    }


    @Test
    public void testLastChild() {

        final String adjusted = adjust("<p>one</p><p><em>bold</em><ul><li>one</li></ul></p><p>two</p>");
        final String expected = expected("<p>one</p><p><em>bold</em></p><ul><li>one</li></ul><p>two</p>");

        assertEquals(expected, adjusted);
    }


    @Test
    public void testFirstChild() {

        final String adjusted = adjust("<p>one</p><p><ul><li>one</li></ul><em>bold</em></p><p>two</p>");
        final String expected = expected("<p>one</p><ul><li>one</li></ul><p><em>bold</em></p><p>two</p>");

        assertEquals(expected, adjusted);
    }


    @Test
    public void testSuccessive() {

        final String adjusted = adjust("<p>one</p><p><ul><li>one</li></ul><ul><li>two</li></ul><em>bold</em></p><p>two</p>");
        final String expected = expected("<p>one</p><ul><li>one</li></ul><ul><li>two</li></ul><p><em>bold</em></p><p>two</p>");

        assertEquals(expected, adjusted);
    }


    @Test
    public void testNestedParagraphs() {

        final String adjusted = adjust("<p>one</p><p><p>child</p></p><p>two</p>");
        final String expected = expected("<p>one</p><p>child</p><p>two</p>");

        assertEquals(expected, adjusted);
    }


    @Test
    public void testListWithinParagraphWithinListWithinParagraph() {

        final String adjusted = adjust("<p>one</p><p><ul><li><p><ul><li>list</li></ul></p></li></ul></p><p>two</p>");
        final String expected = expected("<p>one</p><ul><li><ul><li>list</li></ul></li></ul><p>two</p>");

        assertEquals(expected, adjusted);
    }

}