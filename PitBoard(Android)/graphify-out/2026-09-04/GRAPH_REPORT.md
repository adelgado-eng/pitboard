# Graph Report - PitBoard(Android)  (2026-09-04)

## Corpus Check
- 145 files · ~132,652 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1348 nodes · 2325 edges · 87 communities (76 shown, 11 thin omitted)
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS · INFERRED: 10 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- EventsScreen.kt
- CarDriverEntity
- .create_workflow
- gradle_run.py
- RaceWidget.kt
- PitBoardApplication.kt
- StandingsCategory
- RaceSeries
- AppSettingsRepository
- StandingsHttpClient
- MotorsportStandingsHtmlSource.kt
- Input
- OfficialRosterStandingsSource
- StandingEntity
- RaceScheduleSource
- AppDatabase
- EventEntity
- StandingsSource
- StandingsClass
- StandingsScreen.kt
- MainActivity.kt
- F1AcademyScheduleSource.kt
- JolpicaF1ScheduleSource.kt
- MotoGpPulseliveScheduleSource.kt
- Advanced Design Patterns
- Data Visualization Design
- WikipediaSeasonCalendarSource
- StandingsRepository.kt
- LeMansCupStandingsSource.kt
- WecStandingsSource.kt
- Animation & Microinteractions
- RaceScheduleRepository
- NotificationScheduler
- ConnectivityHelper
- GtWorldChallengeScheduleSource
- ImsaScheduleSource
- RosterNameFilter
- SyncWorker.kt
- UsScheduleDateParsing
- Material Design 3
- FormulaEScheduleSource
- JsonLdSportsEventScheduleSource
- gradlew
- ResyncResult
- Theming & Dark Mode Design
- MD3 Navigation Patterns
- Typography
- MD3 Layout and Responsive Design
- MD3 Color System
- MD3 Theming and Dynamic Color
- Kotlin Multiplatform: expect/actual boundaries
- using-chrisbanes-skills/SKILL.md
- ElmsStandingsSource
- compose-component-design/SKILL.md
- Compose: UI testing patterns
- RaceWidgetConfigActivity.kt
- WCAG 2.1 AA Checklist
- compose-animations/SKILL.md
- Kotlin value class vs data class
- Accessibility Deep Dive
- The Refactoring UI Framework
- AppTheme
- Compose recomposition performance
- Frontend Design
- Kotlin coroutines: structured concurrency
- Refactoring UI Design System
- Kotlin Flow: state and event modeling
- Compose modifier and layout style
- Compose state deferred reads
- Compose stability diagnostics
- Compose state authoring
- Compose state hoisting
- Using chrisbanes skills
- NotificationScheduler.kt
- Compose performance
- Compose side effects
- Compose state and effects
- Understandable
- Screen Reader Considerations
- Focus Management

## God Nodes (most connected - your core abstractions)
1. `RaceSeries` - 69 edges
2. `StandingsCategory` - 58 edges
3. `EventEntity` - 49 edges
4. `StandingsClass` - 44 edges
5. `StandingEntity` - 40 edges
6. `AppSettingsRepository` - 37 edges
7. `Row` - 30 edges
8. `SeriesConfigEntity` - 25 edges
9. `StandingsHttpClient` - 25 edges
10. `GradleRunProcessTest` - 23 edges

## Surprising Connections (you probably didn't know these)
- `StandingRow()` --calls--> `formatPoints()`  [INFERRED]
  app/src/main/java/com/pitboard/app/ui/CategoryStandingsScreen.kt → app/src/main/java/com/pitboard/app/ui/StandingsScreen.kt
- `RaceWidgetConfigScreen()` --calls--> `WidgetConfig`  [INFERRED]
  app/src/main/java/com/pitboard/app/widget/RaceWidgetConfigActivity.kt → app/src/main/java/com/pitboard/app/widget/WidgetPrefsRepository.kt
- `PitBoardApp()` --calls--> `AppSettingsRepository`  [EXTRACTED]
  app/src/main/java/com/pitboard/app/MainActivity.kt → app/src/main/java/com/pitboard/app/data/AppSettingsRepository.kt
- `PitBoardApp()` --calls--> `RaceScheduleRepository`  [EXTRACTED]
  app/src/main/java/com/pitboard/app/MainActivity.kt → app/src/main/java/com/pitboard/app/schedule/RaceScheduleRepository.kt
- `PitBoardApp()` --calls--> `StandingsRepository`  [EXTRACTED]
  app/src/main/java/com/pitboard/app/MainActivity.kt → app/src/main/java/com/pitboard/app/standings/StandingsRepository.kt

## Import Cycles
- None detected.

