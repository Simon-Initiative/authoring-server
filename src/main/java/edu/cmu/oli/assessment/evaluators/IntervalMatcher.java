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

public class IntervalMatcher<T extends Comparable<T>> implements ResponseMatcher<T> {
    private T lowerBound;
    private boolean openLower;
    private T upperBound;
    private boolean openUpper;

    public IntervalMatcher() {
        return;
    }

    public T getLowerBound() {
        return lowerBound;
    }

    public void setLowerBound(T lower) {
        if (!consistentBounds(lower, upperBound)) {
            throw (new IllegalArgumentException("lower bound must be <= upper bound"));
        }
        this.lowerBound = lower;
    }

    public boolean getLowerBoundOpen() {
        return openLower;
    }

    public void setLowerBoundOpen(boolean open) {
        this.openLower = open;
    }

    public T getUpperBound() {
        return upperBound;
    }

    public void setUpperBound(T upper) {
        if (!consistentBounds(lowerBound, upper)) {
            throw (new IllegalArgumentException("upper bound must be >= lower bound"));
        }
        this.upperBound = upper;
    }

    public boolean getUpperBoundOpen() {
        return openUpper;
    }

    public void setUpperBoundOpen(boolean open) {
        this.openUpper = open;
    }

    public void setBounds(T lower, T upper) {
        if (!consistentBounds(lower, upper)) {
            throw (new IllegalArgumentException("lower bound must be <= upper bound"));
        }
        this.lowerBound = lower;
        this.upperBound = upper;
    }

    public boolean matches(T response) {
        if (response == null) return false;

        if (lowerBound != null) {
            int l = response.compareTo(lowerBound);

            if (openLower) {
                if (l <= 0) {
                    return false;
                }
            } else {
                if (l < 0) {
                    return false;
                }
            }
        }

        if (upperBound != null) {
            int u = response.compareTo(upperBound);

            if (openUpper) {
                if (u >= 0) {
                    return false;
                }
            } else {
                if (u > 0) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = 1;
        hashCode = (hashCode * 7) + (lowerBound == null ? 0 : lowerBound.hashCode());
        hashCode = (hashCode * 7) + (openLower ? 1 : 0);
        hashCode = (hashCode * 7) + (upperBound == null ? 0 : upperBound.hashCode());
        hashCode = (hashCode * 7) + (openUpper ? 1 : 0);
        return hashCode;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (other == null) return false;
        if (!(other instanceof IntervalMatcher)) return false;

        IntervalMatcher m = (IntervalMatcher) other;

        if (lowerBound == null) {
            if (m.lowerBound != null) {
                return false;
            }
        } else if (!lowerBound.equals(m.lowerBound)) {
            return false;
        }
        if (openLower != m.openLower) return false;

        if (upperBound == null) {
            if (m.upperBound != null) {
                return false;
            }
        } else if (!upperBound.equals(m.upperBound)) {
            return false;
        }
        if (openUpper != m.openUpper) return false;

        return true;
    }

    private boolean consistentBounds(T lower, T upper) {
        if (lower != null && upper != null) {
            return (lower.compareTo(upper) <= 0);
        } else {
            return true;
        }
    }
}
