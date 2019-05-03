package edu.cmu.oli.content.resource.builders;

import com.google.gson.*;
import org.jdom2.Element;
import org.jdom2.Namespace;

import java.util.List;

/**
 * Methods for parsing content metadata XML.
 *
 * @author Raphael Gachuhi
 */
public final class MetadataReader {

    // =======================================================================
    // Private constructor
    // =======================================================================
    private MetadataReader() {
    }

    // =======================================================================
    // Public static methods
    // =======================================================================

    /**
     * Parses the supplied element into content metadata. This method assumes
     * that the supplied XML has been validated against the OLI content metadata
     * DTD.
     *
     * @param mdElmnt
     * @return
     */
    public static JsonElement elementToMetadata(Element mdElmnt) {
        return elementToMetadata(mdElmnt, Namespace.NO_NAMESPACE);
    }

    /**
     * <p>
     * Parses the supplied element into content metadata. This method assumes
     * that the supplied XML has been validated against the OLI content metadata
     * DTD.</p>
     *
     * @param mdElmnt
     * @param ns
     * @return
     */
    public static JsonElement elementToMetadata(Element mdElmnt, Namespace ns) {
        if (mdElmnt == null) {
            return JsonNull.INSTANCE;
        } else if (ns == null) {
            ns = Namespace.NO_NAMESPACE;
        }
        JsonObject metaDataBuilder = new JsonObject();

        //Metadata md = new Metadata();
        // Authors
        final List<Element> author = mdElmnt.getChildren("author", ns);
        JsonArray authorsArrayBuilder = new JsonArray();
        for (Element authorElmnt : author) {
            authorsArrayBuilder.add(new JsonPrimitive(authorElmnt.getTextNormalize()));
        }
        metaDataBuilder.add("authors", authorsArrayBuilder);

        // License
        Element licenseElmnt = mdElmnt.getChild("license", ns);
        metaDataBuilder.addProperty("license", licenseElmnt == null ? null : licenseElmnt.getTextNormalize());

        // Copyright
        Element copyrightElmnt = mdElmnt.getChild("copyright", ns);
        metaDataBuilder.addProperty("copyright", copyrightElmnt == null ? null : copyrightElmnt.getTextNormalize());

        // Keywords
        Element keywordsElmnt = mdElmnt.getChild("keywords", ns);
        metaDataBuilder.addProperty("keywords", keywordsElmnt == null ? null : keywordsElmnt.getTextNormalize());

        return metaDataBuilder;
    }
}
