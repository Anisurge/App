# Requirements Document

## Introduction

The Anisurge KMP Compose Multiplatform app currently shows a fixed set of rows on the Home Screen — Hero Carousel, Continue Watching, Latest Episodes, New on App, and Upcoming — in a hard-coded order. Different users care about different rows: some only watch what they have started, some chase newest releases, some plan around upcoming. This feature lets users personalize the Home Screen by reordering rows and toggling them on or off. Changes apply immediately, persist across launches, and can be reset to the built-in defaults.

The Hero Carousel is governed by an existing toggle (`expanded_hero_carousel`) and is out of scope for reorder/hide. Customization scope is the vertical list of rows below the hero. The TV variant (`TvAppShell`) renders its own subset and is treated separately, as defined below.

## Glossary

- **Anisurge_App**: The KMP Compose Multiplatform client (`to.kuudere.anisuge`) targeting Android, Compose Desktop (Linux/Windows/macOS), and iOS.
- **Home_Screen**: The mobile/desktop home tab rendered by `HomeScreen.kt` (`HomeContent`).
- **TV_Home_Screen**: The Android TV home tab rendered by `TvAppShell.kt` (`TvHomeTab`).
- **Home_Row**: A single horizontally scrolling section below the hero. The configurable Home_Rows are: `continue_watching`, `latest_episodes`, `new_on_app`, `upcoming`.
- **Row_Id**: A stable string identifier of a Home_Row used for persistence. Valid values are the four listed in Home_Row.
- **Layout_Config**: The ordered list of `(Row_Id, visible: Boolean)` pairs that defines the user's Home_Screen layout.
- **Default_Layout**: The built-in Layout_Config: `continue_watching`, `latest_episodes`, `new_on_app`, `upcoming` — all visible, in that order.
- **Layout_Editor**: The settings UI surface where users reorder and toggle Home_Rows.
- **Settings_Store**: The DataStore-backed local persistence layer (`SettingsStore.kt`) that exposes preferences as Kotlin Flows.
- **Settings_Screen**: The settings UI (`SettingsScreen.kt`) containing tabs such as Appearance, Berries, and Store.
- **Hero_Carousel**: The featured carousel at the top of Home_Screen, controlled by the existing `expanded_hero_carousel` preference and not part of Layout_Config.

## Requirements

### Requirement 1: Define Configurable Home Rows

**User Story:** As a user, I want a clearly defined set of Home_Rows that can be customized, so that I know which sections I can personalize.

#### Acceptance Criteria

1. THE Anisurge_App SHALL recognize exactly four configurable Row_Ids, compared as case-sensitive literal strings: `continue_watching`, `latest_episodes`, `new_on_app`, and `upcoming`.
2. THE Anisurge_App SHALL exclude the Hero_Carousel from Layout_Config and SHALL NOT expose the Hero_Carousel as a configurable Row_Id in any customization option.
3. IF a Row_Id read from Settings_Store does not case-sensitively match one of the four configurable Row_Ids, THEN THE Anisurge_App SHALL discard the unknown Row_Id, preserve the relative order of the remaining valid Row_Ids as stored, and continue loading the remaining Layout_Config without aborting.
4. IF Settings_Store contains no Layout_Config entry or contains zero valid Row_Ids after discarding unknown and duplicate entries, THEN THE Anisurge_App SHALL load a default Layout_Config containing all four configurable Row_Ids in the order: `continue_watching`, `latest_episodes`, `new_on_app`, `upcoming`.
5. IF Settings_Store contains duplicate occurrences of the same valid Row_Id, THEN THE Anisurge_App SHALL retain only the first occurrence, discard the subsequent duplicates, and continue loading the remaining Layout_Config.

### Requirement 2: Default Layout

**User Story:** As a new user, I want a sensible default Home_Screen layout, so that the app is useful before I customize anything.

#### Acceptance Criteria

1. IF no Layout_Config is stored in Settings_Store when the Home_Screen renders, THEN THE Anisurge_App SHALL render Home_Rows in the Default_Layout order with every Home_Row's visible flag set to true.
2. IF a Layout_Config retrieved from Settings_Store is missing one or more configurable Row_Ids, THEN THE Anisurge_App SHALL append the missing Row_Ids in Default_Layout order with visible set to true and persist the updated Layout_Config to Settings_Store before rendering Home_Rows.
3. IF the Layout_Editor opens and no Layout_Config can be loaded from Settings_Store because it is absent, unreadable, or fails to parse, THEN THE Layout_Editor SHALL display the Default_Layout with every Home_Row's visible flag set to true.
4. IF a stored Layout_Config in Settings_Store fails to parse or is structurally invalid when the Home_Screen renders, THEN THE Anisurge_App SHALL discard the invalid Layout_Config, render Home_Rows in the Default_Layout order with every Home_Row's visible flag set to true, and overwrite Settings_Store with the Default_Layout.

