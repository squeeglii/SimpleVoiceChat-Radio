package de.maxhenkel.radio;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class RadioVoicechatPlugin implements VoicechatPlugin {

    public static String RADIOS_CATEGORY = "radios";

    @Nullable
    public static VoicechatServerApi voicechatServerApi;
    @Nullable
    public static VolumeCategory radios;
    private static final List<Runnable> runnables = new ArrayList<>();

    public RadioVoicechatPlugin() {

    }

    @Override
    public String getPluginId() {
        return Radio.MODID;
    }

    @Override
    public void initialize(VoicechatApi api) {

    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
        registration.registerEvent(VoicechatServerStoppedEvent.class, this::onServerStop);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        voicechatServerApi = event.getVoicechat();
        radios = voicechatServerApi.volumeCategoryBuilder()
                .setId(RADIOS_CATEGORY)
                .setName("Radios")
                .setDescription("The volume of radios")
                .setIcon(getIcon("category_radios.png"))
                .build();

        voicechatServerApi.registerVolumeCategory(radios);

        synchronized (runnables) {
            runnables.forEach(Runnable::run);
            runnables.clear();
        }
    }

    private void onServerStop(VoicechatServerStoppedEvent event) {
        voicechatServerApi = null;
    }

    public static void runWhenReady(Runnable runnable) {
        synchronized (runnables) {
            if (voicechatServerApi != null) {
                runnable.run();
                return;
            }
            runnables.add(runnable);
        }
    }

    @Nullable
    private int[][] getIcon(String path) {
        try {
            Enumeration<URL> resources = RadioVoicechatPlugin.class.getClassLoader().getResources(path);
            while (resources.hasMoreElements()) {
                BufferedImage bufferedImage = ImageIO.read(resources.nextElement().openStream());
                if (bufferedImage.getWidth() != 16) {
                    continue;
                }
                if (bufferedImage.getHeight() != 16) {
                    continue;
                }
                int[][] image = new int[16][16];
                for (int x = 0; x < bufferedImage.getWidth(); x++) {
                    for (int y = 0; y < bufferedImage.getHeight(); y++) {
                        image[x][y] = bufferedImage.getRGB(x, y);
                    }
                }
                return image;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
