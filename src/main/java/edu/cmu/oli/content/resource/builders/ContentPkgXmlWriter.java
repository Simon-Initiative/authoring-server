package edu.cmu.oli.content.resource.builders;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.jdom2.DocType;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import edu.cmu.oli.content.models.persistance.entities.WebContent;

/**
 * Methods for generating content package manifest XML.
 *
 * @author Raphael Gachuhi
 */
public final class ContentPkgXmlWriter {

    private static final String _PUBLIC_ID = "-//Carnegie Mellon University//DTD Content Package Simple 2.0//EN";
    private static final String _SYSTEM_ID = "http://oli.cmu.edu/dtd/oli_content_package_simple_2_0.dtd";
    // Metadata and preference namespaces

    private static final Namespace _METADATA_NS = Namespace.getNamespace("cmd",
            "http://oli.web.cmu.edu/content/metadata/");

    private static final Namespace _PREFERENCES_NS = Namespace.getNamespace("pref",
            "http://oli.web.cmu.edu/preferences/");

    private static final Logger log = LoggerFactory.getLogger(ContentPkgXmlWriter.class);

    // =======================================================================
    // Private constructors
    // =======================================================================
    private ContentPkgXmlWriter() {
    }

    // =======================================================================
    // Public static methods
    // =======================================================================

    /**
     * <p>
     * Converts the given content package manifest to an XML document and writes the
     * document to the specified file. The resulting XML will validate to the OLI
     * content package DTD, provided the package was constructed in accordance with
     * the general contract of said DTD.
     *
     * @param mnfst   content package manifest
     * @param dstFile destination file
     * @throws NullPointerException if either argument is <tt>null</tt>
     * @throws IOException          if an error occurs while outputting the document
     */
    public static void manifestToFile(ContentPackage mnfst, java.io.File dstFile) throws IOException {

        try ( // Convert package and output document
                FileOutputStream fos = new FileOutputStream(dstFile)) {
            manifestToStream(mnfst, fos);
        }
    }

    /**
     * <p>
     * Converts the given content package manifest to an XML document and writes the
     * document to the specified output stream. The resulting XML will validate to
     * the OLI content package DTD, provided the package was constructed in
     * accordance with the general contract of said DTD.
     *
     * @param mnfst content package manifest
     * @param os    output stream
     * @throws NullPointerException if either argument is <tt>null</tt>
     * @throws IOException          if an error occurs while outputting the document
     */
    public static void manifestToStream(ContentPackage mnfst, OutputStream os) throws IOException {

        if (mnfst == null) {
            throw (new NullPointerException("'mnfst' cannot be null"));
        } else if (os == null) {
            throw (new NullPointerException("'os' cannot be null"));
        }

        // Define output format: compact with tab indent
        Format format = Format.getCompactFormat();
        format.setIndent("\t");
        XMLOutputter xmlOut = new XMLOutputter(format);

        // Convert package and output document
        xmlOut.output(manifestToDocument(mnfst), os);
    }

    /**
     * <p>
     * Converts the given content package manifest to an XML document. The resulting
     * XML will validate to the OLI content package DTD, provided the package was
     * constructed in accordance with the general contract of said DTD.
     *
     * @param contentPkg content package manifest
     * @return XML document representation of the content package manifest
     * @throws NullPointerException if <tt>mnfst</tt> is <tt>null</tt>
     */
    public static Document manifestToDocument(ContentPackage contentPkg) {

        if (contentPkg == null) {
            throw new NullPointerException("'ContentPackage' cannot be null");
        }

        // Create the document
        Document mnfstDoc = new Document();
        // Document type
        DocType docType = new DocType("package", _PUBLIC_ID, _SYSTEM_ID);
        mnfstDoc.setDocType(docType);

        mnfstDoc.setRootElement(manifestToElement(contentPkg));

        return mnfstDoc;
    }

    // =======================================================================
    // Private static methods
    // =======================================================================
    private static Element manifestToElement(ContentPackage contentPkg) {

        Element pkgElmnt = new Element("package");// mnfstDoc.getRootElement();

        // ContentPackage mnfst = parsePackageJson(pkgElmnt);

        // Package ID and version
        pkgElmnt.setAttribute("id", contentPkg.getId());
        pkgElmnt.setAttribute("version", contentPkg.getVersion());

        // Title
        if (contentPkg.getTitle() != null) {
            Element pkgTitleElmnt = new Element("title");
            pkgTitleElmnt.setText(contentPkg.getTitle());
            pkgElmnt.addContent(pkgTitleElmnt);
        }

        // Description
        if (contentPkg.getDescription() != null) {
            Element dscrElmnt = new Element("description");
            dscrElmnt.setText(contentPkg.getDescription());
            pkgElmnt.addContent(dscrElmnt);
        }

        // Metadata
        if (contentPkg.getMetadata() != null) {
            Element pkgMdElmnt = MetadataWriter.metadataToElement(contentPkg.getMetadata().getJsonObject(),
                    _METADATA_NS);
            if (pkgMdElmnt != null) {
                pkgElmnt.addContent(pkgMdElmnt);
            }
        }

        WebContent icon = contentPkg.getIcon();
        if (icon != null) {
            Element iconElmnt = new Element("icon");
            iconElmnt.setAttribute("href", icon.getFileNode().getPathTo());
            pkgElmnt.addContent(iconElmnt);
        }

        // Preferences
        if (contentPkg.getOptions() != null) {
            Element prefsElmnt = OptionsWriter.preferenceSetToElement(contentPkg.getOptions().getJsonObject(),
                    _PREFERENCES_NS);
            if (prefsElmnt != null) {
                pkgElmnt.addContent(prefsElmnt);
            }
        }

        return pkgElmnt;
    }
}
