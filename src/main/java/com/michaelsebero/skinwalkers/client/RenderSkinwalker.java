package com.michaelsebero.skinwalkers.client;

import java.lang.reflect.Field;

import javax.annotation.Nullable;

import com.michaelsebero.skinwalkers.Skinwalkers;
import com.michaelsebero.skinwalkers.entity.EntitySkinwalker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Draws a skinwalker as its disguise using that mob's own vanilla renderer, so it looks exactly like the real thing,
 * or as the double of the player it copied.
 */
@SideOnly(Side.CLIENT)
public class RenderSkinwalker extends Render<EntitySkinwalker> {

    private static final float PLAYER_SHADOW = 0.5F;
    @Nullable
    private static final Field SHADOW_SIZE = findShadowSize();

    private final RenderPlayer wideArms;
    private final RenderPlayer slimArms;

    public RenderSkinwalker(RenderManager manager) {
        super(manager);
        wideArms = new RenderDouble(manager, false);
        slimArms = new RenderDouble(manager, true);
        shadowSize = PLAYER_SHADOW;
    }

    @Override
    public void doRender(EntitySkinwalker entity, double x, double y, double z, float entityYaw, float partialTicks) {
        EntityLivingBase body = entity.getDisguise();
        if (body == null) {
            return;
        }
        copyPose(entity, body);
        Entity viewer = Minecraft.getMinecraft().getRenderViewEntity();
        if (!entity.isRevealed() && viewer != null && viewer.getEntityId() == entity.getStalkedId()) {
            stareAtCamera(entity, body, viewer, partialTicks);
        }
        if (body instanceof PlayerDouble) {
            RenderPlayer render = "slim".equals(((PlayerDouble) body).getSkinType()) ? slimArms : wideArms;
            render.setRenderOutlines(renderOutlines);
            render.doRender((AbstractClientPlayer) body, x, y, z, entityYaw, partialTicks);
            shadowSize = PLAYER_SHADOW;
            return;
        }
        Render<Entity> render = renderManager.getEntityRenderObject(body);
        if (render != null) {
            render.setRenderOutlines(renderOutlines);
            render.doRender(body, x, y, z, entityYaw, partialTicks);
            shadowSize = shadowSizeOf(render);
        }
    }

    /** Everything vanilla renderers read to pose and animate a mob. */
    private static void copyPose(EntitySkinwalker from, EntityLivingBase to) {
        to.posX = from.posX;
        to.posY = from.posY;
        to.posZ = from.posZ;
        to.prevPosX = from.prevPosX;
        to.prevPosY = from.prevPosY;
        to.prevPosZ = from.prevPosZ;
        to.lastTickPosX = from.lastTickPosX;
        to.lastTickPosY = from.lastTickPosY;
        to.lastTickPosZ = from.lastTickPosZ;
        to.motionX = from.motionX;
        to.motionY = from.motionY;
        to.motionZ = from.motionZ;
        to.rotationYaw = from.rotationYaw;
        to.prevRotationYaw = from.prevRotationYaw;
        to.rotationPitch = from.rotationPitch;
        to.prevRotationPitch = from.prevRotationPitch;
        to.renderYawOffset = from.renderYawOffset;
        to.prevRenderYawOffset = from.prevRenderYawOffset;
        to.rotationYawHead = from.rotationYawHead;
        to.prevRotationYawHead = from.prevRotationYawHead;
        to.limbSwing = from.limbSwing;
        to.limbSwingAmount = from.limbSwingAmount;
        to.prevLimbSwingAmount = from.prevLimbSwingAmount;
        to.swingProgress = from.swingProgress;
        to.prevSwingProgress = from.prevSwingProgress;
        to.swingProgressInt = from.swingProgressInt;
        to.isSwingInProgress = from.isSwingInProgress;
        to.swingingHand = from.swingingHand;
        to.distanceWalkedModified = from.distanceWalkedModified;
        to.prevDistanceWalkedModified = from.prevDistanceWalkedModified;
        to.hurtTime = from.hurtTime;
        to.maxHurtTime = from.maxHurtTime;
        to.deathTime = from.deathTime;
        to.ticksExisted = from.ticksExisted;
        to.onGround = from.onGround;
        to.isAirBorne = from.isAirBorne;
        to.setInvisible(from.isInvisible());
    }

    /**
     * Points the head straight at the stalked player's eyes this frame, however the body is turned, so it is
     * looking right at them whenever they look at it.
     */
    private static void stareAtCamera(EntitySkinwalker entity, EntityLivingBase body, Entity viewer, float partialTicks) {
        double dx = lerp(viewer.prevPosX, viewer.posX, partialTicks) - lerp(entity.prevPosX, entity.posX, partialTicks);
        double dy = lerp(viewer.prevPosY, viewer.posY, partialTicks) + viewer.getEyeHeight()
                - (lerp(entity.prevPosY, entity.posY, partialTicks) + entity.getEyeHeight());
        double dz = lerp(viewer.prevPosZ, viewer.posZ, partialTicks) - lerp(entity.prevPosZ, entity.posZ, partialTicks);
        float yaw = (float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) -(MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0D / Math.PI));
        // Keep the head yaw within a turn of the body's so the renderer's interpolation doesn't spin it the long way.
        float bodyYaw = body.prevRenderYawOffset + MathHelper.wrapDegrees(body.renderYawOffset - body.prevRenderYawOffset) * partialTicks;
        yaw = bodyYaw + MathHelper.wrapDegrees(yaw - bodyYaw);
        body.rotationYawHead = body.prevRotationYawHead = yaw;
        body.rotationPitch = body.prevRotationPitch = pitch;
    }

    private static double lerp(double from, double to, float partialTicks) {
        return from + (to - from) * partialTicks;
    }

    private static float shadowSizeOf(Render<?> render) {
        if (SHADOW_SIZE != null) {
            try {
                return SHADOW_SIZE.getFloat(render);
            } catch (IllegalAccessException ignored) {
                // Fall through to the default.
            }
        }
        return PLAYER_SHADOW;
    }

    @Nullable
    private static Field findShadowSize() {
        for (String name : new String[] {"field_76989_e", "shadowSize"}) {
            try {
                Field field = Render.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Try the development name.
            }
        }
        Skinwalkers.LOG.warn("Render.shadowSize not found; disguised skinwalkers will cast player-sized shadows");
        return null;
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntitySkinwalker entity) {
        // Never bound: the disguise's own renderer binds its textures.
        return null;
    }

    /** A player renderer without the name tag, which would give the double away. */
    private static final class RenderDouble extends RenderPlayer {

        RenderDouble(RenderManager manager, boolean slimArms) {
            super(manager, slimArms);
        }

        @Override
        protected boolean canRenderName(AbstractClientPlayer entity) {
            return false;
        }
    }
}
