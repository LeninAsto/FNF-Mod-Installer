"""Build a signed production bundle without storing credentials.

Interactive: python tools/build_release.py
Automation: pass --stdin-credentials and one JSON line on stdin.
Never pass passwords as command-line arguments.
"""
import getpass
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
if "--stdin-credentials" in sys.argv:
    credentials = json.loads(sys.stdin.readline())
else:
    credentials = {
        "alias": input("Upload key alias: "),
        "store_password": getpass.getpass("Keystore password: "),
        "key_password": getpass.getpass("Key password: "),
    }
env = os.environ.copy()
env.update({
    "FNF_RELEASE_STORE_FILE": str(root / "keystore.jks"),
    "FNF_RELEASE_STORE_PASSWORD": credentials["store_password"],
    "FNF_RELEASE_KEY_ALIAS": credentials["alias"],
    "FNF_RELEASE_KEY_PASSWORD": credentials["key_password"],
})
credentials.clear()
keytool = shutil.which("keytool")
if not keytool:
    raise SystemExit("keytool is not available in PATH")
validation = subprocess.run([
    keytool, "-list", "-keystore", env["FNF_RELEASE_STORE_FILE"],
    "-alias", env["FNF_RELEASE_KEY_ALIAS"],
    "-storepass:env", "FNF_RELEASE_STORE_PASSWORD",
], env=env, capture_output=True, text=True)
if validation.returncode:
    raise SystemExit("Could not validate the upload keystore and alias. Check the signing credentials.")
print("Upload keystore and alias validated.", flush=True)
if "--verify-only" in sys.argv:
    raise SystemExit(0)
command = [str(root / ("gradlew.bat" if os.name == "nt" else "gradlew")),
           "testDebugUnitTest", "lintRelease", "bundleRelease", "--console=plain"]
log_path = root / "app/build/release-build.log"
log_path.parent.mkdir(parents=True, exist_ok=True)
with log_path.open("w", encoding="utf-8") as log:
    process = subprocess.Popen(command, cwd=root, env=env, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, errors="replace")
    for line in process.stdout:
        log.write(line)
        log.flush()
        if any(marker in line for marker in ["> Task", "BUILD SUCCESSFUL", "BUILD FAILED", "FAILURE:", "Error:", "e: ", "* What went wrong:"]):
            print(line.rstrip(), flush=True)
    code = process.wait()
print(f"Build log: {log_path}", flush=True)
raise SystemExit(code)
