package dev.morehoppers.block;

import dev.morehoppers.block.entity.CopperHopperBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** 铜漏斗：10 格、每格可禁用与设定物品白名单，传输速率走 FastHopperBlock。 */
public class CopperHopperBlock extends FastHopperBlock {
    public CopperHopperBlock(Settings settings, int cooldownTicks) {
        super(settings, cooldownTicks);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CopperHopperBlockEntity(pos, state);
    }
}
