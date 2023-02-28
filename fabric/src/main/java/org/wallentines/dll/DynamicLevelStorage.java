package org.wallentines.dll;

import com.mojang.datafixers.DataFixer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jetbrains.annotations.NotNull;
import org.wallentines.dll.mixin.AccessorLevelStorageAccess;
import org.wallentines.midnightcore.api.MidnightCoreAPI;
import org.wallentines.midnightcore.api.server.MServer;
import org.wallentines.midnightcore.fabric.server.FabricServer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;

public class DynamicLevelStorage extends LevelStorageSource {

    private final HashMap<String, DynamicLevelContext> preConfigCache = new HashMap<>();

    private DynamicLevelStorage(Path worldsPath, Path backupsPath, DataFixer dataFixer) {
        super(worldsPath, backupsPath, dataFixer);
    }

    public DynamicLevelContext createWorldContext(WorldConfig config) {

        MServer server = MidnightCoreAPI.getRunningServer();
        if(server == null) throw new IllegalStateException("Attempt to create dynamic level before server startup!");

        return new DynamicLevelContext(((FabricServer) server).getInternal(),this, config);
    }

    @Deprecated
    @Override
    public @NotNull DynamicLevelStorageAccess createAccess(@NotNull String worldId) {
        throw new UnsupportedOperationException("A DynamicLevelContext must be supplied in order to create a Dynamic world!");
    }

    DynamicLevelStorageAccess createAccess(@NotNull String worldId, DynamicLevelContext ctx) throws IOException {
        preConfigCache.put(worldId, ctx);
        return new DynamicLevelStorageAccess(worldId);
    }


    public static DynamicLevelStorage create(Path worldsPath, Path backupsPath) {
        return new DynamicLevelStorage(worldsPath, backupsPath, DataFixers.getDataFixer());
    }


    public class DynamicLevelStorageAccess extends LevelStorageAccess {

        private final DynamicLevelContext context;

        public DynamicLevelStorageAccess(String worldId) throws IOException {
            super(worldId);
            this.context = preConfigCache.remove(worldId);
        }

        public DynamicLevelContext getContext() {

            return context == null ? preConfigCache.get(getLevelId()) : context;
        }

        @Override
        public @NotNull Path getDimensionPath(@NotNull ResourceKey<Level> resourceKey) {

            Path root = ((AccessorLevelStorageAccess) this).getLevelDirectory().path();

            if(context.getConfig().getDimensionKey(LevelStem.OVERWORLD).equals(resourceKey)) {
                return root;
            }
            if(context.getConfig().getDimensionKey(LevelStem.NETHER).equals(resourceKey)) {
                return root.resolve("DIM-1");
            }
            if(context.getConfig().getDimensionKey(LevelStem.END).equals(resourceKey)) {
                return root.resolve("DIM1");
            }

            return root.resolve("dimensions").resolve(resourceKey.location().getNamespace()).resolve(resourceKey.location().getPath());
        }
    }


}
