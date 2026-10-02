package dev.morehoppers.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

/**
 * 原版没有任何 API 能把方块登记进已有的方块实体类型，而我们的漏斗必须是 BlockEntityType.HOPPER
 * （见 MoreHoppers#onInitialize）。这里取出那个可变集合，在初始化时把新方块加进去。
 */
@Mixin(BlockEntityType.class)
public interface BlockEntityTypeAccessor {
    @Accessor("blocks")
    Set<Block> morehoppers$getBlocks();
}
