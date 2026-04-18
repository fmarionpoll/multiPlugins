# Shared Dialogs Consolidation — Migration Plan

Companion to `SHARED_DIALOGS_SURVEY.md`. The survey decided **what** and
**why**; this plan describes **how** and **in what order**.

Status: drafted 2026-04-18. Owner: fred + Cursor agent. All design
decisions are already locked (see §8.1 of the survey); this plan is an
execution document.

---

## 0. Purpose, scope, ground rules

### 0.1 Purpose

Consolidate eleven UI components that are duplicated or near-duplicated
between `multiCAFE` and `multiSPOTS96` into `multiTools`, fixing known
bugs in flight and standardising on the better of the two
implementations in each case.

### 0.2 Ground rules

1. **No module restructure.** The existing Maven layout
   (`m_multiPlugins` → `m_multiCAFE` / `m_multiSPOTS96` / `m_multiTools`)
   stays untouched.
2. **No API breaks for downstream callers.** Plugin-side entry points
   (class names under `plugins.fmp.multicafe.*` and
   `plugins.fmp.multiSPOTS96.*`) retain their public signatures until
   removed. Where a class moves, a thin bridge/subclass stays behind
   for the duration of the migration so that stale class references
   (e.g. serialised XML preferences) keep resolving.
3. **One package per PR.** Each numbered work package (P0.1, P1.1, …)
   is intended to be a reviewable, independently-revertable commit or
   PR. Exception: P0.1/P0.2/P0.5 are small enough to batch.
4. **Both plugins must load in Icy after every package.** No
   "temporarily broken trunk" windows. If a package can't be merged
   without breaking one plugin, split it.
5. **No drive-by changes.** Unrelated refactors and bug fixes go into
   separate commits and are not counted as part of the consolidation.
6. **Tests = Icy smoke test.** The projects have no unit test suite;
   acceptance is "compiles, both plugins activate in Icy, targeted
   manual smoke check passes". Where a smoke check is non-obvious, it
   is spelled out per package.
7. **Rollback = `git revert`.** Each package is sized to revert
   cleanly.

### 0.3 Terminology

- **Prerequisite (P0.x)**: infrastructure change that unblocks one or
  more consolidation packages. Must land first.
- **Drop-in (P1.x)**: zero host-interface, ≤ a few hundred lines,
  mechanical move.
- **Host-interface (P2.x)**: needs a `XxxHost` interface in
  `multiTools` and two `XxxHostImpl` implementations in the plugins to
  decouple from `parent0`.
- **Template-method (P3.x)**: base class in `multiTools` with
  plugin-specific subclasses; non-trivial behavioural unification.

### 0.4 What this plan does not cover

- Behavioural redesigns not already agreed in the survey.
- The longer-term "`multicafe.*` deletion" step after all consumers
  route through `multiTools`. That's a follow-up after P3.2 lands.
- Updating `cursor_setup/PROJECT_CONTEXT_DATABASE.md`. That document
  should be refreshed at the end of each phase, but it's not itself a
  migration task.

---

## 1. Dependency graph

Arrows mean "must land before". Packages on the same line are
parallelisable.

```
                       ┌─────── P0.1 (JComboBoxModelSorted) ───────┐
                       │                                           │
                       ├─────── P0.2 (Logger realignment)          │
                       │                                           │
[start] ──────────────┼─────── P0.3 (ResourceUtilFMP)              │
                       │           │                               │
                       │           ▼                               │
                       │     P1.1 (Canvas2D)                       │
                       │                                           │
                       ├─────── P0.4 (getFilteredCheck)            │
                       │                                           │
                       ├─────── P0.5 (DialogTools addLineOfEls)    │
                       │                                           │
                       ├─────── P0.6 (ViewOptionsHolderBase)       │
                       │                                           │
                       └─────── P0.7 (CafeViewOptionsDTO rename)   │
                                                                   ▼
                                                  ┌─── P1.2 (Excel Options)
                                                  │
                                                  ├─── P1.3 (CorrectDrift)
                                                  │
                                                  ├─── P2.1 (Intervals)  ◄── P0.6
                                                  │
                                                  ├─── P2.2 (Infos)      ◄── P0.1, P0.5
                                                  │
                                                  ├─── P2.3 (Filter)     ◄── P0.4
                                                  │
                                                  ├─── P2.4 (SelectFilesPanel)
                                                  │
                                                  ├─── P3.1 (Edit via DescriptorTarget)
                                                  │
                                                  └─── P3.2 (LoadSaveExperimentBase)
```

Parallelisation notes:

- All of P0.x can be done in parallel by different developers. They
  touch disjoint files.
- Within Phase 1 and Phase 2, packages do not touch each other's
  files and are fully parallelisable after prerequisites land.
- P3.1 and P3.2 are the largest; do them last and ideally sequentially
  so review load stays manageable.

---

## 2. Phase 0 — Prerequisites

### P0.1 · Unify on `JComboBoxModelSorted`

**Goal.** Delete `SortedComboBoxModel`. Both `Infos.java` use
`JComboBoxModelSorted`.

**Files.**

- DELETE `multiTools/src/main/java/plugins/fmp/multitools/tools/JComponents/SortedComboBoxModel.java`
- MODIFY `multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/experiment/Infos.java`
  — swap `SortedComboBoxModel` → `JComboBoxModelSorted`.
- MODIFY `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/a_experiment/Infos.java`
  — already imports `JComboBoxModelSorted`; confirm no stray
  `SortedComboBoxModel` references (expected none).

