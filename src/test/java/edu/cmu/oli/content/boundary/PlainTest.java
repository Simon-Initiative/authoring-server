package edu.cmu.oli.content.boundary;

import edu.cmu.oli.content.analytics.DatasetBuilder;
import org.jdom2.JDOMException;
import org.junit.Test;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.Assert.assertTrue;

/**
 * @author Raphael Gachuhi
 */
public class PlainTest {

    @Test
    public void testNothing() throws JDOMException, IOException {

        String val = "After sorting ints: ";
        MessageDigest messageDigest = null;
        try {
            messageDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        messageDigest.update(val.getBytes());
        String encryptedString = convertByteArrayToHexString(messageDigest.digest());
        System.out.println("After sorting ints: " + encryptedString);
        assertTrue(true);
    }

    private static String convertByteArrayToHexString(byte[] arrayBytes) {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < arrayBytes.length; i++) {
            stringBuffer.append(Integer.toString((arrayBytes[i] & 0xff) + 0x100, 16)
                    .substring(1));
        }
        return stringBuffer.toString();
    }

}
