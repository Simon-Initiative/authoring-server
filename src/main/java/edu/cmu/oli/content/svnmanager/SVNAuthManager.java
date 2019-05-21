package edu.cmu.oli.content.svnmanager;

import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.io.SVNRepository;

import java.io.File;

public class SVNAuthManager extends DefaultSVNAuthenticationManager {

    public SVNAuthManager(File configDirectory, boolean storeAuth, String userName, char[] password, File privateKey, char[] passphrase) {
        super(configDirectory, storeAuth, userName, password, privateKey, passphrase);
    }

    public int getReadTimeout(SVNRepository repository) {
        return 600000;
    }

    public int getConnectTimeout(SVNRepository repository) {
        return 600000;
    }
}
