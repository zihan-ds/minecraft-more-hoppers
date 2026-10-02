package dev.morehoppers.block;

import net.minecraft.block.HopperBlock;

/**
 * 原版漏斗的加速版：除了冷却 tick 数，一切机制（放置、朝向、红石禁用、容器/物品实体交互、比较器）
 * 都走原版 HopperBlock / HopperBlockEntity 的代码。
 */
public class FastHopperBlock extends HopperBlock implements HopperSpeed {
    private final int cooldownTicks;

    public FastHopperBlock(Settings settings, int cooldownTicks) {
        super(settings);
        this.cooldownTicks = cooldownTicks;
    }

    @Override
    public int transferCooldownTicks() {
        return this.cooldownTicks;
    }
}
