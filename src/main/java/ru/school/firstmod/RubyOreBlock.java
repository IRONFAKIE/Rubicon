package ru.school.firstmod;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ру́дный рубиновый блок.
 *
 * <p>Ведёт себя как подозрительный песок: его можно чистить кисточкой.
 * В отличие от подозрительного песка, после очистки он превращается в землю,
 * а до очистки его можно сломать и забрать с собой.</p>
 */
public class RubyOreBlock extends BrushableBlock {
	public RubyOreBlock(Properties properties) {
		super(Blocks.DIRT, SoundEvents.BRUSH_SAND, SoundEvents.BRUSH_SAND_COMPLETED, properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		BrushableBlockEntity blockEntity = new BrushableBlockEntity(pos, state);
		blockEntity.setLootTable(FirstMod.RUBY_ORE_BRUSH_LOOT, 0L);
		return blockEntity;
	}
}
