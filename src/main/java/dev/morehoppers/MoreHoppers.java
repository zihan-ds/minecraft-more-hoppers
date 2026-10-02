package dev.morehoppers;

import dev.morehoppers.mixin.BlockEntityTypeAccessor;
import dev.morehoppers.registry.ModBlocks;
import dev.morehoppers.registry.ModItems;
import dev.morehoppers.registry.ModNetworking;
import dev.morehoppers.registry.ModRecipes;
import dev.morehoppers.registry.ModScreens;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MoreHoppers implements ModInitializer {
    public static final String MOD_ID = "morehoppers";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModBlocks.register();
        ModItems.register();
        ModScreens.register();
        ModRecipes.register();
        ModNetworking.register();

        // 所有漏斗方块实体都必须是 BlockEntityType.HOPPER：HopperBlockEntity 的构造函数把它写死了，
        // 子类换不掉，而 WorldChunk 的 tick 门禁会检查 getType().supports(state)，
        // 不把我们的方块登记进该类型，新漏斗就永远不会被 tick。
        ((BlockEntityTypeAccessor) (Object) BlockEntityType.HOPPER)
                .morehoppers$getBlocks()
                .addAll(ModBlocks.all());
    }
}
