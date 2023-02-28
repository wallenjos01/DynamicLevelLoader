package org.wallentines.dll;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.WorldLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.WorldData;
import org.wallentines.dll.mixin.AccessorWorldPreset;
import org.wallentines.midnightcore.fabric.server.EmptyGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@SuppressWarnings("unused")
public class WorldConfig {

    private final Map<ResourceKey<LevelStem>, ResourceKey<Level>> levelKeys;
    private final WorldPreset worldPreset;
    private final String levelName;

    private final boolean hardcore;
    private final boolean generateStructures;
    private final boolean bonusChest;
    private final boolean autoSave;
    private final boolean autoDelete;
    private final boolean ignoreSessionLock;
    private final boolean recreateLevelData;
    private final long seed;
    private final int pregenRadius;

    private final Difficulty difficulty;
    private final GameType defaultGameMode;
    private final GameRules gameRules;

    private WorldConfig(Map<ResourceKey<LevelStem>, ResourceKey<Level>> levelKeys, WorldPreset worldPreset,
                        String levelName, boolean hardcore, boolean generateStructures, boolean bonusChest,
                        boolean autoSave, boolean autoDelete, boolean ignoreSessionLock, boolean recreateLevelData,
                        long seed, int pregenRadius, Difficulty difficulty, GameType defaultGameMode, GameRules gameRules) {

        this.levelKeys = ImmutableMap.copyOf(levelKeys);
        this.worldPreset = worldPreset;
        this.levelName = levelName;
        this.hardcore = hardcore;
        this.generateStructures = generateStructures;
        this.bonusChest = bonusChest;
        this.autoSave = autoSave;
        this.autoDelete = autoDelete;
        this.ignoreSessionLock = ignoreSessionLock;
        this.recreateLevelData = recreateLevelData;
        this.seed = seed;
        this.pregenRadius = pregenRadius;
        this.difficulty = difficulty;
        this.defaultGameMode = defaultGameMode;
        this.gameRules = gameRules;
    }

    public ResourceKey<Level> getDimensionKey(ResourceKey<LevelStem> stemKey) {
        return levelKeys.get(stemKey);
    }

    public WorldPreset getWorldPreset() {
        return worldPreset;
    }

    public String getLevelName() {
        return levelName;
    }

    public boolean isHardcore() {
        return hardcore;
    }

    public boolean generateStructures() {
        return generateStructures;
    }

    public boolean hasBonusChest() {
        return bonusChest;
    }

    public boolean autoSave() {
        return autoSave;
    }

    public boolean autoDelete() {
        return autoDelete;
    }

    public boolean ignoreSessionLock() {
        return ignoreSessionLock;
    }

    public boolean recreateLevelData() {
        return recreateLevelData;
    }

    public long getSeed() {
        return seed;
    }

    public int getPregenRadius() {
        return pregenRadius;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public GameType getDefaultGameMode() {
        return defaultGameMode;
    }

    public GameRules getGameRules() {
        return gameRules;
    }

    public static DimensionBuilder dimension(RegistryAccess.Frozen access, String dimensionKey) {

        return new DimensionBuilder(access, ResourceKey.create(Registries.LEVEL_STEM, new ResourceLocation(dimensionKey)));
    }


    public static DimensionBuilder dimension(RegistryAccess.Frozen access, ResourceKey<LevelStem> dimensionKey) {

        return new DimensionBuilder(access, dimensionKey);
    }

    public static DimensionBuilder presetDimension(RegistryAccess.Frozen access, ResourceKey<WorldPreset> preset, ResourceKey<LevelStem> dimensionKey) {

        return new DimensionBuilder(access, dimensionKey)
                .copyFromPreset(preset);
    }


    public static Builder builder() {

        return new Builder();
    }


    public static class Builder {
        private String levelName = "New World";
        private boolean hardcore = false;
        private boolean generateStructures = true;
        private boolean bonusChest = false;
        private boolean autoSave = true;
        private boolean autoDelete = false;
        private boolean ignoreSessionLock = false;
        private boolean recreateLevelData = false;
        private long seed = RandomSource.create().nextLong();
        private int pregenRadius = 10;
        private Difficulty difficulty = Difficulty.NORMAL;
        private GameType defaultGameType = GameType.SURVIVAL;
        private final GameRules gameRules = new GameRules();
        private final HashMap<ResourceKey<LevelStem>, LevelStem> dimensionStems = new HashMap<>();
        private final HashMap<ResourceKey<LevelStem>, ResourceKey<Level>> dimensions = new HashMap<>();

        private Builder() { }

        public Builder levelName(String name) {
            this.levelName = name;
            return this;
        }

        public Builder hardcore(boolean hardcore) {
            this.hardcore = hardcore;
            return this;
        }

        public Builder generateStructures(boolean generateStructures) {
            this.generateStructures = generateStructures;
            return this;
        }

        public Builder bonusChest(boolean bonusChest) {
            this.bonusChest = bonusChest;
            return this;
        }

        public Builder autoSave(boolean autoSave) {
            this.autoSave = autoSave;
            return this;
        }

        public Builder autoDelete(boolean autoDelete) {
            this.autoDelete = autoDelete;
            return this;
        }

        public Builder ignoreSessionLock(boolean ignoreSessionLock) {
            this.ignoreSessionLock = ignoreSessionLock;
            return this;
        }

        public Builder recreateLevelData(boolean recreateLevelData) {
            this.recreateLevelData = recreateLevelData;
            return this;
        }

        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        public Builder pregenRadius(int pregenRadius) {
            this.pregenRadius = pregenRadius;
            return this;
        }

        public Builder difficulty(Difficulty difficulty) {
            this.difficulty = difficulty;
            return this;
        }

        public Builder defaultGameMode(GameType gameMode) {
            this.defaultGameType = gameMode;
            return this;
        }

        public <T extends GameRules.Value<T>> Builder gameRule(GameRules.Key<T> key, T value) {
            gameRules.getRule(key).setFrom(value, null);
            return this;
        }

        public Builder gameRule(GameRules.Key<GameRules.BooleanValue> key, boolean value) {
            gameRules.getRule(key).set(value, null);
            return this;
        }

        public Builder gameRule(GameRules.Key<GameRules.IntegerValue> key, int value) {
            gameRules.getRule(key).set(value, null);
            return this;
        }

        public Builder addDimension(String dimensionId, DimensionBuilder builder) {

            return addDimension(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimensionId)), builder);
        }

