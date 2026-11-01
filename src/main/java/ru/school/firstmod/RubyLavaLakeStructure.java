package ru.school.firstmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

/**
 * «Рубиновое лавовое озеро» Верхнего мира.
 *
 * <p>На поверхности появляется озеро лавы неправильной формы, врезанное
 * в ровный участок земли. Под озером, глубоко в камне, спрятана крупная
 * рубиновая жеода с 1–3 Заражёнными сундуками, а на берегу — ровно одна
 * рубиновая жила. Форма озера, положение жилы и содержимое жеоды
 * детерминированы координатами структуры, поэтому структура корректно
 * генерируется сразу в нескольких чанках.</p>
 *
 * <p>Структура находится командой
 * {@code /locate structure first-mod:ruby_lava_lake}.</p>
 */
public class RubyLavaLakeStructure extends Structure {
	public static final MapCodec<RubyLavaLakeStructure> CODEC = simpleCodec(RubyLavaLakeStructure::new);

	private static final int MIN_RADIUS = 6;
	private static final int MAX_RADIUS = 8;
	private static final int MIN_DEPTH = 2;
	private static final int MAX_DEPTH = 4;

	/** Радиус площадки, по которой проверяется рельеф. */
	private static final int SAMPLE_RADIUS = 12;
	private static final int SAMPLE_STEP = 2;

	/** Допустимый перепад высот на площадке. */
	private static final int MAX_SURFACE_STEP = 3;

	/** Ширина естественного берега вокруг лавы. */
	private static final double LAKE_SHORE = 2.0D;

	private static final double LAKE_MIN_OUTLINE = 5.0D;
	private static final double LAKE_MAX_OUTLINE = 8.5D;

	/** Жеода должна лежать в этом диапазоне высот. */
	private static final int GEODE_MIN_Y = -58;
	private static final int GEODE_MAX_Y = 40;

	/** Радиус жеоды: заметно больше крупной аметистовой жеоды. */
	private static final int MIN_GEODE_RADIUS = 11;
	private static final int MAX_GEODE_RADIUS = 14;

	/** Внешняя граница жеоды после искажения формы. */
	private static final double GEODE_MIN_OUTER = 10.0D;
	private static final double GEODE_MAX_OUTER = 14.0D;

	private static final double GEODE_BASALT_FRACTION = 0.78D;
	private static final double GEODE_BLACKSTONE_FRACTION = 0.56D;
	private static final double GEODE_RUBY_FRACTION = 0.40D;

	/** Минимальный слой камня между дном озера и верхом жеоды. */
	private static final int GEODE_TOP_CLEARANCE = 10;

	private static final int MAX_CHESTS = 3;

