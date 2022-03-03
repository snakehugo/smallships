package com.talhanation.smallships.entities.projectile;


import com.talhanation.smallships.entities.AbstractShipDamage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.entity.projectile.DamagingProjectileEntity;
import net.minecraft.entity.projectile.ProjectileHelper;
import net.minecraft.network.IPacket;
import net.minecraft.particles.IParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

public abstract class AbstractCannonBall extends DamagingProjectileEntity {

    public boolean inWater = false;
    public boolean wasShot = false;

    protected AbstractCannonBall(EntityType<? extends AbstractCannonBall> type, World world) {
        super(type, world);
    }

    public AbstractCannonBall(EntityType<? extends AbstractCannonBall> type, LivingEntity owner, double d1, double d2, double d3, World world) {
        super(type, owner, d1, d2, d3, world);
        this.moveTo(d1, d2, d3, this.yRot, this.xRot);
    }


    @Override
    public void tick() {
        this.baseTick();

        Vector3d vector3d = this.getDeltaMovement();
        RayTraceResult raytraceresult = ProjectileHelper.getHitResult(this, this::canHitEntity);

        if (raytraceresult.getType() != RayTraceResult.Type.MISS && !net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this, raytraceresult)) {
            this.onHit(raytraceresult);
        }

        double d0 = this.getX() + vector3d.x;
        double d1 = this.getY() + vector3d.y;
        double d2 = this.getZ() + vector3d.z;
        this.updateRotation();
        float f = 0.99F;
        float f1 = 0.06F;
        this.setDeltaMovement(vector3d.scale(f));
        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, (double)-f1, 0.0D));
        }
        this.setPos(d0, d1, d2);

        if(isAlive()){
            setWasShot(true);
        }

        if(isInWater()){
            setInWater(true);
        }
    }

    public void setWasShot(boolean bool){
        if (bool != wasShot){
            wasShot = true;
            if (this.level.isClientSide) {
                shootParticles();
            }
        }
    }

    public void setInWater(boolean bool){
        if (bool != inWater){
            inWater = true;
            if (this.level.isClientSide) {
                shootParticles();
            }
            if (!this.level.isClientSide) {
                level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_SPLASH, SoundCategory.BLOCKS, 10.0F, 0.8F + 0.4F * this.random.nextFloat());
            }
            this.remove();
        }
    }

    protected void onHit(RayTraceResult rayTraceResult) {
        super.onHit(rayTraceResult);
        if (!this.level.isClientSide) {
            boolean flag = net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(this.level, this.getOwner());
            this.level.explode(this.getOwner(), getX(), getY(), getZ(), 1.25F, Explosion.Mode.BREAK);
            this.remove();
        }
        onHitBlockParticles();
    }

    protected void onHitEntity(EntityRayTraceResult rayTraceResult) {
        super.onHitEntity(rayTraceResult);
        if (!this.level.isClientSide) {
            Entity hit = rayTraceResult.getEntity();
            Entity entity1 = this.getOwner();
            hit.hurt(DamageSource.ANVIL, 20.0F);
            if (entity1 instanceof LivingEntity) {
                this.doEnchantDamageEffects((LivingEntity) entity1, hit);
            }

            if (hit instanceof AbstractShipDamage) {
                AbstractShipDamage shipDamage = (AbstractShipDamage) hit;
                shipDamage.damageShip(12);
            }
        }
    }

    protected void onHitBlockParticles(){
        if (this.level.isClientSide) {
                hitParticles();
        }
    }

    public void hitParticles(){
        for (int i = 0; i < 100; ++i) {
            double d0 = this.random.nextGaussian() * 0.03D;
            double d1 = this.random.nextGaussian() * 0.03D;
            double d2 = this.random.nextGaussian() * 0.03D;
            double d3 = 20.0D;
            this.level.addParticle(ParticleTypes.POOF, this.getX(1.0D) - d0 * d3, this.getRandomY() - d1 * d3, this.getRandomZ(2.0D) - d2 * d3, d0, d1, d2);
            this.level.addParticle(ParticleTypes.LARGE_SMOKE, this.getX(1.0D) - d0 * d3, this.getRandomY() - d1 * d3, this.getRandomZ(2.0D) - d2 * d3, d0, d1, d2);
        }
    }

    public void shootParticles(){
        for (int i = 0; i < 100; ++i) {
            double d0 = this.random.nextGaussian() * 0.03D;
            double d1 = this.random.nextGaussian() * 0.03D;
            double d2 = this.random.nextGaussian() * 0.03D;
            double d3 = 20.0D;
            this.level.addParticle(ParticleTypes.POOF, this.getX(1.0D) - d0 * d3, this.getRandomY() - d1 * d3, this.getRandomZ(2.0D) - d2 * d3, d0, d1, d2);
        }

        for (int i = 0; i < 50; ++i) {
            double d00 = this.random.nextGaussian() * 0.03D;
            double d11 = this.random.nextGaussian() * 0.03D;
            double d22 = this.random.nextGaussian() * 0.03D;
            double d44 = 10.0D;
            this.level.addParticle(ParticleTypes.LARGE_SMOKE, this.getX(1.0D) - d00 * d44, this.getRandomY() - d11 * d44, this.getRandomZ(2.0D) - d22 * d44, d00, d11, d22);
            this.level.addParticle(ParticleTypes.FLAME, this.getX(1.0D) - d00 * d44, this.getRandomY() - d11 * d44, this.getRandomZ(2.0D) - d22 * d44, d00, d11, d22);
        }
    }

    public boolean isPickable() {
        return false;
    }

    public boolean hurt(DamageSource p_70097_1_, float p_70097_2_) {
        return false;
    }

    public float getBrightness() {
        return 0.0F;
    }

    @Override
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    protected boolean shouldBurn() {
        return false;
    }

    protected IParticleData getTrailParticle() {
        return ParticleTypes.SMOKE;
    }

}

/*
                 if (block == Blocks.WATER) {
                    for (int i = 0; i < 200; ++i) {
                        double d0 = this.random.nextGaussian() * 0.02D;
                        double d1 = this.random.nextGaussian() * 0.02D;
                        double d2 = this.random.nextGaussian() * 0.02D;
                        double d3 = 10.0D;
                        this.level.addParticle(new BlockParticleData(ParticleTypes.BLOCK, blockstate), this.getX(1.0D) - d0 * d3, this.getRandomY() - d1 * d3, this.getRandomZ(2.0D) - d2 * d3, d0, d1, d2);
                        this.level.addParticle(ParticleTypes.POOF, this.getX(1.0D) - d0 * d3, this.getRandomY() - d1 * d3, this.getRandomZ(2.0D) - d2 * d3, d0, d1, d2);

                    }
                } else
                 */