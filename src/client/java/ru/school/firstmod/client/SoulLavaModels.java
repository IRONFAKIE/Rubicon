package ru.school.firstmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.SpriteId;

import ru.school.firstmod.FirstMod;

/**
 * Подмена модели ванильной лавы на перекрашенную «Лаву Душ».
 *
 * <p>Геометрия, слой отрисовки и анимация остаются ванильными — меняются
 * только спрайты, поэтому лава сохраняет привычную текстуру и кадры
 * анимации, но становится насыщенной бирюзовой.</p>
 */
public final class SoulLavaModels {
	private static final SpriteId STILL_SPRITE = new SpriteId(
			TextureAtlas.LOCATION_BLOCKS,
			FirstMod.id("block/soul_lava_still")
	);

	private static final SpriteId FLOWING_SPRITE = new SpriteId(
			TextureAtlas.LOCATION_BLOCKS,
			FirstMod.id("block/soul_lava_flow")
	);

	private static FluidModel cachedModel;
	private static TextureAtlasSprite cachedStill;

	private SoulLavaModels() {
	}

	public static FluidModel soulLava(FluidModel vanilla) {
		AtlasManager atlas = Minecraft.getInstance().getAtlasManager();
		TextureAtlasSprite still = atlas.get(STILL_SPRITE);

		if (cachedModel != null && cachedStill == still) {
			return cachedModel;
		}

		TextureAtlasSprite flowing = atlas.get(FLOWING_SPRITE);
		Material.Baked vanillaStill = vanilla.stillMaterial();
		Material.Baked vanillaFlowing = vanilla.flowingMaterial();

		Material.Baked stillMaterial = new Material.Baked(
				still,
				vanillaStill != null && vanillaStill.forceTranslucent()
		);
		Material.Baked flowingMaterial = new Material.Baked(
				flowing,
				vanillaFlowing != null && vanillaFlowing.forceTranslucent()
		);

		cachedModel = new FluidModel(
				vanilla.layer(),
				stillMaterial,
				flowingMaterial,
				vanilla.overlayMaterial(),
				vanilla.tintSource()
		);
		cachedStill = still;
		return cachedModel;
	}
}
