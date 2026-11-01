package ru.school.firstmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Литейная форма из пчелиных сот.
 *
 * <p>Пустую форму можно покрасить любым красителем. ПКМ расплавом стали
 * заполняет форму; через минуту расплав застывает (всё это время из формы
 * капают капли мёда). ПКМ по готовой форме выдаёт стальной слиток.</p>
 */
public class CastingMoldBlock extends Block {
	public static final BooleanProperty FILLED = BooleanProperty.create("filled");
	public static final BooleanProperty READY = BooleanProperty.create("ready");
	public static final IntegerProperty COLOR = IntegerProperty.create("color", 0, 15);

	/** Одна минута до застывания стали. */
	public static final int SETTING_TICKS = 20 * 60;

	private static final VoxelShape SHAPE = Shapes.or(
			Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.125D, 1.0D),
			Shapes.box(0.0D, 0.125D, 0.0D, 1.0D, 0.3125D, 0.125D),
			Shapes.box(0.0D, 0.125D, 0.875D, 1.0D, 0.3125D, 1.0D),
			Shapes.box(0.0D, 0.125D, 0.125D, 0.125D, 0.3125D, 0.875D),
			Shapes.box(0.875D, 0.125D, 0.125D, 1.0D, 0.3125D, 0.875D)
	);

	public CastingMoldBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.defaultBlockState()
				.setValue(FILLED, false)
				.setValue(READY, false)
				.setValue(COLOR, 1));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FILLED, READY, COLOR);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useItemOn(
			ItemStack stack,
			BlockState state,
			Level level,
			BlockPos pos,
			Player player,
			InteractionHand hand,
			BlockHitResult hitResult
	) {
		// Готовая форма отдаёт слиток независимо от предмета в руке.
		if (state.getValue(READY)) {
			if (!level.isClientSide()) {
				collectIngot(level, pos, state, player);
			}
			return InteractionResult.SUCCESS;
		}

		// Покраска.
		if (stack.has(net.minecraft.core.component.DataComponents.DYE)) {
			if (!level.isClientSide()) {
				int color = stack.getOrDefault(
						net.minecraft.core.component.DataComponents.DYE,
						net.minecraft.world.item.DyeColor.WHITE
				).getId();
				level.setBlock(pos, state.setValue(COLOR, color), Block.UPDATE_ALL);
				level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
				if (!player.hasInfiniteMaterials()) {
					stack.shrink(1);
				}
			}
			return InteractionResult.SUCCESS;
		}

		// Заливка расплавом стали.
		if (stack.is(FirstMod.STEEL_MELT)) {
			if (state.getValue(FILLED)) {
				if (!level.isClientSide()) {
					player.sendOverlayMessage(
							Component.translatable("message.first-mod.mold_already_filled")
					);
				}
				return InteractionResult.SUCCESS;
			}

			if (!level.isClientSide()) {
				level.setBlock(pos, state.setValue(FILLED, true).setValue(READY, false), Block.UPDATE_ALL);
				level.scheduleTick(pos, this, SETTING_TICKS);
				level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1.0F, 0.9F);
				if (!player.hasInfiniteMaterials()) {
					stack.shrink(1);
				}
			}
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	@Override
	protected InteractionResult useWithoutItem(
			BlockState state,
			Level level,
			BlockPos pos,
			Player player,
			BlockHitResult hitResult
	) {
		if (!state.getValue(READY)) {
			return InteractionResult.PASS;
		}

		if (!level.isClientSide()) {
			collectIngot(level, pos, state, player);
		}
		return InteractionResult.SUCCESS;
	}

	private void collectIngot(Level level, BlockPos pos, BlockState state, Player player) {
		level.setBlock(pos, state.setValue(FILLED, false).setValue(READY, false), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.2F);

		ItemStack ingot = new ItemStack(FirstMod.STEEL_INGOT);
		if (!player.getInventory().add(ingot)) {
			player.drop(ingot, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}

		if (level instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(
					ParticleTypes.SOUL_FIRE_FLAME,
					pos.getX() + 0.5D,
					pos.getY() + 0.45D,
					pos.getZ() + 0.5D,
					10,
					0.2D,
					0.15D,
					0.2D,
					0.005D
			);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(FILLED) || state.getValue(READY)) {
			return;
		}

		level.setBlock(pos, state.setValue(READY, true), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.HONEYCOMB_WAX_ON, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.sendParticles(
				ParticleTypes.HAPPY_VILLAGER,
				pos.getX() + 0.5D,
				pos.getY() + 0.4D,
				pos.getZ() + 0.5D,
				8,
				0.3D,
				0.1D,
				0.3D,
				0.01D
		);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(FILLED) || state.getValue(READY)) {
			return;
		}

		if (random.nextInt(3) == 0) {
			level.addParticle(
					ParticleTypes.DRIPPING_HONEY,
					pos.getX() + 0.2D + random.nextDouble() * 0.6D,
					pos.getY() + 0.32D,
					pos.getZ() + 0.2D + random.nextDouble() * 0.6D,
					0.0D,
					-0.02D,
					0.0D
			);
		}
	}

	/** Цвет формы по умолчанию (мёд). */
	public static int defaultColor() {
		return 0xFFFFB300;
	}

	/** Ищет ближайший ванильный краситель для сохранённого RGB. */
	public static net.minecraft.world.item.DyeColor dyeFor(int rgb) {
		net.minecraft.world.item.DyeColor best = net.minecraft.world.item.DyeColor.WHITE;
		int bestDistance = Integer.MAX_VALUE;

		for (net.minecraft.world.item.DyeColor color : net.minecraft.world.item.DyeColor.values()) {
			int candidate = color.getTextureDiffuseColor();
			int dr = ((candidate >> 16) & 0xFF) - ((rgb >> 16) & 0xFF);
			int dg = ((candidate >> 8) & 0xFF) - ((rgb >> 8) & 0xFF);
			int db = (candidate & 0xFF) - (rgb & 0xFF);
			int distance = dr * dr + dg * dg + db * db;

			if (distance < bestDistance) {
				bestDistance = distance;
				best = color;
			}
		}

		return best;
	}

	/** Цвет красителя, сохранённый в предмете формы. */
	public static int dyedColor(ItemStack stack) {
		return DyedItemColor.getOrDefault(stack, defaultColor());
	}

	/** Набор свойств, от которых зависит цвет блока. */
	public static final class ColorProperties {
		private ColorProperties() {
		}

		public static java.util.Set<Property<?>> set() {
			return java.util.Set.of(COLOR);
		}
	}
}
