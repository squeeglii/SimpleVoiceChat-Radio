package de.maxhenkel.radio.mixin;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

@Mixin(targets = { "net.minecraft.world.level.block.entity.BlockEntity$1" })
@SuppressWarnings({"unchecked", "rawtypes"})
public abstract class PreserveBlockEntityCustomDataMixin {


    @Redirect(
            method = "get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Set;add(Ljava/lang/Object;)Z"
    ))
    public <E> boolean preserveGet(Set instance, E e) {
        return preserveCustomDataFromPatch(instance, e);
    }

    @Redirect(
            method = "getOrDefault(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Set;add(Ljava/lang/Object;)Z"
            ))
    public <E> boolean preserveGetOrDefault(Set instance, E e) {
        return preserveCustomDataFromPatch(instance, e);
    }


    @Unique
    private <E> boolean preserveCustomDataFromPatch(Set instance, E e) {
        if(!(e instanceof DataComponentType<?> comp)) {
            return instance.add(e); // no clue what's going on if this happens.
        }

        if(comp.equals(DataComponents.CUSTOM_DATA))
            return false;

        return instance.add(e);
    }
}