**Design notes.** `JComboBoxModelSorted` has binary-search insertion
and better null handling (survey §2.2). The multiCAFE `Infos` currently
uses the inferior class for no discoverable reason.

**Steps.**

1. Grep for `SortedComboBoxModel` — expect only the class file and the
   multiCAFE `Infos` import + instantiation.
2. Swap import and the two-or-three instantiation sites.
3. Delete the class file.
4. Compile both plugins.

**Acceptance.**

- `grep -r SortedComboBoxModel` returns no hits.
- multiCAFE `Infos` combo boxes still display sorted content (open
  dialog, verify).

**Risk / rollback.** Negligible. `git revert` restores.

**Size.** Trivial (< 20 LOC).

---

### P0.2 · Realign on `multitools.tools.Logger` in multiSPOTS96

**Goal.** Remove `java.util.logging.Logger` use in the files we're
about to consolidate, so migrated panels log through the Eclipse-visible
wrapper.

**Files.** (from survey §7.3.2 — limited to in-scope files only)

- MODIFY `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/a_experiment/CorrectDrift.java`
- MODIFY `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/canvas2D/Canvas2D_3Transforms.java`

**Design notes.** Do NOT sweep the entire codebase — only the files
touched by this consolidation. A broader sweep is an explicit
non-goal (survey §7.3.2).

**Steps.**

1. Replace `java.util.logging.Logger` import with
   `plugins.fmp.multitools.tools.Logger`.
2. Replace `LOGGER.log(Level.XXX, msg, ex)` calls with
   `Logger.warn(msg, ex)` / `Logger.error(...)` / `Logger.info(...)`.
3. Remove `Level` imports if they become unused.
4. Compile.

**Acceptance.**

- Target files no longer import `java.util.logging.*`.
- A deliberate error path (e.g. bad registration input) produces a
  visible log line in Eclipse console.

**Risk / rollback.** Low. Signature of `Logger.warn/error/info` is
compatible.

**Size.** Trivial (≤ 40 LOC across 2 files).

---

### P0.3 · Move `ResourceUtilFMP` and its icons to `multiTools`

**Goal.** Single canonical `plugins.fmp.multitools.resource.ResourceUtilFMP`
with icons co-located in `multiTools` resources.

**Files.**

- CREATE `multiTools/src/main/java/plugins/fmp/multitools/resource/ResourceUtilFMP.java`
- CREATE (move) `multiTools/src/main/resources/plugins/fmp/multitools/icon/alpha/br_prev.png`
- CREATE (move) `multiTools/src/main/resources/plugins/fmp/multitools/icon/alpha/br_next.png`
- CREATE (move) `multiTools/src/main/resources/plugins/fmp/multitools/icon/alpha/fit_Y.png`
- CREATE (move) `multiTools/src/main/resources/plugins/fmp/multitools/icon/alpha/fit_X.png`
- DELETE `multiCAFE/src/main/java/plugins/fmp/multicafe/resource/ResourceUtilFMP.java`
- DELETE `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/resource/ResourceUtilFMP.java`
- DELETE the four PNGs from each plugin's `src/main/resources/plugins/fmp/*/icon/alpha/`
- MODIFY `multiCAFE/.../canvas2D/Canvas2DWithTransforms.java` (and any
  other caller) — update import to the new package.
- MODIFY `multiCAFE/.../canvas2D/Canvas2D_3Transforms.java` — same.
- MODIFY `multiSPOTS96/.../canvas2D/Canvas2D_3Transforms.java` — same.

**Design notes.**

- The class body uses `MultiCAFE.class.getClassLoader()` and path
  `plugins/fmp/multicafe/icon/alpha/`. In `multiTools` it becomes
  `MultiTools.class.getClassLoader()` and
  `plugins/fmp/multitools/icon/alpha/`.
- Verify the PNGs are byte-identical across the two plugins before
  picking one set. If they diverge, use the multiSPOTS96 set (it's the
  newer codebase) and note it in the commit.

**Steps.**

1. `fc /b multiCAFE\src\main\resources\plugins\fmp\multicafe\icon\alpha\br_prev.png multiSPOTS96\src\main\resources\plugins\fmp\multiSPOTS96\icon\alpha\br_prev.png` etc. — document any differences.
2. Create the new file under `multiTools` with the updated classloader
   and path.
3. Move the PNGs.
4. `rg ResourceUtilFMP` to find all consumers; update imports.
5. Delete the old class files and old PNG copies.
6. Compile both plugins.

**Acceptance.**

- Launch Icy with both plugins; open a `Canvas2D_3Transforms` viewer;
  toolbar buttons show their icons (br_prev/br_next/fit_X/fit_Y are
  rendered).
- `grep -r plugins.fmp.multicafe.resource.ResourceUtilFMP` and
  `grep -r plugins.fmp.multiSPOTS96.resource.ResourceUtilFMP` both
  empty.

**Risk / rollback.** If a caller is missed, buttons show as blank
icons (non-fatal but ugly). Rollback by restoring the old class and
old PNGs.

**Size.** Small (≈ 40 LOC + 4 × 2 PNG moves).

---

### P0.4 · Add `getFilteredCheck()` accessor on `LoadSaveExperiment`

**Goal.** Remove direct field access from `Filter` → `LoadSaveExperiment`
so the shared `FilterPanel` can depend only on a method on the host
interface (P2.3).

**Files.**

- MODIFY `multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/browse/LoadSaveExperiment.java`
  — add `public JCheckBox getFilteredCheck() { return filteredCheck; }`.
- MODIFY `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/a_browse/LoadSaveExperiment.java`
  — same.
