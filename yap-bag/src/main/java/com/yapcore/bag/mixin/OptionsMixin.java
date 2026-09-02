package com.yapcore.bag.mixin;

import com.yapcore.bag.YapBagClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.Arrays;

@Mixin(Options.class)
public abstract class OptionsMixin {

    @Shadow
    @Final
    @Mutable
    public KeyMapping[] keyMappings;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void yap$registerBagKey(Minecraft minecraft, File file, CallbackInfo ci) {
        KeyMapping extra = YapBagClient.openKey();
        KeyMapping[] next = Arrays.copyOf(this.keyMappings, this.keyMappings.length + 1);
        next[this.keyMappings.length] = extra;
        this.keyMappings = next;
    }
}
