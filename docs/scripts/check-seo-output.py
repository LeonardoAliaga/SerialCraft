"""Verify the deployed HTML contract, including the routes previously returning 404."""
from html.parser import HTMLParser
import json
from pathlib import Path
import subprocess
from urllib.parse import unquote, urljoin, urlsplit
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / '.vitepress/dist'
ORIGIN = 'https://serialcraft.xyz'
routes = json.loads(subprocess.check_output([
    'node', '--input-type=module', '-e',
    "import {legacyRedirects,pairs} from './.vitepress/routes.mjs'; console.log(JSON.stringify({legacyRedirects,pairs}))"
], cwd=ROOT, text=True))


class Page(HTMLParser):
    def __init__(self, text):
        super().__init__(convert_charrefs=True)
        self.canonicals = []
        self.hrefs = []
        self.alternates = []
        self.robots = []
        self.refresh = []
        self.language = None
        self.feed(text)

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'html':
            self.language = attrs.get('lang')
        if tag == 'link' and attrs.get('rel') == 'canonical':
            self.canonicals.append(attrs.get('href'))
        if tag == 'link' and attrs.get('rel') == 'alternate':
            self.alternates.append(attrs.get('href'))
        if tag == 'a' and attrs.get('href'):
            self.hrefs.append(attrs['href'])
        if tag == 'meta' and attrs.get('name') == 'robots':
            self.robots.append(attrs.get('content', ''))
        if tag == 'meta' and attrs.get('http-equiv', '').lower() == 'refresh':
            self.refresh.append(attrs.get('content'))


def route(file):
    file = str(file)
    return '/' + (file[:-10] if file.endswith('index.html') else file[:-5])


assert (DIST / 'index.html').is_file(), 'VitePress did not produce HTML'
assert (DIST / 'en/tutorials/digital-analog.html').is_file()
assert (DIST / 'en/tutorials/troubleshooting.html').is_file()
pages = {route(file.relative_to(DIST)): Page(file.read_text())
         for file in DIST.rglob('*.html')
         if file.name != '404.html' and '/' + str(file.relative_to(DIST)) not in routes['legacyRedirects']}
sitemap = ET.fromstring((DIST / 'sitemap.xml').read_text())
urls = {node.text for node in sitemap.iter() if node.tag.endswith('}loc')}
assert urls == {ORIGIN + path for path in pages}, 'Sitemap must contain exactly the canonical documentation pages'

for path, page in pages.items():
    assert page.canonicals == [ORIGIN + path], f'{path}: inconsistent canonical'
    expected_language = 'en' if path.startswith(('/en/', '/versiones/en/')) else 'es'
    assert page.language == expected_language, f'{path}: incorrect document language'
    assert not any('noindex' in value for value in page.robots), f'{path}: unexpected noindex'
    for href in page.hrefs + page.alternates:
        parsed = urlsplit(urljoin(ORIGIN + path, href))
        if parsed.netloc != 'serialcraft.xyz':
            continue
        target = unquote(parsed.path)
        if target in pages:
            continue
        # Linked documents must use the selected clean URL, while downloads,
        # generated images and other real public files remain legitimate links.
        assert not (target.endswith('.html') and target[:-5] in pages), f'{path}: noncanonical link {href}'
        assert not target.endswith('.md'), f'{path}: unresolved Markdown link {href}'
        assert (DIST / target.lstrip('/')).is_file(), f'{path}: broken internal link {href}'

for old, target in routes['legacyRedirects'].items():
    assert target in pages, f'{old}: redirect target missing'
    page = Page((DIST / old.lstrip('/')).read_text())
    assert page.canonicals == [ORIGIN + target]
    assert page.refresh == ['0;url=' + ORIGIN + target]
    assert ORIGIN + old not in urls, f'{old}: a redirect must not be in the sitemap'

for pair in routes['pairs']:
    paths = ['/' + file.replace('index.md', '').removesuffix('.md') for file in pair]
    for path in paths:
        assert {ORIGIN + p for p in paths} <= set(pages[path].alternates), f'{path}: missing reciprocal hreflang'

assert 'noindex' in (DIST / '404.html').read_text()
print(f'SEO output: {len(pages)} canonical pages, reciprocal hreflang, working internal links and {len(routes["legacyRedirects"])} legacy redirects OK')
