# AniSurge i18n Guide

AniSurge is not English-only. User-facing app UI must go through the runtime i18n layer in this package.

## Supported locales

The first supported locale pack is:

- `en` English, the source and fallback language
- `hi` Hindi
- `es` Spanish
- `pt` Portuguese
- `ar` Arabic
- `id` Indonesian
- `fr` French
- `de` German

## Rules for future changes

- Do not add new hardcoded user-facing English strings directly in composables.
- Add new UI labels to `AppStrings` and access them with `LocalAppStrings.current`.
- Keep backend/media/user content unchanged: anime titles, episode names, genres from API, comments, server names, and external metadata are not translated in the client.
- Keep app UI language separate from playback language. `app_locale` controls interface text only; `default_lang` still controls English dub preference.
- For dynamic text, add typed functions to `AppStrings`, for example `resultsCount(count)` or `downloadEpisode(number)`, instead of string concatenation in UI.
- English must remain a safe fallback for any missing translation.
- Arabic translations are supported, but layout direction stays LTR until RTL QA is explicitly done.

## Implementation pattern

```kotlin
val strings = LocalAppStrings.current
Text(strings.search)
Text(strings.resultsCount(state.results.size))
```

Locale persistence lives in `SettingsStore.appLocaleFlow` under the `app_locale` preference key. The app-level provider is installed in `App`, so changing the setting updates UI immediately.
