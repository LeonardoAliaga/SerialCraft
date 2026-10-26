import { mkdir, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { legacyRedirects, origin } from './routes.mjs'

export async function writeLegacyRedirects(config) {
  for (const [oldPath, target] of Object.entries(legacyRedirects)) {
    const file = resolve(config.outDir, '.' + oldPath)
    const destination = origin + target
    await mkdir(dirname(file), { recursive: true })
    // GitHub Pages cannot configure per-path HTTP 301s. An immediate HTML
    // refresh is Google's supported permanent-redirect fallback for static hosts.
    await writeFile(file, `<!doctype html>
<html lang="${target.startsWith('/en/') ? 'en' : 'es'}"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Page moved | SerialCraft</title>
<link rel="canonical" href="${destination}">
<meta http-equiv="refresh" content="0;url=${destination}">
</head><body><p>SerialCraft: this page has moved / Esta página se ha trasladado.</p>
<p><a href="${destination}">Continue / Continuar</a></p></body></html>\n`)
  }
}
