/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package edu.cmu.oli.content.svnmanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.SVNInfo;

/*
 * An implementation of ISVNInfoHandler that is  used  in  WorkingCopy.java  to
 * display  info  on  a  working  copy path.  This implementation is passed  to
 *
 * SVNWCClient.doInfo(File path, SVNRevision revision, boolean recursive,
 * ISVNInfoHandler handler)
 *
 * For each item to be processed doInfo(..) collects information and creates an
 * SVNInfo which keeps that information. Then  doInfo(..)  calls  implementor's
 * handler.handleInfo(SVNInfo) where it passes the gathered info.
 */
public class InfoHandler implements ISVNInfoHandler {
    private final Logger log = LoggerFactory.getLogger(InfoHandler.class);

    /*
     * This is an implementation  of  ISVNInfoHandler.handleInfo(SVNInfo info).
     * Just prints out information on a Working Copy path in the manner of  the
     * native SVN command line client.
     */
    public void handleInfo(SVNInfo info) {
        log.debug("-----------------INFO-----------------");
        log.debug("Local Path: " + info.getFile().getPath());
        log.debug("URL: " + info.getURL());
        if (info.isRemote() && info.getRepositoryRootURL() != null) {
            log.debug("Repository Root URL: "
                    + info.getRepositoryRootURL());
        }
        if (info.getRepositoryUUID() != null) {
            log.debug("Repository UUID: " + info.getRepositoryUUID());
        }
        log.debug("Revision: " + info.getRevision().getNumber());
        log.debug("FNode Kind: " + info.getKind().toString());
        if (!info.isRemote()) {
            log.debug("Schedule: "
                    + (info.getSchedule() != null ? info.getSchedule() : "normal"));
        }
        log.debug("Last Changed Author: " + info.getAuthor());
        log.debug("Last Changed Revision: "
                + info.getCommittedRevision().getNumber());
        log.debug("Last Changed Date: " + info.getCommittedDate());
        if (info.getPropTime() != null) {
            log.debug("Properties Last Updated: " + info.getPropTime());
        }
        if (info.getKind() == SVNNodeKind.FILE && info.getChecksum() != null) {
            if (info.getTextTime() != null) {
                log.debug("Text Last Updated: " + info.getTextTime());
            }
            log.debug("Checksum: " + info.getChecksum());
        }
        if (info.getLock() != null) {
            if (info.getLock().getID() != null) {
                log.debug("Lock Token: " + info.getLock().getID());
            }
            log.debug("Lock Owner: " + info.getLock().getOwner());
            log.debug("Lock Created: "
                    + info.getLock().getCreationDate());
            if (info.getLock().getExpirationDate() != null) {
                log.debug("Lock Expires: "
                        + info.getLock().getExpirationDate());
            }
            if (info.getLock().getComment() != null) {
                log.debug("Lock Comment: "
                        + info.getLock().getComment());
            }
        }
    }
}