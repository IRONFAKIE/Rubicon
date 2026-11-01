package ru.school.firstmod;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Перезарядка духов по каждому озеру отдельно.
 *
 * <p>Ключ озера — стабильная строка, построенная по его границам, поэтому
 * перезарядка переживает перезапуск сервера и не влияет на другие озёра.</p>
 */
public class SoulSpiritCooldowns extends SavedData {
	public static final Codec<SoulSpiritCooldowns> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG)
			.xmap(SoulSpiritCooldowns::new, data -> data.capturedAt);

	public static final SavedDataType<SoulSpiritCooldowns> TYPE = new SavedDataType<>(
			FirstMod.id("soul_spirit_cooldowns"),
			SoulSpiritCooldowns::new,
			CODEC,
			DataFixTypes.LEVEL
	);

	private final Map<String, Long> capturedAt;

	public SoulSpiritCooldowns() {
		this(Map.of());
	}

	private SoulSpiritCooldowns(Map<String, Long> capturedAt) {
		this.capturedAt = new HashMap<>(capturedAt);
	}

	public static SoulSpiritCooldowns get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Идёт ли ещё перезарядка озера. */
	public boolean isOnCooldown(String lakeKey, long gameTime) {
		Long captured = this.capturedAt.get(lakeKey);
		if (captured == null) {
			return false;
		}

		int cooldown = FirstModConfig.spiritRespawnCooldownTicks();
		if (cooldown <= 0) {
			return false;
		}

		return gameTime - captured < cooldown;
	}

	/** Запустить перезарядку озера после успешного захвата духа. */
	public void startCooldown(String lakeKey, long gameTime) {
		this.capturedAt.put(lakeKey, gameTime);
		this.setDirty();
	}
}