        public Builder addDimension(ResourceLocation dimensionId, DimensionBuilder builder) {

            return addDimension(ResourceKey.create(Registries.DIMENSION, dimensionId), builder);
        }

        public Builder addDimension(ResourceKey<Level> dimensionId, DimensionBuilder builder) {

            dimensions.put(builder.dimensionId, dimensionId);
            dimensionStems.put(builder.dimensionId, new LevelStem(builder.dimensionType, builder.getGenerator()));

            return this;
        }

        public Builder discoverDimensions(DynamicLevelContext context, Function<ResourceKey<LevelStem>, ResourceKey<Level>> keyMutator) {

            WorldLoader.DataLoadOutput<WorldData> output = context.getExistingData();
            Registry<LevelStem> reg = output.finalDimensions().registryOrThrow(Registries.LEVEL_STEM);
            for(LevelStem ls : reg) {

                ResourceKey<LevelStem> key = reg.getResourceKey(ls).orElseThrow();
                ResourceKey<Level> dimensionKey = keyMutator.apply(key);

                if(dimensionKey == null) continue;

                addDimension(dimensionKey, dimension(output.finalDimensions(), key).copyFrom(ls));
            }

            return this;
        }

        public WorldConfig build() {

            if(dimensionStems.isEmpty()) throw new IllegalStateException("Cannot create a world config with no dimensions!");

            WorldPreset preset = new WorldPreset(dimensionStems);
            return new WorldConfig(dimensions, preset, levelName, hardcore, generateStructures, bonusChest, autoSave,
                    autoDelete, ignoreSessionLock, recreateLevelData, seed, pregenRadius, difficulty, defaultGameType,
                    gameRules);

        }


    }

    public static class DimensionBuilder {

        private final ResourceKey<LevelStem> dimensionId;
        private final RegistryAccess.Frozen access;
        private Holder<DimensionType> dimensionType;
        private ChunkGenerator generator;

        private DimensionBuilder(RegistryAccess.Frozen access, ResourceKey<LevelStem> dimensionId) {
            this.dimensionId = dimensionId;
            this.access = access;
        }

        public DimensionBuilder type(String type) {

            return type(ResourceKey.create(Registries.DIMENSION_TYPE, new ResourceLocation(type)));
        }

        public DimensionBuilder type(ResourceKey<DimensionType> type) {

            this.dimensionType = access.registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(type);
            return this;
        }

        public DimensionBuilder generator(ChunkGenerator generator) {
            this.generator = generator;
            return this;
        }

        public DimensionBuilder emptyGenerator(ResourceKey<Biome> biome) {
            this.generator = EmptyGenerator.create(biome, access);
            return this;
        }

        public DimensionBuilder copyGenerator(ResourceKey<WorldPreset> preset, String dimension) {
            return copyGenerator(preset, ResourceKey.create(Registries.LEVEL_STEM, new ResourceLocation(dimension)));
        }

        public DimensionBuilder copyGenerator(ResourceKey<WorldPreset> preset, ResourceKey<LevelStem> dimension) {

            WorldPreset normal = access.registryOrThrow(Registries.WORLD_PRESET).get(preset);
            if (normal == null) throw new IllegalStateException("Unable to copy generator! World preset " + preset.location() + " is missing!");

            LevelStem ls = ((AccessorWorldPreset) normal).getDimensions().get(dimensionId);

            generator = ls.generator();
            return this;
        }

        public DimensionBuilder copyFromPreset(ResourceKey<WorldPreset> preset) {

            WorldPreset normal = access.registryOrThrow(Registries.WORLD_PRESET).get(preset);
            if (normal == null) throw new IllegalStateException("Unable to copy generator! World preset " + preset.location() + " is missing!");

            LevelStem ls = ((AccessorWorldPreset) normal).getDimensions().get(dimensionId);

            return copyFrom(ls);
        }

        public DimensionBuilder copyFrom(LevelStem levelStem) {

            dimensionType = levelStem.type();
            generator = levelStem.generator();

            return this;
        }

        private ChunkGenerator getGenerator() {

            if(generator == null) {
                copyGenerator(WorldPresets.NORMAL, dimensionId);
            }

            return generator;
        }

    }

}
