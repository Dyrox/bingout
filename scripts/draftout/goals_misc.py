from gen_lib import *
from goals_items import TOOLS, ARMOR, TRIMS

SLOTS = ["head", "chest", "legs", "feet"]
ADV_ICONS = {
    "GET_THIS_BOAT_HAS_LEGS_ADVANCEMENT": "warped_fungus_on_a_stick", "GET_BULLSEYE_ADVANCEMENT": "target",
    "GET_NOT_QUITE_NINE_LIVES_ADVANCEMENT": "respawn_anchor", "GET_MOB_KABOB_ADVANCEMENT": "iron_spear",
    "GET_OH_SHINY_ADVANCEMENT": "gold_ingot", "GET_SNIPER_DUEL_ADVANCEMENT": "arrow",
    "GET_STAY_HYDRATED_ADVANCEMENT": "dried_ghast", "GET_WHAT_A_DEAL_ADVANCEMENT": "emerald",
    "GET_POWER_OF_BOOKS_ADVANCEMENT": "chiseled_bookshelf", "GET_A_TERRIBLE_FORTRESS_ADVANCEMENT": "nether_bricks",
    "GET_THE_CITY_AT_THE_END_OF_THE_GAME_ADVANCEMENT": "end_stone_bricks", "GET_EYE_SPY_ADVANCEMENT": "ender_eye",
    "GET_THOSE_WERE_THE_DAYS_ADVANCEMENT": "polished_blackstone_bricks", "GET_POSTMORTAL_ADVANCEMENT": "totem_of_undying",
    "GET_HIRED_HELP_ADVANCEMENT": "carved_pumpkin", "GET_WAX_ON_ADVANCEMENT": "honeycomb",
    "GET_WAX_OFF_ADVANCEMENT": "stone_axe", "GET_IT_SPREADS_ADVANCEMENT": "sculk_catalyst",
    "IGNITE_TNT_SULFUR_CUBE": "tnt", "ENTER_NETHER": "obsidian", "ENTER_END": "end_stone",
}


def wear_all(mat):
    return crit("tick", player={"equipment": {s: items_pred(f"{mat}_{a}") for s, a in zip(SLOTS, ARMOR)}})


def ubo(block_pred, tool=None):
    """item_used_on_block criterion with a location (block) predicate & optional held item"""
    loc = [loc_check(block_pred)]
    if tool:
        loc.append(match_tool(items_pred(tool)))
    return crit("item_used_on_block", location=loc)


