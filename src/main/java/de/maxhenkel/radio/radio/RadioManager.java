package de.maxhenkel.radio.radio;

import com.mojang.authlib.GameProfile;
import de.maxhenkel.radio.Radio;
import de.maxhenkel.radio.utils.IPossibleRadioBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class RadioManager {

    private final Map<UUID, RadioStream> radioStreams;

    public RadioManager() {
        this.radioStreams = new HashMap<>();
    }

    // This may be broken entirely as of 1.21.10.
    // Not fixing this.
    @Deprecated(since = "2.0")
    public Optional<RadioData> loadHeadFromGameProfile(SkullBlockEntity skullBlockEntity) {
        if (!(skullBlockEntity.getLevel() instanceof ServerLevel serverLevel))
            return Optional.empty();

        ResolvableProfile resolvableProfile = skullBlockEntity.getOwnerProfile();
        if(resolvableProfile == null) return Optional.empty();

        // todo: Deprecate storing data in player heads -- CustomData works perfectly fine.
        //       The playerhead loading will remain in place, but new data will be saved
        //       to CustomData instead of the GameProfile
        //

        try {
            GameProfile ownerProfile = resolvableProfile.partialProfile();
            RadioData radioData = RadioData.fromGameProfile(ownerProfile);
            if (radioData == null) return Optional.empty();

            return Optional.of(radioData);

            //this.updateStoredRadioData(skullBlockEntity, serverLevel, radioData, ownerProfile);
        } catch (Exception err) {
            Radio.LOGGER.error("Loading legacy format radio data failed", err);
            return Optional.empty();
        }
    }

    /**
     * Opens a new audio stream for the provided radio data.
     * If any previous stream existed with the same id, it is deleted.
     * This can be used to refresh the properties of the stream if they are updated while a stream is already playing.
     * @return true if this method has modified the contents of the RadioData parameter.
     */
    public boolean updateRadioStream(RadioData radioData, ServerLevel serverLevel, BlockEntity skullBlockEntity) {
        boolean idChanged = radioData.assignIdIfNil();

        RadioStream radioStream = new RadioStream(radioData, serverLevel, skullBlockEntity.getBlockPos());
        Radio.LOGGER.debug("Loaded radio stream for '{}' ({})", radioData.getStationName(), radioData.getId());
        radioStream.init();
        RadioStream oldStream = this.radioStreams.put(radioData.getId(), radioStream);

        if (oldStream != null) {
            oldStream.close();
            Radio.LOGGER.warn("Replacing radio stream for '{}' ({})  - Old stream state was '{}'", radioData.getStationName(), radioData.getId(), oldStream.getState());
        }

        return idChanged;
    }

    public Optional<RadioStream> getRadioStream(UUID id) {
        return Optional.ofNullable(this.radioStreams.get(id));
    }

    public Optional<RadioStream> getRadioStream(RadioData radio) {
        return this.getRadioStream(radio.getId());
    }

    public static boolean isValidRadioLocation(UUID id, BlockPos pos, ServerLevel level) {
        if (!level.isLoaded(pos))
            return false;

        BlockEntity blockEntity = level.getBlockEntity(pos);

        if(!(blockEntity instanceof IPossibleRadioBlock radioBlock))
            return false;

        return radioBlock.radio$isRadio() && radioBlock.radio$getRadioData().getId().equals(id);
    }

    public void onRemoveHead(UUID id) {
        RadioStream radioStream = this.radioStreams.remove(id);

        if (radioStream != null) {
            radioStream.close();
            Radio.LOGGER.debug("Removed radio stream for '{}' ({})", radioStream.getRadioData().getStationName(), radioStream.getRadioData().getId());
        } else {
            Radio.LOGGER.debug("Removed radio stream {}", id);
        }
    }

    public void stopStream(UUID id) {
        RadioStream radioStream = radioStreams.get(id);
        if (radioStream != null) {
            radioStream.stop();
        }
    }

    public void updateHeadOnState(UUID id, boolean on) {
        RadioStream radioStream = this.radioStreams.get(id);
        if (radioStream == null) {
            Radio.LOGGER.info("No stream detected.");
            return;
        }

        if (on) {
            radioStream.start();
        } else {
            radioStream.stop();
        }
    }

    public void onChunkUnload(ServerLevel serverLevel, LevelChunk levelChunk) {
        radioStreams.values().removeIf(radioStream -> {
            boolean remove = radioStream.getServerLevel().dimension().equals(serverLevel.dimension()) && isInChunk(radioStream.getPosition(), levelChunk.getPos());
            if (remove) {
                radioStream.close();
            }
            return remove;
        });
    }

    private static boolean isInChunk(BlockPos pos, ChunkPos chunkPos) {
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        return chunkX == chunkPos.x && chunkZ == chunkPos.z;
    }

    public void clear() {
        this.radioStreams.values().forEach(RadioStream::close);
        this.radioStreams.clear();
    }

    private static RadioManager instance;

    public static RadioManager getInstance() {
        if (instance == null) {
            instance = new RadioManager();
        }
        return instance;
    }
}