- MODIFY both `Filter.java` files: switch `parent0.expListTab.filteredCheck`
  (or the equivalent field-chain) to the new accessor.

**Design notes.** `filteredCheck` was public-field in both plugins.
Adding the accessor now (before P2.3 restructures Filter) keeps the
prerequisite change small and mechanical.

**Steps.**

1. Grep `filteredCheck` → 4 consumer files (survey-confirmed).
2. Add the accessor in both `LoadSaveExperiment` classes.
3. Update the two `Filter.java` callers.
4. Leave the field itself package-visible or private — no callers
   outside the two `Filter.java` files should exist after the update.

**Acceptance.**

- Compile succeeds with `filteredCheck` field made `private` in both
  plugins (verify by making it private temporarily, then restoring to
  whatever the surrounding style dictates).
- Filter dialog "Filtered only" checkbox continues to toggle and
  persist in both plugins.

**Risk / rollback.** Low. Pure accessor addition + one-call-site swap
per plugin.

**Size.** Trivial (< 20 LOC).

---

### P0.5 · Replace `addLineOfElements` with `DialogTools.addFiveComponentOnARow` in multiCAFE `Infos`

**Goal.** multiSPOTS96 `Infos` uses the `DialogTools` helper;
multiCAFE `Infos` uses a local helper with the same semantics. Align on
the shared helper before P2.2.

**Files.**

- MODIFY `multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/experiment/Infos.java`
  — replace local `addLineOfElements(...)` call sites with
  `DialogTools.addFiveComponentOnARow(...)`.
- DELETE the local helper method from the same file.

**Design notes.** Pure mechanical rewrite. `DialogTools` is already in
`multiTools` and imported by multiSPOTS96 `Infos` without issue.

**Steps.**

1. Confirm `DialogTools.addFiveComponentOnARow` has the same
   parameter list the multiCAFE caller needs (4 components + column
   layout args).
2. Swap call sites.
3. Delete local helper.
4. Compile.

**Acceptance.**

- multiCAFE `Infos` dialog lays out identically to before (visual
  diff: each row still has the 4-component arrangement).

**Risk / rollback.** Low. Visual regression only.

**Size.** Trivial (< 30 LOC).

---

### P0.6 · `ViewOptionsHolderBase` in `multiTools` + `spotDetectionMode` enum + preferences migration

**Goal.** Factor shared `ViewOptionsHolder` fields (load/save contract,
`viewCages`, `defaultNominalIntervalSec`) into a common base in
`multiTools`. Convert `spotDetectionMode` to an enum. Migrate
multiSPOTS96's `multiSPOTS96Intervals/defaultNominalIntervalSec`
preference into the new shared `viewOptions` node.

**Files.**

- CREATE `multiTools/src/main/java/plugins/fmp/multitools/experiment/ViewOptionsHolderBase.java`
- CREATE `multiTools/src/main/java/plugins/fmp/multitools/experiment/SpotDetectionMode.java`
  (enum: values to be harvested from the current int constants — see
  step 2 below).
- MODIFY `multiCAFE/src/main/java/plugins/fmp/multicafe/ViewOptionsHolder.java`
  — now `extends ViewOptionsHolderBase`; plugin-specific fields stay
  local.
- MODIFY `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/ViewOptionsHolder.java`
  — same; also adds a one-shot migration in `load(XMLPreferences prefs)`:
  if `viewOptions/defaultNominalIntervalSec` is absent but
  `multiSPOTS96Intervals/defaultNominalIntervalSec` exists, copy and
  delete the legacy node.
- MODIFY any consumer that reads `spotDetectionMode` as `int` (grep
  `spotDetectionMode` for the full list) — switch to the enum.

**Design notes.**

- `ViewOptionsHolderBase` owns:
  - `viewCages` (bool)
  - `defaultNominalIntervalSec` (double — used by both plugins now)
  - `load(XMLPreferences prefs)` / `save(XMLPreferences prefs)`
    abstract skeleton with `loadSubclass(prefs)` / `saveSubclass(prefs)`
    hooks.
- Subclasses override the hook methods to handle their plugin-specific
  fields.
- The one-shot preference migration is idempotent: after the first run
  the legacy node is gone, so subsequent loads just read the new
  location.

**Steps.**

1. Read both existing `ViewOptionsHolder.java` files. Identify:
   - shared fields (the two above)
   - plugin-local fields (stay in subclasses)
2. Write `ViewOptionsHolderBase` with the shared fields + template
   methods. Choose a preferences node name: `viewOptions` (survey §12.6).
3. Write `SpotDetectionMode` enum. The multiSPOTS96 codebase currently
   uses `int` constants — find them (grep `spotDetectionMode` or
   `DETECTION_MODE_`), map each to an enum value, keep the ordinal the
   same if any persisted int is still expected to round-trip.
4. Update the two plugin `ViewOptionsHolder` classes to extend the base.
5. Add the one-shot migration in multiSPOTS96's `load()`.
6. Update all `spotDetectionMode` consumers.
7. Compile both plugins.

**Acceptance.**

- Running multiSPOTS96 against a user profile that has
  `multiSPOTS96Intervals/defaultNominalIntervalSec` set: after first
  run, the value appears under `viewOptions/defaultNominalIntervalSec`
  in `preferences.xml`, and the old node is gone.
- `spotDetectionMode` round-trips through save/load as an enum.
- `grep 'int spotDetectionMode'` empty.

**Risk / rollback.** Moderate. Preference migration is stateful —
verify once in a throwaway profile before general rollout.
Rollback: revert the package; old int consumers return, old pref node
is recreated on next save.

