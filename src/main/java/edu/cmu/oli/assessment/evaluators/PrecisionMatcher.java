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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

public class PrecisionMatcher implements ResponseMatcher<BigDecimal> {
    static final Logger log = LoggerFactory.getLogger(PrecisionMatcher.class);
    private int precision;

    public PrecisionMatcher(int precision) {
        setPrecision(precision);
    }

    public PrecisionMatcher(PrecisionMatcher other) {
        if (other == null) {
            throw (new NullPointerException("'other' cannot be null"));
        }
        setPrecision(other.getPrecision());
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        if (precision <= 0) {
            throw (new IllegalArgumentException("'precision' must be greater than zero"));
        }
        this.precision = precision;
    }

    public boolean matches(BigDecimal response) {
        log.info("value= "+ response + "match precision:= "+response.precision() +" vs " + precision + " vs scale= " + response.scale());
        if (response == null) return false;
        return (precision == response.precision());
    }

    @Override
    public int hashCode() {
        return precision;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (other == null) return false;
        if (!(other instanceof PrecisionMatcher)) return false;

        PrecisionMatcher m = (PrecisionMatcher) other;
        return (precision == m.getPrecision());
    }
}