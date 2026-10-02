package dev.morehoppers.client;

import dev.morehoppers.MoreHoppers;
import dev.morehoppers.block.entity.CopperHopperBlockEntity;
import dev.morehoppers.net.CopperHopperSlotPayload;
import dev.morehoppers.screen.CopperHopperScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 铜漏斗界面。交互按原版合成器的规则改写：
 *   左键点空槽 = 切换该槽启用/禁用（禁用时要求手上没有物品）；右键 = 手持物品设为该槽白名单，空手清除。
 * 禁用遮罩复用原版合成器的 disabled_slot 贴图，白名单以幽灵物品显示在空槽里。
 */
public class CopperHopperScreen extends HandledScreen<CopperHopperScreenHandler> {
    private static final Identifier TEXTURE = MoreHoppers.id("textures/gui/container/copper_hopper.png");
    private static final Identifier DISABLED_SLOT = Identifier.ofVanilla("container/crafter/disabled_slot");

    public CopperHopperScreen(CopperHopperScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 151;
        // 与原版漏斗同一关系：标题行始终在玩家背包上方 12px
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    private int originX() {
        return (this.width - this.backgroundWidth) / 2;
    }

    private int originY() {
        return (this.height - this.backgroundHeight) / 2;
    }

    private CopperHopperBlockEntity hopper() {
        if (this.client == null || this.client.world == null) {
            return null;
        }
        return this.client.world.getBlockEntity(this.handler.getPos()) instanceof CopperHopperBlockEntity copper
                ? copper
                : null;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(TEXTURE, this.originX(), this.originY(), 0, 0, this.backgroundWidth, this.backgroundHeight);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.drawSlotStates(context);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    private void drawSlotStates(DrawContext context) {
        CopperHopperBlockEntity hopper = this.hopper();
        if (hopper == null) {
            return;
        }
        int originX = this.originX();
        int originY = this.originY();
        for (int i = 0; i < CopperHopperScreenHandler.HOPPER_SLOTS; i++) {
            Slot slot = this.handler.getSlot(i);
            int x = originX + slot.x;
            int y = originY + slot.y;
            if (hopper.isSlotDisabled(i)) {
                context.drawGuiTexture(DISABLED_SLOT, x - 1, y - 1, 18, 18);
                continue;
            }
            Item filter = hopper.getFilter(i);
            if (filter != null && hopper.getStack(i).isEmpty()) {
                context.drawItem(new ItemStack(filter), x, y);
            }
        }
    }

    @Override
    protected void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType) {
        if (slot != null
                && slot.inventory == this.handler.getHopperInventory()
                && actionType == SlotActionType.PICKUP
                && this.client != null
                && this.client.player != null
                && !this.client.player.isSpectator()) {
            CopperHopperBlockEntity hopper = this.hopper();
            if (hopper != null) {
                int index = slot.id;
                if (button == 0) {
                    // 左键：切换启用/禁用
                    if (hopper.isSlotDisabled(index)) {
                        this.sendSlotState(index, true, hopper.getFilter(index));
                        return;
                    }
                    if (this.handler.getCursorStack().isEmpty()) {
                        this.sendSlotState(index, false, hopper.getFilter(index));
                        return;
                    }
                } else if (button == 1) {
                    // 右键：设定/清除该槽白名单，不改动启用状态
                    ItemStack cursor = this.handler.getCursorStack();
                    this.sendSlotState(index, !hopper.isSlotDisabled(index),
                            cursor.isEmpty() ? null : cursor.getItem());
                    return;
                }
            }
        }
        super.onMouseClick(slot, slotId, button, actionType);
    }

    private void sendSlotState(int slot, boolean enabled, Item filter) {
        ClientPlayNetworking.send(new CopperHopperSlotPayload(this.handler.getPos(), slot, enabled,
                filter == null ? ItemStack.EMPTY : new ItemStack(filter)));
    }
}
