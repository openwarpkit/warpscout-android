---
name: WARPSCOUT for Android
description: Strict native field measurement interface for local WARP operations
colors:
  light-primary: "#1956A3"
  light-background: "#F7F9FC"
  light-text: "#161A20"
  light-surface-variant: "#E5E9EF"
  light-secondary-text: "#414750"
  dark-primary: "#A9C7FF"
  dark-background: "#101318"
  dark-text: "#E2E5EA"
  dark-surface-variant: "#2A2F36"
  dark-secondary-text: "#C2C7D0"
  light-error: "#B3261E"
  dark-error: "#FFB4AB"
typography:
  body:
    fontFamily: "Roboto, sans-serif"
  technical:
    fontFamily: "monospace"
rounded:
  extra-small: "4dp"
  small: "6dp"
  medium: "10dp"
  large: "12dp"
  extra-large: "16dp"
spacing:
  compact: "8dp"
  control: "12dp"
  section: "20dp"
  spacious: "24dp"
---

# Design System: WARPSCOUT for Android

## Overview

**Creative North Star: "Field Measurement Logbook"**

WARPSCOUT is a strict, compact native Android instrument. Direct labels, restrained technical blue, divided rows, and dense measurements make long network operations readable without imitating a terminal. The app background is the continuous canvas.

**Key Characteristics:**

- Native Material 3 structure and interaction
- Compact hierarchy with bottom-pinned primary actions
- Monospace technical values inside system typography
- Flat surfaces with restrained tonal grouping
- Equal support for light and dark themes

## Colors

The static scheme pairs technical blue with cool neutral surfaces and graphite text. Dark theme uses explicit high-contrast role colors rather than an inverted light palette. Dynamic color is optional and disabled by default.

**The Semantic Status Rule.** Status always includes localized text; color is supporting information only.

**The Navigation Surface Rule.** Phone navigation uses `surfaceContainer` only within the exact height of the Navigation Bar. Do not extend that surface into a separator strip or extra backing area.

## Typography

Use the Android system sans-serif through the Material 3 type scale. Reserve headline roles for the onboarding product name and screen context, title roles for sections and controls, and body or label roles for descriptions and metadata. The onboarding wordmark uses a black headline weight with slight letter spacing (1 sp).

Use the platform monospace face for endpoints, protocols, versions, progress fractions, latency, loss, speed, node codes, and scan counts. All text supports system font scaling.

## Layout

Phone screens are single-column and vertically scrollable, with 20 dp horizontal content padding. Onboarding uses 24 dp padding and safe drawing insets. Related controls use 8 to 12 dp spacing; major sections use 16 to 24 dp spacing. Primary actions remain pinned to the bottom action area.

Widths below 720 dp use a Material 3 Navigation Bar for Scan, History, Tools, and Settings. Its `surfaceContainer` backing is exactly the navigation height. Widths at or above 720 dp use a Navigation Rail while preserving content order. About, report, and configuration preview are secondary destinations with standard Android back behavior. Top app bars are compact single-line context bars.

Layouts honor system bars, cutouts, the IME, predictive back, and a 48 by 48 dp minimum touch target. Long identifiers wrap or receive horizontal space; they are never clipped into ambiguous values.

## Elevation & Depth

The system is flat by default. Dividers and Material surface roles establish grouping. Phone navigation uses `surfaceContainer` at zero tonal elevation, without an additional backing strip. Shadows are reserved for transient Material surfaces.

The onboarding cloud is the sole branded depth treatment. Render the exact supplied `warpscout_cloud` asset directly on the app background, with two closely offset warm orange layers beneath it for a slight emboss. Do not redraw, crop, simplify, or place it inside a card.

## Shapes

Use the app's Material shape scale: extra-small 4 dp, small 6 dp, medium 10 dp, large 12 dp, and extra-large 16 dp. Rounded controls remain restrained. Divided lists and technical tables stay structurally flat.

## Components

### Onboarding

Center the embossed Cloudflare cloud and the project name in the available upper area. Do not add onboarding description copy. Keep Create account and Import account as full-width primary and outlined actions pinned at the bottom. Progress and account errors appear directly above them.

### Navigation and top app bars

Use standard Material 3 navigation components. Phone navigation is icon-only with 30 dp icons and localized accessibility descriptions; expanded Navigation Rail destinations retain visible labels. Phone navigation uses `surfaceContainer`, zero tonal elevation, and a backing exactly as tall as the Navigation Bar. Secondary screens use a compact Top App Bar with a standard back icon.

### Presets and Expert mode

Standard, Durable, and Full are filter chips followed by a short description. Expert mode is a labeled full-row switch. Expert fields may retain form state when collapsed, but whenever Expert mode is off every retained expert value is ignored and the scan resolves from preset defaults.

### Switch rows and language

The whole labeled row is the touch target for each Material switch. Interface copy is maintained in Russian and English. Language selection is explicit through System, Russian, and English filter chips.

### Operation state and history

Known totals use determinate progress; unknown totals use indeterminate progress. Metrics use compact monospace values. History is a divided list with preset, protocol, localized status, timestamp, counts, and contextual export actions. Best endpoint details expand inline: the address and icon-only copy action share one row, with endpoint metrics below.

### In-app report

Show reports as horizontally scrollable technical tables, not web views or card stacks. Columns cover status, endpoint, endpoint ping, tunnel ping, loss, observed region, node, node location, and speed. Region and location include country flags for valid two-letter codes. Rows combine text status with restrained semantic tinting.

### Inputs and actions

Use Material outlined fields and filled, outlined, or text buttons according to hierarchy. Primary actions are direct verbs, stay reachable at the bottom, and disable during incompatible operations. Errors name the failed operation and expose the next available action.

Settings opens About with a full-width neutral OutlinedButton. Clear Data uses a full-width error-colored OutlinedButton and always requires confirmation. Configuration preview keeps Share and Download actions visible in its bottom action area.

### About and links

About presents Credits as an ordered list of links with a minimum 48 dp touch target. Project and social destinations use a compact grid of rounded-square buttons with the GitHub, Telegram, supplied channel, supplied chat, and CloudTips logos. Link order is stable. Icon-only actions have localized `contentDescription`; icons paired with visible text may remain silent to avoid duplicate semantics.

## Do's and Don'ts

### Do:

- **Do** keep labels short, factual, localized, and technically precise.
- **Do** use dividers, spacing, and tonal roles instead of decorative cards.
- **Do** verify light theme, dark theme, large font scale, and compact phone navigation.
- **Do** preserve the supplied cloud asset exactly and keep its emboss slight.
- **Do** provide localized accessibility labels for every icon-only control.

### Don't:

- **Don't** add emoji, decorative badges, promotional claims, or terminal styling.
- **Don't** extend the Navigation Bar surface beyond its own height or add a separator strip.
- **Don't** communicate state through color alone or weaken dark-theme contrast.
- **Don't** expose account tokens, private keys, or account IDs in UI, history, logs, or errors.
