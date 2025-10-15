package de.maxhenkel.radio.radio;

import com.mojang.authlib.GameProfile;
import de.maxhenkel.radio.Radio;
import de.maxhenkel.radio.utils.HeadUtils;
import it.unimi.dsi.fastutil.objects.ReferenceSortedSets;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class RadioItem {

    public static ItemStack newRadio(String url, String stationName, boolean on, float range) {
        RadioData data = RadioData.createAnonymousRadioData(url, stationName, on, range);
        return buildStack(data);
    }

    public static ItemStack fromData(RadioData radioData) {
        if(radioData == null) throw new NullPointerException("Radio Data must not be null");
        RadioData data = radioData.getId() == Util.NIL_UUID
                ? radioData
                : RadioData.createAnonymousRadioData(radioData.getUrl(), radioData.getStationName(), radioData.isOn(), radioData.getRange());

        return buildStack(data);
    }

    private static ItemStack buildStack(RadioData data) {
        // profile UUID can be random.
        ItemStack item =  HeadUtils.createHead(UUID.randomUUID(), "radio", Radio.SERVER_CONFIG.radioSkinUrl.get());

        List<Component> lore = new LinkedList<>();

        lore.add(Component.literal("Playing: "+ data.getStationName())
                .withStyle(style -> style.withItalic(false))
                .withStyle(ChatFormatting.GRAY));

        if(!data.isRangeDefault()) {
            lore.add(Component.literal("Range: %.1f block(s)".formatted(data.getRange()))
                    .withStyle(style -> style.withItalic(false))
                    .withStyle(ChatFormatting.GRAY));
        }

        data.serialiseIntoItemStack(item);

        item.set(DataComponents.LORE, new ItemLore(lore));
        item.set(DataComponents.CUSTOM_NAME, Component.literal("Radio"));
        item.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(false, ReferenceSortedSets.singleton(DataComponents.PROFILE)));

        return item;
    }

    public static Optional<RadioData> readRadioData(ItemStack item) {
        if(item == null) return Optional.empty();
        if(!item.has(DataComponents.CUSTOM_DATA)) return Optional.empty();

        CustomData data = item.get(DataComponents.CUSTOM_DATA);

        if(data == null) return Optional.empty(); // shouldn't be but hey, sanity.

        return RadioItem.readRadioData(data);
    }

    public static Optional<RadioData> readRadioData(CustomData data) {
        return data.copyTag().read(RadioData.NBT_CATEGORY, RadioData.CODEC);
    }
}
