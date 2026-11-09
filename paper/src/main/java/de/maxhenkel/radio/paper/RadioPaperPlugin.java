package de.maxhenkel.radio.paper;

import de.maxhenkel.configbuilder.ConfigBuilder;
import de.maxhenkel.radio.RadioCommon;
import de.maxhenkel.radio.RadioVoicechatPlugin;
import de.maxhenkel.radio.config.ServerConfig;
import de.maxhenkel.radio.radio.RadioManager;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class RadioPaperPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        RadioCommon.SERVER_CONFIG = ConfigBuilder.build(getDataFolder().toPath().resolve("radio.properties"), ServerConfig::new);

        BukkitVoicechatService service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            RadioCommon.LOGGER.error("Simple Voice Chat is not available - disabling {}", getName());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        service.registerPlugin(new RadioVoicechatPlugin());

        registerCommandPermission();
        getServer().getPluginManager().registerEvents(new RadioListener(this), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(PaperRadioCommands.create(this), "Create and manage radios"));

        // Chunks loaded before the plugin was enabled (e.g. while the worlds were loading) never fire a ChunkLoadEvent for us.
        for (World world : getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                PaperRadioBlocks.loadRadios(this, chunk);
            }
        }
    }

    @Override
    public void onDisable() {
        RadioManager.getInstance().clear();
    }

    /**
     * Mirrors the command_permission_level config option: by default, everyone may use /radio at level 0 and only
     * operators may above that. Permission plugins can grant or revoke the permission regardless.
     */
    private void registerCommandPermission() {
        PermissionDefault permissionDefault = RadioCommon.SERVER_CONFIG.commandPermissionLevel.get() == 0
                ? PermissionDefault.TRUE
                : PermissionDefault.OP;

        PluginManager pluginManager = getServer().getPluginManager();
        Permission permission = pluginManager.getPermission(PaperRadioCommands.PERMISSION);

        if (permission == null) {
            pluginManager.addPermission(new Permission(PaperRadioCommands.PERMISSION, "Allows using the /radio command", permissionDefault));
        } else {
            permission.setDefault(permissionDefault);
        }
    }
}
