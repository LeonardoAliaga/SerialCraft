"""Publication regression tests: all HTTP requests are mocked."""

import hashlib
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from publish_release import publish
from validate_locales import format_signature, load_catalogue


class PublishTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.jar = Path(self.directory.name) / "serialcraft-0.5.0-beta.jar"
        self.jar.write_bytes(b"verified-build-artifact")
        self.args = (self.jar, "0.5.0-beta", "26.2", "beta", "Spanish notes", "English notes")
        self.calls = []
        self.env = patch.dict(os.environ, {
            "GH_TOKEN": "test-token", "GITHUB_REPOSITORY": "owner/repo", "GITHUB_SHA": "abc123"
        }, clear=True)
        self.env.start()
        self.addCleanup(self.env.stop)

    def draft(self):
        return {"id": 1, "draft": True, "prerelease": True, "assets": [],
                "upload_url": "https://uploads.github.com/repos/owner/repo/releases/1/assets{?name,label}",
                "html_url": "https://github.com/owner/repo/releases/tag/v0.5.0-beta"}

    def request(self, url, token, **kwargs):
        self.calls.append((url, kwargs))
        if kwargs.get("missing_ok"):
            return None
        if kwargs.get("method") == "POST" and url.endswith("/releases"):
            return self.draft()
        if kwargs.get("method") == "PATCH":
            return {**self.draft(), "draft": False}
        return {}

    def test_default_publishes_spanish_github_prerelease_without_modrinth(self):
        with patch("publish_release.request", side_effect=self.request):
            publish(*self.args)
        self.assertTrue(all("modrinth" not in url for url, _ in self.calls))
        create = next(kw["data"] for url, kw in self.calls if url.endswith("/releases") and kw.get("method") == "POST")
        self.assertTrue(create["prerelease"])
        self.assertTrue(create["draft"])
        self.assertEqual(create["tag_name"], "v0.5.0-beta")
        self.assertEqual(create["target_commitish"], "abc123")
        self.assertTrue(create["body"].startswith("Spanish notes"))
        uploaded = next(kw["data"] for url, kw in self.calls if "uploads.github.com" in url)
        self.assertEqual(uploaded, self.jar.read_bytes())
        self.assertEqual(self.calls[-1][1]["data"], {"draft": False, "prerelease": True})

    def test_modrinth_requires_explicit_credentials_before_any_request(self):
        with patch("publish_release.request") as request:
            with self.assertRaisesRegex(ValueError, "Explicit Modrinth publication"):
                publish(*self.args, include_modrinth=True)
            request.assert_not_called()

    def test_existing_github_release_is_not_duplicated(self):
        release = {**self.draft(), "draft": False, "assets": [{"name": self.jar.name, "digest": "sha256:" + hashlib.sha256(self.jar.read_bytes()).hexdigest()}]}
        with patch("publish_release.request", return_value=release) as request:
            publish(*self.args)
        self.assertEqual(request.call_count, 1)

    def test_retry_rejects_a_different_draft_asset(self):
        release = {**self.draft(), "assets": [{"name": self.jar.name, "digest": "sha256:different"}]}
        with patch("publish_release.request", return_value=release) as request:
            with self.assertRaisesRegex(ValueError, "Existing GitHub JAR differs"):
                publish(*self.args)
        self.assertEqual(request.call_count, 1)

    def test_matching_draft_asset_is_reused_and_then_published(self):
        digest = "sha256:" + hashlib.sha256(self.jar.read_bytes()).hexdigest()
        release = {**self.draft(), "assets": [{"name": self.jar.name, "digest": digest}]}
        with patch("publish_release.request", side_effect=[release, {**release, "draft": False}]) as request:
            publish(*self.args)
        self.assertEqual(request.call_count, 2)
        self.assertEqual(request.call_args.kwargs["method"], "PATCH")

    def test_conflicting_tag_blocks_publication(self):
        with patch("publish_release.request", side_effect=[None, {"object": {"type": "commit", "sha": "other"}}]):
            with self.assertRaisesRegex(ValueError, "different commit"):
                publish(*self.args)

    def test_modrinth_remains_available_only_with_explicit_opt_in(self):
        os.environ.update(MODRINTH_TOKEN="test-mr-token", MODRINTH_PROJECT_ID="project")
        def request(url, token, **kwargs):
            if url == "https://api.modrinth.com/v2/version":
                self.calls.append((url, kwargs))
                return {"id": "mr-version"}
            return self.request(url, token, **kwargs)
        with patch("publish_release.request", side_effect=request):
            publish(*self.args, include_modrinth=True)
        payload = next(kw["data"] for url, kw in self.calls if url == "https://api.modrinth.com/v2/version")
        self.assertIn(b'"changelog": "English notes', payload)
        self.assertNotIn(b"Spanish notes", payload)

    def test_both_loaders_upload_before_github_draft_is_published(self):
        neo = self.jar.with_name("serialcraft-neoforge-0.5.0-beta.jar")
        neo.write_bytes(b"verified-neoforge-artifact")
        with patch("publish_release.request", side_effect=self.request):
            publish({"fabric": self.jar, "neoforge": neo}, *self.args[1:])
        uploads = [kwargs["data"] for url, kwargs in self.calls if "uploads.github.com" in url]
        self.assertEqual(uploads, [self.jar.read_bytes(), neo.read_bytes()])
        self.assertEqual(self.calls[-1][1]["data"], {"draft": False, "prerelease": True})

    def test_modrinth_metadata_is_specific_to_each_loader(self):
        os.environ.update(MODRINTH_TOKEN="test-mr-token", MODRINTH_PROJECT_ID="project")
        def request(url, token, **kwargs):
            if url == "https://api.modrinth.com/v2/version":
                self.calls.append((url, kwargs))
                return {"id": "mr-version"}
            return self.request(url, token, **kwargs)
        with patch("publish_release.request", side_effect=request):
            publish({"fabric": self.jar, "neoforge": self.jar}, *self.args[1:], include_modrinth=True)
        payloads = [kw["data"] for url, kw in self.calls if url == "https://api.modrinth.com/v2/version"]
        self.assertIn(b'"loaders": ["fabric"]', payloads[0])
        self.assertIn(b'"project_id": "P7dR8mSH"', payloads[0])
        self.assertIn(b'"version_number": "0.5.0-beta-neoforge"', payloads[1])
        self.assertIn(b'"loaders": ["neoforge"]', payloads[1])
        self.assertIn(b'"dependencies": []', payloads[1])

    def test_conflict_in_second_loader_is_detected_before_any_upload(self):
        neo = self.jar.with_name("serialcraft-neoforge-0.5.0-beta.jar")
        neo.write_bytes(b"verified-neoforge-artifact")
        release = {**self.draft(), "assets": [{"name": neo.name, "digest": "sha256:different"}]}
        with patch("publish_release.request", return_value=release) as request:
            with self.assertRaisesRegex(ValueError, "Existing GitHub JAR differs"):
                publish({"fabric": self.jar, "neoforge": neo}, *self.args[1:])
        self.assertEqual(request.call_count, 1)

    def test_neoforge_only_modrinth_publication_keeps_both_github_artifacts(self):
        os.environ.update(MODRINTH_TOKEN="test-mr-token", MODRINTH_PROJECT_ID="project")
        neo = self.jar.with_name("serialcraft-neoforge-0.5.0-beta.jar")
        neo.write_bytes(b"verified-neoforge-artifact")
        def request(url, token, **kwargs):
            if url == "https://api.modrinth.com/v2/version":
                self.calls.append((url, kwargs))
                return {"id": "mr-neoforge-version"}
            return self.request(url, token, **kwargs)
        with patch("publish_release.request", side_effect=request):
            publish({"fabric": self.jar, "neoforge": neo}, *self.args[1:],
                    include_modrinth=True, modrinth_loader="neoforge")
        payloads = [kwargs["data"] for url, kwargs in self.calls if url == "https://api.modrinth.com/v2/version"]
        self.assertEqual(len(payloads), 1)
        self.assertIn(b'"loaders": ["neoforge"]', payloads[0])
        self.assertIn(b'"dependencies": []', payloads[0])
        self.assertTrue(all(not url.endswith("0.5.0-beta-fabric") for url, _ in self.calls))
        uploads = [kwargs["data"] for url, kwargs in self.calls if "uploads.github.com" in url]
        self.assertEqual(uploads, [self.jar.read_bytes(), neo.read_bytes()])

    def test_missing_selected_loader_is_rejected_before_any_request(self):
        with patch("publish_release.request") as request:
            with self.assertRaisesRegex(ValueError, "no verified JAR"):
                publish(*self.args, include_modrinth=True, modrinth_loader="neoforge")
            request.assert_not_called()


    def test_existing_stable_beta_is_reclassified_without_reupload(self):
        release = {**self.draft(), "draft": False, "prerelease": False,
                   "assets": [{"name": self.jar.name, "digest": "sha256:" + hashlib.sha256(self.jar.read_bytes()).hexdigest()}]}
        with patch("publish_release.request", side_effect=[release, {**release, "prerelease": True}]) as request:
            publish(*self.args)
        self.assertEqual(request.call_count, 2)
        self.assertEqual(request.call_args.kwargs["data"], {"prerelease": True})

    def test_published_release_also_rejects_different_artifact_bytes(self):
        release = {**self.draft(), "draft": False, "assets": [{"name": self.jar.name, "digest": "sha256:different"}]}
        with patch("publish_release.request", return_value=release) as request:
            with self.assertRaisesRegex(ValueError, "Existing GitHub JAR differs"):
                publish(*self.args)
        self.assertEqual(request.call_count, 1)

    def test_note_updates_keep_original_source_commit_and_notification_receipts(self):
        origin = "https://github.com/owner/repo/commit/" + "a" * 40
        marker = "<!-- serialcraft-discord:version1 -->"
        release = {**self.draft(), "draft": False,
                   "body": "Old notes\n\n---\n[Commit de origen](" + origin + ")\n\n" + marker,
                   "assets": [{"name": self.jar.name, "digest": "sha256:" + hashlib.sha256(self.jar.read_bytes()).hexdigest()}]}
        with patch("publish_release.request", side_effect=[release, release]) as request:
            publish(*self.args)
        body = request.call_args.kwargs["data"]["body"]
        self.assertIn("Spanish notes", body)
        self.assertIn(origin, body)
        self.assertIn(marker, body)

class LocaleValidationTests(unittest.TestCase):
    def test_duplicate_keys_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            load_catalogue(b'{"key":"one","key":"two"}')

    def test_invalid_unicode_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Invalid Unicode"):
            load_catalogue(b'{"key":"\\ud800"}')

    def test_minecraft_arguments_can_be_reordered_explicitly(self):
        self.assertEqual(format_signature("%s %s %s%%"), format_signature("%3$s%% %1$s %2$s"))
        self.assertNotEqual(format_signature("%s %s"), format_signature("%s"))
        self.assertNotEqual(format_signature("%s%%"), format_signature("%s"))
        self.assertEqual(format_signature("Oxygen (%)"), format_signature("氧气（%）"))
        with self.assertRaisesRegex(ValueError, "Unsupported"):
            format_signature("%d")


if __name__ == "__main__":
    unittest.main()
