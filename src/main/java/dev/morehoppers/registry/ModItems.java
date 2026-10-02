package dev.morehoppers.registry;

import dev.morehoppers.MoreHoppers;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModItems {
    private ModItems() {
    }

    public static void register() {
        register("gold_hopper", ModBlocks.GOLD_HOPPER);
        register("diamond_hopper", ModBlocks.DIAMOND_HOPPER);
        register("netherite_hopper", ModBlocks.NETHERITE_HOPPER);
        register("copper_hopper", ModBlocks.COPPER_HOPPER);
        register("reverse_hopper", ModBlocks.REVERSE_HOPPER);

        // 原版漏斗就在红石方块页签里，新漏斗跟它放在一起
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> {
            entries.add(ModBlocks.GOLD_HOPPER);
            entries.add(ModBlocks.DIAMOND_HOPPER);
            entries.add(ModBlocks.NETHERITE_HOPPER);
            entries.add(ModBlocks.COPPER_HOPPER);
            entries.add(ModBlocks.REVERSE_HOPPER);
        });
    }

    private static void register(String name, Block block) {
        Registry.register(Registries.ITEM, MoreHoppers.id(name), new BlockItem(block, new Item.Settings()));
    }
}
