package edu.cmu.oli.content.resource.builders;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Methods for parsing a content package manifest from XML. The parser assumes
 * compliance with the OLI content package DTD. Both the simplified and complete
 * package manifest specifications are supported.
 *
 * @author Raphael Gachuhi
 */
public final class ContentPkgXmlReader {
    // Metadata and preference namespaces

    private static final Namespace _METADATA_NS = Namespace.getNamespace("cmd",
            "http://oli.web.cmu.edu/content/metadata/");

    private static final Namespace _PREFERENCES_NS = Namespace.getNamespace("pref",
            "http://oli.web.cmu.edu/preferences/");

    private static final Logger log = LoggerFactory.getLogger(ContentPkgXmlReader.class);

    // =======================================================================
    // Private constructors
    // =======================================================================
    private ContentPkgXmlReader() {
    }

    // =======================================================================
    // Public static methods
    // =======================================================================

    /**
     * Parses the supplied document into a simplified content package manifest. This
     * method assumes that the supplied XML has been validated against the OLI
     * simple content package DTD.
     *
     * @param mnfstDoc manifest document
     * @return content package manifest
     * @throws NullPointerException if <tt>mnfstDoc</tt> is <tt>null</tt>
     * @throws BuildException       if the content package manifest is not valid
     */
    public static JsonObject documentToSimpleManifest(Document mnfstDoc) throws BuildException {

        // Parse common package markup
        Element pkgElmnt = mnfstDoc.getRootElement();
        return parsePackageElement(pkgElmnt);
    }

    // =======================================================================
    // Private static methods
    // =======================================================================
    private static JsonObject parsePackageElement(Element pkgElmnt) throws BuildException {

        // Does root element have correct local name?
        if (!"package".equals(pkgElmnt.getName())) {
            final StringBuilder message = new StringBuilder();
            message.append("Invalid manifest, root element must be named ");
            message.append("'package': name=").append(pkgElmnt.getName());
            log.error(message.toString());
            throw new BuildException(message.toString());
        }

        // Package ID and version
        String pkgId = pkgElmnt.getAttributeValue("id");
        String version = pkgElmnt.getAttributeValue("version");
        // ContentPackage mnfst = new ContentPackage(pkgId, version);
        JsonObject mnfstBuilder = new JsonObject();

        mnfstBuilder.addProperty("@id", pkgId);
        mnfstBuilder.addProperty("@version", version);

        // Title
        Element pkgTitleElmnt = pkgElmnt.getChild("title");
        mnfstBuilder.addProperty("title", pkgTitleElmnt == null ? null : pkgTitleElmnt.getTextNormalize());

        // Description
        Element dscrElmnt = pkgElmnt.getChild("description");
        mnfstBuilder.addProperty("description", dscrElmnt == null ? null : dscrElmnt.getTextNormalize());

        // Metadata
        Element pkgMdElmnt = pkgElmnt.getChild("metadata", _METADATA_NS);
        JsonElement elementToMetadata = elementToMetadata(pkgMdElmnt);
        mnfstBuilder.add("metadata", elementToMetadata);

        // Icon
        Element iconElmnt = pkgElmnt.getChild("icon");
        if (iconElmnt != null) {
            String iconHref = iconElmnt.getAttributeValue("href");
            mnfstBuilder.addProperty("icon", iconHref);
        }

        // Preferences
        Element prefsElmnt = pkgElmnt.getChild("preferences", _PREFERENCES_NS);
        JsonElement elementToPreferenceSet = elementToPreferenceSet(prefsElmnt);
        mnfstBuilder.add("preferences", elementToPreferenceSet);

        // Language
        Element pkgLanguageElmnt = pkgElmnt.getChild("language");
        mnfstBuilder.addProperty("language", pkgLanguageElmnt == null ? null : pkgLanguageElmnt.getTextNormalize());

        return mnfstBuilder;
    }

    private static JsonElement elementToMetadata(Element mdElmnt) {
        return MetadataReader.elementToMetadata(mdElmnt, _METADATA_NS);
    }

    private static JsonElement elementToPreferenceSet(Element prefsElmnt) {
        return OptionsReader.elementToPreferenceSet(prefsElmnt, _PREFERENCES_NS);
    }

}
