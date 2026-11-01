package ru.school.firstmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Приёмник активатора.
 *
 * <p>Принимает беспроводной сигнал от активированного активатора и
 * выдаёт красный сигнал силы 15 на 10 тиков. Фильтр задаётся
 * приседанием + правым кликом активированным активатором: предмет
 * берётся из второй руки. Пустая вторая рука сбрасывает фильтр.</p>
 */
public class ActivatorReceiverBlock extends Block implements EntityBlock {
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	public static final int PULSE_TICKS = 10;
	public static final int ACTIVATION_RADIUS = 24;

	public ActivatorReceiverBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWERED);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ActivatorReceiverBlockEntity(pos, state);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return state.getValue(POWERED) ? 15 : 0;
	}

	@Override
	protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return 0;
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
		if (!(stack.getItem() instanceof ActivatorItem activator) || !activator.isActivated()) {
			return InteractionResult.PASS;
		}

		if (!player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(level.getBlockEntity(pos) instanceof ActivatorReceiverBlockEntity receiver)) {
			return InteractionResult.PASS;
		}

		InteractionHand otherHand = hand == InteractionHand.MAIN_HAND
				? InteractionHand.OFF_HAND
				: InteractionHand.MAIN_HAND;
		ItemStack filter = player.getItemInHand(otherHand);

		if (filter.isEmpty()) {
			receiver.setFilter(ItemStack.EMPTY);
			player.sendOverlayMessage(
					Component.translatable("message.first-mod.receiver.filter_cleared")
			);
		} else {
			receiver.setFilter(filter);
			player.sendOverlayMessage(
					Component.translatable("message.first-mod.receiver.filter_set", filter.getHoverName())
			);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(POWERED)) {
			return;
		}

		level.setBlock(pos, state.setValue(POWERED, false), 3);
		level.playSound(
				null,
				pos,
				SoundEvents.STONE_BUTTON_CLICK_OFF,
				SoundSource.BLOCKS,
				0.45F,
				1.2F
		);
	}

	/**
	 * Запускает все подходящие приёмники вокруг игрока.
	 *
	 * @return количество сработавших приёмников
	 */
	public static int pulseNearby(Level level, Player player) {
		BlockPos center = player.blockPosition();
		int radius = ACTIVATION_RADIUS;
		int radiusSquared = radius * radius;
		int count = 0;

		for (BlockPos pos : BlockPos.betweenClosed(
				center.offset(-radius, -radius, -radius),
				center.offset(radius, radius, radius)
		)) {
			if (pos.distSqr(center) > radiusSquared) {
				continue;
			}

			BlockState state = level.getBlockState(pos);
			if (!state.is(FirstMod.ACTIVATOR_RECEIVER)) {
				continue;
			}

			if (!(level.getBlockEntity(pos) instanceof ActivatorReceiverBlockEntity receiver)) {
				continue;
			}

			if (!receiver.accepts(level, player)) {
				continue;
			}

			pulse(level, pos.immutable());
			count++;
		}

		if (count > 0) {
			level.playSound(
					null,
					center,
					SoundEvents.STONE_BUTTON_CLICK_ON,
					SoundSource.BLOCKS,
					0.6F,
					1.5F
			);
		}

		return count;
	}

	public static void pulse(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);

		if (!state.is(FirstMod.ACTIVATOR_RECEIVER)) {
			return;
		}

		if (!state.getValue(POWERED)) {
			level.setBlock(pos, state.setValue(POWERED, true), 3);
		}

		level.scheduleTick(pos, FirstMod.ACTIVATOR_RECEIVER, PULSE_TICKS);
	}
}
