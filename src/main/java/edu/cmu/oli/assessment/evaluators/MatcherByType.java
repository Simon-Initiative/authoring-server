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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import edu.cmu.oli.assessment.InputFormatException;
import edu.cmu.oli.assessment.InteractionStyle;
import edu.cmu.oli.assessment.PatternFormatException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

/**
 * @author Raphael Gachuhi
 */
public class MatcherByType {
    JsonObject interaction;

    public MatcherByType(JsonObject interaction) {
        this.interaction = interaction;
    }

    public ResponseMatcher parseMatchPattern(String pattern)
            throws PatternFormatException {

        // No response
        if (pattern == null)
            return NoResponseMatcher.getInstance();

        InteractionStyle style = InteractionStyle.valueOf(interaction.get("style").getAsString());

        // Any response
        if ("*".equals(pattern))
            return AnyResponseMatcher.getInstance();

        switch (style) {
            case TEXT:
            case ESSAY:
            case SHORT_ANSWER:
                TextResponseMatcher textResponseMatcher = (TextResponseMatcher) textType(pattern);
                if(interaction.has("caseSensitive")){
                    textResponseMatcher.setCaseSensitive(interaction.get("caseSensitive").getAsBoolean());
                }
                return textResponseMatcher;
            case ORDERING:
                return ordering(pattern);
            case NUMERIC:
                return numeric(pattern);
            case IMAGE_HOTSPOT:
                return imageHotSpot(pattern);
            case MULTIPLE_CHOICE:
            case FILL_IN_THE_BLANK:
                return multipleChoice(pattern);

        }

        return null;
    }


    /**
     * Ordering
     *
     * @return
     * @throws PatternFormatException
     */
    private ResponseMatcher<? super List<String>> ordering(String pattern) throws PatternFormatException {

        // Wildcard
        validateWildcardPattern(pattern);
        return new WildcardMatcher(pattern);
    }

    /**
     * Text
     *
     * @return
     * @throws PatternFormatException
     */
    private ResponseMatcher<? super String> textType(String pattern) throws PatternFormatException {

        // Text
        TextResponseMatcher t;
        if (isRegularExpression(pattern)) {
            // Regular expression
            String regex = patternToRegularExpression(pattern);
            try {
                t = new PatternMatcher(regex);
            } catch (PatternSyntaxException ex) {
                String message = "invalid regular expression: " + pattern;
                throw (new PatternFormatException(message, ex));
            }
        } else {
            // String
            t = new StringMatcher(pattern);
        }

        t.setCaseSensitive(true);
        return t;
    }

    /**
     * Numeric
     *
     * @return
     * @throws PatternFormatException
     */
    private ResponseMatcher<? super BigDecimal> numeric(String pattern)
            throws PatternFormatException {

        // Precision
        int p = pattern.indexOf("#");
        if (p >= 0) {
            int precision;
            try {
                precision = Integer.parseInt(pattern.substring(p + 1));
            } catch (NumberFormatException ex) {
                String message = "invalid precision: " + pattern;
                throw (new PatternFormatException(message, ex));
            }
            PrecisionMatcher m = new PrecisionMatcher(precision);
            if (p == 0) {
                return m;
            } else {
                return new CompositeMatcher<BigDecimal>(
                        parseMatchPattern(pattern.substring(0, p)), m);
            }
        }

        // Interval
        int n = pattern.length();
        int first = pattern.charAt(0);
        int last = pattern.charAt(n - 1);

        if ((first == '(' || first == '[') && (last == ')' || last == ']')) {
            IntervalMatcher<BigDecimal> m = new IntervalMatcher<BigDecimal>();
            int c = pattern.indexOf(",");

            // Lower bound
            String lower = pattern.substring(1, c).trim();
            if (!"".equals(lower)) {
                try {
                    m.setLowerBound(new BigDecimal(lower));
                } catch (NumberFormatException ex) {
                    String message = "invalid lower bound: " + pattern;
                    throw (new PatternFormatException(message, ex));
                }
            }
            m.setLowerBoundOpen(first == '(');

            // Upper bound
            String upper = pattern.substring(c + 1, n - 1).trim();
            if (!"".equals(upper)) {
                try {
                    m.setUpperBound(new BigDecimal(upper));
                } catch (NumberFormatException ex) {
                    String message = "invalid upper bound: " + pattern;
                    throw (new PatternFormatException(message, ex));
                }
            }
            m.setUpperBoundOpen(last == ')');

            return m;
        }

        // Inequality
        int digit = 0;
        while (digit < n && !isNumericCharacter(pattern.charAt(digit))) {
            digit++;
        }

        // Parse inequality operator
        InequalityOperator operator = InequalityOperator.EQ;
        if (digit > 0) {
            String symbol = pattern.substring(0, digit).trim();
            operator = InequalityOperator.findBySymbol(symbol);
            if (operator == null) {
                String message = "unknown inequality operator: " + pattern;
                throw (new PatternFormatException(message));
            }
        }

        // Parse right operand
        String value = pattern.substring(digit).trim();
        BigDecimal rightOperand;
        try {
            rightOperand = new BigDecimal(value);
        } catch (NumberFormatException ex) {
            String message = "invalid match pattern: " + pattern;
            throw new PatternFormatException(message, ex);
        }

        return new InequalityMatcher<BigDecimal>(operator, rightOperand);
    }


