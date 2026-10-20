from pathlib import Path
from zipfile import ZipFile

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/resources/assets/academy/textures/item/darkmatter.png"
OUTPUT = ROOT / "src/main/resources/assets/academy/textures/item"
CLIENT_JAR = Path.home() / ".gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar"
FRAME_TIME = 2

MASKS = {
    "darkmatter_tool": "assets/minecraft/textures/item/netherite_pickaxe.png",
    "darkmatter_sword": "assets/minecraft/textures/item/netherite_sword.png",
    "darkmatter_spear": "assets/minecraft/textures/item/wooden_spear.png",
    "darkmatter_spear_in_hand": "assets/minecraft/textures/item/wooden_spear_in_hand.png",
    "darkmatter_trident": "assets/minecraft/textures/item/trident.png",
    "darkmatter_bow": "assets/minecraft/textures/item/bow.png",
    "darkmatter_bow_pulling_0": "assets/minecraft/textures/item/bow_pulling_0.png",
    "darkmatter_bow_pulling_1": "assets/minecraft/textures/item/bow_pulling_1.png",
    "darkmatter_bow_pulling_2": "assets/minecraft/textures/item/bow_pulling_2.png",
    "darkmatter_crossbow": "assets/minecraft/textures/item/crossbow_standby.png",
    "darkmatter_crossbow_pulling_0": "assets/minecraft/textures/item/crossbow_pulling_0.png",
    "darkmatter_crossbow_pulling_1": "assets/minecraft/textures/item/crossbow_pulling_1.png",
    "darkmatter_crossbow_pulling_2": "assets/minecraft/textures/item/crossbow_pulling_2.png",
    "darkmatter_crossbow_arrow": "assets/minecraft/textures/item/crossbow_arrow.png",
    "darkmatter_crossbow_firework": "assets/minecraft/textures/item/crossbow_firework.png",
    "darkmatter_mace": "assets/minecraft/textures/item/mace.png",
    "darkmatter_arrow": "assets/minecraft/textures/item/arrow.png",
    "darkmatter_feather": "assets/minecraft/textures/item/feather.png",
    "dark_matter_helmet": "assets/minecraft/textures/item/netherite_helmet.png",
    "dark_matter_chestplate": "assets/minecraft/textures/item/netherite_chestplate.png",
    "dark_matter_leggings": "assets/minecraft/textures/item/netherite_leggings.png",
    "dark_matter_boots": "assets/minecraft/textures/item/netherite_boots.png",
}

EQUIPMENT_MASKS = {
    "humanoid": "assets/minecraft/textures/entity/equipment/humanoid/netherite.png",
    "humanoid_baby": "assets/minecraft/textures/entity/equipment/humanoid_baby/netherite.png",
    "humanoid_leggings": "assets/minecraft/textures/entity/equipment/humanoid_leggings/netherite.png",
}

ENTITY_MASKS = {
    "darkmatter_trident": "assets/minecraft/textures/entity/trident/trident.png",
}


def palette(frame: Image.Image) -> list[tuple[int, int, int, int]]:
    colors = [pixel for pixel in frame.getdata() if pixel[3] > 0]
    colors.sort(key=lambda color: 0.2126 * color[0] + 0.7152 * color[1] + 0.0722 * color[2])
    if not colors:
        return [(255, 255, 255, 255)]
    return colors


def recolor(mask: Image.Image, colors: list[tuple[int, int, int, int]]) -> Image.Image:
    rgba = mask.convert("RGBA")
    result = Image.new("RGBA", rgba.size)
    pixels = []
    for red, green, blue, alpha in rgba.getdata():
        if alpha == 0:
            pixels.append((0, 0, 0, 0))
            continue
        luminance = (red + green + blue) / (3.0 * 255.0)
        index = round(luminance * (len(colors) - 1))
        source = colors[max(0, min(index, len(colors) - 1))]
        pixels.append((source[0], source[1], source[2], alpha))
    result.putdata(pixels)
    return result


def main() -> None:
    source = Image.open(SOURCE).convert("RGBA")
    if source.width <= 0 or source.height % source.width != 0:
        raise ValueError("darkmatter.png must be a vertical square-frame animation")
    frame_size = source.width
    frames = [
        source.crop((0, index * frame_size, frame_size, (index + 1) * frame_size))
        for index in range(source.height // frame_size)
    ]
    with ZipFile(CLIENT_JAR) as vanilla:
        for output_name, mask_path in MASKS.items():
            with vanilla.open(mask_path) as stream:
                mask = Image.open(stream).convert("RGBA")
            rendered = [recolor(mask, palette(frame)) for frame in frames]
            sheet = Image.new("RGBA", (mask.width, mask.height * len(rendered)))
            for index, frame in enumerate(rendered):
                sheet.paste(frame, (0, index * mask.height))
            target = OUTPUT / f"{output_name}.png"
            sheet.save(target, optimize=True)
            target.with_suffix(".png.mcmeta").write_text(
                '{\n  "animation": {\n    "frametime": %d\n  }\n}\n' % FRAME_TIME,
                encoding="utf-8",
            )
        equipment_root = ROOT / "src/main/resources/assets/academy/textures/entity/equipment"
        for layer, mask_path in EQUIPMENT_MASKS.items():
            with vanilla.open(mask_path) as stream:
                mask = Image.open(stream).convert("RGBA")
            rendered = [recolor(mask, palette(frame)) for frame in frames]
            sheet = Image.new("RGBA", (mask.width, mask.height * len(rendered)))
            for index, frame in enumerate(rendered):
                sheet.paste(frame, (0, index * mask.height))
            target = equipment_root / layer / "dark_matter.png"
            target.parent.mkdir(parents=True, exist_ok=True)
            sheet.save(target, optimize=True)
            target.with_suffix(".png.mcmeta").write_text(
                '{\n  "animation": {\n    "frametime": %d\n  }\n}\n' % FRAME_TIME,
                encoding="utf-8",
            )
        entity_root = ROOT / "src/main/resources/assets/academy/textures/entity"
        for output_name, mask_path in ENTITY_MASKS.items():
            with vanilla.open(mask_path) as stream:
                mask = Image.open(stream).convert("RGBA")
            rendered = [recolor(mask, palette(frame)) for frame in frames]
            sheet = Image.new("RGBA", (mask.width, mask.height * len(rendered)))
            for index, frame in enumerate(rendered):
                sheet.paste(frame, (0, index * mask.height))
            target = entity_root / f"{output_name}.png"
            target.parent.mkdir(parents=True, exist_ok=True)
            sheet.save(target, optimize=True)
            target.with_suffix(".png.mcmeta").write_text(
                '{\n  "animation": {\n    "frametime": %d\n  }\n}\n' % FRAME_TIME,
                encoding="utf-8",
            )


if __name__ == "__main__":
    main()
