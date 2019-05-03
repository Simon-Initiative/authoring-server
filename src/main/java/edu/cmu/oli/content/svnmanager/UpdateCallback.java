package edu.cmu.oli.content.svnmanager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Raphael Gachuhi
 */
public class UpdateCallback {

    private Map<String, List<File>> changedFiles = new HashMap<>();

    public void addFile(String changeType, File file) {
        List<File> files = changedFiles.get(changeType);
        if (files == null) {
            files = new ArrayList<>();
            changedFiles.put(changeType, files);
        }
        files.add(file);
    }

    public Map<String, List<File>> getChangedFiles() {
        Map<String, List<File>> changes = new HashMap<>();
        changes.putAll(changedFiles);
        changedFiles = new HashMap<>();
        return changes;
    }
}
