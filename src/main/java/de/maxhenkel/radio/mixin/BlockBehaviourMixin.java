package de.maxhenkel.radio.mixin;

import com.mojang.authlib.GameProfile;
import de.maxhenkel.radio.radio.RadioData;
import de.maxhenkel.radio.radio.RadioManager;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
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

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    public void use(BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (level.isClientSide()) {
            return;
        }
        if (!interactionHand.equals(InteractionHand.MAIN_HAND)) {
            return;
        }
        if (!blockState.getBlock().equals(Blocks.PLAYER_HEAD) && !blockState.getBlock().equals(Blocks.PLAYER_WALL_HEAD)) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(blockPos);

        if (!(blockEntity instanceof SkullBlockEntity skullBlockEntity)) {
            return;
        }

        GameProfile profile = skullBlockEntity.getOwnerProfile();
        RadioData radioData = RadioData.fromGameProfile(profile);
        if (radioData == null) {
            return;
        }

        radioData.setOn(!radioData.isOn());
        radioData.updateProfile(profile);
        skullBlockEntity.setChanged();
        RadioManager.getInstance().updateHeadOnState(radioData.getId(), radioData.isOn());

        level.playSound(null, blockPos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1F, 1F);

        cir.setReturnValue(InteractionResult.CONSUME);
        cir.cancel();
    }

}
