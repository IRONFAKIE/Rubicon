package ru.school.firstmod.client;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.strider.AdultStriderModel;
import net.minecraft.client.model.monster.strider.BabyStriderModel;
import net.minecraft.client.model.monster.strider.StriderModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.StriderRenderState;
import net.minecraft.resources.Identifier;

import ru.school.firstmod.FirstMod;
import ru.school.firstmod.SoulStriderSpirit;

/**
 * Рендерер Духа Лавомерки.
 *
 * <p>Использует ванильные модели Страйдера ({@link AdultStriderModel} и
 * {@link BabyStriderModel}) — так сохраняются узнаваемый силуэт, пропорции,
 * походка и отдельная модель детёныша. Меняются только текстуры: обычный
 * Страйдер красный, а Дух — холодный сине-бирюзовый.</p>
 *
 * <p>Масштаб детёныша (0.5) задаёт сам {@link AgeableMobRenderer}, поэтому
 * модель и хитбокс совпадают с ванильным Страйдером.</p>
 */
public class SoulStriderSpiritRenderer
		extends AgeableMobRenderer<SoulStriderSpirit, StriderRenderState, StriderModel> {
	private static final Identifier ADULT_TEXTURE =
			FirstMod.id("textures/entity/soul_strider_spirit.png");
	private static final Identifier BABY_TEXTURE =
			FirstMod.id("textures/entity/soul_strider_spirit_baby.png");

	public SoulStriderSpiritRenderer(EntityRendererProvider.Context context) {
		super(
				context,
				new AdultStriderModel(context.bakeLayer(ModelLayers.STRIDER)),
				new BabyStriderModel(context.bakeLayer(ModelLayers.STRIDER_BABY)),
				0.5F
		);
	}

	@Override
	public StriderRenderState createRenderState() {
		return new StriderRenderState();
	}

	@Override
	public Identifier getTextureLocation(StriderRenderState state) {
		return state.isBaby ? BABY_TEXTURE : ADULT_TEXTURE;
	}
}
