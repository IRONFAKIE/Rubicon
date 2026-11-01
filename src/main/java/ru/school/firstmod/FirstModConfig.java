package ru.school.firstmod;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Серверная конфигурация мода.
 *
 * <p>Файл {@code config/first-mod.json} создаётся при первом запуске.
 * Значения читаются один раз при инициализации мода и используются
 * только на сервере.</p>
 */
public final class FirstModConfig {
	public static final int DEFAULT_SPIRIT_RESPAWN_COOLDOWN_TICKS = 6000;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "first-mod.json";
	private static final String SPIRIT_COOLDOWN_KEY = "spiritRespawnCooldownTicks";

	private static int spiritRespawnCooldownTicks = DEFAULT_SPIRIT_RESPAWN_COOLDOWN_TICKS;

	private FirstModConfig() {
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);

		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				if (root.has(SPIRIT_COOLDOWN_KEY)) {
					spiritRespawnCooldownTicks = Math.max(0, root.get(SPIRIT_COOLDOWN_KEY).getAsInt());
				}
			} catch (Exception exception) {
				FirstMod.LOGGER.warn("Не удалось прочитать {}, используются значения по умолчанию", path, exception);
				spiritRespawnCooldownTicks = DEFAULT_SPIRIT_RESPAWN_COOLDOWN_TICKS;
			}
		}

		write(path);
	}

	private static void write(Path path) {
		JsonObject root = new JsonObject();
		root.addProperty(SPIRIT_COOLDOWN_KEY, spiritRespawnCooldownTicks);

		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (Exception exception) {
			FirstMod.LOGGER.warn("Не удалось записать {}", path, exception);
		}
	}

	/** Задержка возрождения духов в озере после успешного захвата, в тиках. */
	public static int spiritRespawnCooldownTicks() {
		return spiritRespawnCooldownTicks;
	}
}
