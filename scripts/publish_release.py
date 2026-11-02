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
import io
import tomllib

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
    jars = {loader: jar_dir / f"{archive}-{loader}-{version}.jar" for loader in ("fabric", "neoforge")}
    changelogs = []
    for language in ("es", "en"):
        notes = Path("changelogs") / f"{version}.{language}.md"
        changelog = notes.read_text(encoding="utf-8").strip()
        if not changelog:
            raise ValueError(f"Empty changelog: {notes}")
        changelogs.append(changelog)
    for loader, jar in jars.items():
        with zipfile.ZipFile(jar) as package:
            names = set(package.namelist())
            if len(names) != len(package.namelist()):
                raise ValueError(f"Duplicate JAR entries: {jar}")
            if loader == "fabric":
                metadata = json.loads(package.read("fabric.mod.json"))
                if metadata["id"] != "serialcraft" or metadata["version"] != version:
                    raise ValueError("JAR metadata does not match gradle.properties")
                if metadata["depends"]["minecraft"] != minecraft:
                    raise ValueError("JAR Minecraft version does not match gradle.properties")
                if metadata["depends"]["java"] != ">=25":
                    raise ValueError("JAR Java requirement changed unexpectedly")
                if metadata["depends"]["fabricloader"] != f'>={properties["loader_version"].strip()}':
                    raise ValueError("JAR Fabric Loader requirement does not match gradle.properties")
                icon = metadata["icon"]
                nested = [entry["file"] for entry in metadata["jars"]]
                if "META-INF/neoforge.mods.toml" in names:
                    raise ValueError("NeoForge metadata in Fabric JAR")
            else:
                metadata = tomllib.loads(package.read("META-INF/neoforge.mods.toml").decode("utf-8"))
                mod = next(mod for mod in metadata["mods"] if mod["modId"] == "serialcraft")
                if mod["version"] != version:
                    raise ValueError("NeoForge JAR version does not match gradle.properties")
                dependencies = {dep["modId"]: dep for dep in metadata["dependencies"]["serialcraft"]}
                if dependencies["minecraft"]["versionRange"] != f"[{minecraft}]":
                    raise ValueError("NeoForge Minecraft version does not match gradle.properties")
                if dependencies["neoforge"]["versionRange"] != f'[{properties["neoforge_version"].strip()},)':
                    raise ValueError("NeoForge requirement does not match gradle.properties")
                icon = mod["iconFile"]
                nested = [entry["path"] for entry in json.loads(package.read("META-INF/jarjar/metadata.json"))["jars"]]
                if "fabric.mod.json" in names or any(name.startswith("com/serialcraft/fabric/") for name in names):
                    raise ValueError("Fabric integration in NeoForge JAR")
            required = {icon, "com/serialcraft/SerialCraft.class", "com/serialcraft/SerialCraftClient.class",
                        "com/serialcraft/block/entity/HardwareIOBlockEntity.class", "com/serialcraft/connection/SerialHandler.class",
                        "META-INF/services/com.serialcraft.platform.Platform", "META-INF/services/com.serialcraft.platform.PayloadRegistry",
                        "META-INF/services/com.serialcraft.platform.ClientNetworking", "data/serialcraft/recipe/io_block.json"}
            if not required <= names:
                raise ValueError(f"Missing shared code/resources: {required - names}")
            serial = next((entry for entry in nested if "jSerialComm" in entry), None)
            dns = next((entry for entry in nested if "jmdns" in entry), None)
            if not serial or not dns:
                raise ValueError(f"Missing bundled hardware dependencies in {loader}")
            with zipfile.ZipFile(io.BytesIO(package.read(serial))) as library:
                if not any(name.endswith(".dll") for name in library.namelist()) or not any(name.endswith(".so") for name in library.namelist()):
                    raise ValueError("jSerialComm native libraries are missing")
            with zipfile.ZipFile(io.BytesIO(package.read(dns))) as library:
                if "javax/jmdns/JmDNS.class" not in library.namelist():
                    raise ValueError("JmDNS classes are missing")
        validate(jar=jar)
    kind = "alpha" if "alpha" in version.lower() else "beta" if "beta" in version.lower() else "release"
    return jars, version, minecraft, kind, *changelogs


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


def publication_manifest(release, versions, jars, repository, project, version):
    return {
        "repository": repository,
        "version": version,
        "github_release_id": release["id"],
        "github_release_url": release["html_url"],
        "modrinth_project_id": project,
        "versions": [{"loader": loader, "id": data["id"],
                      "version_number": version + "-" + loader,
                      "filename": jars[loader].name,
                      "sha512": hashlib.sha512(jars[loader].read_bytes()).hexdigest()}
                     for loader, data in versions.items()],
    }