### Requirement 3: Reorder Home Rows

**User Story:** As a user, I want to reorder Home_Rows, so that the sections I care about appear first.

#### Acceptance Criteria

1. THE Layout_Editor SHALL present every configurable Home_Row in Layout_Config as a draggable list item displaying the row's localized title and a visibility-state indicator showing either "Visible" or "Hidden".
2. WHEN the user releases a dragged Home_Row at a new position in the Layout_Editor, THE Anisurge_App SHALL update the Layout_Config order to reflect the released position within 200 ms of the release.
3. WHEN the Layout_Config order changes, THE Home_Screen SHALL re-render Home_Rows in the new order on next composition without requiring an app restart.
4. WHEN a drag-and-drop reorder gesture completes with a release at a new position, THE Anisurge_App SHALL persist the updated Layout_Config order to Settings_Store within 500 ms of the release.
5. IF the user cancels an in-progress drag gesture before releasing at a new position, THEN THE Anisurge_App SHALL retain the Layout_Config order as it was at the start of the drag and SHALL NOT persist any change.
6. IF persisting a reordered Layout_Config to Settings_Store fails, THEN THE Anisurge_App SHALL revert the Layout_Editor and Home_Screen to the last successfully persisted Layout_Config order and surface a non-blocking error indication that the reorder was not saved.

### Requirement 4: Show and Hide Home Rows

**User Story:** As a user, I want to hide Home_Rows I do not use, so that my Home_Screen shows only the content I want.

#### Acceptance Criteria

1. THE Layout_Editor SHALL render a binary visibility toggle control for each configurable Home_Row entry present in Layout_Config, where the toggle's on/off state matches that Row's current `visible` flag.
2. WHEN the user toggles visibility for a Home_Row in the Layout_Editor, THE Anisurge_App SHALL update the `visible` flag for that Row_Id in Layout_Config and reflect the new state in both the Layout_Editor toggle and the Home_Screen within 200 ms.
3. WHILE a Home_Row has `visible = false` in Layout_Config, THE Home_Screen SHALL omit that Home_Row from rendering, including its section header and any vertical spacing reserved for it.
4. WHEN the user toggles visibility for a Home_Row, THE Anisurge_App SHALL persist the updated Layout_Config to Settings_Store within 500 ms of the toggle interaction.
5. WHILE a Home_Row has `visible = true` in Layout_Config but its underlying data list contains zero items, THE Home_Screen SHALL hide that Home_Row from rendering while preserving its entry and `visible = true` flag in Layout_Config so the Row returns once the data list contains at least one item.
6. WHERE every configurable Home_Row in Layout_Config has `visible = false`, THE Home_Screen SHALL display an empty-layout placeholder containing a button that, when activated, opens the Layout_Editor.
7. IF persisting an updated Layout_Config to Settings_Store fails, THEN THE Anisurge_App SHALL revert the in-memory `visible` flag for the affected Row_Id to its previous value and display an error indication to the user that the change was not saved.
8. WHERE Layout_Config does not contain an entry for a configurable Home_Row known to the Anisurge_App, THE Anisurge_App SHALL treat that Row as `visible = true` for rendering purposes until the user changes it through the Layout_Editor.

### Requirement 5: Immediate Application of Changes

**User Story:** As a user, I want layout changes to apply immediately, so that I do not have to tap a Save button or restart the app.

#### Acceptance Criteria

1. WHEN the user performs a reorder or visibility-toggle interaction in the Layout_Editor, THE Anisurge_App SHALL apply the change to the in-memory Layout_Config within 1 second without requiring the user to press a Save button or restart the app.
2. WHEN the in-memory Layout_Config changes, THE Home_Screen SHALL reflect the change on its next composition within 1 second of the change.
3. THE Anisurge_App SHALL expose Layout_Config as a Kotlin Flow from Settings_Store such that observers receive updated emissions within 500 ms of any persisted Layout_Config change.
4. IF persisting a Layout_Config change to Settings_Store fails, THEN THE Anisurge_App SHALL preserve the in-memory Layout_Config in the user's intended state and surface a non-blocking error indication that the change was not saved.

### Requirement 6: Persistence Across Launches

**User Story:** As a user, I want my Home_Screen layout to persist, so that my customization survives across app launches and updates.

#### Acceptance Criteria

