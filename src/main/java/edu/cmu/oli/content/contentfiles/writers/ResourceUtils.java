package edu.cmu.oli.content.contentfiles.writers;

import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Resource utility functions.
 */
public final class ResourceUtils {

    private ResourceUtils() {

    }

    /**
     * Finds block level elements (dl, ul, ol, table, p) that are nested inside of
     * paragraph elements and move them up one level to be a sibling
     * of the paragraph, instead of a child.
     *
     * @param document the document to process
     */
    public final static void adjustNestedBlocks(final Document document) {

        final String query = "//p/ul | //p/ol | //p/dl | //p/table | //p/p";
        final XPathExpression<Element> xexpression = XPathFactory.instance().compile(query, Filters.element());
        final Element rootElement = document.getRootElement();

        List<Element> kids = xexpression.evaluate(rootElement);

        // We rerun this query more than once to support simplified
        // processing of one nested block per paragraph
        while (kids.size() > 0) {

            // Create a map of at most one nested block element to its parent
            // This will allow a simpler implementation so we don't have to
            // concern ourselves with conditions where we have multiple lists in a row.
            final Map<Element, Element> elementPairs = new HashMap<>();
            for (Element el : kids) {
                elementPairs.put(el.getParentElement(), el);
            }

            // Now process and correct the parent child pairs
            for (final Map.Entry<Element, Element> entry : elementPairs.entrySet()) {
                adjustBlock(entry.getKey(), entry.getValue());
            }

            // Re-evaluate to pick up cases where we
            // had multiple blocks nested in a single paragraph
            kids = xexpression.evaluate(rootElement);

        }


    }

    /**
     * Processes a single adjustment, handling several cases:
     * <ol>
     * <li>the child is the only element</li>
     * <li>the child is the first element</li>
     * <li>the child is the last element</li>
     * <li>the child is somewhere in the middle of the collection</li>
     * </ol>
     *
     * @param parent the parent element (the paragraph)
     * @param child  the nested block element
     */
    private final static void adjustBlock(final Element parent, final Element child) {

        final int indexOfChild = parent.indexOf(child);
        final Element grandParent = parent.getParentElement();
        final int indexOfParent = grandParent.indexOf(parent);

        if (indexOfChild == 0 && parent.getContentSize() == 1) {
            // Child is the only element.  Just elevate the child and remove the parent.
            parent.removeContent(child);
            grandParent.addContent(indexOfParent + 1, child);
            grandParent.removeContent(parent);

        } else if (indexOfChild == 0) {
            // Child is the first element, reparent the child as a predecessor sibling to the parent.
            parent.removeContent(child);
            grandParent.addContent(indexOfParent, child);

        } else if (indexOfChild == parent.getContentSize() - 1) {
            // Child is last, so we reparent the child as a successor sibling to the parent.
            parent.removeContent(child);
            grandParent.addContent(indexOfParent + 1, child);

        } else {
            // Child is in the middle of the content, so we split the parent element at this point
            // and elevate the child and the remaining split of the parent as successor siblings to the
            // parent.

            parent.removeContent(child);

            final List<Content> split = new ArrayList<Content>();

            do {
                final Content removed = parent.removeContent(indexOfChild);
                split.add(removed);
            } while (parent.getContentSize() > indexOfChild);

            // Insert the block item
            grandParent.addContent(indexOfParent + 1, child);

            // Create and insert a new paragraph to hold the remaining part of the split
            final Element newParagraph = new Element("p");
            newParagraph.addContent(split);
            grandParent.addContent(indexOfParent + 2, newParagraph);

        }

    }

}
