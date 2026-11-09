package de.maxhenkel.radio.utils;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.util.UUIDTypeAdapter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class HeadUtils {

    private static final Gson gson = new GsonBuilder().registerTypeAdapter(UUID.class, new UUIDTypeAdapter()).create();

    public static ItemStack createHead(UUID profileUUID, String internalName, String skinURL) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);

        ResolvableProfile resolvableProfile = HeadUtils.createPlayerHead(profileUUID, internalName, skinURL);
        stack.set(DataComponents.PROFILE, resolvableProfile);

        return stack;
    }

    public static ResolvableProfile createPlayerHead(UUID uuid, String name, String skinUrl) {
        Multimap<String, Property> properties = HashMultimap.create();

        List<Property> textures = new ArrayList<>();
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> skinPartsTextureMap = new HashMap<>();
        skinPartsTextureMap.put(MinecraftProfileTexture.Type.SKIN, new MinecraftProfileTexture(skinUrl, null));

        String skinPartsToJson = gson.toJson(new MinecraftTexturesPayload(skinPartsTextureMap));
        String base64Payload = Base64.getEncoder().encodeToString(skinPartsToJson.getBytes(StandardCharsets.UTF_8));

        textures.add(new Property("textures", base64Payload));
        properties.putAll("textures", textures);

        GameProfile preResolved =  new GameProfile(uuid, name, new PropertyMap(properties));
        return ResolvableProfile.createResolved(preResolved);
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
