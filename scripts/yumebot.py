import requests
import os
import glob
import re
import shutil
import subprocess  # nosec B404 - only runs fixed git commands from CI env
import html

BOT_TOKEN = os.environ.get("BOT_TOKEN")
CHAT_ID = os.environ.get("CHAT_ID")
MESSAGE_THREAD_ID = os.environ.get("MESSAGE_THREAD_ID")
TITLE = os.environ.get("TITLE")
BRANCH = os.environ.get("BRANCH")
WORKFLOW_NAME = os.environ.get("WORKFLOW_NAME", "")
EVENT_NAME = os.environ.get("EVENT_NAME", "")
RUN_NUMBER = os.environ.get("RUN_NUMBER", "")
COMMIT_SHA = os.environ.get("COMMIT_SHA", "")
REPOSITORY = os.environ.get("REPOSITORY", "")
RUN_ID = os.environ.get("RUN_ID", "")
SERVER_URL = os.environ.get("SERVER_URL", "https://github.com")
VERSION_NAME = os.environ.get("VERSION_NAME", "")
VERSION_CODE = os.environ.get("VERSION_CODE", "")
RELEASE_URL = os.environ.get("RELEASE_URL", "")
META_URL = os.environ.get("META_URL", "")
PUBLISH_DIR = os.environ.get("PUBLISH_DIR", "")
LOGO_URL = os.environ.get("LOGO_URL", "https://yumebox.gal.tf/logo/Yume.webp")
COMMIT_MESSAGE = os.environ.get("COMMIT_MESSAGE", "")


def get_commit_message():
    msg = (COMMIT_MESSAGE or "").strip()
    if not msg and COMMIT_SHA:
        if not re.fullmatch(r"[0-9a-fA-F]{7,64}", COMMIT_SHA):
            print("[-] COMMIT_SHA is not a valid git sha, skipping git log")
            return msg
        git = shutil.which("git") or "git"
        try:
            # nosemgrep: python.lang.security.audit.dangerous-subprocess-use-audit.dangerous-subprocess-use-audit, python.lang.security.audit.dangerous-subprocess-use-tainted-env-args.dangerous-subprocess-use-tainted-env-args
            result = subprocess.run(  # noqa: S603 # nosec B603 - fixed git argv, sha validated above
                [git, "log", "-1", "--format=%B", COMMIT_SHA],
                cwd=os.environ.get("GITHUB_WORKSPACE") or ".",
                capture_output=True, text=True, timeout=15,
            )
            msg = (result.stdout or "").strip()
        except Exception as e:
            print(f"[-] git log failed: {e}")
    return msg


def html_escape(text):
    return html.escape(text or "")


def _workflow_label():
    workflow_name_lower = WORKFLOW_NAME.lower()
    if "smart" in workflow_name_lower or "smart" in TITLE.lower():
        return "Smart"
    if "test" in workflow_name_lower:
        return "Test"
    return "Normal"


def _trigger_label():
    return {"push": "Push", "schedule": "Nightly"}.get(EVENT_NAME, "Manual")


def _version_lines():
    if VERSION_NAME and VERSION_CODE:
        version = f"{html_escape(VERSION_NAME)} ({html_escape(VERSION_CODE)})"
        return [f"<b>• Version</b>: <code>{version}</code>"]
    if VERSION_NAME:
        return [f"<b>• Version</b>: <code>{html_escape(VERSION_NAME)}</code>"]
    return []


def _link_lines():
    lines = []
    if REPOSITORY and RUN_ID:
        action_url = f"{SERVER_URL}/{REPOSITORY}/actions/runs/{RUN_ID}"
        lines.append(f'<b>• Download</b>: <a href="{action_url}">workpiece</a>')
    if RELEASE_URL:
        lines.append(f'<b>• Release</b>: <a href="{RELEASE_URL}">open</a>')
    if META_URL:
        lines.append(f'<b>• Meta</b>: <a href="{META_URL}">json</a>')
    return lines


