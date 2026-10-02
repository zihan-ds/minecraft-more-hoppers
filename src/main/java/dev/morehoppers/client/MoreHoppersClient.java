package dev.morehoppers.client;

import dev.morehoppers.registry.ModScreens;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class MoreHoppersClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Fabric API 的 access widener 已经把 HandledScreens.register 与其 Provider 放开为 public，
        // 所以这里直接用原版注册表，不需要自己写 mixin。
        HandledScreens.register(ModScreens.COPPER_HOPPER, CopperHopperScreen::new);
    }
}
