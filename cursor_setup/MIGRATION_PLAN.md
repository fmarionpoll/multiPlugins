# Shared Dialogs — Migration Plan

Companion document to
[cursor_setup/SHARED_DIALOGS_SURVEY.md](cursor_setup/SHARED_DIALOGS_SURVEY.md).
The survey answers *what* to unify and *why*; this plan answers
*how*, in what order, with what rollback, and with which smoke
checks.

All decisions referenced as "(§X.Y)" point back to a resolved entry
in the survey. No new design decisions are introduced here.

---

## 0. Objectives & scope

**Primary goal:** eliminate near-duplicate dialog/UI code between
`multiCAFE` and `multiSPOTS96` by promoting the shared parts into
`multiTools`, using the host-interface pattern where plugin-specific
behaviour remains.

**Secondary goals:**
- Fix a handful of known regressions/drifts (logger, `clearCombos()`,
  `descriptorIndex` fast path in Filter) "for free" as part of the
  consolidation.
- Unify minor utilities that are duplicated or inconsistent across
  plugins (`ResourceUtilFMP`, sorted combo-box model, logger usage,
  `spotDetectionMode` type).
- Create one-time preference-migration code so existing
  multiSPOTS96 users do not lose their nominal-interval setting.

**Out of scope (for this plan):**
- Cross-plugin changes unrelated to dialog consolidation.
- Functional behaviour changes beyond those listed in the survey's
  "Resolved" log.
- Cleanup of `target/` build artefacts currently showing as untracked
  in the working tree — those are IDE-generated and will be handled
  by `.gitignore` hygiene, not this plan.

---

## 1. Phase overview

```mermaid
flowchart TD
    P0[Phase 0 — Prerequisites]

    P1[Phase 1 — Canvas2D]
    P2[Phase 2 — Excel Options]
    P3[Phase 3 — CorrectDrift]
    P4[Phase 4 — Intervals pilot]
    P5[Phase 5 — Infos]
    P6[Phase 6 — Filter]
    P7[Phase 7 — SelectFilesPanel]
    P8[Phase 8 — Edit / DescriptorTarget]
    P9[Phase 9 — LoadSaveExperiment]

    P0 --> P1
    P0 --> P2
    P0 --> P3
    P0 --> P4
    P0 --> P5
    P0 --> P6
    P0 --> P7
    P0 --> P8
    P0 --> P9

    P4 -. pilot for host-interface pattern .-> P5
    P4 -. pilot for host-interface pattern .-> P6
    P4 -. pilot for host-interface pattern .-> P3
    P6 -. getFilteredCheck in P0.4 .-> P0
    P5 -. combo-model in P0.3 .-> P0

    P9 -. most involved; can wait .-> P8
```

Phases 1–8 are mostly parallelisable once Phase 0 is done. Phase 9 is
the largest and most risky; recommend doing it last.

### 1.1 Phase checklist

- [ ] **Phase 0** — Prerequisites (8 work packages, see §3)
- [ ] **Phase 1** — Canvas2D package (3 files moved, 2 plugin
  wrappers deleted)
- [ ] **Phase 2** — Excel Options (1 shared panel, feature flags)
- [ ] **Phase 3** — CorrectDrift (trivial unification after P0.1)
- [ ] **Phase 4** — Intervals pilot (host-interface pattern
  reference implementation)
- [ ] **Phase 5** — Infos (host-interface, `onAfterDuplicateDescriptors`
  hook)
- [ ] **Phase 6** — Filter (host-interface, `getFilteredCheck` +
  `selectExperimentTab`)
- [ ] **Phase 7** — SelectFilesPanel (`Config` value object)
- [ ] **Phase 8** — Edit via `DescriptorTarget` abstraction
- [ ] **Phase 9** — LoadSaveExperiment template-method split

### 1.2 Workflow conventions

- One phase (or sub-package of Phase 0) per commit. Commit messages
  follow the pattern `consolidate: <phase-id> — <one-line summary>`.
- Each commit must leave all three modules (`multiTools`,
  `multiCAFE`, `multiSPOTS96`) compiling (`mvn clean compile` at the
  repo root).
- After each commit, run the plugin from Eclipse/Icy once to confirm
  it starts, the plugin menu item opens, and the affected dialog is
  functional.
- No commits between "doc-updated" and "code-updated" — the survey
  document stays in lock-step with the code.

---

## 2. Package & namespace conventions

New code in `multiTools` lands under these namespaces:

| Area | Namespace |
|---|---|
| Shared dialog panels | `plugins.fmp.multitools.experiment.ui` |
| Dialog host interfaces | `plugins.fmp.multitools.experiment.ui.host` |
| Descriptor scope (Edit) | `plugins.fmp.multitools.experiment.descriptor` |
| Canvas2D | `plugins.fmp.multitools.canvas2D` |
| View options base | `plugins.fmp.multitools` (same level as `MultiTools.java`) |
| Resource utilities | `plugins.fmp.multitools.resource` |

The plugin-specific adapters (host implementations, subclasses) live
inside each plugin:

| Area | multiCAFE | multiSPOTS96 |
|---|---|---|
| Dialog hosts | `plugins.fmp.multicafe.dlg.hosts` | `plugins.fmp.multiSPOTS96.dlg.hosts` |
| `DescriptorTarget` impls | `plugins.fmp.multicafe.dlg.experiment.targets` | `plugins.fmp.multiSPOTS96.dlg.a_experiment.targets` |
| `LoadSaveExperiment` subclass | `plugins.fmp.multicafe.dlg.browse` (existing) | `plugins.fmp.multiSPOTS96.dlg.a_browse` (existing) |

---

## 3. Phase 0 — Prerequisites

Eight small work packages. Each is independently committable and
independently rollback-able. Order below is the recommended
sequence; packages marked "parallelisable" can be done in any order
once P0.0 (branch setup) is done.

### P0.0 — Branch setup (optional)

**Goal:** give the consolidation a dedicated integration branch so
`main` stays stable.

**Steps:**
1. Create branch `consolidate/shared-dialogs` from `main`.
2. All subsequent work lands on this branch.
3. Merge back to `main` phase by phase (or in one go at the end),
   per preference.

**Acceptance:** branch exists; CI (if any) is green.

**Rollback:** delete branch; no impact on `main`.

---

### P0.1 — Logger realignment (2 files)

**Goal (§7.3.2):** eliminate `java.util.logging.Logger` usage from
multiSPOTS96 production sources. Both offenders are on the
consolidation track anyway, but cleaning them first removes noise
from later diffs.

**Preconditions:** none.

**Files touched:**
- Modify:
  `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/canvas2D/Canvas2D_3Transforms.java`
- Modify:
  `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/a_experiment/CorrectDrift.java`

**Steps:**
1. In each file, remove `import java.util.logging.Logger;` and the
   `private static final Logger LOGGER = Logger.getLogger(...)` line.
2. Add `import plugins.fmp.multitools.tools.Logger;`.
3. Replace calls:
   - `LOGGER.severe(msg)` → `Logger.error(msg)`
   - `LOGGER.severe(msg, t)` → `Logger.error(msg, t)`
   - `LOGGER.warning(msg)` → `Logger.warn(msg)`
   - `LOGGER.info(msg)` → `Logger.info(msg)`

**Acceptance:**
- `mvn -pl multiSPOTS96 -am compile` passes.
- Launch multiSPOTS96 and trigger any code path inside
  `Canvas2D_3Transforms` (e.g. open a sequence, switch canvas type)
  and any inside `CorrectDrift` — confirm log output appears in
  Eclipse console with the `[multiSPOTS96] WARN - …` prefix.

**Rollback:** git revert the commit.

**Note:** the test file
`multiSPOTS96/src/test/.../LoadSaveExperimentOptimizedTest0.java`
is intentionally left alone (not user-visible).

---

