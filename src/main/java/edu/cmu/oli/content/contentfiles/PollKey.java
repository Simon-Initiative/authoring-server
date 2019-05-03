package edu.cmu.oli.content.contentfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * @author Raphael Gachuhi
 */
public class PollKey {

    final Path directoryPath;

    Set<Directive> pathSet = new HashSet<>();

    public PollKey(Path directoryPath) {
        this.directoryPath = directoryPath;
    }

    public void addPath(Directive path) {
        pathSet.add(path);
    }

    public Stream<Directive> pathStream() {
        return pathSet.stream();
    }

    public boolean reset() {
        pathSet.clear();
        return Files.isDirectory(directoryPath) && Files.isWritable(directoryPath);
    }

    public boolean isEmpty() {
        return pathSet.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PollKey pollKey = (PollKey) o;

        return directoryPath.equals(pollKey.directoryPath);
    }

    @Override
    public int hashCode() {
        return directoryPath.hashCode();
    }

}
