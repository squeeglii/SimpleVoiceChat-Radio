package de.maxhenkel.radio.mixin;

import com.mojang.authlib.GameProfile;
import de.maxhenkel.radio.radio.RadioData;
import de.maxhenkel.radio.radio.RadioItem;
import de.maxhenkel.radio.radio.RadioManager;
import de.maxhenkel.radio.utils.IPossibleRadioBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PlayerHeadBlock;
import net.minecraft.world.level.block.PlayerWallHeadBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class BlockMixin {

    @Inject(method = "playerDestroy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V"), cancellable = true)
    public void playerDestroy(Level level, Player player, BlockPos blockPos, BlockState blockState, BlockEntity blockEntity, ItemStack itemStack, CallbackInfo ci) {
        if (level.isClientSide())
            return;

        boolean isNotPlayerHeadBlock = !blockState.getBlock().equals(Blocks.PLAYER_HEAD) &&
                                       !blockState.getBlock().equals(Blocks.PLAYER_WALL_HEAD);
        if (isNotPlayerHeadBlock) return;

        if (!(blockEntity instanceof IPossibleRadioBlock radioBlock))
            return;

        if (radioBlock.radio$isRadio()) {
            RadioData radioData = radioBlock.radio$getRadioData();
            RadioManager.getInstance().onRemoveHead(radioData.getId());
            ItemStack speakerItem = RadioItem.fromData(radioData);
            Block.popResource(level, blockPos, speakerItem);
            ci.cancel();
        }
    }

    @Inject(method = "playerWillDestroy", at = @At(value = "HEAD"))
    public void destroy(Level level, BlockPos blockPos, BlockState blockState, Player player, CallbackInfoReturnable<BlockState> cir) {
        if (level.isClientSide())
            return;

        boolean isNotPlayerHeadBlock = !blockState.getBlock().equals(Blocks.PLAYER_HEAD) &&
                                       !blockState.getBlock().equals(Blocks.PLAYER_WALL_HEAD);
        if (isNotPlayerHeadBlock) return;

        BlockEntity blockEntity = level.getBlockEntity(blockPos);

        if (!(blockEntity instanceof IPossibleRadioBlock radioBlock))
            return;

        if (radioBlock.radio$isRadio())
            RadioManager.getInstance().onRemoveHead(radioBlock.radio$getRadioData().getId());
    }

    // TODO Stop radio when block is broken by explosion or non-player

}