### P0.2 — `ResourceUtilFMP` into multiTools

**Goal (§7.3.3):** remove the 19-line duplicate of `ResourceUtilFMP`
so the Canvas2D migration (Phase 1) can import a single class.

**Preconditions:** none.

**Files touched:**
- Create: `multiTools/src/main/java/plugins/fmp/multitools/resource/ResourceUtilFMP.java`
- Delete: `multiCAFE/src/main/java/plugins/fmp/multicafe/resource/ResourceUtilFMP.java`
- Delete: `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/resource/ResourceUtilFMP.java`
- Update imports everywhere that currently imports either of the
  plugin-local versions (`grep -r 'fmp\.multicafe\.resource\|fmp\.multiSPOTS96\.resource'` to find them).

**Steps:**
1. Copy the multiCAFE version (or whichever is the more complete of
   the two — compare first with
   `git diff --no-index multiCAFE/.../ResourceUtilFMP.java multiSPOTS96/.../ResourceUtilFMP.java`
   and keep the superset) into the multiTools location.
2. Change the package declaration.
3. Verify that both plugins' resources (icons, images) are still on
   the classpath at the same resource paths used by the class;
   `ResourceUtilFMP` typically looks up via `getResource("/path")`
   which resolves against the bundle classpath.
4. Replace imports in all plugin sources.
5. Delete the two plugin-local files.

**Acceptance:**
- `mvn clean compile` green on all three modules.
- Launch both plugins and confirm icons/resources render
  unchanged — smoke check the main dialog toolbars on both.

**Rollback:** `git revert`; the two plugin-local files reappear and
imports revert.

---

### P0.3 — Sorted combo-box model unification

**Goal (§7.3.1):** eliminate `SortedComboBoxModel` in favour of
`JComboBoxModelSorted` (binary-search insertion, `null` handling,
`removeElement`, correct `fireContentsChanged` indices).

**Preconditions:** none.

**Files touched:**
- Delete: `multiTools/src/main/java/plugins/fmp/multitools/tools/JComponents/SortedComboBoxModel.java`
- Modify every import site: grep the repo for
  `SortedComboBoxModel` and replace with `JComboBoxModelSorted`
  (identical constructor signature — no call-site changes needed
  beyond the type name).

**Steps:**
1. `rg -l 'SortedComboBoxModel'` — list every file that mentions
   the old class.
2. In each file: replace the import `…SortedComboBoxModel;` with
   `…JComboBoxModelSorted;` and the type reference.
3. Delete the `SortedComboBoxModel.java` source.

**Acceptance:**
- `mvn clean compile` green.
- Open the `Infos` dialog (where `SortedComboBoxModel` was used)
  and confirm combo-box entries appear sorted alphabetically after
  typing into the filter field.

**Rollback:** `git revert`.

---

### P0.4 — `getFilteredCheck()` accessor on both `LoadSaveExperiment`

**Goal (§7.3.4):** expose the `filteredCheck` `JCheckBox` via a
public getter on both plugins' `LoadSaveExperiment` panel so the
shared `FilterPanel` (Phase 6) can fetch it through its host
interface without threading plugin-type.

**Preconditions:** none.

**Files touched:**
- Modify:
  `multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/browse/LoadSaveExperiment.java`
- Modify:
  `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/a_browse/LoadSaveExperiment.java`

**Steps:**
1. In each file: if `filteredCheck` is private, either make it
   package-private (if a public getter already exists) or add:
   ```java
   public JCheckBox getFilteredCheck() { return filteredCheck; }
   ```
2. No existing callers change; they can still use
   `parent0.paneBrowse.panelLoadSave.filteredCheck` directly until
   Phase 6. The getter is additive.

**Acceptance:** `mvn compile` green.

**Rollback:** `git revert`.

---

### P0.5 — `DialogTools.addFiveComponentOnARow` used in multiCAFE Infos

**Goal (§7.3.5):** remove multiCAFE's local `addLineOfElements`
helper in `Infos.java` ahead of Phase 5 consolidation.

**Preconditions:** none (but `DialogTools.addFiveComponentOnARow`
must already exist in multiTools — verify with
`rg 'addFiveComponentOnARow' multiTools/`).

**Files touched:**
- Modify:
  `multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/experiment/Infos.java`

**Steps:**
1. Replace calls to the local `addLineOfElements(...)` with
   `DialogTools.addFiveComponentOnARow(...)`, adjusting argument
   order if the signatures differ.
2. Delete the local helper method.
3. Add import `plugins.fmp.multitools.tools.DialogTools` if not
   already present.

**Acceptance:**
- `mvn compile` green.
- Open the Infos dialog in multiCAFE and confirm the 8-combo grid
  renders identically to before (same row heights, same spacing).

**Rollback:** `git revert`.

---

### P0.6 — `ViewOptionsHolderBase` split + multiSPOTS96 prefs migration

**Goal (§12.4, §1.5):** introduce a shared base class with
`load/save(XMLPreferences)` contract,
`viewCages`, `defaultNominalIntervalSec`. Move
`defaultNominalIntervalSec` off the private `multiSPOTS96Intervals`
XMLPreferences node and onto the shared `viewOptions` node, with a
one-shot migration step.

**Preconditions:** P0.1 (clean logging for any migration-diagnostic
lines we emit).

**Files touched:**
- Create: `multiTools/src/main/java/plugins/fmp/multitools/ViewOptionsHolderBase.java`
- Modify: `multiCAFE/src/main/java/plugins/fmp/multicafe/ViewOptionsHolder.java` (extend base, remove shared fields)
- Modify: `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/ViewOptionsHolder.java` (extend base; add `defaultNominalIntervalSec` inherited from base; keep `viewSpots` + `spotDetectionMode` as subclass fields)
- Modify: `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/MultiSPOTS96.java` (`startInterface` calls `viewOptions.load(prefs)` as before)
- Modify: `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/a_experiment/Intervals.java` (read/write via `parent0.viewOptions.getDefaultNominalIntervalSec()`)

**Steps:**

1. Create `ViewOptionsHolderBase` per §12.4 with `load/save`
   orchestration and abstract `loadPluginFields / savePluginFields`.
2. Make `multiCAFE.ViewOptionsHolder extends ViewOptionsHolderBase`
   and move `viewCages` + `defaultNominalIntervalSec` out of the
   subclass; keep the multiCAFE-specific fields
   (`viewCapillaries`, `viewFliesCenter`, `viewFliesRect`,
   `viewTopLevels`, `viewBottomLevels`, `viewDerivative`,
   `viewGulps`) in `loadPluginFields` / `savePluginFields`.
3. Make `multiSPOTS96.ViewOptionsHolder extends
   ViewOptionsHolderBase` and move `viewCages` out of the subclass;
   keep `viewSpots` + `spotDetectionMode` in the subclass overrides.
4. Inside `multiSPOTS96.ViewOptionsHolder.load(XMLPreferences)`
   (before calling super or via an override), perform the one-time
   migration:
   ```java
   // One-time migration: move defaultNominalIntervalSec from the
   // legacy multiSPOTS96Intervals node to the shared viewOptions node.
   XMLPreferences legacy = PluginPreferences
       .getPreferences("multiSPOTS96Intervals");
   if (legacy != null) {
       String legacyValue = legacy.get("defaultNominalIntervalSec", null);
       if (legacyValue != null && prefs.get(KEY_DEFAULT_NOMINAL_INTERVAL_SEC, null) == null) {
           prefs.put(KEY_DEFAULT_NOMINAL_INTERVAL_SEC, legacyValue);
           legacy.remove("defaultNominalIntervalSec");
           Logger.info("Migrated defaultNominalIntervalSec from "
               + "multiSPOTS96Intervals to viewOptions node ("
               + legacyValue + ")");
       }
   }
   ```
   (adjust API call to match the actual `XMLPreferences` type on
   the Icy side; `getPreferences` may need to be accessed via a
   shared helper or a passed-in `PluginActionable`.)
