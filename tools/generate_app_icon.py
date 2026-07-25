from __future__ import annotations

from pathlib import Path

import aggdraw
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIR = ROOT / "src" / "main" / "resources" / "icons"
MASTER_SIZE = 1024
SUPERSAMPLE = 4


def scaled(value: float) -> float:
    return value * MASTER_SIZE / 512


def draw_icon(size: int) -> Image.Image:
    canvas_size = size * SUPERSAMPLE
    scale = canvas_size / 512
    image = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    def box(values: tuple[float, float, float, float]) -> tuple[int, int, int, int]:
        return tuple(round(value * scale) for value in values)

    # The selected C concept uses a confident Fluent violet squircle with transparent corners.
    draw.rounded_rectangle(
        box((28, 28, 484, 484)),
        radius=round(108 * scale),
        fill=(103, 80, 235, 255),
    )

    # Grand-piano lid silhouette. The generous white mass survives taskbar-size reduction.
    path = aggdraw.Path()
    path.moveto(112 * scale, 294 * scale)
    path.lineto(112 * scale, 116 * scale)
    path.curveto(112 * scale, 91 * scale, 132 * scale, 72 * scale, 157 * scale, 72 * scale)
    path.curveto(235 * scale, 72 * scale, 266 * scale, 124 * scale, 292 * scale, 171 * scale)
    path.curveto(318 * scale, 218 * scale, 349 * scale, 257 * scale, 400 * scale, 272 * scale)
    path.lineto(400 * scale, 294 * scale)
    path.close()
    context = aggdraw.Draw(image)
    context.path(path, aggdraw.Brush("#F8FAFC"))
    context.flush()

    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle(
        box((112, 276, 400, 440)),
        radius=round(38 * scale),
        fill=(248, 250, 252, 255),
    )
    # Square the keyboard's upper corners so it joins the lid cleanly.
    draw.rectangle(box((112, 276, 400, 330)), fill=(248, 250, 252, 255))
    draw.rectangle(box((112, 288, 400, 302)), fill=(91, 73, 215, 255))

    divider_width = max(2, round(8 * scale))
    for x in (169, 227, 285, 343):
        draw.rectangle(
            box((x - 4, 294, x + 4, 440)),
            fill=(100, 85, 222, 255),
        )

    for left in (145, 203, 319):
        draw.rounded_rectangle(
            box((left, 294, left + 45, 395)),
            radius=round(15 * scale),
            fill=(23, 28, 45, 255),
        )
        draw.rectangle(
            box((left, 294, left + 45, 344)),
            fill=(23, 28, 45, 255),
        )

    return image.resize((size, size), Image.Resampling.LANCZOS)


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    sizes = (16, 20, 24, 32, 40, 48, 64, 128, 256, 512)
    rendered = {size: draw_icon(size) for size in sizes}
    for size, image in rendered.items():
        image.save(OUTPUT_DIR / f"xianxu-{size}.png", optimize=True)
    rendered[512].save(OUTPUT_DIR / "xianxu.png", optimize=True)
    rendered[256].save(
        OUTPUT_DIR / "xianxu.ico",
        format="ICO",
        sizes=[(size, size) for size in sizes if size <= 256],
        append_images=[rendered[size] for size in sizes if size < 256],
    )


if __name__ == "__main__":
    main()
