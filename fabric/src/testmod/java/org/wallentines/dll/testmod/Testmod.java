package org.wallentines.dll.testmod;

import net.fabricmc.api.ModInitializer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.wallentines.dll.DynamicLevelCallback;
import org.wallentines.dll.DynamicLevelContext;
import org.wallentines.dll.DynamicLevelStorage;
import org.wallentines.midnightcore.fabric.event.server.CommandLoadEvent;
import org.wallentines.midnightcore.fabric.util.LocationUtil;
import org.wallentines.midnightlib.event.Event;
import org.wallentines.dll.WorldConfig;

import java.nio.file.Path;

public class Testmod implements ModInitializer {
    @Override
    public void onInitialize() {


        Event.register(CommandLoadEvent.class, this, ev -> {

            ev.getDispatcher().register(Commands.literal("dlltest")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(Component.literal("Loading dimension..."), false);

                    ServerPlayer spl = ctx.getSource().getPlayerOrException();
                    WorldConfig config = WorldConfig.builder()
                            .generateStructures(true)
                            .difficulty(Difficulty.HARD)
                            .bonusChest(true)
                            .addDimension(
                                    new ResourceLocation("dlltest:test"),
                                    WorldConfig.presetDimension(ctx.getSource().getServer().registryAccess(), WorldPresets.NORMAL, LevelStem.OVERWORLD)
                            ).build();

                    Path test = Path.of("test");
                    DynamicLevelStorage storage = DynamicLevelStorage.create(test, test);

                    DynamicLevelContext dctx = storage.createWorldContext(config);
                    dctx.loadAllDimensions(new DynamicLevelCallback() {
                        @Override
                        public void onLoaded(ServerLevel level) {

                            LocationUtil.teleport(spl, LocationUtil.getSpawnLocation(level));
                        }

                        @Override
                        public void onProgress(float percent) {

                            ctx.getSource().sendSuccess(Component.literal("World Loading: " + String.format("%.2f", percent * 100.0f) + "% Complete"), false);
                        }
                    });

                    return 1;
                })
            );

        });

    }
}