**Size.** Medium (~200 LOC split across 4 files + enum).

**Migration-time verification.** When you run multiSPOTS96 the first
time after this package lands, confirm in `%APPDATA%/ICY/preferences.xml`
that the old `multiSPOTS96Intervals` node no longer appears. If it
does, the migration code path didn't fire — debug before shipping.

---

### P0.7 · Rename `ViewOptionsDTO` → `CafeViewOptionsDTO`

**Goal.** Make the DTO's multiCAFE-only nature explicit (survey §12.6,
Option A).

**Files.**

- RENAME `multiTools/src/main/java/plugins/fmp/multitools/experiment/ViewOptionsDTO.java`
  → `CafeViewOptionsDTO.java` (class rename inside file too).
- MODIFY all multiCAFE consumers — grep `ViewOptionsDTO` and update.
- `Experiment.onViewerTPositionChanged(..., CafeViewOptionsDTO dto)`
  — rename parameter type. multiSPOTS96 continues to pass `null`.

**Design notes.** Pure rename. If the refactor tooling in your IDE is
up to date, a single "Rename class" across the workspace is sufficient.

**Steps.**

1. IDE rename (or manual + grep). Verify the file's `package` line is
   untouched.
2. Compile both plugins.

**Acceptance.**

- `grep ViewOptionsDTO` (no `Cafe` prefix) returns no hits in Java
  sources.
- multiCAFE viewer still receives view-option flags correctly (open a
  kymograph viewer, toggle a flag, confirm).

**Risk / rollback.** Trivial.

**Size.** Trivial.

---

## 3. Phase 1 — Drop-in moves (cheap wins)

### P1.1 · Canvas2D package → `multiTools.canvas2D`

**Goal.** Consolidate `Canvas2DConstants`, `Canvas2D_3Transforms`,
`Canvas2D_3TransformsPlugin` into `multiTools`. Because `multiTools` is
itself an Icy `PluginLibrary`, the activator lives in `multiTools` and
the duplicate activators in the plugins disappear.

**Depends on.** P0.2 (logger), P0.3 (ResourceUtilFMP).

**Files.**

- CREATE `multiTools/src/main/java/plugins/fmp/multitools/canvas2D/Canvas2DConstants.java`
- CREATE `multiTools/src/main/java/plugins/fmp/multitools/canvas2D/Canvas2D_3Transforms.java`
- CREATE `multiTools/src/main/java/plugins/fmp/multitools/canvas2D/Canvas2D_3TransformsPlugin.java`
- DELETE all six corresponding plugin-side classes.
- DELETE `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/canvas2D/Canvas2D3TransformsCompat.java`
  (expected obsolete — verify first, see below).
- MODIFY any plugin-side references (`Canvas2DWithTransforms.java` in
  multiCAFE probably) to use the new package.

**Design notes.**

- Survey §9.4: `Canvas2D3TransformsCompat` exists only in multiSPOTS96.
  Before deleting, grep for its uses. If it has active callers, the
  deletion becomes its own mini-package.
- Activator registration: with the `plugins.fmp.multitools.canvas2D.Canvas2D_3TransformsPlugin`
  class present in the `multiTools` jar, Icy will pick it up
  automatically. No XML plugin registration change expected, but
  verify Icy's plugin list shows "Canvas 2D 3 Transforms" exactly once
  (not twice) after the change.

**Steps.**

1. Copy the three classes from whichever plugin's version is
   canonical (the survey treats them as effectively identical). Adjust
   package, logger (already done in P0.2), and `ResourceUtilFMP` import
   (already done in P0.3).
2. Grep `Canvas2D3TransformsCompat` — if it has consumers, pause and
   investigate. If not, delete.
3. Grep for any remaining `plugins.fmp.multicafe.canvas2D` or
   `plugins.fmp.multiSPOTS96.canvas2D` references — update to
   `plugins.fmp.multitools.canvas2D`.
4. Delete the plugin-side classes.
5. Compile both plugins.

**Acceptance.**

- Icy plugin list shows the `Canvas2D_3Transforms` viewer plugin
  exactly once.
- Opening a sequence with that viewer works in both multiCAFE and
  multiSPOTS96.
- Keyboard shortcuts and toolbar buttons in the viewer behave
  identically to pre-migration.

**Risk / rollback.** Medium — Icy plugin registration can be
surprising. If the viewer fails to load, revert and investigate before
re-attempting.

**Size.** Medium (≈ 3 class moves + activator cleanup).

**Migration-time verification.**

1. Open Icy plugin manager: confirm the viewer activator appears
   exactly once.
2. Confirm `Canvas2D3TransformsCompat` really is unreferenced before
   deletion (grep + IDE "Find usages").

---

### P1.2 · Excel Options panel

**Goal.** Single `plugins.fmp.multitools.dlg.excel.ExcelOptionsPanel`
with feature flags (multiCAFE has 3 extra checkboxes).

**Depends on.** No prerequisites.

**Files.**

- CREATE `multiTools/src/main/java/plugins/fmp/multitools/dlg/excel/ExcelOptionsPanel.java`
- MODIFY `multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/excel/Options.java`
  — becomes a thin wrapper or is deleted with callers routed to the
  shared panel.
- MODIFY `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/f_excel/Options.java`
  — same.

**Design notes.**

