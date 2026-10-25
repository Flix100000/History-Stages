package net.bananemdnsa.historystages.mixin;

import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to whether a command source is silent. 1.21 has {@code isSilent()}; on 1.20.1 the
 * flag is only the private field. {@code /historystages reload} asks it so the reloads the pedestal
 * and quest rewards run quietly do not rewrite the debug report.
 */
@Mixin(CommandSourceStack.class)
public interface CommandSourceStackAccessor {
    @Accessor("silent")
    boolean historystages$isSilent();
}
