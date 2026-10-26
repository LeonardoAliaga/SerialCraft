"""Publish the verified JAR; --check validates inputs without network access."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile

from validate_locales import validate


def release_inputs(jar_dir):
    properties = dict(re.findall(r"^([a-z_]+)=(.+)$", Path("gradle.properties").read_text(), re.M))
    version = properties["mod_version"].strip()
    minecraft = properties["minecraft_version"].strip()
    archive = properties["archives_base_name"].strip()
    if not re.fullmatch(r"[0-9][0-9A-Za-z.+-]*", version):
        raise ValueError("Invalid mod_version")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", archive):
        raise ValueError("Invalid archives_base_name")
    jar = jar_dir / f"{archive}-{version}.jar"
    changelogs = []
    for language in ("es", "en"):
        notes = Path("changelogs") / f"{version}.{language}.md"
        changelog = notes.read_text(encoding="utf-8").strip()
        if not changelog:
            raise ValueError(f"Empty changelog: {notes}")
        changelogs.append(changelog)
    with zipfile.ZipFile(jar) as package:
        metadata = json.loads(package.read("fabric.mod.json"))
        if metadata["id"] != "serialcraft" or metadata["version"] != version:
            raise ValueError("JAR metadata does not match gradle.properties")
        if metadata["depends"]["minecraft"] != f"~{minecraft}":
            raise ValueError("JAR Minecraft version does not match gradle.properties")
        if metadata["depends"]["java"] != ">=25":
            raise ValueError("JAR Java requirement changed unexpectedly")
        if metadata["depends"]["fabricloader"] != f'>={properties["loader_version"].strip()}':
            raise ValueError("JAR Fabric Loader requirement does not match gradle.properties")
        if metadata["icon"] not in package.namelist():
            raise ValueError("JAR icon is missing")
    validate(jar=jar)
    kind = "alpha" if "alpha" in version.lower() else "beta" if "beta" in version.lower() else "release"
    return jar, version, minecraft, kind, *changelogs


def request(url, token, *, method="GET", data=None, content_type="application/json", missing_ok=False):
    headers = {"User-Agent": "SerialCraft-release (github.com/LeonardoAliaga/SerialCraft)",
               "Accept": "application/json", "Authorization": token}
    if data is not None:
        headers["Content-Type"] = content_type
        if content_type == "application/json":
            data = json.dumps(data).encode("utf-8")
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers, method=method), timeout=60) as response:
            body = response.read()
            return json.loads(body) if body else None
    except urllib.error.HTTPError as error:
        if missing_ok and error.code == 404:
            return None
        detail = error.read().decode("utf-8", errors="replace")[:1000]
        raise RuntimeError(f"{method} {urllib.parse.urlsplit(url).path}: HTTP {error.code}: {detail}") from None


def multipart(metadata, jar):
    boundary = "serialcraft-" + uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\n'
            'Content-Type: application/json\r\n\r\n').encode()
    body += json.dumps(metadata).encode("utf-8")
    body += (f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{jar.name}"\r\n'
             'Content-Type: application/java-archive\r\n\r\n').encode()
    body += jar.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
    return body, f"multipart/form-data; boundary={boundary}"


def modrinth_readme():
    body = Path("MODRINTH.md").read_text(encoding="utf-8").strip()
    if not body:
        raise ValueError("MODRINTH.md must not be empty")
    return body


def sync_modrinth_readme():
    token = os.environ.get("MODRINTH_TOKEN", "")
    if not token:
        raise ValueError("The MODRINTH_TOKEN repository secret is required")
    project = os.environ["MODRINTH_PROJECT_ID"]
    url = f"https://api.modrinth.com/v2/project/{project}"
    body = modrinth_readme()
    current = request(url, token)
    if current["body"].strip() == body:
        print("Modrinth description is already synchronized with MODRINTH.md.")
        return
    request(url, token, method="PATCH", data={"body": body})
    print("Modrinth description synchronized with MODRINTH.md.")


def publish(jar, version, minecraft, kind, changelog_es, changelog_en, *, include_modrinth=False):
    github_token = os.environ.get("GH_TOKEN", "")
    modrinth_token = os.environ.get("MODRINTH_TOKEN", "")
    if not github_token:
        raise ValueError("GH_TOKEN is required")
    if include_modrinth and not modrinth_token:
        raise ValueError("Explicit Modrinth publication requires the MODRINTH_TOKEN repository secret")
    repository = os.environ["GITHUB_REPOSITORY"]
    commit = os.environ["GITHUB_SHA"]
    project = os.environ["MODRINTH_PROJECT_ID"] if include_modrinth else None
    tag = "v" + version
    github_url = f"https://api.github.com/repos/{repository}"
    modrinth_url = "https://api.modrinth.com/v2"
    gh = lambda path, **kw: request(github_url + path, "Bearer " + github_token, **kw)
    mr = lambda path, **kw: request(modrinth_url + path, modrinth_token, **kw)
    release = gh("/releases/tags/" + urllib.parse.quote(tag, safe=""), missing_ok=True)
    modrinth = (mr(f"/project/{project}/version/" + urllib.parse.quote(version, safe=""), missing_ok=True)
                if include_modrinth else None)
    asset = next((a for a in (release or {}).get("assets", []) if a["name"] == jar.name), None)

    # A later push with an unchanged version must leave the published files alone.
    if release and not release["draft"] and asset and (not include_modrinth or modrinth):
        print(f"{version} is already published on the requested platforms; nothing to upload.")
        return

    # Retry a partial publication using the original build artifact. Never replace an existing file.
    sha256 = hashlib.sha256(jar.read_bytes()).hexdigest()
    sha512 = hashlib.sha512(jar.read_bytes()).hexdigest()
    if asset and asset.get("digest") != "sha256:" + sha256:
        raise ValueError("Existing GitHub JAR differs. Re-run failed jobs from its original build, or use a new version.")
    if modrinth and not any(f["hashes"].get("sha512") == sha512 for f in modrinth["files"]):
        raise ValueError("Existing Modrinth JAR differs. Re-run failed jobs from its original build, or use a new version.")
    if release and not release["draft"] and not asset:
        raise ValueError("Existing published GitHub release has no matching JAR; review it before retrying.")

    source = f"https://github.com/{repository}/commit/{commit}"
    github_body = changelog_en + f"\n\n---\n[Source commit]({source})"
    modrinth_body = changelog_en + f"\n\n---\n[Source commit]({source})"
    if not release:
        ref = gh("/git/ref/tags/" + urllib.parse.quote(tag, safe=""), missing_ok=True)
        if ref:
            target = ref["object"]
            while target["type"] == "tag":
                target = gh("/git/tags/" + target["sha"])["object"]
            if target["sha"] != commit:
                raise ValueError(f"Tag {tag} already points to a different commit")
        release = gh("/releases", method="POST", data={
            "tag_name": tag, "target_commitish": commit, "name": f"SerialCraft {version}",
            "body": github_body, "draft": True, "prerelease": kind != "release"})
    if not asset:
        upload = release["upload_url"].split("{", 1)[0] + "?name=" + urllib.parse.quote(jar.name)
        request(upload, "Bearer " + github_token, method="POST", data=jar.read_bytes(), content_type="application/java-archive")

    if include_modrinth and not modrinth:
        data, content_type = multipart({
            "project_id": project, "name": f"SerialCraft {version}", "version_number": version,
            "changelog": modrinth_body, "version_type": kind, "loaders": ["fabric"],
            "game_versions": [minecraft], "environment": "client_and_server", "featured": False,
            "dependencies": [{"project_id": "P7dR8mSH", "dependency_type": "required"}],
            "file_parts": ["file"], "primary_file": "file"}, jar)
        modrinth = mr("/version", method="POST", data=data, content_type=content_type)
    if release["draft"]:
        release = gh(f'/releases/{release["id"]}', method="PATCH", data={"draft": False})
    links = f'GitHub: {release["html_url"]}\n'
    if include_modrinth:
        links += f'Modrinth: https://modrinth.com/mod/{project}/version/{modrinth["id"]}\n'
    print(links)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
            summary.write(f"## Published {version}\n\n" + links)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--publish", action="store_true")
    mode.add_argument("--sync-readme", action="store_true")
    parser.add_argument("--modrinth", action="store_true", help="Explicitly opt into Modrinth publication with --publish")
    parser.add_argument("--jar-dir", type=Path, default=Path("build/libs"))
    args = parser.parse_args()
    if args.modrinth and not args.publish:
        parser.error("--modrinth requires --publish")
    if args.sync_readme:
        sync_modrinth_readme()
        return
    inputs = release_inputs(args.jar_dir)
    print(f"Validated {inputs[0].name}: Minecraft {inputs[2]}, channel {inputs[3]}")
    if args.publish:
        publish(*inputs, include_modrinth=args.modrinth)


if __name__ == "__main__":
    main()
