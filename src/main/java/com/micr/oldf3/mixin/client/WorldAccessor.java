package com.micr.oldf3.mixin.client;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(value=EnvType.CLIENT)
@Mixin(value={World.class})
public interface WorldAccessor {
    @Accessor(value="blockEntityTickers")
    public List<?> getBlockEntityTickers();
}

