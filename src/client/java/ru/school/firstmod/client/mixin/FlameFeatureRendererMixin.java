package ru.school.firstmod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.feature.FlameFeatureRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;

import ru.school.firstmod.client.SoulFireRenderState;

/**
 * Рисует синее пламя душ вместо обычного огня.
 *
 * <p>Ванильный рендерер пламени берёт спрайты один раз на группу. Мы
 * подменяем их на {@code block/soul_fire_0} и {@code block/soul_fire_1}
 * только для тех сущностей, у которых выставлен флаг огня душ.</p>
 */
@Mixin(FlameFeatureRenderer.class)
public abstract class FlameFeatureRendererMixin {
	@Unique
	private static final SpriteId FIRST_MOD_SOUL_FIRE_0 = new SpriteId(
			TextureAtlas.LOCATION_BLOCKS,
			Identifier.withDefaultNamespace("block/soul_fire_0")
	);

	@Unique
	private static final SpriteId FIRST_MOD_SOUL_FIRE_1 = new SpriteId(
			TextureAtlas.LOCATION_BLOCKS,
			Identifier.withDefaultNamespace("block/soul_fire_1")
	);

	@Redirect(
			method = "buildGroup",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/feature/FlameFeatureRenderer;prepare(Lnet/minecraft/client/renderer/feature/FlameFeatureRenderer$Submit;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"
			)
	)
	private void firstmod$soulFirePrepare(
			FlameFeatureRenderer renderer,
			FlameFeatureRenderer.Submit submit,
			VertexConsumer buffer,
			TextureAtlasSprite fire0,
			TextureAtlasSprite fire1
	) {
		if (submit.entityRenderState() instanceof SoulFireRenderState soulState
				&& soulState.firstmod$isSoulFire()) {
			AtlasManager atlas = Minecraft.getInstance().getAtlasManager();
			fire0 = atlas.get(FIRST_MOD_SOUL_FIRE_0);
			fire1 = atlas.get(FIRST_MOD_SOUL_FIRE_1);
		}

		((FlameFeatureRendererInvoker) renderer).firstmod$prepare(submit, buffer, fire0, fire1);
	}
}
