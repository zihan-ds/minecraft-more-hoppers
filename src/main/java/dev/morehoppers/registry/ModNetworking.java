package dev.morehoppers.registry;

import dev.morehoppers.block.entity.CopperHopperBlockEntity;
import dev.morehoppers.net.CopperHopperSlotPayload;
import dev.morehoppers.screen.CopperHopperScreenHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ModNetworking {
    private ModNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(CopperHopperSlotPayload.ID, CopperHopperSlotPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CopperHopperSlotPayload.ID,
                (payload, context) -> applySlotChange(context.player(), payload));
    }

    /**
     * 处理"客户端点了某个槽位"的请求，返回是否真的改动了状态。
     *
     * 信任边界校验：服务端只认"玩家当前打开的就是这个铜漏斗的界面"这一条链路
     * （原版会在玩家走远时用 canUse 关掉界面，所以这条同时覆盖了距离校验），
     * 再加上载荷里的坐标必须与界面指向的方块实体一致、槽位号必须在 0..9，否则整包丢弃。
     */
    public static boolean applySlotChange(ServerPlayerEntity player, CopperHopperSlotPayload payload) {
        if (!(player.currentScreenHandler instanceof CopperHopperScreenHandler handler)) {
            return false;
        }
        if (!(handler.getHopperInventory() instanceof CopperHopperBlockEntity copper)) {
            return false;
        }
        if (!copper.getPos().equals(payload.pos())
                || player.getWorld().getBlockEntity(copper.getPos()) != copper) {
            return false;
        }
        int slot = payload.slot();
        if (slot < 0 || slot >= CopperHopperBlockEntity.SLOT_COUNT) {
            return false;
        }
        copper.setSlotEnabled(slot, payload.enabled());
        ItemStack filter = payload.filter();
        copper.setFilter(slot, filter.isEmpty() ? null : filter.getItem());
        return true;
    }
}
