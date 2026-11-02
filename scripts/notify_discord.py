"""Announce exact verified Modrinth versions; persist per-version delivery receipts."""
import argparse
import json
import os
import re
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request
from publish_release import request


def receipt(version_id, resend_token=None):
    suffix = f'-resend-{resend_token}' if resend_token else ''
    return f'<!-- serialcraft-discord:{version_id}{suffix} -->'


def discord_payload(version, project, loader):
    primary = next(file for file in version['files'] if file['primary'])
    url = f'https://modrinth.com/mod/{project["slug"]}/version/{version["id"]}'
    changelog = version.get('changelog') or 'Sin changelog.'
    if len(changelog) > 1000:
        changelog = changelog[:997] + '...'
    embed = {
        'title': f'🆕 Nueva versión: {version.get("name", "SerialCraft " + version["version_number"])}',
        'description': changelog,
        'color': 0x1bd96a,
        'url': url,
        'fields': [
            {'name': '📦 Versión', 'value': version['version_number'], 'inline': True},
            {'name': '🔧 Loaders', 'value': ', '.join(version.get('loaders', [])) or 'N/A', 'inline': True},
            {'name': '🎮 Minecraft', 'value': ', '.join(version.get('game_versions', [])) or 'N/A', 'inline': True},
            {'name': '📁 Tipo', 'value': version.get('version_type', 'release').capitalize(), 'inline': True},
            {'name': '⬇️ Descarga directa', 'value': f'[{primary["filename"]}]({primary["url"]})', 'inline': False},
        ],
        'footer': {'text': 'Modrinth', 'icon_url': 'https://cdn.modrinth.com/modrinth-new.png'},
    }
    payload = {'username': project.get('title', 'Mod Update'), 'allowed_mentions': {'parse': []}, 'embeds': [embed]}
    if project.get('icon_url'):
        embed['thumbnail'] = {'url': project['icon_url']}
        payload['avatar_url'] = project['icon_url']
    return payload


def send_discord(webhook, payload):
    parts = urllib.parse.urlsplit(webhook)
    query = dict(urllib.parse.parse_qsl(parts.query))
    query['wait'] = 'true'
    url = urllib.parse.urlunsplit(parts._replace(query=urllib.parse.urlencode(query)))
    message = urllib.request.Request(url, data=json.dumps(payload).encode('utf-8'),
                                    headers={'Content-Type': 'application/json', 'User-Agent': 'SerialCraft-release'}, method='POST')
    try:
        with urllib.request.urlopen(message, timeout=30) as response:
            result = json.load(response)
    except urllib.error.HTTPError as error:
        raise RuntimeError(f'Discord notification failed: HTTP {error.code}') from None
    except urllib.error.URLError:
        raise RuntimeError('Discord notification failed: network error') from None
    if not result.get('id'):
        raise RuntimeError('Discord did not confirm a message ID')
    return result['id']


def notify(manifest, *, legacy_receipts=None, resend_token=None):
    if resend_token is not None and not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", resend_token):
        raise ValueError("Invalid Discord resend token")
    token, webhook = os.environ.get('GH_TOKEN', ''), os.environ.get('DISCORD_WEBHOOK_URL', '')
    if not token or not webhook:
        raise ValueError('GH_TOKEN and DISCORD_WEBHOOK_URL are required')
    repository, project_id = os.environ['GITHUB_REPOSITORY'], os.environ['MODRINTH_PROJECT_ID']
    if manifest['repository'] != repository or manifest['modrinth_project_id'] != project_id:
        raise ValueError('Publication manifest belongs to a different repository or project')
    gh = lambda path, **kwargs: request(f'https://api.github.com/repos/{repository}' + path, 'Bearer ' + token, **kwargs)
    mr = lambda path: request('https://api.modrinth.com/v2' + path, os.environ.get('MODRINTH_TOKEN', ''))
    release_path = f'/releases/{manifest["github_release_id"]}'
    release = gh(release_path)
    if release['draft'] or release['tag_name'] != 'v' + manifest['version']:
        raise ValueError('GitHub release is not the published version in the manifest')
    project, body = mr('/project/' + project_id), release.get('body', '')
    migrated = (legacy_receipts or {}).get(project_id, {})
    sent, skipped = [], []
    for entry in manifest['versions']:
        marker = receipt(entry['id'], resend_token)
        if marker in body:
            skipped.append(entry['id'])
            continue
        if not resend_token and entry['id'] in migrated:
            body = body.rstrip() + '\n\n' + marker
            gh(release_path, method='PATCH', data={'body': body})
            skipped.append(entry['id'])
            print(f'Previously announced {entry["loader"]}: {entry["id"]}; receipt migrated.')
            continue
        version = mr('/version/' + entry['id'])
        primary = next(file for file in version['files'] if file['primary'])
        if (version['project_id'] != project_id or version['loaders'] != [entry['loader']]
                or version['version_number'] != entry['version_number']
                or primary['filename'] != entry['filename']
                or primary['hashes'].get('sha512') != entry['sha512']):
            raise ValueError('Modrinth version does not match the verified JAR manifest')
        message_id = send_discord(webhook, discord_payload(version, project, entry['loader']))
        if resend_token and receipt(entry['id']) not in body:
            body = body.rstrip() + '\n\n' + receipt(entry['id'])
        body = body.rstrip() + '\n\n' + marker
        gh(release_path, method='PATCH', data={'body': body})
        sent.append(entry['id'])
        print(f'Discord announced {entry["loader"]}: https://modrinth.com/mod/{project["slug"]}/version/{entry["id"]} (message {message_id})')
    return {'sent': sent, 'skipped': skipped}


def resend_from_event():
    if os.environ.get('GITHUB_EVENT_NAME') != 'push' or not os.environ.get('GITHUB_EVENT_PATH'):
        return None
    event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text(encoding='utf-8'))
    message = (event.get('head_commit') or {}).get('message', '')
    match = re.search(r'\[resend-discord:([A-Za-z0-9_-]{1,64})\]', message)
    return match[1] if match else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--legacy-receipts', type=Path, default=Path('maintenance/discord-notifications.json'))
    parser.add_argument('--resend-token', help='Explicit one-time resend; the same token is skipped on retries')
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding='utf-8'))
    legacy = json.loads(args.legacy_receipts.read_text(encoding='utf-8')) if args.legacy_receipts.exists() else {}
    result = notify(manifest, legacy_receipts=legacy, resend_token=args.resend_token or resend_from_event())
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a', encoding='utf-8') as summary:
            summary.write(f'\n## Discord\n\nSent: {len(result["sent"])}. Already announced: {len(result["skipped"])}.\n')


if __name__ == '__main__':
    main()
