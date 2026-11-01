package ru.school.firstmod;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Микро-озеро Лавы Душ для Долины песка душ.
 *
 * <p>Структура вырезает небольшой круглый бассейн, дно которого —
 * песок душ, а над ним находится обычная лава. Поэтому каждый блок
 * лавы в озере является Лавой Душ. Под озером всегда находится одна
 * рубиновая жила (хрупкий рубин), а прямо под ней — скрытая рубиновая
 * жеода.</p>
 */
public class SoulLavaLakeStructure extends Structure {
	public static final MapCodec<SoulLavaLakeStructure> CODEC = simpleCodec(SoulLavaLakeStructure::new);

	public SoulLavaLakeStructure(StructureSettings settings) {
		super(settings);
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		return onTopOfChunkCenter(
				context,
				Heightmap.Types.WORLD_SURFACE_WG,
				builder -> addPieces(context, builder)
		);
	}

	private static void addPieces(GenerationContext context, StructurePiecesBuilder builder) {
		int x = context.chunkPos().getMinBlockX() + 8;
		int z = context.chunkPos().getMinBlockZ() + 8;
		LevelHeightAccessor heights = context.heightAccessor();
		NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z, heights, context.randomState());

		// Ищем поверхность, на которую можно поставить озеро.
		int surface = -1;
		for (int y = heights.getMaxY() - 2; y > heights.getMinY() + 8; y--) {
			BlockState state = column.getBlock(y);

			// В Нижнем мире сверху лежит бедрок: пропускаем его, иначе
			// структурой выбирается крыша и генерация срывается.
			if (state.is(Blocks.BEDROCK)) {
				continue;
			}

			if (!state.isAir() && state.getFluidState().isEmpty()) {
				surface = y + 1;
				break;
			}
		}

		if (surface < heights.getMinY() + 12 || surface > heights.getMaxY() - 5) {
			return;
		}

