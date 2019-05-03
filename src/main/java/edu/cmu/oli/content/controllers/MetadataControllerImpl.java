package edu.cmu.oli.content.controllers;

import edu.cmu.oli.content.models.persistance.entities.Resource;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Raphael Gachuhi
 */
public class MetadataControllerImpl implements MetadataController {

    private static Map<String, Namespace> metadataNamespaces = new HashMap<>();

    private XPathFactory xFactory = XPathFactory.instance();

    private String metadata = "<metadata>\n" +
            "        <md:metadata xmlns:md=\"http://oli.web.cmu.edu/metadata/\" mode=\"delivery\" timestamp=\"05/07/2018 8:32 AM EDT\" license=\"http://creativecommons.org/licenses/by-nc-sa/3.0/\">\n" +
            "            <md:web_content href=\"webcontentlocation\" />\n" +
            "            <md:session guid=\"3a7e79c60a00005602f2a434c17568d3\" domain_id=\"external\" user_guid=\"previewer\" token=\"s=xxxxx\" />\n" +
            "            <md:authorizations view_material=\"true\" instruct_material=\"true\" debug_content=\"true\" />\n" +
            "            <md:user guid=\"previewer\" first_name=\"Preview\" last_name=\"User\" email=\"previewResource@oli.cmu.edu\" anonymous=\"false\" role=\"instructor\" />\n" +
            "            <md:section guid=\"9adc156c0a00005613c3a2ad82fef705\" admit_code=\"xed-previewResource\" title=\"Quick Preview\" institution=\"CMU\" start_date=\"04/06/2018\" end_date=\"04/06/2019\" time_zone=\"America/New_York\" duration=\"Apr 2018 - Apr 2019\" external_roster=\"false\" page_max=\"2\" guest_section=\"false\">\n" +
            "                <md:instructors>\n" +
            "                    <md:user guid=\"previewer\" first_name=\"Preview\" last_name=\"User\" email=\"previewResource@oli.cmu.edu\" anonymous=\"false\" />\n" +
            "                </md:instructors>\n" +
            "            </md:section>\n" +
            "            <md:user_activity_context instruct_material=\"true\" segment_just_in_time=\"false\" segment_adjusted=\"false\" activity_just_in_time=\"false\" activity_adjusted=\"false\" status=\"during\">\n" +
            "                <md:activity_context guid=\"9adc158c0a000056739f1ef4c463ebae\">\n" +
            "                    <md:segment guid=\"9adc158a0a000056584b51bd654e302c\" segment_guid=\"9adc158b0a0000565659638d6a15cef9\" grain_size=\"module\" high_stakes=\"false\" number=\"1\" module=\"false\" href=\"/jcourse/workbook/activity/page?context=9adc158c0a000056739f1ef4c463ebae\" category=\"content\" audience=\"all\">\n" +
            "                        <md:schedule just_in_time=\"false\" />\n" +
            "                        <md:title>Introduction</md:title>\n" +
            "                    </md:segment>\n" +
            "                    <md:activity guid=\"9adc158b0a0000561a7fc2ac9abf5f3f\" high_stakes=\"false\" number=\"1\" module=\"false\" href=\"/jcourse/workbook/activity/page?context=9adc158c0a000056739f1ef4c463ebae\">\n" +
            "                        <md:schedule just_in_time=\"false\" />\n" +
            "                        <md:title>title</md:title>\n" +
            "                        <md:resource guid=\"9adc12960a0000563bc7e27c9ae26509\" id=\"welcome\" resource_type_id=\"x-oli-workbook_page\" title=\"Welcome!\" href=\"x-oli-workbook_page/welcome.xml\" path=\"x-oli-workbook_page\" />\n" +
            "                    </md:activity>\n" +
            "                </md:activity_context>\n" +
            "            </md:user_activity_context>\n" +
            "            <md:crumb context_guid=\"9adc158b0a0000562577c6fa9efc36bd\" grain_size=\"module\" number=\"1\" href=\"/jcourse/webui/syllabus/module.do?context=9adc158b0a0000562577c6fa9efc36bd\" grain_label=\"Module\" grain_index=\"1\">\n" +
            "                <md:title>Introduction</md:title>\n" +
            "            </md:crumb>\n" +
            "            <md:navigation>\n" +
            "                <md:predecessors>\n" +
            "                    <md:activity guid=\"9adc158a0a000056584b51bd654e302c\" high_stakes=\"false\" number=\"1\" href=\"/jcourse/webui/syllabus/module.do?context=9adc158b0a0000562577c6fa9efc36bd\">\n" +
            "                        <md:schedule just_in_time=\"false\" />\n" +
            "                        <md:title>Introduction</md:title>\n" +
            "                    </md:activity>\n" +
            "                </md:predecessors>\n" +
            "            </md:navigation>\n" +
            "            <md:theme href=\"/repository/presentation/whirlwind-1.4/\" />\n" +
            "        </md:metadata>\n" +
            "    </metadata>";


