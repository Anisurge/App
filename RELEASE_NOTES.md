# Anisurge 0.11.14 — Build 51

**Full Changelog**: [v0.11.13...v0.11.14](https://github.com/Anisurge/App/compare/v0.11.13...v0.11.14)

## 🛡️ VirusTotal False Positive Cleanup

### Windows installer — trimmed JRE modules
- Removed unused JRE modules (`java.sql`, `java.naming`, `java.management`) from the bundled runtime
- Smaller installer footprint + fewer heuristic triggers for AV engines
- Added Windows code signing guide (`signing/WINDOWS_SIGNING.md`) for EV-cert-based elimination
- Added false-positive submission helper (`scripts/submit-false-positive.sh`) for quick AV whitelisting
