package de.maxhenkel.radio.mixin;

import de.maxhenkel.radio.radio.RadioData;
import de.maxhenkel.radio.radio.RadioItem;
import de.maxhenkel.radio.radio.RadioManager;
import de.maxhenkel.radio.utils.IPossibleRadioBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(SkullBlockEntity.class)
public class SkullBlockEntityMixin extends BlockEntity implements IPossibleRadioBlock {

    @Unique
    private RadioData data = null;

    public SkullBlockEntityMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    // block saving / loading
    @Inject(method = "loadAdditional", at = @At("RETURN"))
    public void load(ValueInput valueInput, CallbackInfo ci) {
        this.data = valueInput.read(RadioData.NBT_CATEGORY, RadioData.CODEC).orElse(null);

        if (this.data == null && this.level != null && !this.level.isClientSide()) {
            this.data = RadioManager.getInstance().loadHeadFromGameProfile((SkullBlockEntity) (Object) this).orElse(null);
        }

        if(this.data != null && this.level != null && !this.level.isClientSide()) {
            RadioManager.getInstance().updateRadioStream(this.data, (ServerLevel) this.level, (SkullBlockEntity) (Object) this);
        }
    }

    @Inject(method = "saveAdditional", at = @At("RETURN"))
    public void save(ValueOutput valueOutput, CallbackInfo ci) {
        if(this.data != null) {
            valueOutput.store(RadioData.NBT_CATEGORY, RadioData.CODEC, this.data);
        }
    }

    // item conversion ---
    @Inject(method = "applyImplicitComponents(Lnet/minecraft/core/component/DataComponentGetter;)V", at = @At("RETURN"))
    protected void applyExtraComponents(DataComponentGetter dataComponentGetter, CallbackInfo ci) {
        // Mark both types as checked so they get removed from the held components.
        CustomData data = dataComponentGetter.get(DataComponents.CUSTOM_DATA);
        if(data == null)
            return;

        Optional<RadioData> radioDevice = RadioItem.readRadioData(data);
        if(radioDevice.isEmpty())
            return;

        this.data = radioDevice.get();

        // data is non-null by this point - no need to check.
        if(this.level != null && !this.level.isClientSide()) {
            RadioManager.getInstance().updateRadioStream(this.data, (ServerLevel) this.level, (SkullBlockEntity) (Object) this);
        }
    }

    @Inject(method = "collectImplicitComponents(Lnet/minecraft/core/component/DataComponentMap$Builder;)V", at = @At("RETURN"))
    protected void collectExtraComponents(DataComponentMap.Builder builder, CallbackInfo ci) {
        if(this.data == null) return;

        CustomData data = this.data.saveToNewItemData();
        builder.set(DataComponents.CUSTOM_DATA, data);
    }

    @Override
    public void setLevel(Level newLevel) {
        Level oldLevel = level;
        super.setLevel(newLevel);

        if (oldLevel == null && newLevel != null && !newLevel.isClientSide()) {
            if(this.data == null)
                this.data = RadioManager.getInstance().loadHeadFromGameProfile((SkullBlockEntity) (Object) this).orElse(null);

            if(this.data != null)
                RadioManager.getInstance().updateRadioStream(this.data, (ServerLevel) newLevel, (SkullBlockEntity) (Object) this);
        }
    }

    @Override
    public RadioData radio$getRadioData() {
        return this.data;
    }

    @Override
    public boolean radio$isRadio() {
        return this.data != null;
    }
}
