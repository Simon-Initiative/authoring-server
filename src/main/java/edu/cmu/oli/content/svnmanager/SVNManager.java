package edu.cmu.oli.content.svnmanager;

import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.logging.Logging;
import org.slf4j.Logger;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnCommit;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Raphael Gachuhi
 */
@Default
public class SVNManager {

    @Inject
    @Logging
    Logger log;

    @Inject
    @ConfigurationCache
    Instance<Configurations> configuration;

    private  SVNClientManager clientManager;
    private  ISVNEventHandler myCommitEventHandler;
    private  ISVNEventHandler myUpdateEventHandler;
    private  ISVNEventHandler myWCEventHandler;

    @PostConstruct
    private void init() {

        setupLibrary();

        /*
         * Creating custom handlers that will process events
         */
        myCommitEventHandler = new CommitEventHandler();

        myUpdateEventHandler = new UpdateEventHandler(new UpdateCallback());

        myWCEventHandler = new WorkingCopyEventHandler();

        /*
         * Creates a default run-time configuration options driver. Default options
         * created in this way use the Subversion run-time configuration area (for
         * instance, on a Windows platform it can be found in the '%APPDATA%\Subversion'
         * directory).
         *
         * readonly = true - not to save  any configuration changes that can be done
         * during the program run to a config file (config settings will only
         * be read to initialize; to enable changes the readonly flag should be set
         * to false).
         *
         * SVNWCUtil is a utility class that creates a default options driver.
         */
        DefaultSVNOptions options = SVNWCUtil.createDefaultOptions(true);
        options.setAuthStorageEnabled(false);
        options.addIgnorePattern("deploy.do");

        /*
         * Creates an instance of SVNClientManager providing authentication
         * information (name, password) and an options driver
         */
        String svnUser = System.getenv().get("svn_user");
        String svnPassword = System.getenv().get("svn_password");

        clientManager = SVNClientManager.newInstance(options, svnUser, svnPassword);

        /*
         * Sets a custom event handler for operations of an SVNCommitClient
         * instance
         */
        clientManager.getCommitClient().setEventHandler(myCommitEventHandler);

        /*
         * Sets a custom event handler for operations of an SVNUpdateClient
         * instance
         */
        clientManager.getUpdateClient().setEventHandler(myUpdateEventHandler);

        /*
         * Sets a custom event handler for operations of an SVNWCClient
         * instance
         */
        clientManager.getWCClient().setEventHandler(myWCEventHandler);
    }

    @PreDestroy
    private void destroy(){
        log.info("destroying the client manager");
        clientManager.dispose();
    }

    private  void setupLibrary() {
        /*
         * For using over http:// and https://
         */
        DAVRepositoryFactory.setup();
    }

    public void createRepository(String svnURL) throws SVNException {
        clientManager.createRepository(SVNURL.parseURIEncoded(svnURL), true);
    }

    public SVNCommitInfo doImport(File path, String dstURL, String commitMessage) throws SVNException {
        return clientManager.getCommitClient().doImport(path, SVNURL.parseURIEncoded(dstURL), commitMessage, (SVNProperties) null, true, true, SVNDepth.fromRecurse(true));
    }

    public SVNCommitInfo copy(String sourceURL, String dstURL, String commitMessage) throws SVNException {
        List<SVNCopySource> svnCopySources = Arrays.asList(new SVNCopySource(SVNRevision.HEAD, SVNRevision.HEAD, SVNURL.parseURIEncoded(sourceURL)));
        SVNCopySource[] svnCopySources1 = svnCopySources.toArray(new SVNCopySource[svnCopySources.size()]);
        SVNURL svnurl = SVNURL.parseURIEncoded(dstURL);
        return clientManager.getCopyClient().doCopy(svnCopySources1, svnurl, false, true, true, commitMessage, null);
    }

