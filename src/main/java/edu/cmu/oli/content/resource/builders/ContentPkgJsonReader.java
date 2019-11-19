package edu.cmu.oli.content.resource.builders;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.ws.rs.core.Response;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.ResourceException;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import edu.cmu.oli.content.models.persistance.entities.ErrorLevel;
import edu.cmu.oli.content.models.persistance.entities.FileNode;
import edu.cmu.oli.content.models.persistance.entities.WebContent;

/**
 * Methods for parsing a content package manifest from JSON.
 *
 * @author Raphael Gachuhi
 */
public final class ContentPkgJsonReader {
    private static final Logger log = LoggerFactory.getLogger(ContentPkgJsonReader.class);

    // =======================================================================
    // Private constructors
    // =======================================================================
    private ContentPkgJsonReader() {
    }

    // =======================================================================
    // Public static methods
    // =======================================================================

    public static void parsePackageJson(ContentPackage mnfst, JsonObject pkgJson, boolean update) {
        // Package ID and version
        String pkgId = pkgJson.has("@id") ? pkgJson.get("@id").getAsString() : null;
        String version = pkgJson.has("@version") ? pkgJson.get("@version").getAsString() : null;
        if (pkgId == null || pkgId.isEmpty() || version == null || version.isEmpty()) {
            String message = "Package Json must contain a valid resource id and version  ";
            log.error(message);
            throw new ResourceException(Response.Status.BAD_REQUEST, "package", message);
        }
        String title = pkgJson.has("title") ? pkgJson.get("title").getAsString() : null;

        JsonObject errors = new JsonObject();
        errors.addProperty("contentPackageErrors", pkgId + "_" + version);
        JsonArray errorList = new JsonArray();
        errors.add("errorList", errorList);
        if (title == null || title.isEmpty()) {
            JsonObject err = new com.google.gson.JsonObject();
            err.addProperty("source", pkgId + "_" + version);
            err.addProperty("level", ErrorLevel.WARN.toString());
            err.addProperty("message", "Content Package title missing");
            errorList.add(err);
        }
        mnfst.setId(pkgId);
        mnfst.setVersion(version);
        if (!update) {
            String volumeLocation = File.separator + "oli" + File.separator + "course_content_volume";
            volumeLocation = volumeLocation + File.separator + mnfst.getId() + "_" + AppUtils.generateUID(12);
            while (Files.exists(Paths.get(volumeLocation))) {
                volumeLocation = volumeLocation + File.separator + mnfst.getId() + "_" + AppUtils.generateUID(12);
            }
            mnfst.setVolumeLocation(volumeLocation);
        }
        mnfst.setTitle(title);
        mnfst.setDescription(pkgJson.has("description") ? pkgJson.get("description").getAsString() : null);
        JsonElement metadata = pkgJson.get("metadata");
        mnfst.setMetadata(new JsonWrapper(metadata));
        JsonElement preferences = pkgJson.get("preferences");
        mnfst.setOptions(new JsonWrapper(preferences));

        // JsonWrapper misc = mnfst.getMisc();
        // JsonObject miscInfo = new JsonObject();
        // if (misc != null) {
        // miscInfo = misc.getJsonObject().getAsJsonObject();
        // }
        // miscInfo.addProperty("language", "en-US");
        // mnfst.setMisc(new JsonWrapper(miscInfo));

        JsonObject defaultMisc = new JsonObject();
        defaultMisc.addProperty("language", "en_US");
        JsonElement misc = pkgJson.get("misc").getAsJsonObject();
        mnfst.setMisc(new JsonWrapper(misc == null ? defaultMisc : misc));

        mnfst.setErrors(new JsonWrapper(errors));

        if (pkgJson.has("icon")) {
            String iconHref = null;
            JsonElement iconJson = pkgJson.get("icon");
            if (iconJson.isJsonObject()) {
                if (pkgJson.get("icon").getAsJsonObject().has("fileNode")) {
                    iconHref = pkgJson.get("icon").getAsJsonObject().getAsJsonObject("fileNode").get("pathTo")
                            .getAsString();
                }
            } else if (iconJson.isJsonPrimitive()) {
                iconHref = pkgJson.get("icon").getAsString();
            }

            if (iconHref != null && !iconHref.isEmpty()) {
                WebContent oldIcon = mnfst.getIcon();
                if (!(oldIcon != null && oldIcon.getFileNode().getPathTo().equals(iconHref))) {
                    FileNode iconFile = new FileNode(mnfst.getWebContentVolume(), iconHref, iconHref, "undetermined");
                    WebContent icon = new WebContent();
                    icon.setFileNode(iconFile);
                    mnfst.setIcon(icon);
                }
            }
        }
    }
}
