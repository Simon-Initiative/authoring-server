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

public abstract class TextResponseMatcher implements ResponseMatcher<String> {
    private String pattern;
    private boolean caseSensitive = true;

    public TextResponseMatcher(String pattern) {
        setPattern(pattern);
    }

    public TextResponseMatcher(TextResponseMatcher other) {
        if (other == null) {
            throw (new NullPointerException("'other' cannot be null"));
        }
        setPattern(other.getPattern());
        setCaseSensitive(other.isCaseSensitive());
    }

    public String getPattern() {
        return pattern;
    }

    private void setPattern(String pattern) {
        if (pattern == null) {
            throw (new NullPointerException("'pattern' cannot be null"));
        }
        this.pattern = pattern;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    @Override
    public String toString() {
        return pattern;
    }

    @Override
    public int hashCode() {
        int hashCode = 1;
        hashCode = (hashCode * 7) + pattern.hashCode();
        hashCode = (hashCode * 7) + (caseSensitive ? 1 : 0);
        return hashCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null) return false;
        if (!(other instanceof TextResponseMatcher)) return false;

        TextResponseMatcher m = (TextResponseMatcher) other;
        if (!pattern.equals(m.getPattern())) return false;
        if (caseSensitive != m.isCaseSensitive()) return false;
        return true;
    }
}
