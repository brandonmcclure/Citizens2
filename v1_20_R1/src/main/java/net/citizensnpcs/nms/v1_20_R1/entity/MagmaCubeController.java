package net.citizensnpcs.nms.v1_20_R1.entity;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftMagmaCube;
import org.bukkit.util.Vector;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.nms.v1_20_R1.util.EntityMoveControl;
import net.citizensnpcs.nms.v1_20_R1.util.ForwardingNPCHolder;
import net.citizensnpcs.nms.v1_20_R1.util.NMSBoundingBox;
import net.citizensnpcs.nms.v1_20_R1.util.NMSImpl;
import net.citizensnpcs.npc.CitizensNPC;
import net.citizensnpcs.npc.ai.NPCHolder;
import net.citizensnpcs.util.NMS;
import net.citizensnpcs.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.PositionImpl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class MagmaCubeController extends MobEntityController {
    public MagmaCubeController() {
        super(EntityMagmaCubeNPC.class);
    }

    @Override
    public org.bukkit.entity.MagmaCube getBukkitEntity() {
        return (org.bukkit.entity.MagmaCube) super.getBukkitEntity();
    }

    public static class EntityMagmaCubeNPC extends MagmaCube implements NPCHolder {
        private final CitizensNPC npc;

        private MoveControl oldMoveController;

        public EntityMagmaCubeNPC(EntityType<? extends MagmaCube> types, Level level) {
            this(types, level, null);
        }

        public EntityMagmaCubeNPC(EntityType<? extends MagmaCube> types, Level level, NPC npc) {
            super(types, level);
            this.npc = (CitizensNPC) npc;
            if (npc != null) {
                setSize(3, true);
                this.oldMoveController = this.moveControl;
                this.moveControl = new EntityMoveControl(this);
            }
        }

        @Override
        protected boolean canRide(Entity entity) {
            if (npc != null && (entity instanceof Boat || entity instanceof AbstractMinecart)) {
                return !npc.isProtected();
            }
            return super.canRide(entity);
        }

        @Override
        public boolean causeFallDamage(float f, float f1, DamageSource damagesource) {
            if (npc == null || !npc.isFlyable()) {
                return super.causeFallDamage(f, f1, damagesource);
            }
            return false;
        }

        @Override
        public void checkDespawn() {
            if (npc == null) {
                super.checkDespawn();
            }
        }

        @Override
        protected void checkFallDamage(double d0, boolean flag, BlockState iblockdata, BlockPos blockposition) {
            if (npc == null || !npc.isFlyable()) {
                super.checkFallDamage(d0, flag, iblockdata, blockposition);
            }
        }

        

        @Override
        protected SoundEvent getAmbientSound() {
            return NMSImpl.getSoundEffect(npc, super.getAmbientSound(), NPC.Metadata.AMBIENT_SOUND);
        }

        @Override
        public CraftEntity getBukkitEntity() {
            if (npc != null && !(super.getBukkitEntity() instanceof NPCHolder)) {
                NMSImpl.setBukkitEntity(this, new MagmaCubeNPC(this));
            }
            return super.getBukkitEntity();
        }

        @Override
        protected SoundEvent getDeathSound() {
            return NMSImpl.getSoundEffect(npc, super.getDeathSound(), NPC.Metadata.DEATH_SOUND);
        }

        @Override
        protected SoundEvent getHurtSound(DamageSource damagesource) {
            return NMSImpl.getSoundEffect(npc, super.getHurtSound(damagesource), NPC.Metadata.HURT_SOUND);
        }

        @Override
        public int getMaxFallDistance() {
            return NMS.getFallDistance(npc, super.getMaxFallDistance());
        }

        @Override
        public NPC getNPC() {
            return npc;
        }

        @Override
        public boolean isLeashed() {
            return NMSImpl.isLeashed(npc, super::isLeashed, this);
        }

        @Override
        public boolean isPushable() {
            return npc == null ? super.isPushable()
                    : npc.data().<Boolean> get(NPC.Metadata.COLLIDABLE, !npc.isProtected());
        }

        @Override
        public void knockback(double strength, double dx, double dz) {
            NMS.callKnockbackEvent(npc, (float) strength, dx, dz, (evt) -> super.knockback((float) evt.getStrength(),
                    evt.getKnockbackVector().getX(), evt.getKnockbackVector().getZ()));
        }

        @Override
        protected AABB makeBoundingBox() {
            return NMSBoundingBox.makeBB(npc, super.makeBoundingBox());
        }

        @Override
        public boolean onClimbable() {
            if (npc == null || !npc.isFlyable()) {
                return super.onClimbable();
            } else {
                return false;
            }
        }

        @Override
        public void playerTouch(Player human) {
            if (npc == null) {
                super.playerTouch(human);
            }
        }

        @Override
        public void push(double x, double y, double z) {
            Vector vector = Util.callPushEvent(npc, x, y, z);
            if (vector != null) {
                super.push(vector.getX(), vector.getY(), vector.getZ());
            }
        }

        @Override
        public void push(Entity entity) {
            // this method is called by both the entities involved - cancelling
            // it will not stop the NPC from moving.
            super.push(entity);
            if (npc != null)
                Util.callCollisionEvent(npc, entity.getBukkitEntity());
        }

        @Override
        public void refreshDimensions() {
            if (npc == null) {
                super.refreshDimensions();
            } else {
                NMSImpl.setSize(this, firstTick);
            }
        }

        @Override
        public boolean save(CompoundTag save) {
            return npc == null ? super.save(save) : false;
        }

        @Override
        public Entity teleportTo(ServerLevel worldserver, PositionImpl location) {
            if (npc == null)
                return super.teleportTo(worldserver, location);
            return NMSImpl.teleportAcrossWorld(this, worldserver, location);
        }

        @Override
        public void tick() {
            super.tick();
            if (npc != null) {
                NMSImpl.updateMinecraftAIState(npc, this);
                if (npc.useMinecraftAI() && this.moveControl != this.oldMoveController) {
                    this.moveControl = this.oldMoveController;
                }
                if (!npc.useMinecraftAI() && this.moveControl == this.oldMoveController) {
                    this.moveControl = new EntityMoveControl(this);
                }
                npc.update();
            }
        }

        @Override
        public void travel(Vec3 vec3d) {
            if (npc == null || !npc.isFlyable()) {
                super.travel(vec3d);
            } else {
                NMSImpl.flyingMoveLogic(this, vec3d);
            }
        }

        @Override
        public boolean updateFluidHeightAndDoFluidPushing(TagKey<Fluid> tagkey, double d0) {
            return NMSImpl.fluidPush(npc, this, () -> super.updateFluidHeightAndDoFluidPushing(tagkey, d0));
        }
    }

    public static class MagmaCubeNPC extends CraftMagmaCube implements ForwardingNPCHolder {
        public MagmaCubeNPC(EntityMagmaCubeNPC entity) {
            super((CraftServer) Bukkit.getServer(), entity);
        }
    }
}
