package de.maxhenkel.radio.paper;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonObject;
import de.maxhenkel.radio.RadioCommon;
import de.maxhenkel.radio.radio.RadioData;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PaperRadioItem {

    private PaperRadioItem() {

    }

    public static ItemStack newRadio(String url, String stationName, boolean on, float range) {
        return buildStack(RadioData.createAnonymousRadioData(url, stationName, on, range));
    }

    /**
     * Items don't store the radio's id - a new one is assigned when the radio is placed.
     */
    public static ItemStack fromData(RadioData radioData) {
        Objects.requireNonNull(radioData, "Radio Data must not be null");
        return buildStack(radioData);
    }

    private static ItemStack buildStack(RadioData data) {
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);

        // profile UUID can be random.
        item.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile()
                .uuid(UUID.randomUUID())
                .name("radio")
                .addProperty(new ProfileProperty("textures", createTexturesValue(RadioCommon.SERVER_CONFIG.radioSkinUrl.get())))
                .build());
        item.setData(DataComponentTypes.CUSTOM_NAME, Component.text("Radio"));
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.PROFILE)
                .build());

        writeRadioData(item, data);
        return item;
    }

    public static Optional<RadioData> readRadioData(@Nullable ItemStack item) {
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        return PaperRadioData.read(item.getPersistentDataContainer());
    }

    /**
     * Stores the radio data on the item and refreshes its lore to match.
     */
    public static void writeRadioData(ItemStack item, RadioData data) {
        item.editPersistentDataContainer(container -> PaperRadioData.write(container, data, false));
        item.setData(DataComponentTypes.LORE, ItemLore.lore(createLore(data)));
    }

    private static List<Component> createLore(RadioData data) {
        List<Component> lore = new ArrayList<>();

        lore.add(Component.text("Playing: " + data.getStationName(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        if (!data.isRangeDefault()) {
            lore.add(Component.text("Range: %.1f block(s)".formatted(data.getRange()), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        return lore;
    }

    /**
     * @return the base64 encoded "textures" profile property for a player head with the provided skin.
     */
    private static String createTexturesValue(String skinUrl) {
        JsonObject skin = new JsonObject();
        skin.addProperty("url", skinUrl);

        JsonObject textures = new JsonObject();
        textures.add("SKIN", skin);

        JsonObject payload = new JsonObject();
        payload.add("textures", textures);

        return Base64.getEncoder().encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
    }
}
