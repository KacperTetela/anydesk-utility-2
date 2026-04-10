package raven.anydesk;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class AnyDeskLauncher {

    private static final String EXE_NAME = "anydesk.exe";

    private AnyDeskLauncher() {
    }

    public static Optional<Path> resolveExecutable() {
        Optional<Path> fromPath = resolveFromSystemPath();
        if (fromPath.isPresent()) {
            return fromPath;
        }
        return resolveFromCommonLocations();
    }

    public static Process launch(String anyDeskId) throws Exception {
        String id = normalizeId(anyDeskId);
        Path exe = resolveExecutable().orElseThrow(() ->
                new FileNotFoundException("Could not find " + EXE_NAME + " in PATH or common install locations."));

        ProcessBuilder pb = new ProcessBuilder(exe.toString(), id);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    public static String normalizeId(String raw) {
        if (raw == null) {
            return "";
        }
        // AnyDesk IDs are commonly shown with spaces for readability; CLI accepts the raw ID.
        // We remove spaces to avoid accidental formatting issues.
        return raw.trim().replace(" ", "");
    }

    private static Optional<Path> resolveFromSystemPath() {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        String[] parts = path.split(";");
        for (String p : parts) {
            if (p == null || p.isBlank()) {
                continue;
            }
            Path candidate = Paths.get(p.trim(), EXE_NAME);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> resolveFromCommonLocations() {
        for (Path p : commonWindowsCandidates()) {
            if (Files.isRegularFile(p)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private static List<Path> commonWindowsCandidates() {
        List<Path> list = new ArrayList<>();

        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        String localAppData = System.getenv("LOCALAPPDATA");

        // Common installs
        addIfNotNull(list, programFiles, "AnyDesk", EXE_NAME);
        addIfNotNull(list, programFilesX86, "AnyDesk", EXE_NAME);

        // Some installs or portable copies end up under LocalAppData
        addIfNotNull(list, localAppData, "AnyDesk", EXE_NAME);
        addIfNotNull(list, localAppData, "Programs", "AnyDesk", EXE_NAME);

        // Fallback portable guess (best-effort)
        addIfNotNull(list, "C:\\ProgramData", "AnyDesk", EXE_NAME);

        // De-dupe by normalized string (case-insensitive windows)
        List<Path> deduped = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Path p : list) {
            String key = p.toString().toLowerCase(Locale.ROOT);
            if (!seen.contains(key)) {
                seen.add(key);
                deduped.add(p);
            }
        }
        return deduped;
    }

    private static void addIfNotNull(List<Path> list, String base, String... more) {
        if (base == null || base.isBlank()) {
            return;
        }
        Path p = Paths.get(base, more);
        list.add(p);
    }
}