5. Update `multiSPOTS96.dlg.a_experiment.Intervals` so that reads
   and writes go through `parent0.viewOptions.getDefaultNominalIntervalSec()`
   instead of its private XMLPreferences node.
6. Do NOT remove the `multiSPOTS96Intervals` XMLPreferences node
   from the preferences file explicitly; the migration code clears
   the key, and leftover empty nodes are harmless.

**Acceptance:**
- `mvn clean compile` green.
- Launch multiSPOTS96 from a user profile that has a known
  `defaultNominalIntervalSec` value in the legacy
  `multiSPOTS96Intervals` node (e.g. set it to 30s beforehand).
  After startup, open the Intervals dialog and confirm the
  `nominalIntervalJSpinner` default is 30s — the migration
  succeeded.
- Launch a second time; confirm the log line "Migrated
  defaultNominalIntervalSec…" appears only once (the first run).
- multiCAFE continues to work with its existing `"viewOptions"`
  node unchanged.

**Rollback:** `git revert`. For users who have already run the
migrated build once, their preference will remain on the new node;
the rolled-back code falls back to its default (60s) because the
legacy key is gone. Low-impact; worst case users re-enter their
preferred default once.

---

### P0.7 — `ViewOptionsDTO` rename to `CafeViewOptionsDTO`

**Goal (§12.6 Option A):** make it honest that the DTO is a
multiCAFE-only concern.

**Preconditions:** P0.6 (to reduce churn inside
`ViewOptionsHolder.toViewOptionsDTO()`).

**Files touched:**
- Rename: `multiTools/src/main/java/plugins/fmp/multitools/experiment/ViewOptionsDTO.java`
  → `multiTools/src/main/java/plugins/fmp/multitools/experiment/cafe/CafeViewOptionsDTO.java`
- Modify: `multiTools/src/main/java/plugins/fmp/multitools/experiment/Experiment.java`
  (update `onViewerTPositionChanged` parameter type;
  `applyCamViewOptions` + `applyKymosViewOptions` parameter types).
- Modify: `multiCAFE/.../ViewOptionsHolder.java` (`toViewOptionsDTO()` →
  `toCafeViewOptionsDTO()` returning the new type).
- Modify: `multiCAFE/.../MCExperiment_.java` (call-site rename).
- Modify: `multiCAFE/.../dlg/kymos/Intervals.java` (call-site rename).

**Steps:**
1. Create the `…experiment.cafe` package directory under multiTools.
2. Move the class; rename class identifier.
3. Update the three `Experiment.java` method signatures to take
   `CafeViewOptionsDTO` instead of `ViewOptionsDTO`.
4. Update multiCAFE call sites.
5. Confirm multiSPOTS96 still compiles (it should — it never
   constructed the DTO; it always passes `null`).

**Acceptance:**
- `mvn clean compile` green.
- Smoke: open a camera sequence in multiCAFE, let
  `Experiment.onViewerTPositionChanged` fire, confirm the ROI
  visibility toggles still work (cages/capillaries/flies shown or
  hidden per `viewOptions`).

**Rollback:** `git revert`.

---

### P0.8 — `SpotDetectionMode` enum

**Goal (§12.5):** replace free-form `String` with an enum
`{ BASIC, PIPELINED, AUTO }`.

**Preconditions:** P0.6 (the enum lives on
`multiSPOTS96.ViewOptionsHolder` which is already being refactored).

**Files touched:**
- Create: `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/SpotDetectionMode.java` — or in `multitools.experiment` if the enum becomes shared later. Recommendation: plugin-local for now; promote if a second consumer appears.
- Modify: `multiSPOTS96/.../ViewOptionsHolder.java` — field type + getter/setter + load/save logic.
- Modify every call site reading `spotDetectionMode` as a string.

**Steps:**
1. Define enum:
   ```java
   public enum SpotDetectionMode { BASIC, PIPELINED, AUTO }
   ```
2. Change `spotDetectionMode` field type to `SpotDetectionMode` with
   default `SpotDetectionMode.AUTO`.
3. In `loadPluginFields`:
   ```java
   String raw = prefs.get(KEY_SPOT_DETECTION_MODE, "AUTO");
   try {
       spotDetectionMode = SpotDetectionMode.valueOf(raw);
   } catch (IllegalArgumentException e) {
       spotDetectionMode = SpotDetectionMode.AUTO;
   }
   ```
4. In `savePluginFields`: `prefs.put(KEY_SPOT_DETECTION_MODE, spotDetectionMode.name())`.
5. Update call sites; replace string comparisons (`"AUTO".equals(...)`)
   with enum comparisons (`SpotDetectionMode.AUTO == mode`).

**Acceptance:**
- `mvn compile` green.
- Smoke: open the multiSPOTS96 spot-detection dialog, confirm the
  current value loads correctly from preferences and that switching
  modes persists across restart.

**Rollback:** `git revert`.

---

## 4. Phase 1 — Canvas2D package

**Goal (§9):** move `Canvas2DConstants`, `Canvas2D_3Transforms`,
and `Canvas2D_3TransformsPlugin` into `multiTools`, delete both
per-plugin wrappers, and evaluate `Canvas2D3TransformsCompat`.

**Preconditions:** P0.1 (logger realignment), P0.2
(`ResourceUtilFMP` in multiTools).

**Files touched:**

- Create:
  - `multiTools/src/main/java/plugins/fmp/multitools/canvas2D/Canvas2DConstants.java`
  - `multiTools/src/main/java/plugins/fmp/multitools/canvas2D/Canvas2D_3Transforms.java`
  - `multiTools/src/main/java/plugins/fmp/multitools/canvas2D/Canvas2D_3TransformsPlugin.java`
- Delete:
  - `multiCAFE/src/main/java/plugins/fmp/multicafe/canvas2D/Canvas2DConstants.java`
  - `multiCAFE/src/main/java/plugins/fmp/multicafe/canvas2D/Canvas2D_3Transforms.java`
  - `multiCAFE/src/main/java/plugins/fmp/multicafe/canvas2D/Canvas2D_3TransformsPlugin.java`
  - `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/canvas2D/Canvas2DConstants.java`
  - `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/canvas2D/Canvas2D_3Transforms.java`
  - `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/canvas2D/Canvas2D_3TransformsPlugin.java`
- Modify imports of `Canvas2D_3Transforms` / `Canvas2DConstants` in
  consumers.
- Evaluate and likely delete:
  - `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/canvas2D/Canvas2D3TransformsCompat.java`

**Steps:**

1. Pick the multiCAFE versions as the starting point (they already
   use `plugins.fmp.multitools.tools.Logger`).
2. Copy the three files into the multiTools location, changing the
   `package` declaration to `plugins.fmp.multitools.canvas2D`.
3. Replace the `import plugins.fmp.multicafe.resource.ResourceUtilFMP;`
   line with `import plugins.fmp.multitools.resource.ResourceUtilFMP;`
   (available after P0.2).
4. Delete all six plugin-local files.
5. Grep for remaining imports of the old packages across both
   plugins; update to `plugins.fmp.multitools.canvas2D.*`.
6. **Evaluate `Canvas2D3TransformsCompat`:** open the file and read
   its class-doc / method bodies. If it only exists to paper over
   drift between the two `Canvas2D_3Transforms` implementations
   (expected), delete it and update any callers. If it contains
   spot-specific behaviour, keep it but re-target its
   `extends`/`implements` to the shared class.

**Acceptance:**

- `mvn clean compile` green.
- Launch multiCAFE in Icy; open a camera sequence. Right-click the
  canvas → Canvas type menu. Confirm a single "3-Transforms" entry
  appears. Switch to it, confirm the three-step transform toolbar
  renders, transform once, confirm the transformed frames display.