def publish(jars, version, minecraft, kind, changelog_es, changelog_en, *, include_modrinth=False, modrinth_loader="both"):
    # Retain the single-artifact API used by the original publication regression suite.
    jars = {"fabric": jars} if isinstance(jars, Path) else jars
    selected_loaders = tuple(jars) if modrinth_loader == "both" else (modrinth_loader,)
    if include_modrinth and any(loader not in jars for loader in selected_loaders):
        raise ValueError("Selected Modrinth loader has no verified JAR")
    github_token = os.environ.get("GH_TOKEN", "")
    modrinth_token = os.environ.get("MODRINTH_TOKEN", "")
    if not github_token:
        raise ValueError("GH_TOKEN is required")
    if include_modrinth and not modrinth_token:
        raise ValueError("Explicit Modrinth publication requires the MODRINTH_TOKEN repository secret")
    repository, commit = os.environ["GITHUB_REPOSITORY"], os.environ["GITHUB_SHA"]
    project = os.environ["MODRINTH_PROJECT_ID"] if include_modrinth else None
    tag = "v" + version
    github_url = f"https://api.github.com/repos/{repository}"
    gh = lambda path, **kw: request(github_url + path, "Bearer " + github_token, **kw)
    mr = lambda path, **kw: request("https://api.modrinth.com/v2" + path, modrinth_token, **kw)
    release = gh("/releases/tags/" + urllib.parse.quote(tag, safe=""), missing_ok=True)
    updates = {}
    if release:
        if release.get("prerelease", False) != (kind != "release"):
            updates["prerelease"] = kind != "release"
        previous_body = release.get("body", "").replace("\r\n", "\n")
        origin = re.search(r"\[Commit de origen\]\((https://github\.com/[^\s)]+/commit/[0-9a-f]{40})\)", previous_body)
        if origin:
            receipts = re.findall(r"<!-- serialcraft-discord:[A-Za-z0-9_-]+ -->", previous_body)
            body = changelog_es + f"\n\n---\n[Commit de origen]({origin[1]})"
            if receipts:
                body += "\n\n" + "\n".join(dict.fromkeys(receipts))
            if body.strip() != previous_body.strip():
                updates["body"] = body
    versions = {loader: mr(f"/project/{project}/version/" + urllib.parse.quote(version + "-" + loader, safe=""), missing_ok=True)
                for loader in selected_loaders} if include_modrinth else {}
    assets = {asset["name"]: asset for asset in (release or {}).get("assets", [])}
    complete = release and not release["draft"] and all(jar.name in assets for jar in jars.values()) and (not include_modrinth or all(versions.values()))
    # Validate every existing file before uploading either loader. Never replace published bytes.
    for loader, jar in jars.items():
        asset, modrinth = assets.get(jar.name), versions.get(loader)
        sha256, sha512 = hashlib.sha256(jar.read_bytes()).hexdigest(), hashlib.sha512(jar.read_bytes()).hexdigest()
        if asset and asset.get("digest") != "sha256:" + sha256:
            raise ValueError("Existing GitHub JAR differs. Re-run failed jobs from its original build, or use a new version.")
        if modrinth and not any(f["hashes"].get("sha512") == sha512 for f in modrinth["files"]):
            raise ValueError("Existing Modrinth JAR differs. Re-run failed jobs from its original build, or use a new version.")
        if release and not release["draft"] and not asset:
            raise ValueError("Existing published GitHub release has no matching JAR; review it before retrying.")
    if updates:
        release = gh(f'/releases/{release["id"]}', method="PATCH", data=updates)
    if complete:
        print(f"{version} is already published on the requested platforms; nothing to upload.")
        return publication_manifest(release, versions, jars, repository, project, version)
    source = f"https://github.com/{repository}/commit/{commit}"
    github_body = changelog_es + f"\n\n---\n[Commit de origen]({source})"
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
    for loader, jar in jars.items():
        if jar.name not in assets:
            upload = release["upload_url"].split("{", 1)[0] + "?name=" + urllib.parse.quote(jar.name)
            request(upload, "Bearer " + github_token, method="POST", data=jar.read_bytes(), content_type="application/java-archive")
        if include_modrinth and loader in versions and not versions[loader]:
            data, content_type = multipart({
                "project_id": project, "name": f"SerialCraft {version} ({loader})", "version_number": version + "-" + loader,
                "changelog": modrinth_body, "version_type": kind, "loaders": [loader], "game_versions": [minecraft], "featured": False,
                "dependencies": [{"project_id": "P7dR8mSH", "dependency_type": "required"}] if loader == "fabric" else [],
                "file_parts": ["file"], "primary_file": "file"}, jar)
            versions[loader] = mr("/version", method="POST", data=data, content_type=content_type)
    # Both loader files must upload successfully before the GitHub draft is made public.
    if release["draft"]:
        release = gh(f'/releases/{release["id"]}', method="PATCH", data={"draft": False, "prerelease": kind != "release"})
    links = f'GitHub: {release["html_url"]}\n'
    for loader, modrinth in versions.items():
        links += f'Modrinth ({loader}): https://modrinth.com/mod/{project}/version/{modrinth["id"]}\n'
    print(links)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
            summary.write(f"## Published {version}\n\n" + links)
    return publication_manifest(release, versions, jars, repository, project, version)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--publish", action="store_true")
    mode.add_argument("--sync-readme", action="store_true")
    parser.add_argument("--modrinth", action="store_true", help="Explicitly opt into Modrinth publication with --publish")
    parser.add_argument("--modrinth-loader", choices=("both", "fabric", "neoforge"), default="both",
                        help="Limit Modrinth publication to one loader; GitHub still receives both verified JARs")
    parser.add_argument("--jar-dir", type=Path, default=Path("build/libs"))
    parser.add_argument("--manifest", type=Path, help="Write verified publication IDs for the Discord notification step")
    args = parser.parse_args()
    if args.modrinth and not args.publish:
        parser.error("--modrinth requires --publish")
    if args.modrinth_loader != "both" and not args.modrinth:
        parser.error("--modrinth-loader requires --modrinth")
    if args.sync_readme:
        sync_modrinth_readme()
        return
    inputs = release_inputs(args.jar_dir)
    print(f"Validated {', '.join(jar.name for jar in inputs[0].values())}: Minecraft {inputs[2]}, channel {inputs[3]}")
    if args.publish:
        manifest = publish(*inputs, include_modrinth=args.modrinth, modrinth_loader=args.modrinth_loader)
        if args.manifest:
            args.manifest.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
