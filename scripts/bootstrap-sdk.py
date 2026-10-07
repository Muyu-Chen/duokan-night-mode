#!/usr/bin/env python3
"""Download and verify the small Windows SDK components required by build.py."""
from pathlib import Path
import hashlib, os, urllib.request, zipfile
ROOT = Path(__file__).resolve().parents[1]
SDK = ROOT / ".local/android-sdk"
ARCHIVES = ROOT / ".local/downloads"
PACKAGES = [
    ("https://dl.google.com/android/repository/platform-27_r03.zip",
     "35f747e7e70b2d16e0e4246876be28d15ea1c353",
     {"android-8.1.0/android.jar": "platforms/android-27/android.jar"}),
    ("https://dl.google.com/android/repository/build-tools_r28.0.3-windows.zip",
     "05bd35bb48d11c848da2b393c6f864eb609aacba",
     {"android-9/aapt2.exe": "build-tools/28.0.3/aapt2.exe",
      "android-9/lib/dx.jar": "build-tools/28.0.3/lib/dx.jar",
      "android-9/lib/apksigner.jar": "build-tools/28.0.3/lib/apksigner.jar"})
]
def main():
    if os.name != "nt": raise SystemExit("This bootstrap script targets Windows; provide an installed API27 SDK elsewhere.")
    ARCHIVES.mkdir(parents=True, exist_ok=True)
    for url, expected, entries in PACKAGES:
        saved = ARCHIVES / url.rsplit("/", 1)[-1]
        if not saved.exists(): urllib.request.urlretrieve(url, saved)
        if hashlib.sha1(saved.read_bytes()).hexdigest() != expected:
            raise SystemExit("Official SDK archive checksum mismatch; remove the cached archive and retry")
        with zipfile.ZipFile(saved) as archive:
            for member, relative in entries.items():
                destination = SDK / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(archive.read(member))
    print("Verified SDK components are ready in .local/android-sdk")
if __name__ == "__main__": main()
