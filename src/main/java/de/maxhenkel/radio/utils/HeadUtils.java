package de.maxhenkel.radio.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.util.UUIDTypeAdapter;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PlayerHeadItem;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class HeadUtils {

    public static final String NBT_SOUND_RANGE = "sound_radius";

    public static ItemStack createHead(String itemName, List<Component> loreComponents, GameProfile gameProfile) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        CompoundTag tag = stack.getOrCreateTag();

        MutableComponent nameComponent = Component.literal(itemName).withStyle(style -> style.withItalic(false).withColor(ChatFormatting.WHITE));

        ListTag lore = new ListTag();
        for (int i = 0; i < loreComponents.size(); i++) {
            lore.add(i, StringTag.valueOf(Component.Serializer.toJson(loreComponents.get(i))));
        }

        CompoundTag display = new CompoundTag();
        display.putString(ItemStack.TAG_DISPLAY_NAME, Component.Serializer.toJson(nameComponent));
        display.put(ItemStack.TAG_LORE, lore);
        tag.put(ItemStack.TAG_DISPLAY, display);
        tag.putInt("HideFlags", ItemStack.TooltipPart.ADDITIONAL.getMask());

        tag.put("SkullOwner", NbtUtils.writeGameProfile(new CompoundTag(), gameProfile));

        return stack;
    }

    private static final Gson gson = new GsonBuilder().registerTypeAdapter(UUID.class, new UUIDTypeAdapter()).create();

    public static GameProfile getGameProfile(UUID uuid, String name, String skinUrl) {
        GameProfile gameProfile = new GameProfile(uuid, name);
        PropertyMap properties = gameProfile.getProperties();

        List<Property> textures = new ArrayList<>();


        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textureMap = new HashMap<>();
        textureMap.put(MinecraftProfileTexture.Type.SKIN, new MinecraftProfileTexture(skinUrl, null));

        String json = gson.toJson(new MinecraftTexturesPayload(textureMap));

        String base64Payload = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        textures.add(new Property("Value", base64Payload));

        properties.putAll("textures", textures);

        return gameProfile;
    }

    private static class MinecraftTexturesPayload {

        private final Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures;

        public MinecraftTexturesPayload(Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures) {
            this.textures = textures;
        }

        public Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> getTextures() {
            return textures;
        }
    }

}
