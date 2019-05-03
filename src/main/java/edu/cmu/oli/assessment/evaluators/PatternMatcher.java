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

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class PatternMatcher extends TextResponseMatcher {
    public PatternMatcher(String pattern) {
        super(pattern);                                                            // :TODO: clean-up validation

        if (!isValidPattern(pattern)) {
            throw (new IllegalArgumentException("pattern not a regular expression"));
        }
    }

    public PatternMatcher(PatternMatcher other) {
        super(other);
    }

    public boolean matches(String response) {
        if (response == null) return false;

        // Compile regular expression
        Pattern regex = Pattern.compile(getPattern(), getFlags());

        // Does response match pattern?
        return regex.matcher(response).matches();
    }

    @Override
    public int hashCode() {
        int hashCode = 1;
        hashCode = (hashCode * 7) + super.hashCode();
        return hashCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null) return false;
        if (!(other instanceof PatternMatcher)) return false;

        PatternMatcher m = (PatternMatcher) other;
        if (!super.equals(m)) return false;
        return true;
    }

    public static boolean isValidPattern(String pattern) {
        if (pattern == null) return false;

        try {
            Pattern.compile(pattern, Pattern.CANON_EQ);
        } catch (PatternSyntaxException ex) {
            return false;
        }

        return true;
    }

    private int getFlags() {
        int flags = Pattern.CANON_EQ;
        if (!isCaseSensitive()) {
            flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        }
        return flags;
    }
}
