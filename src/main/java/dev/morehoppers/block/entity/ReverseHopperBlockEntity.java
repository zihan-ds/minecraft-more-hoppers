package dev.morehoppers.block.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * 逆向漏斗的方块实体：把原版漏斗的"吸入口"从上方镜像到下方。
 *
 * 原版 extract() 用 getHopperY() + 1 计算输入容器位置，getInputItemEntities() 用
 * getInputAreaShape() 配合 getHopperY() - 0.5 计算物品实体的拾取盒 —— 两处都只依赖这两个方法，
 * 所以覆写它们即可整体镜像，其余（插入、冷却、红石、比较器）继续用原版逻辑。
 * 输出方向（恒为上方）由 HopperBlockEntityMixin 覆写 getOutputInventory 处理。
 */
public class ReverseHopperBlockEntity extends HopperBlockEntity {
    /** 原版输入位置 = getHopperY() + 1；再减 2 之后正好落在正下方那一格。 */
    private static final double INPUT_Y_OFFSET = -2.0;

    /**
     * 拾取范围 = 原版漏斗的拾取体（斗内 11/16 一直到上方一格，即世界坐标 y+11/16..y+2）
     * 再镜像补上正下方那一格，合并成一个连续盒 y-1..y+2。
     *
     * 必须包含漏斗自己那一格：物品掉进斗里时原版漏斗会把它吸走；如果只把范围整体镜像到下方，
     * 掉进斗里的物品就永远不在拾取范围内，会一直停在漏斗里出不去。
     * 本类的 getHopperY() 比原版低 2 格，所以这里的局部坐标要相应 +2 才是世界坐标。
     */
    private static final Box INPUT_AREA =
            Block.createCuboidShape(0.0, 16.0, 0.0, 16.0, 64.0, 16.0).getBoundingBoxes().get(0);

    public ReverseHopperBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public double getHopperY() {
        return super.getHopperY() + INPUT_Y_OFFSET;
    }

    @Override
    public Box getInputAreaShape() {
        return INPUT_AREA;
    }
}