    private String syllabusMetaData = "<md:syllabus_navigation xmlns:md=\"http://oli.web.cmu.edu/metadata/\" syllabus_guid=\"9adc15840a00005611564dc1d125ea9f\" section_guid=\"9adc156c0a00005613c3a2ad82fef705\" page_max=\"1\">\n" +
            "            <md:labels sequence=\"Sequence\" unit=\"Unit\" module=\"Module\" section=\"Section\" />\n" +
            "            <md:activity_types>\n" +
            "                <md:activity_type resource_type_id=\"x-cmu-stat-stattutor3\" resource_name=\"StatTutor\">\n" +
            "                    <md:uri>/jcourse/superactivity/launcher/deliver</md:uri>\n" +
            "                </md:activity_type>\n" +
            "            </md:activity_types>\n" +
            "            <md:sequence activity_context_guid=\"9adc15890a00005638e35c89e56a0ba5\" title=\"Project Preview\" category=\"content\" audience=\"all\" grain_label=\"Sequence\" high_stakes=\"false\" status=\"during\">\n" +
            "                <md:module activity_context_guid=\"9adc158b0a0000562577c6fa9efc36bd\" title=\"Introduction\" category=\"content\" audience=\"all\" grain_label=\"Module\" grain_index=\"1\" high_stakes=\"false\" status=\"during\" number=\"1\">\n" +
            "                    <md:activity activity_context_guid=\"9adc158c0a000056739f1ef4c463ebae\" title=\"Welcome!\" resource_type_id=\"x-oli-workbook_page\" number=\"1\" high_stakes=\"false\" status=\"during\" />\n" +
            "                </md:module>\n" +
            "            </md:sequence>\n" +
            "            <md:theme href=\"/repository/presentation/whirlwind-1.4/\" />\n" +
            "        </md:syllabus_navigation>";

    @Override
    public Element fetchMetadataForResource(Resource resource, String serverUrl, String themeId) throws JDOMException, IOException {
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        builder.setExpandEntities(false);
        Document metadataDoc = builder.build(new StringReader(this.metadata));

        Namespace md = Namespace.getNamespace("md", "http://oli.web.cmu.edu/metadata/");
        XPathExpression<Element> xexpression = xFactory.compile("//md:web_content", Filters.element(), null, md);
        List<Element> elementList = xexpression.evaluate(metadataDoc);

        elementList.forEach(e -> {
            e.setAttribute("href", serverUrl + "webcontents/" + resource.getContentPackage().getGuid() + "/webcontent");
        });

        xexpression = xFactory.compile("//md:activity_context/md:activity", Filters.element(), null, md);
        elementList = xexpression.evaluate(metadataDoc);
        elementList.forEach(e -> {
            Element titleEl = e.getChild("title", md);
            if (titleEl != null) {
                titleEl.setText(resource.getTitle());
            }
            Element resourceEl = e.getChild("resource", md);
            if (resourceEl != null) {
                resourceEl.setAttribute("id", resource.getId()).setAttribute("resource_type_id", resource.getType()).setAttribute("title", resource.getTitle());
            }
        });

        xexpression = xFactory.compile("//md:module/md:activity", Filters.element(), null, md);
        elementList = xexpression.evaluate(metadataDoc);
        elementList.forEach(e -> {
            e.setAttribute("resource_type_id", resource.getType()).setAttribute("title", resource.getTitle());
        });

        xexpression = xFactory.compile("//md:theme", Filters.element(), null, md);
        elementList = xexpression.evaluate(metadataDoc);
        elementList.forEach(e -> {
            e.setAttribute("href", "/repository/presentation/" + themeId + "/");
        });

        return metadataDoc.getRootElement().detach();
    }

    @Override
    public Element fetchSyllabusMetadata(Resource resource, String serverUrl, String themeId) throws JDOMException, IOException {
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        builder.setExpandEntities(false);
        Document metadataDoc = builder.build(new StringReader(this.syllabusMetaData));

        Namespace md = Namespace.getNamespace("md", "http://oli.web.cmu.edu/metadata/");
        XPathExpression<Element> xexpression = xFactory.compile("//md:theme", Filters.element(), null, md);
        List<Element> elementList = xexpression.evaluate(metadataDoc);
        elementList.forEach(e -> {
            e.setAttribute("href", "/repository/presentation/" + themeId + "/");
        });

        return metadataDoc.getRootElement().detach();
    }

}
