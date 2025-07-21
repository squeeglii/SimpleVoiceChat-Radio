package de.maxhenkel.radio.mixin;

import de.maxhenkel.radio.radio.RadioData;
import de.maxhenkel.radio.radio.RadioManager;
import de.maxhenkel.radio.utils.IPossibleRadioBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.class)
public class BlockBehaviourMixin {

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    public void use(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (level.isClientSide())
            return;

        boolean isNotPlayerHeadBlock = !blockState.getBlock().equals(Blocks.PLAYER_HEAD) &&
                                    !blockState.getBlock().equals(Blocks.PLAYER_WALL_HEAD);
        if (isNotPlayerHeadBlock) return;


        BlockEntity blockEntity = level.getBlockEntity(blockPos);
        if(!(blockEntity instanceof SkullBlockEntity skullBlockEntity)) return;
        if(!(blockEntity instanceof IPossibleRadioBlock radioBlock)) return;
        if(!radioBlock.radio$isRadio()) return;

        RadioData data = radioBlock.radio$getRadioData();
        data.toggleOn();
        skullBlockEntity.setChanged();

        //todo: move this to save?
        RadioManager.getInstance().updateHeadOnState(data.getId(), data.isOn());
        String displayState = data.isOn() ? "on" : "off";

        level.playSound(null, blockPos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1F, 1F);
        player.displayClientMessage(Component.literal("Toggled radio %s.".formatted(displayState)), false);

        cir.setReturnValue(InteractionResult.CONSUME);    // I forget why I changed this but I'm fairly sure it was a bug fix.
        cir.cancel();
    }

}
