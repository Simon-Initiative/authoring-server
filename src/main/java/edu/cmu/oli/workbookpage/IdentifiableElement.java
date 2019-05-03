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

package edu.cmu.oli.workbookpage;

/**
 * <p>
 * Models a page element which can be uniquely identified by its ID and the ID
 * of the resource which contains it.
 * </p>
 *
 * @version $Revision: 1.1 $ $Date: 2006/04/10 21:46:17 $
 */
public final class IdentifiableElement {
    public final String rsrcId;
    public final String id;

    // =======================================================================
    // Public constructors
    // =======================================================================

    /**
     * <p>
     * Creates a new <tt>IdentifiableElement</tt> instance. This method assumes that
     * its arguments are not <tt>null</tt>.
     * </p>
     *
     * @param rsrcId resource identifier
     * @param id     page element identifier
     */
    public IdentifiableElement(String rsrcId, String id) {
        this.rsrcId = rsrcId;
        this.id = id;
    }

    // =======================================================================
    // Public instance methods
    // =======================================================================

    /**
     * <p>
     * Gets the resource identifier.
     * </p>
     *
     * @return resource ID
     */
    public String getResourceId() {
        return rsrcId;
    }

    /**
     * <p>
     * Gets the XML identifier of the page element.
     * </p>
     *
     * @return identifier
     */
    public String getIdentifier() {
        return id;
    }

    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (other == null)
            return false;
        if (!(other instanceof IdentifiableElement))
            return false;

        final IdentifiableElement ie = (IdentifiableElement) other;
        if (!ie.getResourceId().equals(rsrcId))
            return false;
        if (!ie.getIdentifier().equals(id))
            return false;

        return true;
    }

    public int hashCode() {
        int hashCode = 17;
        hashCode = (hashCode * 31) + rsrcId.hashCode();
        hashCode = (hashCode * 31) + id.hashCode();
        return hashCode;
    }
}
