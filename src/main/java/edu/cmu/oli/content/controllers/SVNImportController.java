package edu.cmu.oli.content.controllers;

import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.ContentServiceException;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import edu.cmu.oli.content.security.AppSecurityController;
import edu.cmu.oli.content.security.Secure;
import edu.cmu.oli.content.svnmanager.SVNManager;
import org.jboss.ejb3.annotation.TransactionTimeout;
import org.slf4j.Logger;
import org.tmatesoft.svn.core.SVNException;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Raphael Gachuhi
 */

@Stateless
public class SVNImportController {

    @Inject
    @Logging
    Logger log;

    @Inject
    @Secure
    AppSecurityController securityManager;

    @Inject
    @ConfigurationCache
    Instance<Configurations> configuration;

    @Inject
    Instance<SVNManager> svnManagerInstance;


    // :FIXME: avoid use of global state, not good for horizontal scaling. Switch to using a distributed cache
    public static Map<String, ContentPackage> importPending = new ConcurrentHashMap<>();

    public Optional<String> fetchRemotePackageXmlFile(String baseUrl) {
        SVNManager svnManager = svnManagerInstance.get();
        String filePath = "content/package.xml";
        try {
            Optional<byte[]> bytes = svnManager.fetchRemoteFile(baseUrl, filePath);
            if (!bytes.isPresent() || bytes.get().length == 0) {
                return Optional.empty();
            }
            return Optional.of(new String(bytes.get(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return Optional.empty();

    }

    @TransactionTimeout(unit = TimeUnit.MINUTES, value = 50L)
    public String processImport(String baseUrl, ContentPackage newContentPackage, String preferredUsername) throws ContentServiceException {
        baseUrl = createEditorBranch(baseUrl, newContentPackage.getVersion());
        String packageId = newContentPackage.getId();
        String packageVersion = newContentPackage.getVersion();
        String shallowId = packageId + "-" + packageVersion;
        log.info("ProcessImport " + packageId + "_" + packageVersion);

        String sourceLocation = this.configuration.get().getContentSourceXml();

        Path targetDir = Paths.get(sourceLocation + File.separator + packageId + "_" + AppUtils.generateUID(12));
        while (Files.exists(targetDir)) {
            targetDir = Paths.get(sourceLocation + File.separator + packageId + "_" + AppUtils.generateUID(12));
        }
        try {
            Files.deleteIfExists(targetDir);
            Files.createDirectory(targetDir);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        try {
            svnManagerInstance.get().checkout(baseUrl, targetDir.toAbsolutePath().toString());
        } catch (Throwable e) {
            SVNImportController.importPending.remove(shallowId);
            throw new ContentServiceException(e.getMessage(), e);
        }
        log.info("Checkout Done " + packageId + "_" + packageVersion);
        ProcessImport packageProcessor = null;
        try {
            packageProcessor = (ProcessImport)
                    new InitialContext().lookup("java:global/content-service/ProcessImport");
            packageProcessor.processPackage(newContentPackage, targetDir, preferredUsername, shallowId);
        } catch (NamingException e) {
            throw new ContentServiceException(e.getMessage(), e);
        }

        return baseUrl;
    }

    private void branchRemoteSvnRepo(String fromURL, String toURL) throws ContentServiceException {
        try {
            String commitMessage = "Publisher: copying repositories " + fromURL + " to " + toURL;
            log.info("Creating SVN repository: " + toURL);
            svnManagerInstance.get().copy(fromURL, toURL, commitMessage);
        } catch (SVNException e) {
            log.error("SVN Repository Copy Error: ", e);
            throw new ContentServiceException(e.getMessage(), e);
        }
    }

    public String createEditorBranch(String oldRepoUrl, String version) throws ContentServiceException {
        if (oldRepoUrl.endsWith("/")) {
            oldRepoUrl = oldRepoUrl.substring(0, oldRepoUrl.length() - 1);
        }

        String serverurl = System.getenv().get("SERVER_URL");
        String serverName = serverurl.substring(serverurl.indexOf("/") + 2, serverurl.indexOf("."));
        String vString = version.replaceAll("\\.", "_");

        String branchURL;
        if (oldRepoUrl.endsWith("/trunk")) {
            branchURL = oldRepoUrl.substring(0, oldRepoUrl.lastIndexOf("/trunk")) + "/branches/v_" + vString + "-" + serverName;
        } else if (oldRepoUrl.contains("/branches/")) {
            branchURL = oldRepoUrl.substring(0, oldRepoUrl.lastIndexOf("/branches/")) + "/branches/v_" + vString + "-" + serverName;
        } else if (oldRepoUrl.contains("/tags/")) {
            branchURL = oldRepoUrl.substring(0, oldRepoUrl.lastIndexOf("/tags/")) + "/branches/v_" + vString + "-" + serverName;
        } else {
            throw new ContentServiceException("Imported repository must be from either an svn trunk, branch or tag");
        }

        // Does the branch already exist?
        Optional<String> packageXmlFile = fetchRemotePackageXmlFile(branchURL);
        if (!packageXmlFile.isPresent()) {
            branchRemoteSvnRepo(oldRepoUrl, branchURL);
        }

        return branchURL;
    }

    public String createSvnTag(String oldRepoUrl, String version, String purpose) throws ContentServiceException {
        if (oldRepoUrl.endsWith("/")) {
            oldRepoUrl = oldRepoUrl.substring(0, oldRepoUrl.length() - 1);
        }

        String serverurl = System.getenv().get("SERVER_URL");
        String serverName = serverurl.substring(serverurl.indexOf("/") + 2, serverurl.indexOf("."));
        String vString = version.replaceAll("\\.", "_");

        String currentDate = new SimpleDateFormat("yyyy-MM-dd-HH").format(new Date());

        String tagString = purpose == null ? "v_" + vString + "-" + serverName + "-" + currentDate : "v_" + vString + "-" + serverName + "-" + purpose + "-" + currentDate;

        String tagURL;
        if (oldRepoUrl.endsWith("/trunk")) {
            tagURL = oldRepoUrl.substring(0, oldRepoUrl.lastIndexOf("/trunk")) + "/tags/" + tagString;
        } else if (oldRepoUrl.contains("/branches/")) {
            tagURL = oldRepoUrl.substring(0, oldRepoUrl.lastIndexOf("/branches/")) + "/tags/" + tagString;
        } else if (oldRepoUrl.contains("/tags/")) {
            tagURL = oldRepoUrl.substring(0, oldRepoUrl.lastIndexOf("/tags/")) + "/tags/" + tagString;
        } else {
            throw new ContentServiceException("Tagged repository must be from either an svn trunk, branch or tag");
        }

        // Does the tag already exist?
        Optional<String> packageXmlFile = fetchRemotePackageXmlFile(tagURL);
        if (!packageXmlFile.isPresent()) {
            branchRemoteSvnRepo(oldRepoUrl, tagURL);
        }

        return tagURL;
    }
}
