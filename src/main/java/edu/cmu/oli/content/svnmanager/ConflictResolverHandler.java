/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.cmu.oli.content.svnmanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.*;

public class ConflictResolverHandler implements ISVNConflictHandler {

    private final Logger log = LoggerFactory.getLogger(ConflictResolverHandler.class);

    public ConflictResolverHandler() {
    }

    @Override
    public SVNConflictResult handleConflict(SVNConflictDescription svncd) throws SVNException {
        SVNConflictReason reason = svncd.getConflictReason();
        SVNMergeFileSet mergeFiles = svncd.getMergeFiles();

        // SVNConflictChoice choice;
        // if (deploy) {
        // choice = SVNConflictChoice.THEIRS_FULL;
        // } else {
        // choice = SVNConflictChoice.MINE_FULL;
        // }

        return new SVNConflictResult(SVNConflictChoice.THEIRS_FULL, mergeFiles.getResultFile());
    }
}
