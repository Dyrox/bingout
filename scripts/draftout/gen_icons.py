"""Build static card icons from Draftout's original goal textures and render recipes.

The source PNGs are copied unchanged under assets/goal. Cycling icons use the first
original frame; overlays and stack counts are baked into the card image. No client
resource pack or Draftout mod is needed: Bingo sends these datapack images itself.
"""
import io
import json
from pathlib import Path
from zipfile import ZipFile

from PIL import Image, ImageOps

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
NS = "yet-another-minecraft-bingo"
OUTPUT = ROOT / "common/src/main/resources/data" / NS / NS / "images/draftout"
RECIPES = json.loads((HERE / "icons.json").read_text())
SIZE = 128

# Changes to the original recipes, so that no two goals look the same on the card
RECIPE_CHANGES = {
    # the original reuses GET_BAD_OMEN_STATUS_EFFECT's art as is
    "KILL_ALL_RAID_MOBS": {"kill": True},
    # no original art, and rabbit stew alone is EAT_RABBIT_STEW's icon
    "EAT_ALL_SOUPS": {"grid": ["mushroom_stew", "beetroot_soup", "rabbit_stew", "suspicious_stew"]},
}
for _goal, _change in RECIPE_CHANGES.items():
    RECIPES.setdefault(_goal, {}).update(_change)

# DyeColor texture diffuse colors
DYE_COLORS = {
    "white": 0xF9FFFE, "orange": 0xF9801D, "magenta": 0xC74EBD, "light_blue": 0x3AB3DA,
    "yellow": 0xFED83D, "lime": 0x80C71F, "pink": 0xF38BAA, "gray": 0x474F52,
    "light_gray": 0x9D9D97, "cyan": 0x169C9C, "purple": 0x8932B8, "blue": 0x3C44AA,
    "brown": 0x835432, "green": 0x5E7C16, "red": 0xB02E26, "black": 0x1D1D21,
}