- Launch multiSPOTS96 in Icy separately; repeat. Confirm the same
  single canvas entry appears (no duplicates because it is
  registered from the multiTools jar).
- If both plugins are loaded simultaneously in Icy, confirm the
  canvas entry still appears only once.
- **Migration-time verification (§9.4):** confirmed by the smoke
  checks above.

**Rollback:** `git revert` restores all six plugin-local files and
imports.

---

## 5. Phase 2 — Excel Options

**Goal (§6):** one shared `ExcelOptionsPanel` in multiTools with
a `Features` config object for the three multiCAFE-only checkboxes.

**Preconditions:** P0 complete (for clean logger/deps only; none
strictly required).

**Files touched:**

- Create: `multiTools/src/main/java/plugins/fmp/multitools/experiment/ui/ExcelOptionsPanel.java`
- Delete: `multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/excel/Options.java`
- Delete: `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/f_excel/Options.java`
- Modify callers in `MCExcel_.java` (multiCAFE) and `_DlgExcel_.java`
  (multiSPOTS96).

**Steps:**

1. Take multiCAFE's `Options.java` as the base (it is the superset
   with all three extra checkboxes).
2. Move into `multitools.experiment.ui.ExcelOptionsPanel`.
3. Introduce a `Features` static inner class:
   ```java
   public static final class Features {
       public boolean collateSeries = false;
       public boolean padIntervals  = false;
       public boolean onlyAlive     = false;

       public static Features cafeDefaults() {
           Features f = new Features();
           f.collateSeries = true;
           f.padIntervals  = true;
           f.onlyAlive     = true;
           return f;
       }
       public static Features spots96Defaults() { return new Features(); /* all off */ }
   }
   ```
4. In the constructor, only add a given checkbox if the
   corresponding feature is true.
5. Keep all seven accessor methods
   (`getExcelBuildStep/getStartAllMs/getEndAllMs/getIsFixedFrame/getStartMs/getEndMs/getBinMs`)
   public and unchanged in behaviour.
6. Update `MCExcel_` to construct `new ExcelOptionsPanel(Features.cafeDefaults())`.
7. Update `_DlgExcel_` to construct `new ExcelOptionsPanel(Features.spots96Defaults())`.
8. Delete both plugin-local `Options.java` files.

**Acceptance:**

- `mvn clean compile` green.
- Launch multiCAFE, open Excel export dialog. Confirm all visible
  checkboxes still appear ("Collate series", "Pad intervals",
  "Dead=empty") and that clicking "Export…" produces the same
  output as before.
- Launch multiSPOTS96, open Excel export dialog. Confirm only the
  previously-visible checkboxes (`exportAllFiles`, `transpose`)
  appear and Export produces the same output as before.

**Rollback:** `git revert`; both plugin-local files reappear.

---

## 6. Phase 3 — CorrectDrift

**Goal (§5):** one shared `CorrectDriftPanel` in multiTools; both
plugin-local files deleted.

**Preconditions:** P0.1 (logger realignment — both files now use
the same logger), P0 (`CorrectDriftHost` minimal interface).

**Files touched:**

- Create: `multiTools/src/main/java/plugins/fmp/multitools/experiment/ui/host/DialogHost.java` (shared base interface — see §7.1 of survey)
- Create: `multiTools/src/main/java/plugins/fmp/multitools/experiment/ui/host/CorrectDriftHost.java`
- Create: `multiTools/src/main/java/plugins/fmp/multitools/experiment/ui/CorrectDriftPanel.java`
- Create: `multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/hosts/MultiCafeDialogHost.java` (host adapter — shared across all dialog hosts)
- Create: `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/hosts/MultiSpots96DialogHost.java`
- Delete: `multiCAFE/.../dlg/experiment/CorrectDrift.java`
- Delete: `multiSPOTS96/.../dlg/a_experiment/CorrectDrift.java`
- Modify callers that construct the old `CorrectDrift` class (in
  `MCExperiment_` / `_DlgExperiment_` tabbed-panel setup).

**Steps:**

1. Define the shared base host interface:
   ```java
   // multitools.experiment.ui.host
   public interface DialogHost {
       JComboBoxExperimentLazy getExperimentsCombo();
       DescriptorIndex getDescriptorIndex();
       XMLPreferences getPluginPreferences(String node);
   }
   public interface CorrectDriftHost extends DialogHost { }
   ```
2. Implement `MultiCafeDialogHost` and `MultiSpots96DialogHost`:
   ```java
   public class MultiCafeDialogHost implements DialogHost {
       private final MultiCAFE plugin;
       public MultiCafeDialogHost(MultiCAFE plugin) { this.plugin = plugin; }
       public JComboBoxExperimentLazy getExperimentsCombo() { return plugin.expListComboLazy; }
       public DescriptorIndex getDescriptorIndex() { return plugin.descriptorIndex; }
       public XMLPreferences getPluginPreferences(String n) { return plugin.getPreferences(n); }
   }
   ```
3. Move `CorrectDrift.java` body into
   `multitools.experiment.ui.CorrectDriftPanel`, replacing
   `init(GridLayout, MultiCAFE)` (or `MultiSPOTS96`) with
   `init(GridLayout, CorrectDriftHost)`. Replace
   `parent0.expListComboLazy` with `host.getExperimentsCombo()`.
4. Delete both plugin-local `CorrectDrift.java` files.
5. Update the tabbed-panel wiring in both plugins to construct
   `new CorrectDriftPanel()` and call
   `init(layout, new MultiCafeDialogHost(this))` (or the SPOTS96
   equivalent).

**Acceptance:**

- `mvn clean compile` green.
- Launch each plugin, navigate to the Drift-correction tab. Confirm
  the UI renders identically.
- Load a minimal test experiment, trigger drift correction. Confirm
  it produces the same log output and ROI updates as before.

**Rollback:** `git revert`.

---

## 7. Phase 4 — Intervals pilot

**Goal (§1):** the pilot implementation of the host-interface
pattern. Produces the `IntervalsHost` + shared `IntervalsPanel`
and serves as the template for Phases 5 and 6.

**Preconditions:** P0.6 (`ViewOptionsHolderBase` with
`getDefaultNominalIntervalSec`), Phase 3 (the `DialogHost` base
interface).

**Files touched:**

- Create: `multiTools/src/main/java/plugins/fmp/multitools/experiment/ui/host/IntervalsHost.java`
- Create: `multiTools/src/main/java/plugins/fmp/multitools/experiment/ui/IntervalsPanel.java`
- Modify: `multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/hosts/MultiCafeIntervalsHost.java` (new class — thin adapter)
- Modify: `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/hosts/MultiSpots96IntervalsHost.java` (new class)
- Delete: `multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/experiment/Intervals.java`
- Delete: `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/a_experiment/Intervals.java`
- Modify wiring in `MCExperiment_` and `_DlgExperiment_`.

**Steps:**

1. Define `IntervalsHost extends DialogHost` (§1.4) with:
   - `int getDefaultNominalIntervalSec()` / setter (delegate to
     `plugin.viewOptions.getDefaultNominalIntervalSec()`)
   - `void onAfterIntervalsApply(Experiment exp)` — plugin-specific
     post-apply action
   - `default void onFirstImageIndexChanged(Experiment exp) { }`
