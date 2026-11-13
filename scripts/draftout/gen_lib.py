"""
Helpers for generating the "draftout" goal category.

Run `python3 scripts/draftout/generate.py` from the repository root to regenerate:
  - objectives/draftout/*.json      (bingo goals)
  - advancement/draftout/*.json     (hidden helper advancements used by goals)
  - tierlists/draftout.tierlist.json
  - lang entries (en_us / zh_cn)
"""
import json
import os
from gen_icons import GoalIcons

NS = "yet-another-minecraft-bingo"
RES = os.path.join("common", "src", "main", "resources")
OBJ_DIR = os.path.join(RES, "data", NS, NS, "objectives", "draftout")
ADV_DIR = os.path.join(RES, "data", NS, "advancement", "draftout")
TIER_FILE = os.path.join(RES, "data", NS, NS, "tierlists", "draftout.tierlist.json")
LANG_DIR = os.path.join(RES, "assets", NS, "lang")

COLORS = [
    "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
    "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
]
COLOR_ZH = {
    "white": "白色", "orange": "橙色", "magenta": "品红色", "light_blue": "淡蓝色",
    "yellow": "黄色", "lime": "黄绿色", "pink": "粉红色", "gray": "灰色",
    "light_gray": "淡灰色", "cyan": "青色", "purple": "紫色", "blue": "蓝色",
    "brown": "棕色", "green": "绿色", "red": "红色", "black": "黑色",
}
# DyeColor texture diffuse colors (leather armor dyed with a single dye has exactly this color)
DYE_RGB = {
    "white": 0xF9FFFE, "orange": 0xF9801D, "magenta": 0xC74EBD, "light_blue": 0x3AB3DA,
    "yellow": 0xFED83D, "lime": 0x80C71F, "pink": 0xF38BAA, "gray": 0x474F52,
    "light_gray": 0x9D9D97, "cyan": 0x169C9C, "purple": 0x8932B8, "blue": 0x3C44AA,
    "brown": 0x835432, "green": 0x5E7C16, "red": 0xB02E26, "black": 0x1D1D21,
}


# Strings used by the draft UI (DraftController / DraftCommand)
UI_STRINGS = {
    "en_us": {
        "bingo.draft.pick_number": "Pick %s / %s",
        "bingo.draft.or": "or",
        "bingo.list.draftout": "Draftout",
        "bingo.options.mode_draftout": "Draftout",
        "bingo.options.mode_draftout.tooltip": "Two teams take turns drafting the goals on the board before the game starts (Lockout, 13 goals to win).",
        "bingo.draft.title": "Draftout — Pick %s/%s",
        "bingo.draft.your_turn": "Your team's pick! (%ss)",
        "bingo.draft.waiting": "%s is picking... (%ss)",
        "bingo.draft.offer": "Choose a goal to add to the board:",
        "bingo.draft.pick": "Pick",
        "bingo.draft.reroll": "Reroll (%s left)",
        "bingo.draft.picked": "%s picked %s",
        "bingo.draft.random_pick": "%s ran out of time - randomly picked %s",
        "bingo.draft.rerolled": "%s used their reroll",
        "bingo.draft.no_reroll": "Your team has already used its reroll.",
        "bingo.draft.not_your_turn": "It's not your team's turn to pick.",
        "bingo.draft.not_drafting": "There is no draft in progress.",
        "bingo.draft.started": "Draft started! Teams take turns picking goals for the board. %s picks first.",
        "bingo.draft.complete": "Draft complete! The board is ready.",
        "bingo.draft.requires_two_teams": "Draftout mode requires exactly 2 teams (currently %s).",
        "bingo.draft.skipped": "Draftout mode needs exactly 2 teams - skipping the draft and using a random board.",
    },
    "zh_cn": {
        "bingo.draft.pick_number": "第 %s / %s 选",
        "bingo.draft.or": "或",
        "bingo.list.draftout": "Draftout",
        "bingo.options.mode_draftout": "Draftout 轮选",
        "bingo.options.mode_draftout.tooltip": "开局前两队轮流选择棋盘上的目标（锁定模式，先拿 13 格获胜）。",
        "bingo.draft.title": "Draftout — 第 %s/%s 选",
        "bingo.draft.your_turn": "轮到你的队伍选择！（%s 秒）",
        "bingo.draft.waiting": "%s 正在选择……（%s 秒）",
        "bingo.draft.offer": "选择一个目标加入棋盘：",
        "bingo.draft.pick": "选择",
        "bingo.draft.reroll": "重抽（剩余 %s 次）",
        "bingo.draft.picked": "%s 选择了 %s",
        "bingo.draft.random_pick": "%s 超时，随机选择了 %s",
        "bingo.draft.rerolled": "%s 使用了重抽",
        "bingo.draft.no_reroll": "你的队伍已经用过重抽了。",
        "bingo.draft.not_your_turn": "现在不是你的队伍选择。",
        "bingo.draft.not_drafting": "当前没有进行中的选择阶段。",
        "bingo.draft.started": "选择阶段开始！两队轮流为棋盘选择目标，%s 先选。",
        "bingo.draft.complete": "选择完成！棋盘已就绪。",
        "bingo.draft.requires_two_teams": "轮选模式需要正好 2 支队伍（当前 %s 支）。",
        "bingo.draft.skipped": "轮选模式需要正好 2 支队伍——跳过选择阶段，使用随机棋盘。",
    },
}


