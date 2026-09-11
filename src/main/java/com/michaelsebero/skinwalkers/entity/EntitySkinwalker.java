package com.michaelsebero.skinwalkers.entity;

import javax.annotation.Nullable;

import com.michaelsebero.skinwalkers.ModRegistry;
import com.michaelsebero.skinwalkers.Skinwalkers;
import com.michaelsebero.skinwalkers.SkinwalkersConfig;
import com.michaelsebero.skinwalkers.entity.ai.AIBreakThrough;
import com.michaelsebero.skinwalkers.entity.ai.AIDisguisedPanic;
import com.michaelsebero.skinwalkers.entity.ai.AIPursue;
import com.michaelsebero.skinwalkers.entity.ai.AIRevealedTarget;
import com.michaelsebero.skinwalkers.entity.ai.AIStalk;
import com.michaelsebero.skinwalkers.entity.ai.AIWander;
import com.michaelsebero.skinwalkers.util.Sight;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * A monster that poses as a passive mob. Looked at for too long, or attacked, it screams and takes the form of the
 * player who caught it, then hunts them.
 */
public class EntitySkinwalker extends EntityMob {

    public static final ResourceLocation LOOT_TABLE = new ResourceLocation(Skinwalkers.MOD_ID, "entities/skinwalker");

    private static final DataParameter<NBTTagCompound> APPEARANCE =
            EntityDataManager.createKey(EntitySkinwalker.class, DataSerializers.COMPOUND_TAG);
    /** Entity ID of the player being stalked, or -1; tells that player's client to aim the head at its camera. */
    private static final DataParameter<Integer> STALKED =
            EntityDataManager.createKey(EntitySkinwalker.class, DataSerializers.VARINT);

    /** Status bytes for {@code World.setEntityState}; vanilla living entities use up to 46. */
    private static final byte REVEAL_EFFECTS = 70;
    private static final byte SHIFT_EFFECTS  = 71;

    private static final float PLAYER_WIDTH      = 0.6F;
    private static final float PLAYER_HEIGHT     = 1.8F;
    private static final float PLAYER_EYE_HEIGHT = 1.62F;

    private static final double PURSUIT_SPEED = 1.15D;
    /** Within this distance (squared) of its target when it turns, it strikes straight out of the smoke. */
    private static final double REVEAL_STRIKE_RANGE_SQ = 2.5D * 2.5D;

    private static final double GAZE_RANGE = 128.0;
    /** A slip of the aim shorter than this only pauses the gaze count; longer, and the count drains. */
    private static final int GAZE_SLIP_TICKS = 10;
    /** Once the cooldown has passed, a disguised skinwalker changes shape on average this many ticks later. */
    private static final int SHIFT_CHANCE = 600;
    private static final int STUCK_SAMPLE_TICKS = 10;
    private static final int STUCK_SAMPLES = 3;

    // Both sides. No field initialisers: data parameters can change while the superclass constructs.
    @Nullable
    private EntityLivingBase disguise;
    private boolean revealed;
    private int smokeTicks;

    // Server only.
    @Nullable
    private AIPursue pursue;
    private int shiftCooldown;
    /** Ticks it has been looked at, less ticks since spent unwatched. */
    private int gazeTicks;
    private int unwatchedTicks;
    private int targetlessTicks;
    @Nullable
    private EntityPlayer stalked;
    @Nullable
    private Vec3d stuckSample;
    private int stuckSamples;

    public EntitySkinwalker(World world) {
        super(world);
        setSize(0.9F, 1.4F);
        experienceValue = 7;
        shiftCooldown = shiftCooldownTicks();
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(APPEARANCE, new NBTTagCompound());
        dataManager.register(STALKED, -1);
    }

