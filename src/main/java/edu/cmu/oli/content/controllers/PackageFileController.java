package edu.cmu.oli.content.controllers;

import com.google.gson.Gson;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.ResourceException;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import edu.cmu.oli.content.resource.builders.ContentPkgXmlWriter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;

import javax.enterprise.inject.Default;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

@Default
public class PackageFileController {

    @Inject
    @Logging
    Logger log;

    public void updatePackageXmlIdAndVersion(Path pkgXml, String id, String version, String sTitle) {
        //Process package file
        // Define output format: compact with tab indent
        Format format = Format.getCompactFormat();
        format.setIndent("\t");
        SAXBuilder builder = AppUtils.validatingSaxBuilder();
        builder.setExpandEntities(false);
        Document document;
        try {
            document = builder.build(pkgXml.toFile());
        } catch (JDOMException | IOException e) {
            final String message = "error while parsing package.xml file from " + pkgXml.toString();
            log.error(message, e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, id + "_" + version, message);
        }
        Element rootElement = document.getRootElement();
        rootElement.setAttribute("id", id);
        rootElement.setAttribute("version", version);
        Element title = rootElement.getChild("title");
        title.setText(sTitle);

        XMLOutputter xmlOut = new XMLOutputter(format);
        StringWriter stWriter = new StringWriter();
        try {
            xmlOut.output(document, stWriter);
        } catch (IOException e) {
            log.debug("Exception writing document to string");
        }

        try {
            Files.write(pkgXml, stWriter.toString().getBytes());
        } catch (IOException e) {
            final String message = "error while writing package.xml file " + pkgXml.toString();
            log.error(message, e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, id + "_" + version, message);
        }
    }

    public void updateOrganizationXmlIdAndVersion(Path orgXml, String id, String version) {
        SAXBuilder builder = AppUtils.validatingSaxBuilder();
        builder.setExpandEntities(false);
        Document document;
        Element rootElement;
        StringWriter stWriter;
        // Process organization File
        try {
            document = builder.build(orgXml.toFile());
        } catch (JDOMException | IOException e) {
            final String message = "error while parsing organization.xml file from " + orgXml.toString();
            log.error(message, e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, id + "_" + version, message);
        }
        rootElement = document.getRootElement();
        rootElement.setAttribute("id", id);
        rootElement.setAttribute("version", "1.0");

        Format format = Format.getCompactFormat();
        format.setIndent("\t");
        XMLOutputter xmlOut = new XMLOutputter(format);
        stWriter = new StringWriter();
        try {
            xmlOut.output(document, stWriter);
        } catch (IOException e) {
            log.debug("Error creating string from document");
        }

        try {
            Files.write(orgXml, stWriter.toString().getBytes());
        } catch (IOException e) {
            final String message = "error while writing organization.xml file " + orgXml.toString();
            log.error(message, e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, id + "_" + version, message);
        }
    }

    public void updatePackgeJson(String packageId, ContentPackage contentPackage) {
        // Update package.json file
        String p = contentPackage.getVolumeLocation() + File.separator + contentPackage.getFileNode().getPathTo();

        final Path pathToContentFolder = FileSystems.getDefault().getPath(p.substring(0, p.lastIndexOf(File.separator)));
        try {
            Files.createDirectories(pathToContentFolder);
        } catch (IOException e) {
            final String message = "error while saving" + p + " from content package" + contentPackage.getId() + "_" + contentPackage.getVersion()
                    + "\n " + e.getMessage();
            log.error(message, e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, packageId, message);
        }

        // Write out the json file
        Gson gson = AppUtils.gsonBuilder().setPrettyPrinting().create();
        String jsonString = gson.toJson(contentPackage.getDoc().getJsonObject());
        Path pathToPackageFile = FileSystems.getDefault().getPath(p);
        try {
            Files.write(pathToPackageFile, jsonString.getBytes());
        } catch (IOException e) {
            final String message = "Error: unable to write package.json file located at - " + p;
            log.error(message, e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, packageId, message);
        }
    }

    public void updatePackgeXmlFile(ContentPackage contentPackage) {
        Document document = ContentPkgXmlWriter.manifestToDocument(contentPackage);
        String packageXml = new XMLOutputter(Format.getPrettyFormat()).outputString(document);
        String pathFrom = contentPackage.getSourceLocation() + File.separator + "content/package.xml";

        final Path pathToResource = FileSystems.getDefault().getPath(pathFrom.substring(0, pathFrom.lastIndexOf(File.separator)));
        try {
            Files.createDirectories(pathToResource);
        } catch (IOException e) {
            final String message = "Error while creating directories for path " + pathFrom;
            log.error(message, e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, pathFrom, message);
        }

        Path pathFromResourceFile = FileSystems.getDefault().getPath(pathFrom);
        try {

            Files.write(pathFromResourceFile, packageXml.getBytes());
        } catch (IOException e) {
            final String message = "Error: unable to write package xml file located at - " + pathFrom;
            log.error(message, e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, contentPackage.getId() + "-v" + contentPackage.getVersion(), message);
        }

    }

    public boolean fileExists(Path path) {
        return Files.exists(path);
    }

}