		RandomSource random = context.random();
		int radius = 5 + random.nextInt(4);
		builder.addPiece(new SoulLavaLakePiece(x, surface, z, radius, random));
	}

	@Override
	public StructureType<?> type() {
		return FirstMod.SOUL_LAVA_LAKE_STRUCTURE;
	}

	/**
	 * Один кусок структуры: бассейн, жила и жеода.
	 */
	public static class SoulLavaLakePiece extends StructurePiece {
		private static final int VEIN_HEIGHT = 5;

		private final int centerX;
		private final int surfaceY;
		private final int centerZ;
		private final int radius;
		private final int geodeRadius;
		private final int geodeCenterY;

		public SoulLavaLakePiece(int centerX, int surfaceY, int centerZ, int radius, RandomSource random) {
			super(
					FirstMod.SOUL_LAVA_LAKE_PIECE,
					0,
					makeBounds(centerX, surfaceY, centerZ, radius)
			);
			this.centerX = centerX;
			this.surfaceY = surfaceY;
			this.centerZ = centerZ;
			this.radius = radius;
			this.geodeRadius = 3 + random.nextInt(3);
			this.geodeCenterY = surfaceY - 2 - VEIN_HEIGHT - geodeRadius - 1;
		}

		public SoulLavaLakePiece(CompoundTag tag) {
			super(FirstMod.SOUL_LAVA_LAKE_PIECE, tag);
			this.centerX = tag.getIntOr("CX", 0);
			this.surfaceY = tag.getIntOr("CY", 0);
			this.centerZ = tag.getIntOr("CZ", 0);
			this.radius = tag.getIntOr("R", 5);
			this.geodeRadius = tag.getIntOr("GR", 4);
			this.geodeCenterY = tag.getIntOr("GY", surfaceY - 12);
		}

		private static BoundingBox makeBounds(int x, int surfaceY, int z, int radius) {
			// Радиус жеоды не превышает 5, поэтому фиксированный запас
			// покрывает и жилу, и всю жеоду.
			int minY = Math.max(-60, surfaceY - 2 - VEIN_HEIGHT - 16);
			return new BoundingBox(
					x - radius - 2,
					minY,
					z - radius - 2,
					x + radius + 2,
					surfaceY + 4,
					z + radius + 2
			);
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
			tag.putInt("CX", this.centerX);
			tag.putInt("CY", this.surfaceY);
			tag.putInt("CZ", this.centerZ);
			tag.putInt("R", this.radius);
			tag.putInt("GR", this.geodeRadius);
			tag.putInt("GY", this.geodeCenterY);
		}

		@Override
		public void postProcess(
				WorldGenLevel level,
				StructureManager structureManager,
				ChunkGenerator chunkGenerator,
				RandomSource random,
				BoundingBox chunkBox,
				ChunkPos chunkPos,
				BlockPos origin
		) {
			carveLake(level, random, chunkBox);
			carveVein(level, chunkBox);
			carveGeode(level, random, chunkBox);
		}

		private void carveLake(WorldGenLevel level, RandomSource random, BoundingBox box) {
			int lavaY = this.surfaceY - 1;
			int floorY = this.surfaceY - 2;

			for (int dx = -this.radius - 2; dx <= this.radius + 2; dx++) {
				for (int dz = -this.radius - 2; dz <= this.radius + 2; dz++) {
					int x = this.centerX + dx;
					int z = this.centerZ + dz;
					double distance = Math.sqrt(dx * dx + dz * dz);

					if (distance <= this.radius + 0.2D) {
						set(level, box, x, lavaY, z, Blocks.LAVA.defaultBlockState());
						set(level, box, x, floorY, z, Blocks.SOUL_SAND.defaultBlockState());
						clear(level, box, x, this.surfaceY, z);
						clear(level, box, x, this.surfaceY + 1, z);
					} else if (distance <= this.radius + 1.8D) {
						// Плотный бортик не даёт лаве растечься.
						set(level, box, x, lavaY, z, Blocks.SOUL_SOIL.defaultBlockState());
						set(level, box, x, floorY, z, Blocks.SOUL_SAND.defaultBlockState());
						clear(level, box, x, this.surfaceY, z);
						clear(level, box, x, this.surfaceY + 1, z);

						if (random.nextInt(10) == 0) {
							set(level, box, x, this.surfaceY, z, Blocks.SOUL_FIRE.defaultBlockState());
						}
					}
				}
			}
		}

		private void carveVein(WorldGenLevel level, BoundingBox box) {
			// Одна жила крестом, начинающаяся сразу под песком душ.
			for (int i = 0; i < VEIN_HEIGHT; i++) {
				int y = this.surfaceY - 3 - i;
				set(level, box, this.centerX, y, this.centerZ, Blocks.AIR.defaultBlockState());
				set(level, box, this.centerX, y, this.centerZ, FirstMod.FRAGILE_RUBY_BLOCK.defaultBlockState());
			}

			for (Direction direction : Direction.Plane.HORIZONTAL) {
				int x = this.centerX + direction.getStepX();
				int z = this.centerZ + direction.getStepZ();
				for (int i = 0; i < VEIN_HEIGHT - 1; i++) {
					set(
							level,
							box,
							x,
							this.surfaceY - 3 - i,
							z,
							FirstMod.FRAGILE_RUBY_BLOCK.defaultBlockState()
					);
				}
			}
		}

		private void carveGeode(WorldGenLevel level, RandomSource random, BoundingBox box) {
			int r = this.geodeRadius;
			int cx = this.centerX;
			int cy = this.geodeCenterY;
			int cz = this.centerZ;

			for (int dx = -r; dx <= r; dx++) {
				for (int dy = -r; dy <= r; dy++) {
					for (int dz = -r; dz <= r; dz++) {
						double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
						if (distance > r) {
							continue;
						}

						int x = cx + dx;
						int y = cy + dy;
						int z = cz + dz;
						BlockPos pos = new BlockPos(x, y, z);

						// Не трогаем лавовое море Нижнего мира.
						if (level.getFluidState(pos).is(FluidTags.LAVA)) {
							continue;
						}

						BlockState state;
						if (distance > r - 1.0D) {
							state = Blocks.SMOOTH_BASALT.defaultBlockState();
						} else if (distance > r - 2.0D) {
							state = Blocks.BLACKSTONE.defaultBlockState();
						} else if (distance > r - 3.0D) {
							state = FirstMod.FRAGILE_RUBY_BLOCK.defaultBlockState();
						} else {
							state = Blocks.AIR.defaultBlockState();
						}

						set(level, box, x, y, z, state);
					}
				}
			}

			// Немного рубиновой руды внутри жеоды — для награды.
			for (int i = 0; i < 8; i++) {
				int x = cx + random.nextInt(r * 2 + 1) - r;
				int y = cy + random.nextInt(r * 2 + 1) - r;
				int z = cz + random.nextInt(r * 2 + 1) - r;
				if (level.getBlockState(new BlockPos(x, y, z)).is(Blocks.BLACKSTONE)) {
					set(level, box, x, y, z, FirstMod.RUBY_ORE_BLOCK.defaultBlockState());
				}
			}
		}

		private static void set(WorldGenLevel level, BoundingBox box, int x, int y, int z, BlockState state) {
			if (y < level.getMinY() || y >= level.getMaxY() || !box.isInside(x, y, z)) {
				return;
			}
			level.setBlock(new BlockPos(x, y, z), state, 2);
		}

		private static void clear(WorldGenLevel level, BoundingBox box, int x, int y, int z) {
			set(level, box, x, y, z, Blocks.AIR.defaultBlockState());
		}
	}
}
