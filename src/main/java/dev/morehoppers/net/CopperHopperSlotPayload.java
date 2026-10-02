package dev.morehoppers.net;

import dev.morehoppers.MoreHoppers;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * 客户端点槽位 → 服务端：目标坐标、槽位号、是否启用、该槽白名单（空物品 = 不限制）。
 * 白名单只按物品类型生效，所以发送整摞物品没有意义，但沿用原版 ItemStack 编解码最简单。
 *
 * 注意必须用 OPTIONAL_PACKET_CODEC：无白名单（清除/切换启用）时发的是空 ItemStack，
 * 而 PACKET_CODEC 遇到空物品会抛 "Empty ItemStack not allowed" 并把玩家踢下线。
 */
public record CopperHopperSlotPayload(BlockPos pos, int slot, boolean enabled, ItemStack filter)
        implements CustomPayload {
    public static final CustomPayload.Id<CopperHopperSlotPayload> ID =
            new CustomPayload.Id<>(MoreHoppers.id("copper_hopper_slot"));

    public static final PacketCodec<RegistryByteBuf, CopperHopperSlotPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, CopperHopperSlotPayload::pos,
            PacketCodecs.VAR_INT, CopperHopperSlotPayload::slot,
            PacketCodecs.BOOL, CopperHopperSlotPayload::enabled,
            ItemStack.OPTIONAL_PACKET_CODEC, CopperHopperSlotPayload::filter,
            CopperHopperSlotPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
