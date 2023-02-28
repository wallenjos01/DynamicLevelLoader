package org.wallentines.dll.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.wallentines.dll.DynamicLevelContext;

@Mixin(PlayerList.class)
public class MixinPlayerList {

    @Redirect(method="sendLevelInfo", at=@At(value="INVOKE", target="Lnet/minecraft/server/MinecraftServer;overworld()Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel redirectLevelInfo(MinecraftServer instance, ServerPlayer spl) {

        ServerLevel lvl = spl.getLevel();
        if(lvl instanceof DynamicLevelContext.DynamicLevel dl) {
            return instance.getLevel(dl.getOverworld());
        }

        return instance.overworld();
    }
}
