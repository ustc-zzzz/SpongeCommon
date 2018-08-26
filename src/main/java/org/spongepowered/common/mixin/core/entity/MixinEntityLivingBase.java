/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.mixin.core.entity;

import static com.google.common.base.Preconditions.checkNotNull;

import com.flowpowered.math.vector.Vector3d;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.util.CombatTracker;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.apache.logging.log4j.Level;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.DataManipulator;
import org.spongepowered.api.data.manipulator.mutable.entity.DamageableData;
import org.spongepowered.api.data.manipulator.mutable.entity.HealthData;
import org.spongepowered.api.data.value.mutable.MutableBoundedValue;
import org.spongepowered.api.data.value.mutable.OptionalValue;
import org.spongepowered.api.entity.EntitySnapshot;
import org.spongepowered.api.entity.Transform;
import org.spongepowered.api.entity.living.Living;
import org.spongepowered.api.entity.projectile.Projectile;
import org.spongepowered.api.event.Cancellable;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.cause.entity.damage.DamageFunction;
import org.spongepowered.api.event.cause.entity.damage.DamageModifier;
import org.spongepowered.api.event.cause.entity.damage.source.FallingBlockDamageSource;
import org.spongepowered.api.event.entity.ChangeEntityEquipmentEvent;
import org.spongepowered.api.event.entity.DamageEntityEvent;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.api.event.item.inventory.UseItemStackEvent;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.annotation.NonnullByDefault;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.asm.util.PrettyPrinter;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.SpongeImplHooks;
import org.spongepowered.common.data.manipulator.mutable.entity.SpongeDamageableData;
import org.spongepowered.common.data.manipulator.mutable.entity.SpongeHealthData;
import org.spongepowered.common.data.util.DataConstants;
import org.spongepowered.common.data.value.SpongeValueFactory;
import org.spongepowered.common.data.value.mutable.SpongeOptionalValue;
import org.spongepowered.common.entity.EntityUtil;
import org.spongepowered.common.entity.living.human.EntityHuman;
import org.spongepowered.common.entity.projectile.ProjectileLauncher;
import org.spongepowered.common.event.SpongeCommonEventFactory;
import org.spongepowered.common.event.damage.DamageEventHandler;
import org.spongepowered.common.event.damage.DamageObject;
import org.spongepowered.common.event.tracking.IPhaseState;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.event.tracking.phase.entity.EntityDeathContext;
import org.spongepowered.common.event.tracking.phase.entity.EntityPhase;
import org.spongepowered.common.interfaces.entity.IMixinEntityLivingBase;
import org.spongepowered.common.interfaces.entity.player.IMixinEntityPlayerMP;
import org.spongepowered.common.interfaces.world.IMixinWorld;
import org.spongepowered.common.interfaces.entity.player.IMixinInventoryPlayer;
import org.spongepowered.common.item.inventory.adapter.InventoryAdapter;
import org.spongepowered.common.item.inventory.adapter.impl.slots.SlotAdapter;
import org.spongepowered.common.item.inventory.lens.comp.HotbarLens;
import org.spongepowered.common.item.inventory.lens.impl.fabric.EquipmentSlotFabric;
import org.spongepowered.common.item.inventory.lens.impl.minecraft.PlayerInventoryLens;
import org.spongepowered.common.item.inventory.lens.impl.slots.SlotLensImpl;
import org.spongepowered.common.item.inventory.lens.slots.SlotLens;
import org.spongepowered.common.item.inventory.util.ItemStackUtil;
import org.spongepowered.common.registry.type.event.DamageSourceRegistryModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import javax.annotation.Nullable;

@SuppressWarnings("rawtypes")
@NonnullByDefault
@Mixin(value = EntityLivingBase.class, priority = 999)
public abstract class MixinEntityLivingBase extends MixinEntity implements Living, IMixinEntityLivingBase {

    private static final String WORLD_SPAWN_PARTICLE = "Lnet/minecraft/world/World;spawnParticle(Lnet/minecraft/util/EnumParticleTypes;DDDDDD[I)V";
    private static final int MAX_DEATH_EVENTS_BEFORE_GIVING_UP = 3;

    private int maxAir = 300;
    private boolean runningCollideWithNearby = false;

    @Shadow public int maxHurtResistantTime;
    @Shadow public int hurtTime;
    @Shadow public int maxHurtTime;
    @Shadow protected int scoreValue;
    @Shadow public float attackedAtYaw;
    @Shadow public float limbSwingAmount;
    @Shadow public boolean potionsNeedUpdate;
    @Shadow public boolean dead;
    @Shadow public CombatTracker combatTracker;
    @Shadow @Nullable public EntityLivingBase revengeTarget;
    @Shadow protected AbstractAttributeMap attributeMap;
    @Shadow protected int idleTime;
    @Shadow protected int recentlyHit;
    @Shadow protected float lastDamage;
    @Shadow @Nullable protected EntityPlayer attackingPlayer;
    @Shadow protected ItemStack activeItemStack;
    @Shadow private DamageSource lastDamageSource;
    @Shadow private long lastDamageStamp;
    @Nullable private ItemStack activeItemStackCopy;

    @Shadow public abstract int getItemInUseCount();
    @Shadow public abstract void resetActiveHand();

    @Shadow protected int activeItemStackUseCount;

    // Empty body so that we can call super() in MixinEntityPlayer
    @Shadow public void stopActiveHand() {

    }

