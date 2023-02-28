package org.wallentines.dll.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerLevel.class)
public interface AccessorServerLevel {

    @Accessor("serverLevelData")
    ServerLevelData getServerLevelData();

}
