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

public class SetMatcher<T> implements ResponseMatcher<Set<T>> {
    private BinarySetOperator operator;
    private Set<T> rightOperand;

    public SetMatcher(BinarySetOperator op, Set<T> rgt) {
        setOperator(op);
        setRightOperand(rgt);
    }

    public BinarySetOperator getOperator() {
        return operator;
    }

    private void setOperator(BinarySetOperator op) {
        if (op == null) {
            throw (new NullPointerException("'op' cannot be null"));
        }
        this.operator = op;
    }

    public Set<T> getRightOperand() {
        return rightOperand;
    }

    private void setRightOperand(Set<T> rgt) {
        if (rgt == null) {
            throw (new NullPointerException("'rgt' cannot be null"));
        }
        this.rightOperand = rgt;
    }

    public boolean matches(Set<T> leftOperand) {
        if (leftOperand == null) return false;
        return operator.evaluate(leftOperand, rightOperand);
    }

    @Override
    public int hashCode() {
        int hashCode = 1;
        hashCode = (hashCode * 7) + operator.hashCode();
        hashCode = (hashCode * 7) + rightOperand.hashCode();
        return hashCode;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (other == null) return false;
        if (!(other instanceof SetMatcher)) return false;

        SetMatcher m = (SetMatcher) other;
        if (!operator.equals(m.operator)) return false;
        if (!rightOperand.equals(m.rightOperand)) return false;
        return true;
    }
}