    @Shadow protected abstract void markVelocityChanged();
    @Shadow protected abstract SoundEvent getDeathSound();
    @Shadow protected abstract float getSoundVolume();
    @Shadow protected abstract float getSoundPitch();
    @Shadow protected abstract SoundEvent getHurtSound(DamageSource cause);
    @Shadow public abstract void setHealth(float health);
    @Shadow public abstract void addPotionEffect(net.minecraft.potion.PotionEffect potionEffect);
    @Shadow protected abstract void markPotionsDirty();
    @Shadow public abstract void clearActivePotions();
    @Shadow public abstract void setLastAttackedEntity(net.minecraft.entity.Entity entity);
    @Shadow public abstract boolean isPotionActive(Potion potion);
    @Shadow public abstract float getHealth();
    @Shadow public abstract float getMaxHealth();
    @Shadow public abstract float getRotationYawHead();
    @Shadow public abstract void setRotationYawHead(float rotation);
    @Shadow public abstract Collection getActivePotionEffects();
    @Shadow @Nullable public abstract EntityLivingBase getLastAttackedEntity();
    @Shadow public abstract IAttributeInstance getEntityAttribute(IAttribute attribute);
    @Shadow public abstract ItemStack getItemStackFromSlot(EntityEquipmentSlot slotIn);
    @Shadow protected abstract void applyEntityAttributes();
    @Shadow protected abstract void playHurtSound(net.minecraft.util.DamageSource p_184581_1_);
    @Shadow protected abstract void damageShield(float p_184590_1_);
    @Shadow public abstract void setActiveHand(EnumHand hand);
    @Shadow public abstract ItemStack getHeldItem(EnumHand hand);
    @Shadow public abstract void setHeldItem(EnumHand hand, @Nullable ItemStack stack);
    @Shadow public abstract ItemStack getHeldItemMainhand();
    @Shadow public abstract boolean isHandActive();
    @Shadow protected abstract void onDeathUpdate();
    @Shadow public abstract void knockBack(net.minecraft.entity.Entity entityIn, float p_70653_2_, double p_70653_3_, double p_70653_5_);
    @Shadow public abstract void shadow$setRevengeTarget(EntityLivingBase livingBase);
    @Shadow public abstract void setAbsorptionAmount(float amount);
    @Shadow public abstract float getAbsorptionAmount();
    @Shadow public abstract CombatTracker getCombatTracker();
    @Shadow public abstract void setSprinting(boolean sprinting);
    @Shadow public abstract boolean isOnLadder();
    @Shadow @Nullable public abstract EntityLivingBase getAttackingEntity();
    @Shadow protected abstract void dropLoot(boolean wasRecentlyHit, int lootingModifier, DamageSource source);
    @Shadow protected abstract boolean canDropLoot();
    @Shadow public abstract Random getRNG();
    @Shadow protected abstract void blockUsingShield(EntityLivingBase p_190629_1_);
    @Shadow public abstract boolean canBlockDamageSource(DamageSource p_184583_1_);
    @Shadow private boolean checkTotemDeathProtection(DamageSource p_190628_1_) {
        return false; // SHADOWED
    }
    @Shadow public abstract AbstractAttributeMap getAttributeMap();
    @Shadow public void onKillCommand() {
        // Non-abstract for MixinEntityArmorStand
    }
    @Shadow protected abstract int getExperiencePoints(EntityPlayer attackingPlayer);

    @Shadow @Nullable public abstract EntityLivingBase getRevengeTarget();

    @Shadow public int deathTime;

    @Shadow public abstract void heal(float healAmount);

    private int deathEventsPosted;


    @Override
    public Vector3d getHeadRotation() {
        // pitch, yaw, roll -- Minecraft does not currently support head roll
        return new Vector3d(getRotation().getX(), getRotationYawHead(), 0);
    }

    @Override
    public void setHeadRotation(Vector3d rotation) {
        setRotation(getRotation().mul(0, 1, 1).add(rotation.getX(), 0, 0));
        setRotationYawHead((float) rotation.getY());
    }

    @Override
    public int getMaxAir() {
        return this.maxAir;
    }

    @Override
    public void setMaxAir(int air) {
        this.maxAir = air;
    }

    @Override
    public void setLastDamage(double damage) {
        this.lastDamage = (float) damage;
    }

    @Override
    public void readFromNbt(NBTTagCompound compound) {
        super.readFromNbt(compound);
        if (compound.hasKey("maxAir")) {
            this.maxAir = compound.getInteger("maxAir");
        }
    }

    @Override
    public void writeToNbt(NBTTagCompound compound) {
        super.writeToNbt(compound);
        compound.setInteger("maxAir", this.maxAir);
    }

    @Override
    public Text getTeamRepresentation() {
        return Text.of(this.getUniqueID().toString());
    }

    /**
     * @author blood - May 12th, 2016
     * @author gabizou - June 4th, 2016 - Update for 1.9.4 and Cause Tracker changes
     *
     * @reason SpongeForge requires an overwrite so we do it here instead. This handles all living entity death events
     * (except players). Note should be taken that normally, there are lists for drops captured, however, the drops
     * are retroactively captured by the PhaseTracker and associated through the different phases, depending on
     * how they are handled, so no lists and flags on the entities themselves are needed as they are tracked through
     * the {@link PhaseContext} of the current {@link IPhaseState} at which this method is called. The compatibility
     * for Forge's events to throw are handled specially in SpongeForge.
     */
    @Overwrite
    public void onDeath(DamageSource cause) {
        // Sponge Start - Call our event, and forge's event
        // This will transitively call the forge event
        final boolean isMainThread = !((IMixinWorld) this.world).isFake() || Sponge.isServerAvailable() && Sponge.getServer().isMainThread();
        if (!this.isDead) { // isDead should be set later on in this method so we aren't re-throwing the events.
            if (isMainThread && this.deathEventsPosted <= MAX_DEATH_EVENTS_BEFORE_GIVING_UP) {
                // ignore because some moron is not resetting the entity.
                this.deathEventsPosted++;
                if (SpongeCommonEventFactory.callDestructEntityEventDeath((EntityLivingBase) (Object) this, cause, isMainThread).map(Cancellable::isCancelled).orElse(true)) {
                    // Since the forge event is cancellable
                    return;
                }
            }
        } else {
            this.deathEventsPosted = 0;
        }

        // Double check that the PhaseTracker is already capturing the Death phase
        try (final EntityDeathContext context = createOrNullDeathPhase(isMainThread, cause)) {
            // We re-enter the state only if we aren't already in the death state. This can usually happen when
            // and only when the onDeath method is called outside of attackEntityFrom, which should never happen.
            // but then again, mods....
            if (context != null) {
                context.buildAndSwitch();
            }
            // Sponge End
            if (this.dead) {
                return;
            }

            Entity entity = cause.getTrueSource();
            EntityLivingBase entitylivingbase = this.getAttackingEntity();

            if (this.scoreValue >= 0 && entitylivingbase != null) {
                entitylivingbase.awardKillScore((EntityLivingBase) (Object) this, this.scoreValue, cause);
            }

            if (entity != null) {
                entity.onKillEntity((EntityLivingBase) (Object) this);
            }

            this.dead = true;
            this.getCombatTracker().reset();

            if (!this.world.isRemote) {
                int i = 0;

                if (entity instanceof EntityPlayer) {
                    // Sponge Start - use Forge hooks for the looting modifier.
                    //i = EnchantmentHelper.getLootingModifier((EntityLivingBase) entity);
                    i = SpongeImplHooks.getLootingEnchantmentModifier(this, (EntityLivingBase) entity, cause);
                }

                if (this.canDropLoot() && this.world.getGameRules().getBoolean("doMobLoot")) {
                    boolean flag = this.recentlyHit > 0;
                    this.dropLoot(flag, i, cause);
                }

            }

            // Sponge Start - Don't send the state if this is a human. Fixes ghost items on client.
            if (!((EntityLivingBase) (Object) this instanceof EntityHuman)) {
                this.world.setEntityState((EntityLivingBase) (Object) this, (byte) 3);
            }

        }

        // Sponge End
    }

