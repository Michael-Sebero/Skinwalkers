package com.michaelsebero.skinwalkers.client;

import com.michaelsebero.skinwalkers.CommonProxy;
import com.michaelsebero.skinwalkers.entity.EntitySkinwalker;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit() {
        RenderingRegistry.registerEntityRenderingHandler(EntitySkinwalker.class, RenderSkinwalker::new);
    }

    @Override
    public EntityLivingBase createPlayerDouble(World world, GameProfile profile, byte modelParts, boolean leftHanded) {
        return new PlayerDouble(world, profile, modelParts, leftHanded);
    }

    /** The cape swing and walking bob that EntityPlayer updates each tick. */
    @Override
    public void tickDouble(EntitySkinwalker skinwalker) {
        if (!(skinwalker.getDisguise() instanceof PlayerDouble)) {
            return;
        }
        PlayerDouble body = (PlayerDouble) skinwalker.getDisguise();
        body.posX = skinwalker.posX;
        body.posY = skinwalker.posY;
        body.posZ = skinwalker.posZ;
        body.updateCape();

        double dx = skinwalker.posX - skinwalker.prevPosX;
        double dz = skinwalker.posZ - skinwalker.prevPosZ;
        float bob = skinwalker.onGround && skinwalker.getHealth() > 0.0F
                ? Math.min(0.1F, MathHelper.sqrt(dx * dx + dz * dz))
                : 0.0F;
        body.prevCameraYaw = body.cameraYaw;
        body.cameraYaw += (bob - body.cameraYaw) * 0.4F;
    }
}
