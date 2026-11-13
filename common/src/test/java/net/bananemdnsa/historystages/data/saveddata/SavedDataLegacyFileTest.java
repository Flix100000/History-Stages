package net.bananemdnsa.historystages.data.saveddata;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.21.1 kept {@code data/historystages_<name>.dat}; 26.2 reads {@code data/historystages/<name>.dat}.
 * The old file is picked up once and moved, never overwriting a file already in the new place.
 */
class SavedDataLegacyFileTest {

    private static final String LEGACY = "historystages_individual";

    @Test
    void theLegacyFileIsMovedOnce(@TempDir Path data) throws IOException {
        byte[] content = {1, 2, 3, 4};
        Files.write(data.resolve(LEGACY + ".dat"), content);
        Identifier id = SavedDataTypes.id(LEGACY);
        assertEquals("historystages:individual", id.toString());

        SavedDataTypes.migrateLegacyFile(data, LEGACY, id);

        Path moved = data.resolve("historystages").resolve("individual.dat");
        assertTrue(Files.exists(moved));
        assertFalse(Files.exists(data.resolve(LEGACY + ".dat")));
        assertArrayEquals(content, Files.readAllBytes(moved));

        // A second run finds nothing left to move and changes nothing.
        SavedDataTypes.migrateLegacyFile(data, LEGACY, id);
        assertArrayEquals(content, Files.readAllBytes(moved));
    }

    @Test
    void anExistingNewFileIsNeverOverwritten(@TempDir Path data) throws IOException {
        Files.write(data.resolve(LEGACY + ".dat"), new byte[]{9});
        Path current = data.resolve("historystages").resolve("individual.dat");
        Files.createDirectories(current.getParent());
        Files.write(current, new byte[]{7});

        SavedDataTypes.migrateLegacyFile(data, LEGACY, SavedDataTypes.id(LEGACY));

        assertArrayEquals(new byte[]{7}, Files.readAllBytes(current));
        assertTrue(Files.exists(data.resolve(LEGACY + ".dat")), "the old file stays for the admin to sort out");
    }

    @Test
    void noLegacyFileMeansNothingHappens(@TempDir Path data) {
        SavedDataTypes.migrateLegacyFile(data, LEGACY, SavedDataTypes.id(LEGACY));
        assertFalse(Files.exists(data.resolve("historystages")));
    }
}
