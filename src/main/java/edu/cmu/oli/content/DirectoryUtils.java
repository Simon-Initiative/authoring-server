package edu.cmu.oli.content;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.inject.Default;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

/**
 * @author Raphael Gachuhi
 */

@Default
public class DirectoryUtils {

    Logger log = LoggerFactory.getLogger(DirectoryUtils.class);

    public void createDirectories(String p) {
        log.debug("Path to file: " + p);
        Path pathToResource = Paths.get(p).toAbsolutePath().getParent();
        createDirectories(pathToResource);
    }

    public void createDirectories(Path path) {
        log.debug("Create folders: " + path.toString());
        if (Files.isDirectory(path)) {
            return;
        }

        if (Files.isRegularFile(path)) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.error(e.getLocalizedMessage(), e);
            }
        }
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            final String message = "Error while creating directories for path " + path;
            log.error(message, e);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, path.toString(), message);
        }
    }

    public void copyFilesEndingWith(Path sourceDir, Path targetDir, String end) {
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(sourceDir, path -> path.toString().endsWith(end))) {
            paths.forEach(path -> {
                Path targetFile = targetDir.resolve(sourceDir.relativize(path));
                try {
                    Files.copy(path, targetFile);
                } catch (IOException e) {
                }
            });
        } catch (IOException e) {
        }
    }

    public void copyDirectory(Path sourceDir, Path targetDir, boolean skipRoot) throws IOException {
        SimpleFileVisitor<Path> simpleFileVisitor = new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file,
                                             BasicFileAttributes attributes) {

                try {
                    Path targetFile = targetDir.resolve(sourceDir.relativize(file));
                    Files.copy(file, targetFile);
                } catch (IOException ex) {
                    log.error(ex.getMessage(), ex);
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir,
                                                     BasicFileAttributes attributes) {
                if (skipRoot && dir.equals(sourceDir)) {
                    return FileVisitResult.CONTINUE;
                }
                try {
                    Path newDir = targetDir.resolve(sourceDir.relativize(dir));
                    Files.createDirectory(newDir);
                } catch (IOException ex) {
                    log.error(ex.getMessage(), ex);
                }

                return FileVisitResult.CONTINUE;
            }
        };

        Files.walkFileTree(sourceDir, simpleFileVisitor);
    }

    public int deleteDirectory(Path directory) {
        if (!directory.toFile().exists()) {
            return 1;
        }
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }

            });
            return 1;
        } catch (Throwable e) {
            return -1;
        }
    }

    public void directoryPaths(Path directory, Map<String, Set<Path>> filePaths) {
        if (!directory.toFile().exists()) {
            return;
        }
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Set<Path> files = filePaths.get("files");
                    files.add(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Set<Path> directories = filePaths.get("directories");
                    directories.add(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Throwable e) {
        }

    }

    public void saveFile(InputStream inputStream, Path path) throws IOException {
        try (OutputStream outpuStream = Files.newOutputStream(path)) {
            int read = 0;
            byte[] bytes = new byte[1024];
            while ((read = inputStream.read(bytes)) != -1) {
                outpuStream.write(bytes, 0, read);
            }
        }
    }

    public void saveFile(ZipInputStream zis, Path path) throws IOException {
        try (OutputStream outpuStream = Files.newOutputStream(path)) {
            int read = 0;
            byte[] bytes = new byte[1024];
            while ((read = zis.read(bytes)) != -1) {
                outpuStream.write(bytes, 0, read);
            }
        }
    }
}