def register(g: Gen):
    # ---- armor ----
    for mat, zh, tier in [("leather", "皮革", "C"), ("golden", "金", "C"), ("diamond", "钻石", "A"),
                          ("copper", "铜", "C"), ("iron", "铁", "B")]:
        g.goal(f"WEAR_{mat.upper()}_ARMOR", f"Wear Full {title(mat)} Armor", f"穿戴全套{zh}盔甲",
               advancement(g.adv(f"wear/{mat}", {"c": wear_all(mat)})), tier=tier, icon=f"{mat}_chestplate",
               decoration="ADVANCEMENT")
    g.goal("WEAR_CHAIN_ARMOR_PIECE", "Wear a Chain Armor Piece", "穿戴一件锁链盔甲",
           advancement(g.adv_any("wear/chainmail", [
               crit("tick", player={"equipment": {s: items_pred(f"chainmail_{a}")}}) for s, a in zip(SLOTS, ARMOR)])),
           tier="B", icon="chainmail_chestplate", decoration="ADVANCEMENT")
    grp = g.group()
    for c in COLORS:
        adv = g.adv_any(f"wear/leather_{c}", [
            crit("tick", player={"equipment": {s: items_pred(f"leather_{a}", components={
                "minecraft:dyed_color": DYE_RGB[c]})}}) for s, a in zip(SLOTS, ARMOR)])
        g.goal(f"WEAR_COLORED_LEATHER_ARMOR_PIECE__{c}", f"Wear {title(c)} Leather Armor Piece",
               f"穿戴{COLOR_ZH[c]}皮革盔甲", advancement(adv), tier="C", icon="leather_chestplate",
               decoration="ADVANCEMENT", group=grp,
               item_components={"minecraft:dyed_color": DYE_RGB[c]})
    g.goal("WEAR_FULL_ENCHANTED_ARMOR", "Wear Full Enchanted Armor", "穿戴全套附魔盔甲",
           advancement(g.adv("wear/enchanted", {"c": crit("tick", player={"equipment": {
               s: {"predicates": {"minecraft:enchantments": [{}]}} for s in SLOTS}})})),
           tier="A", icon="diamond_chestplate", decoration="ADVANCEMENT",
           item_components={"minecraft:enchantment_glint_override": True})
    for key, name, zh, icon, tier in [
        ("WEAR_UNIQUE_COLORED_LEATHER_ARMOR", "Wear Full Leather Armor, in different colors", "穿戴四种不同颜色的全套皮革盔甲", "leather_helmet", "B"),
        ("WEAR_UNIQUE_ARMOR", "Wear Full Armor with different materials", "穿戴四种不同材质的全套盔甲", "iron_helmet", "B"),
    ]:
        g.goal(key, name, zh, advancement(g.adv_impossible(key.lower())), tier=tier, icon=icon,
               decoration="ADVANCEMENT")
    g.goal("WEAR_CARVED_PUMPKIN_FOR_5_MINUTES", "Wear a Carved Pumpkin for 5 minutes", "头戴雕刻南瓜 5 分钟",
           scoreboard("draftout_pumpkin", 300), tier="C", icon="carved_pumpkin", decoration="ADVANCEMENT")
    g.goal("FILL_ARMOR_STAND", "Fill an Armor Stand", "装满盔甲架",
           advancement(g.adv("fill_armor_stand", {"c": crit("player_interacted_with_entity", entity={
               "type": "minecraft:armor_stand",
               "equipment": {s: {"count": {"min": 1}} for s in SLOTS}})})),
           tier="B", icon="armor_stand", decoration="ADVANCEMENT")

    # ---- vanilla advancements ----
    for key, adv, name, zh, tier in [
        ("GET_THIS_BOAT_HAS_LEGS_ADVANCEMENT", "nether/ride_strider", "Obtain \"This Boat Has Legs\"", "获得进度“这船有腿！”", "A"),
        ("GET_BULLSEYE_ADVANCEMENT", "adventure/bullseye", "Obtain \"Bullseye\"", "获得进度“正中靶心”", "B"),
        ("GET_NOT_QUITE_NINE_LIVES_ADVANCEMENT", "nether/charge_respawn_anchor", "Fully charge a Respawn Anchor", "充满重生锚", "A"),
        ("GET_MOB_KABOB_ADVANCEMENT", "adventure/spear_many_mobs", "Obtain \"Mob Kabob\"", "获得进度“生物烤串”", "A"),
        ("GET_OH_SHINY_ADVANCEMENT", "nether/distract_piglin", "Obtain \"Oh Shiny\"", "获得进度“金光闪闪”", "B"),
        ("GET_SNIPER_DUEL_ADVANCEMENT", "adventure/sniper_duel", "Obtain \"Sniper Duel\"", "获得进度“狙击手的对决”", "A"),
        ("GET_STAY_HYDRATED_ADVANCEMENT", "husbandry/place_dried_ghast_in_water", "Obtain \"Stay Hydrated!\"", "获得进度“补充水分！”", "B"),
        ("GET_WHAT_A_DEAL_ADVANCEMENT", "adventure/trade", "Trade with a Villager", "与村民交易", "C"),
        ("GET_POWER_OF_BOOKS_ADVANCEMENT", "adventure/read_power_of_chiseled_bookshelf", "Obtain \"The Power of Books\"", "获得进度“知识就是力量”", "B"),
        ("GET_A_TERRIBLE_FORTRESS_ADVANCEMENT", "nether/find_fortress", "Find a Fortress", "找到下界要塞", "B"),
        ("GET_THE_CITY_AT_THE_END_OF_THE_GAME_ADVANCEMENT", "end/find_end_city", "Find an End City", "找到末地城", "S"),
        ("GET_EYE_SPY_ADVANCEMENT", "story/follow_ender_eye", "Find a Stronghold", "找到要塞", "A"),
        ("GET_THOSE_WERE_THE_DAYS_ADVANCEMENT", "nether/find_bastion", "Find a Bastion", "找到堡垒遗迹", "B"),
        ("GET_POSTMORTAL_ADVANCEMENT", "adventure/totem_of_undying", "Obtain \"Postmortal\"", "获得进度“超越生死”", "A"),
        ("GET_HIRED_HELP_ADVANCEMENT", "adventure/summon_iron_golem", "Construct an Iron Golem", "建造铁傀儡", "C"),
        ("GET_WAX_ON_ADVANCEMENT", "husbandry/wax_on", "Wax a Copper Block", "给铜块涂蜡", "C"),
        ("GET_WAX_OFF_ADVANCEMENT", "husbandry/wax_off", "Scrape wax off of Copper Block", "给铜块除蜡", "C"),
        ("GET_IT_SPREADS_ADVANCEMENT", "adventure/kill_mob_near_sculk_catalyst", "Obtain \"It Spreads\"", "获得进度“它蔓延了”", "A"),
        ("IGNITE_TNT_SULFUR_CUBE", "husbandry/uh_oh", "Obtain \"Uh Oh\"", "获得进度“糟糕”", "B"),
        ("ENTER_NETHER", "story/enter_the_nether", "Enter Nether", "进入下界", "C"),
        ("ENTER_END", "story/enter_the_end", "Enter The End", "进入末地", "S"),
    ]:
        g.goal(key, name, zh, advancement("minecraft:" + adv), tier=tier, icon=ADV_ICONS[key])
    g.goal("GET_ANY_SPYGLASS_ADVANCEMENT", "Get any Spyglass Advancement", "获得任一望远镜进度",
           one_of([advancement("minecraft:adventure/spyglass_at_" + m) for m in ["parrot", "ghast", "dragon"]]),
           tier="B", icon="spyglass", decoration="ADVANCEMENT")
    for n, tier in [(10, "C"), (20, "B"), (30, "A")]:
        g.goal(f"GET_{n}_ADVANCEMENTS", f"Get {n} Advancements", f"获得 {n} 个进度",
               scoreboard("draftout_advancements", n), tier=tier, icon="knowledge_book", decoration="ADVANCEMENT")

    # ---- dimensions / exploration ----
    nether_biomes = ["nether_wastes", "crimson_forest", "warped_forest", "soul_sand_valley", "basalt_deltas"]
    g.goal("VISIT_ALL_NETHER_BIOMES", "Visit All Nether Biomes", "探索所有下界生物群系",
           advancement(g.adv("visit_nether_biomes", {b: crit("location", player={"location": {"biomes": mc(b)}})
                                                     for b in nether_biomes})),
           tier="A", icon="netherrack", decoration="ADVANCEMENT")
    cave_biomes = ["lush_caves", "dripstone_caves", "deep_dark"]
    g.goal("VISIT_ALL_CAVE_BIOMES", "Visit all Cave Biomes", "探索所有洞穴生物群系",
           advancement(g.adv("visit_cave_biomes", {b: crit("location", player={"location": {"biomes": mc(b)}})
                                                   for b in cave_biomes})),
           tier="A", icon="sculk", decoration="ADVANCEMENT")
    g.goal("PEARL_THROUGH_NETHER_PORTAL", "Teleport through Nether Portal using Ender Pearl", "用末影珍珠穿过下界传送门",
           advancement(g.adv_impossible("pearl_portal")), tier="B", icon="ender_pearl", decoration="ADVANCEMENT")
    g.goal("REACH_NETHER_ROOF", "Get on Nether Roof", "登上下界顶层",
           advancement(g.adv("nether_roof", {"c": crit("location", player={"location": {
               "dimension": "minecraft:the_nether", "position": {"y": {"min": 128}}}})})),
           tier="A", icon="bedrock", decoration="ADVANCEMENT")
    g.goal("REACH_BEDROCK", "Reach Bedrock", "到达基岩层",
           advancement(g.adv("reach_bedrock", {"c": crit("location", player={"location": {
               "dimension": "minecraft:overworld", "position": {"y": {"max": -59}}}})})),
           tier="C", icon="bedrock", decoration="ADVANCEMENT")
    g.goal("REACH_HEIGHT_LIMIT", "Reach height limit", "到达建筑高度上限",
           advancement(g.adv_any("height_limit", [
               crit("location", player={"location": {"dimension": "minecraft:overworld", "position": {"y": {"min": 320}}}}),
               crit("location", player={"location": {"dimension": "minecraft:the_nether", "position": {"y": {"min": 256}}}}),
               crit("location", player={"location": {"dimension": "minecraft:the_end", "position": {"y": {"min": 256}}}}),
           ])), tier="B", icon="scaffolding", decoration="ADVANCEMENT")

    # ---- player progress ----
    for lvl, tier in [(10, "C"), (20, "A")]:
        g.goal(f"REACH_EXP_LEVEL_{lvl}", f"Reach XP level {lvl}", f"达到 {lvl} 级",
               advancement(g.adv(f"xp_{lvl}", {"c": crit("tick", player={"type_specific": {
                   "type": "minecraft:player", "level": {"min": lvl}}})})),
               tier=tier, icon="experience_bottle", decoration="ADVANCEMENT")
    g.goal("TAKE_200_DAMAGE", "Take 200 damage", "受到 200 点伤害", stat("custom", "damage_taken", 2000), tier="B",
           icon="iron_chestplate", decoration="ADVANCEMENT")
    g.goal("DEAL_400_DAMAGE", "Deal 400 damage", "造成 400 点伤害", stat("custom", "damage_dealt", 4000), tier="B",
           icon="iron_sword", decoration="ADVANCEMENT")
    g.goal("SPRINT_1_KM", "Sprint 1km", "疾跑 1 千米", stat("custom", "sprint_one_cm", 100000), tier="C",
           icon="leather_boots", decoration="ADVANCEMENT")
    g.goal("EMPTY_HUNGER_BAR", "Empty Hunger Bar", "饥饿值归零", advancement(g.adv_impossible("empty_hunger")),
           tier="C", icon="rotten_flesh", decoration="ADVANCEMENT")

    # ---- workstations ----
    for key, s, name, zh, icon, tier in [
        ("USE_STONECUTTER", "interact_with_stonecutter", "Use Stonecutter", "使用切石机", "stonecutter", "C"),
        ("USE_CARTOGRAPHY_TABLE", "interact_with_cartography_table", "Use Cartography Table", "使用制图台", "cartography_table", "C"),
        ("USE_ANVIL", "interact_with_anvil", "Use Anvil", "使用铁砧", "anvil", "B"),
        ("USE_ENCHANTING_TABLE", "enchant_item", "Use Enchanting Table", "使用附魔台", "enchanting_table", "B"),
        ("USE_GRINDSTONE", "interact_with_grindstone", "Use Grindstone", "使用砂轮", "grindstone", "C"),
        ("USE_LOOM", "interact_with_loom", "Use Loom to design a Banner", "使用织布机", "loom", "C"),
        ("USE_SMITHING_TABLE", "interact_with_smithing_table", "Use Smithing Table", "使用锻造台", "smithing_table", "C"),
        ("USE_JUKEBOX", "play_record", "Play a Music Disc in Jukebox", "用唱片机播放唱片", "jukebox", "A"),
    ]:
        g.goal(key, name, zh, stat("custom", s), tier=tier, icon=icon, decoration="ADVANCEMENT")
    g.goal("USE_CAULDRON", "Use Cauldron to wash something", "用炼药锅清洗物品",
           one_of([stat("custom", s) for s in ["clean_armor", "clean_banner", "clean_shulker_box"]]),
           tier="C", icon="cauldron", decoration="ADVANCEMENT")
    g.goal("TUNE_NOTEBLOCK", "Tune a Note Block", "调音音符盒",
           one_of([stat("custom", s) for s in ["tune_noteblock", "play_noteblock"]]), tier="D", icon="note_block",
           decoration="ADVANCEMENT")
    g.goal("USE_COMPOSTER", "Fill Composter and get Bone Meal", "填满堆肥桶并取出骨粉",
           advancement(g.adv("composter_bone_meal", {"c": crit("any_block_use", location=[
               loc_check({
                   "blocks": "minecraft:composter", "state": {"level": "8"}})])})),
           tier="C", icon="composter", decoration="ADVANCEMENT")
    g.goal("LOCK_MAP", "Lock a Map using Cartography Table", "用制图台锁定地图",
           advancement(g.adv_impossible("lock_map")), tier="B", icon="filled_map", decoration="ADVANCEMENT")
    g.goal("FILL_CHISELED_BOOKSHELF", "Fill Chiseled Bookshelf", "填满雕纹书架",
           advancement(g.adv("fill_chiseled_bookshelf", {"c": ubo({"blocks": "minecraft:chiseled_bookshelf", "state": {
               f"slot_{i}_occupied": "true" for i in range(6)}})})),
           tier="B", icon="chiseled_bookshelf", decoration="ADVANCEMENT")
    g.goal("FILL_CAMPFIRE", "Fill Campfire with 4 Food Items", "在营火上放满 4 个食物",
           advancement(g.adv("fill_campfire", {"c": ubo({"blocks": ["minecraft:campfire", "minecraft:soul_campfire"],
                                                         "nbt": "{Items:[{Slot:0b},{Slot:1b},{Slot:2b},{Slot:3b}]}"})})),
           tier="C", icon="campfire", decoration="ADVANCEMENT")
    g.goal("FILL_DECORATED_POT", "Fill a Decorated Pot", "装满饰纹陶罐",
           advancement(g.adv_any("fill_decorated_pot", [
               ubo({"blocks": "minecraft:decorated_pot", "nbt": "{item:{count:64}}"}),
               ubo({"blocks": "minecraft:decorated_pot", "nbt": "{item:{count:16}}"}),
           ])), tier="C", icon="decorated_pot", decoration="ADVANCEMENT")
    g.goal("FILL_INVENTORY_UNIQUE_ITEMS", "Fill Inventory with unique items", "用 36 种不同物品填满背包",
           advancement(g.adv_impossible("fill_inventory_unique")), tier="A", icon="chest", decoration="ADVANCEMENT")
    g.goal("OBTAIN_STACK_OF_64", "Obtain a stack of any Item", "获得一组 64 个物品",
           advancement(g.adv_impossible("stack_of_64")), tier="D", icon="cobblestone", decoration="ADVANCEMENT")

    # ---- decorating ----
    g.goal("PUT_BANNER_ON_SHIELD", "Decorate Shield with Banner", "用旗帜装饰盾牌",
           advancement(g.adv_any("banner_shield", [crit("inventory_changed", items=[items_pred(
               "shield", components={"minecraft:base_color": c})]) for c in COLORS])),
           tier="C", icon="shield", decoration="ADVANCEMENT")
    g.goal("LIGHT_CANDLE", "Light a Candle", "点燃蜡烛",
           advancement(g.adv_any("light_candle", [ubo({"blocks": "#minecraft:candles", "state": {"lit": "true"}}, t)
                                                  for t in ["flint_and_steel", "fire_charge"]])),
           tier="C", icon="candle", decoration="ADVANCEMENT")
    g.goal("PUT_FLOWER_IN_POT", "Pot any plant in Flower Pot", "在花盆里种植物",
           advancement(g.adv("flower_pot", {"c": ubo({"blocks": "#minecraft:flower_pots"})})),
           tier="D", icon="flower_pot", decoration="ADVANCEMENT")
    g.goal("ITEM_FRAME_IN_ITEM_FRAME", "Put Item Frame in an Item Frame", "把物品展示框放进物品展示框",
           advancement(g.adv("frame_in_frame", {"c": crit("player_interacted_with_entity", item=items_pred("item_frame"),
                                                          entity={"type": "minecraft:item_frame"})})),
           tier="C", icon="item_frame", decoration="ADVANCEMENT")
    signs = {
        "crimson": ["crimson_sign", "crimson_wall_sign", "crimson_hanging_sign", "crimson_wall_hanging_sign"],
        "warped": ["warped_sign", "warped_wall_sign", "warped_hanging_sign", "warped_wall_hanging_sign"],
    }
    for key, kinds, name, zh in [
        ("USE_GLOW_INK_CRIMSON", ["crimson"], "Use Glow Ink on Crimson Sign", "对绯红木告示牌使用荧光墨囊"),
        ("USE_GLOW_INK_WARPED", ["warped"], "Use Glow Ink on Warped Sign", "对诡异木告示牌使用荧光墨囊"),
        ("USE_GLOW_INK_NETHER", ["crimson", "warped"], "Use Glow Ink on Crimson or Warped Sign", "对下界木告示牌使用荧光墨囊"),
    ]:
        blocks = [mc(b) for k in kinds for b in signs[k]]
        g.goal(key, name, zh, advancement(g.adv(key.lower(), {"c": ubo({"blocks": blocks}, "glow_ink_sac")})),
               tier="B", icon="glow_ink_sac", decoration="ADVANCEMENT")
    g.goal("CRAFT_ARMOR_TRIM", "Duplicate any Armor Trim", "复制任意盔甲纹饰",
           advancement(g.adv("craft_trim", {"c": crit("recipe_crafted", recipes=[
               f"minecraft:{t}_armor_trim_smithing_template" for t in TRIMS])})),
           tier="A", icon="wild_armor_trim_smithing_template", decoration="ADVANCEMENT")
    g.goal("PLACE_HANGING_SIGN", "Place a Hanging Sign", "放置悬挂式告示牌",
           advancement(g.adv("hanging_sign", {"c": crit("placed_block", location=[
               loc_check({"blocks": "#minecraft:all_hanging_signs"})])})),
           tier="C", icon="oak_hanging_sign", decoration="ADVANCEMENT")
    g.goal("PLACE_PAINTING", "Place Painting", "放置画", stat("used", "painting"), tier="D", icon="painting",
           decoration="ADVANCEMENT")
    g.goal("BUNDLE_IN_BUNDLE", "Put a Bundle in a Bundle", "把收纳袋放进收纳袋",
           advancement(g.adv("bundle_in_bundle", {"c": crit("inventory_changed", items=[{
               "items": "#minecraft:bundles",
               "predicates": {"minecraft:bundle_contents": {"items": {"contains": [{"items": "#minecraft:bundles"}]}}}}])})),
           tier="C", icon="bundle", decoration="ADVANCEMENT")

    # ---- crafting ----
    for n, tier in [(20, "C"), (50, "B"), (100, "S")]:
        g.goal(f"CRAFT_{n}_UNIQUE_ITEMS", f"Craft {n} Unique Items", f"合成 {n} 种物品",
               scoreboard("draftout_crafted", n), tier=tier, icon="crafting_table", decoration="ADVANCEMENT")

    # ---- tool actions ----
    tools = [f"{m}_{t}" for m in ["wooden", "stone", "copper", "golden", "iron", "diamond"] for t in TOOLS + ["spear"]]
    tools += ["fishing_rod", "flint_and_steel", "shears", "brush", "carrot_on_a_stick", "warped_fungus_on_a_stick"]
    armor = [f"{m}_{a}" for m in ["leather", "copper", "golden", "chainmail", "iron", "diamond"] for a in ARMOR]
    g.goal("BREAK_ANY_TOOL", "Break any Tool", "用坏任意工具", one_of([stat("broken", t) for t in tools]), tier="C",
           icon="wooden_pickaxe", decoration="ADVANCEMENT")
    g.goal("BREAK_ANY_ARMOR", "Break any Armor Piece", "用坏任意盔甲", one_of([stat("broken", a) for a in armor]),
           tier="B", icon="leather_boots", decoration="ADVANCEMENT")
    g.goal("BREAK_FISHING_ROD", "Break Fishing Rod", "用坏钓鱼竿", stat("broken", "fishing_rod"), tier="C",
           icon="fishing_rod", decoration="ADVANCEMENT")
    g.goal("BREAK_SHIELD", "Break Shield", "用坏盾牌", stat("broken", "shield"), tier="A", icon="shield",
           decoration="ADVANCEMENT")
    g.goal("HAVE_YOUR_SHIELD_DISABLED", "Have your Shield disabled", "盾牌被破除",
           advancement(g.adv_impossible("shield_disabled")), tier="C", icon="shield", decoration="ADVANCEMENT")
    g.goal("SHOOT_FIREWORK_FROM_CROSSBOW", "Shoot Firework from Crossbow", "用弩发射烟花火箭",
           advancement(g.adv_impossible("crossbow_firework")), tier="B", icon="crossbow", decoration="ADVANCEMENT")
    g.goal("USE_BRUSH_ON_SUSPICIOUS_BLOCK", "Use Brush on Suspicious Sand/Gravel", "刷可疑的沙子/沙砾",
           stat("used", "brush"), tier="B", icon="brush", decoration="ADVANCEMENT")
    g.goal("EXPLODE_END_CRYSTAL", "Explode End Crystal", "引爆末地水晶",
           advancement(g.adv("end_crystal", {"c": crit("player_hurt_entity", entity={"type": "minecraft:end_crystal"})})),
           tier="S", icon="end_crystal", decoration="ADVANCEMENT")
    g.goal("TOOT_GOAT_HORN", "Toot a Goat Horn", "吹响山羊角", stat("used", "goat_horn"), tier="B", icon="goat_horn",
           decoration="ADVANCEMENT")