    @Override
    protected void initEntityAI() {
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new AIBreakThrough(this));
        pursue = new AIPursue(this, PURSUIT_SPEED);
        tasks.addTask(2, pursue);
        tasks.addTask(3, new AIDisguisedPanic(this, 1.25D));
        tasks.addTask(4, new AIStalk(this));
        tasks.addTask(5, new AIWander(this));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 6.0F));
        tasks.addTask(7, new EntityAILookIdle(this));
        targetTasks.addTask(1, new AIRevealedTarget.HurtBy(this));
        targetTasks.addTask(2, new AIRevealedTarget.NearestPlayer(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(SkinwalkersConfig.stats.maxHealth);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(SkinwalkersConfig.stats.attackDamage);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(SkinwalkersConfig.stats.movementSpeed);
        getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(2.0D);
        // Stalkers pick up players from 64 blocks away and path back behind them.
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(48.0D);
    }

    // ---- Appearance ----

    /** Whether it has taken a player's form. */
    public boolean isRevealed() {
        return revealed;
    }

    /** The mob or player double this skinwalker looks like; null on servers for a revealed skinwalker. */
    @Nullable
    public EntityLivingBase getDisguise() {
        return disguise;
    }

    private void setAppearance(NBTTagCompound appearance) {
        dataManager.set(APPEARANCE, appearance);
    }

    @Override
    public void notifyDataManagerChange(DataParameter<?> key) {
        super.notifyDataManagerChange(key);
        if (APPEARANCE.equals(key)) {
            NBTTagCompound appearance = dataManager.get(APPEARANCE);
            revealed = Appearance.isPlayer(appearance);
            disguise = Appearance.create(world, appearance);
            if (revealed) {
                setSize(PLAYER_WIDTH, PLAYER_HEIGHT);
            } else if (disguise != null) {
                setSize(disguise.width, disguise.height);
            }
        }
    }

    @Override
    public float getEyeHeight() {
        if (revealed) {
            return PLAYER_EYE_HEIGHT;
        }
        return disguise != null ? disguise.getEyeHeight() : super.getEyeHeight();
    }

    /** Multiplier that moves this skinwalker at the walking pace of the mob it poses as. */
    public double getPaceFactor() {
        if (revealed || disguise == null) {
            return 1.0D;
        }
        IAttributeInstance theirs = disguise.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        double ours = getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getBaseValue();
        if (theirs == null || ours <= 0.0D) {
            return 1.0D;
        }
        return MathHelper.clamp(theirs.getBaseValue() / ours, 0.4D, 1.6D);
    }

    /** Takes a new disguise; {@code current} is excluded so a change of shape is visible. */
    private boolean assumeDisguise(@Nullable ResourceLocation current) {
        BlockPos pos = new BlockPos(this);
        ResourceLocation id = Disguises.pick(world, pos, current, rand);
        if (id == null) {
            if (current != null) {
                return false;
            }
            id = new ResourceLocation("minecraft", "cow");
        }
        EntityLiving mob = Appearance.createMob(world, id, null);
        if (mob == null) {
            return false;
        }
        mob.setLocationAndAngles(posX, posY, posZ, rotationYaw, 0.0F);
        // Rolls the mob's natural variety: fleece colour, coat pattern and so on.
        mob.onInitialSpawn(world.getDifficultyForLocation(pos), null);
        // Horses and llamas spawn as foals one time in five; the stand-in never grows up, so pose as an adult.
        if (mob instanceof EntityAgeable) {
            ((EntityAgeable) mob).setGrowingAge(0);
        }
        setAppearance(Appearance.ofMob(mob));
        return true;
    }

    @Nullable
    @Override
    public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, @Nullable IEntityLivingData data) {
        data = super.onInitialSpawn(difficulty, data);
        if (Appearance.isEmpty(dataManager.get(APPEARANCE))) {
            assumeDisguise(null);
        }
        return data;
    }

    // ---- Behaviour ----

    @Override
    public void onLivingUpdate() {
        if (!world.isRemote) {
            if (Appearance.isEmpty(dataManager.get(APPEARANCE))) {
                assumeDisguise(null);
            }
            // Stalkers keep their distance, so being far from players must not count as idling.
            if (getAttackTarget() != null || stalked != null) {
                idleTime = 0;
            }
            if (revealed) {
                updateRevealed();
            } else {
                updateDisguised();
            }
        }
        super.onLivingUpdate();
    }

    private void updateDisguised() {
        updateGaze();
        if (revealed) {
            return;
        }
        if (shiftCooldown > 0) {
            shiftCooldown--;
        } else if (rand.nextInt(SHIFT_CHANCE) == 0) {
            if (assumeDisguise(Appearance.mobId(dataManager.get(APPEARANCE)))) {
                shiftEffects();
            }
            shiftCooldown = shiftCooldownTicks();
        }
    }

    private void updateRevealed() {
        updateStuck();
        EntityLivingBase target = getAttackTarget();
        if (target != null && target.isEntityAlive()) {
            targetlessTicks = 0;
            return;
        }
        int redisguiseTicks = SkinwalkersConfig.behaviour.redisguiseSeconds * 20;
        if (redisguiseTicks > 0 && ++targetlessTicks >= redisguiseTicks && assumeDisguise(null)) {
            setAttackTarget(null);
            shiftEffects();
            targetlessTicks = 0;
            shiftCooldown = shiftCooldownTicks();
        }
    }

    /**
     * Reveals itself to a player who keeps the crosshair on it, through glass included. A slipping aim doesn't start
     * the count over: a brief slip pauses it, and a longer look away drains it at the rate it built up.
     */
    private void updateGaze() {
        EntityPlayer watcher = null;
        for (EntityPlayer player : world.playerEntities) {
            if (!player.isSpectator() && player.isEntityAlive() && getDistanceSq(player) <= GAZE_RANGE * GAZE_RANGE
                    && isWatchedBy(player)) {
                watcher = player;
                break;
            }
        }
        if (watcher == null) {
            if (++unwatchedTicks > GAZE_SLIP_TICKS && gazeTicks > 0) {
                gazeTicks--;
            }
            return;
        }
        unwatchedTicks = 0;
        if (++gazeTicks >= Math.max(1, Math.round(SkinwalkersConfig.behaviour.gazeSeconds * 20))) {
            reveal(watcher);
        }
    }

    /** Whether the player's crosshair is on any part of it, with nothing but air or glass between them. */
    public boolean isWatchedBy(EntityPlayer player) {
        Vec3d eye = player.getPositionEyes(1.0F);
        AxisAlignedBB box = getEntityBoundingBox();
        if (!Sight.isAimedAt(eye, player.getLook(1.0F), Sight.lookTarget(box))) {
            return false;
        }
        Vec3d centre = new Vec3d(posX, (box.minY + box.maxY) / 2, posZ);
        return Sight.canSee(world, eye, Sight.eyes(this)) || Sight.canSee(world, eye, centre);
    }

    /** Screams, vanishes into smoke and comes out wearing the player's skin, already going for them. */
    public void reveal(EntityPlayer player) {
        if (world.isRemote || revealed || !isEntityAlive()) {
            return;
        }
        setAppearance(Appearance.ofPlayer(player));
        // Loud enough to carry across the stalking distance.
        playSound(ModRegistry.SCREAM, 4.0F, 0.9F + rand.nextFloat() * 0.2F);
        playSound(ModRegistry.SCREAM_LAYER, 3.0F, 0.9F + rand.nextFloat() * 0.2F);
        world.setEntityState(this, REVEAL_EFFECTS);
        gazeTicks = 0;
        targetlessTicks = 0;
        setStalked(null);
        if (!EntitySelectors.CAN_AI_TARGET.apply(player)) {
            return;
        }
        // No pause after the scream: it strikes if the player is within reach, and is running at them otherwise.
        setAttackTarget(player);
        if (getDistanceSq(player) <= REVEAL_STRIKE_RANGE_SQ && canEntityBeSeen(player)) {
            swingArm(EnumHand.MAIN_HAND);
            attackEntityAsMob(player);
            if (pursue != null) {
                pursue.delayNextAttack();
            }
        }
        getNavigator().tryMoveToEntityLiving(player, PURSUIT_SPEED);
    }

    private void shiftEffects() {
        playSound(ModRegistry.SHIFT, 0.8F, 0.9F + rand.nextFloat() * 0.3F);
        playSound(ModRegistry.SHIFT_LAYER, 0.6F, 0.9F + rand.nextFloat() * 0.3F);
        world.setEntityState(this, SHIFT_EFFECTS);
    }

    private static int shiftCooldownTicks() {
        return SkinwalkersConfig.behaviour.shapeshiftCooldownSeconds * 20;
    }

    /** The player being stalked, which keeps a distant skinwalker from despawning. */
    public void setStalked(@Nullable EntityPlayer player) {
        stalked = player;
        dataManager.set(STALKED, player == null ? -1 : player.getEntityId());
    }

    /** Entity ID of the player it is stalking, on either side; -1 if none. */
    public int getStalkedId() {
        return dataManager.get(STALKED);
    }

    /** Counts samples in which a hunting skinwalker barely moved while still out of reach of its target. */
    private void updateStuck() {
        if (ticksExisted % STUCK_SAMPLE_TICKS != 0) {
            return;
        }
        Vec3d here = getPositionVector();
        EntityLivingBase target = getAttackTarget();
        boolean still = stuckSample != null
                && Math.abs(here.x - stuckSample.x) + Math.abs(here.z - stuckSample.z) < 0.4D
                && Math.abs(here.y - stuckSample.y) < 0.6D;
        stuckSample = here;
        if (still && target != null && getDistanceSq(target) > 6.25D) {
            stuckSamples++;
        } else {
            stuckSamples = 0;
        }
    }

    public boolean isStuck() {
        return stuckSamples >= STUCK_SAMPLES;
    }

    public void clearStuck() {
        stuckSamples = 0;
        stuckSample = null;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (!super.attackEntityFrom(source, amount)) {
            return false;
        }
        Entity attacker = source.getTrueSource();
        if (!revealed && attacker instanceof EntityPlayer && !((EntityPlayer) attacker).isSpectator()) {
            reveal((EntityPlayer) attacker);
        }
        return true;
    }

    /** Sees through glass and glass panes. */
    @Override
    public boolean canEntityBeSeen(Entity entity) {
        return Sight.canSee(world, this, entity);
    }

    @Override
    public boolean getCanSpawnHere() {
        return isOnSurface(world, new BlockPos(posX, getEntityBoundingBox().minY, posZ)) && super.getCanSpawnHere();
    }

    /** Open to the sky, ignoring leaves: forests count, caves and overhangs do not. */
    public static boolean isOnSurface(World world, BlockPos pos) {
        return pos.getY() >= world.getPrecipitationHeight(pos).getY();
    }

    @Override
    protected ResourceLocation getLootTable() {
        return LOOT_TABLE;
    }

    // ---- Sounds: the disguise's while disguised, a player's once revealed ----

    @Override
    public SoundCategory getSoundCategory() {
        return revealed ? SoundCategory.HOSTILE : SoundCategory.NEUTRAL;
    }

    @Override
    public int getTalkInterval() {
        return !revealed && disguise instanceof EntityLiving ? ((EntityLiving) disguise).getTalkInterval() : super.getTalkInterval();
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return revealed || disguise == null ? null : DisguiseSounds.ambient(disguise);
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        if (revealed) {
            return SoundEvents.ENTITY_PLAYER_HURT;
        }
        return disguise == null ? SoundEvents.ENTITY_GENERIC_HURT : DisguiseSounds.hurt(disguise, source);
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        if (revealed) {
            return SoundEvents.ENTITY_PLAYER_DEATH;
        }
        return disguise == null ? SoundEvents.ENTITY_GENERIC_DEATH : DisguiseSounds.death(disguise);
    }

    @Override
    protected float getSoundVolume() {
        return revealed || disguise == null ? 1.0F : DisguiseSounds.volume(disguise);
    }

    @Override
    protected void playStepSound(BlockPos pos, Block block) {
        if (revealed || disguise == null || isSilent() || !DisguiseSounds.step(disguise, this, pos, block)) {
            super.playStepSound(pos, block);
        }
    }

    @Override
    protected SoundEvent getSwimSound() {
        return revealed ? SoundEvents.ENTITY_PLAYER_SWIM : SoundEvents.ENTITY_GENERIC_SWIM;
    }

    @Override
    protected SoundEvent getSplashSound() {
        return revealed ? SoundEvents.ENTITY_PLAYER_SPLASH : SoundEvents.ENTITY_GENERIC_SPLASH;
    }

    @Override
    protected SoundEvent getFallSound(int height) {
        if (revealed) {
            return height > 4 ? SoundEvents.ENTITY_PLAYER_BIG_FALL : SoundEvents.ENTITY_PLAYER_SMALL_FALL;
        }
        return height > 4 ? SoundEvents.ENTITY_GENERIC_BIG_FALL : SoundEvents.ENTITY_GENERIC_SMALL_FALL;
    }

    // ---- Client effects ----

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (world.isRemote) {
            Skinwalkers.proxy.tickDouble(this);
            if (smokeTicks > 0) {
                smokeTicks--;
                smoke(3);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void handleStatusUpdate(byte id) {
        if (id == REVEAL_EFFECTS) {
            smokeTicks = 40;
            smoke(40);
        } else if (id == SHIFT_EFFECTS) {
            smokeTicks = 6;
            smoke(16);
        } else {
            super.handleStatusUpdate(id);
        }
    }

    private void smoke(int count) {
        for (int i = 0; i < count; i++) {
            EnumParticleTypes type = i % 4 == 0 ? EnumParticleTypes.EXPLOSION_NORMAL : EnumParticleTypes.SMOKE_LARGE;
            world.spawnParticle(type,
                    posX + (rand.nextDouble() - 0.5D) * width * 2.5D,
                    posY + rand.nextDouble() * height * 1.2D,
                    posZ + (rand.nextDouble() - 0.5D) * width * 2.5D,
                    rand.nextGaussian() * 0.04D, 0.02D + rand.nextDouble() * 0.06D, rand.nextGaussian() * 0.04D);
        }
    }

    // ---- Saving ----

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setTag("Appearance", dataManager.get(APPEARANCE));
        compound.setInteger("ShapeshiftCooldown", shiftCooldown);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        if (compound.hasKey("Appearance", 10)) {
            setAppearance(compound.getCompoundTag("Appearance"));
        }
        if (compound.hasKey("ShapeshiftCooldown", 99)) {
            shiftCooldown = compound.getInteger("ShapeshiftCooldown");
        }
    }
}
