"""Build Apple, Android and social preview PNGs from the versioned SVG artwork.
Run before VitePress build (requires CairoSVG and Pillow).
"""
from io import BytesIO
from pathlib import Path
import cairosvg
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
PUBLIC = ROOT / 'public'
SVG_LOGO = PUBLIC / 'favicon.svg'
SVG_CAT = PUBLIC / 'media' / 'serialcraft-mascot-dark.svg'

def render_svg(file, width, height):
    return Image.open(BytesIO(cairosvg.svg2png(url=str(file), output_width=width, output_height=height))).convert('RGBA')

def font(size, bold=False):
    fonts = ['DejaVuSans-Bold.ttf', 'LiberationSans-Bold.ttf'] if bold else ['DejaVuSans.ttf', 'LiberationSans-Regular.ttf']
    for candidate in fonts:
        try:
            return ImageFont.truetype(candidate, size)
        except OSError:
            pass
    return ImageFont.load_default(size=size)

def create_icon(size):
    canvas = Image.new('RGBA', (size, size), '#f9f3ee')
    canvas.alpha_composite(render_svg(SVG_LOGO, size, size))
    return canvas.convert('RGB')

def create_card(lang):
    w, h = 1200, 630
    card = Image.new('RGB', (w, h), '#191c20')
    draw = ImageDraw.Draw(card)
    draw.rounded_rectangle((30, 30, w - 30, h - 30), radius=32, fill='#202428')
    for i in range(5):
        y = 80 + i * 102
        draw.line([(36, y), (205, y), (239, y + 36), (368, y + 36)], fill='#29383b', width=3)
        draw.rectangle((360, y + 29, 376, y + 45), fill='#345358')
    draw.ellipse((84, 115, 465, 495), fill='#263239')
    draw.ellipse((130, 161, 423, 454), fill='#2c373c')
    cat = render_svg(SVG_CAT, 337, 490)
    card.paste(cat, (115, 93), mask=cat.getchannel('A'))
    draw.rounded_rectangle((516, 82, 585, 150), radius=17, fill='#f9f3ee')
    logo = render_svg(SVG_LOGO, 66, 66)
    card.paste(logo, (518, 83), mask=logo.getchannel('A'))
    draw.text((603, 99), 'SERIALCRAFT', fill='#9cddcf', font=font(22, True))
    draw.text((510, 180), 'SerialCraft', fill='#f6f5f0', font=font(74, True))
    if lang == 'es':
        draw.text((517, 291), 'El puente entre mundos', fill='#d0d5db', font=font(32, True))
        draw.text((519, 362), 'Conecta Arduino y ESP32', fill='#b2bcc3', font=font(27))
        draw.text((519, 400), 'con Minecraft.', fill='#b2bcc3', font=font(27))
        foot = 'DOCUMENTACIÓN EN ESPAÑOL'
    else:
        draw.text((517, 291), 'The Bridge Between Worlds', fill='#d0d5db', font=font(30, True))
        draw.text((519, 362), 'Connect Arduino and ESP32', fill='#b2bcc3', font=font(27))
        draw.text((519, 400), 'with Minecraft.', fill='#b2bcc3', font=font(27))
        foot = 'ENGLISH DOCUMENTATION'
    draw.rounded_rectangle((514, 492, 866, 548), radius=14, fill='#2d866e')
    draw.text((538, 505), 'serialcraft.xyz', font=font(26, True), fill='#ffffff')
    draw.text((520, 568), foot, font=font(14, True), fill='#6f9b9c')
    return card

def main():
    for source in (SVG_LOGO, SVG_CAT):
        if not source.is_file():
            raise FileNotFoundError(source)
    for size, dest in [(180, 'apple-touch-icon.png'), (192, 'icon-192.png'), (512, 'icon-512.png')]:
        create_icon(size).save(PUBLIC / dest, optimize=True)
    (PUBLIC / 'og').mkdir(parents=True, exist_ok=True)
    for lang in ('es', 'en'):
        create_card(lang).save(PUBLIC / 'og' / f'serialcraft-{lang}.png', optimize=True)
    for dest in ['apple-touch-icon.png', 'icon-192.png', 'icon-512.png', 'og/serialcraft-es.png', 'og/serialcraft-en.png']:
        p = PUBLIC / dest
        with Image.open(p) as im:
            print(f'Asset {dest}: {im.width}x{im.height} ({p.stat().st_size} bytes)')

if __name__ == '__main__':
    main()
