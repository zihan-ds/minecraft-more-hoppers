package dev.morehoppers.registry;

import dev.morehoppers.MoreHoppers;
import dev.morehoppers.recipe.NoTemplateSmithingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModRecipes {
    /** 下界合金漏斗用：锻造台配方但不需要模板。 */
    public static final NoTemplateSmithingRecipe.Serializer NO_TEMPLATE_SMITHING =
            new NoTemplateSmithingRecipe.Serializer();

    private ModRecipes() {
    }

    public static void register() {
        Registry.register(Registries.RECIPE_SERIALIZER,
                MoreHoppers.id("no_template_smithing"), NO_TEMPLATE_SMITHING);
    }
}
