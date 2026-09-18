import os
import math
from PIL import Image, ImageDraw

PROJECT_RES = os.path.join(os.path.dirname(__file__), "app", "src", "main", "res")

NAVY_DARK = (1, 7, 54, 255)       # #010736
NAVY_CARD = (13, 28, 66, 255)     # #0D1C42
NAVY_BLUE = (34, 57, 111, 255)    # #22396F
WARM_CREAM = (252, 241, 208, 255) # #FCF1D0


def render_side_rotate_icon(size: int, is_round: bool = False) -> Image.Image:
    super_size = size * 4
    img = Image.new("RGBA", (super_size, super_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # 1. Background
    if is_round:
        draw.ellipse([0, 0, super_size - 1, super_size - 1], fill=NAVY_DARK)
        draw.ellipse([0, 0, super_size - 1, super_size - 1], outline=NAVY_BLUE, width=int(super_size * 0.025))
    else:
        radius = int(super_size * 0.22)
        draw.rounded_rectangle([0, 0, super_size - 1, super_size - 1], radius=radius, fill=NAVY_DARK)
        draw.rounded_rectangle([0, 0, super_size - 1, super_size - 1], radius=radius, outline=NAVY_BLUE, width=int(super_size * 0.025))

    # Scale mapping from 24x24 coordinate space:
    def s(v):
        return v * (super_size / 24.0)

    # 2. Central Smartphone
    x0, y0 = s(8.8), s(6.2)
    x1, y1 = s(15.2), s(17.8)
    p_rad = s(1.8)
    draw.rounded_rectangle([x0, y0, x1, y1], radius=p_rad, fill=NAVY_CARD, outline=WARM_CREAM, width=int(s(1.3)))

    # Screen inside
    sx0, sy0 = s(9.8), s(7.8)
    sx1, sy1 = s(14.2), s(15.2)
    s_rad = s(0.8)
    draw.rounded_rectangle([sx0, sy0, sx1, sy1], radius=s_rad, fill=NAVY_BLUE)

    # Home indicator bar
    draw.line([s(11.2), s(16.5), s(12.8), s(16.5)], fill=WARM_CREAM, width=int(s(0.8)))

    # 3. Flowing Clockwise Dual Arcs (Option 1)
    cx, cy = 12.0, 12.0
    R = 8.6
    arc_w = int(s(1.8))
    bbox = [s(cx - R), s(cy - R), s(cx + R), s(cy + R)]

    # Top Arc from ~210 deg around top to 360 deg (12 o'clock through right)
    draw.arc(bbox, start=215, end=360, fill=WARM_CREAM, width=arc_w)

    # Top Arrowhead: centered at (20.6, 12) pointing straight down
    # Tip at (20.6, 15.5), wings at (17.6, 10.2) and (23.6, 10.2)
    draw.polygon([
        (s(20.6), s(15.5)),
        (s(17.6), s(10.2)),
        (s(23.6), s(10.2))
    ], fill=WARM_CREAM)

    # Bottom Arc from ~35 deg around bottom to 180 deg (6 o'clock through left)
    draw.arc(bbox, start=35, end=180, fill=WARM_CREAM, width=arc_w)

    # Bottom Arrowhead: centered at (3.4, 12) pointing straight up
    # Tip at (3.4, 8.5), wings at (6.4, 13.8) and (0.4, 13.8)
    draw.polygon([
        (s(3.4), s(8.5)),
        (s(6.4), s(13.8)),
        (s(0.4), s(13.8))
    ], fill=WARM_CREAM)

    return img.resize((size, size), Image.Resampling.LANCZOS)


def main():
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }

    for folder, size in densities.items():
        folder_path = os.path.join(PROJECT_RES, folder)
        os.makedirs(folder_path, exist_ok=True)

        icon_img = render_side_rotate_icon(size, is_round=False)
        icon_img.save(os.path.join(folder_path, "ic_launcher.webp"), "WEBP")

        round_img = render_side_rotate_icon(size, is_round=True)
        round_img.save(os.path.join(folder_path, "ic_launcher_round.webp"), "WEBP")

        print(f"Generated {size}x{size} icons in {folder}")

    # Also generate a test high-res preview icon for inspection
    preview_img = render_side_rotate_icon(512, is_round=False)
    preview_img.save(os.path.join(os.path.dirname(__file__), "test_icon.png"))
    print("All mipmap icons and test_icon.png regenerated successfully.")


if __name__ == "__main__":
    main()
