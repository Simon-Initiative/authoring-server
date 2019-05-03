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

import edu.cmu.oli.assessment.InputFormatException;

public enum NumericNotation {
    AUTOMATIC {
        public String getPattern() {
            return "^[+-]?(([0-9]+\\.?[0-9]*)|(\\.?[0-9]+))([eE][+-]?[0-9]+)?$";
        }

        public String getErrorMessage() {
            return "oli.assessment2.input.invalid.notation.automatic";
        }
    },

    DECIMAL {
        public String getPattern() {
            return "^[+-]?(([0-9]+\\.?[0-9]*)|(\\.?[0-9]+))$";
        }

        public String getErrorMessage() {
            return "oli.assessment2.input.invalid.notation.decimal";
        }
    },

    SCIENTIFIC {
        public String getPattern() {
            return "^((0(\\.0+)?([eE]0)?)|([+-]?[1-9](\\.[0-9]+)?[eE](0|[+-]?[1-9][0-9]*)))$";
        }

        public String getErrorMessage() {
            return "oli.assessment2.input.invalid.notation.scientific";
        }
    };

    public abstract String getPattern();

    public abstract String getErrorMessage();

    public void validateInputValue(String inputValue) throws InputFormatException {
        if (inputValue != null) {
            if (!inputValue.trim().matches(getPattern())) {
                throw new InputFormatException(getErrorMessage());
            }
        }
    }
}