## Communities (87 total, 11 thin omitted)

### Community 0 - "EventsScreen.kt"
Cohesion: 0.05
Nodes (40): Activity, Flow, SeriesConfigDao, SeriesConfigEntity, Context, NotificationPermission, FormulaEStandingsSource, Row (+32 more)

### Community 1 - "CarDriverEntity"
Cohesion: 0.10
Nodes (12): CarDriverDao, Flow, CarDriverEntity, AcoCarDriversSource, ElmsDriversSource, Element, ImsaFetchResult, ImsaStandingsSource (+4 more)

### Community 2 - ".create_workflow"
Cohesion: 0.08
Nodes (10): GradleRunHeartbeatTest, GradleRunProcessTest, GradleRunTestCase, GradleRunWindowsProcessTest, GradleRunWorkflowTest, process_is_running(), ProcessAssertionTest, Tests for the compact-output Gradle wrapper. (+2 more)

### Community 3 - "gradle_run.py"
Cohesion: 0.11
Nodes (45): Any, ArgumentParser, add_fingerprint(), append_run(), create_workflow(), default_root(), Diagnostics, display_command() (+37 more)

### Community 4 - "RaceWidget.kt"
Cohesion: 0.09
Nodes (33): BadgeColors, Color, daysUntil(), eventDisplayName(), EventItem(), EventList(), HeroRow(), Context (+25 more)

### Community 5 - "PitBoardApplication.kt"
Cohesion: 0.08
Nodes (12): EventReminderWorker, Result, PitBoardApplication, Context, RaceScheduleScheduler, Context, StandingsScheduler, DateTimeFormatters (+4 more)

### Community 6 - "StandingsCategory"
Cohesion: 0.09
Nodes (18): Flow, StandingDao, StandingsCategory, ELMS, F1, F1_ACADEMY, F2, F3 (+10 more)

### Community 7 - "RaceSeries"
Cohesion: 0.07
Nodes (23): RaceSeries, ELMS, F1, F1_ACADEMY, F2, F3, FORMULA_E, GT_CHALLENGE_AMERICA (+15 more)

### Community 9 - "StandingsHttpClient"
Cohesion: 0.16
Nodes (12): SessionBadgeMatcher, FormulaEItemList, FormulaEListItem, JsonLdItemList, JsonLdListItem, JsonLdLocation, JsonLdSportsEvent, ImsaGalaxyClass (+4 more)

### Community 10 - "MotorsportStandingsHtmlSource.kt"
Cohesion: 0.16
Nodes (11): Moto2StandingsSource, MotoGpStandingsSource, Element, MotorsportStandingsHtmlSource, ParsedRow, PulseliveCareerStep, PulselivePictures, PulseliveProfilePic (+3 more)

### Community 11 - "Input"
Cohesion: 0.04
Nodes (48): Actions, App Bar (Top), Badge, Bottom Sheet, Button Group, Button Sizes (Expressive), Buttons, Card (+40 more)

### Community 12 - "OfficialRosterStandingsSource"
Cohesion: 0.23
Nodes (5): F1StandingsSource, NascarStandingsSource, Enrichment, OfficialRosterStandingsSource, RosterRow

### Community 13 - "StandingEntity"
Cohesion: 0.29
Nodes (10): Element, SpeedSportStandingsSource, StandingEntity, CarDriversSheet(), CategoryStandingsScreen(), formatLastUpdated(), ImagePreview, ImagePreviewDialog() (+2 more)

### Community 14 - "RaceScheduleSource"
Cohesion: 0.15
Nodes (6): BadgeNotificationSetting, SessionBadgeType, RaceScheduleSource, EspnNascarScheduleSource, Element, IndyCarScheduleSource

### Community 15 - "AppDatabase"
Cohesion: 0.24
Nodes (6): AppDatabase, Context, CoroutineWorker, Result, StandingsSyncWorker, RoomDatabase

### Community 16 - "EventEntity"
Cohesion: 0.27
Nodes (3): EventDao, Flow, EventEntity

### Community 17 - "StandingsSource"
Cohesion: 0.36
Nodes (3): DriverParse, IndyCarStandingsSource, StandingsSource

### Community 18 - "StandingsClass"
Cohesion: 0.08
Nodes (21): FormulaEChampionship, FormulaEChampionshipsResponse, FormulaEDriverStanding, FormulaETeamStanding, Element, StandingsClass, GT3, GTD (+13 more)

### Community 19 - "StandingsScreen.kt"
Cohesion: 0.14
Nodes (12): CategoryOutcome, Flow, SyncResult, StandingsRepository, SyncResult, CategoryRow(), formatPoints(), AndroidViewModel (+4 more)

