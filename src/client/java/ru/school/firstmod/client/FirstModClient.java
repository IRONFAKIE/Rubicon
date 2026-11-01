package ru.school.firstmod.client;

import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import ru.school.firstmod.FirstMod;

public class FirstModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(FirstMod.SOUL_STRIDER_SPIRIT, SoulStriderSpiritRenderer::new);
		EntityRendererRegistry.register(FirstMod.WANDERING_SOUL, WanderingSoulRenderer::new);

		// Цвет установленной литейной формы.
		BlockColorRegistry.register(List.of(new CastingMoldTint()), FirstMod.CASTING_MOLD);

		// Шкала «Душевного переполнения» над опытом.
		//
		// Важно: EXPERIENCE_LEVEL вызывается только при уровне опыта > 0,
		// поэтому привязка к нему прятала шкалу у игроков без опыта.
		// INFO_BAR отрисовывается всегда, пока виден HUD.
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.INFO_BAR,
				FirstMod.id("soul_overflow"),
				new SoulOverflowHud()
		);
	}
}