2. Move `Intervals.java` body into
   `multitools.experiment.ui.IntervalsPanel`. Starting point: the
   multiCAFE version (it has the re-entrancy guard §1.6 and the
   clamp). Apply these modifications during the move:
   - Replace `parent0.expListComboLazy.*` with
     `host.getExperimentsCombo().*`.
   - Replace
     `parent0.viewOptions.getDefaultNominalIntervalSec()` with
     `host.getDefaultNominalIntervalSec()`.
   - Replace the post-apply block (which currently does
     `paneExperiment.updateDialogs(exp)` etc. for multiCAFE /
     `dlgBrowse.loadSaveExperiment.closeCurrentExperiment() + openSelectedExperiment(exp)`
     for multiSPOTS96) with `host.onAfterIntervalsApply(exp)`.
   - Replace `parent0.paneExperiment.updateViewerForSequenceCam(exp)`
     (in the first-image-index change listener) with
     `host.onFirstImageIndexChanged(exp)`.
   - Keep `updateNFramesButton`, the re-entrancy guard
     (`updatingFromExperiment`), and the end-exclusive clamp
     (§1.6).
   - Handle the `GenerationMode.UNKNOWN` summary label: **coerce
     to `DIRECT_FROM_STACK`** if `host` is the multiSPOTS96
     adapter. Implementation: add a
     `default GenerationMode coerceGenerationMode(GenerationMode gm)`
     method to `IntervalsHost` that returns `gm` unchanged by
     default; multiSPOTS96's adapter overrides it to coerce
     `UNKNOWN → DIRECT_FROM_STACK`.
3. Implement `MultiCafeIntervalsHost` and
   `MultiSpots96IntervalsHost`. Both extend
   `MultiCafeDialogHost` / `MultiSpots96DialogHost` (respectively)
   and add the Intervals-specific methods:
   - multiCAFE `onAfterIntervalsApply(exp)`:
     ```java
     plugin.paneBrowse.panelLoadSave.closeCurrentExperiment();
     plugin.paneExperiment.updateDialogs(exp);
     plugin.paneExperiment.updateViewerForSequenceCam(exp);
     plugin.paneExperiment.tabOptions.applyCentralViewOptionsToCamViewer(exp);
     ```
   - multiSPOTS96 `onAfterIntervalsApply(exp)`:
     ```java
     plugin.dlgBrowse.loadSaveExperiment.closeCurrentExperiment();
     plugin.dlgBrowse.loadSaveExperiment.openSelectedExperiment(exp);
     ```
   - multiCAFE `onFirstImageIndexChanged(exp)`:
     `plugin.paneExperiment.updateViewerForSequenceCam(exp);`
   - multiSPOTS96 `onFirstImageIndexChanged(exp)`: default no-op.
4. Update the tabbed-panel wiring in both plugins.
5. Delete both plugin-local `Intervals.java` files.

**Acceptance:**

- `mvn clean compile` green.
- **multiCAFE smoke test:** load an experiment, open Intervals.
  - Advance `indexFirstImageJSpinner` — confirm viewer updates
    (the `onFirstImageIndexChanged` hook).
  - Enter a `fixedNumberOfImages` larger than images on disk;
    confirm clamp prevents over-count (§1.6).
  - Click Apply; confirm paneExperiment + viewer refresh fires.
- **multiSPOTS96 smoke test:** load an experiment, open Intervals.
  - Change nominal interval, click Apply; confirm experiment
    closes and re-opens via the `loadSaveExperiment` calls.
  - Change the default nominal interval; exit and restart;
    confirm the default persists (proves P0.6 migration works
    end-to-end).
  - Confirm summary label shows `"direct from stack"` for an
    experiment with `GenerationMode.UNKNOWN`.

**Rollback:** `git revert` restores both plugin-local files.

**Pilot artefacts to reuse in phases 5 and 6:**
- `DialogHost` base interface.
- `MultiCafeDialogHost` / `MultiSpots96DialogHost` adapters.
- The `XxxHost extends DialogHost` pattern.
- The host-file layout under `dlg/hosts/`.

---

## 8. Phase 5 — Infos

**Goal (§2):** shared `InfosPanel` in multiTools.

**Preconditions:** Phase 4 (pilot + `DialogHost`), P0.3 (combo
model unification), P0.5 (`DialogTools.addFiveComponentOnARow`).

**Files touched:**

- Create: `multiTools/src/main/java/plugins/fmp/multitools/experiment/ui/host/InfosHost.java`
- Create: `multiTools/src/main/java/plugins/fmp/multitools/experiment/ui/InfosPanel.java`
- Create: `multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/hosts/MultiCafeInfosHost.java`
- Create: `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/hosts/MultiSpots96InfosHost.java`
- Delete: `multiCAFE/.../dlg/experiment/Infos.java`
- Delete: `multiSPOTS96/.../dlg/a_experiment/Infos.java`

**Steps:**

1. Define `InfosHost extends DialogHost` with:
   - `default void onAfterDuplicateDescriptors(Experiment source, Experiment destination) { }`
2. Move `Infos.java` into `InfosPanel` starting from whichever
   version compiles cleanly (they're 95% identical). Apply:
   - `SortedComboBoxModel` → `JComboBoxModelSorted` (already done
     in P0.3).
   - `addLineOfElements` → `DialogTools.addFiveComponentOnARow`
     (already done in P0.5 for multiCAFE).
   - Replace `parent0.expListComboLazy.*` / `parent0.descriptorIndex.*`
     with `host.getExperimentsCombo()` / `host.getDescriptorIndex()`.
   - **Carry multiCAFE's `clearCombos()` fix** (include `stim2Combo`
     and `conc2Combo` in the clears, §2.5 resolution).
   - `zoomToUpperCorner`: pick multiCAFE's toggle behaviour (zoom
     in/out) over multiSPOTS96's "always zoom in".
   - Wire `duplicatePreviousDescriptors()` to call
     `host.onAfterDuplicateDescriptors(exp0, exp)` after the
     descriptor transfer.
3. `MultiCafeInfosHost.onAfterDuplicateDescriptors(exp0, exp)`:
   ```java
   plugin.paneCapillaries.tabCreate.setGroupedBy2(/* bool from exp0 */);
   plugin.paneCapillaries.tabInfos.setDlgInfosCapillaryDescriptors(exp.getCapillaries());
   // + the volume transfer logic from transferPreviousExperimentCapillariesInfos()
   ```
   (copy the logic from the current
   `transferPreviousExperimentCapillariesInfos` method.)
4. `MultiSpots96InfosHost.onAfterDuplicateDescriptors(...)`: default
   no-op (inherited).

**Acceptance:**

- `mvn clean compile` green.
- multiCAFE: load two experiments, select the second, open Infos,
  click "Get previous", confirm capillary volumes and grouping
  transfer across (this is the multiCAFE-specific extension).
- multiSPOTS96: load two experiments, select the second, open
  Infos, click "Get previous", confirm descriptors transfer.
- Open Infos in multiSPOTS96, populate stim2/conc2, click "Clear",
  confirm **all** 8 combos clear (regression test for §2.5).

**Rollback:** `git revert`.

---

## 9. Phase 6 — Filter

**Goal (§3):** shared `FilterPanel` in multiTools.

**Preconditions:** Phase 4 (pilot), P0.4 (`getFilteredCheck()`
accessor).

**Files touched:**

- Create: `multiTools/src/main/java/plugins/fmp/multitools/experiment/ui/host/FilterHost.java`
- Create: `multiTools/src/main/java/plugins/fmp/multitools/experiment/ui/FilterPanel.java`
- Create: `multiCAFE/.../dlg/hosts/MultiCafeFilterHost.java`
- Create: `multiSPOTS96/.../dlg/hosts/MultiSpots96FilterHost.java`
- Delete: `multiCAFE/.../dlg/experiment/Filter.java`
- Delete: `multiSPOTS96/.../dlg/a_experiment/Filter.java`

**Steps:**

1. Define `FilterHost extends DialogHost`:
   ```java
   public interface FilterHost extends DialogHost {
       JCheckBox getFilteredCheck();
       void selectExperimentTab();
   }
   ```
