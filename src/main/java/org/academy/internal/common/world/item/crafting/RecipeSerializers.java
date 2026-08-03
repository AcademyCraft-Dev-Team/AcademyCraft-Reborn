package org.academy.internal.common.world.item.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static org.academy.AcademyCraft.MODID;

public final class RecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
    static final StreamCodec<RegistryFriendlyByteBuf, DarkmatterDuplicationRecipe>
            DARKMATTER_DUPLICATION_STREAM_CODEC = StreamCodec.of(
                    (buffer, recipe) -> {
                    },
                    buffer -> new DarkmatterDuplicationRecipe()
            );
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<DarkmatterDuplicationRecipe>>
            DARKMATTER_DUPLICATION = RECIPE_SERIALIZERS.register("darkmatter_duplication", () ->
                    new RecipeSerializer<>(
                            MapCodec.unit(DarkmatterDuplicationRecipe::new),
                            DARKMATTER_DUPLICATION_STREAM_CODEC
                    ));

    private RecipeSerializers() {
    }
}
