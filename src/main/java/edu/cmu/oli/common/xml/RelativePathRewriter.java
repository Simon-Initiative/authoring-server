/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018. Carnegie Mellon University
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

package edu.cmu.oli.common.xml;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Text;
import org.jdom2.filter.AbstractFilter;
import org.jdom2.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class RelativePathRewriter {

    private static final Logger log = LoggerFactory.getLogger(RelativePathRewriter.class);

    private final String rootPath;
    private static final Map<String, PathRewriteSpec[]> _PATH_SPECS;
    private static final char _DEFAULT_FILE_SEPARATOR = '/';

    static {
        // @src
        AttributeSpec srcAttrSpec = new AttributeSpec("src");
        // @href
        AttributeSpec hrefAttrSpec = new AttributeSpec("href");

        AttributeSpec posterAttrSpec = new AttributeSpec("poster");

        AttributeSpec layoutAttrSpec = new AttributeSpec("layout");

        // Construct normalizeRelative specifications by element
        _PATH_SPECS = new HashMap<String, PathRewriteSpec[]>();
        _PATH_SPECS.put("audio",
                new PathRewriteSpec[]{srcAttrSpec});
        _PATH_SPECS.put("applet",
                new PathRewriteSpec[]{new AttributeSpec("codebase")});
        _PATH_SPECS.put("conjugate",
                new PathRewriteSpec[]{srcAttrSpec});
        _PATH_SPECS.put("director",
                new PathRewriteSpec[]{srcAttrSpec});
        _PATH_SPECS.put("flash",
                new PathRewriteSpec[]{srcAttrSpec});
        _PATH_SPECS.put("image",
                new PathRewriteSpec[]{srcAttrSpec});
        _PATH_SPECS.put("image_hotspot",
                new PathRewriteSpec[]{srcAttrSpec});
        _PATH_SPECS.put("image_input",
                new PathRewriteSpec[]{srcAttrSpec});
        _PATH_SPECS.put("link",
                new PathRewriteSpec[]{hrefAttrSpec});
        _PATH_SPECS.put("path",
                new PathRewriteSpec[]{hrefAttrSpec});
        _PATH_SPECS.put("pronunciation",
                new PathRewriteSpec[]{srcAttrSpec});
        _PATH_SPECS.put("video",
                new PathRewriteSpec[]{srcAttrSpec, hrefAttrSpec, posterAttrSpec});
        _PATH_SPECS.put("source",
                new PathRewriteSpec[]{srcAttrSpec});
        _PATH_SPECS.put("track",
                new PathRewriteSpec[]{srcAttrSpec});
        _PATH_SPECS.put("panopto",
                new PathRewriteSpec[]{srcAttrSpec});
        _PATH_SPECS.put("unity",
                new PathRewriteSpec[]{srcAttrSpec});
        _PATH_SPECS.put("mathematica",
                new PathRewriteSpec[]{srcAttrSpec});
        _PATH_SPECS.put("custom",
                new PathRewriteSpec[]{srcAttrSpec, layoutAttrSpec});
    }

    private static final Filter _NAME_FILTER = new AbstractFilter() {

        @Override
        public Element filter(Object obj) {
            Element element = obj instanceof Element ? (Element) obj : null;

            if (element != null && _PATH_SPECS.containsKey(element.getName())) {
                return element;
            }
            return null;
        }
    };

    private static interface PathRewriteSpec {

        void normalizeRelative(String rootPath, Element e);

        void rewriteAbsolute(String rootPath, Element e);
    }

    ;

    private static class AttributeSpec implements PathRewriteSpec {

        private static final Logger log = LoggerFactory.getLogger(AttributeSpec.class);
        private final String name;

        AttributeSpec(String name) {
            this.name = name;
        }

        public void normalizeRelative(String rootPath, Element e) {
            String rawRelPath = e.getAttributeValue(name);
            if (rawRelPath != null && isRelativePath(rawRelPath)) {
                String relPath = normalizeFileSeparators(rawRelPath.trim());
                String absPath = resolveRelativePath(rootPath, relPath);
                log.trace("rewrite(): attribute=" + name + ": " + relPath + " ==>> " + absPath);
                e.setAttribute(name, absPath);
            }
        }

        public void rewriteAbsolute(String rootPath, Element e) {
            String relPath = e.getAttributeValue(name);
            if (relPath != null && isRelativePath(relPath)) {
                String absPath = rootPath + relPath;
                log.trace("rewrite(): attribute=" + name + ": " + relPath + " ==>> " + absPath);
                e.setAttribute(name, absPath);
            }
        }
    }

    private static class TextSpec implements PathRewriteSpec {

        private static final Logger log = LoggerFactory.getLogger(TextSpec.class);
        private static final TextSpec instance = new TextSpec();

        private TextSpec() {
        }

        static TextSpec getInstance() {
            return instance;
        }

        public void normalizeRelative(String rootPath, Element e) {
            String rawRelPath = e.getTextTrim();
            if (rawRelPath != null && isRelativePath(rawRelPath)) {
                String relPath = normalizeFileSeparators(rawRelPath.trim());
                String absPath = resolveRelativePath(rootPath, relPath);
                log.trace("rewrite(): " + relPath + " ==>> " + absPath);
                e.setText(absPath);
            }
        }

        public void rewriteAbsolute(String rootPath, Element e) {
            String relPath = e.getTextTrim();
            if (relPath != null && isRelativePath(relPath)) {
                String absPath = rootPath + relPath;
                log.trace("rewrite(): " + relPath + " ==>> " + absPath);
                e.setText(absPath);
            }
        }
    }

    private static class PathTagSpec implements PathRewriteSpec {

        private static final Logger log = LoggerFactory.getLogger(PathTagSpec.class);
        private static final PathTagSpec instance = new PathTagSpec();

        private PathTagSpec() {
        }

        static PathTagSpec getInstance() {
            return instance;
        }

        public void normalizeRelative(String rootPath, Element e) {
            String rawRelPath = e.getAttributeValue("href");
            if (rawRelPath != null && isRelativePath(rawRelPath)) {
                // Rewrite the path
                String relPath = normalizeFileSeparators(rawRelPath.trim());
                String absPath = resolveRelativePath(rootPath, relPath);
                log.trace("rewrite(): " + relPath + " ==>> " + absPath);

                // Replace path element with text of rewritten path
                Element parent = e.getParentElement();
                int idx = parent.indexOf(e);
                parent.setContent(idx, new Text(absPath));
            }
        }

        public void rewriteAbsolute(String rootPath, Element e) {
            String relPath = e.getAttributeValue("href");
            if (relPath != null && isRelativePath(relPath)) {
                String absPath = rootPath + relPath;
                log.trace("rewrite(): " + relPath + " ==>> " + absPath);

                // Replace path element with text of rewritten path
                Element parent = e.getParentElement();
                int idx = parent.indexOf(e);
                parent.setContent(idx, new Text(absPath));
            }
        }
    }

    // =======================================================================
    // Public constructors
    // =======================================================================
    public RelativePathRewriter(String path) {
        if (path == null) {
            throw (new NullPointerException("'rootPath' cannot be null"));
        }

        // Trim and normalize file separators in the root path
        path = normalizeFileSeparators(path.trim());

        // Does the root path end at a file or a dirctory?
        if (isFilePath(path)) {
            // File, get path to parent directory
            path = getParentPath(path);
        }

        // Remove leading file separators so paths are relative
        this.rootPath = trimLeadingFileSeparators(path);
        log.trace("RelativePathRewriter(): rootPath=" + this.rootPath);
    }

    // =======================================================================
    // Public instance methods
    // =======================================================================
    public void normalizeRelativePaths(Document doc) {
        //log.debug("normalizeRelativePaths(): rewriting relative paths");

        // Identify elements to normalizeRelative, we do not normalizeRelative them directly to
        // avoid concurrent modification of the iterator.
        List<Element> matches = new ArrayList<Element>();
        for (Iterator i = doc.getDescendants(_NAME_FILTER); i.hasNext(); ) {
            Element child = (Element) i.next();
            matches.add(child);
        }

        // Rewrite paths contained in matching elements
        int n = 0;
        for (Element child : matches) {
            PathRewriteSpec[] specs = _PATH_SPECS.get(child.getName());
            for (PathRewriteSpec spec : specs) {
                spec.normalizeRelative(rootPath, child);
                n++;
            }
        }

        //log.debug("normalizeRelativePaths(): " + n + " candidate paths were processed");
    }

    public void convertToAbsolutePaths(Document doc) {
        //log.debug("convertToAbsolutePaths(): converting to absolute paths");

        // Identify elements to normalizeRelative, we do not normalizeRelative them directly to
        // avoid concurrent modification of the iterator.
        List<Element> matches = new ArrayList<Element>();
        for (Iterator i = doc.getDescendants(_NAME_FILTER); i.hasNext(); ) {
            Element child = (Element) i.next();
            matches.add(child);
        }

        // Rewrite paths contained in matching elements
        int n = 0;
        for (Element child : matches) {
            PathRewriteSpec[] specs = _PATH_SPECS.get(child.getName());
            for (PathRewriteSpec spec : specs) {
                //log.debug(new XMLOutputter(Format.getPrettyFormat()).outputString(child));
                spec.rewriteAbsolute(rootPath, child);
                n++;
            }
        }

        //log.debug("convertToAbsolutePaths(): " + n + " candidate paths were processed");
    }

    // =======================================================================
    // Private static methods
    // =======================================================================
    private static boolean isFileSeparator(char c) {
        return (c == '/' || c == '\\');
    }

    private static String normalizeFileSeparators(String path) {
        return path.replace('\\', '/');
    }

    private static String trimLeadingFileSeparators(String path) {
        int n = 0;
        while (n < path.length() && isFileSeparator(path.charAt(n))) {
            n++;
        }
        return (n > 0 ? path.substring(n) : path);
    }

    private static int firstFileSeparator(String path) {
        int n = 0;
        while (n < path.length() && !isFileSeparator(path.charAt(n))) {
            n++;
        }
        return (n == path.length() ? -1 : n);
    }

    private static String getParentPath(String path) {
        for (int i = (path.length() - 2); i >= 0; i--) {
            if (isFileSeparator(path.charAt(i))) {
                return path.substring(0, i + 1);
            }
        }
        return "";
    }

    private static boolean isFilePath(String path) {
        return !isFileSeparator(path.charAt(path.length() - 1));
    }

    private static boolean isRelativePath(String path) {
        // Is there a leading file separator?
        if (path == null || path.isEmpty() || isFileSeparator(path.charAt(0))) {
            return false;
            // Does the path contain a protocol?
        } else if (path.contains(":")) {
            return false;
        } else {
            return true;
        }
    }

    private static String resolveRelativePath(String root, String path) {
        // Is the specified path empty?
        if ("".equals(path)) {
            return root;
        }

        // Locate first file separator in path
        int slash = firstFileSeparator(path);
        if (slash == 0) {
            throw (new AssertionError("path is not relative: " + path));
        }

        // Locate the next file path component
        String next = null;

        // Does the path contain a file separator?
        if (slash == -1) {
            // No, next component is the full path
            next = path;
            path = "";
        } else {
            // Yes, search continues after next path
            next = path.substring(0, slash + 1);
            path = path.substring(slash + 1);

            // Remove leading file separators
            path = trimLeadingFileSeparators(path);
        }

        // Is the next path the current path?
        if (next.equals("./")) {
            return resolveRelativePath(root, path);
            // Is the next path the parent path?
        } else if (next.equals("../")) {
            return resolveRelativePath(getParentPath(root), path);
            // Append next path element to the root
        } else {
            return resolveRelativePath(root + next, path);
        }
    }
}
