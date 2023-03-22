package org.wallentines.dll;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.*;
import net.minecraft.Util;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapIndex;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.wallentines.dll.mixin.AccessorMinecraftServer;
import org.wallentines.dll.mixin.AccessorServerLevel;
import org.wallentines.midnightcore.fabric.event.server.ServerStopEvent;
import org.wallentines.midnightlib.event.Event;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.LockSupport;

public class DynamicLevelContext {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final MinecraftServer server;
    private final DynamicLevelStorage.DynamicLevelStorageAccess storageAccess;
    private final ChunkProgressListener chunkProgressListener;
    private final WorldConfig config;

    private final Map<ResourceKey<Level>, DynamicLevel> levels = new HashMap<>();
    private WorldStem worldStem;

    public DynamicLevelContext(MinecraftServer server, DynamicLevelStorage storage, WorldConfig config) {

        this.server = server;
        this.config = config;

        Event.register(ServerStopEvent.class, this, ev -> {
            if(config.autoDelete()) {
                unloadAndDelete();
            } else {
                unload(config.autoSave());
            }
        });

        try {
            this.storageAccess = storage.createAccess(config.getLevelName(), this);
        } catch (IOException ex) {
            ex.printStackTrace();
            throw new IllegalStateException("Unable to create dynamic level context!");
        }

        this.chunkProgressListener = ((AccessorMinecraftServer) server).getProgressListenerFactory().create(config.getPregenRadius() + 1);
    }

    public WorldConfig getConfig() {
        return config;
    }

    public void initialize(Runnable onSuccess, Runnable onFail) {

        ExecutorService exe = Util.backgroundExecutor();
        exe.submit(() -> {

            try {
                // Get DataPack config from server
                WorldDataConfiguration worldDataConfiguration = server.getWorldData().getDataConfiguration();

                AccessorMinecraftServer acc = (AccessorMinecraftServer) server;

                WorldLoader.DataLoadContext dlc = new WorldLoader.DataLoadContext(
                        server.getResourceManager(),
                        worldDataConfiguration,
                        acc.getRegistries().getAccessForLoading(RegistryLayer.DIMENSIONS),
                        acc.getRegistries().compositeAccess()
                );
                WorldLoader.DataLoadOutput<WorldData> dl = loadLevelData(dlc);

                LayeredRegistryAccess<RegistryLayer> serverAccess = acc.getRegistries();
                LayeredRegistryAccess<RegistryLayer> dimensions = serverAccess.replaceFrom(RegistryLayer.DIMENSIONS, dl.finalDimensions());

                this.worldStem = new WorldStem(
                        acc.getReloadableResources().resourceManager(),
                        acc.getReloadableResources().managers(),
                        dimensions,
                        dl.cookie()
                );

                if(config.autoSave()) {
                    storageAccess.saveDataTag(worldStem.registries().compositeAccess(), worldStem.worldData());
                }

                server.submit(onSuccess);

            } catch (Exception ex) {
                LOGGER.error("An error occurred while initializing a dynamic level!");
                ex.printStackTrace();

                unload(false);
                server.submit(onFail);
            }
        });
    }

    public Collection<ResourceKey<Level>> getLevelKeys() {

        return levels.keySet();
    }

    public ServerLevel getLevel(ResourceKey<Level> key) {

        return levels.get(key);
    }

    public WorldStem getWorldStem() {
        return worldStem;
    }

