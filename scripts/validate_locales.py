"""Check UTF-8 catalogues, Minecraft format arguments and UI key coverage."""

import argparse
from collections import Counter
import json
from pathlib import Path
import re
import zipfile

LANG = Path("src/main/resources/assets/serialcraft/lang")
FORMAT = re.compile(r"%(?:(\d+)\$)?([A-Za-z%]|$)")


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def load_catalogue(raw):
    data = json.loads(raw.decode("utf-8"), object_pairs_hook=unique_object)
    if not isinstance(data, dict):
        raise ValueError("Expected a JSON object")
    for key, value in data.items():
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"Empty or non-string translation: {key}")
        if any(c == "\ufffd" or 0xD800 <= ord(c) <= 0xDFFF
               or (ord(c) < 32 and c not in "\n\t") for c in value):
            raise ValueError(f"Invalid Unicode or control character: {key}")
    return {k: v for k, v in data.items() if not k.startswith("_")}


def format_signature(value):
    arguments = []
    automatic = 0
    escapes = 0
    for match in FORMAT.finditer(value):
        index, kind = match.groups()
        if kind == "%" and index is None:
            escapes += 1
        elif kind == "s":
            if index is None:
                automatic += 1
                index = automatic
            if int(index) < 1:
                raise ValueError("Minecraft format arguments start at 1")
            arguments.append(int(index))
        else:
            raise ValueError(f"Unsupported Minecraft format token: {match.group()}")
    return Counter(arguments), escapes


def required_ui_keys(root, reference):
    required = set()
    for directory in ("src/main/java", "src/client/java"):
        for path in (root / directory).rglob("*.java"):
            source = path.read_text(encoding="utf-8")
            for key in re.findall(r'"((?:gui|message|key|block|item)\.serialcraft\.[\w.]+)"', source):
                # A trailing dot identifies a dynamic namespace, not a complete key.
                if not key.endswith("."):
                    required.add(key)
            prefix = {"HomePage.java": "gui.serialcraft.home.",
                      "IoEditor.java": "gui.serialcraft.io."}.get(path.name)
            if prefix:
                for suffix in re.findall(r'\btr\("([\w.]+)"\s*[,)]', source):
                    required.add(prefix + suffix)

    # Dynamic keys are composed from serialized modes, identity enums and events.
    for filename, prefix in (("IoMode", "mode"), ("SignalType", "signal"), ("LogicMode", "logic")):
        source = (root / f"src/main/java/com/serialcraft/board/{filename}.java").read_text(encoding="utf-8")
        for name in re.findall(r'\b[A-Z_]+\("([a-z_]+)"\)', source):
            required.add(f"gui.serialcraft.{prefix}.{name}")
    identity = (root / "src/client/java/com/serialcraft/identity/BoardIdentity.java").read_text(encoding="utf-8")
    for enum, prefix in (("Family", "family"), ("Confidence", "confidence")):
        body = re.search(r"enum " + enum + r"\s*\{(.*?)\}", identity, re.S).group(1)
        body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
        required.update("gui.serialcraft.home." + prefix + "." + name.lower()
                        for name in re.findall(r"\b[A-Z_]+\b", body))
    events = (root / "src/client/java/com/serialcraft/client/events/GameEvent.java").read_text(encoding="utf-8")
    required.update("gui.serialcraft.events.event." + name.lower()
                    for name in re.findall(r"^    ([A-Z_]+)\(Category\.", events, re.M))
    required.update("gui.serialcraft.events.category." + name for name in ("world", "player", "combat"))
    missing = required - reference.keys()
    if missing:
        raise ValueError(f"UI keys missing in English: {sorted(missing)}")
    return required


def validate(root=Path("."), jar=None):
    catalogues = {path.name: load_catalogue(path.read_bytes()) for path in sorted((root / LANG).glob("*.json"))}
    reference = catalogues["en_us.json"]
    required = required_ui_keys(root, reference)
    for name, translations in catalogues.items():
        missing = reference.keys() - translations.keys()
        extra = translations.keys() - reference.keys()
        if missing or extra:
            raise ValueError(f"{name}: missing={sorted(missing)}, extra={sorted(extra)}")
        for key, value in translations.items():
            if format_signature(value) != format_signature(reference[key]):
                raise ValueError(f"{name}: format arguments differ for {key}")
        print(f"{name}: {len(translations)} keys; Unicode and format arguments OK")
    if jar:
        with zipfile.ZipFile(jar) as package:
            bad_entry = package.testzip()
            if bad_entry:
                raise ValueError(f"Corrupt JAR entry: {bad_entry}")
            for name, translations in catalogues.items():
                if load_catalogue(package.read(f"assets/serialcraft/lang/{name}")) != translations:
                    raise ValueError(f"JAR locale differs from source: {name}")
            for asset in ("cats.png", "icons-w.png", "logo-txt.png"):
                path = f"assets/serialcraft/textures/gui/{asset}"
                if package.read(path) != (root / "src/main/resources" / path).read_bytes():
                    raise ValueError(f"JAR UI asset differs from source: {asset}")
        print(f"{jar.name}: archive integrity, packaged locales and UI assets OK")
    print(f"UI coverage: {len(required)} literal and dynamic keys across pages, navigation and IO editor")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path)
    args = parser.parse_args()
    validate(jar=args.jar)
