package dev.morehoppers.block;

/**
 * 由本模组的漏斗方块实现，给出每次成功传输后的冷却 tick 数（原版为 8 tick / 2.5 个每秒）。
 * 读取它的是 HopperBlockEntityMixin，原版漏斗不受影响。
 */
public interface HopperSpeed {
    int transferCooldownTicks();
}