    public void loadAllDimensions(DynamicLevelCallback callback) {

        // Initialize the level data if not done already
        if(worldStem == null) {
            initialize(() -> loadAllDimensions(callback), callback::onFail);
            return;
        }

        Registry<LevelStem> stemRegistry = worldStem.registries().compositeAccess().registryOrThrow(Registries.LEVEL_STEM);
        WorldData worldData = worldStem.worldData();

        ServerLevel rootLevel = null;

        boolean debug = worldData.isDebugWorld();
        long seed = worldData.worldGenOptions().seed();
        long seedHash = BiomeManager.obfuscateSeed(seed);

        // Begin loading dimensions
        for(LevelStem stem : stemRegistry) {

            ResourceKey<LevelStem> stemKey = stemRegistry.getResourceKey(stem).orElseThrow();
            ResourceKey<Level> dimensionKey = config.getDimensionKey(stemKey);

            if(dimensionKey == null) {
                continue;
            }

            boolean root = stemKey.equals(LevelStem.OVERWORLD);
            ServerLevelData serverLevelData = root ? worldData.overworldData() : new DerivedLevelData(worldData, worldData.overworldData());

            // These should be present in all worlds. GameRules can still disable spawning
            List<CustomSpawner> spawners = ImmutableList.of(new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(serverLevelData));
            DynamicLevel level = new DynamicLevel(Util.backgroundExecutor(), serverLevelData, dimensionKey, stem, chunkProgressListener, seedHash, spawners, root);

            levels.put(dimensionKey, level);
            ((AccessorMinecraftServer) server).getLevels().put(dimensionKey, level);

            if(root) rootLevel = level;

        }

        chunkProgressListener.start();

        if(rootLevel == null) {
            callback.onFail();
            return;
        }

        final ServerLevel finalRootLevel = rootLevel;

        Util.backgroundExecutor().submit(() -> {

            try {

                // Generate chunks for root level
                ServerLevelData serverLevelData = ((AccessorServerLevel) finalRootLevel).getServerLevelData();

                ServerChunkCache serverChunkCache = finalRootLevel.getChunkSource();
                serverChunkCache.getLightEngine().setTaskPerBatch(500);

                BlockPos spawn = new BlockPos(serverLevelData.getXSpawn(), serverLevelData.getYSpawn(), serverLevelData.getZSpawn());
                finalRootLevel.setDefaultSpawnPos(spawn, serverLevelData.getSpawnAngle());
                chunkProgressListener.updateSpawnPos(new ChunkPos(spawn));

                int pregenArea = (2 * config.getPregenRadius()) + 1;
                pregenArea *= pregenArea;

                while (serverChunkCache.getTickingGenerated() < pregenArea) {
                    Thread.yield();
                    LockSupport.parkNanos("waiting for tasks", 250000000L); // Quarter Second

                    float progress = Math.min(1.0f, (float) serverChunkCache.getTickingGenerated() / (float) pregenArea);
                    try {
                        callback.onProgress(progress);
                    } catch (Exception ex) {
                        LOGGER.warn("An exception occurred while sending a dynamic dimension load progress callback!");
                        ex.printStackTrace();
                    }
                }

                if (!serverLevelData.isInitialized()) {
                    AccessorMinecraftServer.callSetInitialSpawn(finalRootLevel, serverLevelData, config.hasBonusChest(), debug);
                    if (debug) {
                        ((AccessorMinecraftServer) server).callSetupDebugLevel(worldData);
                    }
                    serverLevelData.setInitialized(true);
                }
                addWorldBorderListener(finalRootLevel);

                for(ServerLevel other : levels.values()) {

                    if(other == finalRootLevel) continue;

                    WorldBorder wb = finalRootLevel.getWorldBorder();
                    wb.addListener(new BorderChangeListener.DelegateBorderChangeListener(other.getWorldBorder()));
                }

                // Load Chunks
                ForcedChunksSavedData chunksSavedData = finalRootLevel.getDataStorage().get(ForcedChunksSavedData::load, ForcedChunksSavedData.FILE_ID);

                if (chunksSavedData != null) {
                    for (long l : chunksSavedData.getChunks()) {
                        ChunkPos pos = new ChunkPos(l);
                        serverChunkCache.updateChunkForced(pos, true);
                    }
                }

                serverChunkCache.getLightEngine().setTaskPerBatch(5);
                finalRootLevel.setSpawnSettings(server.isSpawningMonsters(), server.isSpawningAnimals());

                chunkProgressListener.stop();
                server.submit(() -> callback.onLoaded(finalRootLevel));

            } catch (Exception ex) {

                LOGGER.warn("An exception occurred while loading a dynamic dimension!");
                ex.printStackTrace();
                server.submit(callback::onFail);
            }
        });

    }

    public void unloadDimension(ResourceKey<Level> dimensionKey, boolean save) {

        ((AccessorMinecraftServer) server).getLevels().remove(dimensionKey);

        DynamicLevel level = levels.remove(dimensionKey);
        if(level == null) return;

        // Save world
        if(save) {
            level.save(null, true, true);
        }

        // Unload world
        try {
            level.getChunkSource().close();

        } catch (IOException ex) {
            LOGGER.warn("An exception occurred while unloading a dynamic level!");
            ex.printStackTrace();
        }
    }

