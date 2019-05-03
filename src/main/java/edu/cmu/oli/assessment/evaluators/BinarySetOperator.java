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

import java.util.Set;

public enum BinarySetOperator {
    PROPER_SUBSET("<") {
        public <C> boolean evaluate(Set<C> lft, Set<C> rgt) {
            if (lft.size() >= rgt.size()) return false;
            return rgt.containsAll(lft);
        }
    },

    SUBSET("<=") {
        public <C> boolean evaluate(Set<C> lft, Set<C> rgt) {
            if (lft.size() > rgt.size()) return false;
            return rgt.containsAll(lft);
        }
    },

    EQ("=") {
        public <C> boolean evaluate(Set<C> lft, Set<C> rgt) {
            return lft.equals(rgt);
        }
    },

    SUPERSET(">=") {
        public <C> boolean evaluate(Set<C> lft, Set<C> rgt) {
            if (lft.size() < rgt.size()) return false;
            return lft.containsAll(rgt);
        }
    },

    PROPER_SUPERSET(">") {
        public <C> boolean evaluate(Set<C> lft, Set<C> rgt) {
            if (lft.size() <= rgt.size()) return false;
            return lft.containsAll(rgt);
        }
    },

    NE("!=") {
        public <C> boolean evaluate(Set<C> lft, Set<C> rgt) {
            return !lft.equals(rgt);
        }
    },

    DISJOINT("<>") {
        public <C> boolean evaluate(Set<C> lft, Set<C> rgt) {
            for (C o : lft) {
                if (rgt.contains(o)) return false;
            }
            return true;
        }
    },

    INTERSECTS("><") {
        public <C> boolean evaluate(Set<C> lft, Set<C> rgt) {
            for (C o : lft) {
                if (rgt.contains(o)) return true;
            }
            return false;
        }
    };

    private final String symbol;

    BinarySetOperator(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    @Override
    public String toString() {
        return getSymbol();
    }

    public abstract <C> boolean evaluate(Set<C> lft, Set<C> rgt);

    public static BinarySetOperator findBySymbol(String s) {
        for (BinarySetOperator o : values()) {
            if (o.getSymbol().equals(s)) {
                return o;
            }
        }
        return null;
    }
}