- Per survey §6, zero `parent0.*` access, so no host interface needed.
- Feature flags: `showStimulusGenerator`, `showFilenameFromCondition`,
  `showAdvancedTiming` (or similar — harvest the three multiCAFE-only
  checkboxes' roles from the current source). Constructor takes the
  flag set.
- Each plugin instantiates `new ExcelOptionsPanel(flags)` and reads
  the checkbox values back through accessors on the panel.

**Steps.**

1. Read both `Options.java` files side by side. Identify the 3
   multiCAFE-extra checkboxes.
2. Write `ExcelOptionsPanel` with all checkboxes. Wire `visible(false)`
   or `add/skip` logic to the flag set.
3. Preferences load/save keeps its existing node name per plugin (the
   panel owns the prefs node name as a constructor argument).
4. Plugin-side `Options.java` becomes a wrapper returning the shared
   panel (or direct consumer — author's choice; wrapper is marginally
   safer for rollback).
5. Compile both plugins.

**Acceptance.**

- multiCAFE Excel Options dialog looks identical to pre-migration (all
  checkboxes present).
- multiSPOTS96 Excel Options dialog looks identical (no unexpected new
  checkboxes).
- Preferences round-trip correctly for both plugins.

**Risk / rollback.** Low. Independent UI panel.

**Size.** Small-to-medium (~200 LOC shared panel + two thin wrappers).

---

### P1.3 · CorrectDrift panel

**Goal.** Single `plugins.fmp.multitools.dlg.experiment.CorrectDriftPanel`.
Both plugins delegate to `multitools.series.*` algorithms already, so
the panel is mostly a UI shell.

**Depends on.** P0.2 (logger). Multicafe's `Register.java` is
**not in scope** — it's a distinct multiCAFE-only panel (survey §5.4).

**Files.**

- CREATE `multiTools/src/main/java/plugins/fmp/multitools/dlg/experiment/CorrectDriftPanel.java`
- MODIFY `multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/experiment/CorrectDrift.java`
  — wrap/delete, see below.
- MODIFY `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/a_experiment/CorrectDrift.java`
  — same.

**Design notes.**

- One `CorrectDriftHost` interface in `multiTools` with a single
  method: `Experiment getCurrentExperiment()` (or whatever the two
  plugins converge on — likely `ExperimentHost` shared across all
  panels in Phase 2; revisit when P2.1 is drafted).
- For Phase 1 keep it minimal: if `CorrectDrift` only needs the
  selected experiment, inject it as a `Supplier<Experiment>` in the
  constructor rather than defining a full host interface. This keeps
  P1.3 decoupled from the Phase 2 host-interface work.

**Steps.**

1. Read both `CorrectDrift.java` files. Catalogue every `parent0.*`
   access (survey says few; confirm).
2. Write `CorrectDriftPanel(Supplier<Experiment>)`.
3. Plugin wrapper: `new CorrectDriftPanel(() -> parent0.expListTab.getCurrentExperiment())`.
4. Compile both plugins.

**Acceptance.**

- Run drift correction on a small test sequence in both plugins.
  Output matches pre-migration (registration log lines, saved output
  files).

**Risk / rollback.** Low.

**Size.** Medium (~150 LOC).

---

## 4. Phase 2 — Host-interface consolidations

### P2.1 · IntervalsPanel + IntervalsHost (pilot)

**Goal.** First host-interface consolidation. Establishes the pattern
for P2.2, P2.3, P2.4.

**Depends on.** P0.6 (so `defaultNominalIntervalSec` preference is in
the shared `viewOptions` node).

**Files.**

- CREATE `multiTools/src/main/java/plugins/fmp/multitools/dlg/experiment/IntervalsHost.java`
- CREATE `multiTools/src/main/java/plugins/fmp/multitools/dlg/experiment/IntervalsPanel.java`
- MODIFY `multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/experiment/Intervals.java`
  — thin shell; instantiates `IntervalsPanel` with a
  `MultiCafeIntervalsHostImpl`.
- MODIFY `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/a_experiment/Intervals.java`
  — same, with `MultiSPOTS96IntervalsHostImpl`.

**Design notes.**

- `IntervalsHost` exposes (draft — finalise during implementation):
  - `Experiment getCurrentExperiment()`
  - `ViewOptionsHolderBase getViewOptions()`
  - `void onIntervalsChanged()` — refresh hook for plugin-side state.
- `IntervalsPanel` inherits both the multiCAFE re-entrancy guard and
  the end-exclusive clamp for `fixedNumberOfImagesJSpinner` (survey
  §1.5 resolved: keep both).
- `descriptorIndex` fast-path and `indexStatusLabel`: if present in the
  Intervals context (not just Filter), carry over. Likely out-of-scope
  for Intervals — verify.

**Steps.**

1. Diff both `Intervals.java` files side by side. Extract the union
   into `IntervalsPanel`.
2. Define `IntervalsHost` with the minimum methods needed.
3. Implement `MultiCafeIntervalsHostImpl` and
   `MultiSPOTS96IntervalsHostImpl` (both delegate to `parent0`
   field-chains).
4. Plugin `Intervals.java` becomes a thin `JPanel` subclass that
   constructs the shared panel and installs the host.
5. Compile both plugins.

**Acceptance.**

- Both plugins open the Intervals dialog with identical layout.
- Entering a fixed-number-of-images count larger than the available
  frame count clamps to the end-exclusive value in both plugins (this
  was a multiCAFE-only bug fix; verify it now fires in multiSPOTS96
  too).
- Re-entrant spinner updates no longer trigger infinite loops
  (multiCAFE: no regression; multiSPOTS96: verify).

**Risk / rollback.** Medium. First host-interface consolidation — the
pattern is new to the codebase. Expect ~1 day of iteration on the
interface shape.

**Size.** Medium (~300 LOC shared panel + host interface + two
shells).

---

### P2.2 · InfosPanel + InfosHost

**Goal.** Consolidate the 8-combo descriptor editor. Fixes the
`clearCombos()` bug (missed `stim2`/`conc2`) in multiSPOTS96.

**Depends on.** P0.1 (JComboBoxModelSorted), P0.5 (DialogTools helper).

**Files.**

- CREATE `multiTools/src/main/java/plugins/fmp/multitools/dlg/experiment/InfosHost.java`
- CREATE `multiTools/src/main/java/plugins/fmp/multitools/dlg/experiment/InfosPanel.java`
- MODIFY both plugin `Infos.java` — thin shells.

**Design notes.**

- `InfosHost` surface similar to `IntervalsHost`. Reuse the same
  `Experiment` accessor; consider promoting an `ExperimentHost`
  super-interface when P2.3 lands.
- `clearCombos()`: the shared version clears all 8 combos (`sex`,
  `strain`, `cond1`, `conc1`, `stim2`, `conc2`, `comment1`, `comment2`).
  Bug fix applies automatically to multiSPOTS96 via consolidation.

**Steps.**

1. Extract the shared panel. Keep the multiCAFE-style logging
   commented (verbose) or use `Logger.info` which doesn't mirror to
   console — intentional quietness (survey §2.5).
2. Define `InfosHost`. Implement in both plugins.
3. Plugin shells.
4. Compile.

**Acceptance.**

- multiSPOTS96 Infos dialog: `clearCombos` action now clears all 8
  fields (manual test: populate all 8, click clear).
- multiCAFE Infos unchanged.

**Risk / rollback.** Low.

**Size.** Medium (~250 LOC).

---

### P2.3 · FilterPanel + FilterHost

**Goal.** Keep the best of both: multiCAFE's auto-ticking checkboxes +
listener factory, multiSPOTS96's `descriptorIndex` fast path and
`indexStatusLabel`.

**Depends on.** P0.4 (`getFilteredCheck()` accessor).

**Files.**

- CREATE `multiTools/src/main/java/plugins/fmp/multitools/dlg/experiment/FilterHost.java`
- CREATE `multiTools/src/main/java/plugins/fmp/multitools/dlg/experiment/FilterPanel.java`
- MODIFY both plugin `Filter.java` — thin shells.

**Design notes.**

- `FilterHost` exposes:
  - `JCheckBox getFilteredCheck()` (now trivial thanks to P0.4)
  - `DescriptorIndex getDescriptorIndex()` — both plugins should
    return theirs; if multiCAFE doesn't have one, it returns `null`
    and the panel falls back to the slow path.
  - `List<Experiment> getExperimentsAsListNoLoad()` (survey §3.5:
    drop `getExperimentsAsList` in favour of the no-load variant).
- Retire multiCAFE's legacy filter entries `capillarytrack`,
  `multicafe`, `roisline`, `MCcapillaries` (survey §10.5). Delete the
  constants and any code that branches on them.

**Steps.**

1. Extract the union panel. Wire up:
   - auto-ticking checkbox behaviour (from multiCAFE).
   - `descriptorIndex` fast path with `null` fallback.
   - `indexStatusLabel` (present for both plugins — survey §3.5).
2. Define `FilterHost`. Implement in both plugins.
3. Remove legacy filter entries.
4. Plugin shells.
5. Compile.

**Acceptance.**

- Both plugins: typing into filter fields auto-ticks the adjacent
  "enabled" checkbox.
- multiCAFE: filter evaluation is noticeably faster on large experiment
  lists (the `descriptorIndex` fast path now applies to both plugins).
- `indexStatusLabel` shows index state in both plugins.
- Legacy filter entry dropdowns no longer show `capillarytrack` etc.

**Risk / rollback.** Medium. Behaviour change visible to multiCAFE
users (faster filter, fewer dropdown options).

**Size.** Medium-large (~350 LOC).

---

### P2.4 · SelectFilesPanel + Config

**Goal.** Unify via a `Config` value object (survey §11).

**Depends on.** None beyond Phase 0.

**Files.**

- CREATE `multiTools/src/main/java/plugins/fmp/multitools/dlg/browse/SelectFilesPanel.java`
- CREATE `multiTools/src/main/java/plugins/fmp/multitools/dlg/browse/SelectFilesPanelConfig.java`
- MODIFY both plugin `SelectFilesPanel.java` — thin shells.

**Design notes.**

- `Config` captures per-plugin differences: default filter entries,
  legacy filename recognition list, initial directory resolver
  (`getResultsDirectory()` — the two accessors are aliases per survey
  §11.5 resolution).
- Because the two accessors point to the same internal variable, use
  `getResultsDirectory()` uniformly. Leave `getExperimentDirectory()`
  in place for now as a thin alias (separate deletion pass later).
- Retire legacy filter entries same as P2.3.

**Steps.**

1. Write `Config` with the plugin-specific fields.
2. Extract `SelectFilesPanel` taking a `Config` in its constructor.
3. Plugin shells instantiate the shared panel with their respective
   `Config`.
4. Compile.

**Acceptance.**

- File selection dialog works in both plugins with the correct default
  filter.
- Legacy filenames (pre-consolidation test data) still load in
  multiCAFE (verify on a known-good legacy experiment).

**Risk / rollback.** Medium. File dialogs are user-visible and
behavioural regressions are noticeable.

**Size.** Medium (~250 LOC).

---

## 5. Phase 3 — Larger design work

### P3.1 · EditPanel via `DescriptorTarget`

**Goal.** Unify `Edit.java` by parameterising over descriptor target
(experiment / capillary / spot / cage). Enables multiSPOTS96 to gain
multiCAFE's advanced two-condition editing mode.

**Depends on.** Survey §4 fully read. No code prerequisites beyond
Phase 0.

**Files.**

- CREATE `multiTools/src/main/java/plugins/fmp/multitools/dlg/experiment/DescriptorTarget.java`
  (interface / strategy for "what am I editing"):
  - `List<String> getFields()`
  - `String getValue(Object descriptor, String field)`
  - `void setValue(Object descriptor, String field, String value)`
  - `String getLabel()`
- CREATE concrete targets:
  - `ExperimentDescriptorTarget` (shared)
  - `CapillaryDescriptorTarget` (multiCAFE)
  - `SpotDescriptorTarget` (multiSPOTS96)
  - `CageDescriptorTarget` (multiSPOTS96)
- CREATE `multiTools/src/main/java/plugins/fmp/multitools/dlg/experiment/EditPanel.java`
- CREATE `multiTools/src/main/java/plugins/fmp/multitools/dlg/experiment/EditHost.java`
- MODIFY both plugin `Edit.java` — thin shells. multiSPOTS96 now
  enables the two-condition UI (survey §4.5).

**Design notes.**

- Combine multiCAFE's two-condition UI and `waitForSaveToComplete()`
  helper with multiSPOTS96's async / progress-bar save pattern.
- `EditPanel` renders N `DescriptorTarget` instances in tabs (one
  tab per target). multiCAFE gets Experiment + Capillary tabs;
  multiSPOTS96 gets Experiment + Spot + Cage tabs.
- Keep the multiCAFE "condition-filtered apply" behaviour — the user
  confirmed this is what `two-condition UI` refers to (survey §4.5
  resolution).
- Async save: all plugins use the `SwingWorker` pattern with progress
  bar. `waitForSaveToComplete()` becomes a test/helper API on the
  panel for deterministic smoke testing.

**Steps.**

1. Write `DescriptorTarget` interface + the four concrete targets.
   Each wraps its underlying domain object (Capillary / Spot / Cage /
   Experiment).
2. Write `EditPanel(EditHost, List<DescriptorTarget>)`.
3. Port multiCAFE's two-condition UI into `EditPanel`.
4. Port multiSPOTS96's async save + progress pattern into `EditPanel`.
5. Plugin shells instantiate with the right target list.
6. Compile and smoke-test both plugins.

**Acceptance.**

- multiCAFE: edit a capillary field in two-condition mode; verify
  apply-only-matching behaviour. No regression vs pre-migration.
- multiSPOTS96: edit a spot field in two-condition mode (now
  available); verify same behaviour.
- Both plugins: save shows a progress bar for large experiments and
  blocks the Edit dialog until complete.

**Risk / rollback.** High. This is the largest per-package design
change. Expect a dedicated week.

**Size.** Large (~600–800 LOC).

---

### P3.2 · LoadSaveExperimentBase (template-method split)

**Goal.** Share the common browsing/loading skeleton; let plugin-side
subclasses handle domain-specific load paths (kymographs, capillaries,
spots, cages).

**Depends on.** P2.4 (SelectFilesPanel) lands first so the shared base
already pulls the consolidated panel.

**Files.**

- CREATE `multiTools/src/main/java/plugins/fmp/multitools/dlg/browse/LoadSaveExperimentBase.java`
- CREATE `multiTools/src/main/java/plugins/fmp/multitools/dlg/browse/LoadSaveExperimentHost.java`
- MODIFY `multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/browse/LoadSaveExperiment.java`
  — now `extends LoadSaveExperimentBase`, overrides:
  - `loadDomainSpecificData(Experiment)` (kymographs, capillaries)
  - `getBinSubdirectoryChooser()` (bin directory dialog — multiCAFE
    only; survey §11.5)
  - Image-count mismatch repair (multiCAFE only).
- MODIFY `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/a_browse/LoadSaveExperiment.java`
  — now `extends LoadSaveExperimentBase`, overrides:
  - `loadDomainSpecificData(Experiment)` (spots, cages)
  - Legacy migration tool entry point.
  - Restore `createButton` ("Create from directories") — survey §11.5.

**Design notes.**

- Image loading path: survey §11.5 resolved in favour of
  multiSPOTS96's approach (no patches). The base class implements
  that path; multiCAFE relinquishes its bespoke image-count mismatch
  repair in favour of the multiSPOTS96 simple path, unless it's
  critical for existing multiCAFE data — flag this for user review
  **before** implementing.
- Promote `openSelectedExperiment()` and `closeAllExperiments()` to
  `public` on the base (survey §11.5 resolution).
- `createButton` ("Create from directories") is reinstated in
  multiSPOTS96 for consistency (survey §11.5 resolution).

**Steps.**

1. Side-by-side diff of the two `LoadSaveExperiment.java` files (they
   are ~500 lines apart). Categorise each block:
   - shared skeleton → base
   - multiCAFE-specific → override
   - multiSPOTS96-specific → override
2. Write `LoadSaveExperimentBase` with the shared skeleton and
   abstract hook methods. Include `openSelectedExperiment` /
   `closeAllExperiments` as `public`.
3. Write the two subclasses.
4. **Before running the real image-loading migration** (survey §11.5):
   run multiCAFE against a dataset known to need the image-count
   mismatch repair. Confirm the multiSPOTS96-style simple loader
   either handles it or surfaces a clear error. If it silently
   corrupts, fall back to a plugin-specific override in the multiCAFE
   subclass.
5. Compile and smoke-test both plugins on representative data.

**Acceptance.**

- multiCAFE: open a kymograph experiment; all capillary/kymo data
  loads correctly.
- multiSPOTS96: open a spot experiment; all spot/cage data loads
  correctly.
- Both: `createButton` present and functional.
- multiCAFE-only: bin-directory chooser still present.
- multiSPOTS96-only: legacy migration tool still reachable.

**Risk / rollback.** High. This is the largest divergence (survey:
498-line diff). Plan for an extra revision round.

**Size.** Large (~800–1000 LOC).

**Migration-time verification.**

1. Open a legacy multiCAFE experiment with known image-count mismatch.
   Confirm load behaviour matches pre-migration.
2. Run multiSPOTS96 legacy migration tool. Confirm it still executes.

---

## 6. Appendix A — File matrix (at a glance)

| Package | New files in `multiTools` | Deleted plugin files | Modified plugin files |
|---|---|---|---|
| P0.1 | — | `SortedComboBoxModel.java` | 2 × `Infos.java` |
| P0.2 | — | — | 2 files (CorrectDrift + Canvas2D_3Transforms in multiSPOTS96) |
| P0.3 | `ResourceUtilFMP.java` + 4 PNGs | 2 × `ResourceUtilFMP.java` + 8 PNGs | Canvas2D callers (3 files) |
| P0.4 | — | — | 2 × `LoadSaveExperiment.java`, 2 × `Filter.java` |
| P0.5 | — | — | multiCAFE `Infos.java` |
| P0.6 | `ViewOptionsHolderBase.java`, `SpotDetectionMode.java` | — | 2 × `ViewOptionsHolder.java` + `spotDetectionMode` consumers |
| P0.7 | `CafeViewOptionsDTO.java` (rename) | `ViewOptionsDTO.java` (old name) | multiCAFE consumers |
| P1.1 | 3 Canvas2D classes | 6 Canvas2D classes + `Canvas2D3TransformsCompat.java` | Canvas2DWithTransforms + any ref |
| P1.2 | `ExcelOptionsPanel.java` | — | 2 × `Options.java` |
| P1.3 | `CorrectDriftPanel.java` | — | 2 × `CorrectDrift.java` |
| P2.1 | `IntervalsHost.java`, `IntervalsPanel.java` | — | 2 × `Intervals.java` |
| P2.2 | `InfosHost.java`, `InfosPanel.java` | — | 2 × `Infos.java` |
| P2.3 | `FilterHost.java`, `FilterPanel.java` | — | 2 × `Filter.java` |
| P2.4 | `SelectFilesPanel.java`, `SelectFilesPanelConfig.java` | — | 2 × `SelectFilesPanel.java` |
| P3.1 | `EditPanel.java`, `EditHost.java`, `DescriptorTarget.java` + 4 targets | — | 2 × `Edit.java` |
| P3.2 | `LoadSaveExperimentBase.java`, `LoadSaveExperimentHost.java` | — | 2 × `LoadSaveExperiment.java` |

---

## 7. Appendix B — Migration-time verifications (consolidated checklist)

These are the checks to perform at a specific package during the
migration — not normal acceptance tests, but one-time "did the
migration itself do what it claims" checks.

| Ref | When | Check |
|---|---|---|
| V1 | After P0.6 lands and multiSPOTS96 is run once | `preferences.xml` no longer has `multiSPOTS96Intervals/defaultNominalIntervalSec`. The value appears under `viewOptions/` instead. |
| V2 | During P1.1, before deletion | `rg Canvas2D3TransformsCompat` returns no active callers. If it does, pause and investigate. |
| V3 | After P1.1 lands | Icy plugin manager shows `Canvas2D_3Transforms` activator exactly once, not twice. |
| V4 | During P3.2, before merge | Run multiCAFE against a dataset with a known image-count mismatch. The multiSPOTS96-style simple loader either handles it cleanly or surfaces a clear error — no silent corruption. |

---

## 8. Suggested cadence

A realistic rhythm for one developer:

- **Week 1:** P0.1, P0.2, P0.4, P0.5, P0.7 (all trivial; batch into
  1–2 commits).
- **Week 2:** P0.3 (ResourceUtilFMP — PNG moves) + P0.6
  (ViewOptionsHolderBase — stateful preference migration, needs care).
- **Week 3:** P1.1 (Canvas2D), P1.2 (Excel Options), P1.3 (CorrectDrift)
  — parallelisable, each a half-day.
- **Week 4:** P2.1 (Intervals pilot — establishes the host-interface
  pattern).
- **Week 5:** P2.2 (Infos), P2.3 (Filter), P2.4 (SelectFilesPanel) in
  sequence (each builds familiarity with the pattern).
- **Week 6–7:** P3.1 (Edit). Dedicated focus.
- **Week 8–9:** P3.2 (LoadSaveExperiment). Dedicated focus.

At the end of each phase, refresh `PROJECT_CONTEXT_DATABASE.md` with
the new package layout.

---

## 9. After the plan

Once all packages are merged, two follow-ups are worth scheduling:

1. **Second-pass logger sweep.** Expand P0.2's scope to every
   remaining `java.util.logging.Logger` usage in the plugin codebases.
   Deferred from Phase 0 to keep the prerequisite lean.
2. **Plugin-side class deletion.** Once all consumers route through
   `multiTools`, the thin plugin-side shells can be deleted and
   callers updated to reference the shared panels directly. This is a
   separate PR per shell, low-risk.

Both are scope-creep for the current plan; they're listed here so
they don't get lost.
