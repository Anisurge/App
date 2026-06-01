#!/bin/bash
# False-Positive Submission Helper for VirusTotal Flags
#
# Use this script to generate submission reports for the 3 AV engines
# that flagged the Anisurge Windows installer as malicious.
#
# Usage:
#   ./submit-false-positive.sh <path-to-installer.exe>
#
# Prerequisites:
#   - curl
#   - sha256sum (or shasum on macOS)

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <path-to-installer.exe>"
    exit 1
fi

INSTALLER="$1"
if [ ! -f "$INSTALLER" ]; then
    echo "Error: File not found: $INSTALLER"
    exit 1
fi

# Compute hashes
SHA256=$(sha256sum "$INSTALLER" | cut -d' ' -f1)
SHA1=$(sha1sum "$INSTALLER" | cut -d' ' -f1)
MD5=$(md5sum "$INSTALLER" | cut -d' ' -f1)
FILESIZE=$(stat -c%s "$INSTALLER")
VERSION=$(echo "$INSTALLER" | grep -oP '[\d]+\.[\d]+\.[\d]+\.[\d]+' || echo "unknown")

echo "══════════════════════════════════════════════════════════════"
echo "  Anisurge — False Positive Submission Report"
echo "══════════════════════════════════════════════════════════════"
echo ""
echo "  File:         $(basename "$INSTALLER")"
echo "  Version:      $VERSION"
echo "  Size:         $((FILESIZE / 1024 / 1024)) MB"
echo "  SHA-256:      $SHA256"
echo "  SHA-1:        $SHA1"
echo "  MD5:          $MD5"
echo ""
echo "══════════════════════════════════════════════════════════════"
echo "  App Description"
echo "══════════════════════════════════════════════════════════════"
echo ""
echo "  Application:  Anisurge"
echo "  Type:         Anime streaming desktop application"
echo "  Platform:     Windows (x86_64)"
echo "  Framework:    Kotlin Multiplatform + Compose Desktop"
echo "  Publisher:    Anisurge (to.kuudere.anisuge)"
echo "  Website:      https://anisurge.lol"
echo "  Source:       https://github.com/r3ap3reditz/Anisurge-KMP"
echo "  Language:     Kotlin (open source, auditable)"
echo ""
echo "══════════════════════════════════════════════════════════════"
echo "  Flagging Engines"
echo "══════════════════════════════════════════════════════════════"
echo ""
echo "  1. Gridinsoft — Trojan.Heur!.02012923"
echo "     Submit: https://www.gridinsoft.com/contact"
echo "     Include: SHA-256 + VirusTotal link + explanation"
echo ""
echo "  2. VBA32 — Suspected Of Archive.MailBomb"
echo "     Submit: https://www.vba32.com/contact/"
echo "     Include: SHA-256 + explanation (large JRE bundle)"
echo ""
echo "  3. Trapmine — Malicious.moderate.ml.score"
echo "     Submit: contact@trapmine.com"
echo "     Include: Attach sample or provide download link"
echo ""
echo "══════════════════════════════════════════════════════════════"
echo "  Suggested Email/Form Body"
echo "══════════════════════════════════════════════════════════════"
echo ""
cat << BODY
--- Begin Report ---

Subject: False Positive — Anisurge Windows Installer

To the security team,

The file below is a FALSE POSITIVE detection. It is a legitimate,
open-source anime streaming desktop application built with Kotlin
Multiplatform and Compose Desktop.

The large file size (~390 MB) is due to the embedded Java Runtime
Environment (JRE) bundled by JDK's jpackage tool. No malware or
suspicious behavior is present.

Reasons for heuristic false alarm:
1. Large binary size (390 MB) — bundled JRE + app JARs + native libs
2. JPackage-generated PE structure — uncommon layout triggers ML
3. No code signing certificate applied yet (new release)

Please whitelist this application.

Application:  Anisurge
Version:      $VERSION
SHA-256:      $SHA256
SHA-1:        $SHA1
MD5:          $MD5
Size:         $((FILESIZE / 1024 / 1024)) MB
Source code:  https://github.com/r3ap3reditz/Anisurge-KMP
Website:      https://anisurge.lol

VirusTotal link (if submitted):
  https://www.virustotal.com/gui/file/$SHA256

--- End Report ---
BODY

echo ""
echo "══════════════════════════════════════════════════════════════"
echo "  Next Steps"
echo "══════════════════════════════════════════════════════════════"
echo ""
echo "  1. Upload the installer to VirusTotal to get a permalink:"
echo "     https://www.virustotal.com/gui/"
echo ""
echo "  2. Copy the report above and submit to each flagged engine."
echo ""
echo "  3. For fastest results, get an EV Code Signing certificate."
echo "     See: signing/WINDOWS_SIGNING.md"
echo ""
echo "══════════════════════════════════════════════════════════════"
