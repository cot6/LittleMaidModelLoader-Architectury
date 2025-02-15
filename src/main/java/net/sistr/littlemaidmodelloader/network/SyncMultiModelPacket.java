package net.sistr.littlemaidmodelloader.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.sistr.littlemaidmodelloader.LittleMaidModelLoader;
import net.sistr.littlemaidmodelloader.entity.compound.IHasMultiModel;
import net.sistr.littlemaidmodelloader.entity.compound.IHasMultiModel.Layer;
import net.sistr.littlemaidmodelloader.entity.compound.IHasMultiModel.Part;
import net.sistr.littlemaidmodelloader.resource.manager.LMTextureManager;
import net.sistr.littlemaidmodelloader.resource.util.ArmorSets;
import net.sistr.littlemaidmodelloader.resource.util.TextureColors;

public class SyncMultiModelPacket {
    public static final Identifier ID =
            new Identifier(LittleMaidModelLoader.MODID, "sync_multi_model");

    @Environment(EnvType.CLIENT)
    public static void sendC2SPacket(Entity entity, IHasMultiModel hasMultiModel) {
        PacketByteBuf passedData = createC2SPacket(entity, hasMultiModel);
        ClientPlayNetworking.send(ID, passedData);
    }

    public static PacketByteBuf createC2SPacket(Entity entity, IHasMultiModel hasMultiModel) {
        PacketByteBuf passedData = new PacketByteBuf(Unpooled.buffer());
        passedData.writeInt(entity.getEntityId());
        passedData.writeString(hasMultiModel.getTextureHolder(Layer.SKIN, Part.HEAD)
                .getTextureName());
        for (Part part : Part.values()) {
            passedData.writeString(hasMultiModel.getTextureHolder(Layer.INNER, part).getTextureName());
        }
        passedData.writeEnumConstant(hasMultiModel.getColor());
        passedData.writeBoolean(hasMultiModel.isContract());
        return passedData;
    }

    public static void sendS2CPacket(Entity entity, IHasMultiModel hasMultiModel) {
        PacketByteBuf passedData = createS2CPacket(entity, hasMultiModel);
        PlayerLookup.tracking(entity).forEach(watchingPlayer ->
                ServerPlayNetworking.send(watchingPlayer, ID, passedData));
    }

    public static PacketByteBuf createS2CPacket(Entity entity, IHasMultiModel hasMultiModel) {
        PacketByteBuf passedData = new PacketByteBuf(Unpooled.buffer());
        passedData.writeInt(entity.getEntityId());
        passedData.writeString(hasMultiModel.getTextureHolder(Layer.SKIN, Part.HEAD).getTextureName());
        for (Part part : Part.values()) {
            passedData.writeString(hasMultiModel.getTextureHolder(Layer.INNER, part).getTextureName());
        }
        passedData.writeEnumConstant(hasMultiModel.getColor());
        passedData.writeBoolean(hasMultiModel.isContract());
        return passedData;
    }

    @Environment(EnvType.CLIENT)
    public static void receiveS2CPacket(MinecraftClient client, ClientPlayNetworkHandler handler,
                                        PacketByteBuf buf, PacketSender responseSender) {
        int entityId = buf.readInt();
        String textureName = buf.readString();
        ArmorSets<String> armorTextureName = new ArmorSets<>();
        for (Part part : Part.values()) {
            armorTextureName.setArmor(buf.readString(), part);
        }
        TextureColors color = buf.readEnumConstant(TextureColors.class);
        boolean isContract = buf.readBoolean();
        client.execute(() ->
                applyMultiModelClient(entityId, isContract, color, textureName, armorTextureName));
    }

    //context.getTaskQueue().execute()の中では@Environmentの効力が及ばないため別メソッドに分離
    @Environment(EnvType.CLIENT)
    public static void applyMultiModelClient(int entityId, boolean isContract, TextureColors color,
                                             String textureName, ArmorSets<String> armorTextureName) {
        World world = MinecraftClient.getInstance().world;
        if (world == null) return;
        Entity entity = world.getEntityById(entityId);
        if (!(entity instanceof IHasMultiModel)) return;
        IHasMultiModel multiModel = (IHasMultiModel) entity;
        multiModel.setContract(isContract);
        multiModel.setColor(color);
        LMTextureManager textureManager = LMTextureManager.INSTANCE;
        textureManager.getTexture(textureName).filter(textureHolder ->
                multiModel.isAllowChangeTexture(entity, textureHolder, Layer.SKIN, Part.HEAD))
                .ifPresent(textureHolder -> multiModel.setTextureHolder(textureHolder, Layer.SKIN, Part.HEAD));
        for (Part part : Part.values()) {
            String armorName = armorTextureName.getArmor(part)
                    .orElseThrow(() -> new IllegalStateException("テクスチャが存在しません。"));
            textureManager.getTexture(armorName).filter(textureHolder ->
                    multiModel.isAllowChangeTexture(entity, textureHolder, Layer.INNER, part))
                    .ifPresent(textureHolder -> multiModel.setTextureHolder(textureHolder, Layer.INNER, part));
        }
    }

    public static void receiveC2SPacket(MinecraftServer server, ServerPlayerEntity player,
                                        ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        int entityId = buf.readInt();
        String textureName = buf.readString(32767);
        ArmorSets<String> armorTextureName = new ArmorSets<>();
        for (Part part : Part.values()) {
            armorTextureName.setArmor(buf.readString(32767), part);
        }
        TextureColors color = buf.readEnumConstant(TextureColors.class);
        boolean isContract = buf.readBoolean();
        server.execute(() ->
                applyMultiModelServer(player, entityId, isContract, color, textureName, armorTextureName));
    }

    //クライアントに倣って分離
    public static void applyMultiModelServer(PlayerEntity player, int entityId, boolean isContract, TextureColors color,
                                             String textureName, ArmorSets<String> armorTextureName) {
        Entity entity = player.world.getEntityById(entityId);
        if (!(entity instanceof IHasMultiModel)) return;
        IHasMultiModel multiModel = (IHasMultiModel) entity;
        multiModel.setContract(isContract);
        multiModel.setColor(color);
        LMTextureManager textureManager = LMTextureManager.INSTANCE;
        textureManager.getTexture(textureName).filter(textureHolder ->
                multiModel.isAllowChangeTexture(entity, textureHolder, Layer.SKIN, Part.HEAD))
                .ifPresent(textureHolder -> multiModel.setTextureHolder(textureHolder, Layer.SKIN, Part.HEAD));
        for (Part part : Part.values()) {
            String armorName = armorTextureName.getArmor(part)
                    .orElseThrow(() -> new IllegalStateException("テクスチャが存在しません。"));
            textureManager.getTexture(armorName).filter(textureHolder ->
                    multiModel.isAllowChangeTexture(entity, textureHolder, Layer.INNER, part))
                    .ifPresent(textureHolder -> multiModel.setTextureHolder(textureHolder, Layer.INNER, part));
        }
        sendS2CPacket(entity, multiModel);
    }

}
