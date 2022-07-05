package edu.cmu.oli.content.resource.builders;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class MoveObjrefToHeaderTest {

    String fileString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE workbook_page PUBLIC \"-//Carnegie Mellon University//DTD Workbook Page 3.7//EN\" \"http://oli.web.cmu.edu/dtd/oli_workbook_page_3_7.dtd\">\n" +
            "<?xml-stylesheet type=\"text/css\" href=\"http://oli.web.cmu.edu/authoring/oxy-author/oli_workbook_page_3_7.css\"?>\n" +
            "<workbook_page id=\"F1L1_2Comm1.1_p01\">\n" +
            "    <head>\n" +
            "        <title>Bonjour</title>\n" +
            "    </head>\n" +
            "    <body>\n" +
            "        <objref idref=\"RecogSpoken_COBJ\"/>\n" +
            "        <objref  idref=\"F1_00_GreetingsFormal_LOBJ\"/>\n" +
            "        <objref idref=\"F1L1S2_1_OBJ\"/>\n" +
            "        <p>page content</p>\n" +
            "    </body>\n" +
            "</workbook_page>\n";

    @Test
    public void moveObjrefToHeader() throws JDOMException, IOException {
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        builder.setExpandEntities(false);
        Document document = builder.build(new StringReader(fileString.trim()));
        String query = "/workbook_page/body/objref";
        XPathExpression<Element> xexpression = XPathFactory.instance().compile(query, Filters.element());
        List<Element> kids = xexpression.evaluate(document);
        if (!kids.isEmpty()) {
            Element header = document.getRootElement().getChild("head");
            for (Element kid : kids) {
                header.addContent(kid.detach());
            }
        }
        query = "/workbook_page/head/objref";
        xexpression = XPathFactory.instance().compile(query, Filters.element());
        assertEquals(xexpression.evaluate(document).size(), 3);
    }
}
