package com.micr.oldf3.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.DebugOptionsScreen;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Environment(value=EnvType.CLIENT)
@Mixin(value={Keyboard.class})
public class KeyboardMixin {
    @Redirect(method={"processF3"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V"))
    private void oldF3_blockDebugOptionsScreen(MinecraftClient client, Screen screen) {
        if (screen instanceof DebugOptionsScreen) {
            return;
        }
        client.setScreen(screen);
    }
}
