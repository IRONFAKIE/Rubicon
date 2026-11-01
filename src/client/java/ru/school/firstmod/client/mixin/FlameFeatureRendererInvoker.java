package ru.school.firstmod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.feature.FlameFeatureRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Доступ к приватному методу отрисовки одного языка пламени. */
@Mixin(FlameFeatureRenderer.class)
public interface FlameFeatureRendererInvoker {
	@Invoker("prepare")
	void firstmod$prepare(
			FlameFeatureRenderer.Submit submit,
			VertexConsumer buffer,
			TextureAtlasSprite fire0,
			TextureAtlasSprite fire1
	);
}
