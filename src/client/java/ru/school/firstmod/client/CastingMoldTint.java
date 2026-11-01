package ru.school.firstmod.client;

import java.util.Set;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import ru.school.firstmod.CastingMoldBlock;

/**
 * Тонирование установленной литейной формы по её свойству {@code color}.
 */
public class CastingMoldTint implements BlockTintSource {
	@Override
	public int color(BlockState state) {
		DyeColor dye = DyeColor.byId(state.getValue(CastingMoldBlock.COLOR));
		return 0xFF000000 | dye.getTextureDiffuseColor();
	}

	@Override
	public Set<Property<?>> relevantProperties() {
		return Set.of(CastingMoldBlock.COLOR);
	}
}