2. Move `Filter.java` body into `FilterPanel`. Start from the
   multiCAFE version (has the listener factory) and layer in:
   - **Adopt multiCAFE's listener factory** (auto-ticks field's
     checkbox after "Select…", §3.5).
   - **Adopt multiSPOTS96's `indexStatusLabel`** on a 5th row
     (§3.5).
   - **Adopt multiSPOTS96's `descriptorIndex` fast path** in
     `getValuesForField(...)` with fallback to lightweight scan
     (§3.5).
   - **Use `host.getExperimentsCombo().getExperimentsAsListNoLoad()`**
     everywhere (§3.5) — drop `getExperimentsAsList`.
3. Implement adapters:
   - multiCAFE: `getFilteredCheck()` →
     `plugin.paneBrowse.panelLoadSave.getFilteredCheck()`;
     `selectExperimentTab()` →
     `plugin.paneExperiment.tabsPane.setSelectedIndex(0)`.
   - multiSPOTS96: `getFilteredCheck()` →
     `plugin.dlgBrowse.loadSaveExperiment.getFilteredCheck()`;
     `selectExperimentTab()` →
     `plugin.dlgExperiment.tabsPane.setSelectedIndex(0)`.
4. Delete both plugin-local files.

**Acceptance:**

- `mvn clean compile` green.
- Both plugins: load ≥3 experiments with distinct field values;
  open Filter tab. Click "Select…" on the `stim1` field, pick a
  value — confirm (a) the `stim1` checkbox auto-ticks and (b) the
  `indexStatusLabel` reads "index: ready" or "index: loading…"
  appropriately.
- Apply filter; confirm the experiments combo is filtered to
  matching experiments.
- Clear filter; confirm the full list reappears.

**Rollback:** `git revert`.

---

## 10. Phase 7 — SelectFilesPanel

**Goal (§10):** shared `SelectFilesPanel` in multiTools with a
`Config` value object.

**Preconditions:** P0 (clean baseline), Phase 9 is not a hard
dependency — this phase does not interact with LoadSaveExperiment
internals.

**Files touched:**

- Create: `multiTools/src/main/java/plugins/fmp/multitools/experiment/ui/SelectFilesPanelConfig.java`
- Create: `multiTools/src/main/java/plugins/fmp/multitools/experiment/ui/SelectFilesPanel.java`
- Delete: `multiCAFE/.../dlg/browse/SelectFilesPanel.java`
- Delete: `multiSPOTS96/.../dlg/a_browse/SelectFilesPanel.java`
- Modify: callers in `LoadSaveExperiment` (both plugins — or the
  shared base after Phase 9) to construct
  `new SelectFilesPanel(SelectFilesPanelConfig.forCafe())` etc.

**Steps:**

1. Define `SelectFilesPanelConfig`:
   ```java
   public final class SelectFilesPanelConfig {
       public final String[] filterOptions;
       public final int defaultIndex;
       public final List<String> legacyExperimentFilenames;   // lowercased
       public final Function<String,String> legacyPatternRemap;

       public static SelectFilesPanelConfig forCafe() {
           return new SelectFilesPanelConfig(
               new String[] { "cam", "grabs", "experiment" }, // retired legacy entries, §10.5
               2,
               Collections.singletonList("mcexperiment.xml"),
               Function.identity()
           );
       }

       public static SelectFilesPanelConfig forSpots96() {
           return new SelectFilesPanelConfig(
               new String[] { "cam", "grabs", "experiment" },
               2,
               Arrays.asList("mcexperiment.xml", "ms96_experiment.xml"),
               Function.identity()
           );
       }
   }
   ```
   (multiCAFE's legacy filter entries retired per §10.5 resolution.
   If §10.5 migration-time verification reveals they are still in
   use externally, re-add them to `forCafe()`.)
2. Move `SelectFilesPanel` body into multiTools. Apply:
   - `getExperimentDirectory()` → `getResultsDirectory()`
     (both are aliases, pick the canonical one, §10.5).
   - `System.out.println(...)` → `Logger.warn(...)` (multiSPOTS96
     regression fix, §7.3.2).
   - Filter options and default index read from `config` rather
     than being hardcoded.
   - `isLegacyExperimentFile(Path)` checks against
     `config.legacyExperimentFilenames`.
3. Delete both plugin-local files.
4. **Migration-time verification (§10.5):** before this phase runs,
   search user docs / README / release notes for occurrences of
   the legacy filter strings (`capillarytrack`, `multicafe`,
   `roisline`, `MCcapillaries`). If any external workflow
   documents them, add them back to `forCafe()`.

**Acceptance:**

- `mvn clean compile` green.
- multiCAFE: open Browse tab, click "Select root directory and
  search…", confirm the filter combo lists the three current
  entries. Run a search, confirm files are listed.
- multiSPOTS96: same — additionally confirm the legacy
  `ms96_experiment.xml` files are recognised by
  `isLegacyExperimentFile`.

**Rollback:** `git revert`.

---

## 11. Phase 8 — Edit via `DescriptorTarget`

**Goal (§4 Option C):** shared `EditPanel` + `BulkDescriptorEditor`
engine in multiTools, plugin-specific `DescriptorTarget` impls.

**Preconditions:** Phase 4 (pilot), full P0.

This is the largest of the panel unifications; treat as a separate
mini-project. It can run in parallel with phases 5–7 once P0 and
Phase 4 are done.

**Files touched:**

- Create: `multiTools/src/main/java/plugins/fmp/multitools/experiment/descriptor/DescriptorLevel.java`
- Create: `multiTools/src/main/java/plugins/fmp/multitools/experiment/descriptor/DescriptorTarget.java`
- Create: `multiTools/src/main/java/plugins/fmp/multitools/experiment/descriptor/BulkDescriptorEditor.java`
- Create: `multiTools/src/main/java/plugins/fmp/multitools/experiment/ui/host/EditHost.java`
- Create: `multiTools/src/main/java/plugins/fmp/multitools/experiment/ui/EditPanel.java`
- Create: `multiCAFE/.../dlg/experiment/targets/CapillaryDescriptorTarget.java`
- Create: `multiSPOTS96/.../dlg/a_experiment/targets/SpotDescriptorTarget.java`
- Create: `multiSPOTS96/.../dlg/a_experiment/targets/CageDescriptorTarget.java`
- Create: `multiCAFE/.../dlg/hosts/MultiCafeEditHost.java`
- Create: `multiSPOTS96/.../dlg/hosts/MultiSpots96EditHost.java`
- Delete: `multiCAFE/.../dlg/experiment/EditCapillariesConditional.java`
- Delete: `multiSPOTS96/.../dlg/a_experiment/Edit.java`

**Steps:**

1. Define the descriptor abstractions (§4.4):
   ```java
   public enum DescriptorLevel { EXPERIMENT, CONTAINER }

   public interface DescriptorTarget {
       String getLevelLabel();                   // "Capillaries", "Spots", "Cages"
       List<String> getFields();                 // valid field names for this target
       int replaceFieldIfEqualOldValue(
           Experiment exp,
           String field, String oldValue, String newValue);
   }
   ```
