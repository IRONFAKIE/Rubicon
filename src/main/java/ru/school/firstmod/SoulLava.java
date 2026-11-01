package ru.school.firstmod;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Утилиты для «Лавы Душ».
 *
 * <p>Лава Душ — это обычная ванильная лава, которая непосредственно
 * опирается на песок душ. Отдельного блока/жидкости нет: при попытке
 * набрать такую лаву ведром игрок получает обычное ведро лавы, а
 * «душевные» свойства действуют только пока лава связана с песком душ.</p>
 *
 * <p>Цвет должен сохраняться на всех стадиях течения. Поэтому блок лавы
 * считается душевным не только тогда, когда под ним лежит песок душ, но и
 * тогда, когда вверх по течению (к источнику) находится такой блок. Поиск
 * идёт строго против направления потока: уровень жидкости должен
 * возрастать, а падающая лава прослеживается вверх по столбу. Это не даёт
 * статусу бесконечно расползаться по всей соседней лаве.</p>
 */
public final class SoulLava {
	/** Предохранитель от случайных циклов и слишком длинных потоков. */
	private static final int MAX_TRACE_STEPS = 96;

	private SoulLava() {
	}

	/** Является ли блок в позиции лавой, связанной с песком душ. */
	public static boolean isSoulLava(BlockGetter level, BlockPos pos) {
		return isSoulLava(level, pos, level.getFluidState(pos));
	}

	/**
	 * То же самое, но с уже полученным состоянием жидкости — так рендерер
	 * не запрашивает состояние блока повторно.
	 */
	public static boolean isSoulLava(BlockGetter level, BlockPos pos, FluidState state) {
		if (!state.is(FluidTags.LAVA)) {
			return false;
		}

		// Источник (или промежуточный блок) прямо на песке душ.
		if (isSoulSandBelow(level, pos)) {
			return true;
		}

		// Настоящий источник без песка душ под ним — обычная лава.
		// (Источники не могут «наследовать» цвет от соседей.)
		if (state.isSource()) {
			return false;
		}

		return tracesToSoulSand(level, pos);
	}

	/** Верхний блок лавы, под которым находится песок душ. */
	public static boolean isSoulLavaSurface(BlockGetter level, BlockPos pos) {
		return isSoulLava(level, pos) && !fluidState(level, pos.above()).is(FluidTags.LAVA);
	}

	/**
	 * Идёт вверх по течению от текущего блока и ищет лаву на песке душ.
	 *
	 * <p>Правила ванильного течения:</p>
	 * <ul>
	 *   <li>горизонтальный поток имеет уровень меньше, чем у блока-источника
	 *       выше по течению;</li>
	 *   <li>падающая лава имеет полный уровень ({@link FluidState#AMOUNT_FULL})
	 *       и питается блоком сверху;</li>
	 *   <li>поток может огибать угол, поэтому диагональный сосед тоже
	 *       рассматривается, но только если оба ортогональных угла заняты
	 *       твёрдыми блоками — иначе это была бы просто параллельная лава.</li>
	 * </ul>
	 */
	private static boolean tracesToSoulSand(BlockGetter level, BlockPos start) {
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();

		BlockPos startPos = start.immutable();
		queue.add(startPos);
		visited.add(startPos);

		int steps = 0;

		while (!queue.isEmpty() && steps++ < MAX_TRACE_STEPS) {
			BlockPos current = queue.poll();
			FluidState currentState = fluidState(level, current);

			if (!currentState.is(FluidTags.LAVA)) {
				continue;
			}

			if (isSoulSandBelow(level, current)) {
				return true;
			}

			if (currentState.isSource()) {
				// Дошли до обычного источника — эта ветка потока не душевная.
				continue;
			}

			int amount = currentState.getAmount();

			// Падающая лава: питается блоком сверху.
			if (amount >= FluidState.AMOUNT_FULL) {
				enqueue(level, current.above(), queue, visited);
				continue;
			}

			// Горизонтальный поток: выше по течению уровень строго больше.
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos neighbour = current.relative(direction);
				FluidState neighbourState = fluidState(level, neighbour);

				if (neighbourState.is(FluidTags.LAVA)
						&& neighbourState.getAmount() > amount) {
					enqueue(level, neighbour, queue, visited);
				}
			}

			// Поток может стекать из падающего столба сверху.
			FluidState above = fluidState(level, current.above());
			if (above.is(FluidTags.LAVA) && above.getAmount() >= FluidState.AMOUNT_FULL) {
				enqueue(level, current.above(), queue, visited);
			}

			// Диагональный поток вокруг угла. Проверяем только настоящие
			// углы, чтобы не перекрашивать параллельные потоки.
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				Direction clockwise = direction.getClockWise();
				BlockPos diagonal = current.relative(direction).relative(clockwise);
				FluidState diagonalState = fluidState(level, diagonal);

				if (!diagonalState.is(FluidTags.LAVA)
						|| diagonalState.getAmount() <= amount) {
					continue;
				}

				boolean sideFree = !fluidState(level, current.relative(direction)).is(FluidTags.LAVA);
				boolean cornerFree = !fluidState(level, current.relative(clockwise)).is(FluidTags.LAVA);

				if (sideFree && cornerFree) {
					enqueue(level, diagonal, queue, visited);
				}
			}
		}

		return false;
	}

	/**
	 * Безопасно читает состояние жидкости.
	 *
	 * <p>Во время компиляции секции рендера доступен только снимок
	 * 3×3×3 секций. Запрос за его границы — это не «ошибка рендера»,
	 * а нормальная ситуация на краю снимка, поэтому такие позиции
	 * считаются пустыми.</p>
	 */
	private static FluidState fluidState(BlockGetter level, BlockPos pos) {
		if (!canQuery(level, pos)) {
			return Fluids.EMPTY.defaultFluidState();
		}

		return level.getFluidState(pos);
	}

	private static boolean isSoulSandBelow(BlockGetter level, BlockPos pos) {
		BlockPos below = pos.below();

		return canQuery(level, below) && level.getBlockState(below).is(Blocks.SOUL_SAND);
	}

	private static boolean canQuery(BlockGetter level, BlockPos pos) {
		return !(level instanceof SoulLavaBounds bounds) || bounds.containsPos(pos);
	}

	private static void enqueue(
			BlockGetter level,
			BlockPos pos,
			ArrayDeque<BlockPos> queue,
			Set<BlockPos> visited
	) {
		BlockPos immutable = pos.immutable();

		if (!canQuery(level, immutable)) {
			return;
		}

		if (visited.add(immutable) && level.getFluidState(immutable).is(FluidTags.LAVA)) {
			queue.add(immutable);
		}
	}
}