1. WHEN Layout_Config is modified by the user through Home_Screen customization actions, THE Anisurge_App SHALL persist the updated Layout_Config to Settings_Store as a single JSON-encoded string under one preference key within 500 milliseconds of the modification.
2. WHEN the Anisurge_App starts, THE Anisurge_App SHALL load Layout_Config from Settings_Store and complete the load within 1000 milliseconds before rendering Home_Screen row content.
3. IF no Layout_Config value exists in Settings_Store when the Anisurge_App starts, THEN THE Anisurge_App SHALL initialize Layout_Config to Default_Layout, persist Default_Layout to Settings_Store, and render Home_Screen using Default_Layout.
4. IF reading or decoding the stored Layout_Config JSON fails for any reason (malformed syntax, missing required fields, wrong field types, or unrecognized schema version), THEN THE Anisurge_App SHALL fall back to Default_Layout, overwrite the stored value in Settings_Store with the Default_Layout JSON, surface a non-blocking error indication that the saved layout was reset, and render Home_Screen using Default_Layout.
5. WHEN the Anisurge_App starts after an app update and the set of configurable Row_Ids is unchanged from the previous version, THE Anisurge_App SHALL load and apply the previously stored Layout_Config without modification.
6. IF the set of configurable Row_Ids has changed between app versions, THEN THE Anisurge_App SHALL retain stored user preferences for Row_Ids present in both the stored Layout_Config and the new Row_Ids set, apply Default_Layout values for any newly introduced Row_Ids, discard preferences for Row_Ids no longer present, and persist the merged Layout_Config to Settings_Store before rendering Home_Screen content.

### Requirement 7: Reset to Defaults

**User Story:** As a user, I want to reset the Home_Screen layout, so that I can return to the built-in arrangement after experimenting.

#### Acceptance Criteria

1. WHILE the Layout_Editor is open, THE Layout_Editor SHALL display a "Reset to defaults" control.
2. WHEN the user activates the "Reset to defaults" control, THE Anisurge_App SHALL display a confirmation prompt offering an explicit confirm action and an explicit cancel action before any change is applied to Layout_Config.
3. WHEN the user selects the confirm action on the reset prompt, THE Anisurge_App SHALL replace Layout_Config in Settings_Store with the Default_Layout within 1 second.
4. WHEN the Layout_Config replacement completes successfully, THE Home_Screen SHALL re-render within 1 second with Home_Rows in the exact Default_Layout order and with every Home_Row marked visible.
5. IF the user selects the cancel action on the reset prompt, THEN THE Anisurge_App SHALL dismiss the prompt and preserve the existing Layout_Config without modification.
6. IF replacing Layout_Config in Settings_Store fails, THEN THE Anisurge_App SHALL retain the previous Layout_Config, leave the Home_Screen layout unchanged, and display an error indication that the reset did not complete.

### Requirement 8: Layout Editor Discoverability

**User Story:** As a user, I want to find the Layout_Editor easily, so that I can customize my Home_Screen without searching.

#### Acceptance Criteria

1. THE Settings_Screen SHALL display a "Home Layout" entry under the Appearance tab as a navigable list item with a visible text label.
2. WHEN the user activates the "Home Layout" entry in the Appearance tab, THE Anisurge_App SHALL navigate to the Layout_Editor within 500 milliseconds.
3. THE Home_Screen SHALL provide a secondary entry point to the Layout_Editor within its top bar overflow menu, displayed as a menu item with a visible text label that includes "Layout" or "Home Layout".
4. WHEN the user activates the secondary entry point in the Home_Screen overflow menu, THE Anisurge_App SHALL navigate to the Layout_Editor within 500 milliseconds.
5. IF navigation to the Layout_Editor fails to load, THEN THE Anisurge_App SHALL remain on the originating screen and display an error indication that the Layout_Editor could not be opened.

### Requirement 9: TV Variant Behavior

**User Story:** As an Android TV user, I want the Home_Screen customization scope to be defined for TV, so that the experience is predictable on the TV variant.

#### Acceptance Criteria

1. THE TV_Home_Screen SHALL render Home_Rows from Layout_Config whose `visible = true` and whose Row_Id is in the TV_Home_Screen supported Row_Id set, preserving the order defined in Layout_Config.
2. WHEN the TV_Home_Screen encounters a Row_Id that is not in the TV_Home_Screen supported Row_Id set, THE TV_Home_Screen SHALL skip that Row_Id, SHALL NOT display a user-visible error or warning, and SHALL continue rendering the remaining Row_Ids in Layout_Config order.
3. THE TV_Home_Screen SHALL NOT provide any UI affordance, navigation entry point, or menu item that opens or invokes the Layout_Editor.
4. WHEN Layout_Config is updated by the user on a non-TV device variant for the same account, THE TV_Home_Screen SHALL apply the `visible = true` and TV-supported subset of the updated Layout_Config on its next composition or recomposition.
5. IF the `visible = true` and TV-supported subset of Layout_Config is empty, THEN THE TV_Home_Screen SHALL render an empty Home rows area without reporting a layout error and SHALL remain navigable to other TV_Home_Screen UI elements.

