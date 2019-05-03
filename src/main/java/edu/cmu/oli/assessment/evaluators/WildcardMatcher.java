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

package edu.cmu.oli.assessment.evaluators;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

public class WildcardMatcher implements ResponseMatcher<List<String>> {
    private static final char _DELIM = ',';
    private String glob;

    public WildcardMatcher(String glob) {
        setPattern(glob);
    }

    public WildcardMatcher(WildcardMatcher other) {
        if (other == null) {
            throw (new NullPointerException("'other' cannot be null"));
        }
        setPattern(other.getPattern());
    }

    public String getPattern() {
        return glob;
    }

    public void setPattern(String glob) {
        if (glob == null) {
            throw (new NullPointerException("'glob' cannot be null"));
        }
        this.glob = glob;
    }

    public boolean matches(List<String> response) {
        if (response == null) return false;

        Pattern pattern = compilePattern();

        StringBuilder b = new StringBuilder();
        for (Iterator<String> i = response.iterator(); i.hasNext(); ) {
            b.append(i.next());
            if (i.hasNext()) b.append(_DELIM);
        }

        return pattern.matcher(b).matches();
    }

    @Override
    public int hashCode() {
        int hashCode = 1;
        hashCode = (hashCode * 7) + glob.hashCode();
        return hashCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null) return false;
        if (!(other instanceof WildcardMatcher)) return false;

        WildcardMatcher m = (WildcardMatcher) other;
        if (!glob.equals(m.glob)) return false;
        return true;
    }

    public static boolean isWildcardOperator(String s) {
        if ("*".equals(s)) return true;
        if ("+".equals(s)) return true;
        if ("?".equals(s)) return true;
        return false;
    }

    private Pattern compilePattern() {
        return Pattern.compile(globToPattern(glob));
    }

    private static String globToPattern(String glob) {
        StringBuilder regex = new StringBuilder();
        List<String> normal = normalizeGlob(glob);

        boolean skipDelim = true;
        for (Iterator<String> i = normal.iterator(); i.hasNext(); ) {
            String token = i.next();
            if ("*".equals(token)) {
                if (skipDelim) {
                    regex.append("([^,]+(,[^,]+)*)");
                    if (i.hasNext()) regex.append("?");
                } else {
                    regex.append("(,[^,]+)*");
                }
            } else if ("?".equals(token)) {
                if (!skipDelim) regex.append(_DELIM);
                regex.append("([^,]+)");
            } else {
                if (!skipDelim) regex.append(_DELIM);
                String escaped = Pattern.quote(token);
                regex.append('(').append(escaped).append(')');
            }
            skipDelim = false;
        }

        return regex.toString();
    }

    private static List<String> normalizeGlob(String glob) {
        String[] tokens = glob.split(",");
        List<String> normal = new ArrayList<String>();
        boolean lastStar = false;
        for (int i = 0, n = tokens.length; i < n; i++) {
            String token = tokens[i].trim();
            if ("+".equals(token)) {
                normal.add("?");
                if (!lastStar) {
                    normal.add("*");
                }
                lastStar = true;
            } else if ("*".equals(token)) {
                if (!lastStar) {
                    normal.add("*");
                    lastStar = true;
                }
            } else if ("?".equals(token)) {
                normal.add(token);
                lastStar = false;
            } else {
                normal.add(token);
                lastStar = false;
            }
        }
        return normal;
    }
}