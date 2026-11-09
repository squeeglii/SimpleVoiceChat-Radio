package de.maxhenkel.radio.radio;

import de.maxhenkel.radio.utils.IPossibleRadioBlock;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public record FabricRadioLocation(ServerLevel level, BlockPos pos) implements RadioLocation {

    @Override
    public de.maxhenkel.voicechat.api.ServerLevel getVoicechatLevel(VoicechatServerApi api) {
        return api.fromServerLevel(this.level);
    }

    @Override
    public int getX() {
        return this.pos.getX();
    }

    @Override
    public int getY() {
        return this.pos.getY();
    }

    @Override
    public int getZ() {
        return this.pos.getZ();
    }

    @Override
    public void runOnServerThread(Runnable task) {
        this.level.getServer().execute(task);
    }

    @Override
    public void spawnMusicParticles() {
        Vec3 vec3 = Vec3.atBottomCenterOf(this.pos).add(0D, 1D, 0D);
        this.level.players().stream().filter(player -> player.position().distanceTo(Vec3.atCenterOf(this.pos)) <= 32D).forEach(player -> {
            float random = (float) this.level.getRandom().nextInt(4) / 24F;
            this.level.sendParticles(ParticleTypes.NOTE, vec3.x(), vec3.y(), vec3.z(), 0, random, 0D, 0D, 1D);
        });
    }

    @Override
    public boolean isRadioPresent(UUID radioId) {
        if (!this.level.isLoaded(this.pos))
            return false;

        BlockEntity blockEntity = this.level.getBlockEntity(this.pos);

        if(!(blockEntity instanceof IPossibleRadioBlock radioBlock))
            return false;

        return radioBlock.radio$isRadio() && radioBlock.radio$getRadioData().getId().equals(radioId);
    }

    public boolean isInChunk(ServerLevel level, ChunkPos chunkPos) {
        int chunkX = SectionPos.blockToSectionCoord(this.pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(this.pos.getZ());
        return this.level.dimension().equals(level.dimension()) && chunkX == chunkPos.x() && chunkZ == chunkPos.z();
    }
}