    public void unloadAndDelete() {

        if(worldStem == null) return;

        for(ResourceKey<Level> key : new ArrayList<>(levels.keySet())) {
            unloadDimension(key, false);
        }

        try {

            storageAccess.deleteLevel();
            worldStem = null;

        } catch (IOException ex) {

            LOGGER.warn("An exception occurred while deleting a dynamic world!");
            ex.printStackTrace();
        }
    }

    public void unload(boolean save) {

        if(worldStem == null) return;

        for(ResourceKey<Level> key : new ArrayList<>(levels.keySet())) {
            unloadDimension(key, save);
        }

        try {
            // Unload session
            if (save) {
                storageAccess.saveDataTag(worldStem.registries().compositeAccess(), worldStem.worldData());
            }
            storageAccess.close();
            worldStem = null;

        } catch (IOException ex) {

            LOGGER.warn("An exception occurred while unloading a dynamic world!");
            ex.printStackTrace();
        }
    }

    public WorldLoader.DataLoadOutput<WorldData> getExistingData() {

        if(worldStem == null) return null;

        // Get access to the server's world presets
        WorldPreset worldPreset = config.getWorldPreset();

        // Load dimensions from world preset
        WorldDimensions worldDimensions = worldPreset.createWorldDimensions();

        return loadExistingData(worldDimensions, worldStem.registries().compositeAccess(), server.getWorldData().getDataConfiguration(), storageAccess);
    }

    private static WorldLoader.DataLoadOutput<WorldData> loadExistingData(WorldDimensions worldDimensions, RegistryAccess access, WorldDataConfiguration dataConfiguration, LevelStorageSource.LevelStorageAccess storageAccess) {

        DynamicOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, access);
        Pair<WorldData, WorldDimensions.Complete> data = storageAccess.getDataTag(ops, dataConfiguration, worldDimensions.dimensions(), access.allRegistriesLifecycle());

        if (data == null) {
            return null;
        }

