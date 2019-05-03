/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018. Carnegie Mellon University
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package edu.cmu.oli.common.xml;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * Generate XML element IDs.
 */
public final class XmlIdGenerator {

    private static final char DELIM = '|';

    private XmlIdGenerator() {
    }

    public static String generateID(String s1) {
        // Generate a hash of the string (concatenation of element name and attributes)
        byte[] md5 = DigestUtils.md5(s1);
        // Base64 encode the result as a URL safe string
        String b64 = Base64.encodeBase64URLSafeString(md5);
        // XML ID must match the NAME production, cannot begin with a digit
        String hash;
        if (Character.isDigit(b64.codePointAt(0))) {
            hash = "_" + b64;
        } else {
            hash = b64;
        }

        return hash;
    }

    public static String generateID(String s1, String s2) {
        StringBuilder s = new StringBuilder();
        s.append(s1).append(DELIM);
        s.append(s2).append(DELIM);
        return generateID(s.toString());
    }

    public static String generateID(String s1, String s2, String s3) {
        StringBuilder s = new StringBuilder();
        s.append(s1).append(DELIM);
        s.append(s2).append(DELIM);
        s.append(s3).append(DELIM);
        return generateID(s.toString());
    }

    public static String generateID(String s1, String s2, String s3, String s4) {
        StringBuilder s = new StringBuilder();
        s.append(s1).append(DELIM);
        s.append(s2).append(DELIM);
        s.append(s3).append(DELIM);
        s.append(s4).append(DELIM);
        return generateID(s.toString());
    }

    public static String generateID(String s1, String s2, String s3, String s4, String s5) {
        StringBuilder s = new StringBuilder();
        s.append(s1).append(DELIM);
        s.append(s2).append(DELIM);
        s.append(s3).append(DELIM);
        s.append(s4).append(DELIM);
        s.append(s5).append(DELIM);
        return generateID(s.toString());
    }

    public static String generateID(String... args) {
        StringBuilder s = new StringBuilder();
        for (String arg : args) {
            s.append(arg).append(DELIM);
        }
        return generateID(s.toString());
    }
}