    @Override
    public void resetDeathEventsPosted() {
        this.deathEventsPosted = 0;
    }

    /**
     * @author gabizou - April 29th, 2018
     * @reason Due to cancelling death events, "healing" the entity is the only way to cancel the
     * death, but we still want to reset the death event counter. This is the simplest way to get it working
     * with forge mods who do not have access to Sponge's API.
     *
     * @param health The health
     * @param info The callback
     */
    @Inject(method = "setHealth", at = @At("HEAD"))
    private void onSetHealthResetEvents(float health, CallbackInfo info) {
        if (this.getHealth() <= 0 && health > 0) {
            resetDeathEventsPosted();
        }
    }

    @Nullable
    private EntityDeathContext createOrNullDeathPhase(boolean isMainThread, DamageSource source) {
        boolean tracksEntityDeaths = false;
        if (((IMixinWorld) this.world).isFake() || !isMainThread) { // Short circuit to avoid erroring on handling
            return null;
        }
        final IPhaseState<?> state = PhaseTracker.getInstance().getCurrentPhaseData().state;
        tracksEntityDeaths = !state.tracksEntityDeaths() && state != EntityPhase.State.DEATH;
        if (tracksEntityDeaths) {
            final EntityDeathContext context = EntityPhase.State.DEATH.createPhaseContext()
                .setDamageSource((org.spongepowered.api.event.cause.entity.damage.source.DamageSource) source)
                .source(this);
            this.getNotifierUser().ifPresent(context::notifier);
            this.getCreatorUser().ifPresent(context::owner);
            return context;
        }
        return null;
    }

