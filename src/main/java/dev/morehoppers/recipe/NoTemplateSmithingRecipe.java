package dev.morehoppers.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.morehoppers.registry.ModRecipes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SmithingRecipe;
import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

/**
 * 锻造台配方，但不需要模板（下界合金漏斗用）。
 *
 * 仍然实现 SmithingRecipe 并沿用 RecipeType.SMITHING，所以锻造台界面、配方书、合成逻辑都是原版的；
 * 只有模板槽的判定改了：testTemplate 仅在模板槽为空时成立，于是
 *   - matches 要求模板槽留空 + 基座/附加匹配；
 *   - 锻造台的 shift-click 分流（getQuickMoveSlot 会先问 testTemplate）仍会把基座物品放进基座槽。
 */
public class NoTemplateSmithingRecipe implements SmithingRecipe {
    private final Ingredient base;
    private final Ingredient addition;
    private final ItemStack result;

    public NoTemplateSmithingRecipe(Ingredient base, Ingredient addition, ItemStack result) {
        this.base = base;
        this.addition = addition;
        this.result = result;
    }

    @Override
    public boolean matches(SmithingRecipeInput input, World world) {
        return input.template().isEmpty()
                && this.base.test(input.base())
                && this.addition.test(input.addition());
    }

    @Override
    public ItemStack craft(SmithingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        return this.result.copy();
    }

    @Override
    public ItemStack getResult(RegistryWrapper.WrapperLookup registries) {
        return this.result;
    }

    @Override
    public boolean testTemplate(ItemStack stack) {
        return stack.isEmpty();
    }

    @Override
    public boolean testBase(ItemStack stack) {
        return this.base.test(stack);
    }

    @Override
    public boolean testAddition(ItemStack stack) {
        return this.addition.test(stack);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.NO_TEMPLATE_SMITHING;
    }

    public static class Serializer implements RecipeSerializer<NoTemplateSmithingRecipe> {
        private static final MapCodec<NoTemplateSmithingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                                Ingredient.DISALLOW_EMPTY_CODEC.fieldOf("base").forGetter(recipe -> recipe.base),
                                Ingredient.DISALLOW_EMPTY_CODEC.fieldOf("addition").forGetter(recipe -> recipe.addition),
                                ItemStack.CODEC.fieldOf("result").forGetter(recipe -> recipe.result))
                        .apply(instance, NoTemplateSmithingRecipe::new));

        private static final PacketCodec<RegistryByteBuf, NoTemplateSmithingRecipe> PACKET_CODEC = PacketCodec.tuple(
                Ingredient.PACKET_CODEC, recipe -> recipe.base,
                Ingredient.PACKET_CODEC, recipe -> recipe.addition,
                ItemStack.PACKET_CODEC, recipe -> recipe.result,
                NoTemplateSmithingRecipe::new);

        @Override
        public MapCodec<NoTemplateSmithingRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<RegistryByteBuf, NoTemplateSmithingRecipe> packetCodec() {
            return PACKET_CODEC;
        }
    }
}
