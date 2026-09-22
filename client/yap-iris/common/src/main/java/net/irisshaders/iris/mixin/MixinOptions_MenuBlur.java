package net.irisshaders.iris.mixin;

import net.irisshaders.iris.Iris;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Menu background blur runs a fullscreen post pass over the last world frame.
 * With an Iris pack that frame is already shader-composited, and the blur can
 * stall the main thread long enough for a multiplayer keepalive kick when
 * opening Video Options / pause menus in-world.
 */
@Mixin(Options.class)
public class MixinOptions_MenuBlur {
	@Inject(method = "getMenuBackgroundBlurriness", at = @At("HEAD"), cancellable = true)
	private void iris$skipMenuBlurWithShaders(CallbackInfoReturnable<Integer> cir) {
		if (Iris.isPackInUseQuick()) {
			cir.setReturnValue(0);
		}
	}
}
