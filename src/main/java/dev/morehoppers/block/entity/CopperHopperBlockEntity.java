package dev.morehoppers.block.entity;

import dev.morehoppers.registry.ModScreens;
import dev.morehoppers.screen.CopperHopperScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

/**
 * 铜漏斗：10 格，每格可单独禁用、可设一个"只允许该物品通过"的白名单。
 *
 * 输入侧约束放在 isValid（原版 transfer -> canInsert -> isValid，所以别的漏斗往里塞东西也一样受限），
 * 输出侧禁用槽由 HopperBlockEntityMixin 跳过；界面打开走 Fabric 的 ExtendedScreenHandlerFactory，
 * 客户端拿得到方块坐标，禁用/白名单状态用原版方块实体同步链路（NBT）下发。
 */
public class CopperHopperBlockEntity extends HopperBlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {
    public static final int SLOT_COUNT = 10;

    private static final String NBT_DISABLED = "DisabledSlots";
    private static final String NBT_FILTERS = "Filters";

    private final boolean[] disabledSlots = new boolean[SLOT_COUNT];
    private final Item[] filters = new Item[SLOT_COUNT];

    /** 只有在原版 insert 正在挑选要输出的槽位时才为 true（由 HopperBlockEntityMixin 维护）。 */
    private boolean selectingOutputSlots;

    public CopperHopperBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        // 原版构造函数固定给 5 格，且 size() 直接返回这个列表的大小，所以扩容只能在这里做。
        this.setHeldStacks(DefaultedList.ofSize(SLOT_COUNT, ItemStack.EMPTY));
    }

    public boolean isSlotDisabled(int slot) {
        return slot >= 0 && slot < SLOT_COUNT && this.disabledSlots[slot];
    }

    /** 返回该槽位的白名单物品，未设定时为 null。 */
    public Item getFilter(int slot) {
        return slot >= 0 && slot < SLOT_COUNT ? this.filters[slot] : null;
    }

    public void setSlotEnabled(int slot, boolean enabled) {
        if (slot < 0 || slot >= SLOT_COUNT || this.disabledSlots[slot] == !enabled) {
            return;
        }
        this.disabledSlots[slot] = !enabled;
        this.stateChanged();
    }

    public void setFilter(int slot, Item item) {
        if (slot < 0 || slot >= SLOT_COUNT || this.filters[slot] == item) {
            return;
        }
        this.filters[slot] = item;
        this.stateChanged();
    }

    private void stateChanged() {
        this.markDirty();
        if (this.world != null && !this.world.isClient) {
            this.world.updateListeners(this.pos, this.getCachedState(), this.getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    /** 被禁用的槽位不接受任何物品；设了白名单的槽位只接受该物品（按物品类型，忽略组件与数量）。 */
    @Override
    public boolean isValid(int slot, ItemStack stack) {
        if (this.isSlotDisabled(slot)) {
            return false;
        }
        Item filter = this.getFilter(slot);
        return filter == null || stack.isOf(filter);
    }

    public void setSelectingOutputSlots(boolean selecting) {
        this.selectingOutputSlots = selecting;
    }

    /**
     * 原版 insert 逐格读取槽位内容来决定推出什么，这里在它挑选期间把被禁用的槽位伪装成空的，
     * 于是禁用槽里的物品既不输出，也不会让 insert 提前返回（阻塞其它槽位）。
     */
    @Override
    public ItemStack getStack(int slot) {
        if (this.selectingOutputSlots && this.isSlotDisabled(slot)) {
            return ItemStack.EMPTY;
        }
        return super.getStack(slot);
    }

    @Override
    protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory) {
        return new CopperHopperScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
        return this.pos;
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        int mask = nbt.getInt(NBT_DISABLED);
        NbtList list = nbt.getList(NBT_FILTERS, NbtElement.STRING_TYPE);
        for (int i = 0; i < SLOT_COUNT; i++) {
            this.disabledSlots[i] = (mask & (1 << i)) != 0;
            this.filters[i] = null;
            if (i < list.size()) {
                Identifier id = Identifier.tryParse(list.getString(i));
                if (id != null) {
                    this.filters[i] = Registries.ITEM.getOrEmpty(id).orElse(null);
                }
            }
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        int mask = 0;
        NbtList list = new NbtList();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (this.disabledSlots[i]) {
                mask |= 1 << i;
            }
            Item filter = this.filters[i];
            list.add(NbtString.of(filter == null ? "" : Registries.ITEM.getId(filter).toString()));
        }
        nbt.putInt(NBT_DISABLED, mask);
        nbt.put(NBT_FILTERS, list);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return this.createNbt(registries);
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    /** 供界面与网络接收器做类型校验用。 */
    public Inventory asInventory() {
        return this;
    }
}
