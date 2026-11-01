package ru.school.firstmod.client;

/**
 * Признак того, что сущность должна гореть синим огнём душ.
 *
 * <p>Реализуется миксином в {@code EntityRenderState}. Значение
 * выставляется на клиенте по синхронизированному вложению
 * {@code first-mod:soul_fire_until}.</p>
 */
public interface SoulFireRenderState {
	boolean firstmod$isSoulFire();

	void firstmod$setSoulFire(boolean value);
}
