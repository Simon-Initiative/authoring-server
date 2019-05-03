package edu.cmu.oli.content.contentfiles;

import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import org.jdom2.Document;


/**
 * @author Raphael Gachuhi
 */
public class PackageXml {

    private Document document;

    private ContentPackage contentPackage;


    public PackageXml(Document document, ContentPackage contentPackage) {
        this.document = document;
        this.contentPackage = contentPackage;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public ContentPackage getContentPackage() {
        return contentPackage;
    }

    public void setContentPackage(ContentPackage contentPackage) {
        this.contentPackage = contentPackage;
    }
}
