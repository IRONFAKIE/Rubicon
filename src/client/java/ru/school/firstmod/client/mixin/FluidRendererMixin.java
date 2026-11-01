package ru.school.firstmod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import ru.school.firstmod.SoulLava;
import ru.school.firstmod.client.SoulLavaModels;

/**
 * Отрисовывает «Лаву Душ» бирюзовыми спрайтами.
 *
 * <p>Позиция блока запоминается в {@link ThreadLocal}: рендер чанков
 * многопоточный, поэтому общее поле использовать нельзя.</p>
 */
@Mixin(FluidRenderer.class)
public abstract class FluidRendererMixin {
	@Unique
	private static final ThreadLocal<BlockPos> FIRST_MOD_FLUID_POS = new ThreadLocal<>();

	@Unique
	private static final ThreadLocal<BlockAndTintGetter> FIRST_MOD_FLUID_LEVEL = new ThreadLocal<>();

	@Inject(method = "tesselate", at = @At("HEAD"))
	private void firstmod$rememberFluidPos(
			BlockAndTintGetter level,
			BlockPos pos,
			FluidRenderer.Output output,
			BlockState blockState,
			FluidState fluidState,
			CallbackInfo callback
	) {
		FIRST_MOD_FLUID_POS.set(pos);
		FIRST_MOD_FLUID_LEVEL.set(level);
	}

	@Inject(method = "tesselate", at = @At("RETURN"))
	private void firstmod$forgetFluidPos(
			BlockAndTintGetter level,
			BlockPos pos,
			FluidRenderer.Output output,
			BlockState blockState,
			FluidState fluidState,
			CallbackInfo callback
	) {
		FIRST_MOD_FLUID_POS.remove();
		FIRST_MOD_FLUID_LEVEL.remove();
	}

	@Redirect(
			method = "tesselate",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/block/FluidStateModelSet;get(Lnet/minecraft/world/level/material/FluidState;)Lnet/minecraft/client/renderer/block/FluidModel;"
			)
	)
	private FluidModel firstmod$soulLavaModel(FluidStateModelSet models, FluidState fluidState) {
		FluidModel model = models.get(fluidState);

		BlockPos pos = FIRST_MOD_FLUID_POS.get();
		BlockAndTintGetter level = FIRST_MOD_FLUID_LEVEL.get();
		if (pos == null || level == null) {
			return model;
		}

		// Проверяем не только песок душ прямо под блоком, но и всю
		// цепочку течения вверх по потоку, чтобы все стадии сохраняли цвет.
		if (!SoulLava.isSoulLava(level, pos, fluidState)) {
			return model;
		}

		return SoulLavaModels.soulLava(model);
	}
}
