"""Draw the Smart QR launcher icons. Run from the project root with Pillow."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"
NAVY = (11, 26, 74, 255)
BLUE = (28, 63, 170, 255)
CYAN = (61, 183, 255, 255)
WHITE = (255, 255, 255, 255)

FOREGROUND = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}
LAUNCHER = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def rounded_rect(draw, box, radius, fill):
    draw.rounded_rectangle(box, radius=radius, fill=fill)


def finder(draw, x, y, size):
    radius = size * 0.2
    rounded_rect(draw, (x, y, x + size, y + size), radius, NAVY)
    inset = size * 0.22
    rounded_rect(
        draw,
        (x + inset, y + inset, x + size - inset, y + size - inset),
        radius * 0.55,
        WHITE,
    )
    eye = size * 0.30
    ex = x + (size - eye) / 2
    ey = y + (size - eye) / 2
    rounded_rect(draw, (ex, ey, ex + eye, ey + eye), eye * 0.22, BLUE)


def draw_mark(draw, left, top, mark):
    finder_size = mark * 0.36
    finder(draw, left, top, finder_size)
    finder(draw, left + mark - finder_size, top, finder_size)
    finder(draw, left, top + mark - finder_size, finder_size)

    pattern = [
        "10101",
        "01101",
        "10110",
        "01011",
        "1010C",
    ]
    gap = mark * 0.055
    region_left = left + finder_size + gap
    region_top = top + finder_size + gap
    region = (left + mark) - region_left
    step = region / len(pattern)
    cell = step * 0.74
    for row, line in enumerate(pattern):
        for col, token in enumerate(line):
            if token == "0":
                continue
            x = region_left + col * step + (step - cell) / 2
            y = region_top + row * step + (step - cell) / 2
            color = CYAN if token == "C" else NAVY
            rounded_rect(draw, (x, y, x + cell, y + cell), cell * 0.3, color)


def background(size):
    image = Image.new("RGBA", (size, size))
    pixels = image.load()
    for y in range(size):
        t = y / max(1, size - 1)
        r = int(234 + (255 - 234) * t)
        g = int(242 + (255 - 242) * t)
        b = 255
        for x in range(size):
            pixels[x, y] = (r, g, b, 255)
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    radius = int(size * 0.42)
    glow_draw.ellipse(
        (size * 0.5 - radius, size * 0.02 - radius, size * 0.5 + radius, size * 0.02 + radius),
        fill=(255, 255, 255, 90),
    )
    glow = glow.filter(ImageFilter.GaussianBlur(radius=max(1, int(size * 0.08))))
    return Image.alpha_composite(image, glow)


def foreground(size):
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    inset = size * 0.20
    mark = size - inset * 2
    shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    rounded_rect(
        shadow_draw,
        (inset, inset + mark * 0.05, inset + mark, inset + mark + mark * 0.03),
        mark * 0.08,
        (28, 63, 170, 36),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=max(1, int(size * 0.018))))
    image = Image.alpha_composite(image, shadow)
    draw_mark(ImageDraw.Draw(image), inset, inset, mark)
    return image


def save(image, folder, name):
    path = ROOT / folder
    path.mkdir(parents=True, exist_ok=True)
    image.save(path / name, "PNG")


def main():
    for folder, size in FOREGROUND.items():
        save(background(size).convert("RGB"), folder, "ic_launcher_background.png")
        save(foreground(size), folder, "ic_launcher_foreground.png")
    for folder, size in LAUNCHER.items():
        icon = Image.alpha_composite(background(size), foreground(size)).convert("RGB")
        save(icon, folder, "ic_launcher.png")
        save(icon, folder, "ic_launcher_round.png")
    preview = Image.alpha_composite(background(1024), foreground(1024)).convert("RGB")
    preview_path = Path(__file__).resolve().parents[1] / "icon-preview.png"
    preview.save(preview_path, "PNG")
    print(preview_path)


if __name__ == "__main__":
    main()
