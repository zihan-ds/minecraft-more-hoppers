package dev.morehoppers.registry;

import dev.morehoppers.MoreHoppers;
import dev.morehoppers.block.CopperHopperBlock;
import dev.morehoppers.block.FastHopperBlock;
import dev.morehoppers.block.ReverseHopperBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.List;

/** 五种漏斗方块。属性全部照抄原版漏斗（硬度、抗爆、音效、需要镐、非完整方块、不遮挡视线）。 */
public final class ModBlocks {
    public static final Block GOLD_HOPPER = new FastHopperBlock(hopperSettings(), 4);
    public static final Block DIAMOND_HOPPER = new FastHopperBlock(hopperSettings(), 2);
    public static final Block NETHERITE_HOPPER = new FastHopperBlock(hopperSettings(), 1);
    public static final Block COPPER_HOPPER = new CopperHopperBlock(hopperSettings(), 5);
    public static final Block REVERSE_HOPPER = new ReverseHopperBlock(hopperSettings(), 5);

    private ModBlocks() {
    }

    private static AbstractBlock.Settings hopperSettings() {
        return AbstractBlock.Settings.copy(Blocks.HOPPER);
    }

    public static void register() {
        register("gold_hopper", GOLD_HOPPER);
        register("diamond_hopper", DIAMOND_HOPPER);
        register("netherite_hopper", NETHERITE_HOPPER);
        register("copper_hopper", COPPER_HOPPER);
        register("reverse_hopper", REVERSE_HOPPER);
    }

    private static void register(String name, Block block) {
        Registry.register(Registries.BLOCK, MoreHoppers.id(name), block);
    }

    public static List<Block> all() {
        return List.of(GOLD_HOPPER, DIAMOND_HOPPER, NETHERITE_HOPPER, COPPER_HOPPER, REVERSE_HOPPER);
    }
}
