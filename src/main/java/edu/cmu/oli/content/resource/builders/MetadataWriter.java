package edu.cmu.oli.content.resource.builders;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jdom2.Element;
import org.jdom2.Namespace;


/**
 * Methods for generating content metadata XML.
 *
 * @author Raphael Gachuhi
 */
public final class MetadataWriter {

    // =======================================================================
    // Private constructors
    // =======================================================================
    private MetadataWriter() {
    }

    // =======================================================================
    // Public static methods
    // =======================================================================

    /**
     * <p>
     * Converts the given metadata to an XML element. The result XML conforms to
     * the OLI metadata DTD.</p>
     *
     * @param md metadata
     * @return XML element representation of the metadata
     * @throws NullPointerException if <tt>md</tt> is <tt>null</tt>
     */
    public static Element metadataToElement(JsonElement md) {
        return metadataToElement(md, Namespace.NO_NAMESPACE);
    }

    /**
     * <p>
     * Converts the given metadata to an XML element in the specified namespace.
     * The result XML conforms to the OLI metadata DTD.</p>
     *
     * @param mdi metadata
     * @param nsi XML namespace
     * @return XML element representation of the metadata
     * @throws NullPointerException if either argument is <tt>null</tt>
     */
    public static Element metadataToElement(JsonElement mdi, Namespace nsi) {
        if (mdi == null || mdi.isJsonNull() || mdi.isJsonPrimitive()) {
            return null;
        }
        if (nsi == null) {
            nsi = Namespace.NO_NAMESPACE;
        }

        final Namespace ns = nsi;
        JsonObject md = (JsonObject) mdi;

        Element mdElmnt = new Element("metadata", ns);

        // Authors
        JsonArray jsonArray = md.getAsJsonArray("authors");
        if (jsonArray != null) {
            jsonArray.forEach((jv) -> {
                Element authorElmnt = new Element("author", ns);
                authorElmnt.setText(jv.toString());
                mdElmnt.addContent(authorElmnt);
            });
        }

        // License
        String license = md.has("license") ? md.get("license").getAsString() : null;
        if (license != null) {
            Element licenseElmnt = new Element("license", ns);
            licenseElmnt.setText(license);
            mdElmnt.addContent(licenseElmnt);
        }

        // Copyright
        String copyright = md.has("copyright") ? md.get("copyright").getAsString() : null;
        if (copyright != null) {
            Element copyElmnt = new Element("copyright", ns);
            copyElmnt.setText(copyright);
            mdElmnt.addContent(copyElmnt);
        }

        // Keywords
        String keywords = md.has("keywords") ? md.get("keywords").getAsString() : null;
        if (keywords != null) {
            Element keywordsElmnt = new Element("keywords", ns);
            keywordsElmnt.setText(keywords);
            mdElmnt.addContent(keywordsElmnt);
        }

        return mdElmnt;
    }
}
