package net.irisshaders.iris.compat.sodium.mixin;

import net.caffeinemc.mods.sodium.client.config.structure.Config;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.compat.sodium.SodiumWorldKick;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium rebuilds every option page on Video Settings open. With Iris shaders
 * active in-world that full rebuild stacks on the same frame as menu open and
 * freezes the client. Defer the binding reset + first rebuild by one tick so
 * the screen can appear without a multi-second hitch.
 */
@Mixin(VideoSettingsScreen.class)
public abstract class MixinSodiumVideoSettingsScreen {
	@Unique
	private boolean iris$deferHeavyOpen;
	@Unique
	private boolean iris$openedDeferred;

	@Inject(method = "<init>(Lnet/minecraft/client/gui/screens/Screen;Lnet/caffeinemc/mods/sodium/client/config/structure/OptionPage;)V", at = @At("TAIL"))
	private void iris$markDefer(CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		iris$deferHeavyOpen = Iris.isPackInUseQuick() && mc != null && mc.level != null;
	}

	@Redirect(
		method = "<init>(Lnet/minecraft/client/gui/screens/Screen;Lnet/caffeinemc/mods/sodium/client/config/structure/OptionPage;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/caffeinemc/mods/sodium/client/config/structure/Config;resetAllOptionsFromBindings()V"
		)
	)
	private void iris$deferBindingReset(Config config) {
		if (Iris.isPackInUseQuick() && Minecraft.getInstance().level != null) {
			// Binding reset runs with the deferred first rebuild (see iris$deferFirstRebuild).
			return;
		}
		config.resetAllOptionsFromBindings();
	}

	@Inject(method = "rebuild", at = @At("HEAD"), cancellable = true)
	private void iris$deferFirstRebuild(CallbackInfo ci) {
		if (!iris$deferHeavyOpen || iris$openedDeferred) {
			return;
		}
		iris$openedDeferred = true;
		ci.cancel();
		VideoSettingsScreen self = (VideoSettingsScreen) (Object) this;
		Minecraft.getInstance().execute(() -> {
			if (Minecraft.getInstance().gui.screen() != self) {
				return;
			}
			// kickNow = reset + invalidate + allChanged (Video Settings ctor+init
			// plus mesh rebuild). init() after that rebuilds the UI only — stock
			// init also invalidate()s again, which is harmless.
			SodiumWorldKick.kickNow(Minecraft.getInstance());
			self.init(self.width, self.height);
		});
	}
}
