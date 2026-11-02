"""Announce exact verified Modrinth versions; persist per-version delivery receipts."""
import argparse
import json
import os
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request
from publish_release import request


def receipt(version_id):
    return f'<!-- serialcraft-discord:{version_id} -->'


def discord_payload(version, project, loader):
    primary = next(file for file in version['files'] if file['primary'])
    url = f'https://modrinth.com/mod/{project["slug"]}/version/{version["id"]}'
    label = 'NeoForge' if loader == 'neoforge' else 'Fabric'
    embed = {
        'title': f'SerialCraft {version["version_number"]} · {label}',
        'url': url, 'color': 0x4995B6,
        'description': (version.get('changelog') or 'Nueva versión de SerialCraft.')[:1000],
        'fields': [
            {'name': 'Loader', 'value': label, 'inline': True},
            {'name': 'Minecraft', 'value': ', '.join(version['game_versions']), 'inline': True},
            {'name': 'Tipo', 'value': version['version_type'].capitalize(), 'inline': True},
            {'name': 'Modrinth', 'value': f'[Ver esta versión]({url})'},
            {'name': 'JAR', 'value': f'[{primary["filename"]}]({primary["url"]})'},
        ],
    }
    payload = {'username': project.get('title', 'SerialCraft'), 'allowed_mentions': {'parse': []}, 'embeds': [embed]}
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


def notify(manifest, *, legacy_receipts=None):
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
        marker = receipt(entry['id'])
        if marker in body:
            skipped.append(entry['id'])
            continue
        if entry['id'] in migrated:
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
        body = body.rstrip() + '\n\n' + marker
        gh(release_path, method='PATCH', data={'body': body})
        sent.append(entry['id'])
        print(f'Discord announced {entry["loader"]}: https://modrinth.com/mod/{project["slug"]}/version/{entry["id"]} (message {message_id})')
    return {'sent': sent, 'skipped': skipped}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--legacy-receipts', type=Path, default=Path('maintenance/discord-notifications.json'))
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding='utf-8'))
    legacy = json.loads(args.legacy_receipts.read_text(encoding='utf-8')) if args.legacy_receipts.exists() else {}
    result = notify(manifest, legacy_receipts=legacy)
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a', encoding='utf-8') as summary:
            summary.write(f'\n## Discord\n\nSent: {len(result["sent"])}. Already announced: {len(result["skipped"])}.\n')


if __name__ == '__main__':
    main()
