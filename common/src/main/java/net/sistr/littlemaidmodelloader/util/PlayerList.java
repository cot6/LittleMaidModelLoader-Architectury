package net.sistr.littlemaidmodelloader.util;

import me.shedaniel.architectury.annotations.ExpectPlatform;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Collection;

public class PlayerList {

    @ExpectPlatform
    public static Collection<ServerPlayerEntity> tracking(Entity entity) {
        throw new AssertionError();
    }

}