def title(s):
    return " ".join(w.capitalize() for w in s.split("_"))


def mc(x):
    return x if ":" in x else "minecraft:" + x


class Gen:
    def __init__(self):
        self.objectives = {}   # name -> json
        self.advancements = {}  # name -> json
        self.lang_en = {}
        self.lang_zh = {}
        self.tiers = {t: [] for t in "SABCD"}
        self.groups = []
        self.goal_names = []  # draftout GoalType constant names that were mapped
        self.icons = GoalIcons()

    # ---------- advancements ----------
    def adv(self, name, criteria, requirements=None):
        """Register a hidden advancement (no display => no toast / chat message)."""
        data = {"criteria": criteria}
        if requirements is not None:
            data["requirements"] = requirements
        self.advancements[name] = data
        return f"{NS}:draftout/{name}"

    def adv_any(self, name, criteria_list):
        """Advancement that completes when ANY of the criteria is met"""
        crit = {f"c{i}": c for i, c in enumerate(criteria_list)}
        return self.adv(name, crit, [list(crit.keys())])

    def adv_all(self, name, criteria_list):
        crit = {f"c{i}": c for i, c in enumerate(criteria_list)}
        return self.adv(name, crit)

    def adv_counter(self, name, criteria):
        """
        Advancement that is never completed (it also needs the impossible "never" criterion), so every
        criterion keeps being tracked. DraftoutTriggerController counts the completed criteria.
        """
        crit_all = dict(criteria)
        crit_all["never"] = {"trigger": "minecraft:impossible"}
        return self.adv(name, crit_all, [list(criteria.keys()), ["never"]])

    def adv_impossible(self, name):
        """Advancement granted from code (DraftoutTriggerController)"""
        return self.adv(name, {"granted": {"trigger": "minecraft:impossible"}})

    # ---------- goals ----------
    def goal(self, key, name, zh, data, tier="C", icon=None, decoration=None, group=None, image=None,
             item_components=None):
        """
        key: draftout GoalType constant (e.g. OBTAIN_WOODEN_TOOLS), or KEY__variant for color variants
        """
        file = key.lower()
        lang_key = f"bingo.objective.draftout.{file}"
        display = dict(data.pop("display", {}) or {})
        display["name"] = {"translate": lang_key, "fallback": name}
        if icon:
            display["item"] = mc(icon)
        if item_components:
            display["itemComponents"] = item_components
        if decoration:
            display["decoration"] = decoration
        if image:
            display["image"] = image
        self.icons.apply(key, display, data.get("item"))
        # Stats sub-goals don't provide item art. SomeOf's default grid would hide
        # our explicit icon and render empty cells (e.g. eating all soups).
        if any(isinstance(o, dict) and o.get("type") == "stats" for o in data.get("objectives", [])):
            display["itemStyle"] = "NORMAL"
        out = {"type": data.pop("type")}
        out["display"] = display
        out.update(data)
        self.objectives[file] = out
        self.lang_en[lang_key] = name
        self.lang_zh[lang_key] = zh
        oid = f"{NS}:draftout/{file}"
        self.tiers[tier].append(oid)
        if group is not None:
            group.append(oid)
        self.goal_names.append(key.split("__")[0])
        return oid

    def group(self):
        g = []
        self.groups.append(g)
        return g

    # ---------- write ----------
    def write(self):
        self.icons.write()
        for d in (OBJ_DIR, ADV_DIR):
            os.makedirs(d, exist_ok=True)
            for f in os.listdir(d):
                if f.endswith(".json"):
                    os.remove(os.path.join(d, f))
        for name, data in self.objectives.items():
            with open(os.path.join(OBJ_DIR, name + ".json"), "w") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
                f.write("\n")
        for name, data in self.advancements.items():
            path = os.path.join(ADV_DIR, name + ".json")
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "w") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
                f.write("\n")
        tierlist = {"replace": False, "values": []}
        for t in "SABCD":
            tierlist[t] = sorted(self.tiers[t])
        tierlist["groups"] = [sorted(g) for g in self.groups if g]
        with open(TIER_FILE, "w") as f:
            json.dump(tierlist, f, indent=4)
            f.write("\n")
        for lang, entries in (("en_us", self.lang_en), ("zh_cn", self.lang_zh)):
            path = os.path.join(LANG_DIR, lang + ".json")
            with open(path) as f:
                existing = json.load(f)
            ours = dict(entries)
            ours.update(UI_STRINGS[lang])
            existing = {k: v for k, v in existing.items()
                        if not k.startswith("bingo.objective.draftout.") and k not in ours}
            existing.update(dict(sorted(ours.items())))
            with open(path, "w") as f:
                json.dump(existing, f, indent=2, ensure_ascii=False)
                f.write("\n")


