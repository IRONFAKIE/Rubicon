package ru.school.firstmod;

import java.util.Set;

import com.mojang.serialization.Codec;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityType;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FirstMod implements ModInitializer {
	public static final String MOD_ID = "first-mod";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Максимум делений шкалы «Душевного переполнения». */
	public static final int SOUL_OVERFLOW_MAX = 6;

	// ------------------------------------------------------------------
	// Компоненты
	// ------------------------------------------------------------------

	/** Этап порчи рубина (1–5). */
	public static final DataComponentType<Integer> RUBY_STAGE = registerRubyStage();

	// ------------------------------------------------------------------
	// Сущности
	// ------------------------------------------------------------------

	public static final ResourceKey<EntityType<?>> SOUL_STRIDER_SPIRIT_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, id("soul_strider_spirit"));

	public static final EntityType<SoulStriderSpirit> SOUL_STRIDER_SPIRIT = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			SOUL_STRIDER_SPIRIT_KEY,
			EntityType.Builder.of(SoulStriderSpirit::new, MobCategory.CREATURE)
					.sized(0.9F, 1.7F)
					.clientTrackingRange(10)
					.build(SOUL_STRIDER_SPIRIT_KEY)
	);

	public static final ResourceKey<EntityType<?>> WANDERING_SOUL_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, id("wandering_soul"));

	public static final EntityType<WanderingSoul> WANDERING_SOUL = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			WANDERING_SOUL_KEY,
			EntityType.Builder.of(WanderingSoul::new, MobCategory.MONSTER)
					.sized(0.4F, 0.8F)
					.clientTrackingRange(8)
					.build(WANDERING_SOUL_KEY)
	);

	// ------------------------------------------------------------------
	// Состояние игрока
	// ------------------------------------------------------------------

	/**
	 * «Душевное переполнение»: 0–6 делений.
	 *
	 * <p>Значение хранится на игроке, переживает смерть и синхронизируется
	 * на клиент, чтобы отрисовывать полосу над шкалой опыта.</p>
	 */
	public static final AttachmentType<Integer> SOUL_OVERFLOW = AttachmentRegistry.<Integer>create(
			id("soul_overflow"),
			builder -> builder
					.initializer(() -> 0)
					.persistent(Codec.INT)
					.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
					.copyOnDeath()
	);

	/**
	 * Игровое время, до которого цель должна гореть синим огнём душ.
	 *
	 * <p>Значение не сохраняется на диск (это кратковременный визуальный
	 * эффект), но синхронизируется всем клиентам, чтобы огонь видели все
	 * игроки рядом.</p>
	 */
	public static final AttachmentType<Long> SOUL_FIRE_UNTIL = AttachmentRegistry.<Long>create(
			id("soul_fire_until"),
			builder -> builder
					.initializer(() -> 0L)
					.syncWith(ByteBufCodecs.LONG, AttachmentSyncPredicate.all())
	);

	// ------------------------------------------------------------------
	// Предметы
	// ------------------------------------------------------------------

	public static final ResourceKey<Item> MAGIC_RUBY_KEY = itemKey("magic_ruby");
	public static final Item MAGIC_RUBY = registerRuby(MAGIC_RUBY_KEY, MagicRubyItem.Kind.BASE);

	public static final ResourceKey<Item> PROTECTED_RUBY_KEY = itemKey("protected_ruby");
	public static final Item PROTECTED_RUBY = registerRuby(PROTECTED_RUBY_KEY, MagicRubyItem.Kind.PROTECTED);

	public static final ResourceKey<Item> ADVANCED_PROTECTED_RUBY_KEY = itemKey("advanced_protected_ruby");
	public static final Item ADVANCED_PROTECTED_RUBY = registerRuby(
			ADVANCED_PROTECTED_RUBY_KEY,
			MagicRubyItem.Kind.ADVANCED
	);

	public static final ResourceKey<Item> CROWN_KEY = itemKey("crown");
	public static final Item CROWN = Registry.register(
			BuiltInRegistries.ITEM,
			CROWN_KEY,
			new Item(new Item.Properties().setId(CROWN_KEY).stacksTo(1))
	);

	public static final ResourceKey<Item> ADVANCED_CROWN_KEY = itemKey("advanced_crown");
	public static final Item ADVANCED_CROWN = Registry.register(
			BuiltInRegistries.ITEM,
			ADVANCED_CROWN_KEY,
			new Item(new Item.Properties().setId(ADVANCED_CROWN_KEY).stacksTo(1))
	);

	/** Серый рубин — визуальное обозначение рубина на перезарядке. */
	public static final ResourceKey<Item> GREY_RUBY_KEY = itemKey("grey_ruby");
	public static final Item GREY_RUBY = Registry.register(
			BuiltInRegistries.ITEM,
			GREY_RUBY_KEY,
			new Item(new Item.Properties().setId(GREY_RUBY_KEY))
	);

	/** Тонированная бутылка — ловушка для Духа Лавомерки. */
	public static final ResourceKey<Item> TINTED_BOTTLE_KEY = itemKey("tinted_bottle");
	public static final Item TINTED_BOTTLE = Registry.register(
			BuiltInRegistries.ITEM,
			TINTED_BOTTLE_KEY,
			new Item(new Item.Properties().setId(TINTED_BOTTLE_KEY).stacksTo(16))
	);

	/**
	 * Бутылка с душой.
	 *
	 * <p>Служит топливом: горит как уголь и вдвое ускоряет плавку.
	 * Выпивается за стандартные 1,6 секунды (32 тика) и только по
	 * завершении питья даёт деление «Душевного переполнения» и Тьму.</p>
	 */
	public static final ResourceKey<Item> BOTTLE_OF_SOUL_KEY = itemKey("bottle_of_soul");
	public static final Item BOTTLE_OF_SOUL = Registry.register(
			BuiltInRegistries.ITEM,
			BOTTLE_OF_SOUL_KEY,
			new BottleOfSoulItem(new Item.Properties()
					.setId(BOTTLE_OF_SOUL_KEY)
					.stacksTo(16)
					.component(
							DataComponents.COOKING_FUEL,
							new CookingFuel(
									ResolvableInt.fromKey(ContextIntProviders.COOKING_TIME_COAL),
									ResolvableFloat.fromKey(ContextFloatProviders.COOKING_FAST_SPEED_MULTIPLIER)
							)
					)
					.component(
							DataComponents.CONSUMABLE,
							Consumable.builder()
									.consumeSeconds(1.6F)
									.animation(ItemUseAnimation.DRINK)
									.sound(SoundEvents.GENERIC_DRINK)
									.hasConsumeParticles(false)
									.onConsume(new ApplyStatusEffectsConsumeEffect(
											new MobEffectInstance(MobEffects.DARKNESS, 60, 0)
									))
									.build()
					))
	);

	/** Стальной слиток — результат застывания расплава в форме. */
	public static final ResourceKey<Item> STEEL_INGOT_KEY = itemKey("steel_ingot");
	public static final Item STEEL_INGOT = registerSimpleItem(STEEL_INGOT_KEY);

	// Расплавы минералов.
	public static final Item LAPIS_MELT = registerMelt("lapis_melt");
	public static final Item COAL_MELT = registerMelt("coal_melt");
	public static final Item REDSTONE_MELT = registerMelt("redstone_melt");
	public static final Item EMERALD_MELT = registerMelt("emerald_melt");
	public static final Item IRON_MELT = registerMelt("iron_melt");
	public static final Item GOLD_MELT = registerMelt("gold_melt");
	public static final Item DIAMOND_MELT = registerMelt("diamond_melt");
	public static final Item NETHERITE_MELT = registerMelt("netherite_melt");
	public static final Item RUBY_MELT = registerMelt("ruby_melt");
	public static final Item STEEL_MELT = registerMelt("steel_melt");

	/**
	 * Все предметы-расплавы.
	 *
	 * <p>Печь выдаёт их только тогда, когда топливом реально горит
	 * «Бутылка с душой». Объявляется после предметов, чтобы порядок
	 * статической инициализации был корректным.</p>
	 */
	private static final Set<Item> MELT_ITEMS = Set.of(
			LAPIS_MELT,
			COAL_MELT,
			REDSTONE_MELT,
			EMERALD_MELT,
			IRON_MELT,
			GOLD_MELT,
			DIAMOND_MELT,
			NETHERITE_MELT,
			RUBY_MELT,
			STEEL_MELT
	);

	/** Яйцо призыва Духа Лавомерки. */
	public static final ResourceKey<Item> SOUL_STRIDER_SPIRIT_SPAWN_EGG_KEY =
			itemKey("soul_strider_spirit_spawn_egg");

	public static final Item SOUL_STRIDER_SPIRIT_SPAWN_EGG = Registry.register(
			BuiltInRegistries.ITEM,
			SOUL_STRIDER_SPIRIT_SPAWN_EGG_KEY,
			new SpawnEggItem(new Item.Properties()
					.setId(SOUL_STRIDER_SPIRIT_SPAWN_EGG_KEY)
					.component(
							DataComponents.ENTITY_DATA,
							TypedEntityData.<EntityType<?>>of(SOUL_STRIDER_SPIRIT, new CompoundTag())
					))
	);

	// ------------------------------------------------------------------
	// Блок руды
	// ------------------------------------------------------------------

	public static final ResourceKey<Block> RUBY_ORE_BLOCK_KEY =
			ResourceKey.create(Registries.BLOCK, id("ruby_ore_block"));

	public static final Block RUBY_ORE_BLOCK = Registry.register(
			BuiltInRegistries.BLOCK,
			RUBY_ORE_BLOCK_KEY,
			new RubyOreBlock(BlockBehaviour.Properties.of()
					.setId(RUBY_ORE_BLOCK_KEY)
					.mapColor(MapColor.DIRT)
					.strength(0.6F)
					.sound(SoundType.GRAVEL)
					.requiresCorrectToolForDrops())
	);

	public static final ResourceKey<Item> RUBY_ORE_BLOCK_ITEM_KEY = itemKey("ruby_ore_block");

	public static final Item RUBY_ORE_BLOCK_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			RUBY_ORE_BLOCK_ITEM_KEY,
			new BlockItem(RUBY_ORE_BLOCK, new Item.Properties().setId(RUBY_ORE_BLOCK_ITEM_KEY))
	);

	// ------------------------------------------------------------------
	// Хрупкий рубин (блок рубиновой жеоды)
	// ------------------------------------------------------------------

	/**
	 * Хрупкий рубин — внутренний слой рубиновой жеоды.
	 *
	 * <p>{@code noLootTable()} гарантирует, что блок не выпадает ни при
	 * обычной добыче, ни при шёлковом касании.</p>
	 */
	public static final ResourceKey<Block> FRAGILE_RUBY_BLOCK_KEY =
			ResourceKey.create(Registries.BLOCK, id("fragile_ruby_block"));

	public static final Block FRAGILE_RUBY_BLOCK = Registry.register(
			BuiltInRegistries.BLOCK,
			FRAGILE_RUBY_BLOCK_KEY,
			new Block(BlockBehaviour.Properties.of()
					.setId(FRAGILE_RUBY_BLOCK_KEY)
					.mapColor(MapColor.COLOR_PURPLE)
					.strength(0.5F)
					.sound(SoundType.AMETHYST)
					.noLootTable())
	);

	public static final ResourceKey<Item> FRAGILE_RUBY_BLOCK_ITEM_KEY = itemKey("fragile_ruby_block");

	public static final Item FRAGILE_RUBY_BLOCK_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			FRAGILE_RUBY_BLOCK_ITEM_KEY,
			new BlockItem(FRAGILE_RUBY_BLOCK, new Item.Properties().setId(FRAGILE_RUBY_BLOCK_ITEM_KEY))
	);

	// ------------------------------------------------------------------
	// Рубиновый блок
	// ------------------------------------------------------------------

	public static final ResourceKey<Block> RUBY_BLOCK_KEY =
			ResourceKey.create(Registries.BLOCK, id("ruby_block"));

	public static final Block RUBY_BLOCK = Registry.register(
			BuiltInRegistries.BLOCK,
			RUBY_BLOCK_KEY,
			new Block(BlockBehaviour.Properties.of()
					.setId(RUBY_BLOCK_KEY)
					.mapColor(MapColor.COLOR_RED)
					.strength(5.0F, 6.0F)
					.sound(SoundType.METAL)
					.requiresCorrectToolForDrops())
	);

	public static final ResourceKey<Item> RUBY_BLOCK_ITEM_KEY = itemKey("ruby_block");

	public static final Item RUBY_BLOCK_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			RUBY_BLOCK_ITEM_KEY,
			new BlockItem(RUBY_BLOCK, new Item.Properties().setId(RUBY_BLOCK_ITEM_KEY))
	);

	// ------------------------------------------------------------------
	// Литейная форма
	// ------------------------------------------------------------------

	public static final ResourceKey<Block> CASTING_MOLD_KEY =
			ResourceKey.create(Registries.BLOCK, id("casting_mold"));

	public static final CastingMoldBlock CASTING_MOLD = Registry.register(
			BuiltInRegistries.BLOCK,
			CASTING_MOLD_KEY,
			new CastingMoldBlock(BlockBehaviour.Properties.of()
					.setId(CASTING_MOLD_KEY)
					.mapColor(MapColor.COLOR_ORANGE)
					.strength(0.8F)
					.sound(SoundType.WOOD)
					.noOcclusion())
	);

	public static final ResourceKey<Item> CASTING_MOLD_ITEM_KEY = itemKey("casting_mold");

	public static final Item CASTING_MOLD_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			CASTING_MOLD_ITEM_KEY,
			new CastingMoldBlockItem(CASTING_MOLD, new Item.Properties().setId(CASTING_MOLD_ITEM_KEY))
	);

	// ------------------------------------------------------------------
	// Заражённый сундук
	// ------------------------------------------------------------------

	/** Лут-таблица Заражённого сундука. */
	public static final ResourceKey<LootTable> INFECTED_CHEST_LOOT =
			ResourceKey.create(Registries.LOOT_TABLE, id("chests/infected_chest"));

	public static final ResourceKey<Block> INFECTED_CHEST_KEY =
			ResourceKey.create(Registries.BLOCK, id("infected_chest"));

	public static final InfectedChestBlock INFECTED_CHEST = Registry.register(
			BuiltInRegistries.BLOCK,
			INFECTED_CHEST_KEY,
			new InfectedChestBlock(BlockBehaviour.Properties.of()
					.setId(INFECTED_CHEST_KEY)
					.mapColor(MapColor.COLOR_RED)
					.strength(2.5F)
					.sound(SoundType.WOOD)
					.requiresCorrectToolForDrops()
					.pushReaction(PushReaction.IMMOVEABLE)
					.explosionResistance(1200.0F))
	);

	// ------------------------------------------------------------------
	// Приёмник активатора
	// ------------------------------------------------------------------

	public static final ResourceKey<Block> ACTIVATOR_RECEIVER_KEY =
			ResourceKey.create(Registries.BLOCK, id("activator_receiver"));

	public static final ActivatorReceiverBlock ACTIVATOR_RECEIVER = Registry.register(
			BuiltInRegistries.BLOCK,
			ACTIVATOR_RECEIVER_KEY,
			new ActivatorReceiverBlock(BlockBehaviour.Properties.of()
					.setId(ACTIVATOR_RECEIVER_KEY)
					.mapColor(MapColor.METAL)
					.strength(3.5F)
					.sound(SoundType.METAL)
					.requiresCorrectToolForDrops())
	);

	// ------------------------------------------------------------------
	// Стальной блок
	// ------------------------------------------------------------------

	public static final ResourceKey<Block> STEEL_BLOCK_KEY =
			ResourceKey.create(Registries.BLOCK, id("steel_block"));

	public static final Block STEEL_BLOCK = Registry.register(
			BuiltInRegistries.BLOCK,
			STEEL_BLOCK_KEY,
			new Block(BlockBehaviour.Properties.of()
					.setId(STEEL_BLOCK_KEY)
					.mapColor(MapColor.METAL)
					.strength(5.0F, 6.0F)
					.sound(SoundType.METAL)
					.requiresCorrectToolForDrops())
	);

	// ------------------------------------------------------------------
	// Блок-сущности
	// ------------------------------------------------------------------

	public static final BlockEntityType<InfectedChestBlockEntity> INFECTED_CHEST_BE = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			id("infected_chest"),
			new BlockEntityType<>(
					InfectedChestBlockEntity::new,
					Set.of(INFECTED_CHEST)
			)
	);

	public static final BlockEntityType<ActivatorReceiverBlockEntity> ACTIVATOR_RECEIVER_BE = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			id("activator_receiver"),
			new BlockEntityType<>(
					ActivatorReceiverBlockEntity::new,
					Set.of(ACTIVATOR_RECEIVER)
			)
	);

	// ------------------------------------------------------------------
	// Предметы новых блоков и активатор
	// ------------------------------------------------------------------

	public static final ResourceKey<Item> INFECTED_CHEST_ITEM_KEY = itemKey("infected_chest");

	public static final Item INFECTED_CHEST_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			INFECTED_CHEST_ITEM_KEY,
			new BlockItem(INFECTED_CHEST, new Item.Properties().setId(INFECTED_CHEST_ITEM_KEY))
	);

	public static final ResourceKey<Item> ACTIVATOR_RECEIVER_ITEM_KEY = itemKey("activator_receiver");

	public static final Item ACTIVATOR_RECEIVER_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			ACTIVATOR_RECEIVER_ITEM_KEY,
			new BlockItem(ACTIVATOR_RECEIVER, new Item.Properties().setId(ACTIVATOR_RECEIVER_ITEM_KEY))
	);

	public static final ResourceKey<Item> STEEL_BLOCK_ITEM_KEY = itemKey("steel_block");

	public static final Item STEEL_BLOCK_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			STEEL_BLOCK_ITEM_KEY,
			new BlockItem(STEEL_BLOCK, new Item.Properties().setId(STEEL_BLOCK_ITEM_KEY))
	);

	public static final ResourceKey<Item> ACTIVATOR_KEY = itemKey("activator");

	public static final Item ACTIVATOR = Registry.register(
			BuiltInRegistries.ITEM,
			ACTIVATOR_KEY,
			new ActivatorItem(false, new Item.Properties().setId(ACTIVATOR_KEY).stacksTo(1))
	);

	public static final ResourceKey<Item> ACTIVATED_ACTIVATOR_KEY = itemKey("activated_activator");

	public static final Item ACTIVATED_ACTIVATOR = Registry.register(
			BuiltInRegistries.ITEM,
			ACTIVATED_ACTIVATOR_KEY,
			new ActivatorItem(true, new Item.Properties().setId(ACTIVATED_ACTIVATOR_KEY).stacksTo(1))
	);

	// ------------------------------------------------------------------
	// Генерация
	// ------------------------------------------------------------------

	/** Лут-таблица, которая выдаётся при очистке руды кисточкой. */
	public static final ResourceKey<LootTable> RUBY_ORE_BRUSH_LOOT =
			ResourceKey.create(Registries.LOOT_TABLE, id("archaeology/ruby_ore_block"));

	/** Размещённая фича генерации рубиновой жилы под поверхностной лавой. */
	public static final ResourceKey<PlacedFeature> RUBY_VEIN_PLACED_FEATURE =
			ResourceKey.create(Registries.PLACED_FEATURE, id("ruby_vein"));

	/** Тип структуры «озеро Лавы Душ». */
	public static final StructureType<SoulLavaLakeStructure> SOUL_LAVA_LAKE_STRUCTURE = Registry.register(
			BuiltInRegistries.STRUCTURE_TYPE,
			id("soul_lava_lake"),
			() -> SoulLavaLakeStructure.CODEC
	);

	/** Тип куска структуры «озеро Лавы Душ». */
	public static final StructurePieceType SOUL_LAVA_LAKE_PIECE = Registry.register(
			BuiltInRegistries.STRUCTURE_PIECE,
			id("soul_lava_lake"),
			(StructurePieceType.ContextlessType) SoulLavaLakeStructure.SoulLavaLakePiece::new
	);

	/** Тип структуры «рубиновое лавовое озеро» Верхнего мира. */
	public static final StructureType<RubyLavaLakeStructure> RUBY_LAVA_LAKE_STRUCTURE = Registry.register(
			BuiltInRegistries.STRUCTURE_TYPE,
			id("ruby_lava_lake"),
			() -> RubyLavaLakeStructure.CODEC
	);

	/** Тип куска структуры «рубиновое лавовое озеро». */
	public static final StructurePieceType RUBY_LAVA_LAKE_PIECE = Registry.register(
			BuiltInRegistries.STRUCTURE_PIECE,
			id("ruby_lava_lake"),
			(StructurePieceType.ContextlessType) RubyLavaLakeStructure.RubyLavaLakePiece::new
	);

	// ------------------------------------------------------------------
	// Вкладка креатива
	// ------------------------------------------------------------------

	public static final ResourceKey<CreativeModeTab> RUBY_TAB_KEY =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("magic_ruby"));

	public static final CreativeModeTab RUBY_TAB = Registry.register(
			BuiltInRegistries.CREATIVE_MODE_TAB,
			RUBY_TAB_KEY,
			FabricCreativeModeTab.builder()
					.title(Component.translatable("itemGroup.first-mod.magic_ruby"))
					.icon(() -> new ItemStack(MAGIC_RUBY))
					.displayItems((parameters, output) -> {
						output.accept(MAGIC_RUBY);
						output.accept(GREY_RUBY);
						output.accept(PROTECTED_RUBY);
						output.accept(ADVANCED_PROTECTED_RUBY);
						output.accept(CROWN);
						output.accept(ADVANCED_CROWN);
						output.accept(RUBY_ORE_BLOCK_ITEM);
						output.accept(FRAGILE_RUBY_BLOCK_ITEM);
						output.accept(RUBY_BLOCK_ITEM);
						output.accept(STEEL_BLOCK_ITEM);
						output.accept(INFECTED_CHEST_ITEM);
						output.accept(ACTIVATOR_RECEIVER_ITEM);
						output.accept(ACTIVATOR);
						output.accept(ACTIVATED_ACTIVATOR);
						output.accept(TINTED_BOTTLE);
						output.accept(BOTTLE_OF_SOUL);
						output.accept(STEEL_INGOT);
						output.accept(CASTING_MOLD_ITEM);
						output.accept(LAPIS_MELT);
						output.accept(COAL_MELT);
						output.accept(REDSTONE_MELT);
						output.accept(EMERALD_MELT);
						output.accept(IRON_MELT);
						output.accept(GOLD_MELT);
						output.accept(DIAMOND_MELT);
						output.accept(NETHERITE_MELT);
						output.accept(RUBY_MELT);
						output.accept(STEEL_MELT);
						output.accept(SOUL_STRIDER_SPIRIT_SPAWN_EGG);
					})
					.build()
	);

	@Override
	public void onInitialize() {
		FirstModConfig.load();

		// Кисточка работает только с BrushableBlockEntity,
		// поэтому разрешаем стандартному блоку-сущности работать с нашим блоком.
		((FabricBlockEntityType) BlockEntityTypes.BRUSHABLE_BLOCK).addValidBlock(RUBY_ORE_BLOCK);

		// Жеода создаётся только структурой озера, без отдельной
		// глобальной фичи: иначе под озером появлялись бы две жеоды.

		// Рубиновая жила начинается ровно на один блок ниже поверхностной лавы.
		BiomeModifications.addFeature(
				BiomeSelectors.foundInOverworld(),
				GenerationStep.Decoration.UNDERGROUND_ORES,
				RUBY_VEIN_PLACED_FEATURE
		);

		// Дух Лавомерки: атрибуты, спавн только в лаве Долины песка душ.
		FabricDefaultAttributeRegistry.register(SOUL_STRIDER_SPIRIT, SoulStriderSpirit.createAttributes());
		SpawnPlacements.register(
				SOUL_STRIDER_SPIRIT,
				SpawnPlacementTypes.IN_LAVA,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				SoulStriderSpirit::checkSpawnRules
		);
		BiomeModifications.addSpawn(
				BiomeSelectors.includeByKey(Biomes.SOUL_SAND_VALLEY),
				MobCategory.CREATURE,
				SOUL_STRIDER_SPIRIT,
				8,
				1,
				2
		);

		// Блуждающая душа призывается только «Душевным переполнением».
		FabricDefaultAttributeRegistry.register(WANDERING_SOUL, Vex.createAttributes());

		SoulLakeSpiritSpawner.init();
		SoulOverflowHandler.init();
		SpiritSpawnEggHandler.register();
		InfectedChestProtection.init();

		registerCreativeTabEntries();
		registerCommands();
	}

	private static void registerCreativeTabEntries() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(MAGIC_RUBY);
			output.accept(GREY_RUBY);
			output.accept(PROTECTED_RUBY);
			output.accept(ADVANCED_PROTECTED_RUBY);
			output.accept(CROWN);
			output.accept(ADVANCED_CROWN);
			output.accept(RUBY_ORE_BLOCK_ITEM);
			output.accept(FRAGILE_RUBY_BLOCK_ITEM);
			output.accept(RUBY_BLOCK_ITEM);
			output.accept(STEEL_BLOCK_ITEM);
			output.accept(INFECTED_CHEST_ITEM);
			output.accept(ACTIVATOR_RECEIVER_ITEM);
			output.accept(ACTIVATOR);
			output.accept(ACTIVATED_ACTIVATOR);
			output.accept(TINTED_BOTTLE);
			output.accept(BOTTLE_OF_SOUL);
			output.accept(STEEL_INGOT);
			output.accept(CASTING_MOLD_ITEM);
			output.accept(LAPIS_MELT);
			output.accept(COAL_MELT);
			output.accept(REDSTONE_MELT);
			output.accept(EMERALD_MELT);
			output.accept(IRON_MELT);
			output.accept(GOLD_MELT);
			output.accept(DIAMOND_MELT);
			output.accept(NETHERITE_MELT);
			output.accept(RUBY_MELT);
			output.accept(STEEL_MELT);
			output.accept(SOUL_STRIDER_SPIRIT_SPAWN_EGG);
		});

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> {
			output.accept(STEEL_BLOCK_ITEM);
		});

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(INFECTED_CHEST_ITEM);
			output.accept(ACTIVATOR_RECEIVER_ITEM);
		});
	}

	private static void registerCommands() {
		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> dispatcher.register(
						Commands.literal("hello").executes(context -> {
							String message = "Привет от нашего Java-мода!";
							context.getSource().sendSuccess(() -> Component.literal(message), false);
							return 1;
						})
				)
		);
	}

	// ------------------------------------------------------------------
	// Хелперы
	// ------------------------------------------------------------------

	private static DataComponentType<Integer> registerRubyStage() {
		DataComponentType<Integer> type = DataComponentType.<Integer>builder()
				.persistent(Codec.intRange(1, 5))
				.networkSynchronized(ByteBufCodecs.VAR_INT)
				.build();

		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("ruby_stage"), type);
		return type;
	}

	private static Item registerRuby(ResourceKey<Item> key, MagicRubyItem.Kind kind) {
		Item.Properties properties = new Item.Properties()
				.setId(key)
				.durability(kind.maxDurability())
				.component(RUBY_STAGE, 1);

		// Улучшенный рубин можно чинить незеритовым ломом на наковальне.
		if (kind == MagicRubyItem.Kind.ADVANCED) {
			properties.repairable(Items.NETHERITE_SCRAP);
		}

		return Registry.register(BuiltInRegistries.ITEM, key, new MagicRubyItem(kind, properties));
	}

	private static Item registerSimpleItem(ResourceKey<Item> key) {
		return Registry.register(
				BuiltInRegistries.ITEM,
				key,
				new Item(new Item.Properties().setId(key))
		);
	}

	private static Item registerMelt(String path) {
		ResourceKey<Item> key = itemKey(path);
		return Registry.register(
				BuiltInRegistries.ITEM,
				key,
				new Item(new Item.Properties().setId(key).stacksTo(16))
		);
	}

	private static ResourceKey<Item> itemKey(String path) {
		return ResourceKey.create(Registries.ITEM, id(path));
	}

	/** Является ли стопка одним из предметов-расплавов. */
	public static boolean isMelt(ItemStack stack) {
		return !stack.isEmpty() && MELT_ITEMS.contains(stack.getItem());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
