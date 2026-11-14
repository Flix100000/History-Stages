package net.bananemdnsa.historystages;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Where the mod's source lives since the port: common, the two loader modules, and whatever still
 * waits in port-pending. A source guard that only walked {@code common/src/main/java} would let a
 * loader module or a not-yet-moved file break its rule unseen.
 */
public final class SourceRoots {

    private static final Path MAIN_JAVA = Path.of("src", "main", "java");

    private SourceRoots() {}

    /**
     * Every existing copy of {@code relative} (a path under {@code src/main/java}, as the guards
     * write it, run from the common project) across all source roots.
     */
    public static List<Path> all(Path relative) {
        Path inside = MAIN_JAVA.relativize(relative);
        List<Path> out = new ArrayList<>();
        for (Path root : List.of(MAIN_JAVA, Path.of("..", "neoforge", "src", "main", "java"),
                Path.of("..", "fabric", "src", "main", "java"), Path.of("..", "port-pending", "java"))) {
            Path candidate = root.resolve(inside);
            if (Files.isDirectory(candidate)) out.add(candidate);
        }
        return out;
    }

    /** {@code Files.walk} over every copy of {@code relative}; close it like the plain one. */
    public static Stream<Path> walk(Path relative) throws IOException {
        List<Stream<Path>> streams = new ArrayList<>();
        try {
            for (Path dir : all(relative)) streams.add(Files.walk(dir));
        } catch (IOException e) {
            streams.forEach(Stream::close);
            throw e;
        }
        return streams.stream().flatMap(s -> s).onClose(() -> streams.forEach(Stream::close));
    }
}
