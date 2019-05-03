/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018. Carnegie Mellon University
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package edu.cmu.oli.workbookpage.nodes;


import com.google.gson.JsonObject;
import edu.cmu.oli.content.models.persistance.entities.Resource;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ProcessingException;

/**
 * Embeds an inline activity into the workbook page.
 */
public class InlineNode {

    private static final Logger log = LoggerFactory.getLogger(InlineNode.class);

    private String idref;
    private String src;
    private String height;
    private String width;
    private String purpose;

    public Element process(Element element, Resource resource, String webContentPath, JsonObject inlineDelivery) throws ProcessingException {
        this.idref = element.getAttributeValue("idref");
        this.src = element.getAttributeValue("src");
        this.width = element.getAttributeValue("width");
        this.height = element.getAttributeValue("height");
        this.purpose = element.getAttributeValue("objective");
        if (purpose == null) {
            this.purpose = element.getAttributeValue("purpose");
        }

        String deliveryMethod = inlineDelivery.get("method").getAsString();

        Element inlineElmnt = new Element(deliveryMethod);

        // ID
        inlineElmnt.setAttribute("id", resource.getId());

        inlineElmnt.setAttribute("actCtxGuid", resource.getGuid());

        // Source
        if (src == null) {
            inlineElmnt.setAttribute("src", inlineDelivery.get("clientURI").getAsString());
        } else {
            inlineElmnt.setAttribute("src", src);
        }

        // Width
        if (width != null) {
            inlineElmnt.setAttribute("width", width);
        }

        // Height
        if (height != null) {
            inlineElmnt.setAttribute("height", height);
        }

        // Purpose
        if (this.purpose != null) {
            inlineElmnt.setAttribute("purpose", this.purpose);
        }

        // Title
        Element titleElmnt = new Element("title");
        if (resource.getTitle() != null) {
            titleElmnt.setText(resource.getTitle());
        }
        inlineElmnt.addContent(titleElmnt);

        // Dynamic Parameters
        Element p0 = new Element("param");
        p0.setAttribute("name", "inlineClient");
        p0.setText(inlineDelivery.get("clientURI").getAsString());
        inlineElmnt.addContent(p0);

        inlineElmnt.setAttribute("serviceUri", inlineDelivery.get("serviceURI").getAsString());

        Element p1 = new Element("param");
        p1.setAttribute("name", "activityMode");
        p1.setText("delivery");
        inlineElmnt.addContent(p1);

        Element p2 = new Element("param");
        p2.setAttribute("name", "activityContextGUID");
        p2.setText(resource.getGuid());
        inlineElmnt.addContent(p2);

        Element p3 = new Element("param");
        p3.setAttribute("name", "userGUID");
        p3.setText("Previewer");
        inlineElmnt.addContent(p3);

        Element p4 = new Element("param");
        p4.setAttribute("name", "activityService");
        p4.setText(inlineDelivery.get("serviceURI").getAsString());
        inlineElmnt.addContent(p4);

        Element p5 = new Element("param");
        p5.setAttribute("name", "webContentPath");
        p5.setText(webContentPath);
        inlineElmnt.addContent(p5);

        Element p6 = new Element("param");
        p6.setAttribute("name", "logService");
        // :FIXME: do not hardcode path to log service
        p6.setText("/log/server");
        inlineElmnt.addContent(p6);


        // Static Parameters - Syllabus Config
        if (!(deliveryMethod.equals("inline_superactivity"))) {
            inlineElmnt.setAttribute("activityComponents", "/superactivity/assessment");
        }
        return inlineElmnt;
    }

}
