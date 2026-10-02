package dev.morehoppers.screen;

import dev.morehoppers.block.entity.CopperHopperBlockEntity;
import dev.morehoppers.registry.ModScreens;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/**
 * 铜漏斗界面：10 个槽位排成 2 行 5 列（x=44+18i，y=20/38）+ 玩家背包。
 * 面板比原版漏斗高一行，所以玩家背包整体下移 18px（51→69、109→127），贴图同样下移。
 */
public class CopperHopperScreenHandler extends ScreenHandler {
    public static final int HOPPER_SLOTS = CopperHopperBlockEntity.SLOT_COUNT;

    private static final int HOPPER_COLUMNS = 5;
    private static final int HOPPER_ROWS = HOPPER_SLOTS / HOPPER_COLUMNS;
    private static final int SLOT_X = 44;
    private static final int SLOT_Y = 20;
    private static final int PLAYER_INVENTORY_Y = 69;
    private static final int HOTBAR_Y = 127;

    private final Inventory inventory;
    private final BlockPos pos;

    /** 客户端构造：ScreenHandlerType 的工厂走这里，物品由服务端同步，坐标由 ExtendedScreenHandlerType 下发。 */
    public CopperHopperScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory, new SimpleInventory(HOPPER_SLOTS), pos);
    }

    /** 服务端构造：直接绑定真实方块实体。 */
    public CopperHopperScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        this(syncId, playerInventory, inventory,
                inventory instanceof CopperHopperBlockEntity hopper ? hopper.getPos() : BlockPos.ORIGIN);
    }

    private CopperHopperScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, BlockPos pos) {
        super(ModScreens.COPPER_HOPPER, syncId);
        checkSize(inventory, HOPPER_SLOTS);
        this.inventory = inventory;
        this.pos = pos;
        inventory.onOpen(playerInventory.player);

        for (int row = 0; row < HOPPER_ROWS; row++) {
            for (int column = 0; column < HOPPER_COLUMNS; column++) {
                int index = column + row * HOPPER_COLUMNS;
                this.addSlot(new Slot(inventory, index, SLOT_X + column * 18, SLOT_Y + row * 18) {
                    @Override
                    public boolean canInsert(ItemStack stack) {
                        // 服务端容器是铜漏斗本体，禁用槽与白名单在这里生效；客户端是占位容器，交给服务端判定
                        return !(inventory instanceof CopperHopperBlockEntity hopper)
                                || hopper.isValid(index, stack);
                    }
                });
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        8 + column * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, HOTBAR_Y));
        }
    }

    public Inventory getHopperInventory() {
        return this.inventory;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return !(this.inventory instanceof CopperHopperBlockEntity hopper)
                || Inventory.canPlayerUse(hopper, player);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        this.inventory.onClose(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack stack = slot.getStack();
            result = stack.copy();
            if (index < HOPPER_SLOTS) {
                if (!this.insertItem(stack, HOPPER_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.insertItem(stack, 0, HOPPER_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
            if (stack.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTakeItem(player, stack);
        }
        return result;
    }
}
