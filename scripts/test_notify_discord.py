"""External sends are mocked; verify loader routing and durable retry behavior."""
import copy
import os
import unittest
from unittest.mock import patch
from notify_discord import notify, receipt


class DiscordTests(unittest.TestCase):
    def setUp(self):
        self.env = patch.dict(os.environ, {'GH_TOKEN': 'gh', 'DISCORD_WEBHOOK_URL': 'https://discord.invalid/webhook',
            'GITHUB_REPOSITORY': 'owner/repo', 'MODRINTH_PROJECT_ID': 'project'}, clear=True)
        self.env.start(); self.addCleanup(self.env.stop)
        self.release = {'id': 1, 'tag_name': 'v0.5.3-beta', 'draft': False, 'body': 'Release notes'}
        self.manifest = {'repository': 'owner/repo', 'version': '0.5.3-beta', 'github_release_id': 1,
            'modrinth_project_id': 'project', 'versions': []}
        self.versions = {}
        for loader in ('fabric', 'neoforge'):
            version_id = loader + '-id'
            filename = f'serialcraft-{loader}-0.5.3-beta.jar'
            self.manifest['versions'].append({'id': version_id, 'loader': loader,
                'version_number': '0.5.3-beta-' + loader, 'filename': filename, 'sha512': loader + '-hash'})
            self.versions[version_id] = {'id': version_id, 'project_id': 'project', 'loaders': [loader],
                'version_number': '0.5.3-beta-' + loader, 'version_type': 'beta', 'game_versions': ['26.2'],
                'changelog': 'Notes', 'files': [{'primary': True, 'filename': filename,
                    'url': 'https://cdn.modrinth.com/' + filename, 'hashes': {'sha512': loader + '-hash'}}]}
        self.network = patch('notify_discord.request', side_effect=self.request)
        self.network.start(); self.addCleanup(self.network.stop)

    def request(self, url, token, **kwargs):
        if '/releases/1' in url:
            if kwargs.get('method') == 'PATCH': self.release.update(kwargs['data'])
            return copy.deepcopy(self.release)
        if url.endswith('/project/project'):
            return {'id': 'project', 'slug': 'serialcraft', 'title': 'SerialCraft'}
        return copy.deepcopy(self.versions[url.rsplit('/', 1)[-1]])

    def test_one_message_per_loader_uses_exact_version_and_jar_links(self):
        with patch('notify_discord.send_discord', return_value='message') as send:
            result = notify(self.manifest)
        self.assertEqual(result['sent'], ['fabric-id', 'neoforge-id'])
        self.assertEqual(send.call_count, 2)
        for call, loader in zip(send.call_args_list, ('fabric', 'neoforge')):
            payload = call.args[1]
            self.assertEqual(payload['embeds'][0]['url'], f'https://modrinth.com/mod/serialcraft/version/{loader}-id')
            self.assertIn(f'serialcraft-{loader}-0.5.3-beta.jar', payload['embeds'][0]['fields'][-1]['value'])
            self.assertEqual(payload['allowed_mentions'], {'parse': []})

    def test_retry_does_not_resend_confirmed_versions(self):
        with patch('notify_discord.send_discord', return_value='message') as send:
            notify(self.manifest)
            result = notify(self.manifest)
        self.assertEqual(send.call_count, 2)
        self.assertEqual(result['sent'], [])
        self.assertEqual(result['skipped'], ['fabric-id', 'neoforge-id'])

    def test_partial_failure_retries_only_the_unconfirmed_loader(self):
        with patch('notify_discord.send_discord', side_effect=['first', RuntimeError('unavailable')]):
            with self.assertRaisesRegex(RuntimeError, 'unavailable'): notify(self.manifest)
        self.assertIn(receipt('fabric-id'), self.release['body'])
        self.assertNotIn(receipt('neoforge-id'), self.release['body'])
        with patch('notify_discord.send_discord', return_value='second') as send:
            result = notify(self.manifest)
        self.assertEqual(send.call_count, 1)
        self.assertEqual(result['sent'], ['neoforge-id'])

    def test_mismatched_loader_is_not_announced(self):
        self.versions['fabric-id']['loaders'] = ['neoforge']
        with patch('notify_discord.send_discord') as send:
            with self.assertRaisesRegex(ValueError, 'does not match'): notify(self.manifest)
            send.assert_not_called()

    def test_previous_successful_legacy_announcement_is_migrated(self):
        with patch('notify_discord.send_discord', return_value='message') as send:
            result = notify(self.manifest, legacy_receipts={'project': {'neoforge-id': 'verified-old-run'}})
        self.assertEqual(send.call_count, 1)
        self.assertEqual(result['sent'], ['fabric-id'])
        self.assertIn(receipt('neoforge-id'), self.release['body'])

    def test_wrong_project_is_rejected(self):
        self.manifest['modrinth_project_id'] = 'another-project'
        with patch('notify_discord.send_discord') as send:
            with self.assertRaisesRegex(ValueError, 'different repository or project'): notify(self.manifest)
            send.assert_not_called()

    def test_explicit_resend_overrides_old_receipts_once_without_repeating_on_retry(self):
        legacy = {'project': {'neoforge-id': 'verified-old-run'}}
        with patch('notify_discord.send_discord', return_value='message') as send:
            notify(self.manifest, legacy_receipts=legacy)
            result = notify(self.manifest, legacy_receipts=legacy, resend_token='restore-original-embed-v1')
            retry = notify(self.manifest, legacy_receipts=legacy, resend_token='restore-original-embed-v1')
            regular = notify(self.manifest, legacy_receipts=legacy)
        self.assertEqual(result['sent'], ['fabric-id', 'neoforge-id'])
        self.assertEqual(send.call_count, 3)
        self.assertEqual(retry['sent'], [])
        self.assertEqual(regular['sent'], [])

    def test_invalid_resend_token_cannot_send_a_notification(self):
        with patch('notify_discord.send_discord') as send:
            with self.assertRaisesRegex(ValueError, 'Invalid Discord resend token'):
                notify(self.manifest, resend_token='invalid token')
            send.assert_not_called()
