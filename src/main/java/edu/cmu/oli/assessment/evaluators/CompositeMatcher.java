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

public class CompositeMatcher<T> implements ResponseMatcher<T> {
    private ResponseMatcher<? super T> lft;
    private ResponseMatcher<? super T> rgt;
    private BooleanOperator op = BooleanOperator.AND;

    public enum BooleanOperator {AND, OR, XOR}

    public CompositeMatcher(ResponseMatcher<? super T> lft, ResponseMatcher<? super T> rgt) {
        if (lft == null) {
            throw (new NullPointerException("'lft' cannot be null"));
        } else if (rgt == null) {
            throw (new NullPointerException("'rgt' cannot be null"));
        }

        this.lft = lft;
        this.rgt = rgt;
    }

    public ResponseMatcher getLeft() {
        return lft;
    }

    public void setLeft(ResponseMatcher<? super T> lft) {
        if (lft == null) {
            throw (new NullPointerException("'lft' cannot be null"));
        }
        this.lft = lft;
    }

    public ResponseMatcher getRight() {
        return rgt;
    }

    public void setRight(ResponseMatcher<? super T> rgt) {
        if (rgt == null) {
            throw (new NullPointerException("'rgt' cannot be null"));
        }
        this.rgt = rgt;
    }

    public boolean matches(T response) {
        if (response == null) return false;

        boolean lftMatches = lft.matches(response);
        boolean rgtMatches = rgt.matches(response);

        switch (op) {
            case AND:
                return (lftMatches && rgtMatches);
            case OR:
                return (lftMatches || rgtMatches);
            case XOR:
                return ((lftMatches || rgtMatches) && !(lftMatches && rgtMatches));
        }
        ;

        return false;
    }

    @Override
    public int hashCode() {
        int hashCode = 31;
        hashCode = (hashCode * 17) + lft.hashCode();
        hashCode = (hashCode * 17) + rgt.hashCode();
        hashCode = (hashCode * 17) + op.hashCode();
        return hashCode;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (other == null) return false;
        if (!(other instanceof CompositeMatcher)) return false;

        CompositeMatcher m = (CompositeMatcher) other;
        if (!lft.equals(m.lft)) return false;
        if (!rgt.equals(m.rgt)) return false;
        if (op != m.op) return false;
        return true;
    }
}