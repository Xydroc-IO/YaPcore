package net.irisshaders.iris.compat.sodium.mixin;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SodiumWorldRenderer.class)
public interface SodiumWorldRendererAccessor {
	@Accessor(value = "level", remap = false)
	ClientLevel iris$getLevel();

	@Accessor(value = "lastFogParameters", remap = false)
	void iris$setLastFogParameters(FogParameters parameters);

	@Accessor(value = "renderDistance", remap = false)
	void iris$setRenderDistance(int renderDistance);
}