2. Create the non-UI engine
   `BulkDescriptorEditor` in `multitools.experiment.descriptor`.
   It encapsulates:
   - Iterating over experiments from the combo
     (`host.getExperimentsCombo().getExperimentsAsListNoLoad()`).
   - For each experiment: wait for
     `exp.isSaving()` with 30s timeout
     (migrate `EditCapillariesConditional.waitForSaveToComplete`).
   - For each experiment: apply conditions (1 or 2) and call
     `target.replaceFieldIfEqualOldValue(...)` on the target.
   - Progress reporting via `SwingWorker` + `ProgressFrame`
     (migrate multiSPOTS96's async pattern).
   - Descriptor-index update (`descriptorIndex.removeValue` /
     `addValue`) after successful replacements (migrate
     multiSPOTS96's incremental index update).
3. Define `EditHost extends DialogHost`:
   ```java
   public interface EditHost extends DialogHost {
       List<DescriptorTarget> getContainerTargets();
       void onAfterBulkEdit(Experiment exp);
   }
   ```
4. Create `EditPanel` — the shared UI. Based on
   `EditCapillariesConditional`'s structure (2-condition AND + target
   + new value, §4.5), but:
   - Async execution via `BulkDescriptorEditor`, progress frame.
   - Target picker: `DescriptorLevel.EXPERIMENT` or one of
     `host.getContainerTargets()`.
   - Two-condition row always visible (§4.5 resolution); if user
     leaves condition 2 empty, the engine applies only condition 1.
5. Implement targets:
   - **multiCAFE:** `CapillaryDescriptorTarget` with
     `getLevelLabel()="Capillaries"`, using
     `Capillary.setField(String field, String value)` inside the
     `replaceFieldIfEqualOldValue` body. Copy the per-capillary
     iteration logic from
     `EditCapillariesConditional.replaceFieldWithConditions(...)`.
   - **multiSPOTS96:** two separate targets —
     `SpotDescriptorTarget` and `CageDescriptorTarget` — built
     from the existing
     `replaceSpotsFieldValueWithNewValueIfOld` and
     `replaceCageFieldValueWithNewValueIfOld` methods.
6. Implement hosts:
   - multiCAFE: `onAfterBulkEdit(exp)` — refresh paneExperiment
     tabs (`tabInfos.initCombos()`, `tabFilter.initCombos()`) and
     paneCapillaries (`updateDialogs(exp)`).
   - multiSPOTS96: `onAfterBulkEdit(exp)` — refresh
     `dlgExperiment.tabInfos.initCombos()`,
     `dlgExperiment.tabFilter.initCombos()`,
     `dlgMeasure.tabCharts.displayChartPanels(exp)`.
7. Delete both plugin-local Edit files.

**Acceptance:**

- `mvn clean compile` green.
- multiCAFE: load 2+ experiments with distinct capillary fields.
  In Edit, set `condition 1 = stim1 is sucrose`, `condition 2 =
  conc1 is 10`, target = `Capillaries`, field = `stim2`, new
  value = `NaCl`. Click Apply. Confirm a progress frame appears,
  the matching capillaries are updated, and Infos/Filter combos
  refresh afterwards.
- multiSPOTS96: same pattern but with spot targets. Confirm
  experiment-level edits work too (level = `EXPERIMENT`).
- Verify descriptor-index updates: after an edit,
  `Filter`'s `indexStatusLabel` should immediately reflect the
  new distinct values.

**Rollback:** `git revert`. Non-trivial — this is a big file; be
prepared to debug import paths.

---

## 12. Phase 9 — LoadSaveExperiment template-method split

**Goal (§11):** introduce `LoadSaveExperimentBase` in multiTools
with shared skeleton; each plugin's `LoadSaveExperiment` extends it
and overrides plugin-specific hooks.

**Preconditions:** P0 (especially P0.4 `getFilteredCheck`), and
ideally Phase 4 (pilot) complete so the host-interface pattern is
established.

This is the largest, highest-risk phase. Budget extra review and
testing time.

**Files touched:**

- Create: `multiTools/src/main/java/plugins/fmp/multitools/experiment/ui/host/LoadSaveHost.java`
- Create: `multiTools/src/main/java/plugins/fmp/multitools/experiment/ui/LoadSaveExperimentBase.java` (abstract)
- Modify: `multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/browse/LoadSaveExperiment.java` (now extends base; overrides plugin-specific hooks only)
- Modify: `multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/a_browse/LoadSaveExperiment.java` (same)
- Create: `multiCAFE/.../dlg/hosts/MultiCafeLoadSaveHost.java`
- Create: `multiSPOTS96/.../dlg/hosts/MultiSpots96LoadSaveHost.java`

**Steps:**

1. Define `LoadSaveHost` (§11.4):
   ```java
   public interface LoadSaveHost extends DialogHost {
       void onAfterMetadataScan();
       void onAfterExperimentOpen(Experiment exp);
       void onCloseAllExperiments();
   }
   ```
2. Create `LoadSaveExperimentBase` containing the shared skeleton:
   - UI construction (buttons, panels, filteredCheck).
   - Metadata-scan worker (the per-file metadata read path).
   - Descriptor-cache `SwingWorker` with progress reporting
     (port multiCAFE's version, including `progressFrame.setMessage/setPosition`).
   - `public boolean openSelectedExperiment(Experiment exp)` —
     orchestrates the load sequence, calling abstract/overridable
     hooks for plugin-specific steps. **Public** (§11.5).
   - `public void closeCurrentExperiment()`
   - `public void closeAllExperiments()` — **public** (§11.5).
   - `itemStateChanged`, `sequenceChanged`, `sequenceClosed`,
     `propertyChange` listener wiring.
   - `createButton` + `addExperimentFrom3NamesAnd2Lists` helper
     — **now shared** (§11.5, createButton restored to
     multiSPOTS96).
   - Abstract hooks:
     ```java
     protected abstract void doLoadExperimentSpecifics(Experiment exp, ProgressFrame pf);
     protected abstract void doSaveExperimentSpecifics(Experiment exp);
     protected void doMigrateLegacyIfApplicable(Experiment exp) { /* default no-op */ }
     protected String doResolveBinDirectory(Experiment exp) { return null; /* no bin-directory chooser by default */ }
     protected void doRefreshAfterLoad(Experiment exp) { /* default calls host.onAfterExperimentOpen(exp) */ }
     ```
   - Image loading: **adopt multiSPOTS96's v2 path**
     (`getImagesListFromPathV2` + `loadImageList`) per §11.5.
     Do **not** port multiCAFE's nFrames-mismatch patch.
   - **Migration-time verification (§11.5):** after running the
     migrated multiCAFE at least once with a real dataset,
     confirm no `nFrames=1` wrong-save occurs. If it does, fix
     inside `ExperimentDirectories.getImagesListFromPathV2` /
     `loadImageList` (library-level).
3. Subclass `multiCAFE.dlg.browse.LoadSaveExperiment extends
   LoadSaveExperimentBase`:
   - `doLoadExperimentSpecifics(exp, pf)` — calls
     `loadCapillariesData(exp, pf)`,
     `loadKymographsAndMeasures(exp, selectedBinDir, pf)`,
     cage load, fly-position transfer,
     `initTmsForFlyPositions`.
   - `doSaveExperimentSpecifics(exp)` — calls
     `save_capillaries_description_and_measures()`.
   - `doResolveBinDirectory(exp)` — calls `BinDirectoryResolver`
     (§11.5, multiCAFE-only behaviour).
4. Subclass `multiSPOTS96.dlg.a_browse.LoadSaveExperiment extends
   LoadSaveExperimentBase`:
   - `doLoadExperimentSpecifics(exp, pf)` — calls
     `load_spots_description_and_measures()` and cage load.
   - `doSaveExperimentSpecifics(exp)` — calls
     `save_spots_description_and_measures()`.
   - `doMigrateLegacyIfApplicable(exp)` — invokes
     `migrateLegacyExperimentInBackground(exp)` (uses
     `MigrationTool`).
   - `doResolveBinDirectory(exp)` — default no-op (§11.5).
5. Implement `MultiCafeLoadSaveHost` /
   `MultiSpots96LoadSaveHost`:
   - `onAfterMetadataScan()` — multiCAFE calls
     `plugin.paneExperiment.tabInfos.initCombos()` +
     `tabFilter.initCombos()`. multiSPOTS96 calls
     `plugin.dlgExperiment.tabInfos.initCombos()` +
     `tabFilter.initCombos()`.
   - `onAfterExperimentOpen(exp)` — plugin-specific sibling refresh
     (paneKymos, paneCapillaries, paneCages, paneLevels for
     multiCAFE; dlgSpots, dlgMeasure for multiSPOTS96).
   - `onCloseAllExperiments()` — clear plugin-specific
     tab/descriptor state.
6. **Reintroduce multiCAFE-side regressions as base-class fixes:**
   - Logger consistency: base class uses
     `plugins.fmp.multitools.tools.Logger` everywhere. The
     `System.out.println` calls currently in multiSPOTS96's
     version are **not** carried over; they're replaced at
     base-class level with `Logger.warn`/`Logger.debug` (§7.3.2).
   - `lastMetadataScanFailed` recovery flag → base class
     (§11 drift inventory).
   - Descriptor-cache progress reporting → base class.

**Acceptance:**

- `mvn clean compile` green.
- **multiCAFE regression battery:**
  - Load a fresh capillary experiment; verify capillaries, cages,
    kymographs all load correctly.
  - Load a bin_xxx experiment; verify
    `BinDirectoryResolver` prompts correctly (for single
    experiments) or uses cached selection (for series).
  - Close an experiment; verify save-capillaries-and-cages path
    runs.
  - Load an experiment with `nFrames=1` wrongly saved — **verify
    no corruption occurs** (§11.5 migration-time verification).
    If corruption does occur, the fix is library-level as noted
    in step 2.
- **multiSPOTS96 regression battery:**
  - Load a fresh spots experiment; verify spots load correctly.
  - Load a **legacy** multiSPOTS96 experiment (with
    `mcexperiment.xml` naming); verify the
    `migrateLegacyExperimentInBackground` path upgrades it and
    saves as `ms96_experiment.xml`.
  - Click the restored `createButton` ("Create from
    directories"); verify it creates a new experiment record.
  - Close all experiments; verify filter checkboxes, combos, and
    `descriptorIndex` clear correctly.
- Verify method visibility: external callers (like
  `Intervals.java`) still compile and work against
  `openSelectedExperiment(exp)` being public.

**Rollback:** `git revert`. Given the size, consider splitting
this phase across several commits:
- Commit 9a: add `LoadSaveExperimentBase` skeleton (methods
  still `throw new UnsupportedOperationException()`). No plugin
  changes yet.
- Commit 9b: migrate multiCAFE to extend the base.
- Commit 9c: migrate multiSPOTS96 to extend the base.
- Commit 9d: remove duplicated helpers from both plugins.

Each commit leaves the build green.

---

## 13. Cross-cutting concerns

### 13.1 Testing strategy

No unit-test suite exists today for these dialogs (per the survey's
inspection). For this plan, acceptance is driven by:

1. **Compile check:** `mvn clean compile` on the repo root after
   every commit.
2. **Icy-launch smoke test:** start Icy with both multiCAFE and
   multiSPOTS96 plugins installed; confirm both plugin menus
   appear and the main dialog opens.
3. **Per-phase smoke tests:** listed in each phase's "Acceptance"
   block. These exercise the affected dialog along the paths most
   likely to regress.
4. **Regression corpus:** keep a pair of "known-good" experiment
   datasets (one per plugin) that exercises the major code paths
   (load, filter, info-edit, Intervals-change, bulk-edit, save,
   close). Run the full acceptance battery of phases 4–9 on this
   corpus after each of those phases.

### 13.2 Rollback protocol

Three layers of rollback:

1. **File-level:** `git revert <commit>` — every phase is one or
   more commits; each commit leaves the build green. This is the
   primary recovery path.
2. **Phase-level:** if a whole phase proves broken after merge,
   revert the range of commits for that phase in one `git revert
   -n <range> && git commit`.
3. **Preferences-level (P0.6 only):** the multiSPOTS96
   `defaultNominalIntervalSec` migration is one-way at the
   preferences level — once the new node is written and the old
   key is removed, rolling back the code does not restore the old
   key. The failure mode is benign: the old code reads the missing
   key and uses its hardcoded default (60s). Users who had
   customised the value re-enter it once. Document this in the
   migration-plan commit message.

### 13.3 Coordination with ongoing work

- The git status at the start of this plan shows an already-modified
  `multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/experiment/Intervals.java`
  and a new `Intervals.java` under
  `multiCAFE/.../dlg/experiment/`. Confirm both are committed or
  stashed before Phase 4 begins.
- The `target/classes/**` entries in git status are IDE-generated
  build artefacts and should be added to `.gitignore` (separate
  hygiene task; not part of this plan).
- If you begin any new feature work inside the dialogs listed in
  this plan (e.g. adding a new checkbox to `Intervals`) during the
  migration, do it on `consolidate/shared-dialogs` rather than
  `main` — otherwise you'll have to port it to the shared
  implementation afterwards.

### 13.4 Pause points

Natural places to pause and re-evaluate before continuing:

- After **P0 complete**: zero functional changes should have
  happened yet; only naming/packaging and the one-time prefs
  migration. A good moment to regression-test both plugins
  end-to-end.
- After **Phase 4 (Intervals pilot)**: the host-interface pattern
  is now concrete. Review with the user whether to continue as
  planned or adjust (e.g. change interface shape, change
  `hosts/` package location).
- After **Phase 8 (Edit) or Phase 9 (LoadSaveExperiment)** —
  whichever is done first of the two "big" items: re-scope the
  remaining big item based on lessons learned.

### 13.5 Metrics to watch

- Total LoC in `multiCAFE/src/main` + `multiSPOTS96/src/main` should
  *decrease* phase by phase. Target reduction: ~40 % across the
  consolidated files (ballpark, based on survey file sizes).
- Total LoC in `multiTools/src/main` will *increase* by roughly
  half the reduction (the shared code lives once instead of twice).
- Net LoC across all three modules should drop by ~15–20 KB.

---

## 14. Post-migration follow-ups (out of scope here)

Flagged in the survey but explicitly not part of this plan:

- Deprecate `Experiment.getExperimentDirectory()` in favour of
  `getResultsDirectory()` (§10.2 follow-up).
- Evaluate whether `Canvas2DWithTransforms` /
  `Canvas2DWithTransformsPlugin` (multiCAFE-only) and the
  now-deleted `Canvas2D3TransformsCompat` (multiSPOTS96-only) have
  any residual value after the unified `Canvas2D_3Transforms` is in
  place.
- Decide whether `CafeViewOptionsDTO` should eventually become a
  pluggable bag (§12.6 Option B) if multiSPOTS96 develops a
  cluttered-viewer problem of its own.
- `.gitignore` hygiene for `target/` directories visible in git
  status at start of this plan.
- Consider a follow-up survey of any `_DlgXxx_` /  `MCXxx_` tabbed
  container classes to see whether they share structure that
  warrants consolidation into a shared tabbed-container in
  multiTools.

---

## 15. Summary

One paragraph, for stakeholders:

> Over nine phases, we move the shared UI code from two plugins
> (`multiCAFE`, `multiSPOTS96`) into the already-existing shared
> library module (`multiTools`), using a light host-interface
> pattern where plugin-specific behaviour remains. Phase 0
> (prerequisites) lays the groundwork — logger, resource utility,
> combo model, shared `ViewOptionsHolderBase` — without user-visible
> change. Phases 1–3 deliver the easy wins (Canvas2D, Excel Options,
> CorrectDrift) with almost no risk. Phase 4 is the pilot of the
> host-interface pattern (Intervals) and unblocks phases 5–6 (Infos,
> Filter). Phase 7 (SelectFilesPanel) is a parameterisation job.
> Phases 8 and 9 are the heaviest: Edit becomes a shared panel
> parameterised by a `DescriptorTarget` (so multiCAFE's capillary
> editor and multiSPOTS96's spot/cage editor share an engine);
> LoadSaveExperiment becomes a shared base class with plugin
> subclasses, carrying the multiCAFE bug fixes forward and the
> multiSPOTS96 legacy-migration hook. The end state is a ~15–20 KB
> net reduction in duplicated code, aligned logging, and a single
> place to add the next generation of dialogs.
