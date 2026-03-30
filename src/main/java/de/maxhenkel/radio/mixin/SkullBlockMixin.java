package de.maxhenkel.radio.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import de.maxhenkel.radio.radio.RadioData;
import de.maxhenkel.radio.radio.RadioManager;
import de.maxhenkel.radio.utils.IPossibleRadioBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSkullBlock.class)
public class SkullBlockMixin {

    // \/\/\/ Radio is toggleable. Redstone impulse toggles the state (like a copper bulb)

    @Inject(
            method = "neighborChanged(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/redstone/Orientation;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
    ))
    public void handleRedstoneToggle(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean movedByPiston, CallbackInfo ci, @Local(name = "signal") boolean isPowered) {
        // @At already checked if signal != block powered state, as well as this being server-side

        level.getBlockEntity(pos, BlockEntityType.SKULL).ifPresent(skull -> {
            if(isPowered && skull instanceof IPossibleRadioBlock radio && radio.radio$isRadio()) {
                RadioData radioData = radio.radio$getRadioData();
                radioData.toggleOn();
                RadioManager.getInstance().updateHeadOnState(radioData.getId(), radioData.isOn());
            }
        });
    }


}