	/** Смещения для маленькой жилы руды на берегу. */
	private static final int[][] VEIN_OFFSETS = {
			{0, 0, 0},
			{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
			{0, -1, 0},
			{1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
			{1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1},
			{1, -1, 1}, {-1, -1, 1}, {1, -1, -1}, {-1, -1, -1}
	};

	public RubyLavaLakeStructure(StructureSettings settings) {
		super(settings);
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		int x = context.chunkPos().getMinBlockX() + 8;
		int z = context.chunkPos().getMinBlockZ() + 8;
		LevelHeightAccessor heights = context.heightAccessor();
		int minY = heights.getMinY();
		int maxY = heights.getMaxY();

		int minSurface = Integer.MAX_VALUE;
		int maxSurface = Integer.MIN_VALUE;
		int sum = 0;
		int count = 0;

		// Проверяем рельеф по сетке: ровная площадка, открытое небо и
		// отсутствие пещер прямо под будущей чашей и берегом.
		for (int dx = -SAMPLE_RADIUS; dx <= SAMPLE_RADIUS; dx += SAMPLE_STEP) {
			for (int dz = -SAMPLE_RADIUS; dz <= SAMPLE_RADIUS; dz += SAMPLE_STEP) {
				NoiseColumn column = context.chunkGenerator().getBaseColumn(
						x + dx,
						z + dz,
						heights,
						context.randomState()
				);

				int surface = findSurface(column, minY + 4, maxY - 6);
				if (surface == Integer.MIN_VALUE || !isSuitableColumn(column, surface)) {
					return Optional.empty();
				}

				minSurface = Math.min(minSurface, surface);
				maxSurface = Math.max(maxSurface, surface);
				sum += surface;
				count++;
			}
		}

		if (maxSurface - minSurface > MAX_SURFACE_STEP) {
			return Optional.empty();
		}

		int surfaceY = Mth.clamp(Math.round((float) sum / count), minSurface, maxSurface);

		if (surfaceY < minY + 20 || surfaceY > maxY - 12) {
			return Optional.empty();
		}

		RandomSource random = context.random();
		int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
		int depth = MIN_DEPTH + random.nextInt(MAX_DEPTH - MIN_DEPTH + 1);
		int geodeRadius = MIN_GEODE_RADIUS + random.nextInt(MAX_GEODE_RADIUS - MIN_GEODE_RADIUS + 1);

		// Жеода должна целиком помещаться в мир и не подходить к
		// поверхности ближе, чем GEODE_TOP_CLEARANCE блоков.
		int minGeodeCenter = Math.max(
				GEODE_MIN_Y + (int) GEODE_MAX_OUTER + 1,
				minY + (int) GEODE_MAX_OUTER + 2
		);
		int maxGeodeCenter = Math.min(
				Math.min(
						GEODE_MAX_Y - (int) GEODE_MAX_OUTER - 1,
						maxY - (int) GEODE_MAX_OUTER - 2
				),
				surfaceY - (int) GEODE_MAX_OUTER - GEODE_TOP_CLEARANCE
		);

		if (minGeodeCenter > maxGeodeCenter) {
			return Optional.empty();
		}

		int geodeCenterY = Mth.clamp(surfaceY - 52, minGeodeCenter, maxGeodeCenter);

		int pieceX = x;
		int pieceZ = z;
		int pieceSurface = surfaceY;
		int pieceRadius = radius;
		int pieceDepth = depth;
		int pieceGeodeRadius = geodeRadius;
		int pieceGeodeY = geodeCenterY;

		return Optional.of(new GenerationStub(
				new BlockPos(pieceX, pieceSurface, pieceZ),
				builder -> builder.addPiece(new RubyLavaLakePiece(
						pieceX,
						pieceSurface,
						pieceZ,
						pieceRadius,
						pieceDepth,
						pieceGeodeRadius,
						pieceGeodeY
				))
		));
	}

	/**
	 * Ищет уровень первого воздуха над верхним твёрдым блоком колонки.
	 * Нижняя граница мира и бедрок пропускаются.
	 */
	private static int findSurface(NoiseColumn column, int minY, int maxY) {
		for (int y = maxY; y >= minY; y--) {
			BlockState state = column.getBlock(y);

			if (state.isAir()) {
				continue;
			}
			if (state.is(Blocks.BEDROCK)) {
				continue;
			}

			return y + 1;
		}

		return Integer.MIN_VALUE;
	}

	private static boolean isSuitableColumn(NoiseColumn column, int surfaceY) {
		BlockState top = column.getBlock(surfaceY - 1);

		// Озеро не должно оказаться в воде или на дне океана.
		if (!top.getFluidState().isEmpty()) {
			return false;
		}
		if (top.isAir()) {
			return false;
		}

		// Над озером должно быть открытое небо.
		if (!column.getBlock(surfaceY).isAir() || !column.getBlock(surfaceY + 1).isAir()) {
			return false;
		}

		// Под чашей не должно быть пустот: иначе получится «висящее»
		// дно или озеро над пещерой.
		for (int depth = 1; depth <= 6; depth++) {
			BlockState state = column.getBlock(surfaceY - 1 - depth);

			if (state.isAir() || !state.getFluidState().isEmpty()) {
				return false;
			}
		}

		return true;
	}

	@Override
	public StructureType<?> type() {
		return FirstMod.RUBY_LAVA_LAKE_STRUCTURE;
	}

	/**
	 * Один кусок структуры: чаша озера, глубокая жеода, сундуки и жила.
	 */
	public static class RubyLavaLakePiece extends StructurePiece {
		private final int centerX;
		private final int surfaceY;
		private final int centerZ;
		private final int radius;
		private final int depth;
		private final int geodeRadius;
		private final int geodeCenterY;

		public RubyLavaLakePiece(
				int centerX,
				int surfaceY,
				int centerZ,
				int radius,
				int depth,
				int geodeRadius,
				int geodeCenterY
		) {
			super(
					FirstMod.RUBY_LAVA_LAKE_PIECE,
					0,
					makeBounds(centerX, surfaceY, centerZ, radius, geodeRadius, geodeCenterY)
			);
			this.centerX = centerX;
			this.surfaceY = surfaceY;
			this.centerZ = centerZ;
			this.radius = radius;
			this.depth = depth;
			this.geodeRadius = geodeRadius;
			this.geodeCenterY = geodeCenterY;
		}

		public RubyLavaLakePiece(CompoundTag tag) {
			super(FirstMod.RUBY_LAVA_LAKE_PIECE, tag);
			this.centerX = tag.getIntOr("CX", 0);
			this.surfaceY = tag.getIntOr("CY", 0);
			this.centerZ = tag.getIntOr("CZ", 0);
			this.radius = tag.getIntOr("R", MIN_RADIUS);
			this.depth = tag.getIntOr("D", 3);
			this.geodeRadius = tag.getIntOr("GR", MIN_GEODE_RADIUS);
			this.geodeCenterY = tag.getIntOr("GY", this.surfaceY - 52);
			this.boundingBox = makeBounds(
					this.centerX,
					this.surfaceY,
					this.centerZ,
					this.radius,
					this.geodeRadius,
					this.geodeCenterY
			);
		}

		private static BoundingBox makeBounds(
				int x,
				int surfaceY,
				int z,
				int radius,
				int geodeRadius,
				int geodeCenterY
		) {
			// Запас покрывает берег озера, жилу и максимальный внешний
			// радиус жеоды с искажённой формой.
			int geodeExtent = (int) Math.ceil(GEODE_MAX_OUTER) + 2;
			int half = Math.max(radius + 4, geodeExtent);
			int minY = Math.min(geodeCenterY - geodeExtent, surfaceY - 12);
			int maxY = surfaceY + 5;

			return new BoundingBox(
					x - half,
					minY,
					z - half,
					x + half,
					maxY,
					z + half
			);
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
			tag.putInt("CX", this.centerX);
			tag.putInt("CY", this.surfaceY);
			tag.putInt("CZ", this.centerZ);
			tag.putInt("R", this.radius);
			tag.putInt("D", this.depth);
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
			carveLake(level, chunkBox);
			carveGeode(level, chunkBox);
			decorateGeode(level, chunkBox);
			placeChests(level, chunkBox);
			// Жила рисуется последней, уже в готовом камне на берегу.
			carveVein(level, chunkBox);
		}

		private void carveLake(WorldGenLevel level, BoundingBox box) {
			int lavaTop = this.surfaceY - 2;
			int lavaBottom = lavaTop - this.depth + 1;
			int floor = lavaBottom - 1;
			int extent = MAX_RADIUS + (int) Math.ceil(LAKE_SHORE) + 2;

			for (int dx = -extent; dx <= extent; dx++) {
				for (int dz = -extent; dz <= extent; dz++) {
					int x = this.centerX + dx;
					int z = this.centerZ + dz;
					double distance = Math.sqrt(dx * dx + dz * dz);
					double outline = outlineRadius(dx, dz);

					if (distance > outline + LAKE_SHORE) {
						continue;
					}

					if (distance <= outline) {
						carveLakeColumn(level, box, x, z, lavaTop, lavaBottom, floor);
					} else {
						carveShoreColumn(level, box, x, z, lavaTop);
					}
				}
			}
		}

		private void carveLakeColumn(
				WorldGenLevel level,
				BoundingBox box,
				int x,
				int z,
				int lavaTop,
				int lavaBottom,
				int floor
		) {
			// Убираем землю над уровнем лавы, чтобы чаша была открытой.
			// Верхняя граница фиксирована, поэтому результат не зависит
			// от порядка обработки соседних чанков.
			for (int y = lavaTop + 1; y <= this.surfaceY + 4; y++) {
				clear(level, box, x, y, z);
			}

			// Сама лава и дно чаши.
			for (int y = lavaBottom; y <= lavaTop; y++) {
				set(level, box, x, y, z, Blocks.LAVA.defaultBlockState());
			}
			set(level, box, x, floor, z, Blocks.MAGMA_BLOCK.defaultBlockState());

			// Затыкаем возможные пустоты прямо под дном: никаких
			// висящих островков лавы над пещерами.
			for (int y = floor - 1; y >= floor - 5; y--) {
				BlockState state = level.getBlockState(new BlockPos(x, y, z));

				if (state.isAir() || !state.getFluidState().isEmpty()) {
					set(level, box, x, y, z, Blocks.STONE.defaultBlockState());
				}
			}
		}

		private void carveShoreColumn(
				WorldGenLevel level,
				BoundingBox box,
				int x,
				int z,
				int lavaTop
		) {
			// Берег не поднимаем выше уровня лавы и не строим стен:
			// только заделываем редкие провалы, чтобы лава не вылилась.
			BlockState atLavaLevel = level.getBlockState(new BlockPos(x, lavaTop, z));

			// Если на уровне лавы уже есть твёрдый блок, берег intact.
			if (!atLavaLevel.isAir() && atLavaLevel.getFluidState().isEmpty()) {
				return;
			}

			for (int y = lavaTop; y >= lavaTop - 4; y--) {
				BlockState state = level.getBlockState(new BlockPos(x, y, z));

				if (!state.isAir() && state.getFluidState().isEmpty()) {
					break;
				}

				set(level, box, x, y, z, Blocks.STONE.defaultBlockState());
			}
		}

		private void carveGeode(WorldGenLevel level, BoundingBox box) {
			double[] phases = geodePhases();
			BlockState smoothBasalt = Blocks.SMOOTH_BASALT.defaultBlockState();
			BlockState blackstone = Blocks.BLACKSTONE.defaultBlockState();
			BlockState fragileRuby = FirstMod.FRAGILE_RUBY_BLOCK.defaultBlockState();

			int extent = (int) Math.ceil(GEODE_MAX_OUTER) + 1;

			for (int dx = -extent; dx <= extent; dx++) {
				for (int dy = -extent; dy <= extent; dy++) {
					for (int dz = -extent; dz <= extent; dz++) {
						double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

						if (distance > GEODE_MAX_OUTER) {
							continue;
						}

						// Направление задаёт небольшое искажение формы:
						// жеода не выглядит идеальным шаром.
						double nx = distance == 0.0D ? 0.0D : dx / distance;
						double ny = distance == 0.0D ? 0.0D : dy / distance;
						double nz = distance == 0.0D ? 0.0D : dz / distance;

						double external = geodeExternal(nx, ny, nz, phases);

						if (distance > external) {
							continue;
						}

						double basaltInner = external * GEODE_BASALT_FRACTION;
						double blackstoneInner = external * GEODE_BLACKSTONE_FRACTION;
						double rubyInner = external * GEODE_RUBY_FRACTION;

						int x = this.centerX + dx;
						int y = this.geodeCenterY + dy;
						int z = this.centerZ + dz;

						// Бедрок и жидкости не трогаем.
						BlockState current = level.getBlockState(new BlockPos(x, y, z));
						if (current.is(BlockTags.GEODE_INVALID_BLOCKS)) {
							continue;
						}

						BlockState state;

						if (distance > basaltInner) {
							state = smoothBasalt;
						} else if (distance > blackstoneInner) {
							state = blackstone;
						} else if (distance > rubyInner) {
							state = fragileRuby;
						} else {
							state = Blocks.AIR.defaultBlockState();
						}

						set(level, box, x, y, z, state);
					}
				}
			}
		}

		/**
		 * Выращивает редкие рубиновые кристаллы с внутренней стороны
		 * рубиновой оболочки. Позиции детерминированы, поэтому на
		 * границах чанков не появляются разрывы.
		 */
		private void decorateGeode(WorldGenLevel level, BoundingBox box) {
			double[] phases = geodePhases();
			RandomSource random = RandomSource.create(this.geodeSeed() ^ 0xC0FFEEL);
			BlockState fragileRuby = FirstMod.FRAGILE_RUBY_BLOCK.defaultBlockState();

			int extent = (int) Math.ceil(GEODE_MAX_OUTER) + 1;

			for (int dx = -extent; dx <= extent; dx++) {
				for (int dy = -extent; dy <= extent; dy++) {
					for (int dz = -extent; dz <= extent; dz++) {
						double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

						if (distance > GEODE_MAX_OUTER) {
							continue;
						}

						double nx = distance == 0.0D ? 0.0D : dx / distance;
						double ny = distance == 0.0D ? 0.0D : dy / distance;
						double nz = distance == 0.0D ? 0.0D : dz / distance;

						double external = geodeExternal(nx, ny, nz, phases);
						double rubyInner = external * GEODE_RUBY_FRACTION;
						double blackstoneInner = external * GEODE_BLACKSTONE_FRACTION;

						if (distance <= rubyInner || distance > blackstoneInner) {
							continue;
						}

						if (random.nextFloat() >= 0.09F) {
							continue;
						}

						for (int step = 1; step <= 2; step++) {
							int x = this.centerX + dx - (int) Math.round(nx * step);
							int y = this.geodeCenterY + dy - (int) Math.round(ny * step);
							int z = this.centerZ + dz - (int) Math.round(nz * step);

							BlockState current = level.getBlockState(new BlockPos(x, y, z));
							if (!current.isAir()) {
								break;
							}

							set(level, box, x, y, z, fragileRuby);
						}
					}
				}
			}
		}

		/**
		 * Ставит 1–3 Заражённых сундука в полости жеоды. Позиции
		 * выбираются только по геометрии жеоды, без опроса мира, чтобы
		 * результат не зависел от порядка генерации чанков.
		 */
		private void placeChests(WorldGenLevel level, BoundingBox box) {
			double[] phases = geodePhases();
			RandomSource random = RandomSource.create(this.geodeSeed() ^ 0x0C4E57L);
			int chestCount = 1 + random.nextInt(MAX_CHESTS);

			List<BlockPos> selected = new ArrayList<>();
			int attempts = 0;

			while (selected.size() < chestCount && attempts < 96) {
				attempts++;

				double theta = random.nextDouble() * Math.PI * 2.0D;
				double cosPhi = random.nextDouble() * 2.0D - 1.0D;
				double sinPhi = Math.sqrt(Math.max(0.0D, 1.0D - cosPhi * cosPhi));

				double nx = Math.cos(theta) * sinPhi;
				double ny = cosPhi;
				double nz = Math.sin(theta) * sinPhi;

				double external = geodeExternal(nx, ny, nz, phases);
				double cavity = external * GEODE_RUBY_FRACTION;
				double distance = cavity * (0.35D + random.nextDouble() * 0.35D);

				int dx = (int) Math.round(nx * distance);
				int dy = (int) Math.round(ny * distance);
				int dz = (int) Math.round(nz * distance);

				if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) < 2) {
					continue;
				}

				boolean tooClose = false;

				for (BlockPos other : selected) {
					int ox = other.getX() - this.centerX;
					int oy = other.getY() - this.geodeCenterY;
					int oz = other.getZ() - this.centerZ;

					if (Math.abs(ox - dx) <= 1 && Math.abs(oy - dy) <= 1 && Math.abs(oz - dz) <= 1) {
						tooClose = true;
						break;
					}
				}

				if (tooClose) {
					continue;
				}

				selected.add(new BlockPos(this.centerX + dx, this.geodeCenterY + dy, this.centerZ + dz));
			}

			for (BlockPos pos : selected) {
				int x = pos.getX();
				int y = pos.getY();
				int z = pos.getZ();

				if (!box.isInside(x, y, z)) {
					continue;
				}

				BlockState support = level.getBlockState(new BlockPos(x, y - 1, z));
				if (support.isAir() || !support.getFluidState().isEmpty()) {
					set(level, box, x, y - 1, z, Blocks.SMOOTH_BASALT.defaultBlockState());
				}

				clear(level, box, x, y + 1, z);

				Direction facing = Direction.from2DDataValue(Math.floorMod(x * 31 + z, 4));
				BlockState chestState = FirstMod.INFECTED_CHEST.defaultBlockState()
						.setValue(ChestBlock.FACING, facing)
						.setValue(ChestBlock.TYPE, ChestType.SINGLE)
						.setValue(ChestBlock.WATERLOGGED, false);

				set(level, box, x, y, z, chestState);

				BlockEntity blockEntity = level.getBlockEntity(pos);
				if (blockEntity instanceof InfectedChestBlockEntity chest) {
					chest.setLootTable(FirstMod.INFECTED_CHEST_LOOT, chestSeed(pos));
				}
			}
		}

		/**
		 * Ровно одна жила на видимом берегу. Угол выбирается зерном формы,
		 * а расстояние — по фактическому контуру озера. Жила целиком
		 * находится в камне ниже поверхности и никогда не выступает над
		 * землёй или над лавой.
		 */
		private void carveVein(WorldGenLevel level, BoundingBox box) {
			RandomSource random = RandomSource.create(this.shapeSeed() ^ 0x5EEDL);

			int angleIndex = random.nextInt(16);
			double angle = angleIndex * (Math.PI * 2.0D / 16.0D);
			double dirX = Math.cos(angle);
			double dirZ = Math.sin(angle);

			int offsetX = 0;
			int offsetZ = 0;

			for (int distance = 1; distance <= MAX_RADIUS + 7; distance++) {
				offsetX = (int) Math.round(dirX * distance);
				offsetZ = (int) Math.round(dirZ * distance);

				double actualDistance = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
				if (actualDistance > outlineRadius(offsetX, offsetZ) + 0.75D) {
					break;
				}
			}

			int x = this.centerX + offsetX;
			int z = this.centerZ + offsetZ;
			int topY = this.surfaceY - 2;
			int size = 6 + random.nextInt(4);

			int[] order = new int[VEIN_OFFSETS.length];
			for (int index = 0; index < order.length; index++) {
				order[index] = index;
			}
			for (int index = order.length - 1; index > 0; index--) {
				int swap = random.nextInt(index + 1);
				int temp = order[index];
				order[index] = order[swap];
				order[swap] = temp;
			}

			int placed = 0;

			for (int layer = 0; layer < 5 && placed < size; layer++) {
				for (int index = 0; index < order.length && placed < size; index++) {
					int[] offset = VEIN_OFFSETS[order[index]];
					int vx = x + offset[0];
					int vy = topY - layer + offset[1];
					int vz = z + offset[2];

					if (vy > this.surfaceY - 2) {
						continue;
					}
					if (Math.abs(vx - x) > 2 || Math.abs(vz - z) > 2) {
						continue;
					}
					if (vy < topY - 5) {
						continue;
					}

					BlockState current = level.getBlockState(new BlockPos(vx, vy, vz));
					if (!isVeinReplaceable(current)) {
						continue;
					}

					BlockState veinState = Math.abs(offset[0]) + Math.abs(offset[1]) + Math.abs(offset[2]) <= 1
							? FirstMod.FRAGILE_RUBY_BLOCK.defaultBlockState()
							: FirstMod.RUBY_ORE_BLOCK.defaultBlockState();

					set(level, box, vx, vy, vz, veinState);
					placed++;
				}
			}
		}

		private static boolean isVeinReplaceable(BlockState state) {
			return state.is(BlockTags.STONE_ORE_REPLACEABLES)
					|| state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
					|| state.is(Blocks.DIRT)
					|| state.is(Blocks.GRASS_BLOCK)
					|| state.is(Blocks.COARSE_DIRT)
					|| state.is(Blocks.PODZOL);
		}

		/**
		 * Радиус озера в направлении (dx, dz). Сумма синусов с фазами,
		 * выведенными из координат, даёт одну и ту же форму для всех
		 * чанков, в которые попадает структура.
		 */
		private double outlineRadius(int dx, int dz) {
			long seed = this.shapeSeed();

			double phase1 = ((seed >>> 8) & 0xFFFFL) / 65536.0D * Math.PI * 2.0D;
			double phase2 = ((seed >>> 24) & 0xFFFFL) / 65536.0D * Math.PI * 2.0D;
			double phase3 = ((seed >>> 40) & 0xFFFFL) / 65536.0D * Math.PI * 2.0D;

			double angle = Math.atan2(dz, dx);
			double wobble = 0.90D * Math.sin(angle * 3.0D + phase1)
					+ 0.50D * Math.sin(angle * 5.0D + phase2)
					+ 0.35D * Math.sin(angle * 2.0D + phase3)
					+ 0.25D * Math.sin(angle * 7.0D + phase1 * 0.5D);

			return Mth.clamp(this.radius + wobble, LAKE_MIN_OUTLINE, LAKE_MAX_OUTLINE);
		}

		private long shapeSeed() {
			long hash = this.centerX * 341873128712L
					+ this.centerZ * 132897987541L
					+ this.surfaceY * 42317861L
					+ this.radius * 17L;

			hash ^= hash >>> 33;
			hash *= 0xFF51AFD7ED558CCDL;
			hash ^= hash >>> 33;
			hash *= 0xC4CEB9FE1A85EC53L;
			hash ^= hash >>> 33;

			return hash;
		}

		/** Отдельное зерно для формы жеоды — она не должна зависеть от озера. */
		private long geodeSeed() {
			long hash = this.centerX * 132897987541L
					+ this.centerZ * 341873128712L
					+ this.surfaceY * 42317861L
					+ this.geodeRadius * 31L
					+ this.geodeCenterY * 17L;

			hash ^= hash >>> 33;
			hash *= 0xFF51AFD7ED558CCDL;
			hash ^= hash >>> 33;
			hash *= 0xC4CEB9FE1A85EC53L;
			hash ^= hash >>> 33;

			return hash;
		}

		private long chestSeed(BlockPos pos) {
			long hash = pos.asLong() ^ this.geodeSeed() ^ 0x9E3779B97F4A7C15L;

			hash ^= hash >>> 33;
			hash *= 0xFF51AFD7ED558CCDL;
			hash ^= hash >>> 33;
			hash *= 0xC4CEB9FE1A85EC53L;
			hash ^= hash >>> 33;

			return hash == 0L ? 1L : hash;
		}

		private double[] geodePhases() {
			long seed = this.geodeSeed();

			return new double[] {
					((seed >>> 8) & 0xFFFFL) / 65536.0D * Math.PI * 2.0D,
					((seed >>> 24) & 0xFFFFL) / 65536.0D * Math.PI * 2.0D,
					((seed >>> 40) & 0xFFFFL) / 65536.0D * Math.PI * 2.0D
			};
		}

		private double geodeExternal(double nx, double ny, double nz, double[] phases) {
			double wobble = 0.09D * Math.sin(nx * 3.7D + phases[0])
					+ 0.07D * Math.sin(ny * 4.9D + phases[1])
					+ 0.06D * Math.sin(nz * 3.1D + phases[2]);

			return Mth.clamp(
					this.geodeRadius * (1.0D + wobble),
					GEODE_MIN_OUTER,
					GEODE_MAX_OUTER
			);
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