    @Redirect(method = "applyPotionDamageCalculations", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;isPotionActive(Lnet/minecraft/potion/Potion;)Z") )
    private boolean onIsPotionActive(EntityLivingBase entityIn, Potion potion) {
        return false; // handled in our damageEntityHook
    }

    /**
     * @author blood - Some time ago in 2015?
     * @reason Our damage hook handles armor modifiers and "replaying" damage to armor.
     *
     * @param entityIn The entity being damaged
     * @param damage The damage to deal
     */
    @Redirect(method = "applyArmorCalculations", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;damageArmor(F)V") )
    private void onDamageArmor(EntityLivingBase entityIn, float damage) {
        // do nothing as this is handled in our damageEntityHook
    }

    /**
     * @author bloodmc - November 21, 2015
     * @reason This shouldn't be used internally but a mod may still call it so we simply reroute to our hook.
     */
    @Overwrite
    protected void damageEntity(DamageSource damageSource, float damage) {
        this.damageEntityHook(damageSource, damage);
    }

    /**
     * @author bloodmc - November 22, 2015
     * @author gabizou - Updated April 11th, 2016 - Update for 1.9 changes
     * @author Aaron1011 - Updated Nov 11th, 2016 - Update for 1.11 changes
     *
     * @reason Reroute damageEntity calls to our hook in order to prevent damage.
     */
    @Override
    @Overwrite
    public boolean attackEntityFrom(DamageSource source, float amount) {
        // Sponge start - Add certain hooks for necessities
        this.lastDamageSource = source;
        if (source == null) {
            new PrettyPrinter(60).centre().add("Null DamageSource").hr()
                .addWrapped("Sponge has found a null damage source! This should NEVER happen "
                            + "as the DamageSource is used for all sorts of calculations. Usually"
                            + " this can be considered developer error. Please report the following"
                            + " stacktrace to the most appropriate mod/plugin available.")
                .add()
                .add(new IllegalArgumentException("Null DamageSource"))
                .log(SpongeImpl.getLogger(), Level.WARN);
            return false;
        }
        // Sponge - This hook is for forge use mainly
        if (!this.hookModAttack((EntityLivingBase) (Object) this, source, amount))
            return false;
        // Sponge end
        if (this.isEntityInvulnerable(source)) {
            return false;
        } else if (this.world.isRemote) {
            return false;
        } else {
            this.idleTime = 0;

            // Sponge - if the damage source is ignored, then do not return false here, as the health
            // has already been set to zero if Keys#HEALTH or SpongeHealthData is set to zero.
            if (this.getHealth() <= 0.0F && source != DamageSourceRegistryModule.IGNORED_DAMAGE_SOURCE) {
                return false;
            } else if (source.isFireDamage() && this.isPotionActive(MobEffects.FIRE_RESISTANCE)) {
                return false;
            } else {
                // Sponge - ignore as this is handled in our damageEntityHookge
//                if (false && (source == DamageSource.anvil || source == DamageSource.fallingBlock)
//                    && this.getEquipmentInSlot(4) != null) {
//                    this.getEquipmentInSlot(4).damageItem((int) (amount * 4.0F + this.rand.nextFloat() * amount * 2.0F),
//                            (EntityLivingBase) (Object) this);
//                    amount *= 0.75F;
//                }
                // Sponge End

                // Sponge - set the 'shield blocking ran' flag to the proper value, since
                // we comment out the logic below
                boolean flag = amount > 0.0F && this.canBlockDamageSource(source);

                // Sponge start - this is handled in our damageEntityHook
                /*boolean flag = false;

                if (amount > 0.0F && this.canBlockDamageSource(source)) {
                    this.damageShield(amount);

                    if (!source.isProjectile())
                    {
                        Entity entity = source.getSourceOfDamage();

                        if (entity instanceof EntityLivingBase)
                        {
                            this.blockUsingShield((EntityLivingBase)entity);
                        }
                    }

                    flag = true;
                }*/
                // Sponge end

                this.limbSwingAmount = 1.5F;
                boolean flag1 = true;

                if ((float) this.hurtResistantTime > (float) this.maxHurtResistantTime / 2.0F) {
                    if (amount <= this.lastDamage) { // Technically, this is wrong since 'amount' won't be 0 if a shield is used. However, we need damageEntityHook so that we process the shield, so we leave it as-is
                        return false;
                    }

                    // Sponge start - reroute to our damage hook
                    if (!this.damageEntityHook(source, amount - this.lastDamage)) {
                        return false;
                    }
                    // Sponge end

                    this.lastDamage = amount;
                    flag1 = false;
                } else {
                    // Sponge start - reroute to our damage hook
                    if (!this.damageEntityHook(source, amount)) {
                        return false;
                    }
                    this.lastDamage = amount;
                    this.hurtResistantTime = this.maxHurtResistantTime;
                    // this.damageEntity(source, amount); // handled above
                    // Sponge end
                    this.hurtTime = this.maxHurtTime = 10;
                }

                this.attackedAtYaw = 0.0F;
                net.minecraft.entity.Entity entity = source.getTrueSource();

                if (entity != null) {
                    if (entity instanceof EntityLivingBase) {
                        this.shadow$setRevengeTarget((EntityLivingBase) entity);
                    }

                    if (entity instanceof EntityPlayer) {
                        this.recentlyHit = 100;
                        this.attackingPlayer = (EntityPlayer) entity;
                    } else if (entity instanceof EntityWolf) {
                        EntityWolf entityWolf = (EntityWolf) entity;

                        if (entityWolf.isTamed()) {
                            this.recentlyHit = 100;
                            this.attackingPlayer = null;
                        }
                    }
                }

                if (flag1) {
                    if (flag) {
                        this.world.setEntityState((EntityLivingBase) (Object) this, (byte) 29);
                    } else if (source instanceof net.minecraft.util.EntityDamageSource && ((net.minecraft.util.EntityDamageSource) source).getIsThornsDamage()) {
                        this.world.setEntityState((EntityLivingBase) (Object) this, (byte) 33);
                    } else {
                        this.world.setEntityState((EntityLivingBase) (Object) this, (byte) 2);
                    }


                    if (source != DamageSource.DROWN && !flag) { // Sponge - remove 'amount > 0.0F' - it's redundant in Vanilla, and breaks our handling of shields
                        this.markVelocityChanged();
                    }

                    if (entity != null) {
                        double d1 = entity.posX - this.posX;
                        double d0;

                        for (d0 = entity.posZ - this.posZ; d1 * d1 + d0 * d0 < 1.0E-4D; d0 = (Math.random() - Math.random()) * 0.01D) {
                            d1 = (Math.random() - Math.random()) * 0.01D;
                        }

                        this.attackedAtYaw = (float) (MathHelper.atan2(d0, d1) * 180.0D / Math.PI - (double) this.rotationYaw);
                        this.knockBack(entity, 0.4F, d1, d0);
                    } else {
                        this.attackedAtYaw = (float) ((Math.random() * 2.0D) * 180);
                    }
                }

                if (this.getHealth() <= 0.0F) {
                    if (!this.checkTotemDeathProtection(source)) {
                        SoundEvent soundevent = this.getDeathSound();

                        if (flag1 && soundevent != null) {
                            this.playSound(soundevent, this.getSoundVolume(), this.getSoundPitch());
                        }

                        // Sponge Start - notify the cause tracker
                        try (final EntityDeathContext context = createOrNullDeathPhase(true, source)) {
                            if (context != null) {
                                context.buildAndSwitch();
                            }
                            this.onDeath(source);
                        }
                    }
                    // Sponge End
                } else if (flag1) {
                    this.playHurtSound(source);
                }


                if (!flag) // Sponge - remove 'amount > 0.0F'
                {
                    this.lastDamageSource = source;
                    this.lastDamageStamp = this.world.getTotalWorldTime();
                }

                return !flag; // Sponge - remove 'amount > 0.0F'
            }
        }
    }

    /**
     * @author gabizou - January 4th, 2016
     * This is necessary for invisibility checks so that vanish players don't actually send the particle stuffs.
     */
    @Redirect(method = "updateItemUse", at = @At(value = "INVOKE", target = WORLD_SPAWN_PARTICLE))
    public void spawnItemParticle(World world, EnumParticleTypes particleTypes, double xCoord, double yCoord, double zCoord, double xOffset,
            double yOffset, double zOffset, int ... p_175688_14_) {
        if (!this.isVanished()) {
            this.world.spawnParticle(particleTypes, xCoord, yCoord, zCoord, xOffset, yOffset, zOffset, p_175688_14_);
        }
    }

    @Override
    public boolean damageEntityHook(DamageSource damageSource, float damage) {
        if (!this.isEntityInvulnerable(damageSource)) {
            final boolean human = (Object) this instanceof EntityPlayer;
            // apply forge damage hook
            damage = applyModDamage((EntityLivingBase) (Object) this, damageSource, damage);
            float originalDamage = damage; // set after forge hook.
            if (damage <= 0) {
                damage = 0;
            }

            List<DamageFunction> originalFunctions = new ArrayList<>();
            Optional<DamageFunction> hardHatFunction =
                DamageEventHandler.createHardHatModifier((EntityLivingBase) (Object) this, damageSource);
            Optional<List<DamageFunction>> armorFunction =
                provideArmorModifiers((EntityLivingBase) (Object) this, damageSource, damage);
            Optional<DamageFunction> resistanceFunction =
                DamageEventHandler.createResistanceModifier((EntityLivingBase) (Object) this, damageSource);
            Optional<List<DamageFunction>> armorEnchantments =
                DamageEventHandler.createEnchantmentModifiers((EntityLivingBase) (Object) this, damageSource);
            Optional<DamageFunction> absorptionFunction =
                DamageEventHandler.createAbsorptionModifier((EntityLivingBase) (Object) this, damageSource);
            Optional<DamageFunction> shieldFunction =
                DamageEventHandler.createShieldFunction((EntityLivingBase) (Object) this, damageSource, damage);

            if (hardHatFunction.isPresent()) {
                originalFunctions.add(hardHatFunction.get());
            }

            if (shieldFunction.isPresent()) {
                originalFunctions.add(shieldFunction.get());
            }

            if (armorFunction.isPresent()) {
                originalFunctions.addAll(armorFunction.get());
            }

            if (resistanceFunction.isPresent()) {
                originalFunctions.add(resistanceFunction.get());
            }

            if (armorEnchantments.isPresent()) {
                originalFunctions.addAll(armorEnchantments.get());
            }

            if (absorptionFunction.isPresent()) {
                originalFunctions.add(absorptionFunction.get());
            }
            try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
                DamageEventHandler.generateCauseFor(damageSource, frame);

                DamageEntityEvent event = SpongeEventFactory.createDamageEntityEvent(frame.getCurrentCause(), originalFunctions, this, originalDamage);
                if (damageSource != DamageSourceRegistryModule.IGNORED_DAMAGE_SOURCE) { // Basically, don't throw an event if it's our own damage source
                    Sponge.getEventManager().post(event);
                }
                if (event.isCancelled()) {
                    return false;
                }

                damage = (float) event.getFinalDamage();

                // Helmet
                final ItemStack mainHandItem = this.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND);
                if ((damageSource instanceof FallingBlockDamageSource) && mainHandItem != null) {
                    mainHandItem.damageItem((int) (event.getBaseDamage() * 4.0F + this.rand.nextFloat() * event.getBaseDamage() * 2.0F), (EntityLivingBase) (Object) this);
                }

                // Shield
                if (shieldFunction.isPresent()) {
                    this.damageShield((float) event.getBaseDamage()); // TODO gabizou: Should this be in the API?
                    if (!damageSource.isProjectile()) {
                        Entity entity = damageSource.getImmediateSource();

                        if (entity instanceof EntityLivingBase) {
                            this.blockUsingShield((EntityLivingBase) entity);
                        }
                    }
                }

                // Armor
                if (!damageSource.isUnblockable()) {
                    for (DamageFunction modifier : event.getModifiers()) {
                        applyArmorDamage((EntityLivingBase) (Object) this, damageSource, event, modifier.getModifier());
                    }
                }

                double absorptionModifier = absorptionFunction.map(function -> event.getDamage(function.getModifier())).orElse(0d);
                if (absorptionFunction.isPresent()) {
                    absorptionModifier = event.getDamage(absorptionFunction.get().getModifier());
                }

                this.setAbsorptionAmount(Math.max(this.getAbsorptionAmount() + (float) absorptionModifier, 0.0F));
                if (damage != 0.0F) {
                    if (human) {
                        ((EntityPlayer) (Object) this).addExhaustion(damageSource.getHungerDamage());
                    }
                    float f2 = this.getHealth();

                    this.setHealth(f2 - damage);
                    this.getCombatTracker().trackDamage(damageSource, f2, damage);

                    if (human) {
                        return true;
                    }

                    this.setAbsorptionAmount(this.getAbsorptionAmount() - damage);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * @author Aaron1011 - August 15, 2016
     * @reason An overwrite avoids the need for a local-capture inject and two redirects
     */
    // TODO: Investigate mixing into setPositionAndUpdate to catch more teleports
    @Overwrite
    public boolean attemptTeleport(double x, double y, double z)
    {
        double d0 = this.posX;
        double d1 = this.posY;
        double d2 = this.posZ;
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        boolean flag = false;
        BlockPos blockpos = new BlockPos((Entity) (Object) this);
        World world = this.world;
        Random random = this.getRNG();

        if (world.isBlockLoaded(blockpos))
        {
            boolean flag1 = false;

            while (!flag1 && blockpos.getY() > 0)
            {
                BlockPos blockpos1 = blockpos.down();
                IBlockState iblockstate = world.getBlockState(blockpos1);

                if (iblockstate.getMaterial().blocksMovement())
                {
                    flag1 = true;
                }
                else
                {
                    --this.posY;
                    blockpos = blockpos1;
                }
            }

            if (flag1)
            {
                // Sponge start
                if (!world.isRemote) {
                    Transform<org.spongepowered.api.world.World> fromTransform = this.getTransform().setPosition(new Vector3d(d0, d1, d2));
                    Transform<org.spongepowered.api.world.World> toTransform = this.getTransform().setPosition(new Vector3d(this.posX, this.posY, this.posZ));

                    MoveEntityEvent.Teleport event = EntityUtil.handleDisplaceEntityTeleportEvent((Entity) (Object) this, fromTransform, toTransform);
                    if (event.isCancelled()) {
                        this.posX = d0;
                        this.posY = d1;
                        this.posZ = d2;
                        return false;
                    }
                    Vector3d position = event.getToTransform().getPosition();
                    this.rotationYaw = (float) event.getToTransform().getYaw();
                    this.rotationPitch = (float) event.getToTransform().getPitch();
                    this.setPositionAndUpdate(position.getX(), position.getY(), position.getZ());
                } else {
                    this.setPositionAndUpdate(this.posX, this.posY, this.posZ);
                }
                // Sponge end

                if (world.getCollisionBoxes((Entity) (Object) this, this.getEntityBoundingBox()).isEmpty() && !world.containsAnyLiquid(this.getEntityBoundingBox()))
                {
                    flag = true;
                }
            }
        }

        if (!flag)
        {
            // Sponge start - this is technically a teleport, since it sends packets to players and calls 'updateEntityWithOptionalForce' - even though it doesn't really move the entity at all
            if (!world.isRemote) {
                Transform<org.spongepowered.api.world.World> transform = this.getTransform().setPosition(new Vector3d(d0, d1, d2));
                MoveEntityEvent.Teleport event = EntityUtil.handleDisplaceEntityTeleportEvent((Entity) (Object) this, transform, transform);
                if (event.isCancelled()) {
                    return false;
                }
                Vector3d position = event.getToTransform().getPosition();
                this.rotationYaw = (float) event.getToTransform().getYaw();
                this.rotationPitch = (float) event.getToTransform().getPitch();
                this.setPositionAndUpdate(position.getX(), position.getY(), position.getZ());
            } else {
                this.setPositionAndUpdate(d0, d1, d2);
            }
            // Sponge end

            return false;
        }
        else
        {
            // int i = 128;

            for (int j = 0; j < 128; ++j)
            {
                double d6 = (double)j / 127.0D;
                float f = (random.nextFloat() - 0.5F) * 0.2F;
                float f1 = (random.nextFloat() - 0.5F) * 0.2F;
                float f2 = (random.nextFloat() - 0.5F) * 0.2F;
                double d3 = d0 + (this.posX - d0) * d6 + (random.nextDouble() - 0.5D) * (double)this.width * 2.0D;
                double d4 = d1 + (this.posY - d1) * d6 + random.nextDouble() * (double)this.height;
                double d5 = d2 + (this.posZ - d2) * d6 + (random.nextDouble() - 0.5D) * (double)this.width * 2.0D;
                world.spawnParticle(EnumParticleTypes.PORTAL, d3, d4, d5, (double)f, (double)f1, (double)f2, new int[0]);
            }

            if ((Object) this instanceof EntityCreature)
            {
                ((EntityCreature) (Object) this).getNavigator().clearPath();
            }

            return true;
        }
    }

    @Override
    public float applyModDamage(EntityLivingBase entityLivingBase, DamageSource source, float damage) {
        return damage;
    }

    @Override
    public Optional<List<DamageFunction>> provideArmorModifiers(EntityLivingBase entityLivingBase,
                                                                                                         DamageSource source, double damage) {
        return DamageEventHandler.createArmorModifiers(entityLivingBase, source, damage);
    }

    @Override
    public void applyArmorDamage(EntityLivingBase entityLivingBase, DamageSource source, DamageEntityEvent entityEvent, DamageModifier modifier) {
        Optional<DamageObject> optional = modifier.getCause().first(DamageObject.class);
        if (optional.isPresent()) {
            DamageEventHandler.acceptArmorModifier((EntityLivingBase) (Object) this, source, modifier, entityEvent.getDamage(modifier));
        }
    }

    @Override
    public boolean hookModAttack(EntityLivingBase entityLivingBase, DamageSource source, float amount) {
        return true;
    }

    /**
     * @author gabizou - January 4th, 2016
     * @reason This allows invisiblity to ignore entity collisions.
     */
    @Overwrite
    public boolean canBeCollidedWith() {
        return !(this.isVanished() && this.ignoresCollision()) && !this.isDead;
    }

    @Override
    public int getRecentlyHit() {
        return this.recentlyHit;
    }

    @Redirect(method = "updateFallState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldServer;spawnParticle(Lnet/minecraft/util/EnumParticleTypes;DDDIDDDD[I)V"))
    private void spongeSpawnParticleForFallState(WorldServer worldServer, EnumParticleTypes particleTypes, double xCoord, double yCoord,
            double zCoord, int numberOfParticles, double xOffset, double yOffset, double zOffset, double particleSpeed, int... extraArgs) {
        if (!this.isVanished()) {
            worldServer.spawnParticle(particleTypes, xCoord, yCoord, zCoord, numberOfParticles, xOffset, yOffset, zOffset, particleSpeed, extraArgs);
        }

    }

    @Redirect(method = "onEntityUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;onDeathUpdate()V"))
    private void causeTrackDeathUpdate(EntityLivingBase entityLivingBase) {
        if (!entityLivingBase.world.isRemote) {
            try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame();
                 PhaseContext<?> context = EntityPhase.State.DEATH_UPDATE.createPhaseContext().source(entityLivingBase)) {
                context.buildAndSwitch();
                frame.pushCause(entityLivingBase);
                ((IMixinEntityLivingBase) entityLivingBase).onSpongeDeathUpdate();
            }
        } else {
            ((IMixinEntityLivingBase) entityLivingBase).onSpongeDeathUpdate();
        }
    }

    private EquipmentSlotFabric inventory = new EquipmentSlotFabric(this);
    private EnumMap<EntityEquipmentSlot, SlotLens> slotLens = new EnumMap<>(EntityEquipmentSlot.class);

    @Inject(method = "onUpdate", locals = LocalCapture.CAPTURE_FAILHARD,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;getItemStackFromSlot(Lnet/minecraft/inventory/EntityEquipmentSlot;)Lnet/minecraft/item/ItemStack;"))
    private void onGetItemStackFromSlot(CallbackInfo ci, int i, EntityEquipmentSlot[] slots, int j, int k,
                                        EntityEquipmentSlot entityEquipmentSlot, ItemStack before) {
        ItemStack after = this.getItemStackFromSlot(entityEquipmentSlot);
        EntityLivingBase entity = EntityUtil.toNative((IMixinEntityLivingBase) this);
        if (!ItemStack.areItemStacksEqual(after, before)) {
            InventoryAdapter slotAdapter;
            if (entity instanceof EntityPlayerMP) {
                SlotLens slotLens;
                IMixinInventoryPlayer inventory = (IMixinInventoryPlayer) ((EntityPlayerMP) entity).inventory;
                PlayerInventoryLens inventoryLens = (PlayerInventoryLens) inventory.getRootLens();
                switch (entityEquipmentSlot) {
                case OFFHAND:
                    slotLens = inventoryLens.getOffhandLens();
                    break;
                case MAINHAND:
                    HotbarLens hotbarLens = inventoryLens.getMainLens().getHotbar();
                    slotLens = hotbarLens.getSlot(hotbarLens.getSelectedSlotIndex(inventory.getFabric()));
                    break;
                default:
                    slotLens = inventoryLens.getEquipmentLens().getSlot(entityEquipmentSlot.getIndex());
                }
                slotAdapter = slotLens.getAdapter(inventory.getFabric(), inventory);
            } else {
                if (this.slotLens.isEmpty()) {
                    for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
                        this.slotLens.put(slot, new SlotLensImpl(slot.getSlotIndex()));
                    }
                }
                slotAdapter = this.slotLens.get(entityEquipmentSlot).getAdapter(this.inventory, null);
            }
            ChangeEntityEquipmentEvent event = SpongeCommonEventFactory.callChangeEntityEquipmentEvent(entity,
                    ItemStackUtil.snapshotOf(before), ItemStackUtil.snapshotOf(after), (SlotAdapter) slotAdapter);
            if (event.isCancelled()) {
                this.setItemStackToSlot(entityEquipmentSlot, before);
                return;
            }
            Transaction<ItemStackSnapshot> transaction = event.getTransaction();
            if (!transaction.isValid()) {
                this.setItemStackToSlot(entityEquipmentSlot, before);
                return;
            }
            Optional<ItemStackSnapshot> optional = transaction.getCustom();
            if (optional.isPresent()) {
                ItemStack custom = ItemStackUtil.fromSnapshotToNative(optional.get());
                this.setItemStackToSlot(entityEquipmentSlot, custom);
            }
        }
    }

    @Override
    public void onSpongeDeathUpdate() {
        this.onDeathUpdate();
    }

    @Redirect(method = "onDeathUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;getExperiencePoints(Lnet/minecraft/entity/player/EntityPlayer;)I"))
    private int onGetExperiencePoints(EntityLivingBase entity, EntityPlayer attackingPlayer) {
        if (entity instanceof IMixinEntityPlayerMP) {
            if (((IMixinEntityPlayerMP) entity).keepInventory()) {
                return 0;
            }
        }
        return this.getExperiencePoints(attackingPlayer);
    }

