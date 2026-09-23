package net.irisshaders.iris.mixin;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.compat.sodium.SodiumWorldKick;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Small hook giving Iris a chance to check for keyboard input for its keybindings.
 *
 * <p>This is equivalent to the END_CLIENT_TICK event in Fabric API, but since it's a super simple mixin and we
 * only need this event (out of the many events provided by Fabric API) I've just implemented it myself. This
 * alone shaves over 60kB off the released JAR size.</p>
 */
@Mixin(Minecraft.class)
public class MixinMinecraft_Keybinds {
	@Inject(method = "tick()V", at = @At("RETURN"))
	private void iris$onTick(CallbackInfo ci) {
		Profiler.get().push("iris_keybinds");

		// Process pending kicks before deferred pack load / pipeline rebuild.
		// handleKeybinds may destroy+prepare and arm(2); if onClientTick ran
		// after that, the countdown could hit 0 same tick and allChanged ran
		// before beginLevelRendering set block-ID maps.
		SodiumWorldKick.onClientTick((Minecraft) (Object) this);
		Iris.handleKeybinds((Minecraft) (Object) this);

		Profiler.get().pop();
	}
}
