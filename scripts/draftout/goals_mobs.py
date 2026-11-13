from gen_lib import *

HOSTILE = [
    "blaze", "bogged", "breeze", "cave_spider", "creaking", "creeper", "drowned", "elder_guardian",
    "ender_dragon", "endermite", "evoker", "ghast", "guardian", "hoglin", "husk", "magma_cube", "parched",
    "phantom", "piglin_brute", "pillager", "ravager", "shulker", "silverfish", "skeleton", "slime", "spider",
    "stray", "vex", "vindicator", "warden", "witch", "wither", "wither_skeleton", "zoglin", "zombie",
    "zombie_villager", "sulfur_cube",
]
BREEDABLE = [
    "armadillo", "axolotl", "bee", "camel", "cat", "chicken", "cow", "donkey", "fox", "frog", "goat",
    "hoglin", "horse", "llama", "mooshroom", "ocelot", "panda", "pig", "rabbit", "sheep", "sniffer",
    "strider", "turtle", "wolf", "nautilus",
]
FOODS_COMPOSTABLE = [
    "apple", "baked_potato", "beetroot", "bread", "carrot", "cookie", "dried_kelp", "glow_berries",
    "melon_slice", "potato", "pumpkin_pie", "sweet_berries",
]
EFFECTS = [
    "speed", "slowness", "haste", "mining_fatigue", "strength", "instant_health", "instant_damage",
    "jump_boost", "nausea", "regeneration", "resistance", "fire_resistance", "water_breathing",
    "invisibility", "blindness", "night_vision", "hunger", "weakness", "poison", "wither", "health_boost",
    "absorption", "saturation", "glowing", "levitation", "luck", "unluck", "slow_falling",
    "conduit_power", "dolphins_grace", "bad_omen", "hero_of_the_village", "darkness", "trial_omen",
    "raid_omen", "wind_charged", "weaving", "oozing", "infested", "breath_of_the_nautilus",
]


