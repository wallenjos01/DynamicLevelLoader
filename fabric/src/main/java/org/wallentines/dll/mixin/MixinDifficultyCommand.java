package org.wallentines.dll.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.DifficultyCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.wallentines.dll.DynamicLevelContext;

@Mixin(DifficultyCommand.class)
public class MixinDifficultyCommand {

    @Redirect(method = "setDifficulty", at=@At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/WorldData;getDifficulty()Lnet/minecraft/world/Difficulty;"))
    private static Difficulty redirectGetDifficulty(WorldData instance, CommandSourceStack source) {
        return source.getLevel().getDifficulty();
    }

    @Redirect(method = "setDifficulty", at=@At(value="INVOKE", target = "Lnet/minecraft/server/MinecraftServer;setDifficulty(Lnet/minecraft/world/Difficulty;Z)V"))
    private static void redirectSetDifficulty(MinecraftServer instance, Difficulty difficulty, boolean force, CommandSourceStack source) {

        ServerLevel lvl = source.getLevel();
        WorldData data;

        if(lvl instanceof DynamicLevelContext.DynamicLevel dl) {

            data = dl.getContext().getWorldStem().worldData();

        } else {

            data = instance.getWorldData();
        }

        if(force || data.isDifficultyLocked()) {
            data.setDifficulty(data.isHardcore() ? Difficulty.HARD : difficulty);
            lvl.setSpawnSettings(data.getDifficulty() != Difficulty.PEACEFUL, true);
            lvl.players().forEach(pl -> pl.connection.send(new ClientboundChangeDifficultyPacket(data.getDifficulty(), data.isDifficultyLocked())));
        }
    }

}
