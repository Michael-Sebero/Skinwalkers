package com.michaelsebero.skinwalkers.client;

import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The copy of a player a revealed skinwalker is drawn as. Never added to the world: the renderer poses it like the
 * skinwalker each frame. Its skin comes from the player list while the player is online, and otherwise from the
 * textures in the profile the skinwalker saved, the way player heads get theirs.
 */
@SideOnly(Side.CLIENT)
public class PlayerDouble extends EntityOtherPlayerMP {

    @Nullable
    private Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures;
    @Nullable
    private ResourceLocation skin;
    @Nullable
    private ResourceLocation cape;

    public PlayerDouble(World world, GameProfile profile, byte modelParts, boolean leftHanded) {
        super(world, profile);
        dataManager.set(PLAYER_MODEL_FLAG, modelParts);
        dataManager.set(MAIN_HAND, (byte) (leftHanded ? 0 : 1));
    }

    /** Copies EntityPlayer's private cape physics, which the renderer reads, for a double that is never ticked. */
    void updateCape() {
        prevChasingPosX = chasingPosX;
        prevChasingPosY = chasingPosY;
        prevChasingPosZ = chasingPosZ;
        double dx = posX - chasingPosX;
        double dy = posY - chasingPosY;
        double dz = posZ - chasingPosZ;
        if (Math.abs(dx) > 10.0D) {
            chasingPosX = prevChasingPosX = posX;
        }
        if (Math.abs(dy) > 10.0D) {
            chasingPosY = prevChasingPosY = posY;
        }
        if (Math.abs(dz) > 10.0D) {
            chasingPosZ = prevChasingPosZ = posZ;
        }
        chasingPosX += dx * 0.25D;
        chasingPosY += dy * 0.25D;
        chasingPosZ += dz * 0.25D;
    }

    // It looks like the player it copied, whatever game mode that player is in now.
    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    @Override
    public boolean hasPlayerInfo() {
        return super.hasPlayerInfo() || !textures().isEmpty();
    }

    @Override
    public ResourceLocation getLocationSkin() {
        if (getPlayerInfo() != null) {
            return super.getLocationSkin();
        }
        MinecraftProfileTexture texture = textures().get(MinecraftProfileTexture.Type.SKIN);
        if (texture == null) {
            return DefaultPlayerSkin.getDefaultSkin(getUniqueID());
        }
        if (skin == null) {
            skin = Minecraft.getMinecraft().getSkinManager().loadSkin(texture, MinecraftProfileTexture.Type.SKIN);
        }
        return skin;
    }

    @Override
    public String getSkinType() {
        if (getPlayerInfo() != null) {
            return super.getSkinType();
        }
        MinecraftProfileTexture texture = textures().get(MinecraftProfileTexture.Type.SKIN);
        if (texture == null) {
            return DefaultPlayerSkin.getSkinType(getUniqueID());
        }
        String model = texture.getMetadata("model");
        return model == null ? "default" : model;
    }

    @Nullable
    @Override
    public ResourceLocation getLocationCape() {
        NetworkPlayerInfo info = getPlayerInfo();
        if (info != null) {
            return info.getLocationCape();
        }
        MinecraftProfileTexture texture = textures().get(MinecraftProfileTexture.Type.CAPE);
        if (texture != null && cape == null) {
            cape = Minecraft.getMinecraft().getSkinManager().loadSkin(texture, MinecraftProfileTexture.Type.CAPE);
        }
        return cape;
    }

    private Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures() {
        if (textures == null) {
            textures = Minecraft.getMinecraft().getSkinManager().loadSkinFromCache(getGameProfile());
        }
        return textures;
    }
}
