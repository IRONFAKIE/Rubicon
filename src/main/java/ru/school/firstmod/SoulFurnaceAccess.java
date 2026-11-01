package ru.school.firstmod;

/**
 * Доступ к состоянию печи «сейчас горит Бутылка с душой».
 *
 * <p>Реализуется миксином в {@code AbstractFurnaceBlockEntity}; флаг
 * сохраняется вместе с блок-сущностью, поэтому перезагрузка чанка не
 * позволяет обойти ограничение.</p>
 */
public interface SoulFurnaceAccess {
	boolean firstmod$isSoulFuel();

	void firstmod$setSoulFuel(boolean value);
}