    @Inject(method = "onItemPickup", at = @At("HEAD"))
    public void onEntityItemPickup(net.minecraft.entity.Entity entityItem, int unused, CallbackInfo ci) {
        if (!this.world.isRemote) {
//            EntityUtil.toMixin(entityItem).setDestructCause(Cause.of(NamedCause.of("PickedUp", this)));
        }
    }

    @Inject(method = "onItemUseFinish", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;resetActiveHand()V"))
    private void updateHealthForUseFinish(CallbackInfo ci) {
        if (this instanceof IMixinEntityPlayerMP) {
            ((IMixinEntityPlayerMP) this).refreshScaledHealth();
        }
    }

    // Data delegated methods

    @Override
    public HealthData getHealthData() {
        return new SpongeHealthData(this.getHealth(), this.getMaxHealth());
    }

    @Override
    public MutableBoundedValue<Double> health() {
        return SpongeValueFactory.boundedBuilder(Keys.HEALTH)
                .minimum(0D)
                .maximum((double) this.getMaxHealth())
                .defaultValue((double) this.getMaxHealth())
                .actualValue((double) this.getHealth())
                .build();
    }

    @Override
    public MutableBoundedValue<Double> maxHealth() {
        return SpongeValueFactory.boundedBuilder(Keys.MAX_HEALTH)
                .minimum(1D)
                .maximum((double) Float.MAX_VALUE)
                .defaultValue(20D)
                .actualValue((double) this.getMaxHealth())
                .build();
    }

    @Override
    public DamageableData getDamageableData() {
        return new SpongeDamageableData((Living) this.revengeTarget, (double) this.lastDamage);
    }

    @Override
    public OptionalValue<EntitySnapshot> lastAttacker() {
        return new SpongeOptionalValue<>(Keys.LAST_ATTACKER, Optional.empty(), Optional.ofNullable(this.revengeTarget == null ?
                null : ((Living) this.revengeTarget).createSnapshot()));
    }

    @Override
    public OptionalValue<Double> lastDamage() {
        return new SpongeOptionalValue<>(Keys.LAST_DAMAGE, Optional.empty(), Optional.ofNullable(this.revengeTarget == null ?
                null : (double) (this.lastDamage)));
    }

    @Override
    public double getLastDamageTaken() {
        return this.lastDamage;
    }

    @Override
    public void supplyVanillaManipulators(List<DataManipulator<?, ?>> manipulators) {
        super.supplyVanillaManipulators(manipulators);
        manipulators.add(getHealthData());
    }

    @Override
    public <T extends Projectile> Optional<T> launchProjectile(Class<T> projectileClass) {
        return ProjectileLauncher.launch(checkNotNull(projectileClass, "projectile class"), this, null);
    }

    @Override
    public <T extends Projectile> Optional<T> launchProjectile(Class<T> projectileClass, Vector3d velocity) {
        return ProjectileLauncher.launch(checkNotNull(projectileClass, "projectile class"), this, checkNotNull(velocity, "velocity"));
    }

    @Override
    public void setElytraFlying(boolean value) {
        setFlag(DataConstants.ELYTRA_FLYING_FLAG, value);
    }

    // Start implementation of UseItemstackEvent

    @Inject(method = "setActiveHand", cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD,
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;activeItemStack:Lnet/minecraft/item/ItemStack;"))
    private void onSetActiveItemStack(EnumHand hand, CallbackInfo ci, ItemStack stack) {
        if (this.world.isRemote) {
            return;
        }

        Sponge.getCauseStackManager().pushCause(this);
        UseItemStackEvent.Start event = SpongeEventFactory.createUseItemStackEventStart(Sponge.getCauseStackManager().getCurrentCause(),
                stack.getMaxItemUseDuration(), stack.getMaxItemUseDuration(), ItemStackUtil.snapshotOf(stack));
        if (SpongeImpl.postEvent(event)) {
            ci.cancel();
        } else {
            this.activeItemStackUseCount = event.getRemainingDuration();
        }
        Sponge.getCauseStackManager().popCause();
    }

    @Redirect(method = "setActiveHand", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;activeItemStackUseCount:I"))
    private void getItemDuration(EntityLivingBase this$0, int count) {
        if (this.world.isRemote) {
            this.activeItemStackUseCount = count;
        }
        // If we're on the server, do nothing, since we already set this field on onSetActiveItemStack
    }

    @Redirect(method = "updateActiveHand",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;getItemInUseCount()I", ordinal = 0))
    private int onGetRemainingItemDuration(EntityLivingBase self) {
        if (this.world.isRemote) {
            return self.getItemInUseCount();
        }

        Sponge.getCauseStackManager().pushCause(this);
        UseItemStackEvent.Tick event = SpongeEventFactory.createUseItemStackEventTick(Sponge.getCauseStackManager().getCurrentCause(),
                this.activeItemStackUseCount, this.activeItemStackUseCount, ItemStackUtil.snapshotOf(this.activeItemStack));
        SpongeImpl.postEvent(event);
        Sponge.getCauseStackManager().popCause();
        // Because the item usage will only finish if activeItemStackUseCount == 0 and decrements it first, it should be >= 1
        this.activeItemStackUseCount = Math.max(event.getRemainingDuration(), 1);

        if (event.isCancelled()) {
            // Get prepared for some cool hacks: We're within the condition for updateItemUse
            // So if we don't want it to call the method we just pass a value that makes the
            // condition evaluate to false, so an integer >= 25
            return 26;
        }
        SpongeImplHooks.onUseItemTick((EntityLivingBase) (Object) this, this.activeItemStack, this.activeItemStackUseCount);


        return getItemInUseCount();
    }

    @Inject(method = "onItemUseFinish", cancellable = true,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;updateItemUse(Lnet/minecraft/item/ItemStack;I)V"))
    private void onUpdateItemUse(CallbackInfo ci) {
        if (this.world.isRemote) {
            return;
        }

        Sponge.getCauseStackManager().pushCause(this);
        UseItemStackEvent.Finish event = SpongeEventFactory.createUseItemStackEventFinish(Sponge.getCauseStackManager().getCurrentCause(),
                this.activeItemStackUseCount, this.activeItemStackUseCount, ItemStackUtil.snapshotOf(this.activeItemStack));
        SpongeImpl.postEvent(event);
        Sponge.getCauseStackManager().popCause();
        if (event.getRemainingDuration() > 0) {
            this.activeItemStackUseCount = event.getRemainingDuration();
            ci.cancel();
        } else if (event.isCancelled()) {
            resetActiveHand();
            ci.cancel();
        } else {
            this.activeItemStackCopy = this.activeItemStack.copy();
        }
    }

    @Redirect(method = "onItemUseFinish", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;"
            + "setHeldItem(Lnet/minecraft/util/EnumHand;Lnet/minecraft/item/ItemStack;)V"))
    private void onSetHeldItem(EntityLivingBase self, EnumHand hand, @Nullable ItemStack stack) {
        if (this.world.isRemote) {
            self.setHeldItem(hand, stack);
            return;
        }

        // Unforunately, ItemFood calls ItemStack#shrink in Item#onItemUseFinish.
        // To ensure that we provide the original ItemStack in the event,
        // we make a copy of in our onUpdateItemUse redirect
        // If the event or transaction is cancelled, we make sure to explicitly
        // set the copy back in the player's hand, since it may have been already
        // modified if an ItemFood is being used.

        ItemStackSnapshot activeItemStackSnapshot = ItemStackUtil.snapshotOf(this.activeItemStackCopy);
        Sponge.getCauseStackManager().pushCause(this);
        UseItemStackEvent.Replace event = SpongeEventFactory.createUseItemStackEventReplace(Sponge.getCauseStackManager().getCurrentCause(),
                this.activeItemStackUseCount, this.activeItemStackUseCount, activeItemStackSnapshot,
                new Transaction<>((org.spongepowered.api.item.inventory.ItemStack) this.activeItemStackCopy, ItemStackUtil.snapshotOf(stack)));

        if (SpongeImpl.postEvent(event)) {
            Sponge.getCauseStackManager().popCause();
            this.setHeldItem(hand, this.activeItemStackCopy.copy());
            return;
        }
        Sponge.getCauseStackManager().popCause();

        if (!event.getItemStackResult().isValid()) {
            this.setHeldItem(hand, this.activeItemStackCopy.copy());
            return;
        }

        setHeldItem(hand, ItemStackUtil.fromSnapshotToNative(event.getItemStackResult().getFinal()));
    }

    @Redirect(method = "stopActiveHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;"
            + "onPlayerStoppedUsing(Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;I)V")) // stopActiveHand
    private void onStopPlayerUsing(ItemStack stack, World world, EntityLivingBase self, int duration) {
        if (this.world.isRemote) {
            stack.onPlayerStoppedUsing(world, self, duration);
            return;
        }
        Sponge.getCauseStackManager().pushCause(this);
        if (!SpongeImpl.postEvent(SpongeEventFactory.createUseItemStackEventStop(Sponge.getCauseStackManager().getCurrentCause(),
                duration, duration, ItemStackUtil.snapshotOf(stack)))) {
            stack.onPlayerStoppedUsing(world, self, duration);
        }
        Sponge.getCauseStackManager().popCause();
    }

    @Inject(method = "resetActiveHand", at = @At("HEAD"))
    private void onResetActiveHand(CallbackInfo ci) {
        if (this.world.isRemote) {
            return;
        }

        // If we finished using an item, activeItemStackCopy will be non-null
        // However, if a player stopped using an item early, activeItemStackCopy will not be set
        ItemStackSnapshot snapshot = ItemStackUtil.snapshotOf(this.activeItemStackCopy != null ? this.activeItemStackCopy : this.activeItemStack);

        Sponge.getCauseStackManager().pushCause(this);
        SpongeImpl.postEvent(SpongeEventFactory.createUseItemStackEventReset(Sponge.getCauseStackManager().getCurrentCause(),
                this.activeItemStackUseCount, this.activeItemStackUseCount, snapshot));
        Sponge.getCauseStackManager().popCause();

        this.activeItemStackCopy = null;
    }

    // End implementation of UseItemStackEvent

}