### Community 20 - "MainActivity.kt"
Cohesion: 0.30
Nodes (10): BottomDestination, bottomDestinations(), Bundle, ComponentActivity, MainActivity, PitBoardApp(), PitBoardBottomBar(), StartupLoadingScreen() (+2 more)

### Community 21 - "F1AcademyScheduleSource.kt"
Cohesion: 0.24
Nodes (7): F1AcademyRace, F1AcademyScheduleSource, F1AcademySeasonData, F1AcademySession, NextDataPageProps, NextDataProps, NextDataRoot

### Community 22 - "JolpicaF1ScheduleSource.kt"
Cohesion: 0.27
Nodes (7): JolpicaCircuit, JolpicaF1ScheduleSource, JolpicaMrData, JolpicaRace, JolpicaRaceTable, JolpicaResponse, JolpicaSession

### Community 23 - "MotoGpPulseliveScheduleSource.kt"
Cohesion: 0.31
Nodes (5): MotoGpPulseliveScheduleSource, PulseliveBroadcast, PulseliveCategory, PulseliveCircuit, PulseliveEvent

### Community 24 - "Advanced Design Patterns"
Cohesion: 0.04
Nodes (47): Active/Pressed, Advanced Design Patterns, Avoid, Backdrop, Background Images, Border Radius System, Breadcrumbs, Button Hierarchy (+39 more)

### Community 25 - "Data Visualization Design"
Cohesion: 0.05
Nodes (41): Accessibility in Data Viz, Alternative: Simple Numbers, Annotation Patterns, Bar Chart Rules, Bar Charts, Bar Styling, Categorical Colors, Chart Annotations (+33 more)

### Community 27 - "StandingsRepository.kt"
Cohesion: 0.16
Nodes (7): DriverDbStandingsSource, Element, F1AcademyStandingsSource, F2StandingsSource, F3StandingsSource, Moto3StandingsSource, PorscheSupercupStandingsSource

### Community 28 - "LeMansCupStandingsSource.kt"
Cohesion: 0.44
Nodes (3): Element, LeMansCupStandingsSource, Document

### Community 29 - "WecStandingsSource.kt"
Cohesion: 0.39
Nodes (5): Element, Elements, ParsedRow, WecStandingsSource, org

### Community 30 - "Animation & Microinteractions"
Cohesion: 0.05
Nodes (38): Accessibility Considerations, Animation Checklist, Animation & Microinteractions, Avoid Problematic Animations, Batch Animations, Button States, Common Animation Patterns, Common Easing Curves (+30 more)

### Community 31 - "RaceScheduleRepository"
Cohesion: 0.53
Nodes (4): SyncResult, RaceScheduleRepository, SeriesOutcome, SyncResult

### Community 32 - "NotificationScheduler"
Cohesion: 0.31
Nodes (4): NotificationScheduler, CoroutineWorker, Result, RaceScheduleSyncWorker

### Community 37 - "SyncWorker.kt"
Cohesion: 0.38
Nodes (4): Context, CoroutineWorker, Result, SyncWorker

### Community 39 - "Material Design 3"
Cohesion: 0.06
Nodes (34): Anti-Patterns, App Shell, Audit Methods, Audit Procedure, Basic Usage, Card Grid, Color Tokens (`--md-sys-color-*`), Common Patterns (+26 more)

### Community 42 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 44 - "Theming & Dark Mode Design"
Cohesion: 0.06
Nodes (35): Advanced: Multiple Themes, Brand Themes, Building a Dark Mode Palette, Buttons, Cards and Surfaces, Checklist, Common Mistakes, Component Considerations (+27 more)

### Community 47 - "MD3 Navigation Patterns"
Cohesion: 0.07
Nodes (29): Anatomy, Anatomy, Complete Responsive Shell, CSS Implementation, Decision Tree, Guidelines, Guidelines, Guidelines (+21 more)

### Community 48 - "Typography"
Cohesion: 0.07
Nodes (29): Baseline Type Scale (Default Values), Component Elevation Mapping, Component Shape Mapping, Component Type Usage, Corner Radius Scale, CSS Custom Properties, CSS Custom Properties, CSS Implementation (+21 more)

### Community 49 - "MD3 Layout and Responsive Design"
Cohesion: 0.07
Nodes (28): Adaptive Component Behavior, Audit Checklist for Foldable/Large Screen Support, Book Posture Pattern, Canonical Layouts, Complete App Layout Example, CSS Container Queries, CSS Media Queries (web), dp to px Conversion (+20 more)

