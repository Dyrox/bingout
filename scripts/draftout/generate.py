#!/usr/bin/env python3
"""
Generates the "draftout" goal category (a port of the 316 Draftout lockout goals).
Run from the repository root:  python3 scripts/draftout/generate.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from gen_lib import Gen, mc  # noqa: E402
import goals_items  # noqa: E402
import goals_mobs  # noqa: E402
import goals_misc  # noqa: E402

SPY_MOBS = """allay armadillo axolotl bat bee blaze bogged breeze camel camel_husk cat cave_spider chicken cod
copper_golem cow creaking creeper dolphin donkey drowned elder_guardian ender_dragon enderman endermite evoker fox
frog ghast glow_squid goat guardian happy_ghast hoglin horse husk iron_golem llama magma_cube mooshroom
mule nautilus ocelot panda parched parrot phantom pig piglin piglin_brute pillager polar_bear pufferfish rabbit
ravager salmon sheep shulker silverfish skeleton skeleton_horse slime sniffer snow_golem spider squid stray strider
sulfur_cube tadpole trader_llama tropical_fish turtle vex villager vindicator wandering_trader warden witch wither
wither_skeleton wolf zoglin zombie zombie_horse zombie_nautilus zombie_villager zombified_piglin""".split()


def main():
    g = Gen()
    goals_items.register(g)
    goals_mobs.register(g, [mc(m) for m in SPY_MOBS])
    goals_misc.register(g)

    # Placeholder tile shown on the board for slots that have not been drafted yet.
    # It can never be completed, and is excluded from normal cards via the "unobtainable" tag.
    placeholder_adv = g.adv_impossible("placeholder")
    g.objectives["placeholder"] = {
        "type": "advancement",
        "display": {
            "name": {"translate": "bingo.objective.draftout.placeholder", "fallback": "Not drafted yet"},
            "item": "minecraft:light_gray_stained_glass_pane",
            "decoration": "NONE",
        },
        "advancement": placeholder_adv,
    }
    g.lang_en["bingo.objective.draftout.placeholder"] = "Not drafted yet"
    g.lang_zh["bingo.objective.draftout.placeholder"] = "尚未选择"
    g.write()

    # Report which Draftout goal types are covered
    goal_types_file = os.path.join(os.path.dirname(__file__), "draftout_goal_types.txt")
    if os.path.exists(goal_types_file):
        all_types = [l.strip() for l in open(goal_types_file) if l.strip()]
        mapped = set(g.goal_names)
        missing = [t for t in all_types if t not in mapped]
        print(f"Mapped {len(set(all_types)) - len(missing)}/{len(set(all_types))} Draftout goal types")
        if missing:
            print("Missing:", " ".join(missing))
    print(f"Wrote {len(g.objectives)} objectives, {len(g.advancements)} advancements")


if __name__ == "__main__":
    main()
