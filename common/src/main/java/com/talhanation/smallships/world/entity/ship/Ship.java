package com.talhanation.smallships.world.entity.ship;

import com.talhanation.smallships.DamageSourceShip;
import com.talhanation.smallships.Kalkül;
import com.talhanation.smallships.mixin.BoatAccessor;
import com.talhanation.smallships.world.entity.Cannon;
import com.talhanation.smallships.world.entity.ship.abilities.Bannerable;
import com.talhanation.smallships.world.entity.ship.abilities.Cannonable;
import com.talhanation.smallships.world.entity.ship.abilities.Damageable;
import com.talhanation.smallships.world.entity.ship.abilities.Sailable;
import com.talhanation.smallships.world.item.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class Ship extends Boat {
    public static final EntityDataAccessor<CompoundTag> ATTRIBUTES = SynchedEntityData.defineId(Ship.class, EntityDataSerializers.COMPOUND_TAG);
    public static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(Ship.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ROT_SPEED = SynchedEntityData.defineId(Ship.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Byte> SAIL_STATE = SynchedEntityData.defineId(Ship.class, EntityDataSerializers.BYTE);
    public static final EntityDataAccessor<String>  SAIL_COLOR = SynchedEntityData.defineId(Ship.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<ItemStack> BANNER = SynchedEntityData.defineId(Ship.class, EntityDataSerializers.ITEM_STACK);
    public static final EntityDataAccessor<Float> CANNON_POWER = SynchedEntityData.defineId(Ship.class, EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(Ship.class, EntityDataSerializers.FLOAT);
    private float prevWaveAngle;
    private float waveAngle;
    public float prevBannerWaveAngle;
    public float bannerWaveAngle;
    protected boolean cannonKeyPressed;
    public int cooldown = 0;
    public List<Cannon> CANNONS = new ArrayList<>();
    public List<Cannonable.CannonPosition> CANNON_POS = new ArrayList<>();

    public Ship(EntityType<? extends Boat> entityType, Level level) {
        super(entityType, level);
        if (this.getCustomName() == null) this.setCustomName(Component.literal(StringUtils.capitalize(EntityType.getKey(this.getType()).getPath())));
        this.maxUpStep = 0.6F;
    }

    @Override
    public void tick() {
        super.tick();
        if (this instanceof Sailable sailShip) sailShip.tickSailShip();
        if (this instanceof Bannerable bannerShip) bannerShip.tickBannerShip();
        if (this instanceof Cannonable cannonShip) cannonShip.tickCannonShip();

        if (cooldown > 0) cooldown--;

        /**
         * Fixes the data after imminently stop of the ship if the driver ejected
         **/
        if ((this.getControllingPassenger() == null)){
            setSailState((byte) 0);
            this.setRotSpeed(Kalkül.subtractToZero(this.getRotSpeed(), getVelocityResistance() * 2.5F));
            this.setSpeed(Kalkül.subtractToZero(this.getSpeed(), getVelocityResistance()));
        }

        boolean isSwimming = (getSpeed() > 0.085F || getSpeed() < -0.085F);
        this.updateShipAmbience(isSwimming);
        this.updateKnockBack(isSwimming);

        this.updateWaveAngle();
        this.updateWaterMobs();

        this.floatUp();

    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.getEntityData().define(SPEED, 0.0F);
        this.getEntityData().define(DAMAGE, 0.0F);
        this.getEntityData().define(ROT_SPEED, 0.0F);
        this.getEntityData().define(ATTRIBUTES, this.createDefaultAttributes());

        if (this instanceof Sailable sailShip) sailShip.defineSailShipSynchedData();
        if (this instanceof Bannerable bannerShip) bannerShip.defineBannerShipSynchedData();
        if (this instanceof Cannonable cannonShip) cannonShip.defineCannonShipSynchedData();
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        Attributes attributes = new Attributes();
        attributes.loadSaveData(tag, this);
        this.setData(ATTRIBUTES, attributes.getSaveData());

        if (this instanceof Sailable sailShip) sailShip.readSailShipSaveData(tag);
        if (this instanceof Bannerable bannerShip) bannerShip.readBannerShipSaveData(tag);
        if (this instanceof Cannonable cannonShip) cannonShip.readCannonShipSaveData(tag);
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        Attributes attributes = new Attributes();
        attributes.loadSaveData(this.getData(ATTRIBUTES));
        attributes.addSaveData(tag);

        if (this instanceof Sailable sailShip) sailShip.addSailShipSaveData(tag);
        if (this instanceof Bannerable bannerShip) bannerShip.addBannerShipSaveData(tag);
        if (this instanceof Cannonable cannonShip) cannonShip.addCannonShipSaveData(tag);
    }

    public <T> T getData(EntityDataAccessor<T> accessor) {
        return this.getEntityData().get(accessor);
    }

    public <T> void setData(EntityDataAccessor<T> accessor, T value) {
        this.getEntityData().set(accessor, value);
    }


    @Override
    protected void controlBoat() {
        if(this.isInWater()) {
            byte sailstate = this.getSailState();
            float modifier = 1;// - (getBiomesModifier() + getPassengerModifier() + getCannonModifier() + getCargoModifier());


            float blockedmodf = 1;

            //blockedmodf = 0.00001F;
            Attributes attributes = this.getAttributes();
            float maxSp = (attributes.maxSpeed / (12F * 1.15F)) * modifier;
            float maxBackSp = attributes.maxReverseSpeed * modifier;
            float maxRotSp = (attributes.maxRotationSpeed * 0.1F + 1.8F) * modifier;
            float acceleration = attributes.acceleration;

            float speed = Kalkül.subtractToZero(this.getSpeed(), getVelocityResistance());

            ((BoatAccessor) this).setDeltaRotation(0);
            float rotationSpeed = Kalkül.subtractToZero(getRotSpeed(), getVelocityResistance() * 2.5F);
            if (((BoatAccessor) this).isInputRight()) {
                if (rotationSpeed <= maxRotSp) {
                    rotationSpeed = Math.min(rotationSpeed + this.getAttributes().rotationAcceleration * 1 / 8, maxRotSp);
                }
            }

            if (((BoatAccessor) this).isInputLeft()) {
                if (rotationSpeed >= -maxRotSp) {
                    rotationSpeed = Math.max(rotationSpeed - this.getAttributes().rotationAcceleration * 1 / 8, -maxRotSp);
                }
            }

            setRotSpeed(rotationSpeed);

            ((BoatAccessor) this).setDeltaRotation(rotationSpeed);

            setYRot(getYRot() + ((BoatAccessor) this).getDeltaRotation());

            if (sailstate != (byte) 0) {
                switch (sailstate) {
                    case (byte) 1 -> {
                        maxSp *= 4 / 16F;
                        if (speed <= maxSp)
                            speed = Math.min(speed + acceleration * 9F / 16, maxSp);
                    }
                    case (byte) 2 -> {
                        maxSp *= 8 / 16F;
                        if (speed <= maxSp)
                            speed = Math.min(speed + acceleration * 11F / 16, maxSp);
                    }
                    case (byte) 3 -> {
                        maxSp *= 12 / 16F;
                        if (speed <= maxSp)
                            speed = Math.min(speed + acceleration * 13F / 16, maxSp);
                    }
                    case (byte) 4 -> {
                        maxSp *= 1F;
                        if (speed <= maxSp) {
                            speed = Math.min(speed + acceleration, maxSp);
                        }
                    }
                }
            }

            if (((BoatAccessor) this).isInputUp()) {
                if (sailstate == (byte) 0) {
                    if (speed <= maxSp)
                        speed = Math.min(speed + acceleration, maxSp); //speed = Math.min(speed + acceleration * 1 / 8, maxSp);
                } else {
                    if (this instanceof Sailable sailShip && sailstate != 4) {
                        Entity entity = this.getControllingPassenger();
                        if(entity instanceof Player player)
                            sailShip.increaseSail(player, speed, rotationSpeed);
                    }
                }
            }

            if (((BoatAccessor) this).isInputDown()) {
                if (sailstate == (byte) 0) {
                    if (speed >= -maxBackSp) speed = Math.max(speed - acceleration, -maxBackSp);
                } else {
                    if (this instanceof Sailable sailShip && sailstate != 1) {
                        Entity entity = this.getControllingPassenger();
                        if (entity instanceof Player player)
                            sailShip.decreaseSail(player, speed, rotationSpeed);
                    }
                }
            }

            if (((BoatAccessor) this).isInputLeft() || ((BoatAccessor) this).isInputRight()) {
                speed = speed * (1.0F - (Mth.abs(getRotSpeed()) * 0.015F));
            }

            setSpeed(speed * blockedmodf);

            setDeltaMovement(Kalkül.calculateMotionX(this.getSpeed(), this.getYRot()), getDeltaMovement().y, Kalkül.calculateMotionZ(this.getSpeed(), this.getYRot()));
        }
    }

    public float getSpeed() {
        return entityData.get(SPEED);
    }
    public float getRotSpeed() {
        return entityData.get(ROT_SPEED);
    }
    public void setSailState(byte state) {
        this.setData(SAIL_STATE, state);
    }
    public byte getSailState() {
        return this.getData(SAIL_STATE);
    }
    public void setSpeed(float f) {
        this.entityData.set(SPEED, f);
    }
    public void setRotSpeed(float f) {
        this.entityData.set(ROT_SPEED, f);
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand interactionHand) {
        //if (this instanceof Cannonable cannonShip && cannonShip.interactCannon(player, interactionHand)) return InteractionResult.SUCCESS;
        if (this instanceof Sailable sailShip && sailShip.interactSail(player, interactionHand)) return InteractionResult.SUCCESS;
        if (this instanceof Bannerable bannerShip && bannerShip.interactBanner(player, interactionHand)) return InteractionResult.SUCCESS;

        //from Mob
        if(player.getItemInHand(interactionHand).is(Items.LEAD)){

        }

        return super.interact(player, interactionHand);
    }
    @Override
    public @NotNull Vec3 getDismountLocationForPassenger(@NotNull LivingEntity livingEntity) {
        if (this instanceof Sailable sailShip && this.getSailState() != 0) sailShip.toggleSail();
        return super.getDismountLocationForPassenger(livingEntity);
    }

    @Override
    public double getPassengersRidingOffset() {
        return (double)this.getBbHeight() * 0.75D;
    }

    private void updateWaveAngle(){
        this.prevWaveAngle = this.waveAngle;
        this.waveAngle = (float) Math.sin(getWaveSpeed() * (float) this.tickCount) * getWaveFactor();
    }

    private float getWaveFactor() {
        return this.getLevel().isRaining() ? 3F : 1.25F;
    }

    private float getWaveSpeed() {
        return this.getLevel().isRaining() ? 0.12F : 0.03F;
    }

    public float getWaveAngle(float partialTicks) {
        return Mth.lerp(partialTicks, this.prevWaveAngle, this.waveAngle);
    }

    public Attributes getAttributes() {
        Attributes attributes = new Attributes();
        attributes.loadSaveData(this.getData(ATTRIBUTES));
        return attributes;
    }
    public void setCannonKeyPressed(boolean b){
        cannonKeyPressed = b;
    }
    public boolean isCannonKeyPressed() {
        return cannonKeyPressed;
    }

    @Override
    // keep until multi part entity, otherwise entity just vanishes (stops rendering) on screen edges
    public @NotNull AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(5.0D);
    }

    @Override
    protected abstract int getMaxPassengers();

    @Override
    public abstract @NotNull Item getDropItem();

    public abstract CompoundTag createDefaultAttributes();

    public Cannonable.CannonPosition getCannonPos(int index) {
        return CANNON_POS.get(index);
    }

    /************************************
     * Natural slowdown of the ship
     * increase -> slowdown will be higher
     * decrease -> slowdown will be lower
     ************************************/
    public float getVelocityResistance() {
        return 0.009F;
    }

    protected abstract void waterSplash();

    private void collisionDamage(Entity entityIn) {
        if (entityIn instanceof LivingEntity && !getPassengers().contains(entityIn)) {
            if (entityIn.getBoundingBox().intersects(getBoundingBox().expandTowards(1,1,1))) {
                float speed = getSpeed();
                if (speed > 0.1F) {
                    float damage = speed * 10;
                    entityIn.hurt(DamageSourceShip.DAMAGE_SHIP, damage);
                }

            }
        }
    }

    private void updateShipAmbience(boolean isSwimming) {
        if (isSwimming) {
            if (this.isInWater()) {

                waterSplash();

                //if (SmallShipsConfig.PlaySwimmSound.get()) {
                this.level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_SWIM, this.getSoundSource(), 0.05F, 0.8F + 0.4F * this.random.nextFloat());
                //}
            }
        }
    }

    private void updateKnockBack(boolean isSwimming) {
        if (isSwimming) {
            //this.knockBack(this.level.getEntities(this, this.getBoundingBox().inflate(4.0D, 2.0D, 4.0D), EntitySelector.NO_CREATIVE_OR_SPECTATOR));
        }
    }

    public void updateWaterMobs() {
        //if (SmallShipsConfig.WaterMobFlee.get()) {
            double radius = 15.0D;
            List<WaterAnimal> list1 = this.level.getEntitiesOfClass(WaterAnimal.class, new AABB(getX() - radius, getY() - radius, getZ() - radius, getX() + radius, getY() + radius, getZ() + radius));
            for (WaterAnimal ent : list1)
                fleeEntity(ent);
        //}
    }

    public void fleeEntity(Mob entity) {
        double fleeDistance = 10.0D;
        Vec3 vecBoat = new Vec3(getX(), getY(), getZ());
        Vec3 vecEntity = new Vec3(entity.getX(), entity.getY(), entity.getZ());
        Vec3 fleeDir = vecEntity.subtract(vecBoat);
        fleeDir = fleeDir.normalize();
        Vec3 fleePos = new Vec3(vecEntity.x + fleeDir.x * fleeDistance, vecEntity.y + fleeDir.y * fleeDistance, vecEntity.z + fleeDir.z * fleeDistance);
        entity.getNavigation().moveTo(fleePos.x, fleePos.y, fleePos.z, 1.5D);
    }

    protected void floatUp(){
        if (this.isEyeInFluid(FluidTags.WATER))
            this.setDeltaMovement(getDeltaMovement().x, 0.2D, getDeltaMovement().z);
    }
    @Override
    public boolean hurt(DamageSource damageSource, float f) {
        if (this.isInvulnerableTo(damageSource)) {
            return false;
        } else if (!this.level.isClientSide && !this.isRemoved()) {
            this.setHurtDir(-this.getHurtDir());
            this.setHurtTime(10);
            this.setDamage(this.getDamage() + f * 10.0F);
            this.markHurt();
            this.gameEvent(GameEvent.ENTITY_DAMAGE, damageSource.getEntity());
            boolean bl = damageSource.getEntity() instanceof Player && ((Player)damageSource.getEntity()).getAbilities().instabuild;
            if (bl || this.getDamage() > 40.0F) {
                if (!bl && this.level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                    this.destroy(damageSource);
                }

                this.discard();
            }

            return true;
        } else {
            return true;
        }
    }
}