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

public enum InequalityOperator {
    LT("<") {
        public <C extends Comparable<C>> boolean evaluate(C lft, C rgt) {
            return (lft.compareTo(rgt) < 0);
        }

    },

    LTE("<=") {
        public <C extends Comparable<C>> boolean evaluate(C lft, C rgt) {
            return (lft.compareTo(rgt) <= 0);
        }

    },

    EQ("=") {
        public <C extends Comparable<C>> boolean evaluate(C lft, C rgt) {
            return (lft.compareTo(rgt) == 0);
        }

    },

    GTE(">=") {
        public <C extends Comparable<C>> boolean evaluate(C lft, C rgt) {
            return (lft.compareTo(rgt) >= 0);
        }

    },

    GT(">") {
        public <C extends Comparable<C>> boolean evaluate(C lft, C rgt) {
            return (lft.compareTo(rgt) > 0);
        }

    },

    NE("!=") {
        public <C extends Comparable<C>> boolean evaluate(C lft, C rgt) {
            return (lft.compareTo(rgt) != 0);
        }

    };

    private final String symbol;

    InequalityOperator(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    @Override
    public String toString() {
        return getSymbol();
    }

    public abstract <C extends Comparable<C>> boolean evaluate(C lft, C rgt);

    public static InequalityOperator findBySymbol(String s) {
        for (InequalityOperator o : values()) {
            if (o.getSymbol().equals(s)) {
                return o;
            }
        }
        return null;
    }
}