    /**
     * ImageHotSpot
     *
     * @return
     * @throws PatternFormatException
     */
    private ResponseMatcher<? super Set<String>> imageHotSpot(String pattern)
            throws PatternFormatException {

        // Set operator
        int i = pattern.indexOf("{");
        int n = pattern.length();
        if (i > 0 && pattern.charAt(n - 1) == '}') {
            // Parse set operator
            String symbol = pattern.substring(0, i).trim();
            BinarySetOperator operator = BinarySetOperator.findBySymbol(symbol);
            if (operator == null) {
                String message = "unknown set operator: " + pattern;
                throw (new PatternFormatException(message));
            }

            // Parse right operand
            String values = pattern.substring(i + 1, n - 1);

            Set<String> rightOperand;
            try {
                rightOperand = parseIdentifierSet(values);
            } catch (InputFormatException ex) {
                String message = "invalid match pattern: " + pattern;
                throw (new PatternFormatException(message, ex));
            }

            // Create set matcher
            return new SetMatcher<String>(operator, rightOperand);
        }

        // Equality
        Set<String> rightOperand;
        try {
            rightOperand = parseIdentifierSet(pattern);
        } catch (InputFormatException ex) {
            String message = "invalid match pattern: " + pattern;
            throw (new PatternFormatException(message, ex));
        }
        return new EqualityMatcher<Set<String>>(rightOperand);
    }

    /**
     * MultipleChoice
     *
     * @return
     * @throws PatternFormatException
     */
    private ResponseMatcher<? super Set<String>> multipleChoice(String pattern)
            throws PatternFormatException {

        // Set operator
        int i = pattern.indexOf("{");
        int n = pattern.length();
        if (i > 0 && pattern.charAt(n - 1) == '}') {
            // Parse set operator
            String symbol = pattern.substring(0, i).trim();
            BinarySetOperator operator = BinarySetOperator.findBySymbol(symbol);
            if (operator == null) {
                String message = "invalid match pattern, unknown set operator: " + pattern;
                throw (new PatternFormatException(message));
            }

            // Parse right operand
            String values = pattern.substring(i + 1, n - 1);

            Set<String> rightOperand;
            try {
                rightOperand = parseIdentifierSetMultipleChoice(values);
            } catch (InputFormatException ex) {
                String message = "invalid match pattern, no such choice: " + pattern;
                throw (new PatternFormatException(message, ex));
            }

            // Create set matcher
            return new SetMatcher<String>(operator, rightOperand);
        }

        // Equality
        Set<String> rightOperand;
        try {
            rightOperand = parseIdentifierSetMultipleChoice(pattern);
        } catch (InputFormatException ex) {
            String message = "invalid match pattern, no such choice: " + pattern;
            throw (new PatternFormatException(message, ex));
        }
        return new EqualityMatcher<Set<String>>(rightOperand);
    }

    private Set<String> parseIdentifierSetMultipleChoice(String inputValue)
            throws InputFormatException {

        Set<String> identifiers = new HashSet<String>();
        String[] values = inputValue.split(",");
        for (String value : values) {
            String trimmed = value.trim();
            if (getChoice(trimmed) == null) {
                String message = "oli.assessment2.input.invalid.selection";
                throw (new InputFormatException(message));
            }
            identifiers.add(trimmed);
        }
        return identifiers;
    }

    private void validateWildcardPattern(String pattern)
            throws PatternFormatException {

        String[] tokens = pattern.split(",");
        for (String token : tokens) {
            String trimmed = token.trim();
            if (WildcardMatcher.isWildcardOperator(trimmed)) {
                continue;
            } else if (getChoice(trimmed) == null) {
                String message = "pattern contains invalid choice: " + pattern;
                throw (new PatternFormatException(message));
            }
        }
    }

    private JsonElement getChoice(String value) {
        if (value == null) {
            throw new NullPointerException("'value' cannot be null");
        }
        JsonArray choices = interaction.getAsJsonArray("choices");
        for (JsonElement c : choices) {
            if (c.getAsJsonObject().get("value").getAsString().equals(value)) {
                return c;
            }
        }
        return null;
    }

    private Set<String> parseIdentifierSet(String inputValue)
            throws InputFormatException {

        Set<String> identifiers = new HashSet<String>();
        String[] values = inputValue.split(",");
        for (String value : values) {
            String trimmed = value.trim();
            if (getHotspot(trimmed) == null) {
                String message = "oli.assessment2.input.invalid.selection";
                throw (new InputFormatException(message));
            }
            identifiers.add(trimmed);
        }
        return identifiers;
    }

    private JsonElement getHotspot(String value) {
        if (value == null) {
            throw new NullPointerException("'value' cannot be null");
        }

        JsonArray hotspots = interaction.getAsJsonArray("hotspots");
        for (JsonElement c : hotspots) {
            if (c.getAsJsonObject().get("value").getAsString().equals(value)) {
                return c;
            }
        }
        return null;
    }

