"""Build static card icons from Draftout's original goal textures and render recipes.

The source PNGs are copied unchanged under assets/goal. Cycling icons use the first
original frame; overlays and stack counts are baked into the card image. No client
resource pack or Draftout mod is needed: Bingo sends these datapack images itself.
"""
import io
import json
from pathlib import Path
from zipfile import ZipFile

from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
NS = "yet-another-minecraft-bingo"
OUTPUT = ROOT / "common/src/main/resources/data" / NS / NS / "images/draftout"
RECIPES = json.loads((HERE / "icons.json").read_text())
SIZE = 128


class GoalIcons:
    def __init__(self):
        self.images = {}
        self.items = ZipFile(ROOT / "common/images.zip")
        self.digits = Image.open(
            ROOT / "common/src/main/resources" / NS / "numbers.png"
        ).convert("RGBA")

    def texture(self, name, crop=None):
        with Image.open(HERE / "assets/goal" / name) as source:
            image = source.convert("RGBA")
        if crop:
            image = image.crop(crop)
        return image.resize((SIZE, SIZE), Image.Resampling.NEAREST)

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
            image.alpha_composite(shadow, (x + scale, y + scale))
            image.alpha_composite(glyph, (x, y))
            x += 6 * scale

    def apply(self, key, display, item=None):
        goal, _, variant = key.partition("__")
        recipe = RECIPES.get(goal)
        if not recipe or not any(k in recipe for k in ("texture", "overlays", "count")):
            return
        texture = recipe.get("texture")
        overlays = recipe.get("overlays", [])
        if texture:
            texture = texture.format(variant=variant)
            if goal == "KILL_COLORED_SHEEP" and not (HERE / "assets/goal" / texture).exists():
                # Bingo includes six extra sheep colors absent from the original mod.
                # Keep their exact color using a wool item plus Draftout's skull overlay.
                image = self.item(f"{variant}_wool")
                overlays = ["overlay/skull.png"]
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
