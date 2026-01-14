# Disk Structure Explanation

## Current Structure on Disk

### Original Projects (Still Exist)

The **original projects are still on disk** at their original locations:

```
C:\Users\fred\git\
├── MultiCAFE\          # Original project (still exists)
│   ├── pom.xml
│   ├── src\
│   └── ...
├── multiSPOTS\         # Original project (still exists)
│   ├── pom.xml
│   ├── src\
│   └── ...
└── multiSPOTS96\       # Original project (still exists)
    ├── pom.xml
    ├── src\
    └── ...
```

### New Multi-Module Project (Copy, Not Link)

The **multiPlugins directory contains COPIES** of the code, not links:

```
C:\Users\fred\git\multiPlugins\
├── pom.xml             # Parent POM
├── multiTools\         # NEW - extracted shared code
│   ├── pom.xml
│   └── src\main\java\plugins\fmp\multitools\
│       ├── fmp_experiment\  # COPIED from original projects
│       ├── fmp_tools\
│       ├── fmp_series\
│       ├── fmp_service\
│       └── fmp_resource\
├── multiCAFE\          # COPY of original MultiCAFE
│   ├── pom.xml
│   └── src\main\java\plugins\fmp\multicafe\
│       ├── dlg\        # Plugin-specific code
│       └── ...         # fmp_* directories REMOVED (now in multiTools)
├── multiSPOTS\         # COPY of original multiSPOTS
│   ├── pom.xml
│   └── src\main\java\plugins\fmp\multispots\
│       └── ...         # fmp_* directories REMOVED (now in multiTools)
└── multiSPOTS96\       # COPY of original multiSPOTS96
    ├── pom.xml
    └── src\main\java\plugins\fmp\multispots96\
        └── ...         # fmp_* directories REMOVED (now in multiTools)
```

## Important Points

### 1. **Copies, Not Links**
- The code in `multiPlugins/` is a **COPY**, not a symbolic link or junction
- Changes in `multiPlugins/` do **NOT** affect the original projects
- Changes in original projects do **NOT** affect `multiPlugins/`

### 2. **Two Separate Codebases**
You now have:
- **Original projects**: `C:\Users\fred\git\MultiCAFE\`, etc. (unchanged)
- **New multi-module project**: `C:\Users\fred\git\multiPlugins\` (new structure)

### 3. **What Was Copied**

**For multiCAFE:**
- ✅ Copied: `src/` directory (plugin-specific code: dlg/, viewer1D/, etc.)
- ✅ Copied: `src/main/resources/` (icons, etc.)
- ❌ Removed: `fmp_*` directories (moved to multiTools)
- ✅ Created: New `pom.xml` that depends on multiTools

**For multiSPOTS and multiSPOTS96:**
- ✅ Copied: `src/` directory (plugin-specific code)
- ❌ Removed: `fmp_*` directories (moved to multiTools)
- ✅ Created: New `pom.xml` that depends on multiTools

**For multiTools:**
- ✅ Created: New module
- ✅ Copied: All `fmp_*` packages from original projects
- ✅ Updated: Package names from `plugins.fmp.multicafe.fmp_*` to `plugins.fmp.multitools.fmp_*`

## Workflow Options

### Option 1: Work Only in multiPlugins (Recommended)

**Use the new structure going forward:**
- Develop in `C:\Users\fred\git\multiPlugins\`
- Original projects become backups/reference
- Eventually delete or archive original projects

**Pros:**
- Single source of truth
- Multi-module structure works correctly
- Shared code properly managed

**Cons:**
- Need to migrate any ongoing work from original projects

### Option 2: Keep Both (During Transition)

**Work in both locations temporarily:**
- Keep original projects for reference
- Develop new features in `multiPlugins/`
- Manually sync important changes if needed

**Pros:**
- Safe transition period
- Can compare structures

**Cons:**
- Risk of divergence
- Confusion about which is "current"
- Manual sync required

### Option 3: Delete Originals (After Verification)

**After verifying multiPlugins works:**
- Delete original `MultiCAFE/`, `multiSPOTS/`, `multiSPOTS96/` directories
- Keep only `multiPlugins/` structure

**Pros:**
- Clean structure
- No confusion
- Single codebase

**Cons:**
- Lose original structure (but you have it in git history)

## What About Git?

### If Original Projects Are Git Repositories

The original projects (`MultiCAFE/`, `multiSPOTS/`, `multiSPOTS96/`) are likely separate Git repositories.

**Options:**

1. **Keep Separate Repos** (Current State)
   - Original repos remain unchanged
   - Create new repo for `multiPlugins/`
   - Multi-module project is separate

2. **Migrate to Single Repo**
   - Create new Git repo in `multiPlugins/`
   - Commit the new structure
   - Original repos become historical

3. **Git Submodules** (Advanced)
   - Keep original repos
   - Use Git submodules in `multiPlugins/`
   - More complex but preserves history

## Recommendation

**For now:**
- Work in `C:\Users\fred\git\multiPlugins\` for new development
- Keep original projects as reference/backup
- Once confident the new structure works, consider:
  - Creating a Git repo for `multiPlugins/`
  - Archiving or deleting original project directories

## Summary

**Answer to your question:**

> Is the code copied or linked?

**COPIED** - The code in `multiPlugins/` is a physical copy of the original projects. They are **separate directories** with **separate code**. Changes in one do not affect the other.

The structure is:
- `C:\Users\fred\git\MultiCAFE\` → Original (unchanged)
- `C:\Users\fred\git\multiPlugins\multiCAFE\` → Copy (new structure, depends on multiTools)

You can verify this by:
1. Making a change in `multiPlugins/multiCAFE/`
2. Checking `MultiCAFE/` - it won't have the change
3. They are independent copies