    public void doMkDir(Set<String> svnURLs, String message) {
        Set<SVNURL> svnurlSet = svnURLs.stream().map(svnURL -> {
            try {
                return SVNURL.parseURIEncoded(svnURL);
            } catch (SVNException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toSet());
        SVNURL[] svnurls = svnurlSet.toArray(new SVNURL[svnurlSet.size()]);
        try {
            clientManager.getCommitClient().doMkDir(svnurls, message);
        } catch (SVNException e) {
            throw new RuntimeException(e);
        }
    }

    public long doSwitch(File path, String dstURL) throws SVNException {

        return clientManager.getUpdateClient().doSwitch(path, SVNURL.parseURIEncoded(dstURL), SVNRevision.HEAD,
                SVNRevision.HEAD, SVNDepth.getInfinityOrFilesDepth(true), true, false);
    }

    public void diff(File path, String svnURL, OutputStream result) throws SVNException {
        clientManager.getDiffClient().doDiff(path, SVNRevision.HEAD, SVNURL.parseURIEncoded(svnURL), SVNRevision.HEAD, SVNDepth.INFINITY, false, result, new ArrayList<>());
    }

    public Optional<byte[]> fetchRemoteFile(String baseUrl, String filePath) throws SVNException {
        SVNRepository repository = clientManager.createRepository(SVNURL.parseURIEncoded(baseUrl), false);
        SVNNodeKind nodeKind = repository.checkPath(filePath, -1);
        if (!nodeKind.equals(SVNNodeKind.FILE)) {
            return Optional.empty();
        }
        SVNProperties properties = new SVNProperties();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        repository.getFile(filePath, -1, properties, out);

        return Optional.of(out.toByteArray());
    }

    public void checkout(String baseUrl, String target) throws SVNException {
        DefaultSVNOptions options = SVNWCUtil.createDefaultOptions(null, true);
        boolean store = options.isAuthStorageEnabled();
        ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(null,
                System.getenv().get("svn_user"), System.getenv().get("svn_password").toCharArray(), null, null, store);

        SVNURL svnUrl = SVNURL.parseURIEncoded(baseUrl);

        SVNRepository svnRepo = SVNRepositoryFactory.create(svnUrl);
        svnRepo.setAuthenticationManager(authManager);
        SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        svnOperationFactory.setAuthenticationManager(authManager);

        SVNDirEntry entry = svnRepo.info(".", -1);
        long remoteRevision = entry.getRevision();

        final SvnCheckout checkout = svnOperationFactory.createCheckout();
        checkout.setSource(SvnTarget.fromURL(svnUrl));
        checkout.setSingleTarget(SvnTarget.fromFile(Paths.get(target).toFile()));
        remoteRevision = checkout.run();
    }

    /*
     * Committs changes in a working copy to a repository. Like
     * 'svn commit PATH -m "some comment"' command. It's done by invoking
     *
     * SVNCommitClient.doCommit(File[] paths, boolean keepLocks, String commitMessage,
     * boolean force, boolean recursive)
     *
     * which takes the following parameters:
     *
     * paths - working copy paths which changes are to be committed;
     *
     * keepLocks - if true then doCommit(..) won't unlock locked paths; otherwise they will
     * be unlocked after a successful commit;
     *
     * commitMessage - a commit log message;
     *
     * force - if true then a non-recursive commit will be forced anyway;
     *
     * recursive - if true and a path corresponds to a directory then doCommit(..) recursively
     * commits changes for the entire directory, otherwise - only for child entries of the
     * directory;
     */
    public SVNCommitInfo commit(File wcPath, String commitMessage)
            throws SVNException {
        log.info("Committing to svn repo now " + wcPath.getAbsolutePath());
        /*
         * Returns SVNCommitInfo containing information on the new revision committed
         * (revision number, etc.)
         */

        DefaultSVNOptions options = SVNWCUtil.createDefaultOptions(null, true);
        boolean store = options.isAuthStorageEnabled();
        ISVNAuthenticationManager authManager = new SVNAuthManager(null, store,
                System.getenv().get("svn_user"), System.getenv().get("svn_password").toCharArray(), null, null);

        SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        svnOperationFactory.setAuthenticationManager(authManager);

        svnOperationFactory.setEventHandler(myCommitEventHandler);

        SvnCommit commit = svnOperationFactory.createCommit();
        commit.setSingleTarget(SvnTarget.fromFile(wcPath));
        commit.setCommitMessage(commitMessage);
        commit.setKeepLocks(false);
        commit.setKeepChangelists(false);
        commit.setDepth(SVNDepth.fromRecurse(true));
        SVNCommitInfo run = commit.run();
        log.info("Committing to svn repo done " + wcPath.getAbsolutePath());
        return run;
    }

    /*
     * Updates a working copy (brings changes from the repository into the working copy).
     * Like 'svn update PATH' command; It's done by invoking
     *
     * SVNUpdateClient.doUpdate(File file, SVNRevision revision, boolean recursive)
     *
     * which takes the following parameters:
     *
     * file - a working copy entry that is to be updated;
     *
     * revision - a revision to which a working copy is to be updated;
     *
     * recursive - if true and an entry is a directory then doUpdate(..) recursively
     * updates the entire directory, otherwise - only child entries of the directory;
     */
    public Map<String, List<File>> update(File wcPath) throws SVNException {

        SVNUpdateClient updateClient = clientManager.getUpdateClient();
        /*
         * sets externals not to be ignored during the update
         */
        updateClient.setIgnoreExternals(false);
        DefaultSVNOptions options = (DefaultSVNOptions) updateClient.getOptions();
        // Configure a ConflictResolverHandler
        options.setConflictHandler(new ConflictResolverHandler());

        /*
         * returns the number of the revision wcPath was updated to
         */
        updateClient.doUpdate(wcPath, SVNRevision.HEAD, SVNDepth.fromRecurse(true), false, false);

        return ((UpdateEventHandler) this.myUpdateEventHandler).getCallback().getChangedFiles();
    }


    /*
     * Collects status information on local path(s). Like 'svn status (-u) (-N)'
     * command. It's done by invoking
     *
     * SVNStatusClient.doStatus(File path, boolean recursive,
     * boolean remote, boolean reportAll, boolean includeIgnored,
     * boolean collectParentExternals, ISVNStatusHandler handler)
     *
     * which takes the following parameters:
     *
     * path - an entry which status info to be gathered;
     *
     * recursive - if true and an entry is a directory then doStatus(..) collects status
     * info not only for that directory but for each item inside stepping down recursively;
     *
     * remote - if true then doStatus(..) will cover the repository (not only the working copy)
     * as well to find out what entries are out of date;
     *
     * reportAll - if true then doStatus(..) will also include unmodified entries;
     *
     * includeIgnored - if true then doStatus(..) will also include entries being ignored;
     *
     * collectParentExternals - if true then externals definitions won't be ignored;
     *
     * handler - an implementation of ISVNStatusHandler to process status info per each entry
     * doStatus(..) traverses; such info is collected in an SVNStatus object and
     * is passed to a handler's handleStatus(SVNStatus status) method where an implementor
     * decides what to do with it.
     */
    public SVNStatus showStatus(File path, boolean remote)
            throws SVNException {
        /*
         * StatusHandler displays status information for each entry in the console (in the
         * manner of the native Subversion command line client)
         */

        //clientManager.getLookClient().doGetChanged();

        return clientManager.getStatusClient().doStatus(path, remote);
    }

    public boolean isVersioned(File path) {
        try {
            return clientManager.getStatusClient().doStatus(path, false).isVersioned();
        } catch (SVNException e) {
            return false;
        }
    }

    public List<File> listModifiedFiles(File path) throws SVNException {
        SVNClientManager svnClientManager = SVNClientManager.newInstance();
        try {
            final List<File> fileList = new ArrayList<File>();
            svnClientManager.getStatusClient().doStatus(path, SVNRevision.HEAD, SVNDepth.INFINITY, false, false, false, false, status -> {
                SVNStatusType statusType = status.getContentsStatus();
                if (statusType != SVNStatusType.STATUS_NONE && statusType != SVNStatusType.STATUS_NORMAL
                        && statusType != SVNStatusType.STATUS_IGNORED) {
                    fileList.add(status.getFile());
                }
            }, null);
            return fileList;
        }finally {
            log.info("destroying listModifiedFiles client manager");
            svnClientManager.dispose();
        }
    }

    public List<File> listAddedFiles(File path) throws SVNException {
        SVNClientManager svnClientManager = SVNClientManager.newInstance();
        try {

        final List<File> fileList = new ArrayList<>();
        svnClientManager.getStatusClient().doStatus(path, SVNRevision.HEAD, SVNDepth.INFINITY, false, false, false, false, status -> {
            SVNStatusType statusType = status.getContentsStatus();
            if (statusType == SVNStatusType.STATUS_NONE) {
                fileList.add(status.getFile());
            }
        }, null);

        return fileList;
        } finally {
            log.info("destroying listAddedFiles client manager");
            svnClientManager.dispose();
        }
    }

    /*
     * Collects information on local path(s). Like 'svn info (-R)' command.
     * It's done by invoking
     *
     * SVNWCClient.doInfo(File path, SVNRevision revision,
     * boolean recursive, ISVNInfoHandler handler)
     *
     * which takes the following parameters:
     *
     * path - a local entry for which info will be collected;
     *
     * revision - a revision of an entry which info is interested in; if it's not
     * WORKING then info is got from a repository;
     *
     * recursive - if true and an entry is a directory then doInfo(..) collects info
     * not only for that directory but for each item inside stepping down recursively;
     *
     * handler - an implementation of ISVNInfoHandler to process info per each entry
     * doInfo(..) traverses; such info is collected in an SVNInfo object and
     * is passed to a handler's handleInfo(SVNInfo info) method where an implementor
     * decides what to do with it.
     */
    public void showInfo(File wcPath, boolean isRecursive, ISVNInfoHandler handler) throws SVNException {
        /*
         * InfoHandler displays information for each entry in the console (in the manner of
         * the native Subversion command line client)
         */
        clientManager.getWCClient().doInfo(wcPath, SVNRevision.UNDEFINED, SVNRevision.WORKING, SVNDepth.getInfinityOrEmptyDepth(isRecursive), (Collection) null, handler);
    }

    /*
     * Puts directories and files under version control scheduling them for addition
     * to a repository. They will be added in a next commit. Like 'svn add PATH'
     * command. It's done by invoking
     *
     * SVNWCClient.doAdd(File path, boolean force,
     * boolean mkdir, boolean climbUnversionedParents, boolean recursive)
     *
     * which takes the following parameters:
     *
     * path - an entry to be scheduled for addition;
     *
     * force - set to true to force an addition of an entry anyway;
     *
     * mkdir - if true doAdd(..) creates an empty directory at path and schedules
     * it for addition, like 'svn mkdir PATH' command;
     *
     * climbUnversionedParents - if true and the parent of the entry to be scheduled
     * for addition is not under version control, then doAdd(..) automatically schedules
     * the parent for addition, too;
     *
     * recursive - if true and an entry is a directory then doAdd(..) recursively
     * schedules all its inner dir entries for addition as well.
     */
    public void addEntry(File path) throws SVNException {
        File[] paths = new File[]{path};
        clientManager.getWCClient().doAdd(paths, false, false, false, SVNDepth.fromRecurse(true), false, false, false);
    }

    /*
     * Locks working copy paths, so that no other user can commit changes to them.
     * Like 'svn lock PATH' command. It's done by invoking
     *
     * SVNWCClient.doLock(File[] paths, boolean stealLock, String lockMessage)
     *
     * which takes the following parameters:
     *
     * paths - an array of local entries to be locked;
     *
     * stealLock - set to true to steal the lock from another user or working copy;
     *
     * lockMessage - an optional lock comment string.
     */
    public void lock(File wcPath, boolean isStealLock, String lockComment) throws SVNException {
        clientManager.getWCClient().doLock(new File[]{wcPath}, isStealLock, lockComment);
    }

    public void unlock(File wcPath, boolean force) throws SVNException {
        clientManager.getWCClient().doUnlock(new File[]{wcPath}, force);
    }

    /*
     * Schedules directories and files for deletion from version control upon the next
     * commit (locally). Like 'svn delete PATH' command. It's done by invoking
     *
     * SVNWCClient.doDelete(File path, boolean force, boolean dryRun)
     *
     * which takes the following parameters:
     *
     * path - an entry to be scheduled for deletion;
     *
     * force - a boolean flag which is set to true to force a deletion even if an entry
     * has local modifications;
     *
     * dryRun - set to true not to delete an entry but to check if it can be deleted;
     * if false - then it's a deletion itself.
     */
    public void delete(File wcPath, boolean force) throws SVNException {
        clientManager.getWCClient().doDelete(wcPath, force, false);
    }

    public void revert(File wcPath) throws SVNException {
        clientManager.getWCClient().doRevert(new File[]{wcPath}, SVNDepth.INFINITY, null);
    }

    public void cleanup(File wcPath) throws SVNException {
        clientManager.getWCClient().doCleanup(wcPath, true);
    }

}
