package ru.school.firstmod;

import net.minecraft.core.BlockPos;

/**
 * Ограниченная область блоков, доступная для чтения.
 *
 * <p>Клиентский рендер чанков передаёт в отрисовку жидкостей
 * {@code RenderSectionRegion} — снимок только 3×3×3 секций вокруг
 * компилируемой секции. Любое обращение за пределы этого региона
 * приводит к {@code ArrayIndexOutOfBoundsException}. Этот интерфейс
 * позволяет утилитам (например, {@link SoulLava}) заранее узнать границы
 * и не выходить за них.</p>
 */
public interface SoulLavaBounds {
	/** Находится ли позиция внутри доступной области. */
	boolean containsPos(BlockPos pos);
}
