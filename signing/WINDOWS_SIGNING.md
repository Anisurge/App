# Windows Code Signing Guide

The VirusTotal false-positive rate on the Windows EXE installer drops to **near zero** when the binary is digitally signed with an **Extended Validation (EV) Code Signing Certificate**. This guide walks you through getting, configuring, and automating Windows code signing for Anisurge.

---

## 1. Why Code Signing Matters

| Factor | Unsigned / Self-Signed | EV-Signed |
|---|---|---|
| Windows SmartScreen | "Windows protected your PC" warning | "Verified publisher" — no warning after reputation builds |
| AV heuristic threshold | Low — large binaries trigger ML easily | High — signed binaries get trust bonus |
| VirusTotal community score | 0 reputation | Immediate reputation from the cert chain |
| Gridinsoft / VBA32 / Trapmine | Likely flagged | Almost always clean |

**With an EV cert, the 3 detections you're seeing (Gridinsoft, VBA32, Trapmine) will almost certainly disappear.**

---

## 2. Getting an EV Code Signing Certificate

Purchase from any of these Certificate Authorities (CAs):

| CA | Price (USD/year) | Notes |
|---|---|---|
| [Sectigo](https://sectigo.com/ssl-certificates-tls/ev-code-signing) | ~$250-350 | Widely accepted, good value |
| [DigiCert](https://www.digicert.com/code-signing/) | ~$300-500 | Highest trust reputation |
| [GlobalSign](https://www.globalsign.com/en/code-signing-certificate) | ~$300-500 | Excellent support |

**EV certs require:**
- Organization validation (business registration, DUNS number or equivalent)
- Hardware token (USB key) or cloud HSM — **EV certs CANNOT be stored as files on disk**
- Physical possession of the token for each signing

Alternatively, a **standard (OV) Code Signing cert** can be stored as a `.pfx` file (easier for CI), but:
- Doesn't get the same SmartScreen instant reputation
- Still significantly reduces AV false positives
- Can be used in the current `signing/windows.pfx` workflow

---

## 3. Setting Up Signing

### Option A: EV Cert via Azure Key Vault / Cloud HSM (Recommended for CI)

1. Store the cert in Azure Key Vault or similar HSM
2. Use `jsign` or `AzureSignTool` in CI to sign after the build:

```bash
# Example with AzureSignTool
AzureSignTool sign \
  -kvu "https://your-vault.vault.azure.net" \
  -kvi "your-client-id" \
  -kvs "your-client-secret" \
  -kvc "your-cert-name" \
  -tr "http://timestamp.digicert.com" \
  -v "path/to/Anisurge-*-setup.exe"
```

### Option B: Standard OV Cert (`.pfx` file)

1. Purchase an OV Code Signing cert from Sectigo/Digicert/GlobalSign
2. Export the cert + private key as `.pfx` (PKCS#12):

```bash
# Export from certificate store (Windows)
certutil -exportPFX -p "your-password" MY "certificate-serial-number" windows.pfx
```

3. Place `windows.pfx` in this directory (`signing/windows.pfx`)
4. The build script (`build.sh`) will auto-detect it and sign:

```bash
./build.sh 0.11.3 54
```

The build script already handles this:
```bash
WIN_CERT="$SCRIPT_DIR/signing/windows.pfx"
if [ -f "$WIN_CERT" ]; then
    WIN_SIGNING_ARGS="-Pcompose.desktop.signing.windows.certificateFile=$WIN_CERT \
                      -Pcompose.desktop.signing.windows.keyPassword=anisuge2026"
fi
```

---

## 4. GitHub Actions CI Signing

In `.github/workflows/build-release.yml`, add a step to sign on Windows runners:

```yaml
windows-sign:
    needs: resolve-version
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Decode signing certificate
        run: |
          mkdir signing -Force
          # Store the cert as a GitHub Actions secret (base64-encoded)
          $cert = [System.Convert]::FromBase64String("${{ secrets.WINDOWS_PFX_BASE64 }}")
          [System.IO.File]::WriteAllBytes("signing/windows.pfx", $cert)
      
      - name: Build and sign Windows installer
        run: |
          ./gradlew :composeApp:packageExe --no-daemon `
            -PappVersion="${{ needs.resolve-version.outputs.version_name }}" `
            -PappBuildNumber="${{ needs.resolve-version.outputs.build_num }}" `
            -Pcompose.desktop.signing.windows.certificateFile="$env:GITHUB_WORKSPACE/signing/windows.pfx" `
            -Pcompose.desktop.signing.windows.keyPassword="${{ secrets.WINDOWS_PFX_PASSWORD }}"
```

**Important:** Set `WINDOWS_PFX_BASE64` and `WINDOWS_PFX_PASSWORD` as repository secrets in GitHub.

---

## 5. Submitting False Positives (Until You Get a Cert)

While waiting for the EV cert, submit the clean installer to the 3 flagging engines:

| Engine | Submission URL | Notes |
|---|---|---|
| **Gridinsoft** | https://www.gridinsoft.com/contact | Include SHA-256 and VirusTotal link |
| **VBA32** | https://www.vba32.com/contact/ | Label them "False positive" |
| **Trapmine** | contact@trapmine.com | Attach the sample or VT link |

See `../scripts/submit-false-positive.sh` for a helper script.

---

## 6. Verification

After signing, verify the signature:

```bash
# Windows
signtool verify /a /v Anisurge-*-setup.exe

# Check with osslsigncode (cross-platform)
osslsigncode verify -in Anisurge-*-setup.exe
```

Re-upload to VirusTotal — the signed binary will have a **new SHA-256**, and the flags should drop to 0/61.
