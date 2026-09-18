#!/usr/bin/env python3
"""
Release Automation Script for Side Rotate
-----------------------------------------
1. Automatically increments versionCode and updates versionName in app/build.gradle.kts.
2. Builds the project using Gradle (assembleDebug).
3. Archives and names the output APK to:
   'version/SideRotate_v<versionName>.apk'
4. Stages, commits, and creates a Git tag for the release.
5. Optionally pushes to GitHub origin to trigger GitHub Actions release pipeline.
"""

import sys
import os
import re
import shutil
import subprocess
from pathlib import Path

PROJECT_ROOT = Path(__file__).parent.resolve()
BUILD_GRADLE_PATH = PROJECT_ROOT / "app" / "build.gradle.kts"
VERSION_DIR = PROJECT_ROOT / "version"
APK_SOURCE = PROJECT_ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"


def bump_version_string(version_str: str) -> str:
    """Bumps the patch component of a version string (e.g. 1.0.1 -> 1.0.2)."""
    parts = version_str.strip().split(".")
    if len(parts) == 1:
        return f"{parts[0]}.0.1"
    elif len(parts) == 2:
        return f"{parts[0]}.{parts[1]}.1"
    else:
        try:
            patch = int(parts[-1]) + 1
            return ".".join(parts[:-1] + [str(patch)])
        except ValueError:
            return f"{version_str}.1"


def update_gradle_versions(custom_version_name: str = None) -> tuple[int, str]:
    if not BUILD_GRADLE_PATH.exists():
        print(f"Error: Could not find build file at {BUILD_GRADLE_PATH}")
        sys.exit(1)

    content = BUILD_GRADLE_PATH.read_text(encoding="utf-8")

    code_match = re.search(r'versionCode\s*=\s*(\d+)', content)
    name_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)

    if not code_match or not name_match:
        print("Error: Could not find versionCode or versionName in build.gradle.kts")
        sys.exit(1)

    old_code = int(code_match.group(1))
    old_name = name_match.group(1)

    new_code = old_code + 1
    new_name = custom_version_name if custom_version_name else bump_version_string(old_name)

    content = re.sub(r'versionCode\s*=\s*\d+', f'versionCode = {new_code}', content, count=1)
    content = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{new_name}"', content, count=1)

    BUILD_GRADLE_PATH.write_text(content, encoding="utf-8")
    print(f"[*] Updated Version: {old_name} (code {old_code}) -> {new_name} (code {new_code})")
    return new_code, new_name


def run_gradle_build():
    print("[*] Starting Gradle assembleDebug build...")
    gradle_cmd = "gradlew.bat" if os.name == "nt" else "./gradlew"
    cmd = [str(PROJECT_ROOT / gradle_cmd), "assembleDebug"]

    result = subprocess.run(cmd, cwd=str(PROJECT_ROOT), shell=(os.name == "nt"))
    if result.returncode != 0:
        print(f"[-] Gradle build failed with exit code {result.returncode}")
        sys.exit(result.returncode)
    print("[+] Gradle build completed successfully.")


def package_release(version_name: str) -> Path:
    if not APK_SOURCE.exists():
        print(f"[-] Error: Generated APK not found at {APK_SOURCE}")
        sys.exit(1)

    VERSION_DIR.mkdir(parents=True, exist_ok=True)
    destination_apk = VERSION_DIR / f"SideRotate_v{version_name}.apk"

    shutil.copy2(APK_SOURCE, destination_apk)
    size_mb = destination_apk.stat().st_size / (1024 * 1024)

    print("\n=======================================================")
    print(f"  RELEASE READY: SideRotate v{version_name}")
    print("=======================================================")
    print(f"  Output Path : {destination_apk}")
    print(f"  File Size   : {size_mb:.2f} MB")
    print("=======================================================\n")
    return destination_apk


def git_commit_and_tag(version_name: str, commit_message: str = None, should_push: bool = True):
    print("[*] Managing Git repository state...")

    # Initialize git if needed
    if not (PROJECT_ROOT / ".git").exists():
        subprocess.run(["git", "init"], cwd=str(PROJECT_ROOT), check=True)
        print("[+] Initialized git repository.")

    tag_name = f"v{version_name}"
    final_message = commit_message if commit_message else f"Release {tag_name}: Side Rotate {tag_name}"

    # Stage changes
    subprocess.run(["git", "add", "-A"], cwd=str(PROJECT_ROOT), check=True)

    # Check if there are changes to commit
    status_proc = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=str(PROJECT_ROOT),
        capture_output=True,
        text=True
    )

    if status_proc.stdout.strip():
        subprocess.run(
            ["git", "commit", "-m", final_message],
            cwd=str(PROJECT_ROOT),
            check=True
        )
        print(f"[+] Committed: {final_message}")
    else:
        print("[*] No code changes to commit.")

    # Create tag if it doesn't already exist
    tag_check = subprocess.run(
        ["git", "tag", "-l", tag_name],
        cwd=str(PROJECT_ROOT),
        capture_output=True,
        text=True
    )
    if not tag_check.stdout.strip():
        subprocess.run(["git", "tag", "-a", tag_name, "-m", final_message], cwd=str(PROJECT_ROOT), check=True)
        print(f"[+] Created Git tag {tag_name}.")
    else:
        print(f"[*] Git tag {tag_name} already exists.")

    if should_push:
        # Check if remote 'origin' exists
        remote_proc = subprocess.run(
            ["git", "remote", "get-url", "origin"],
            cwd=str(PROJECT_ROOT),
            capture_output=True,
            text=True
        )
        if remote_proc.returncode == 0:
            print("[*] Pushing to origin and tags...")
            subprocess.run(["git", "push", "origin", "main", "--tags"], cwd=str(PROJECT_ROOT))
            print("[+] Successfully pushed to GitHub.")
        else:
            print("[!] Note: No git remote 'origin' configured yet. Run 'git remote add origin <url>' to enable push.")


def main():
    custom_ver = None
    commit_msg = None
    should_push = True

    args = sys.argv[1:]
    filtered_args = []
    i = 0
    while i < len(args):
        arg = args[i]
        if arg == "--no-push":
            should_push = False
        elif arg in ("--message", "-m") and i + 1 < len(args):
            commit_msg = args[i + 1]
            i += 1
        elif arg in ("--version", "-v") and i + 1 < len(args):
            custom_ver = args[i + 1]
            i += 1
        else:
            filtered_args.append(arg)
        i += 1

    if not custom_ver and filtered_args:
        custom_ver = filtered_args[0]
        if len(filtered_args) > 1 and not commit_msg:
            commit_msg = filtered_args[1]

    new_code, new_name = update_gradle_versions(custom_ver)
    run_gradle_build()
    dest = package_release(new_name)
    git_commit_and_tag(new_name, commit_message=commit_msg, should_push=should_push)


if __name__ == "__main__":
    main()