def sheep_wool_rgb(color):
    """Wool tint relative to the art's shading: the game tints sheep with the dye color * 0.75,
    except white sheep with 0xE6E6E6 (ColorLerper.Type.SHEEP)."""
    if color == "white":
        return [0xE6 / 0.75] * 3
    value = DYE_COLORS[color]
    return [(value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF]


class GoalIcons:
    def __init__(self):
        self.images = {}
        self.items = ZipFile(ROOT / "common/images.zip")
        self.digits = Image.open(
            ROOT / "common/src/main/resources" / NS / "numbers.png"
        ).convert("RGBA")
        self.sheep_model = None

    def texture(self, name, crop=None, size=SIZE):
        with Image.open(HERE / "assets/goal" / name) as source:
            image = source.convert("RGBA")
        if crop:
            image = image.crop(crop)
        return image.resize((size, size), Image.Resampling.NEAREST)

    def with_sword(self, art):
        """Like the original kill art: the (smaller) art at the bottom left, a sword pointing up-left at the top right."""
        image = Image.new("RGBA", (SIZE, SIZE))
        image.alpha_composite(art, (0, SIZE - art.height))
        half = SIZE // 2
        sword = ImageOps.mirror(self.item("iron_sword")).resize((half, half), Image.Resampling.NEAREST)
        image.alpha_composite(sword, (half, 0))
        return image

    def grid(self, items):
        """Up to four items in a 2x2 grid, like the card's own icons for "all of" goals."""
        image = Image.new("RGBA", (SIZE, SIZE))
        half = SIZE // 2
        for i, name in enumerate(items):
            icon = self.item(name).resize((half, half), Image.Resampling.NEAREST)
            image.alpha_composite(icon, ((i % 2) * half, (i // 2) * half))
        return image

    def sheep(self, color):
        """
        Draftout's sheep art in a color it has no art for. Each art pixel is fitted as
        constant + shade * wool color over the original colors (wool pixels: constant 0;
        face and sword: shade 0; antialiased sword edges: both), then rendered with `color`.
        """
        import numpy as np

        if self.sheep_model is None:
            known = sorted(
                p.stem.removeprefix("kill_").removesuffix("_sheep")
                for p in (HERE / "assets/goal/sheep").glob("kill_*_sheep.png")
            )
            arts = np.stack([
                np.asarray(Image.open(HERE / f"assets/goal/sheep/kill_{c}_sheep.png").convert("RGBA"), float)
                for c in known
            ])
            n, h, w, _ = arts.shape
            # rows (color, channel), unknowns (constant r, g, b, shade)
            design = np.zeros((n * 3, 4))
            for i, c in enumerate(known):
                design[i * 3:i * 3 + 3, :3] = np.eye(3)
                design[i * 3:i * 3 + 3, 3] = sheep_wool_rgb(c)
            values = arts[..., :3].transpose(0, 3, 1, 2).reshape(n * 3, h * w)
            fit = np.linalg.lstsq(design, values, rcond=None)[0]
            self.sheep_model = (
                fit[:3].T.reshape(h, w, 3),
                fit[3].reshape(h, w, 1),
                np.median(arts[..., 3], axis=0),
            )

        constant, shade, alpha = self.sheep_model
        rgb = np.clip(constant + shade * sheep_wool_rgb(color), 0, 255)
        art = Image.fromarray(np.dstack([rgb, alpha]).round().astype(np.uint8), "RGBA")
        return art.resize((SIZE, SIZE), Image.Resampling.NEAREST)

    def item(self, name):
        name = name.removeprefix("minecraft:")
        with Image.open(io.BytesIO(self.items.read(name + ".png"))) as source:
            return source.convert("RGBA").resize((SIZE, SIZE), Image.Resampling.NEAREST)

    def draw_count(self, image, count):
        digits = str(count)
        width = len(digits) * 6 - 1
        scale = min(8, (SIZE - 4) // width)
        x, y = SIZE - width * scale, SIZE - 7 * scale
        for digit in digits:
            offset = int(digit) * 5
            glyph = self.digits.crop((offset, 0, offset + 5, 7))
            glyph = glyph.resize((5 * scale, 7 * scale), Image.Resampling.NEAREST)
            shadow = Image.new("RGBA", glyph.size, (32, 32, 32, 0))
            shadow.putalpha(glyph.getchannel("A"))
            # a thin outline keeps white digits readable on light art (white wool, concrete)
            edge = max(1, scale // 3)
            for dx in (-edge, 0, edge):
                for dy in (-edge, 0, edge):
                    if dx or dy:
                        image.alpha_composite(shadow, (x + dx, y + dy))
            image.alpha_composite(shadow, (x + scale, y + scale))
            image.alpha_composite(glyph, (x, y))
            x += 6 * scale

    def apply(self, key, display, item=None):
        goal, _, variant = key.partition("__")
        recipe = RECIPES.get(goal)
        if not recipe or not any(k in recipe for k in ("texture", "overlays", "count", "grid")):
            return
        texture = recipe.get("texture")
        overlays = recipe.get("overlays", [])
        if "grid" in recipe:
            image = self.grid(recipe["grid"])
        elif recipe.get("kill"):
            image = self.with_sword(self.texture(texture, recipe.get("crop"), SIZE * 3 // 4))
        elif texture:
            texture = texture.format(variant=variant)
            if goal == "KILL_COLORED_SHEEP" and not (HERE / "assets/goal" / texture).exists():
                # Bingo includes six extra sheep colors absent from the original mod.
                image = self.sheep(variant)
            else:
                image = self.texture(texture, recipe.get("crop"))
        else:
            image = self.item(recipe.get("item") or display.get("item") or item)
        for overlay in overlays:
            image.alpha_composite(self.texture(overlay))
        if recipe.get("count", 1) > 1:
            self.draw_count(image, recipe["count"])
        if image.getchannel("A").getbbox() is None:
            raise ValueError(f"Empty Draftout icon for {key}")
        filename = key.lower()
        self.images[filename] = image
        # Keep the fallback item at count 1; the custom image already includes its count.
        if "item" not in display and item:
            display["item"] = item
        display["image"] = f"{NS}:draftout/{filename}"
        # Custom art takes precedence over SomeOf's automatically assembled sub-icons.
        display["itemStyle"] = "NORMAL"
        # The original art already conveys breeding, mining, death, etc.
        display["decoration"] = "NONE"

    def write(self):
        OUTPUT.mkdir(parents=True, exist_ok=True)
        for file in OUTPUT.glob("*.png"):
            if file.stem not in self.images:
                file.unlink()
        for name, image in self.images.items():
            image.save(OUTPUT / f"{name}.png", optimize=True)
        self.items.close()
        print(f"Wrote {len(self.images)} Draftout card icons")
