package raven.anydesk;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public final class AnyDeskCleanupService {

    public static final Path DEFAULT_ANYDESK_DATA_DIR = Path.of("C:\\ProgramData\\AnyDesk");

    public AnyDeskCleanupService() {
    }

    public CleanupResult cleanAnyDeskFolder() {
        return cleanFolder(DEFAULT_ANYDESK_DATA_DIR);
    }

    public CleanupResult cleanFolder(Path dir) {
        if (dir == null) {
            return CleanupResult.skipped("Cleanup path is null.");
        }
        if (!Files.exists(dir)) {
            return CleanupResult.skipped("Nothing to clean (folder not found).");
        }

        try {
            deleteRecursively(dir);
            return CleanupResult.success("AnyDesk traces cleaned.");
        } catch (AccessDeniedException e) {
            return CleanupResult.error("Access denied while cleaning. AnyDesk may still be locking files.", e);
        } catch (IOException e) {
            return CleanupResult.error("Cleanup failed due to I/O error.", e);
        } catch (Exception e) {
            return CleanupResult.error("Cleanup failed.", e);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static final class CleanupResult {
        public final boolean cleaned;
        public final boolean skipped;
        public final String message;
        public final Exception error;

        private CleanupResult(boolean cleaned, boolean skipped, String message, Exception error) {
            this.cleaned = cleaned;
            this.skipped = skipped;
            this.message = message;
            this.error = error;
        }

        public static CleanupResult success(String message) {
            return new CleanupResult(true, false, message, null);
        }

        public static CleanupResult skipped(String message) {
            return new CleanupResult(false, true, message, null);
        }

        public static CleanupResult error(String message, Exception error) {
            return new CleanupResult(false, false, message, error);
        }
    }
}