    private boolean isRegularExpression(String pattern) {
        // Regular expression cannot be null
        if (pattern == null) return false;

        // Regular expression are contained within forward slashes
        int n = pattern.length();
        if (n < 2) {
            return false;
        } else if (pattern.charAt(0) != '/') {
            return false;
        } else if (pattern.charAt(n - 1) != '/') {
            return false;
        }
        return true;
    }

    private String patternToRegularExpression(String pattern) {
        // Regular expression are contained within forward slashes
        return pattern.substring(1, pattern.length() - 1);
    }

    private boolean isNumericCharacter(char c) {
        return (Character.isDigit(c) || c == '+' || c == '-' || c == '.');
    }

    public Object parseInputValue(String inputValue) throws InputFormatException {

        InteractionStyle style = InteractionStyle.valueOf(interaction.get("style").getAsString());

        switch (style) {
            case TEXT:
            case ESSAY:
            case SHORT_ANSWER:
                return parseTextInput(inputValue);
            case ORDERING:
                return parseOrderingInput(inputValue);
            case NUMERIC:
                return parseNumericInput(inputValue);
            case IMAGE_HOTSPOT:
                return parseImageHotSpotInput(inputValue);
            case MULTIPLE_CHOICE:
            case FILL_IN_THE_BLANK:
                return parseMultipleChoiceInput(inputValue);

        }

        return null;
    }

    public Set<String> parseImageHotSpotInput(String inputValue)
            throws InputFormatException {

        if (inputValue == null) {
            return null;
        }
        if ("".equals(inputValue)) {
            return null;
        }
        return parseIdentifierSet(inputValue);
    }

    public Set<String> parseMultipleChoiceInput(String inputValue)
            throws InputFormatException {

        if (inputValue == null) {
            return null;
        }
        if ("".equals(inputValue)) {
            return null;
        }
        return parseIdentifierSetMultipleChoice(inputValue);
    }

    public BigDecimal parseNumericInput(String inputValue)
            throws InputFormatException {

        // Validate input

        NumericNotation.AUTOMATIC.validateInputValue(inputValue);

        // No response
        if (inputValue == null)
            return null;

        // Parse number from input
        try {
            return new BigDecimal(inputValue.trim());
        } catch (NumberFormatException ex) {
            //log.error("parseInputValue(): invalid number: " + inputValue);
            String message = "oli.assessment2.input.invalid.number";
            throw (new InputFormatException(message, ex));
        }
    }

    public List<String> parseOrderingInput(String inputValue)
            throws InputFormatException {

        if (inputValue == null) return null;
        return parseIdentifierList(inputValue);
    }

    private List<String> parseIdentifierList(String inputValue)
            throws InputFormatException {

        int n = interaction.getAsJsonArray("choices").size();
        String[] identifiers = new String[n];

        // Does the input define ordinals?
        boolean ordinals = inputValue.contains("=");

        // For each component of the ordering...
        String[] values = inputValue.split(",");
        for (int i = 0, m = values.length; i < m; i++) {
            String value = values[i];
            int ordinal = i;

            // Use ordinals given in input string?
            if (ordinals) {
                // Split identifier/ordinal pair
                String[] p = values[i].split("=");
                if (p.length != 2) {
                    String message = "oli.assessment2.input.invalid.syntax";
                    throw (new InputFormatException(message));
                }

                // Value
                value = p[0];

                // Ordinal
                try {
                    ordinal = Integer.parseInt(p[1].trim()) - 1;
                } catch (NumberFormatException ex) {
                    String message = "oli.assessment2.input.invalid.ordinal";
                    throw (new InputFormatException(message, ex));
                }
                if (ordinal < 0 || ordinal >= n) {
                    String message = "oli.assessment2.input.invalid.ordinal";
                    throw (new InputFormatException(message));
                }
            }

            // Is identifier a valid choice?
            if (getChoice(value) == null) {
                String message = "oli.assessment2.input.invalid.selection";
                throw (new InputFormatException(message));
            }

            // Check for duplicate ordinals
            if (identifiers[ordinal] != null) {
                String message = "oli.assessment2.input.duplicate.ordinal";
                throw (new InputFormatException(message));
            }

            // Add identifier at correct position
            identifiers[ordinal] = value.trim();
        }

        // Compact ordering, check for duplicate choices
        List<String> compact = new ArrayList<String>(values.length);
        for (String id : identifiers) {
            // Skip empty positions in partial ordering
            if (id == null) continue;

            // Check for duplicate choices
            if (compact.contains(id)) {
                String message = "oli.assessment2.input.duplicate.selection";
                throw (new InputFormatException(message));
            }

            // Add identifier to compact ordering
            compact.add(id);
        }

        return compact;
    }

    public String parseTextInput(String inputValue) {
        // Apply whitespace strategy to input
        WhitespaceStrategy ws = WhitespaceStrategy.NORMALIZE;
        return ws.apply(inputValue);
    }
}
