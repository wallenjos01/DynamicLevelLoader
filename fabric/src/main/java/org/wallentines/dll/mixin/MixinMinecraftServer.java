package org.wallentines.dll.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.wallentines.dll.DynamicLevelContext;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {

    @Redirect(method="createLevels", at=@At(value="INVOKE", target="Lnet/minecraft/server/players/PlayerList;addWorldborderListener(Lnet/minecraft/server/level/ServerLevel;)V"))
    private void redirectWorldBorder(PlayerList instance, ServerLevel serverLevel) {
        DynamicLevelContext.addWorldBorderListener(serverLevel);
    }

}
