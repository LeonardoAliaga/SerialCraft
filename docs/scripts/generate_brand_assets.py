"""Generate iOS/Android icons and branded social previews from original SVG assets.

Run before VitePress build. Requires Pillow and CairoSVG. Social previews
use versioned URLs to avoid stale Discord and social-crawler image caches.
"""
from io import BytesIO
from pathlib import Path

import cairosvg
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
PUBLIC = ROOT / "public"
SVG_FAVICON = PUBLIC / "favicon.svg"
SVG_WORDMARK = PUBLIC / "brand" / "serialcraft-wordmark.svg"
SVG_CAT = PUBLIC / "brand" / "serialcraft-cat.svg"
BRAND_BLUE = "#4995B6"


def render_svg(svg_file: Path, width: int, height: int) -> Image.Image:
    raster = cairosvg.svg2png(url=str(svg_file), output_width=width, output_height=height)
    return Image.open(BytesIO(raster)).convert("RGBA")


def fit_svg(svg_file: Path, max_width: int, max_height: int) -> Image.Image:
    # Render large before downscaling to preserve fine vector detail.
    image = render_svg(svg_file, max_width * 2, max_height * 2)
    bounds = image.getbbox()
    if not bounds:
        raise ValueError(f"The SVG is fully transparent: {svg_file}")
    image = image.crop(bounds)
    image.thumbnail((max_width, max_height), Image.Resampling.LANCZOS)
    return image


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = (
        ("DejaVuSans-Bold.ttf", "LiberationSans-Bold.ttf") if bold
        else ("DejaVuSans.ttf", "LiberationSans-Regular.ttf")
    )
    for candidate in candidates:
        try:
            return ImageFont.truetype(candidate, size)
        except OSError:
            pass
    raise RuntimeError("A compatible TrueType font is required for social previews")


def create_icon(size: int) -> Image.Image:
    icon = Image.new("RGBA", (size, size), "#F9F3EE")
    icon.alpha_composite(render_svg(SVG_FAVICON, size, size))
    return icon.convert("RGB")


def draw_background(card: Image.Image) -> None:
    w, h = card.size
    overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    d.rounded_rectangle((21, 21, w - 21, h - 21), radius=35, fill=(255, 255, 255, 11), outline=(255, 255, 255, 45), width=2)
    for i in range(4):
        y = 100 + i * 117
        d.line([(1050, y), (1090, y), (1118, y + 28), (1175, y + 28)], fill=(225, 249, 255, 52), width=3)
        d.ellipse((1042, y - 4, 1051, y + 5), fill=(225, 249, 255, 65))
    d.ellipse((810, -405, 1560, 345), outline=(238, 252, 255, 38), width=3)
    d.ellipse((835, -380, 1540, 320), outline=(238, 252, 255, 28), width=3)
    card.alpha_composite(overlay)


def create_card(lang: str) -> Image.Image:
    if lang not in ("es", "en"):
        raise ValueError("Unsupported social preview language")
    card = Image.new("RGBA", (1200, 630), BRAND_BLUE)
    draw_background(card)
    draw = ImageDraw.Draw(card)

    # Use the exact submitted mascot and floor; never crop the cat's ears or paws.
    draw.rounded_rectangle((44, 44, 500, 585), radius=36, fill="#293A44", outline="#85C1D8", width=2)
    draw.ellipse((111, 101, 437, 427), fill="#325666", outline="#466D7F", width=4)
    cat = fit_svg(SVG_CAT, 422, 510)
    cat_x = 44 + (456 - cat.width) // 2
    cat_y = 71 + (490 - cat.height) // 2
    card.alpha_composite(cat, (cat_x, cat_y))

    # Submitted wordmark includes the infinity symbol, title and author.
    logo = fit_svg(SVG_WORDMARK, 607, 279)
    logo_x = 551 + (604 - logo.width) // 2
    logo_y = 58 + (277 - logo.height) // 2
    card.alpha_composite(logo, (logo_x, logo_y))

    draw = ImageDraw.Draw(card)
    is_es = lang == "es"
    tagline = "El puente entre mundos" if is_es else "The Bridge Between Worlds"
    hardware_line = "Arduino · ESP32 · Raspberry Pi Pico y más" if is_es else "Arduino · ESP32 · Raspberry Pi Pico & more"
    detail_line = "Electrónica real en Minecraft Java" if is_es else "Real electronics in Minecraft Java"
    category = "DOCUMENTACIÓN OFICIAL" if is_es else "OFFICIAL DOCUMENTATION"

    draw.rounded_rectangle((533, 350, 1159, 570), radius=22, fill="#2D5367")
    draw.text((558, 368), tagline, font=font(31, True), fill="#FFFFFF")
    draw.text((558, 419), hardware_line, font=font(22), fill="#EAF7FC")
    draw.text((558, 452), detail_line, font=font(23), fill="#EAF7FC")
    draw.rounded_rectangle((554, 504, 846, 551), radius=14, fill="#EAF7FC")
    draw.text((573, 517), "serialcraft.xyz", font=font(22, True), fill="#224E62")
    draw.text((872, 522), category, font=font(14, True), fill="#D6F0F9")
    return card.convert("RGB")


def main() -> None:
    for required in (SVG_FAVICON, SVG_WORDMARK, SVG_CAT):
        if not required.is_file():
            raise FileNotFoundError(required)
    for size, name in ((180, "apple-touch-icon.png"), (192, "icon-192.png"), (512, "icon-512.png")):
        create_icon(size).save(PUBLIC / name, optimize=True)
    (PUBLIC / "og").mkdir(parents=True, exist_ok=True)
    for lang in ("es", "en"):
        path = PUBLIC / "og" / f"serialcraft-{lang}-v2.png"
        create_card(lang).save(path, optimize=True)
        print(f"{path.relative_to(ROOT)}: 1200 x 630, {path.stat().st_size} bytes")
    for path in (PUBLIC / "apple-touch-icon.png", PUBLIC / "icon-192.png", PUBLIC / "icon-512.png"):
        with Image.open(path) as im:
            print(f"{path.relative_to(ROOT)}: {im.width} x {im.height}")


if __name__ == "__main__":
    main()
