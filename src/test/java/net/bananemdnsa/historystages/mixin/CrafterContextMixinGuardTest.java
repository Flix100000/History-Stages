package net.bananemdnsa.historystages.mixin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A station that names its crafter may not do it with {@code @Redirect}.
 *
 * <p>Mixin lets exactly one redirector own a call site. Polymorph redirects the very same
 * {@code getRecipeFor} in {@code CraftingMenu} to offer its recipe picker, and whichever of the
 * two mixins is applied second is dropped — with {@code defaultRequire} set, being dropped is a
 * hard injection failure and the game never reaches the main menu. That is Issue #127, and the
 * smithing table was one class-load away from the same crash: Polymorph reads the recipe list
 * there with {@code @ModifyVariable}, which needs the call we were replacing to still be in the
 * method.
 *
 * <p>{@code @WrapMethod} wraps the enclosing method instead and leaves the body untouched, so
 * everyone else's hooks survive — and their lookups land inside our window, which is what makes
 * Polymorph's alternatives obey the stage locks in the first place.
 */
class CrafterContextMixinGuardTest {

    @Test
    void noCrafterContextIsOpenedWithARedirect() throws IOException {
        Path mixins = Path.of("src", "main", "java", "net", "bananemdnsa", "historystages", "mixin");
        assertTrue(Files.isDirectory(mixins), "expected the test to run from the project root");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(mixins)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                if (text.contains("RecipeCraftContext") && text.contains("@Redirect")) {
                    offenders.add(file.getFileName().toString());
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "these set a crafter with @Redirect and will fight other recipe mods over the "
                        + "call site — wrap the method instead: " + offenders);
    }
}