def register(g: Gen, spy_mobs):
    # ---- taming ----
    for key, mobs, name, zh, icon, tier in [
        ("TAME_CAT", ["cat"], "Cat", "猫", "cod", "C"),
        ("TAME_PARROT", ["parrot"], "Parrot", "鹦鹉", "wheat_seeds", "B"),
        ("TAME_HORSE", ["horse", "zombie_horse"], "Horse", "马", "saddle", "C"),
        ("TAME_WOLF", ["wolf"], "Wolf", "狼", "bone", "C"),
        ("TAME_NAUTILUS", ["nautilus"], "Nautilus", "鹦鹉螺", "nautilus_shell", "B"),
    ]:
        adv = g.adv_any(key.lower(), [crit("tame_animal", entity={"type": mc(m)}) for m in mobs])
        g.goal(key, f"Tame a {name}", f"驯服{zh}", advancement(adv), tier=tier, icon=icon,
               decoration="ADVANCEMENT")

    # ---- breeding ----
    for mob, zh, icon, tier in [
        ("chicken", "鸡", "wheat_seeds", "D"), ("cow", "牛", "wheat", "D"), ("hoglin", "疣猪兽", "crimson_fungus", "B"),
        ("pig", "猪", "carrot", "D"), ("camel", "骆驼", "cactus", "B"), ("armadillo", "犰狳", "spider_eye", "C"),
        ("rabbit", "兔子", "dandelion", "C"), ("fox", "狐狸", "sweet_berries", "B"), ("sheep", "羊", "wheat", "D"),
        ("strider", "炽足兽", "warped_fungus", "B"), ("goat", "山羊", "wheat", "C"), ("frog", "青蛙", "slime_ball", "B"),
    ]:
        key = "BREED_FROGS" if mob == "frog" else f"BREED_{mob.upper()}"
        adv = g.adv(key.lower(), {"bred": crit("bred_animals", child={"type": mc(mob)})})
        g.goal(key, f"Breed {title(mob)}s", f"繁殖{zh}", advancement(adv), tier=tier, icon=icon,
               decoration="ADVANCEMENT")
    # unique breeding: criteria per animal, counted by DraftoutTriggerController -> scoreboard
    g.adv_counter("counter/bred", {m: crit("bred_animals", child={"type": mc(m)}) for m in BREEDABLE})
    for n, tier in [(4, "C"), (6, "B"), (8, "A")]:
        g.goal(f"BREED_{n}_UNIQUE_ANIMALS", f"Breed {n} Unique Animals", f"繁殖 {n} 种动物",
               scoreboard("draftout_bred", n), tier=tier, icon="wheat", decoration="ADVANCEMENT")

    g.goal("DUPLICATE_ALLAY", "Duplicate Allay", "复制悦灵",
           advancement(g.adv("duplicate_allay", {"c": crit(
               "player_interacted_with_entity", item=items_pred("amethyst_shard"),
               entity={"type": "minecraft:allay"})})), tier="A", icon="amethyst_shard", decoration="ADVANCEMENT")

    # ---- kills ----
    for mob, zh, tier in [
        ("witch", "女巫", "B"), ("bogged", "沼骸", "B"), ("parched", "焦骸", "B"), ("husk", "尸壳", "C"),
        ("zombie_villager", "僵尸村民", "C"), ("stray", "流浪者", "C"), ("zoglin", "僵尸疣猪兽", "A"),
        ("silverfish", "蠹虫", "B"), ("guardian", "守卫者", "B"), ("ghast", "恶魂", "B"), ("bat", "蝙蝠", "C"),
        ("snow_golem", "雪傀儡", "C"), ("elder_guardian", "远古守卫者", "S"), ("warden", "监守者", "S"),
    ]:
        g.goal(f"KILL_{mob.upper()}", f"Kill {title(mob)}", f"击杀{zh}", stat("killed", mob), tier=tier,
               icon=f"{mob}_spawn_egg", decoration="ADVANCEMENT")
    g.goal("KILL_SNOW_GOLEM_IN_NETHER", "Kill Snow Golem in The Nether", "在下界击杀雪傀儡",
           advancement(g.adv("kill_snow_golem_in_nether", {"c": crit(
               "player_killed_entity", entity={"type": "minecraft:snow_golem"},
               player={"location": {"dimension": "minecraft:the_nether"}})})),
           tier="B", icon="snow_golem_spawn_egg", decoration="ADVANCEMENT")
    g.goal("KILL_PILLAGER_USING_CROSSBOW", "Kill Pillager using Crossbow", "用弩击杀掠夺者",
           advancement("minecraft:adventure/whos_the_pillager_now"), tier="B", icon="crossbow",
           decoration="ADVANCEMENT")
    g.goal("KILL_BREEZE_USING_WIND_CHARGE", "Kill Breeze using Wind Charge", "用风弹击杀旋风人",
           advancement(g.adv("kill_breeze_wind_charge", {"c": crit(
               "player_killed_entity", entity={"type": "minecraft:breeze"},
               killing_blow={"direct_entity": {"type": "minecraft:wind_charge"}})})),
           tier="A", icon="wind_charge", decoration="ADVANCEMENT")
    grp = g.group()
    for c in COLORS:
        adv = g.adv(f"kill_sheep/{c}", {"c": crit(
            "player_killed_entity", entity={"type": "minecraft:sheep", "components": {"minecraft:sheep/color": c}})})
        g.goal(f"KILL_COLORED_SHEEP__{c}", f"Kill {title(c)} Sheep", f"击杀{COLOR_ZH[c]}羊", advancement(adv),
               tier="C", icon=f"{c}_wool", decoration="ADVANCEMENT", group=grp)
    hostile_kills = [stat("killed", m) for m in HOSTILE]
    for n, tier in [(7, "C"), (10, "B"), (13, "A"), (15, "S")]:
        g.goal(f"KILL_{n}_UNIQUE_HOSTILE_MOBS", f"Kill {n} Unique Hostile Mobs", f"击杀 {n} 种敌对生物",
               some_of(hostile_kills, n), tier=tier, icon="iron_sword", decoration="ADVANCEMENT")
    g.goal("KILL_30_UNDEAD_MOBS", "Kill 30 Undead Mobs", "击杀 30 只亡灵生物", scoreboard("draftout_undead", 30),
           tier="B", icon="zombie_head", decoration="ADVANCEMENT")
    g.goal("KILL_20_ARTHROPOD_MOBS", "Kill 20 Arthropods", "击杀 20 只节肢生物",
           scoreboard("draftout_arthropod", 20), tier="B", icon="spider_eye", decoration="ADVANCEMENT")
    g.goal("KILL_100_MOBS", "Kill 100 Mobs", "击杀 100 只生物", stat("custom", "mob_kills", 100), tier="A",
           icon="diamond_sword", decoration="ADVANCEMENT")
    g.goal("KILL_ALL_RAID_MOBS", "Kill all Raid Mobs", "击杀所有袭击生物",
           all_of([stat("killed", m) for m in ["pillager", "vindicator", "ravager", "witch", "vex", "evoker"]]),
           tier="S", icon="ominous_bottle", decoration="ADVANCEMENT")

    # ---- deaths (granted by DraftoutTriggerController) ----
    deaths = [
        ("DIE_BY_ANVIL", "Die to falling Anvil", "被下落的铁砧砸死", "anvil", "B"),
        ("DIE_BY_BEE_STING", "Die to Bee Sting", "被蜜蜂蛰死", "bee_nest", "B"),
        ("DIE_BY_BERRY_BUSH", "Die to Berry Bush", "被甜浆果丛扎死", "sweet_berries", "B"),
        ("DIE_BY_CACTUS", "Die to Cactus", "被仙人掌扎死", "cactus", "C"),
        ("DIE_BY_FALLING_OFF_VINE", "Die by falling off Vines", "从藤蔓上摔死", "vine", "B"),
        ("DIE_BY_FALLING_STALACTITE", "Die to Falling Stalactite", "被下落的钟乳石砸死", "pointed_dripstone", "B"),
        ("DIE_BY_FIREWORK", "Die to Firework Rocket", "被烟花火箭炸死", "firework_rocket", "B"),
        ("DIE_BY_INTENTIONAL_GAME_DESIGN", "Die to [Intentional Game Design]", "死于[刻意的游戏设计]", "red_bed", "B"),
        ("DIE_BY_IRON_GOLEM", "Die to Iron Golem", "被铁傀儡杀死", "iron_block", "B"),
        ("DIE_BY_DROWNING", "Die by Drowning", "淹死", "water_bucket", "C"),
        ("DIE_TO_POLAR_BEAR", "Die to Polar Bear", "被北极熊杀死", "polar_bear_spawn_egg", "B"),
        ("DIE_BY_MAGIC", "Die by Magic", "被魔法杀死", "splash_potion", "B"),
        ("DIE_BY_TNT_MINECART", "Die to TNT Minecart", "被 TNT 矿车炸死", "tnt_minecart", "A"),
        ("FREEZE_TO_DEATH", "Die by Freezing", "冻死", "powder_snow_bucket", "B"),
        ("DIE_TO_PUFFERFISH", "Die to Pufferfish", "被河豚杀死", "pufferfish", "B"),
        ("DIE_TO_TRIDENT", "Die to Trident", "被三叉戟杀死", "trident", "A"),
        ("DIE_TO_WARDEN", "Die to Warden", "被监守者杀死", "sculk_shrieker", "A"),
        ("DIE_TO_VOID", "Die by falling in Void", "掉入虚空而死", "end_portal_frame", "B"),
        ("DIE_TO_SULFUR_CUBE", "Die to Sulfur Cube", "被硫磺方块怪杀死", "sulfur_cube_spawn_egg", "B"),
    ]
    for key, name, zh, icon, tier in deaths:
        adv = g.adv_impossible("death/" + key.lower())
        g.goal(key, name, zh, advancement(adv), tier=tier, icon=icon, image=f"{NS}:skull_cross",
               decoration="ADVANCEMENT")

    # ---- status effects ----
    for eff, zh, icon, tier in [
        ("absorption", "伤害吸收", "golden_apple", "C"), ("bad_omen", "不祥之兆", "ominous_bottle", "B"),
        ("glowing", "发光", "spectral_arrow", "B"), ("jump_boost", "跳跃提升", "rabbit_foot", "B"),
        ("levitation", "飘浮", "shulker_shell", "A"), ("mining_fatigue", "挖掘疲劳", "prismarine_shard", "B"),
        ("nausea", "反胃", "pufferfish", "C"), ("poison", "中毒", "spider_eye", "C"),
        ("weakness", "虚弱", "fermented_spider_eye", "B"),
    ]:
        key = f"GET_{eff.upper()}_STATUS_EFFECT"
        adv = g.adv("effect/" + eff, {"c": crit("effects_changed", player={"effects": {mc(eff): {}}})})
        g.goal(key, f"Get {title(eff)}", f"获得{zh}效果", advancement(adv), tier=tier, icon=icon,
               decoration="ADVANCEMENT")
    milk = {e: crit("consume_item", item=items_pred("milk_bucket"), player={"effects": {mc(e): {}}})
            for e in EFFECTS}
    g.goal("REMOVE_STATUS_EFFECT_USING_MILK", "Remove Effect using Milk Bucket", "用牛奶桶清除状态效果",
           advancement(g.adv("milk_effect", milk, [list(milk.keys())])), tier="C", icon="milk_bucket",
           decoration="ADVANCEMENT")
    for n, tier in [(3, "C"), (4, "B"), (6, "A")]:
        g.goal(f"GET_{n}_STATUS_EFFECTS_AT_ONCE", f"Get {n} Status Effects at once", f"同时拥有 {n} 种状态效果",
               scoreboard("draftout_effects", n), tier=tier, icon="potion", decoration="ADVANCEMENT")

    # ---- spyglass ----
    for mob, zh, tier in [("enderman", "末影人", "C"), ("iron_golem", "铁傀儡", "C"), ("piglin_brute", "猪灵蛮兵", "A")]:
        adv = g.adv("spy/" + mob, {"c": crit("using_item", item=items_pred("spyglass"),
                                             player={"type_specific": {"type": "minecraft:player",
                                                                       "looking_at": {"type": mc(mob)}}})})
        g.goal(f"SPY_{mob.upper()}", f"Spy on {title(mob)}", f"用望远镜观察{zh}", advancement(adv), tier=tier,
               icon="spyglass", decoration="ADVANCEMENT")
    spy = {m.split(":")[1]: crit("using_item", item=items_pred("spyglass"),
                                 player={"type_specific": {"type": "minecraft:player", "looking_at": {"type": m}}})
           for m in spy_mobs}
    g.adv_counter("counter/spy", spy)
    for n, tier in [(10, "C"), (15, "B"), (20, "A"), (25, "S")]:
        g.goal(f"SPY_{n}_UNIQUE_MOBS", f"Spy on {n} Unique Mobs", f"用望远镜观察 {n} 种生物",
               scoreboard("draftout_spy", n), tier=tier, icon="spyglass", decoration="ADVANCEMENT")

    # ---- leash ----
    g.goal("LEASH_IRON_GOLEM", "Leash an Iron Golem", "拴住铁傀儡",
           advancement(g.adv_impossible("leash_iron_golem")), tier="B", icon="lead", decoration="ADVANCEMENT")
    for n, tier in [(4, "C"), (6, "B"), (8, "A")]:
        g.goal(f"LEASH_{n}_UNIQUE_MOBS", f"Leash {n} unique Mobs at once", f"同时拴住 {n} 种生物",
               scoreboard("draftout_leash", n), tier=tier, icon="lead", decoration="ADVANCEMENT")

    # ---- riding ----
    g.goal("RIDE_HORSE", "Ride a Horse", "骑马", stat("custom", "horse_one_cm"), tier="C", icon="saddle",
           decoration="ADVANCEMENT")
    g.goal("RIDE_MINECART", "Ride a Minecart", "乘坐矿车", stat("custom", "minecart_one_cm"), tier="D",
           icon="minecart", decoration="ADVANCEMENT")
    g.goal("RIDE_PIG", "Ride Pig with Carrot on a Stick", "用胡萝卜钓竿骑猪", stat("used", "carrot_on_a_stick"),
           tier="C", icon="carrot_on_a_stick", decoration="ADVANCEMENT")
    g.goal("RIDE_GEYSER", "Ride a Geyser", "被间歇泉弹飞", advancement(g.adv_impossible("ride_geyser")),
           tier="B", icon="sulfur_spike", decoration="ADVANCEMENT")
    g.goal("SIT_ON_COLORED_CUSHION", "Sit on a Cushion", "坐在坐垫上",
           advancement(g.adv("sit_cushion", {"c": crit("tick", player={"vehicle": {"type": "minecraft:cushion"}})})),
           tier="C", icon="red_cushion", decoration="ADVANCEMENT")

    # ---- mob tricks ----
    g.goal("ENRAGE_ZOMBIFIED_PIGLIN", "Enrage Zombified Piglin", "激怒僵尸猪灵",
           advancement(g.adv_impossible("enrage_zombified_piglin")), tier="C", icon="golden_sword",
           decoration="ADVANCEMENT")
    g.goal("ENRAGE_ENDERMAN", "Enrage Enderman", "激怒末影人", advancement(g.adv_impossible("enrage_enderman")),
           tier="C", icon="ender_pearl", decoration="ADVANCEMENT")
    for key, mob, name, zh, nm in [
        ("TURN_IRON_GOLEM_UPSIDE_DOWN", "iron_golem", "Name an Iron Golem 'Dinnerbone'", "把铁傀儡命名为 Dinnerbone", "dinnerbone"),
        ("TURN_GHAST_UPSIDE_DOWN", "ghast", "Name a Ghast 'Dinnerbone'", "把恶魂命名为 Dinnerbone", "dinnerbone"),
        ("NAME_SHEEP_JEB", "sheep", "Name a Sheep 'jeb_'", "把羊命名为 jeb_", "jeb"),
    ]:
        g.goal(key, name, zh, advancement(g.adv_impossible(f"name_{mob}_{nm}")), tier="B", icon="name_tag",
               decoration="ADVANCEMENT")
    g.goal("GIVE_DIAMOND_TO_FOX", "Give a Diamond to a Fox", "给狐狸一颗钻石",
           advancement(g.adv("diamond_fox", {"c": crit("thrown_item_picked_up_by_entity", item=items_pred("diamond"),
                                                       entity={"type": "minecraft:fox"})})),
           tier="B", icon="diamond", decoration="ADVANCEMENT")
    g.goal("USE_GOLDEN_DANDELION", "Feed Golden Dandelion to any Baby Mob", "给幼年生物喂金蒲公英",
           advancement(g.adv("golden_dandelion", {"c": crit(
               "player_interacted_with_entity", item=items_pred("golden_dandelion"),
               entity={"flags": {"is_baby": True}})})), tier="C", icon="golden_dandelion", decoration="ADVANCEMENT")
    g.goal("PUT_WOLF_ARMOR_ON_WOLF", "Put Wolf Armor on Wolf", "给狼穿上狼铠",
           advancement(g.adv("wolf_armor", {"c": crit(
               "player_interacted_with_entity", item=items_pred("wolf_armor"), entity={"type": "minecraft:wolf"})})),
           tier="B", icon="wolf_armor", decoration="ADVANCEMENT")
    g.goal("CONSTRUCT_COPPER_GOLEM", "Construct a Copper Golem", "建造铜傀儡",
           advancement(g.adv("copper_golem", {"c": crit("summoned_entity", entity={"type": "minecraft:copper_golem"})})),
           tier="C", icon="copper_block", decoration="ADVANCEMENT")

    # ---- consume ----
    for key, food, zh, tier in [
        ("EAT_CHORUS_FRUIT", "chorus_fruit", "紫颂果", "S"), ("EAT_COOKIE", "cookie", "曲奇", "C"),
        ("EAT_GLOW_BERRY", "glow_berries", "发光浆果", "B"), ("EAT_POISONOUS_POTATO", "poisonous_potato", "毒马铃薯", "C"),
        ("EAT_PUMPKIN_PIE", "pumpkin_pie", "南瓜派", "C"), ("EAT_RABBIT_STEW", "rabbit_stew", "兔肉煲", "B"),
        ("EAT_SUSPICIOUS_STEW", "suspicious_stew", "迷之炖菜", "C"), ("EAT_BEETROOT_SOUP", "beetroot_soup", "甜菜汤", "C"),
        ("DRINK_HONEY_BOTTLE", "honey_bottle", "蜂蜜瓶", "B"),
    ]:
        verb, vzh = ("Drink", "喝") if key.startswith("DRINK") else ("Eat", "吃")
        g.goal(key, f"{verb} {title(food)}", f"{vzh}{zh}", stat("used", food), tier=tier, icon=food,
               decoration="ADVANCEMENT")
    g.goal("EAT_CAKE", "Eat a slice of Cake", "吃一块蛋糕", stat("custom", "eat_cake_slice"), tier="C", icon="cake",
           decoration="ADVANCEMENT")
    g.goal("DRINK_WATER_BOTTLE", "Drink Water Bottle", "喝水瓶",
           advancement(g.adv("drink_water", {"c": crit("consume_item", item=items_pred(
               "potion", components={"minecraft:potion_contents": {"potion": "minecraft:water"}}))})),
           tier="D", icon="potion", decoration="ADVANCEMENT",
           item_components={"minecraft:potion_contents": {"potion": "minecraft:water"}})
    g.goal("EAT_ALL_SOUPS", "Eat every type of Soup", "吃遍所有汤",
           all_of([stat("used", f) for f in ["suspicious_stew", "mushroom_stew", "rabbit_stew", "beetroot_soup"]]),
           tier="A", icon="rabbit_stew", decoration="ADVANCEMENT")
    for n, tier in [(5, "D"), (10, "C"), (15, "B"), (20, "A"), (25, "S")]:
        g.goal(f"EAT_{n}_UNIQUE_FOOD", f"Eat {n} Unique Food", f"吃 {n} 种食物", scoreboard("draftout_food", n),
               tier=tier, icon="cooked_beef", decoration="ADVANCEMENT")
    comp = {f: crit("item_used_on_block", location=[
        loc_check({"blocks": "minecraft:composter"}),
        match_tool(items_pred(f))]) for f in FOODS_COMPOSTABLE}
    g.adv_counter("counter/compost", comp)
    for n, tier in [(3, "C"), (5, "B"), (7, "A")]:
        g.goal(f"COMPOST_{n}_UNIQUE_FOODS", f"Compost {n} Unique Foods", f"向堆肥桶放入 {n} 种食物",
               scoreboard("draftout_compost", n), tier=tier, icon="composter", decoration="ADVANCEMENT")

    # ---- brewing ----
    g.goal("USE_BREWING_STAND", "Use Brewing Stand", "使用酿造台", advancement("minecraft:nether/brew_potion"),
           tier="B", icon="brewing_stand")
    for key, potions, name, zh, tier in [
        ("BREW_HEALING_POTION", ["healing", "strong_healing"], "Healing", "治疗", "A"),
        ("BREW_INVISIBILITY_POTION", ["invisibility", "long_invisibility"], "Invisibility", "隐身", "A"),
        ("BREW_POISON_POTION", ["poison", "long_poison", "strong_poison"], "Poison", "剧毒", "A"),
        ("BREW_SWIFTNESS_POTION", ["swiftness", "long_swiftness", "strong_swiftness"], "Swiftness", "迅捷", "A"),
        ("BREW_WATER_BREATHING_POTION", ["water_breathing", "long_water_breathing"], "Water Breathing", "水肺", "A"),
        ("BREW_WEAKNESS_POTION", ["weakness", "long_weakness"], "Weakness", "虚弱", "B"),
    ]:
        adv = g.adv("brew/" + potions[0], {"c": crit("brewed_potion", potion={"potions": [mc(p) for p in potions]})})
        g.goal(key, f"Brew a Potion of {name}", f"酿造{zh}药水", advancement(adv), tier=tier, icon="potion",
               decoration="ADVANCEMENT",
               item_components={"minecraft:potion_contents": {"potion": mc(potions[0])}})
    g.goal("BREW_LINGERING_POTION", "Brew a Lingering Potion", "酿造滞留药水", item("lingering_potion"), tier="S")
