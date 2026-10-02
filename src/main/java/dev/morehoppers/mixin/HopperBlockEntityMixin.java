package dev.morehoppers.mixin;

import dev.morehoppers.block.HopperSpeed;
import dev.morehoppers.block.ReverseHopperBlock;
import dev.morehoppers.block.entity.CopperHopperBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 三种漏斗机制全部通过这一处补丁挂到原版漏斗上，不复制任何原版传输逻辑：
 *   1. 传输速率：原版成功传输后固定写 8 冷却，这里按方块声明的 tick 数写入（原版漏斗不变）。
 *   2. 逆向漏斗：输出容器从上/前改为正上方。
 *   3. 铜漏斗：被禁用的槽位不参与输出（同时不打断 insert 的槽位循环）。
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Shadow
    private int transferCooldown;

    @Inject(method = "setTransferCooldown", at = @At("HEAD"), cancellable = true)
    private void morehoppers$applyTransferSpeed(int cooldown, CallbackInfo ci) {
        if (cooldown > 0
                && ((BlockEntity) (Object) this).getCachedState().getBlock() instanceof HopperSpeed speed) {
            this.transferCooldown = speed.transferCooldownTicks();
            ci.cancel();
        }
    }

    /**
     * 标题：原版把漏斗容器的名字写死成 container.hopper（英文显示就是 "Item Hopper"），
     * 所有复用原版方块实体的自定义漏斗都会显示成原版漏斗的名字。这里对自定义漏斗改用方块自身名字。
     */
    @Inject(method = "getContainerName", at = @At("HEAD"), cancellable = true)
    private void morehoppers$useBlockName(CallbackInfoReturnable<Text> cir) {
        Block block = ((BlockEntity) (Object) this).getCachedState().getBlock();
        if (block instanceof HopperSpeed) {
            cir.setReturnValue(Text.translatable(block.getTranslationKey()));
        }
    }

    @Inject(method = "getOutputInventory", at = @At("HEAD"), cancellable = true)
    private static void morehoppers$reverseOutputTowards(World world, BlockPos pos, HopperBlockEntity blockEntity,
                                                        CallbackInfoReturnable<Inventory> cir) {
        if (blockEntity.getCachedState().getBlock() instanceof ReverseHopperBlock) {
            cir.setReturnValue(HopperBlockEntity.getInventoryAt(world, pos.up()));
        }
    }

    /**
     * 铜漏斗：原版 insert 逐格把物品推出去，而"哪些格禁用"只有我们的方块实体知道。
     * 这里在 insert 前后打一个"正在输出"的标记，方块实体在标记期间对被禁用的槽位装作是空的
     * （既不输出，也不会打断 insert 的槽位循环）。用 @Inject 而不是 @Redirect，是因为当前
     * 环境里的 MixinExtras 对带 at 的 @Redirect 有解析 bug。
     */
    @Inject(method = "insert", at = @At("HEAD"))
    private static void morehoppers$beginInsert(World world, BlockPos pos, HopperBlockEntity blockEntity,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (blockEntity instanceof CopperHopperBlockEntity copper) {
            copper.setSelectingOutputSlots(true);
        }
    }

    @Inject(method = "insert", at = @At("RETURN"))
    private static void morehoppers$endInsert(World world, BlockPos pos, HopperBlockEntity blockEntity,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (blockEntity instanceof CopperHopperBlockEntity copper) {
            copper.setSelectingOutputSlots(false);
        }
    }
}
