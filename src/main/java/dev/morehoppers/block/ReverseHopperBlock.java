package dev.morehoppers.block;

import dev.morehoppers.block.entity.ReverseHopperBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * 逆向漏斗：放置、朝向、形状与原版漏斗完全一致，模型上下翻转显示；
 * 传输方向相对原版镜像（下方容器吸出、上方容器输出），由方块实体与 mixin 实现。
 *
 * 模型是翻转过的（blockstates 里的 "x": 180），所以轮廓（选择线框）与射线形状也必须一起镜像，
 * 否则会出现"线框贴在原版位置、方块画在下面"的错位。
 */
public class ReverseHopperBlock extends HopperBlock implements HopperSpeed {
    private final int cooldownTicks;

    public ReverseHopperBlock(Settings settings, int cooldownTicks) {
        super(settings);
        this.cooldownTicks = cooldownTicks;
    }

    @Override
    public int transferCooldownTicks() {
        return this.cooldownTicks;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ReverseHopperBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return mirrorVertically(super.getOutlineShape(state, world, pos, context));
    }

    @Override
    protected VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) {
        return mirrorVertically(super.getRaycastShape(state, world, pos));
    }

    /** 原版形状的每个包围盒做 y -> 1 - y 的镜像再并起来，几何上就是原版形状的上下镜像。 */
    private static VoxelShape mirrorVertically(VoxelShape shape) {
        VoxelShape mirrored = VoxelShapes.empty();
        for (Box box : shape.getBoundingBoxes()) {
            mirrored = VoxelShapes.union(mirrored, VoxelShapes.cuboid(
                    new Box(box.minX, 1.0 - box.maxY, box.minZ, box.maxX, 1.0 - box.minY, box.maxZ)));
        }
        return mirrored;
    }
}
