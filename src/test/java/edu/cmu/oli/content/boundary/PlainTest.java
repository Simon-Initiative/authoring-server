package edu.cmu.oli.content.boundary;

import edu.cmu.oli.content.analytics.DatasetBuilder;
import edu.cmu.oli.content.contentfiles.readers.A2ToJsonTest;
import org.jdom2.JDOMException;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

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

        System.out.println("Time zones in GMT:");
        List<String> gmt = getTimeZoneList(OffsetBase.GMT);
        for (String timeZone : gmt) {
            System.out.println(timeZone);
        }
    }

    private static String convertByteArrayToHexString(byte[] arrayBytes) {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < arrayBytes.length; i++) {
            stringBuffer.append(Integer.toString((arrayBytes[i] & 0xff) + 0x100, 16)
                    .substring(1));
        }
        return stringBuffer.toString();
    }

    public enum OffsetBase {
        GMT, UTC
    }

    public List<String> getTimeZoneList(OffsetBase base) {
        String[] availableZoneIds = TimeZone.getAvailableIDs();
        List<String> result = new ArrayList<>(availableZoneIds.length);

        for (String zoneId : availableZoneIds) {
            TimeZone curTimeZone = TimeZone.getTimeZone(zoneId);

            String offset = calculateOffset(curTimeZone.getRawOffset());

            result.add(String.format("(%s%s) %s", base, offset, zoneId ));
        }

        Collections.sort(result);

        return result;
    }

    private String calculateOffset(int rawOffset) {
        if (rawOffset == 0) {
            return "+00:00";
        }
        long hours = TimeUnit.MILLISECONDS.toHours(rawOffset);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(rawOffset);
        minutes = Math.abs(minutes - TimeUnit.HOURS.toMinutes(hours));

        return String.format("%+03d:%02d", hours, Math.abs(minutes));
    }
}
