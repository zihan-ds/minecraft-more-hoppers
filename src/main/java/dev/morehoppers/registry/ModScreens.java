package dev.morehoppers.registry;

import dev.morehoppers.MoreHoppers;
import dev.morehoppers.screen.CopperHopperScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.math.BlockPos;

public final class ModScreens {
    /**
     * ExtendedScreenHandlerType 会把方块坐标一并发给客户端，铜漏斗界面才能找到客户端的方块实体
     * 来画禁用遮罩与白名单幽灵物品。
     */
    public static final ScreenHandlerType<CopperHopperScreenHandler> COPPER_HOPPER =
            new ExtendedScreenHandlerType<>(CopperHopperScreenHandler::new, BlockPos.PACKET_CODEC);

    private ModScreens() {
    }

    public static void register() {
        Registry.register(Registries.SCREEN_HANDLER, MoreHoppers.id("copper_hopper"), COPPER_HOPPER);
    }
}
