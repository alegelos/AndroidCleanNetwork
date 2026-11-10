#!/usr/bin/env python3
"""Copy the self-contained AndroidCleanNetwork skill without overwriting user files."""

import argparse
from pathlib import Path
import shutil
import sys


SKILL_NAME = "androidcleannetwork"
SKILL_SOURCE = Path(__file__).resolve().parent.parent / "skills" / SKILL_NAME
IGNORED_NAMES = (".build", ".gradle", ".kotlin", "build", "__pycache__", ".DS_Store", "local.properties", ".idea", "*.iml")


def install_skill(source: Path, destination: Path) -> Path:
    source = source.resolve()
    destination = destination.expanduser().absolute()

    if not (source / "SKILL.md").is_file():
        raise ValueError(f"Missing skill entrypoint: {source / 'SKILL.md'}")
    if destination.name != SKILL_NAME:
        raise ValueError(f"The destination folder must be named {SKILL_NAME}.")
    if destination.exists() or destination.is_symlink():
        raise FileExistsError(f"Destination already exists; nothing was overwritten: {destination}")

    resolved_destination = destination.resolve()
    if resolved_destination == source or source in resolved_destination.parents:
        raise ValueError("The destination must be outside the source skill folder.")

    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(source, destination, ignore=shutil.ignore_patterns(*IGNORED_NAMES))
    return destination


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "destination", type=Path,
        help="New skill folder, e.g. /path/to/App/.agents/skills/androidcleannetwork",
    )
    args = parser.parse_args()
    try:
        installed = install_skill(SKILL_SOURCE, args.destination)
    except (OSError, ValueError) as error:
        print(f"Installation failed: {error}", file=sys.stderr)
        return 1
    print(f"Installed AndroidCleanNetwork skill: {installed}")
    print("Use your agent's skill selector or invoke $androidcleannetwork in Codex.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
