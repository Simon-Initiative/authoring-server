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

package edu.cmu.oli.assessment.builders;

import org.jdom2.Attribute;
import org.jdom2.Element;
import org.jdom2.filter.ElementFilter;
import org.jdom2.filter.Filters;
import org.jdom2.util.IteratorIterable;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.inject.Default;
import java.util.*;

/**
 * @author Raphael Gachuhi
 */
@Default
public class Assessment2Transform {

    private static Logger log = LoggerFactory.getLogger(Assessment2Transform.class);

    private static Set<String> questionTypes = new HashSet<>(Arrays.asList("multiple_choice", "text",
            "fill_in_the_blank", "numeric", "essay", "short_answer", "image_hotspot", "ordering"));

    public Element transformToUnified(Element root) {
        XPathFactory xFactory = XPathFactory.instance();

        // Account for old A2 docs that used "yes" and "no" for shuffle
        XPathExpression<Element> xexpression = xFactory.compile("//input", Filters.element());
        List<Element> kids = xexpression.evaluate(root);
        Iterator<Element> it = kids.iterator();
        while (it.hasNext()) {
            Element next = it.next();
            final String shuffle = next.getAttributeValue("shuffle");

            if ("yes".equals(shuffle)) {
                next.setAttribute("shuffle", "true");
            }
            if ("no".equals(shuffle)) {
                next.setAttribute("shuffle", "false");
            }
        }

        xexpression = xFactory.compile(
                "//multiple_choice | //text | //fill_in_the_blank | //numeric | //essay | //short_answer | //image_hotspot | //ordering",
                Filters.element());
        kids = xexpression.evaluate(root);
        it = kids.iterator();
        while (it.hasNext()) {
            Element next = it.next();
            Element body = next.getChild("body");
            IteratorIterable<Element> inputRefs = next.getDescendants(new ElementFilter("input_ref"));
            IteratorIterable<Element> inputs = next.getDescendants(new ElementFilter("input"));
            IteratorIterable<Element> imageInput = next.getDescendants(new ElementFilter("image_input"));

            if (imageInput.hasNext()) {
                imageInput.forEach(element -> {
                    element.setName(next.getName());
                    Attribute select = next.getAttribute("select");
                    if (select != null) {
                        select.detach();
                        element.setAttribute(select);
                    }
                    Attribute grading = next.getAttribute("grading");
                    if (grading != null) {
                        grading.detach();
                        element.setAttribute(grading);
                    }
                });
            }
            if (inputs.hasNext()) {
                inputs.forEach(element -> {
                    element.setName(next.getName());
                    Attribute select = next.getAttribute("select");
                    if (select != null) {
                        select.detach();
                        element.setAttribute(select);
                    }

                });
            } else if (inputRefs.hasNext() && !next.getName().equalsIgnoreCase("image_hotspot")) {
                List<Element> refs = new ArrayList<>();
                inputRefs.forEach(element -> refs.add(element));
                for (int i = 0; i < refs.size(); i++) {
                    Element ty = new Element(next.getName());
                    ty.setAttribute("id", refs.get(i).getAttributeValue("input"));
                    next.addContent(next.indexOf(body) + (i + 1), ty);
                    Attribute select = next.getAttribute("select");
                    if (select != null) {
                        select.detach();
                        ty.setAttribute(select);
                    }
                }
            } else if (!next.getName().equalsIgnoreCase("image_hotspot")) {
                Element input = next.getChild("input");
                if (input == null) {
                    input = new Element(next.getName());
                    input.setAttribute("id", next.getAttributeValue("id") + "_i");
                } else {
                    input.setName(next.getName());
                }
                Attribute select = next.getAttribute("select");
                if (select != null) {
                    select.detach();
                    input.setAttribute(select);
                }
                next.addContent(next.indexOf(body) + 1, input);
            }
            next.setName("question");
        }

        // Identify data from older versions of the A2 DTD where "responses"
        // was used instead of "part". Adjust that name and two attribute names.
        xexpression = xFactory.compile("//responses", Filters.element());
        kids = xexpression.evaluate(root);
        it = kids.iterator();
        while (it.hasNext()) {
            Element next = it.next();
            next.setName("part");

            final String partId = next.getAttributeValue("part_id");
            if (partId != null) {
                next.setAttribute("id", partId);
                next.removeAttribute("part_id");
            }

            final String targetInputs = next.getAttributeValue("target_inputs");
            if (targetInputs != null) {
                next.setAttribute("targets", targetInputs);
                next.removeAttribute("target_inputs");
            }
        }

        return root;
    }

    public Element transformFromUnified(Element root) {
        XPathFactory xFactory = XPathFactory.instance();
        XPathExpression<Element> xexpression = xFactory.compile("//question", Filters.element());

        List<Element> kids = xexpression.evaluate(root);
        for (Element next : kids) {
            IteratorIterable<Element> questionType = null;
            int countTypes = 0;
            for (String el : questionTypes) {
                Optional<IteratorIterable<Element>> found = findDescendants(next, el);
                if (found.isPresent()) {
                    questionType = found.get();
                    countTypes++;
                    if (countTypes > 1) {
                        final StringBuilder message = new StringBuilder();
                        message.append("Assessment2 type questions cannot have multiple parts ");
                        message.append(root.getName());
                        log.error(message.toString());
                        throw new RuntimeException(message.toString());
                    }
                }
            }
            if (questionType == null || !questionType.hasNext()) {
                final StringBuilder message = new StringBuilder();
                message.append(
                        "Issue converting assessment model no valid question type found (multiple_choice, text etc)");
                message.append(root.getName());
                log.error(message.toString());
                throw new RuntimeException(message.toString());
            }
            String name = questionType.next().getName();

            next.setName(name);
            switch (name) {
                case "short_answer":
                case "essay":
                    List<Element> elements = new ArrayList<>();
                    questionType.forEach(element -> {
                        elements.add(element);
                    });
                    elements.forEach(e -> {
                        e.getParent().removeContent(e);
                    });
                    break;
                case "image_hotspot":
                    questionType.forEach(element -> {
                        element.setName("image_input");
                        element.getAttributes().forEach(attribute -> {
                            if (attribute.getName().equalsIgnoreCase("select") ||
                                    attribute.getName().equalsIgnoreCase("grading")) {
                                attribute.detach();
                                ((Element) element.getParent()).setAttribute(attribute);
                            }
                        });

                    });
                    break;
                default:
                    questionType.forEach(element -> {
                        element.setName("input");
                    });
                    break;
            }
        }

        return root;
    }

    private Optional<IteratorIterable<Element>> findDescendants(Element next, String name) {
        IteratorIterable<Element> inputs = next.getDescendants(new ElementFilter(name));
        if (inputs.hasNext()) {
            return Optional.of(inputs);
        }
        return Optional.empty();
    }
}