# ---------- objective data builders ----------
def item(i, count=1):
    d = {"type": "item", "item": mc(i)}
    if count != 1:
        d["count"] = count
    return d


def all_of(objs):
    return {"type": "all_of", "objectives": [mc(o) if isinstance(o, str) else o for o in objs]}


def one_of(objs):
    return {"type": "one_of", "objectives": [mc(o) if isinstance(o, str) else o for o in objs]}


def some_of(objs, n):
    return {"type": "some_of", "objectives": [mc(o) if isinstance(o, str) else o for o in objs], "min": n}


def stat(stat_type, stat_name=None, min_=1):
    d = {"type": "stats", "statType": mc(stat_type)}
    if stat_name:
        d["statName"] = mc(stat_name)
    if min_ != 1:
        d["min"] = min_
    return d


def advancement(adv_id):
    return {"type": "advancement", "advancement": adv_id}


def scoreboard(name, min_):
    return {"type": "scoreboard", "scoreboardName": name, "min": min_}


# ---------- advancement criteria builders ----------
def player_pred(pred):
    return {"player": pred}


ENTITY_KEYS = {
    "type": "minecraft:entity_type", "location": "minecraft:location", "equipment": "minecraft:equipment",
    "flags": "minecraft:flags", "vehicle": "minecraft:vehicle", "effects": "minecraft:effects",
    "components": "minecraft:components", "nbt": "minecraft:nbt", "passenger": "minecraft:passenger",
}
ENTITY_FIELDS = {"player", "entity", "child", "parent", "partner", "bystander"}


def ent(pred):
    """Convert a simple entity predicate into the 26.x format with namespaced sub-predicate keys"""
    out = {}
    for k, v in pred.items():
        if k == "type" and isinstance(v, str) and v.startswith("#"):
            out["minecraft:entity_tags"] = v[1:]
        elif k == "type_specific":
            v = dict(v)
            kind = v.pop("type").split(":")[1]
            if "looking_at" in v:
                v["looking_at"] = ent(v["looking_at"])
            out[f"minecraft:type_specific/{kind}"] = v
        elif k in ("vehicle", "passenger"):
            out[ENTITY_KEYS[k]] = ent(v)
        else:
            out[ENTITY_KEYS.get(k, k)] = v
    return out


def entity_condition(pred):
    return {"type": "minecraft:entity_properties", "entity": "this", "predicate": ent(pred)}


def crit(trigger, **conditions):
    c = {"trigger": mc(trigger)}
    for k, v in list(conditions.items()):
        if k in ENTITY_FIELDS:
            conditions[k] = entity_condition(v)
        elif k == "killing_blow":
            conditions[k] = {kk: ent(vv) if kk in ("direct_entity", "source_entity") else vv for kk, vv in v.items()}
        elif k == "location" and isinstance(v, list):
            conditions[k] = v[0] if len(v) == 1 else {"type": "minecraft:all_of", "terms": v}
    if conditions:
        c["conditions"] = conditions
    return c


def loc_check(block_pred):
    return {"type": "minecraft:location_check", "predicate": {"block": block_pred}}


def match_tool(item_pred):
    return {"type": "minecraft:match_tool", "predicate": item_pred}


def items_pred(items, **extra):
    if isinstance(items, list):
        items = [mc(i) for i in items]
    else:
        items = mc(items) if not items.startswith("#") else items
    return {"items": items, **extra}
