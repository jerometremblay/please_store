package com.jerome.pleasestore.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class PleaseStoreConfig {
    public static final Common COMMON;
    public static final ModConfigSpec COMMON_SPEC;

    static {
        Pair<Common, ModConfigSpec> commonSpec = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = commonSpec.getLeft();
        COMMON_SPEC = commonSpec.getRight();
    }

    private PleaseStoreConfig() {
    }

    public static final class Common {
        public final ModConfigSpec.IntValue villagerSearchRadius;

        private Common(ModConfigSpec.Builder builder) {
            builder.comment("Settings for how Please Store controllers find villagers.")
                    .translation("pleasestore.config.category.controller")
                    .push("controller");

            villagerSearchRadius = builder
                    .comment("Maximum distance in blocks from the target inventory used to search for villagers carrying items.")
                    .translation("pleasestore.config.villagerSearchRadius")
                    .defineInRange("villagerSearchRadius", 16, 1, 64);

            builder.pop();
        }
    }
}