### Community 50 - "MD3 Color System"
Cohesion: 0.08
Nodes (23): Accent Colors, Baseline Color Scheme (Default Values), Color Harmonization, Color Pairing Rules, Color Roles, Content-Based, Dark Theme, Dynamic Color (+15 more)

### Community 51 - "MD3 Theming and Dynamic Color"
Cohesion: 0.09
Nodes (22): 1. Choose a Seed Color, 2. Generate the Scheme, 3. Apply the Theme, 4. Export as CSS, Automatic Generation, Brand Color Integration, Color Harmonization for Additional Brand Colors, Component-Level Overrides (web) (+14 more)

### Community 52 - "Kotlin Multiplatform: expect/actual boundaries"
Cohesion: 0.11
Nodes (15): Core principle, Kotlin function ownership, Procedure, Related, Boundary procedure, Choose the boundary, Core principle, Kotlin Multiplatform: expect/actual boundaries (+7 more)

### Community 53 - "using-chrisbanes-skills/SKILL.md"
Cohesion: 0.13
Nodes (12): Core principle, Gradle run, Procedure, Core principle, Kotlin concurrency and Flow, Procedure, Topic router, Core principle (+4 more)

### Community 54 - "ElmsStandingsSource"
Cohesion: 0.33
Nodes (4): ElmsStandingsSource, Element, Elements, TeamRow

### Community 55 - "compose-component-design/SKILL.md"
Cohesion: 0.14
Nodes (11): Compose slot API pattern, Core principle, Exceptions, Procedure, Related, Compose component design, Core principle, Procedure (+3 more)

### Community 56 - "Compose: UI testing patterns"
Cohesion: 0.17
Nodes (12): Callback testing, Compose: UI testing patterns, Core principle, Fake images and platform services, Interaction state with MutableInteractionSource, Keep UI tests deterministic, Keyboard and focus, Prefer plain UI tests (+4 more)

### Community 57 - "RaceWidgetConfigActivity.kt"
Cohesion: 0.35
Nodes (8): ColorPickerDialog(), EventCountRow(), Bundle, Color, ComponentActivity, RaceWidgetConfigActivity, RaceWidgetConfigScreen(), safeParseColor()

### Community 58 - "WCAG 2.1 AA Checklist"
Cohesion: 0.20
Nodes (10): 1.1 Text Alternatives, 1.3 Adaptable, 1.4 Distinguishable, 2.1 Keyboard Accessible, 2.4 Navigable, 4.1 Compatible, Operable, Perceivable (+2 more)

### Community 60 - "compose-animations/SKILL.md"
Cohesion: 0.25
Nodes (6): AnimatedContent identity, API choice, Compose: animations, Core principle, Procedure, When not to use this skill

### Community 61 - "Kotlin value class vs data class"
Cohesion: 0.25
Nodes (8): Contract checks, Core principle, Decision flow, Do not apply, Kotlin value class vs data class, Packed values, Related, Review procedure

### Community 62 - "Accessibility Deep Dive"
Cohesion: 0.25
Nodes (8): Accessibility Deep Dive, Accessibility Philosophy, Automated Testing, Common Fixes Quick Reference, Manual Testing Required, Table of Contents, Testing Checklist, Testing Tools

### Community 64 - "The Refactoring UI Framework"
Cohesion: 0.25
Nodes (8): 1. Visual Hierarchy, 2. Spacing & Sizing, 3. Typography, 4. Color, 5. Depth & Shadows, 6. Images & Icons, 7. Layout & Composition, The Refactoring UI Framework

### Community 65 - "AppTheme"
Cohesion: 0.33
Nodes (5): AppTheme, DARK, LIGHT, SYSTEM, PitBoardTheme()

### Community 66 - "Compose recomposition performance"
Cohesion: 0.29
Nodes (7): Compose recomposition performance, False leads, Related, Review order, Route here → focused skill, Three axes, When NOT to apply

### Community 67 - "Frontend Design"
Cohesion: 0.29
Nodes (6): Design principles, Frontend Design, Ground it in the subject, More on writing in design, Process: brainstorm, explore, plan, critique, build, critique again, Restraint and self-critique

### Community 68 - "Kotlin coroutines: structured concurrency"
Cohesion: 0.29
Nodes (7): Blocking boundaries, Cancellation, Core principle, Incremental refactor, Kotlin coroutines: structured concurrency, Review procedure, Scope and launch choices

### Community 69 - "Refactoring UI Design System"
Cohesion: 0.29
Nodes (7): About the Authors, Common Mistakes, Core Principle, Further Reading, Quick Diagnostic, Refactoring UI Design System, Scoring