### Requirement 10: Layout Editor Accessibility and Feedback

**User Story:** As a user using assistive input or keyboard, I want the Layout_Editor to be accessible, so that I can reorder and toggle Home_Rows without a touch drag gesture.

#### Acceptance Criteria

1. THE Layout_Editor SHALL provide "Move up" and "Move down" controls for each Home_Row in addition to drag handles, and these controls SHALL be focusable by keyboard Tab navigation and Android D-pad navigation with a visible focus indicator.
2. IF a Home_Row is at the first position in Layout_Config, THEN THE Layout_Editor SHALL render the "Move up" control for that Home_Row in a non-interactive disabled state that rejects tap, keyboard Enter or Space, and D-pad center activation, and SHALL expose the disabled state to the platform accessibility service.
3. IF a Home_Row is at the last position in Layout_Config, THEN THE Layout_Editor SHALL render the "Move down" control for that Home_Row in a non-interactive disabled state that rejects tap, keyboard Enter or Space, and D-pad center activation, and SHALL expose the disabled state to the platform accessibility service.
4. WHEN the user changes the order of a Home_Row using keyboard or D-pad input, THE Layout_Editor SHALL announce the affected Home_Row's localized title together with its new position index and total Home_Row count through the platform accessibility service within 500 ms of the order change.
5. WHEN the user changes the visibility of a Home_Row using keyboard or D-pad input, THE Layout_Editor SHALL announce the affected Home_Row's localized title together with its new visibility state ("Shown" or "Hidden") through the platform accessibility service within 500 ms of the visibility change.
6. THE Layout_Editor SHALL display each Home_Row's localized title from `LocalAppStrings`, and IF a localized title is not available for the active locale, THEN THE Layout_Editor SHALL display the Row_Id literal as a fallback.

### Requirement 11: Account Sync Compatibility

**User Story:** As a user with an Anisurge account, I want my future settings sync to be able to carry Layout_Config, so that my customization can follow me across devices when sync arrives.

#### Acceptance Criteria

1. THE Anisurge_App SHALL persist Layout_Config as a JSON object containing a numeric `version` field and a `rows` array, where each `rows` entry is an object containing a string `id` equal to a defined Row_Id and a Boolean `visible` field.
2. THE Anisurge_App SHALL write the `version` field as a non-negative integer set to the schema version recognized by the running build (current value: 1).
3. IF the stored JSON `version` is greater than the schema version recognized by the running Anisurge_App, THEN THE Anisurge_App SHALL render Default_Layout.
4. IF the stored JSON `version` is greater than the schema version recognized by the running Anisurge_App, THEN THE Anisurge_App SHALL retain the stored JSON byte-for-byte without modification or deletion.
5. IF the stored Layout_Config JSON is absent, unparseable, or fails schema validation, THEN THE Anisurge_App SHALL render Default_Layout.
6. IF the stored Layout_Config JSON is present but unparseable or fails schema validation, THEN THE Anisurge_App SHALL leave the existing stored JSON unchanged.
7. WHERE the Anisurge BFF exposes a settings sync endpoint for Layout_Config, THE Anisurge_App SHALL push and pull the Layout_Config JSON using the schema defined in criterion 1 with the same field names, field types, and `version` semantics.

### Requirement 12: Error Handling for Settings Store Failures

**User Story:** As a user, I want the Home_Screen to remain usable if Settings_Store is unavailable, so that a storage failure does not break my browsing experience.

#### Acceptance Criteria

1. IF reading Layout_Config from Settings_Store throws an error, THEN THE Anisurge_App SHALL record the error to the application log within 1 second of the failure.
2. IF reading Layout_Config from Settings_Store throws an error, THEN THE Anisurge_App SHALL render Home_Screen using Default_Layout within 2 seconds of the failure, without surfacing a blocking error dialog.
3. IF writing Layout_Config to Settings_Store throws an error, THEN THE Anisurge_App SHALL retain the in-memory Layout_Config matching the user's intended change and SHALL NOT roll it back to the previously persisted value.
4. IF writing Layout_Config to Settings_Store throws an error, THEN THE Layout_Editor SHALL display a non-blocking error message indicating that saving the layout failed, and the message SHALL remain visible until the user dismisses it or a subsequent write to Settings_Store succeeds.
5. WHILE a write failure error message is displayed in the Layout_Editor, THE Layout_Editor SHALL present a retry control that, when activated, re-submits the current in-memory Layout_Config to Settings_Store.
6. WHEN a retry initiated from the write failure error message completes successfully, THE Layout_Editor SHALL dismiss the error message and remove the retry control within 1 second of the successful write.
