package org.wallentines.dll;

import net.minecraft.server.level.ServerLevel;

import java.util.function.Consumer;

public interface DynamicLevelCallback {

    void onLoaded(ServerLevel level);
    default void onFail() { }
    default void onProgress(float percent) { }


    static DynamicLevelCallback of(Consumer<ServerLevel> loaded, Runnable failed) {
        return new DynamicLevelCallback() {
            @Override
            public void onLoaded(ServerLevel level) {
                loaded.accept(level);
            }

            @Override
            public void onFail() {
                failed.run();
            }
        };
    }

    static DynamicLevelCallback of(Consumer<ServerLevel> loaded, Runnable failed, Consumer<Float> progress) {
        return new DynamicLevelCallback() {
            @Override
            public void onLoaded(ServerLevel level) {
                loaded.accept(level);
            }

            @Override
            public void onFail() {
                failed.run();
            }

            @Override
            public void onProgress(float percent) {
                progress.accept(percent);
            }
        };
    }

}
