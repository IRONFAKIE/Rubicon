package ru.school.firstmod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Предмет литейной формы.
 *
 * <p>Сохраняет цвет красителя и переносит его в состояние блока при
 * установке.</p>
 */
public class CastingMoldBlockItem extends BlockItem {
	public CastingMoldBlockItem(net.minecraft.world.level.block.Block block, Item.Properties properties) {
		super(block, properties);
	}

	@Override
	protected BlockState getPlacementState(BlockPlaceContext context) {
		BlockState state = super.getPlacementState(context);
		if (state == null) {
			return null;
		}

		DyeColor dye = context.getItemInHand().get(DataComponents.DYE);
		if (dye == null) {
			dye = CastingMoldBlock.dyeFor(CastingMoldBlock.dyedColor(context.getItemInHand()));
		}

		return state.setValue(CastingMoldBlock.COLOR, dye.getId());
	}
}