        return new WorldLoader.DataLoadOutput<>(data.getFirst(), data.getSecond().dimensionsRegistryAccess());
    }

    private WorldLoader.DataLoadOutput<WorldData> loadLevelData(WorldLoader.DataLoadContext dataLoadContext) {

        // Get access to the server's world presets
        WorldPreset worldPreset = config.getWorldPreset();

        // Load dimensions from world preset
        WorldDimensions worldDimensions = worldPreset.createWorldDimensions();

        // Try to load level.dat, unless requested not to
        if(!config.recreateLevelData()) {

            WorldLoader.DataLoadOutput<WorldData> data = loadExistingData(worldDimensions, dataLoadContext.datapackWorldgen(), dataLoadContext.dataConfiguration(), storageAccess);

            // Return early if level.dat was loaded
            if (data != null) {
                return data;
            }
        }

        // Create a new level.dat

        // Create default level settings from config
        LevelSettings levelSettings = new LevelSettings(
                config.getLevelName(),
                config.getDefaultGameMode(),
                config.isHardcore(),
                config.getDifficulty(),
                false, config.getGameRules(), dataLoadContext.dataConfiguration()
        );

        // Create default world options from config
        WorldOptions worldOptions = new WorldOptions(config.getSeed(), config.generateStructures(), config.hasBonusChest());

        // Finalize dimension registry now that custom settings have been applied
        WorldDimensions.Complete complete = worldDimensions.bake(worldDimensions.dimensions());
        Lifecycle lifecycle = complete.lifecycle().add(dataLoadContext.datapackWorldgen().allRegistriesLifecycle());

        // Create level data for this dynamic level
        PrimaryLevelData primary = new PrimaryLevelData(levelSettings, worldOptions, complete.specialWorldProperty(), lifecycle);

        // Return the level data and the dimension registry
        return new WorldLoader.DataLoadOutput<>(primary, complete.dimensionsRegistryAccess());

    }

    public static void addWorldBorderListener(ServerLevel serverLevel) {

        serverLevel.getWorldBorder().addListener(new BorderChangeListener() {
            @Override
            public void onBorderSizeSet(@NotNull WorldBorder worldBorder, double d) {
                ClientboundSetBorderSizePacket pck = new ClientboundSetBorderSizePacket(worldBorder);
                serverLevel.players().forEach(pl -> pl.connection.send(pck));
            }

            @Override
            public void onBorderSizeLerping(@NotNull WorldBorder worldBorder, double d, double e, long l) {
                ClientboundSetBorderLerpSizePacket pck = new ClientboundSetBorderLerpSizePacket(worldBorder);
                serverLevel.players().forEach(pl -> pl.connection.send(pck));
            }

            @Override
            public void onBorderCenterSet(@NotNull WorldBorder worldBorder, double d, double e) {
                ClientboundSetBorderCenterPacket pck = new ClientboundSetBorderCenterPacket(worldBorder);
                serverLevel.players().forEach(pl -> pl.connection.send(pck));
            }

            @Override
            public void onBorderSetWarningTime(@NotNull WorldBorder worldBorder, int i) {
                ClientboundSetBorderWarningDelayPacket pck = new ClientboundSetBorderWarningDelayPacket(worldBorder);
                serverLevel.players().forEach(pl -> pl.connection.send(pck));
            }

            @Override
            public void onBorderSetWarningBlocks(@NotNull WorldBorder worldBorder, int i) {
                ClientboundSetBorderWarningDistancePacket pck = new ClientboundSetBorderWarningDistancePacket(worldBorder);
                serverLevel.players().forEach(pl -> pl.connection.send(pck));
            }

            @Override
            public void onBorderSetDamagePerBlock(@NotNull WorldBorder worldBorder, double d) { }

            @Override
            public void onBorderSetDamageSafeZOne(@NotNull WorldBorder worldBorder, double d) { }
        });
    }

    @ParametersAreNonnullByDefault
    public class DynamicLevel extends ServerLevel {

        public DynamicLevel(Executor executor, ServerLevelData serverLevelData, ResourceKey<Level> dimensionKey, LevelStem levelStem, ChunkProgressListener chunkProgressListener, long seed, List<CustomSpawner> spawners, boolean tickTime) {
            super(server, executor, storageAccess, serverLevelData, dimensionKey, levelStem, chunkProgressListener, false, seed, spawners, tickTime);
        }

        public ResourceKey<Level> getOverworld() {

            return config.getDimensionKey(LevelStem.OVERWORLD);
        }

        public ResourceKey<Level> getNether() {

            return config.getDimensionKey(LevelStem.NETHER);
        }

        public ResourceKey<Level> getEnd() {

            return config.getDimensionKey(LevelStem.END);
        }

        public DynamicLevelContext getContext() {

            return DynamicLevelContext.this;
        }

        @Override
        public long getSeed() {
            return config.getSeed();
        }

        @Nullable
        public MapItemSavedData getMapData(String string) {
            return getDataStorage().get(MapItemSavedData::load, string);
        }

        @Override
        public boolean noSave() {
            return !config.autoSave();
        }

        @Override
        public void setMapData(String string, MapItemSavedData mapItemSavedData) {
            getDataStorage().set(string, mapItemSavedData);
        }

        @Override
        public int getFreeMapId() {
            return getDataStorage().computeIfAbsent(MapIndex::load, MapIndex::new, "idcounts").getFreeAuxValueForMap();
        }

        @Override
        public void setDefaultSpawnPos(BlockPos blockPos, float f) {

            ChunkPos chunkPos = new ChunkPos(new BlockPos(levelData.getXSpawn(), 0, levelData.getZSpawn()));
            this.levelData.setSpawn(blockPos, f);

            this.getChunkSource().removeRegionTicket(TicketType.START, chunkPos, 11, Unit.INSTANCE);
            this.getChunkSource().addRegionTicket(TicketType.START, new ChunkPos(blockPos), 11, Unit.INSTANCE);
        }

        @Nullable
        @Override
        public BlockPos findNearestMapStructure(TagKey<Structure> tagKey, BlockPos blockPos, int i, boolean bl) {

            if (!worldStem.worldData().worldGenOptions().generateStructures()) {
                return null;
            } else {
                Optional<HolderSet.Named<Structure>> optional = server.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(tagKey);
                if (optional.isEmpty()) {
                    return null;
                } else {
                    Pair<?, ?> pair = this.getChunkSource().getGenerator().findNearestMapStructure(this, optional.get(), blockPos, i, bl);
                    return pair != null ? (BlockPos) pair.getFirst() : null;
                }
            }
        }

        @Override
        public boolean isFlat() {
            return worldStem.worldData().isFlatWorld();
        }
    }

}