def _commit_lines():
    commit_short = COMMIT_SHA[:7] if COMMIT_SHA else "unknown"
    if REPOSITORY and COMMIT_SHA:
        commit_url = f"{SERVER_URL}/{REPOSITORY}/commit/{COMMIT_SHA}"
        link = f'<a href="{commit_url}">{html_escape(commit_short)}</a>'
        lines = [f"<b>• Commit</b>: {link}"]
    else:
        lines = [f"<b>• Commit</b>: {html_escape(commit_short)}"]

    commit_message = get_commit_message()
    if commit_message:
        body = commit_message.strip()
        if len(body) > 700:
            body = body[:700].rstrip() + "…"
        lines.append(f"<blockquote>{html_escape(body)}</blockquote>")
    return lines


def get_caption():
    display_title = TITLE
    if _workflow_label() == "Test" and "test" not in TITLE.lower():
        display_title = f"{TITLE} Test"

    lines = [
        f"<b>{html_escape(display_title)}</b>",
        f"<b>• Trigger</b>: {html_escape(_trigger_label())}",
        f"<b>• Branch</b>: {html_escape(BRANCH)}",
    ]
    lines.extend(_version_lines())
    lines.extend(_link_lines())
    lines.extend(_commit_lines())
    return "\n".join(lines)


def check_environ():
    if BOT_TOKEN is None:
        print("[-] Invalid BOT_TOKEN")
        exit(1)
    if CHAT_ID is None:
        print("[-] Invalid CHAT_ID")
        exit(1)
    if TITLE is None:
        print("[-] Invalid TITLE")
        exit(1)
    if BRANCH is None:
        print("[-] Invalid BRANCH")
        exit(1)


def find_apk_files():
    if PUBLISH_DIR:
        pattern = os.path.join(PUBLISH_DIR, "*arm64-v8a*.apk")
        found = sorted(glob.glob(pattern))
        if found:
            print(f"[+] Found {len(found)} files in {pattern}")
            return found

    patterns = [
        "./app/build/outputs/apk/release/*arm64-v8a*.apk",
        "app/build/outputs/apk/release/*arm64-v8a*.apk",
        "/github/workspace/app/build/outputs/apk/release/*arm64-v8a*.apk"
    ]

    files = []
    for pattern in patterns:
        found = glob.glob(pattern)
        if found:
            files.extend(found)
            print(f"[+] Found {len(found)} files in {pattern}")

    files = list(set(files))

    if not files:
        print("[-] No APK files found!")
        exit(1)

    print(f"[+] Total files to upload: {len(files)}")
    for f in files:
        print(f"    - {f}")

    return files


def send_files_via_bot_api():
    print("[+] Starting Telegram upload")
    check_environ()

    files = find_apk_files()

    # Bot API URL
    bot_url = f"https://api.telegram.org/bot{BOT_TOKEN}"

    caption = get_caption()
    print("[+] Caption:", caption)

    file_path = files[0]
    print(f"[+] Uploading {file_path}...")

    reply_to_id = None
    try:
        photo_data = {
            'chat_id': CHAT_ID,
            'photo': LOGO_URL,
            'caption': caption,
            'parse_mode': 'HTML',
        }
        if MESSAGE_THREAD_ID:
            photo_data['message_thread_id'] = MESSAGE_THREAD_ID
        photo_resp = requests.post(f"{bot_url}/sendPhoto", data=photo_data, timeout=60)
        print(f"[+] Photo+caption: {photo_resp.status_code}")
        if photo_resp.status_code == 200:
            reply_to_id = photo_resp.json().get("result", {}).get("message_id")
        else:
            print(f"[-] Photo send failed: {photo_resp.text}")
    except Exception as e:
        print(f"[-] Photo send failed: {e}")

    with open(file_path, 'rb') as f:
        data = {'chat_id': CHAT_ID}
        if MESSAGE_THREAD_ID:
            data['message_thread_id'] = MESSAGE_THREAD_ID
        if reply_to_id:
            data['reply_to_message_id'] = reply_to_id

        files_data = {'document': f}

        response = requests.post(
            f"{bot_url}/sendDocument",
            data=data,
            files=files_data,
            timeout=60,
        )

    if response.status_code == 200:
        print(f"[+] {file_path} uploaded successfully!")
        return True
    else:
        print(f"[-] Failed to upload {file_path}: {response.text}")
        return False


if __name__ == "__main__":
    try:
        send_files_via_bot_api()
    except Exception as e:
        print(f"[-] Error: {e}")
        exit(1)