### Community 70 - "Kotlin Flow: state and event modeling"
Cohesion: 0.33
Nodes (6): Choose the contract, Core principle, Kotlin Flow: state and event modeling, Related, Sharing and derived state, State initialization and updates

### Community 73 - "Compose modifier and layout style"
Cohesion: 0.40
Nodes (5): Compose modifier and layout style, Core principle, Exceptions, Procedure, Related

### Community 74 - "Compose state deferred reads"
Cohesion: 0.40
Nodes (5): Compose state deferred reads, Core principle, Exceptions, Procedure, Related

### Community 75 - "Compose stability diagnostics"
Cohesion: 0.40
Nodes (5): Compose stability diagnostics, Core principle, Exceptions, Procedure, Related

### Community 76 - "Compose state authoring"
Cohesion: 0.40
Nodes (5): Compose state authoring, Core principle, Exceptions, Procedure, Related

### Community 77 - "Compose state hoisting"
Cohesion: 0.40
Nodes (5): Compose state hoisting, Core principle, Exceptions, Procedure, Related

### Community 78 - "Using chrisbanes skills"
Cohesion: 0.40
Nodes (5): Combination boundaries, Common routes, Core principle, Routing procedure, Using chrisbanes skills

### Community 81 - "Compose performance"
Cohesion: 0.50
Nodes (4): Compose performance, Core principle, Procedure, Topic router

### Community 82 - "Compose side effects"
Cohesion: 0.50
Nodes (4): Compose side effects, Core principle, Exceptions, Procedure

### Community 83 - "Compose state and effects"
Cohesion: 0.50
Nodes (4): Compose state and effects, Core principle, Procedure, Topic router

### Community 84 - "Understandable"
Cohesion: 0.50
Nodes (4): 3.1 Readable, 3.2 Predictable, 3.3 Input Assistance, Understandable

### Community 85 - "Screen Reader Considerations"
Cohesion: 0.50
Nodes (4): Announce Dynamic Changes, Hide Decorative Content, Provide Context, Screen Reader Considerations

### Community 86 - "Focus Management"
Cohesion: 0.50
Nodes (4): Focus Management, Implementation, Roving Tabindex (for component groups), When to Manage Focus

## Knowledge Gaps
- **511 isolated node(s):** `LIGHT`, `DARK`, `SYSTEM`, `F1`, `F2` (+506 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **11 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `RaceSeries` connect `RaceSeries` to `EventsScreen.kt`, `ImsaScheduleSource`, `RaceWidget.kt`, `PitBoardApplication.kt`, `AppSettingsRepository`, `StandingsHttpClient`, `FormulaEScheduleSource`, `RaceScheduleSource`, `EventEntity`, `F1AcademyScheduleSource.kt`, `JolpicaF1ScheduleSource.kt`, `MotoGpPulseliveScheduleSource.kt`, `RaceWidgetConfigActivity.kt`, `WikipediaSeasonCalendarSource`, `RaceScheduleRepository`?**
  _High betweenness centrality (0.057) - this node is a cross-community bridge._
- **Why does `StandingsHttpClient` connect `StandingsHttpClient` to `CarDriverEntity`, `RosterNameFilter`, `MotorsportStandingsHtmlSource.kt`, `StandingEntity`, `RaceScheduleSource`, `StandingsSource`, `StandingsClass`, `F1AcademyScheduleSource.kt`, `JolpicaF1ScheduleSource.kt`, `MotoGpPulseliveScheduleSource.kt`, `ElmsStandingsSource`, `WikipediaSeasonCalendarSource`, `StandingsRepository.kt`, `LeMansCupStandingsSource.kt`, `WecStandingsSource.kt`?**
  _High betweenness centrality (0.035) - this node is a cross-community bridge._
- **Why does `AppSettingsRepository` connect `AppSettingsRepository` to `NotificationScheduler`, `AppTheme`, `EventsScreen.kt`, `SyncWorker.kt`, `RaceSeries`, `NotificationScheduler.kt`, `AppDatabase`, `StandingsScreen.kt`, `MainActivity.kt`, `RaceWidgetConfigActivity.kt`?**
  _High betweenness centrality (0.035) - this node is a cross-community bridge._
- **What connects `LIGHT`, `DARK`, `SYSTEM` to the rest of the system?**
  _511 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `EventsScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.05070028011204482 - nodes in this community are weakly interconnected._
- **Should `CarDriverEntity` be split into smaller, more focused modules?**
  _Cohesion score 0.10338680926916222 - nodes in this community are weakly interconnected._
- **Should `.create_workflow` be split into smaller, more focused modules?**
  _Cohesion score 0.07683000604960677 - nodes in this community are weakly interconnected._