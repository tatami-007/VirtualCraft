package dev.tatami.virtualcraft.mining;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import dev.tatami.virtualcraft.registry.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public record MiningSurveyTarget(String key, Component name, ItemStack defaultIcon, List<TagKey<Block>> tags, List<Block> fallbackBlocks) {
	private static volatile List<MiningSurveyTarget> cachedAll;
	private static volatile List<MiningSurveyTarget> cachedAvailable;

	private static final List<MiningSurveyTarget> TARGETS = List.of(
			new MiningSurveyTarget("all", Component.translatable("survey.virtual_craft.target.all"), new ItemStack(Blocks.DIAMOND_ORE), List.of(cOre("ores")), vanillaOres()),
			target("electric_ore", Component.translatable("survey.virtual_craft.target.electric_ore"), new ItemStack(ModBlocks.ELECTRIC_ORE.get()), "electric", List.of(ModBlocks.ELECTRIC_ORE.get(), ModBlocks.DEEPSLATE_ELECTRIC_ORE.get())),
			target("coal", Component.translatable("survey.virtual_craft.target.coal"), new ItemStack(Blocks.COAL_ORE), "coal", List.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE)),
			target("iron", Component.translatable("survey.virtual_craft.target.iron"), new ItemStack(Blocks.IRON_ORE), "iron", List.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE)),
			target("copper", Component.translatable("survey.virtual_craft.target.copper"), new ItemStack(Blocks.COPPER_ORE), "copper", List.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE)),
			target("gold", Component.translatable("survey.virtual_craft.target.gold"), new ItemStack(Blocks.GOLD_ORE), "gold", List.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE)),
			target("redstone", Component.translatable("survey.virtual_craft.target.redstone"), new ItemStack(Blocks.REDSTONE_ORE), "redstone", List.of(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE)),
			target("lapis", Component.translatable("survey.virtual_craft.target.lapis"), new ItemStack(Blocks.LAPIS_ORE), "lapis", List.of(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE)),
			target("diamond", Component.translatable("survey.virtual_craft.target.diamond"), new ItemStack(Blocks.DIAMOND_ORE), "diamond", List.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE)),
			target("emerald", Component.translatable("survey.virtual_craft.target.emerald"), new ItemStack(Blocks.EMERALD_ORE), "emerald", List.of(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE)),
			target("quartz", Component.translatable("survey.virtual_craft.target.quartz"), new ItemStack(Blocks.NETHER_QUARTZ_ORE), "quartz", List.of(Blocks.NETHER_QUARTZ_ORE)),
			target("ancient_debris", Component.translatable("survey.virtual_craft.target.ancient_debris"), new ItemStack(Blocks.ANCIENT_DEBRIS), "ancient_debris", List.of(Blocks.ANCIENT_DEBRIS)),
			target("tin", Component.translatable("survey.virtual_craft.target.tin"), ItemStack.EMPTY, "tin", List.of()),
			target("lead", Component.translatable("survey.virtual_craft.target.lead"), ItemStack.EMPTY, "lead", List.of()),
			target("silver", Component.translatable("survey.virtual_craft.target.silver"), ItemStack.EMPTY, "silver", List.of()),
			target("nickel", Component.translatable("survey.virtual_craft.target.nickel"), ItemStack.EMPTY, "nickel", List.of()),
			target("uranium", Component.translatable("survey.virtual_craft.target.uranium"), ItemStack.EMPTY, "uranium", List.of()),
			target("osmium", Component.translatable("survey.virtual_craft.target.osmium"), ItemStack.EMPTY, "osmium", List.of()),
			target("zinc", Component.translatable("survey.virtual_craft.target.zinc"), ItemStack.EMPTY, "zinc", List.of()),
			target("aluminum", Component.translatable("survey.virtual_craft.target.aluminum"), ItemStack.EMPTY, "aluminum", List.of()),
			target("platinum", Component.translatable("survey.virtual_craft.target.platinum"), ItemStack.EMPTY, "platinum", List.of()),
			target("iridium", Component.translatable("survey.virtual_craft.target.iridium"), ItemStack.EMPTY, "iridium", List.of()),
			target("apatite", Component.translatable("survey.virtual_craft.target.apatite"), ItemStack.EMPTY, "apatite", List.of()),
			target("sulfur", Component.translatable("survey.virtual_craft.target.sulfur"), ItemStack.EMPTY, "sulfur", List.of()));

	public boolean matches(BlockState state) {
		for (TagKey<Block> tag : tags) {
			if (state.is(tag)) {
				return true;
			}
		}
		for (Block block : fallbackBlocks) {
			if (state.is(block)) {
				return true;
			}
		}
		return false;
	}

	public boolean isAvailable() {
		if (!fallbackBlocks.isEmpty()) {
			return true;
		}
		return BuiltInRegistries.BLOCK.stream().anyMatch(block -> matches(block.defaultBlockState()));
	}

	public ItemStack icon() {
		if (!defaultIcon.isEmpty()) {
			return defaultIcon;
		}
		return BuiltInRegistries.BLOCK.stream()
				.filter(block -> matches(block.defaultBlockState()))
				.findFirst()
				.map(block -> new ItemStack(block.asItem()))
				.filter(stack -> !stack.isEmpty())
				.orElse(new ItemStack(Blocks.STONE));
	}

	public static List<MiningSurveyTarget> all() {
		List<MiningSurveyTarget> cached = cachedAll;
		if (cached == null) {
			cached = withDynamicTargets(false);
			cachedAll = cached;
		}
		return cached;
	}

	public static List<MiningSurveyTarget> available() {
		List<MiningSurveyTarget> cached = cachedAvailable;
		if (cached == null) {
			cached = withDynamicTargets(true);
			cachedAvailable = cached;
		}
		return cached;
	}

	public static MiningSurveyTarget defaultTarget() {
		return TARGETS.getFirst();
	}

	public static Optional<MiningSurveyTarget> byKey(String key) {
		return all().stream().filter(target -> target.key.equals(key)).findFirst();
	}

	private static MiningSurveyTarget target(String key, Component name, ItemStack icon, String oreTag, List<Block> fallbackBlocks) {
		return new MiningSurveyTarget(key, name, icon, List.of(cOre("ores/" + oreTag)), fallbackBlocks);
	}

	private static TagKey<Block> cOre(String path) {
		return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", path));
	}

	private static List<Block> vanillaOres() {
		return List.of(
				ModBlocks.ELECTRIC_ORE.get(),
				ModBlocks.DEEPSLATE_ELECTRIC_ORE.get(),
				Blocks.COAL_ORE,
				Blocks.DEEPSLATE_COAL_ORE,
				Blocks.IRON_ORE,
				Blocks.DEEPSLATE_IRON_ORE,
				Blocks.COPPER_ORE,
				Blocks.DEEPSLATE_COPPER_ORE,
				Blocks.GOLD_ORE,
				Blocks.DEEPSLATE_GOLD_ORE,
				Blocks.NETHER_GOLD_ORE,
				Blocks.REDSTONE_ORE,
				Blocks.DEEPSLATE_REDSTONE_ORE,
				Blocks.LAPIS_ORE,
				Blocks.DEEPSLATE_LAPIS_ORE,
				Blocks.DIAMOND_ORE,
				Blocks.DEEPSLATE_DIAMOND_ORE,
				Blocks.EMERALD_ORE,
				Blocks.DEEPSLATE_EMERALD_ORE,
				Blocks.NETHER_QUARTZ_ORE,
				Blocks.ANCIENT_DEBRIS);
	}

	private static List<MiningSurveyTarget> withDynamicTargets(boolean onlyAvailable) {
		Map<String, MiningSurveyTarget> targets = new LinkedHashMap<>();
		for (MiningSurveyTarget target : TARGETS) {
			if (!onlyAvailable || target.isAvailable()) {
				targets.put(target.key(), target);
			}
		}
		for (MiningSurveyTarget target : dynamicOreTargets()) {
			if (!onlyAvailable || target.isAvailable()) {
				targets.putIfAbsent(target.key(), target);
			}
		}
		return List.copyOf(targets.values());
	}

	private static List<MiningSurveyTarget> dynamicOreTargets() {
		Map<TagKey<Block>, Block> taggedOres = new LinkedHashMap<>();
		Set<TagKey<Block>> builtInTags = TARGETS.stream()
				.flatMap(target -> target.tags().stream())
				.collect(Collectors.toUnmodifiableSet());
		BuiltInRegistries.BLOCK.stream().forEach(block -> block.builtInRegistryHolder().tags()
				.filter(MiningSurveyTarget::isOreTag)
				.forEach(tag -> taggedOres.putIfAbsent(tag, block)));
		return taggedOres.entrySet().stream()
				.filter(entry -> !builtInTags.contains(entry.getKey()))
				.map(entry -> dynamicTarget(entry.getKey(), entry.getValue()))
				.toList();
	}

	private static MiningSurveyTarget dynamicTarget(TagKey<Block> tag, Block iconBlock) {
		ItemStack icon = new ItemStack(iconBlock.asItem());
		Component name = icon.isEmpty() ? Component.literal(prettyOreName(tag.location().getPath())) : icon.getHoverName();
		return new MiningSurveyTarget("tag:" + tag.location(), name, icon, List.of(tag), List.of(iconBlock));
	}

	private static boolean isOreTag(TagKey<Block> tag) {
		ResourceLocation id = tag.location();
		return "c".equals(id.getNamespace()) && id.getPath().startsWith("ores/") && id.getPath().length() > "ores/".length();
	}

	private static String prettyOreName(String path) {
		String raw = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
		StringBuilder builder = new StringBuilder(raw.length());
		boolean upper = true;
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			builder.append(upper ? Character.toUpperCase(c) : c);
			upper = c == ' ';
		}
		return builder.toString();
	}
}
