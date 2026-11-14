from gen_lib import *

TOOLS = ["axe", "hoe", "pickaxe", "sword", "shovel"]
ARMOR = ["helmet", "chestplate", "leggings", "boots"]
FLOWERS = [
    "dandelion", "open_eyeblossom", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip",
    "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley",
    "wither_rose", "torchflower", "closed_eyeblossom", "golden_dandelion", "sunflower", "lilac", "peony",
    "rose_bush", "pitcher_plant", "flowering_azalea_leaves", "flowering_azalea", "mangrove_propagule",
    "cherry_leaves", "pink_petals", "wildflowers", "chorus_flower", "spore_blossom", "cactus_flower",
]
DYES = [c + "_dye" for c in COLORS]
SAPLINGS = [
    "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling", "dark_oak_sapling",
    "pale_oak_sapling", "azalea", "flowering_azalea", "mangrove_propagule", "cherry_sapling", "poplar_sapling",
]
SEEDS = ["wheat_seeds", "melon_seeds", "pumpkin_seeds", "beetroot_seeds", "torchflower_seeds", "pitcher_pod"]
BUCKETS = [
    "bucket", "water_bucket", "lava_bucket", "milk_bucket", "powder_snow_bucket", "pufferfish_bucket",
    "salmon_bucket", "cod_bucket", "tropical_fish_bucket", "axolotl_bucket", "tadpole_bucket",
    "sulfur_cube_bucket",
]
WORKSTATIONS = [
    "barrel", "blast_furnace", "brewing_stand", "cartography_table", "cauldron", "composter",
    "fletching_table", "grindstone", "lectern", "loom", "smithing_table", "smoker", "stonecutter",
]
TRIMS = [
    "sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide", "snout", "rib", "spire", "wayfinder",
    "shaper", "silence", "raiser", "host", "flow", "bolt",
]
BANNER_PATTERNS = [
    "flower", "creeper", "skull", "mojang", "globe", "piglin", "flow", "guster", "field_masoned",
    "bordure_indented",
]
MUSIC_DISCS = [
    "13", "cat", "blocks", "chirp", "creator", "creator_music_box", "far", "lava_chicken", "mall", "mellohi",
    "stal", "strad", "ward", "11", "wait", "pigstep", "otherside", "5", "relic", "precipice", "tears",
]
SHERDS = [
    "angler", "archer", "arms_up", "blade", "brewer", "burn", "danger", "explorer", "flow", "friend", "guster",
    "heart", "heartbreak", "howl", "miner", "mourner", "plenty", "prize", "scrape", "sheaf", "shelter",
    "skull", "snort",
]
COPPER_CHESTS = [
    p + "copper_chest" for p in [
        "", "exposed_", "weathered_", "oxidized_", "waxed_", "waxed_exposed_", "waxed_weathered_",
        "waxed_oxidized_",
    ]
]


