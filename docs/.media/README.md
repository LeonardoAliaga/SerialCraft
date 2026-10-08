# SerialCraft 0.4.6 — contrato de medios

Estas páginas se editan en la rama `test`. El catálogo está en `docs/.vitepress/media-slots.mts`.

## Cómo agregar una foto, captura o video (sin editar el tutorial)

1. Abre [el catálogo](../.vitepress/media-slots.mts) y encuentra el ID de la toma en Notion (ejemplo: `H02`).
2. Usa exactamente el nombre de archivo y extensión de la entrada (`sc046-h02-arduino-cableado.webp`).
3. Coloca el archivo en `docs/public/media/0.4.6/`. Para fotos usa `webp`; para videos breves `mp4`.
4. Ejecuta desde `docs/`: `npm ci` (solo cuando necesites instalar) y `npm run docs:build`.
5. VitePress convertirá **automáticamente** el marcador `<!-- SC046_MEDIA:H02 -->` en imagen o video con texto alternativo y pie cuando encuentre el archivo. Si falta, el marcador es invisible para visitantes: no aparecen imágenes rotas.

Cada ID tiene un nombre único reutilizable en varias páginas. Solo agrega un archivo por ID, no copies el mismo recurso en cada tutorial.

## Reglas de calidad

- Las imágenes y videos deben documentar resultados reales de la versión 0.4.6-beta; no simules fotos ni terminales como prueba de hardware.
- No publiques contraseñas, tokens Wi-Fi, rutas personales ni identificadores sensibles.
- Captura el estado inicial, acción y resultado del experimento, e identifica cuál de las tres escalas estás mostrando.
- Asegúrate de que el voltaje del ESP32 sea 3,3 V. LED con resistencia de corriente.
- **No subas grabaciones grandes al repositorio:** los archivos de video largos conviene alojarlos en una plataforma de video externa; en ese caso sustituye el bloque correspondiente por un enlace/embed verificado o pide adaptar el marcador. Los clips `mp4` previstos aquí deben ser breves y optimizados.
- Comprueba permiso de publicación de cada persona que aparezca y licencias de música.
- La compilación solo valida que el HTML se genere; la verificación física requiere una prueba del montaje.

## Ejemplo rápido

`H02` aparece en LED y potenciómetro. Una vez que guardes `docs/public/media/0.4.6/sc046-h02-arduino-cableado.webp`, aparecerá en ambas páginas en el siguiente build, sin tocar el Markdown.

**Plan de rodaje en Notion:** https://app.notion.com/p/3f3168e004f08192aed2f8470a648fc3
