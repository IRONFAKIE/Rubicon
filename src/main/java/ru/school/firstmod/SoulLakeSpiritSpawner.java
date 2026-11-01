package ru.school.firstmod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.AABB;

/**
 * Заселяет озёра Лавы Душ духами лавомерки.
 *
 * <p>Количество духов зависит от площади озера: в маленьких лужах
 * появляется 1–2 духа, в крупных — до 5. Духи постоянные, поэтому
 * повторная проверка не создаёт бесконечных копий.</p>
 */
public final class SoulLakeSpiritSpawner {
	private static final int MAX_LAKE_BLOCKS = 1024;

	private static int tickCounter;

	private SoulLakeSpiritSpawner() {
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(SoulLakeSpiritSpawner::tick);
	}

	private static void tick(ServerLevel level) {
		if (level.dimension() != Level.NETHER) {
			return;
		}

		tickCounter++;
		if (tickCounter % 40 != 0) {
			return;
		}

		for (ServerPlayer player : level.players()) {
			if (!level.getBiome(player.blockPosition()).is(Biomes.SOUL_SAND_VALLEY)) {
				continue;
			}

			BlockPos lava = findNearbySoulLava(level, player.blockPosition());
			if (lava == null) {
				continue;
			}

			Set<BlockPos> lake = collectLake(level, lava);
			if (lake.isEmpty()) {
				continue;
			}

			int expected = expectedSpirits(lake.size());
			int existing = countSpirits(level, lava, lake.size());
			if (existing >= expected) {
				continue;
			}

			String key = lakeKey(level, lake);
			if (SoulSpiritCooldowns.get(level).isOnCooldown(key, level.getGameTime())) {
				// Озеро ещё восстанавливается после захвата духа.
				continue;
			}

			spawnSpirit(level, lake, key);
		}
	}

	/** Стабильный ключ озера по его минимальным координатам. */
	private static String lakeKey(ServerLevel level, Set<BlockPos> lake) {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;

		for (BlockPos pos : lake) {
			minX = Math.min(minX, pos.getX());
			minY = Math.min(minY, pos.getY());
			minZ = Math.min(minZ, pos.getZ());
		}

		return level.dimension().identifier() + "@" + minX + "," + minY + "," + minZ;
	}

	private static BlockPos findNearbySoulLava(ServerLevel level, BlockPos center) {
		int radius = 28;

		for (int dx = -radius; dx <= radius; dx += 4) {
			for (int dz = -radius; dz <= radius; dz += 4) {
				int x = center.getX() + dx;
				int z = center.getZ() + dz;

				for (int y = center.getY() + 6; y >= center.getY() - 28; y--) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!level.isLoaded(pos)) {
						continue;
					}
					if (SoulLava.isSoulLavaSurface(level, pos)) {
						return pos;
					}
				}
			}
		}

		return null;
	}

	private static Set<BlockPos> collectLake(ServerLevel level, BlockPos start) {
		Set<BlockPos> lake = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		lake.add(start.immutable());
		queue.add(start.immutable());

		while (!queue.isEmpty() && lake.size() < MAX_LAKE_BLOCKS) {
			BlockPos current = queue.poll();

			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos next = current.relative(direction).immutable();
				if (lake.contains(next)) {
					continue;
				}
				if (SoulLava.isSoulLavaSurface(level, next)) {
					lake.add(next);
					queue.add(next);
				}
			}
		}

		return lake;
	}

	private static int expectedSpirits(int lakeSize) {
		if (lakeSize <= 12) {
			return lakeSize <= 5 ? 1 : 2;
		}
		if (lakeSize <= 30) {
			return 3;
		}
		if (lakeSize <= 70) {
			return 4;
		}
		return 5;
	}

	private static int countSpirits(ServerLevel level, BlockPos center, int lakeSize) {
		int range = Math.max(8, (int) Math.sqrt(lakeSize) + 8);
		AABB area = new AABB(center).inflate(range, 8.0D, range);
		return level.getEntitiesOfClass(SoulStriderSpirit.class, area).size();
	}

	private static void spawnSpirit(ServerLevel level, Set<BlockPos> lake, String key) {
		List<BlockPos> candidates = new ArrayList<>(lake);
		int start = level.getRandom().nextInt(candidates.size());

		for (int i = 0; i < candidates.size(); i++) {
			BlockPos lava = candidates.get((start + i) % candidates.size());
			BlockPos spawn = lava.above();
			if (!level.getBlockState(spawn).isAir()) {
				continue;
			}

			SoulStriderSpirit spirit = FirstMod.SOUL_STRIDER_SPIRIT.spawn(
					level,
					spawn,
					EntitySpawnReason.STRUCTURE
			);
			if (spirit == null) {
				continue;
			}

			spirit.setPersistenceRequired();
			spirit.setLakeKey(key);
			level.sendParticles(
					ParticleTypes.SOUL,
					spawn.getX() + 0.5D,
					spawn.getY() + 0.8D,
					spawn.getZ() + 0.5D,
					18,
					0.4D,
					0.6D,
					0.4D,
					0.01D
			);
			return;
		}
	}
}