def register(g: Gen):
    # ---- tool sets (in inventory at once) ----
    tool_sets = [
        ("WOODEN", "wooden", "木质", "D"), ("STONE", "stone", "石质", "D"), ("IRON", "iron", "铁质", "C"),
        ("GOLDEN", "golden", "金质", "C"), ("DIAMOND", "diamond", "钻石", "A"), ("COPPER", "copper", "铜质", "C"),
    ]
    for key, mat, zh, tier in tool_sets:
        g.goal(f"OBTAIN_{key}_TOOLS", f"Obtain all {title(mat)} Tools", f"获得全套{zh}工具",
               all_of([f"{mat}_{t}" for t in TOOLS]), tier=tier, icon=f"{mat}_pickaxe", decoration="MULTI_ITEM")

    # ---- mining ----
    # Like Draftout, a block counts as soon as the player breaks it, with any tool (the vanilla "mined"
    # statistic only counts blocks broken with the correct tool, e.g. obsidian needs a diamond pickaxe).
    # Each goal is a hidden advancement whose criteria are the block ids; DraftoutTriggerController grants
    # the matching criterion when a playing player breaks the block.
    def mined(name, blocks):
        return advancement(g.adv(f"mine/{name}", {b: crit("impossible") for b in blocks}, [list(blocks)]))

    g.goal("MINE_DIAMOND_ORE", "Mine Diamond Ore", "挖掘钻石矿石",
           mined("diamond_ore", ["diamond_ore", "deepslate_diamond_ore"]), tier="B", icon="diamond_ore")
    g.goal("MINE_EMERALD_ORE", "Mine Emerald Ore", "挖掘绿宝石矿石",
           mined("emerald_ore", ["emerald_ore", "deepslate_emerald_ore"]), tier="B", icon="emerald_ore")
    g.goal("MINE_MOB_SPAWNER", "Mine Mob Spawner", "挖掘刷怪笼", mined("spawner", ["spawner"]), tier="B",
           icon="spawner")
    g.goal("MINE_TURTLE_EGG", "Mine Turtle Egg", "挖掘海龟蛋", mined("turtle_egg", ["turtle_egg"]), tier="B",
           icon="turtle_egg")
    g.goal("MINE_3_TYPES_OF_ICE", "Mine 3 types of Ice", "挖掘 3 种冰",
           some_of([mined(b, [b]) for b in ["ice", "packed_ice", "blue_ice", "frosted_ice"]], 3),
           tier="B", icon="blue_ice")
    g.goal("MINE_OBSIDIAN", "Mine Obsidian", "挖掘黑曜石", mined("obsidian", ["obsidian"]), tier="D",
           icon="obsidian")
    g.goal("MINE_CRAFTER", "Mine Crafter", "挖掘合成器", mined("crafter", ["crafter"]), tier="C", icon="crafter")
    g.goal("MINE_SEA_LANTERN", "Mine Sea Lantern", "挖掘海晶灯", mined("sea_lantern", ["sea_lantern"]), tier="B",
           icon="sea_lantern")

    # ---- single items ----
    singles = [
        ("OBTAIN_RED_NETHER_BRICK_STAIRS", "red_nether_brick_stairs", "Red Nether Brick Stairs", "红色下界砖楼梯", "B"),
        ("OBTAIN_TROPICAL_FISH_BUCKET", "tropical_fish_bucket", "Bucket of Tropical Fish", "热带鱼桶", "C"),
        ("OBTAIN_SULFUR_CUBE_BUCKET", "sulfur_cube_bucket", "Bucket of Sulfur Cube", "硫磺方块怪桶", "B"),
        ("OBTAIN_BOOKSHELF", "bookshelf", "Bookshelf", "书架", "C"),
        ("OBTAIN_MOSSY_STONE_BRICK_WALL", "mossy_stone_brick_wall", "Mossy Stone Brick Wall", "苔石砖墙", "C"),
        ("OBTAIN_FLOWERING_AZALEA", "flowering_azalea", "Flowering Azalea", "盛开的杜鹃花丛", "C"),
        ("OBTAIN_SCAFFOLDING", "scaffolding", "Scaffolding", "脚手架", "C"),
        ("OBTAIN_BELL", "bell", "Bell", "钟", "C"),
        ("OBTAIN_ENCHANT_BOTTLE", "experience_bottle", "Bottle o' Enchanting", "附魔之瓶", "B"),
        ("OBTAIN_POWDER_SNOW_BUCKET", "powder_snow_bucket", "Powder Snow Bucket", "细雪桶", "C"),
        ("OBTAIN_SOUL_LANTERN", "soul_lantern", "Soul Lantern", "灵魂灯笼", "C"),
        ("OBTAIN_ANCIENT_DEBRIS", "ancient_debris", "Ancient Debris", "远古残骸", "A"),
        ("OBTAIN_NETHERITE_SCRAP", "netherite_scrap", "Netherite Scrap", "下界合金碎片", "A"),
        ("OBTAIN_AMETHYST_BLOCK", "amethyst_block", "Block of Amethyst", "紫水晶块", "C"),
        ("OBTAIN_ENDER_CHEST", "ender_chest", "Ender Chest", "末影箱", "B"),
        ("OBTAIN_HEART_OF_THE_SEA", "heart_of_the_sea", "Heart of the Sea", "海洋之心", "B"),
        ("OBTAIN_WITHER_SKELETON_SKULL", "wither_skeleton_skull", "Wither Skeleton Skull", "凋灵骷髅头颅", "A"),
        ("OBTAIN_END_ROD", "end_rod", "End Rod", "末地烛", "S"),
        ("OBTAIN_SPONGE", "sponge", "Sponge", "海绵", "A"),
        ("OBTAIN_DRAGON_EGG", "dragon_egg", "Dragon Egg", "龙蛋", "S"),
        ("OBTAIN_TNT", "tnt", "TNT", "TNT", "C"),
        ("OBTAIN_COBWEB", "cobweb", "Cobweb", "蜘蛛网", "C"),
        ("OBTAIN_MUD_BRICK_WALL", "mud_brick_wall", "Mud Brick Wall", "泥砖墙", "C"),
        ("OBTAIN_DAYLIGHT_DETECTOR", "daylight_detector", "Daylight Detector", "阳光探测器", "B"),
        ("OBTAIN_REDSTONE_REPEATER", "repeater", "Redstone Repeater", "红石中继器", "D"),
        ("OBTAIN_RESIN_BLOCK", "resin_block", "Block of Resin", "树脂块", "B"),
        ("OBTAIN_RESIN_BRICK_WALL", "resin_brick_wall", "Resin Brick Wall", "树脂砖墙", "B"),
        ("OBTAIN_REDSTONE_COMPARATOR", "comparator", "Redstone Comparator", "红石比较器", "B"),
        ("OBTAIN_OBSERVER", "observer", "Observer", "侦测器", "B"),
        ("OBTAIN_SMOOTH_QUARTZ_STAIRS", "smooth_quartz_stairs", "Smooth Quartz Stairs", "平滑石英楼梯", "B"),
        ("OBTAIN_ACTIVATOR_RAIL", "activator_rail", "Activator Rail", "激活铁轨", "C"),
        ("OBTAIN_DETECTOR_RAIL", "detector_rail", "Detector Rail", "探测铁轨", "C"),
        ("OBTAIN_POWERED_RAIL", "powered_rail", "Powered Rail", "动力铁轨", "B"),
        ("OBTAIN_DISPENSER", "dispenser", "Dispenser", "发射器", "C"),
        ("OBTAIN_PISTON", "piston", "Piston", "活塞", "D"),
        ("OBTAIN_COAL_BLOCK", "coal_block", "Block of Coal", "煤炭块", "D"),
        ("OBTAIN_WRITTEN_BOOK", "written_book", "Written Book (sign a Book & Quill)", "成书（书与笔署名后）", "C"),
        ("OBTAIN_TINTED_GLASS", "tinted_glass", "Tinted Glass", "遮光玻璃", "B"),
        ("OBTAIN_LODESTONE", "lodestone", "Lodestone", "磁石", "B"),
        ("OBTAIN_EMERALD_BLOCK", "emerald_block", "Block of Emerald", "绿宝石块", "A"),
        ("OBTAIN_DRIED_KELP_BLOCK", "dried_kelp_block", "Dried Kelp Block", "干海带块", "C"),
        ("OBTAIN_DEAD_BUSH", "dead_bush", "Dead Bush", "枯萎的灌木", "C"),
        ("OBTAIN_CLOCK", "clock", "Clock", "时钟", "D"),
        ("OBTAIN_REDSTONE_LAMP", "redstone_lamp", "Redstone Lamp", "红石灯", "B"),
        ("OBTAIN_SOUL_CAMPFIRE", "soul_campfire", "Soul Campfire", "灵魂营火", "C"),
        ("OBTAIN_BRICK_WALL", "brick_wall", "Brick Wall", "砖墙", "C"),
        ("OBTAIN_GILDED_BLACKSTONE", "gilded_blackstone", "Gilded Blackstone", "镶金黑石", "A"),
        ("OBTAIN_SMOOTH_BASALT", "smooth_basalt", "Smooth Basalt", "平滑玄武岩", "C"),
        ("OBTAIN_BONE_BLOCK", "bone_block", "Bone Block", "骨块", "D"),
        ("OBTAIN_STRAW_BED", "straw_bed", "Straw Bed", "稻草床", "C"),
        ("OBTAIN_BAMBOO_MOSAIC_STAIRS", "bamboo_mosaic_stairs", "Bamboo Mosaic Stairs", "竹马赛克楼梯", "B"),
    ]
    for key, i, name, zh, tier in singles:
        g.goal(key, f"Obtain {name}", f"获得{zh}", item(i), tier=tier)

    g.goal("OBTAIN_COPPER_CHEST", "Obtain Copper Chest", "获得铜箱子", one_of(COPPER_CHESTS),
           tier="C", icon="copper_chest")
    g.goal("OBTAIN_SUSPICIOUS_BLOCK", "Obtain Suspicious Block", "获得可疑的方块",
           one_of(["suspicious_sand", "suspicious_gravel"]), tier="B", icon="suspicious_sand")
    g.goal("OBTAIN_POTTERY_SHERD", "Obtain Pottery Sherd", "获得陶片",
           one_of([s + "_pottery_sherd" for s in SHERDS]), tier="B", icon="archer_pottery_sherd")

    # ---- "every type of" (all at once) ----
    g.goal("OBTAIN_ALL_RAW_ORE_BLOCKS", "Obtain every type of Raw Ore Block", "获得所有粗矿块",
           all_of(["raw_copper_block", "raw_gold_block", "raw_iron_block"]), tier="A", icon="raw_gold_block",
           decoration="MULTI_ITEM")
    g.goal("OBTAIN_ALL_TORCHES", "Obtain every type of Torch", "获得所有火把",
           all_of(["torch", "redstone_torch", "soul_torch", "copper_torch"]), tier="C", icon="soul_torch",
           decoration="MULTI_ITEM")
    g.goal("OBTAIN_ALL_MINECARTS", "Obtain every type of Minecart", "获得所有矿车",
           all_of(["minecart", "chest_minecart", "furnace_minecart", "hopper_minecart", "tnt_minecart"]),
           tier="A", icon="tnt_minecart", decoration="MULTI_ITEM")
    g.goal("OBTAIN_ALL_FURNACES", "Obtain every type of Furnace", "获得所有熔炉",
           all_of(["furnace", "blast_furnace", "smoker"]), tier="C", icon="blast_furnace",
           decoration="MULTI_ITEM")
    g.goal("OBTAIN_ALL_PUMPKINS", "Obtain every type of Pumpkin", "获得所有南瓜",
           all_of(["pumpkin", "carved_pumpkin", "jack_o_lantern"]), tier="C", icon="jack_o_lantern",
           decoration="MULTI_ITEM")

    # ---- "N unique" (at once) ----
    g.goal("OBTAIN_3_HORSE_ARMORS", "Obtain 3 types of Horse Armor", "获得 3 种马铠",
           some_of(["leather_horse_armor", "copper_horse_armor", "iron_horse_armor", "golden_horse_armor",
                    "diamond_horse_armor", "netherite_horse_armor"], 3), tier="A", icon="golden_horse_armor")
    g.goal("OBTAIN_4_UNIQUE_SEEDS", "Obtain 4 Unique Seeds", "获得 4 种种子", some_of(SEEDS, 4),
           tier="C", icon="wheat_seeds")
    g.goal("OBTAIN_6_UNIQUE_FLOWERS", "Obtain 6 Unique Flowers", "获得 6 种花", some_of(FLOWERS, 6),
           tier="C", icon="poppy")
    g.goal("OBTAIN_4_UNIQUE_SAPLINGS", "Obtain 4 Unique Saplings", "获得 4 种树苗", some_of(SAPLINGS, 4),
           tier="C", icon="cherry_sapling")
    g.goal("OBTAIN_6_UNIQUE_BUCKETS", "Obtain 6 Unique Buckets", "获得 6 种桶", some_of(BUCKETS, 6),
           tier="B", icon="water_bucket")
    g.goal("OBTAIN_ALL_MUSHROOMS", "Obtain 4 Unique Mushrooms", "获得 4 种蘑菇",
           some_of(["red_mushroom", "brown_mushroom", "crimson_fungus", "warped_fungus", "shelf_mushroom"], 4),
           tier="C", icon="red_mushroom")
    g.goal("OBTAIN_7_UNIQUE_WORKSTATIONS", "Obtain 7 Unique Workstations", "获得 7 种村民工作站方块",
           some_of(WORKSTATIONS, 7), tier="B", icon="smithing_table")
    g.goal("OBTAIN_2_ARMOR_TRIMS", "Obtain 2 Unique Armor Trims", "获得 2 种盔甲纹饰锻造模板",
           some_of([t + "_armor_trim_smithing_template" for t in TRIMS], 2), tier="A",
           icon="coast_armor_trim_smithing_template")
    g.goal("OBTAIN_3_BANNER_PATTERNS", "Obtain 3 Unique Banner Patterns", "获得 3 种旗帜图案",
           some_of([p + "_banner_pattern" for p in BANNER_PATTERNS], 3), tier="A", icon="flower_banner_pattern")
    g.goal("OBTAIN_12_UNIQUE_DYES", "Obtain 12 Unique Dyes", "获得 12 种染料", some_of(DYES, 12),
           tier="B", icon="lime_dye")
    g.goal("OBTAIN_3_UNIQUE_MUSIC_DISCS", "Obtain 3 Unique Music Discs", "获得 3 种唱片",
           some_of(["music_disc_" + d for d in MUSIC_DISCS], 3), tier="S", icon="music_disc_cat")

    # ---- stacks ----
    g.goal("OBTAIN_32_ARROWS", "Obtain 32 Arrows", "获得 32 支箭",
           one_of([item("arrow", 32), item("spectral_arrow", 32)]), tier="C", icon="arrow")
    g.goal("OBTAIN_64_ARROWS", "Obtain 64 Arrows", "获得 64 支箭", item("arrow", 64), tier="B")
    g.goal("OBTAIN_16_GLOW_LICHEN", "Obtain 16 Glow Lichen", "获得 16 个发光地衣", item("glow_lichen", 16),
           tier="C")
    g.goal("OBTAIN_64_COARSE_DIRT", "Obtain 64 Coarse Dirt", "获得 64 个砂土", item("coarse_dirt", 64), tier="C")
    g.goal("OBTAIN_64_NAME_TAGS", "Obtain 64 Name Tags", "获得 64 个命名牌", item("name_tag", 64), tier="S")
    g.goal("OBTAIN_64_OF_ANY_FLOWER", "Obtain 64 of any Flower", "获得 64 朵同种花",
           one_of([item(f, 64) for f in FLOWERS]), tier="C", icon="dandelion")
    g.goal("FILL_INVENTORY_MUSHROOM_STEW", "Fill Inventory with Mushroom Stew", "用蘑菇煲填满背包",
           item("mushroom_stew", 36), tier="B")

    # ---- color variants (only one variant of each group may appear) ----
    for key, base, name, zh, count, tier in [
        ("OBTAIN_COLORED_GLAZED_TERRACOTTA", "glazed_terracotta", "Glazed Terracotta", "带釉陶瓦", 1, "C"),
        ("OBTAIN_64_COLORED_WOOL", "wool", "Wool", "羊毛", 64, "C"),
        ("OBTAIN_64_COLORED_CONCRETE", "concrete", "Concrete", "混凝土", 64, "B"),
        ("OBTAIN_COLORED_HARNESS", "harness", "Harness", "挽具", 1, "B"),
    ]:
        grp = g.group()
        for c in COLORS:
            n = f"Obtain {'64 ' if count > 1 else ''}{title(c)} {name}"
            z = f"获得{'64 个' if count > 1 else ''}{COLOR_ZH[c]}{zh}"
            g.goal(f"{key}__{c}", n, z, item(f"{c}_{base}", count), tier=tier, group=grp)
