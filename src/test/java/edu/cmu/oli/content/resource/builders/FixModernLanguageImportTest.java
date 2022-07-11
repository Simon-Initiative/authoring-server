package edu.cmu.oli.content.resource.builders;

import edu.cmu.oli.assessment.builders.Assessment2Transform;
import org.jdom2.*;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class FixModernLanguageImportTest {

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

    String a2ToIn = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE assessment PUBLIC \"-//Carnegie Mellon University//DTD Assessment MathML 2.4//EN\" \"http://oli.web.cmu.edu/dtd/oli_assessment_mathml_2_4.dtd\">\n" +
            "<assessment xmlns:cmd=\"http://oli.web.cmu.edu/content/metadata/2.1/\"\n" +
            "    id=\"all_a2aaf4985d67bb40a6834d60f8baae118e\" recommended_attempts=\"3\" max_attempts=\"3\">\n" +
            "    <title>all_a2</title>\n" +
            "    <introduction><p>Some introductory content</p> <p>More introductory content</p></introduction>\n" +
            "    <page id=\"fc7875e688b842ef9e362313459e68a2\">\n" +
            "        <title>Page 1</title>\n" +
            "        <content available=\"always\">\n" +
            "            <p id=\"a303c2e512944e88a9dee6c9774c83e5\"><em>THIS IS EXAMPLE SUPPORTING CONTENT. PLEASE\n" +
            "                    EDIT OR DELETE IT.</em></p>\n" +
            "            <p id=\"c0af937f499a42a18cdcd14c9545e028\">Review the Policy Statement, Privileges and\n" +
            "                Responsibilities and Misuse and Inappropriate Behavior sections of the Computing\n" +
            "                Policy, then answer the following questions. df</p>\n" +
            "        </content>\n" +
            "        <multiple_choice id=\"all_a2aaf4985d67bb40a6834d60f8baae118e_1a\" grading=\"instructor\"\n" +
            "            select=\"single\">\n" +
            "            <body><p id=\"e39402749ce74e0ab56e6c7d26ebbaed\"><em>THIS IS AN EXAMPLE MULTIPLE CHOICE\n" +
            "                        QUESTION. PLEASE EDIT OR DELETE IT.</em></p><p\n" +
            "                    id=\"b6e9b7bc6c5143b39f2b5080b9e42531\">Albert sees that his girlfriend has\n" +
            "                    written her password on a note beside her computer; he logs in and sends a joke\n" +
            "                    email to one of her friends. This action is: </p></body>\n" +
            "            <input shuffle=\"true\" id=\"ans\" labels=\"false\">\n" +
            "                <choice value=\"yes\">Acceptable</choice>\n" +
            "                <choice value=\"no\">Unacceptable</choice>\n" +
            "            </input>\n" +
            "            <part id=\"bd00f09fec444d28abad746ff061155e\">\n" +
            "                <response match=\"yes\" score=\"0\">\n" +
            "                    <feedback><p id=\"dea3aed2e8994f80ad82388dde019d9a\">Incorrect; using another\n" +
            "                            student&apos;s password is not acceptable, even if it&apos;s left out in\n" +
            "                            the open. Further, Albert has assumed his girlfriend&apos;s identity by\n" +
            "                            using her account, which is also a violation of the Computing\n" +
            "                            Policy.</p></feedback>\n" +
            "                </response>\n" +
            "                <response match=\"no\" score=\"1\">\n" +
            "                    <feedback><p id=\"e412f97c52014436a5fade4ea61e9f3b\">Correct; this is a pretty\n" +
            "                            clear violation of the policy, including using another person&apos;s\n" +
            "                            account and impersonating another individual.</p></feedback>\n" +
            "                </response>\n" +
            "            </part>\n" +
            "        </multiple_choice>\n" +
            "        <multiple_choice id=\"b60f8c1fcee746b78bf2acacfb244676\" grading=\"automatic\" select=\"multiple\">\n" +
            "            <body><p id=\"c4fe60dfa9a84f7482d8d0d5ef612e21\">sdasfasadf</p></body>\n" +
            "            <input shuffle=\"true\" id=\"ce504dcbe34b44608adc3dbe86a93a2a\" labels=\"false\">\n" +
            "                <choice value=\"A\">sdd</choice>\n" +
            "                <choice value=\"B\">ghs</choice>\n" +
            "            </input>\n" +
            "            <part id=\"b8ae9eb564384168872c25f3ec01c74c\">\n" +
            "                <response match=\"A\" score=\"1\">\n" +
            "                    <feedback><p id=\"a5cf8ec05c05416c966642a9cf4c0c7e\">correct</p></feedback>\n" +
            "                </response>\n" +
            "                <response match=\"A,B\" name=\"AUTOGEN_{A,B}\" score=\"0\">\n" +
            "                    <feedback><p id=\"fc790dbb6811437b8c76e15a977255c9\">incorrect</p></feedback>\n" +
            "                </response>\n" +
            "                <response match=\"B\" name=\"AUTOGEN_{B}\" score=\"0\">\n" +
            "                    <feedback><p id=\"c3b14a47996c450abdf5c9da5426ffc4\">incorrect</p></feedback>\n" +
            "                </response>\n" +
            "            </part>\n" +
            "        </multiple_choice>\n" +
            "        <ordering id=\"dd619a046d7946c09b62238c754efbc4\" grading=\"automatic\">\n" +
            "            <body><p id=\"a49dce0053de4c4bb8088063bc9d37ab\">agafasf</p></body>\n" +
            "            <input shuffle=\"true\" id=\"cd28aa175b5a42139c919212d555355f\">\n" +
            "                <choice value=\"A\">choice a</choice>\n" +
            "                <choice value=\"B\">choice b</choice>\n" +
            "            </input>\n" +
            "            <part id=\"eadda1a7e6d54d17971343d9107d6e8b\">\n" +
            "                <response match=\"A,B\" score=\"1\">\n" +
            "                    <feedback><p id=\"e8e313dd534d44cd901ac71f904d6f1e\">correct</p></feedback>\n" +
            "                </response>\n" +
            "                <response match=\"B,A\" name=\"AUTOGEN_{B,A}\" score=\"0\">\n" +
            "                    <feedback><p id=\"a22704e589384a6a88086143a1267f9c\">incorrect</p></feedback>\n" +
            "                </response>\n" +
            "            </part>\n" +
            "        </ordering>\n" +
            "        <short_answer id=\"eddf00b15ac7494686d11761c759ff4e\" grading=\"automatic\">\n" +
            "            <body><p id=\"a3b8718e724d483e89cfd5b828ca6b8b\">those short</p></body>\n" +
            "            <part id=\"d577474e5ff145dbb73377902139d96c\">\n" +
            "                <response match=\"*\" score=\"1\">\n" +
            "                    <feedback><p id=\"e04f2be4cae24b458d4173774dd19753\">the expert\n" +
            "                        answer</p></feedback>\n" +
            "                </response>\n" +
            "            </part>\n" +
            "        </short_answer>\n" +
            "        <essay id=\"ae493f463b7e4ba3913b156af0c01f69\" grading=\"automatic\">\n" +
            "            <body><p id=\"a9b9d87cb7e04279b3c6a89a2008abd0\">the essay</p></body>\n" +
            "            <part id=\"f0890d1ea63d4f96bc15c8ff97a6c25f\">\n" +
            "                <response match=\"*\" score=\"1\">\n" +
            "                    <feedback><p id=\"aa3d7765cdb749a28640b52b0fece1af\">the expert\n" +
            "                        answer</p></feedback>\n" +
            "                </response>\n" +
            "            </part>\n" +
            "        </essay>\n" +
            "        <numeric id=\"b02ce38326d346f6a1a4baac56c8cc06\" grading=\"automatic\">\n" +
            "            <body><p id=\"e2d83a7bd41643669084baf971734edf\">Add numeric, <input_ref\n" +
            "                        input=\"ed7a7c9f631c44be89b2ebb186714c64\"/>text, or dropdown <input_ref\n" +
            "                        input=\"d587601dcaa34fbabb46900271331d8f\"/>components</p></body>\n" +
            "            <input id=\"ed7a7c9f631c44be89b2ebb186714c64\" size=\"small\"/>\n" +
            "            <input id=\"d587601dcaa34fbabb46900271331d8f\" size=\"small\"/>\n" +
            "            <part id=\"cd86aa3498784125b4bae4b48c2d728c\">\n" +
            "                <response match=\"3\" score=\"1\" input=\"ed7a7c9f631c44be89b2ebb186714c64\">\n" +
            "                    <feedback><p id=\"d925c0d2f7b94047a58ca236b54ebd2e\">Correct!</p></feedback>\n" +
            "                </response>\n" +
            "                <response match=\"*\" score=\"0\" input=\"ed7a7c9f631c44be89b2ebb186714c64\">\n" +
            "                    <feedback><p id=\"cbae4a33129e4df1b5b4c1260b9d3821\">Incorrect.</p></feedback>\n" +
            "                </response>\n" +
            "            </part>\n" +
            "            <part id=\"f1d59525bf4746098b2290825daa8c7e\">\n" +
            "                <response match=\"6\" score=\"1\" input=\"d587601dcaa34fbabb46900271331d8f\">\n" +
            "                    <feedback><p id=\"e9d13f07fc3344ab982fbf4ae4a43dfa\">Correct!</p></feedback>\n" +
            "                </response>\n" +
            "                <response match=\"*\" score=\"0\" input=\"d587601dcaa34fbabb46900271331d8f\">\n" +
            "                    <feedback><p id=\"d0b1a1c9621647738223112886b87b30\">Incorrect.</p></feedback>\n" +
            "                </response>\n" +
            "            </part>\n" +
            "        </numeric>\n" +
            "        <text id=\"bb7e1b61d2e74fe09b4b85bec343f7a4\" grading=\"automatic\" whitespace=\"trim\"\n" +
            "            case_sensitive=\"false\" evaluation=\"regex\" keyboard=\"none\">\n" +
            "            <body><p id=\"b1c8ff8d281943839352bd2216b090e2\">Add numeric, text, or<input_ref\n" +
            "                        input=\"e01b13b829c64adab6ff3cd988714832\"/> dropdown components</p></body>\n" +
            "            <input id=\"e01b13b829c64adab6ff3cd988714832\" size=\"small\"/>\n" +
            "            <part id=\"c2cc715adf0f47808d3f48b54daf40e1\">\n" +
            "                <response match=\"answer\" score=\"1\" input=\"e01b13b829c64adab6ff3cd988714832\">\n" +
            "                    <feedback><p id=\"efd1634deb3148e68b543812e3292112\">Correct!</p></feedback>\n" +
            "                </response>\n" +
            "                <response match=\"*\" score=\"0\" input=\"e01b13b829c64adab6ff3cd988714832\">\n" +
            "                    <feedback><p id=\"a75d73c7d37c4175b4d2de4fae2c077d\">Incorrect.</p></feedback>\n" +
            "                </response>\n" +
            "            </part>\n" +
            "        </text>\n" +
            "        <fill_in_the_blank id=\"eaf7c0b65e6b47de9305a5eb004509aa\" grading=\"automatic\">\n" +
            "            <body><p id=\"b18ab0c66f4845b5b8190b6d394431c4\">Add numeric, text, or dropdown <input_ref\n" +
            "                        input=\"c2a3786f63ce4dedb95a46570a983415\"/>components</p></body>\n" +
            "            <input shuffle=\"true\" id=\"c2a3786f63ce4dedb95a46570a983415\">\n" +
            "                <choice value=\"a7906f6c31a241f5a62a1fbfd9b34e40\">one</choice>\n" +
            "                <choice value=\"c36c9ad6abd94488949486e38312aa14\">false</choice>\n" +
            "            </input>\n" +
            "            <part id=\"ed1ddd8e53d249bc9744390a6b240052\">\n" +
            "                <response match=\"a7906f6c31a241f5a62a1fbfd9b34e40\" score=\"1\"\n" +
            "                    input=\"c2a3786f63ce4dedb95a46570a983415\">\n" +
            "                    <feedback><p id=\"f7784d8ce99045b29184a8812ce92f24\">good</p></feedback>\n" +
            "                </response>\n" +
            "                <response match=\"c36c9ad6abd94488949486e38312aa14\" score=\"0\"\n" +
            "                    input=\"c2a3786f63ce4dedb95a46570a983415\">\n" +
            "                    <feedback><p id=\"c39dba64d2894280a1f94621f41ca2e8\">wrong</p></feedback>\n" +
            "                </response>\n" +
            "            </part>\n" +
            "        </fill_in_the_blank>\n" +
            "    </page>\n" +
            "</assessment>\n";
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

    @Test
    public void transformA2ToInline() throws JDOMException, IOException {
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);;
        builder.setExpandEntities(false);
        try {
            Document document = builder.build(new StringReader(a2ToIn.trim()));
            DocType docType = document.getDocType();
            if(docType.getSystemID().contains("oli_assessment_")) {
                Assessment2Transform a2Transform = new Assessment2Transform();
                a2Transform.transformToUnified(document.getRootElement());
                docType = new DocType("assessment",
                        "-//Carnegie Mellon University//DTD Inline Assessment MathML 1.4//EN",
                        "http://oli.cmu.edu/dtd/oli_inline_assessment_mathml_1_4.dtd");
                document.setDocType(docType);
                Element rootElement = document.getRootElement();
                rootElement.removeAttribute("recommended_attempts");
                rootElement.removeAttribute("max_attempts");
                XPathExpression<Element> questionTypes = XPathFactory.instance().compile(
                        "//multiple_choice | //text | //fill_in_the_blank | //numeric | //essay | //short_answer | //image_hotspot | //ordering",
                        Filters.element());
                String query = "//question | //introduction";
                XPathExpression<Element> xexpression = XPathFactory.instance().compile(query, Filters.element());
                List<Element> kids = xexpression.evaluate(document);
                for (Element el : kids) {
                    if (el.getName().equalsIgnoreCase("introduction")) {
                        Element firstPage = rootElement.getChild("page");
                        if (firstPage != null) {
                            Element firstPageContent = firstPage.getChild("content");
                            if (firstPageContent == null) {
                                firstPageContent = new Element("content");
                                firstPage.addContent(firstPage.indexOf(firstPage.getChild("title")) + 1, firstPageContent);
                            }
                            List<Element> contents = el.getChildren();
                            for (Element content : contents) {
                                firstPageContent.addContent(contents.indexOf(content), content.clone());
                            }
                        }
                        el.detach();
                    }

                    if (el.getName().equalsIgnoreCase("question")) {
                        List<Element> qt = questionTypes.evaluate(el);
                        qt.forEach(qel -> {
                            if (qel.getName().equalsIgnoreCase("essay")) {
                                qel.setName("short_answer");
                            }
                        });
                        List<String> ats = el.getAttributes().stream().map(Attribute::getName).collect(Collectors.toList());

                        ats.forEach(at -> {
                            if(!at.equalsIgnoreCase("id")){
                                Attribute atr = el.getAttribute(at);
                                qt.forEach(e -> {
                                    if(e.getName().equalsIgnoreCase("text")) {
                                        if (!at.equalsIgnoreCase("grading")) {
                                            e.setAttribute(atr.getName(), atr.getValue());
                                        }
                                    }
                                });
                                atr.detach();
                            }
                        });

                        Attribute cs = el.getAttribute("case_sensitive");
                        if(cs != null) {
                            cs.detach();
                            qt.forEach(e -> {
                                if(e.getName().equalsIgnoreCase("text")) {
                                    e.setAttribute(cs.getName(), cs.getValue());
                                }
                            });
                        }
                    }
                }
                Format format = Format.getPrettyFormat();
                format.setIndent("\t");
                format.setTextMode(Format.TextMode.PRESERVE);
                System.out.println("What is up ----- \n" + new XMLOutputter(format).outputString(document));
                query = "//question";
                xexpression = XPathFactory.instance().compile(query, Filters.element());
                assertEquals(xexpression.evaluate(document).size(), 8);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
