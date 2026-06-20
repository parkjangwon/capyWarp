# Play Console Release Guide

This project can publish Google Play releases through the Google Play Android Developer API. Use this when Play Console browser upload is blocked or when a repeatable CLI release is safer.

## Safety

- Never commit keystore passwords, API keys, OAuth tokens, or full local property values.
- Commit a Google Play edit only when the user explicitly asked to release, publish, roll out, or submit.
- Always validate before commit.
- Use a new `versionCode` for each Play upload attempt. If Play accepted a bad artifact, bump `versionCode` before retrying.

## CapyWarp Facts

- Package name: `org.parkjw.capywarp`
- Successful Play release: production `7 (1.1.4)`
- Successful AAB SHA256: `24bf4fc3f93a5cdbdaa55cff6e562fae14f82e0aabd6ddec72c29a2c93b8b665`
- `v1.1.4` fixed the Play 16KB memory page size blocker by updating AGP `8.5.0 -> 8.5.2` and bumping `versionCode` to `7`.
- Release signing supports `CAPYWARP_SIGNING_PROPERTIES_FILE` so secrets can be read from an external local properties file without committing them.

## Build A Signed AAB

```bash
CAPYWARP_SIGNING_PROPERTIES_FILE=/path/to/local.properties \
  ./gradlew :app:bundleRelease \
  -x lintVitalAnalyzeRelease \
  -x lintVitalReportRelease \
  -x lintVitalRelease
```

Only skip lint when there is a known environment blocker and note the reason in the release summary.

Verify the artifact:

```bash
shasum -a 256 app/build/outputs/bundle/release/app-release.aab
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

## Prepare Google Play API

```bash
gcloud auth application-default login \
  --scopes=https://www.googleapis.com/auth/androidpublisher,https://www.googleapis.com/auth/cloud-platform,openid,https://www.googleapis.com/auth/userinfo.email

gcloud auth application-default set-quota-project <gcp-project-id>
gcloud services enable androidpublisher.googleapis.com --project=<gcp-project-id>
```

## Publish With The Local Skill Script

The Codex skill lives at:

```text
/Users/pjw/.codex/skills/play-console-release
```

Create release notes JSON:

```json
[
  {"language": "ko-KR", "text": "Gemini 최신 모델 호출 안정화\n이미지 생성 응답 처리 개선"},
  {"language": "en-US", "text": "Stabilized Gemini API calls\nImproved image generation response handling"}
]
```

Run:

```bash
/Users/pjw/.codex/skills/play-console-release/scripts/play_release.sh \
  --package org.parkjw.capywarp \
  --track production \
  --aab app/build/outputs/bundle/release/app-release.aab \
  --release-name "7 (1.1.4)" \
  --version-code 7 \
  --gcp-project <gcp-project-id> \
  --notes-file /tmp/capywarp-release-notes.json \
  --commit
```

The script creates an edit, uploads the AAB, updates the track, validates, and commits only when `--commit` is present.

## 16KB Page Size Error

If Play Console reports `앱이 16KB 메모리 페이지 크기를 지원하지 않습니다`:

1. Inspect native libraries:
   ```bash
   zipinfo -1 app/build/outputs/bundle/release/app-release.aab | rg '\.so$|native.pb'
   ```
2. Check Android Gradle Plugin version. AGP `8.5.0` can fail Play's server-side 16KB validation; `8.5.2` worked for CapyWarp.
3. Bump `versionCode`, rebuild, upload again, and trust Play API upload/validate as the server-side result.
