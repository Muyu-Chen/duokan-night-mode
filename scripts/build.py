#!/usr/bin/env python3
"""Build the original helper only. No reader APK or device dump is an input."""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, uuid, zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
EXPECTED_CERT = "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8"

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sdk", type=Path, default=Path(os.environ.get("ANDROID_SDK_ROOT", ROOT / ".local/android-sdk")))
    parser.add_argument("--build-tools", default="28.0.3")
    parser.add_argument("--unsigned", action="store_true")
    parser.add_argument("--signing-key", type=Path)
    parser.add_argument("--signing-cert", type=Path)
    args = parser.parse_args()
    sdk = args.sdk.resolve()
    tools = sdk / "build-tools" / args.build_tools
    android_jar = sdk / "platforms/android-27/android.jar"
    aapt = tools / ("aapt2.exe" if os.name == "nt" else "aapt2")
    dx = tools / "lib/dx.jar"
    signer = tools / "lib/apksigner.jar"
    for item in (android_jar, aapt, dx, signer):
        if not item.is_file(): raise SystemExit("Missing SDK component: " + str(item))
    manifest = ET.parse(ROOT / "app/AndroidManifest.xml").getroot()
    if manifest.findall("uses-permission") or ANDROID + "sharedUserId" in manifest.attrib:
        raise SystemExit("Unexpected permission or shared UID")
    label = manifest.find("application").get(ANDROID + "label")
    if label != "\u591a\u770b\u591c\u95f4":
        raise SystemExit("Unexpected application label / encoding")

    out = ROOT / "build"
    out.mkdir(exist_ok=True)
    classes = out / ("classes-" + uuid.uuid4().hex)
    classes.mkdir()
    log = []
    def run(command):
        result = subprocess.run([str(arg) for arg in command], capture_output=True,
            text=True, encoding="utf-8", errors="replace")
        log.append(result.stdout + result.stderr)
        (out / "build.log").write_text("\n".join(log), encoding="utf-8")
        if result.returncode: raise SystemExit("Build command failed:\n" + log[-1][-1800:])
        return result.stdout

    java_files = sorted((ROOT / "app/src").rglob("*.java"))
    run(["javac", "-encoding", "UTF-8", "--release", "8", "-cp", android_jar,
         "-d", classes, *java_files])
    dex = out / "classes.dex"
    run(["java", "-jar", dx, "--dex", "--output=" + str(dex), classes])
    resources = out / "resources.zip"
    run([aapt, "compile", "--dir", ROOT / "app/res", "-o", resources])
    unsigned = out / "duokan-night-unsigned.apk"
    run([aapt, "link", "-o", unsigned, "--manifest", ROOT / "app/AndroidManifest.xml",
         "-I", android_jar, resources])
    with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(dex, "classes.dex")
    artifact = unsigned
    if not args.unsigned:
        if not args.signing_key or not args.signing_cert:
            raise SystemExit("Signing requires explicit --signing-key and --signing-cert; or use --unsigned")
        artifact = out / "duokan-night.apk"
        run(["java", "-jar", signer, "sign", "--key", args.signing_key.resolve(),
             "--cert", args.signing_cert.resolve(), "--min-sdk-version", "27",
             "--v2-signing-enabled", "true", "--out", artifact, unsigned])
        verification = run(["java", "-jar", signer, "verify", "--min-sdk-version", "27",
                            "--verbose", "--print-certs", artifact])
        if EXPECTED_CERT not in verification:
            raise SystemExit("Certificate does not match the verified target profile")
    tree = run([aapt, "dump", "--file", "AndroidManifest.xml", artifact])
    if "uses-permission" in tree or "sharedUserId" in tree:
        raise SystemExit("Unexpected permissions in packaged manifest")
    (out / "manifest.txt").write_text(tree, encoding="utf-8")
    info = {"artifact": artifact.name, "bytes": artifact.stat().st_size,
            "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
            "package": manifest.attrib["package"],
            "version": manifest.attrib[ANDROID + "versionName"], "signed": not args.unsigned}
    (out / "artifact.json").write_text(json.dumps(info, indent=2), encoding="utf-8")
    print(json.dumps(info, indent=2))

if __name__ == "__main__":
    main()
