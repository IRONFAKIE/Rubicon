package ru.school.firstmod.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VexRenderer;
import net.minecraft.client.renderer.entity.state.VexRenderState;
import net.minecraft.resources.Identifier;

import ru.school.firstmod.FirstMod;

/**
 * Рендерер блуждающей души: геометрия векса с голубой текстурой.
 */
public class WanderingSoulRenderer extends VexRenderer {
	private static final Identifier TEXTURE = FirstMod.id("textures/entity/wandering_soul.png");

	public WanderingSoulRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public Identifier getTextureLocation(VexRenderState state) {
		return TEXTURE;
	}
}
