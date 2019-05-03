package edu.cmu.oli.content.resource.builders;

import edu.cmu.oli.assessment.builders.Assessment2Transform;
import edu.cmu.oli.content.AppUtils;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.Assert.assertEquals;

/**
 * @author Raphael Gachuhi
 */
public class Assessment2TransformTest {

    private Assessment2Transform cut;
    private String originalA2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?> <!DOCTYPE assessment PUBLIC \"-//Carnegie Mellon University//DTD Assessment 2.4//EN\" \"http://oli.cmu.edu/dtd/oli_assessment_2_4.dtd\"> <assessment xmlns:cmd=\"http://oli.web.cmu.edu/content/metadata/2.1/\" id=\"pre_quiz\" max_attempts=\"1\"> <title>Pre Quiz (Ungraded)</title> <fill_in_the_blank id=\"q2_prequiz\"> <body> <p>Using the table on Correlations Among the Social Behavior, Peer Experiences, and Self-Perception Measures</p> <p> This is a <input_ref input=\"i1\"/> , which means that there is a <input_ref input=\"i2\"/> between Aggression and Perceived Behavior-Conduct. A high score on Aggression tends to go with a <input_ref input=\"i3\"/> score on Perceived Behavior-Conduct; a low score on Aggression tends to go with a <input_ref input=\"i4\"/> on Perceived Perceived Behavior-Conduct. </p> </body> <input id=\"i1\" shuffle=\"false\"> <choice value=\"high_correlation\">high correlation</choice> <choice value=\"medium_correlation\">medium correlation</choice> <choice value=\"low_correlation\">low correlation</choice> <choice value=\"no_correlation\">no correlation</choice> </input> <input id=\"i2\" shuffle=\"false\"> <choice value=\"strong_relationship\">strong relationship</choice> <choice value=\"moderate_relationship\">moderate relationship</choice> <choice value=\"weak_relationship\">weak relationship</choice> <choice value=\"no_relationship\">no relationship</choice> </input> <input id=\"i3\" shuffle=\"false\"> <choice value=\"high\">high</choice> <choice value=\"low\">low</choice> </input> <input id=\"i4\" shuffle=\"false\"> <choice value=\"high\">high</choice> <choice value=\"low\">low</choice> </input> <part id=\"p1\"> <response input=\"i1\" match=\"high_correlation\" score=\"0\"> <feedback>Incorrect.</feedback> </response> <response input=\"i1\" match=\"medium_correlation\" score=\"10\"> <feedback>Correct.</feedback> </response> <response input=\"i1\" match=\"low_correlation\" score=\"0\"> <feedback>Incorrect.</feedback> </response> <response input=\"i1\" match=\"no_correlation\" score=\"0\"> <feedback>Incorrect.</feedback> </response> </part> <part id=\"p2\"> <response input=\"i2\" match=\"strong_relationship\" score=\"0\"> <feedback>Incorrect.</feedback> </response> <response input=\"i2\" match=\"moderate_relationship\" score=\"10\"> <feedback>Correct.</feedback> </response> <response input=\"i2\" match=\"weak_relationship\" score=\"0\"> <feedback>Incorrect.</feedback> </response> <response input=\"i2\" match=\"no_relationship\" score=\"0\"> <feedback>Incorrect.</feedback> </response> </part> <part id=\"p3\"> <response input=\"i3\" match=\"high\" score=\"0\"> <feedback>Incorrect.</feedback> </response> <response input=\"i3\" match=\"low\" score=\"10\"> <feedback>Correct.</feedback> </response> </part> <part id=\"p4\"> <response input=\"i4\" match=\"high\" score=\"10\"> <feedback>Correct.</feedback> </response> <response input=\"i4\" match=\"low\" score=\"0\"> <feedback>Incorrect.</feedback> </response> </part> </fill_in_the_blank> </assessment>";
    private String unifiedA2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?> <!DOCTYPE assessment PUBLIC \"-//Carnegie Mellon University//DTD Assessment 2.4//EN\" \"http://oli.cmu.edu/dtd/oli_assessment_2_4.dtd\"> <assessment xmlns:cmd=\"http://oli.web.cmu.edu/content/metadata/2.1/\" id=\"pre_quiz\" max_attempts=\"1\"> <title>Pre Quiz (Ungraded)</title> <question id=\"q2_prequiz\"> <body> <p>Using the table on Correlations Among the Social Behavior, Peer Experiences, and Self-Perception Measures</p> <p> This is a <input_ref input=\"i1\" /> , which means that there is a <input_ref input=\"i2\" /> between Aggression and Perceived Behavior-Conduct. A high score on Aggression tends to go with a <input_ref input=\"i3\" /> score on Perceived Behavior-Conduct; a low score on Aggression tends to go with a <input_ref input=\"i4\" /> on Perceived Perceived Behavior-Conduct. </p> </body> <fill_in_the_blank id=\"i1\" shuffle=\"false\"> <choice value=\"high_correlation\">high correlation</choice> <choice value=\"medium_correlation\">medium correlation</choice> <choice value=\"low_correlation\">low correlation</choice> <choice value=\"no_correlation\">no correlation</choice> </fill_in_the_blank> <fill_in_the_blank id=\"i2\" shuffle=\"false\"> <choice value=\"strong_relationship\">strong relationship</choice> <choice value=\"moderate_relationship\">moderate relationship</choice> <choice value=\"weak_relationship\">weak relationship</choice> <choice value=\"no_relationship\">no relationship</choice> </fill_in_the_blank> <fill_in_the_blank id=\"i3\" shuffle=\"false\"> <choice value=\"high\">high</choice> <choice value=\"low\">low</choice> </fill_in_the_blank> <fill_in_the_blank id=\"i4\" shuffle=\"false\"> <choice value=\"high\">high</choice> <choice value=\"low\">low</choice> </fill_in_the_blank> <part id=\"p1\"> <response input=\"i1\" match=\"high_correlation\" score=\"0\"> <feedback>Incorrect.</feedback> </response> <response input=\"i1\" match=\"medium_correlation\" score=\"10\"> <feedback>Correct.</feedback> </response> <response input=\"i1\" match=\"low_correlation\" score=\"0\"> <feedback>Incorrect.</feedback> </response> <response input=\"i1\" match=\"no_correlation\" score=\"0\"> <feedback>Incorrect.</feedback> </response> </part> <part id=\"p2\"> <response input=\"i2\" match=\"strong_relationship\" score=\"0\"> <feedback>Incorrect.</feedback> </response> <response input=\"i2\" match=\"moderate_relationship\" score=\"10\"> <feedback>Correct.</feedback> </response> <response input=\"i2\" match=\"weak_relationship\" score=\"0\"> <feedback>Incorrect.</feedback> </response> <response input=\"i2\" match=\"no_relationship\" score=\"0\"> <feedback>Incorrect.</feedback> </response> </part> <part id=\"p3\"> <response input=\"i3\" match=\"high\" score=\"0\"> <feedback>Incorrect.</feedback> </response> <response input=\"i3\" match=\"low\" score=\"10\"> <feedback>Correct.</feedback> </response> </part> <part id=\"p4\"> <response input=\"i4\" match=\"high\" score=\"10\"> <feedback>Correct.</feedback> </response> <response input=\"i4\" match=\"low\" score=\"0\"> <feedback>Incorrect.</feedback> </response> </part> </question> </assessment>";

    @Before
    public void setUp() throws Exception {
        cut = new Assessment2Transform();
    }

    @Test
    public void transformToUnified() throws JDOMException, IOException {
        StringReader st = new StringReader(originalA2);
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        Document document = builder.build(st);
        cut.transformToUnified(document.getRootElement());
        XMLOutputter xmlOut = new XMLOutputter(Format.getPrettyFormat());
        String toUnified = xmlOut.outputString(document);
        st = new StringReader(unifiedA2);
        document = builder.build(st);
        String unifield = xmlOut.outputString(document);
        assertEquals(unifield, toUnified);
    }

    @Test
    public void transformFromUnified() throws JDOMException, IOException {
        StringReader st = new StringReader(unifiedA2);
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        Document document = builder.build(st);
        cut.transformFromUnified(document.getRootElement());
        XMLOutputter xmlOut = new XMLOutputter(Format.getRawFormat());
        String fromUnified = xmlOut.outputString(document);
        st = new StringReader(originalA2);
        document = builder.build(st);
        String original = xmlOut.outputString(document);
        assertEquals(original, fromUnified);
    }
}