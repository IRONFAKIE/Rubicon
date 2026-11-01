package ru.school.firstmod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

import ru.school.firstmod.FirstMod;
import ru.school.firstmod.client.SoulFireRenderState;

/**
 * Переносит синхронизированное состояние «огонь душ» в состояние отрисовки.
 *
 * <p>Пока серверное время не вышло, сущность принудительно рисуется
 * объятой пламенем — даже если она невосприимчива к огню.</p>
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void firstmod$extractSoulFire(
			Entity entity,
			EntityRenderState state,
			float partialTick,
			CallbackInfo callback
	) {
		if (!(state instanceof SoulFireRenderState soulState)) {
			return;
		}

		long until = ((AttachmentTarget) entity).getAttachedOrElse(FirstMod.SOUL_FIRE_UNTIL, 0L);
		boolean active = until > entity.level().getGameTime();

		soulState.firstmod$setSoulFire(active);

		if (active) {
			state.displayFireAnimation = true;
		}
	}
}
