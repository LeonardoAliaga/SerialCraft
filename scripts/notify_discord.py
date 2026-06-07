import os
import requests
import json

MODRINTH_TOKEN = os.environ.get("MODRINTH_TOKEN")
DISCORD_WEBHOOK_URL = os.environ.get("DISCORD_WEBHOOK_URL")
PROJECT_ID = os.environ.get("MODRINTH_PROJECT_ID")  # ej: "tu-mod-slug"

def get_latest_version():
    headers = {}
    if MODRINTH_TOKEN:
        headers["Authorization"] = MODRINTH_TOKEN

    url = f"https://api.modrinth.com/v2/project/{PROJECT_ID}/version"
    response = requests.get(url, headers=headers)
    response.raise_for_status()
    versions = response.json()
    return versions[0] if versions else None  # La más reciente va primero

def get_project_info():
    headers = {}
    if MODRINTH_TOKEN:
        headers["Authorization"] = MODRINTH_TOKEN

    url = f"https://api.modrinth.com/v2/project/{PROJECT_ID}"
    response = requests.get(url, headers=headers)
    response.raise_for_status()
    return response.json()

def send_discord_notification(version, project):
    # Construir la lista de archivos descargables
    files = version.get("files", [])
    primary_file = next((f for f in files if f.get("primary")), files[0] if files else None)

    # Loaders y game versions soportados
    loaders = ", ".join(version.get("loaders", []))
    game_versions = ", ".join(version.get("game_versions", []))
    changelog = version.get("changelog") or "Sin changelog."

    # Truncar changelog si es muy largo
    if len(changelog) > 1000:
        changelog = changelog[:997] + "..."

    # Embed de Discord
    embed = {
        "title": f"🆕 Nueva versión: {version['name']}",
        "description": changelog,
        "color": 0x1bd96a,  # Verde de Modrinth
        "url": f"https://modrinth.com/mod/{PROJECT_ID}/version/{version['version_number']}",
        "thumbnail": {
            "url": project.get("icon_url", "")
        },
        "fields": [
            {
                "name": "📦 Versión",
                "value": version["version_number"],
                "inline": True
            },
            {
                "name": "🔧 Loaders",
                "value": loaders or "N/A",
                "inline": True
            },
            {
                "name": "🎮 Minecraft",
                "value": game_versions or "N/A",
                "inline": True
            },
            {
                "name": "📁 Tipo",
                "value": version.get("version_type", "release").capitalize(),
                "inline": True
            }
        ],
        "footer": {
            "text": "Modrinth",
            "icon_url": "https://cdn.modrinth.com/modrinth-new.png"
        }
    }

    # Botón de descarga (si hay archivo)
    if primary_file:
        embed["fields"].append({
            "name": "⬇️ Descarga directa",
            "value": f"[{primary_file['filename']}]({primary_file['url']})",
            "inline": False
        })

    payload = {
        "username": project.get("title", "Mod Update"),
        "avatar_url": project.get("icon_url", ""),
        "embeds": [embed]
    }

    response = requests.post(
        DISCORD_WEBHOOK_URL,
        data=json.dumps(payload),
        headers={"Content-Type": "application/json"}
    )
    response.raise_for_status()
    print(f"✅ Notificación enviada: {version['name']}")

if __name__ == "__main__":
    print("Obteniendo versión más reciente de Modrinth...")
    version = get_latest_version()
    project = get_project_info()

    if not version:
        print("No se encontraron versiones.")
        exit(0)

    send_discord_notification(version, project)