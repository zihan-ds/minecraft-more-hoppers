package dev.morehoppers.gametest;

import dev.morehoppers.MoreHoppers;
import dev.morehoppers.block.entity.CopperHopperBlockEntity;
import dev.morehoppers.net.CopperHopperSlotPayload;
import dev.morehoppers.registry.ModBlocks;
import dev.morehoppers.registry.ModNetworking;
import dev.morehoppers.screen.CopperHopperScreenHandler;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmithingRecipe;
import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 把验收标准写成可执行检查：速率比值、红石停转、铜漏斗的 10 格/禁用/白名单、
 * 逆向漏斗朝上输送，以及"不需要模板的锻造配方"确实被数据包解析出来并可合成。
 * 用 Fabric 的空模板（8x8x8 空气），所有方块坐标都是相对结构的。
 */
public class HopperGameTest implements FabricGameTest {
    private static final int Z = 3;
    private static final int HOPPER_Y = 1;
    private static final int DOWN_TARGET_Y = 0;
    private static final int UP_SOURCE_Y = 0;
    private static final int UP_TARGET_Y = 2;
    private static final int SOURCE_STACKS = 4;
    private static final int SOURCE_ITEMS = SOURCE_STACKS * 64;
    private static final int MEASURE_TICKS = 80;

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void transferRatesMatchSpec(TestContext context) {
        buildDownwardLane(context, 1, Blocks.HOPPER);
        buildDownwardLane(context, 3, ModBlocks.GOLD_HOPPER);
        buildDownwardLane(context, 5, ModBlocks.DIAMOND_HOPPER);
        buildDownwardLane(context, 7, ModBlocks.NETHERITE_HOPPER);

        context.runAtTick(MEASURE_TICKS, () -> {
            int vanilla = countItems(context, pos(1, DOWN_TARGET_Y));
            int gold = countItems(context, pos(3, DOWN_TARGET_Y));
            int diamond = countItems(context, pos(5, DOWN_TARGET_Y));
            int netherite = countItems(context, pos(7, DOWN_TARGET_Y));
            MoreHoppers.LOGGER.info("{} tick 内传输：原版(8tick) {} / 金(4) {} / 钻石(2) {} / 下界合金(1) {}",
                    MEASURE_TICKS, vanilla, gold, diamond, netherite);

            // 直接按"每 N 游戏刻 1 件"断言绝对件数：既能精确区分 8/4/2/1 四种速率，
            // 也顺带证明原版漏斗（8 tick）没有被改动。
            assertNear(context, "原版漏斗 8 游戏刻 1 件", expectedItems(8), vanilla);
            assertNear(context, "金漏斗 4 游戏刻 1 件", expectedItems(4), gold);
            assertNear(context, "钻石漏斗 2 游戏刻 1 件", expectedItems(2), diamond);
            assertNear(context, "下界合金漏斗 1 游戏刻 1 件", expectedItems(1), netherite);
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void copperAndReverseHopperRates(TestContext context) {
        buildDownwardLane(context, 1, ModBlocks.COPPER_HOPPER);
        buildUpwardLane(context, 3, ModBlocks.REVERSE_HOPPER);

        context.runAtTick(MEASURE_TICKS, () -> {
            int copper = countItems(context, pos(1, DOWN_TARGET_Y));
            int reversed = countItems(context, pos(3, UP_TARGET_Y));
            MoreHoppers.LOGGER.info("{} tick 内传输：铜(向下) {} / 逆向(向上) {}", MEASURE_TICKS, copper, reversed);

            // 5 tick 一个 → 80 tick 约 15 件
            assertNear(context, "铜漏斗 5 游戏刻 1 件", expectedItems(5), copper);
            assertNear(context, "逆向漏斗 5 游戏刻 1 件", expectedItems(5), reversed);
            // 逆向漏斗的输入在下方、输出在上方：源箱减少
            context.assertTrue(countItems(context, pos(3, UP_SOURCE_Y)) < SOURCE_ITEMS,
                    "逆向漏斗应从下方箱子里取走物品");
            context.complete();
        });
    }

    /**
     * 回归检查：掉进逆向漏斗自己斗里的物品必须被吸走并送到上方容器，不能永远卡在漏斗里。
     * 场景里下方故意不放容器，所以物品只可能来自掉进斗里的这个实体。
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void reverseHopperPicksUpItemsDroppedIntoIt(TestContext context) {
        context.setBlockState(3, HOPPER_Y, Z, ModBlocks.REVERSE_HOPPER);
        context.setBlockState(3, UP_TARGET_Y, Z, Blocks.CHEST);

        BlockPos hopperPos = pos(3, HOPPER_Y);
        BlockPos absolute = context.getAbsolutePos(hopperPos);
        context.getWorld().spawnEntity(new ItemEntity(context.getWorld(),
                absolute.getX() + 0.5, absolute.getY() + 0.75, absolute.getZ() + 0.5,
                new ItemStack(Items.PAPER, 1)));

        context.runAtTick(60, () -> {
            int inHopper = countItems(context, hopperPos);
            int inTarget = countItems(context, pos(3, UP_TARGET_Y));
            context.assertTrue(inTarget == 1,
                    "掉进斗里的物品应被吸走并送到上方箱子，实际送到 " + inTarget + " 件");
            context.assertTrue(inHopper == 0, "漏斗里不应残留物品，实际残留 " + inHopper + " 件");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void redstoneStopsTransfer(TestContext context) {
        buildDownwardLane(context, 1, ModBlocks.GOLD_HOPPER);
        buildDownwardLane(context, 3, ModBlocks.GOLD_HOPPER);
        // 贴着 x=3 那条通道的漏斗放红石块
        context.setBlockState(4, HOPPER_Y, Z, Blocks.REDSTONE_BLOCK);

        context.runAtTick(60, () -> {
            int unpowered = countItems(context, pos(1, DOWN_TARGET_Y));
            int powered = countItems(context, pos(3, DOWN_TARGET_Y));
            context.assertTrue(powered == 0, "被红石充能的漏斗不应传输，实际传输了 " + powered + " 件");
            context.assertTrue(unpowered > 0, "未充能的漏斗应该在传输，实际 0 件");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void copperHopperSlotsAndFilter(TestContext context) {
        context.setBlockState(1, HOPPER_Y, Z, ModBlocks.COPPER_HOPPER);
        context.setBlockState(1, DOWN_TARGET_Y, Z, Blocks.CHEST);
        CopperHopperBlockEntity copper = context.getBlockEntity(new BlockPos(1, HOPPER_Y, Z));

        context.assertTrue(copper.size() == 10, "铜漏斗应有 10 个槽位，实际 " + copper.size());

        copper.setFilter(0, Items.DIAMOND);
        context.assertTrue(copper.isValid(0, new ItemStack(Items.DIAMOND)), "白名单槽应接受白名单物品");
        context.assertFalse(copper.isValid(0, new ItemStack(Items.PAPER)), "白名单槽不应接受其它物品");
        copper.setFilter(0, null);

        copper.setSlotEnabled(1, false);
        context.assertTrue(copper.isSlotDisabled(1), "槽位 1 应处于禁用状态");
        context.assertFalse(copper.isValid(1, new ItemStack(Items.PAPER)), "被禁用的槽位不应接受任何物品");

        // 禁用槽里的物品不能输出，启用槽里的物品要照常输出
        copper.setStack(0, new ItemStack(Items.PAPER, 1));
        copper.setStack(1, new ItemStack(Items.PAPER, 1));

        context.runAtTick(40, () -> {
            context.assertTrue(copper.getStack(1).getCount() == 1, "被禁用槽位里的物品应留在原地");
            context.assertTrue(copper.getStack(0).isEmpty(), "启用槽位里的物品应已被输出");
            context.assertTrue(countItems(context, pos(1, DOWN_TARGET_Y)) == 1, "下方箱子应正好收到 1 件");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
    public void netheriteHopperSmithingNeedsNoTemplate(TestContext context) {
        SmithingRecipeInput input = new SmithingRecipeInput(
                ItemStack.EMPTY,
                new ItemStack(ModBlocks.DIAMOND_HOPPER),
                new ItemStack(Items.NETHERITE_INGOT));

        List<RecipeEntry<SmithingRecipe>> matches = context.getWorld().getRecipeManager()
                .getAllMatches(RecipeType.SMITHING, input, context.getWorld());
        context.assertTrue(!matches.isEmpty(), "模板槽留空 + 钻石漏斗 + 下界合金锭 应匹配到配方");

        ItemStack result = matches.get(0).value().craft(input, context.getWorld().getRegistryManager());
        context.assertTrue(result.isOf(ModBlocks.NETHERITE_HOPPER.asItem()),
                "锻造结果应为下界合金漏斗，实际 " + result);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
    public void copperHopperSlotClicksAreValidated(TestContext context) {
        context.setBlockState(1, HOPPER_Y, Z, ModBlocks.COPPER_HOPPER);
        CopperHopperBlockEntity copper = context.getBlockEntity(new BlockPos(1, HOPPER_Y, Z));
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();

        // 没打开这个界面 → 整包丢弃
        context.assertFalse(ModNetworking.applySlotChange(player,
                        new CopperHopperSlotPayload(copper.getPos(), 0, false, ItemStack.EMPTY)),
                "没有打开对应界面时不应改动状态");
        context.assertFalse(copper.isSlotDisabled(0), "状态不应被改动");

        // 打开界面后：合法的"左键禁用 + 右键设白名单"生效
        player.currentScreenHandler = new CopperHopperScreenHandler(1, player.getInventory(), copper);
        context.assertTrue(ModNetworking.applySlotChange(player,
                        new CopperHopperSlotPayload(copper.getPos(), 0, false, new ItemStack(Items.DIAMOND))),
                "打开界面后的合法请求应生效");
        context.assertTrue(copper.isSlotDisabled(0), "槽位 0 应变为禁用");
        context.assertTrue(copper.getFilter(0) == Items.DIAMOND, "槽位 0 的白名单应为钻石");

        // 越界槽位 → 丢弃
        context.assertFalse(ModNetworking.applySlotChange(player,
                        new CopperHopperSlotPayload(copper.getPos(), 99, false, ItemStack.EMPTY)),
                "越界的槽位号应被丢弃");

        // 坐标与界面指向的方块实体不一致 → 丢弃
        context.assertFalse(ModNetworking.applySlotChange(player,
                        new CopperHopperSlotPayload(copper.getPos().up(), 1, false, ItemStack.EMPTY)),
                "坐标不匹配的请求应被丢弃");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
    public void reverseHopperOutlineMatchesFlippedModel(TestContext context) {
        VoxelShape vanilla = Blocks.HOPPER.getDefaultState()
                .getOutlineShape(context.getWorld(), BlockPos.ORIGIN, ShapeContext.absent());
        VoxelShape reversed = ModBlocks.REVERSE_HOPPER.getDefaultState()
                .getOutlineShape(context.getWorld(), BlockPos.ORIGIN, ShapeContext.absent());

        // 原版漏斗：喷口在正下方，顶部中央是漏斗口（空的）
        context.assertTrue(coversPoint(vanilla, 0.5, 0.1, 0.5), "原版漏斗在靠近底部处应有实体（喷口）");
        context.assertFalse(coversPoint(vanilla, 0.5, 0.9, 0.5), "原版漏斗顶部中央应是空的");

        // 逆向漏斗：模型上下翻转，轮廓必须同步翻转，否则选择线框会和方块错位
        context.assertTrue(coversPoint(reversed, 0.5, 0.9, 0.5), "逆向漏斗在靠近顶部处应有实体（喷口朝上）");
        context.assertFalse(coversPoint(reversed, 0.5, 0.1, 0.5), "逆向漏斗底部中央应是空的");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
    public void copperHopperSlotPayloadRoundTrips(TestContext context) {
        // "清除白名单 / 切换启用"时白名单是空 ItemStack，这条路径必须能编码：
        // 之前用 ItemStack.PACKET_CODEC 会抛 "Empty ItemStack not allowed" 并把玩家踢下线。
        for (ItemStack filter : List.of(ItemStack.EMPTY, new ItemStack(Items.DIAMOND))) {
            CopperHopperSlotPayload original = new CopperHopperSlotPayload(new BlockPos(4, 5, 6), 7, false, filter);
            RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());

            CopperHopperSlotPayload.CODEC.encode(buffer, original);
            CopperHopperSlotPayload decoded = CopperHopperSlotPayload.CODEC.decode(buffer);

            context.assertTrue(decoded.pos().equals(original.pos())
                            && decoded.slot() == original.slot()
                            && decoded.enabled() == original.enabled()
                            && decoded.filter().getItem() == original.filter().getItem()
                            && decoded.filter().getCount() == original.filter().getCount(),
                    "载荷编解码应保持一致，实际 " + decoded);
        }
        context.complete();
    }

    // ---------------------------------------------------------------- 工具

    private static BlockPos pos(int x, int y) {
        return new BlockPos(x, y, Z);
    }

    /** 上方箱子 → 漏斗 → 下方箱子（原版漏斗的常规朝向）。 */
    private static void buildDownwardLane(TestContext context, int x, Block hopper) {
        context.setBlockState(x, UP_TARGET_Y, Z, Blocks.CHEST);
        context.setBlockState(x, HOPPER_Y, Z, hopper);
        context.setBlockState(x, DOWN_TARGET_Y, Z, Blocks.CHEST);
        fill(context, pos(x, UP_TARGET_Y));
    }

    /** 下方箱子 → 漏斗 → 上方箱子（给逆向漏斗用）。 */
    private static void buildUpwardLane(TestContext context, int x, Block hopper) {
        context.setBlockState(x, UP_SOURCE_Y, Z, Blocks.CHEST);
        context.setBlockState(x, HOPPER_Y, Z, hopper);
        context.setBlockState(x, UP_TARGET_Y, Z, Blocks.CHEST);
        fill(context, pos(x, UP_SOURCE_Y));
    }

    /**
     * 回归检查：GUI 贴图必须是 256x256 画布。
     * DrawContext.drawTexture(Identifier,int,int,int,int,int,int) 这个 7 参数重载内部把
     * textureWidth/Height 写死成 256，贴图不是 256 宽就会被 UV 错位并整体拉伸
     * （曾经用 176x151 紧贴内容的画布，导致槽位框被放大 1.46 倍、面板右边被裁掉）。
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void guiTextureIsVanillaCanvasSize(TestContext context) {
        String path = "/assets/morehoppers/textures/gui/container/copper_hopper.png";
        try (InputStream in = HopperGameTest.class.getResourceAsStream(path)) {
            context.assertTrue(in != null, "找不到 GUI 贴图 " + path);
            byte[] header = in.readNBytes(24);
            int width = readInt(header, 16);
            int height = readInt(header, 20);
            context.assertEquals(256, width, "GUI 贴图画布宽度必须是 256，实际 " + width);
            context.assertEquals(256, height, "GUI 贴图画布高度必须是 256，实际 " + height);
        } catch (IOException e) {
            throw new IllegalStateException("读取 GUI 贴图失败: " + path, e);
        }
        context.complete();
    }

    /** PNG 头里 IHDR 的宽高（大端 4 字节）。 */
    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) << 24 | (data[offset + 1] & 0xFF) << 16
                | (data[offset + 2] & 0xFF) << 8 | (data[offset + 3] & 0xFF);
    }

    private static void fill(TestContext context, BlockPos pos) {
        Inventory inventory = inventoryAt(context, pos);
        for (int slot = 0; slot < SOURCE_STACKS; slot++) {
            inventory.setStack(slot, new ItemStack(Items.PAPER, 64));
        }
        inventory.markDirty();
    }

    private static Inventory inventoryAt(TestContext context, BlockPos pos) {
        BlockEntity blockEntity = context.getBlockEntity(pos);
        if (!(blockEntity instanceof Inventory inventory)) {
            throw new IllegalStateException("预期是容器，实际是 " + blockEntity + " @ " + pos);
        }
        return inventory;
    }

    private static int countItems(TestContext context, BlockPos pos) {
        Inventory inventory = inventoryAt(context, pos);
        int count = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            count += inventory.getStack(slot).getCount();
        }
        return count;
    }

    private static void assertNear(TestContext context, String label, int expected, int actual) {
        context.assertTrue(Math.abs(actual - expected) <= 2,
                label + "：期望约 " + expected + " 件，实际 " + actual + " 件");
    }

    /** 某点是否落在形状内部（用形状自身的包围盒判断，与实现细节无关）。 */
    private static boolean coversPoint(VoxelShape shape, double x, double y, double z) {
        for (Box box : shape.getBoundingBoxes()) {
            if (x >= box.minX && x <= box.maxX
                    && y >= box.minY && y <= box.maxY
                    && z >= box.minZ && z <= box.maxZ) {
                return true;
            }
        }
        return false;
    }

    /**
     * 一条"上方容器 → 漏斗 → 下方容器"的通道在 T tick 后应该送达多少件：
     * 第 1 tick 先把物品吸进漏斗，之后每 cooldownTicks 完成一次"推 1 件 + 吸 1 件"。
     */
    private static int expectedItems(int cooldownTicks) {
        return (MEASURE_TICKS - 1) / cooldownTicks;
    }
}
