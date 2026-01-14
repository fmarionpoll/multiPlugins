# Eclipse Maven Update Dialog - Modules Not Showing

## Problem

When updating Maven project for `multiPlugins`, only `multiTools` appears in the update dialog, even though parent `pom.xml` lists 4 modules.

## Root Causes

1. **Version Mismatch**: Module POMs had version `2.3.0` while parent has `2.2.3`
2. **Modules Not Recognized as Maven Projects**: Eclipse may not have recognized the nested modules

## Solutions

### Solution 1: Fix Version Mismatch ✅

All module POMs should use the same version as parent:
- Parent: `2.2.3`
- All modules: `2.2.3` (not `2.3.0`)

**Fixed in:**
- `multiCAFE/pom.xml`
- `multiSPOTS/pom.xml`
- `multiSPOTS96/pom.xml`

### Solution 2: Convert Modules to Maven Projects

If modules still don't appear:

1. **Right-click each module** (`multiCAFE`, `multiSPOTS`, `multiSPOTS96`)
2. Select **Configure** → **Convert to Maven Project**
3. Eclipse will detect the `pom.xml` and configure it

### Solution 3: Re-import Modules

If Solution 2 doesn't work:

1. **Close** the module projects (right-click → Close Project)
2. **File** → **Import** → **Maven** → **Existing Maven Projects**
3. **Root Directory**: `C:\Users\fred\git\multiPlugins\multiCAFE`
4. Click **Finish**
5. Repeat for `multiSPOTS` and `multiSPOTS96`

### Solution 4: Refresh and Update

After fixing versions:

1. **Right-click** `multiPlugins` → **Refresh**
2. **Right-click** `multiPlugins` → **Maven** → **Update Project...**
3. Check **"Force Update of Snapshots/Releases"**
4. **Select All** projects (should now show all 4 modules)
5. Click **OK**

### Solution 5: Check for POM Errors

Verify each module's `pom.xml` is valid:

1. Open each `pom.xml` file
2. Check for red error markers
3. Common issues:
   - Invalid XML syntax
   - Missing required elements
   - Incorrect parent reference

## Expected Behavior

After fixes, when you:
- **Right-click** `multiPlugins` → **Maven** → **Update Project...**

You should see in the dialog:
- ✅ multiPlugins (parent)
- ✅ multiTools
- ✅ multiCAFE
- ✅ multiSPOTS
- ✅ multiSPOTS96

All should be checked and ready to update.

## Verification

Check Package Explorer - you should see:
```
multiPlugins [M]
├── multiTools [M]
├── multiCAFE [M]
├── multiSPOTS [M]
└── multiSPOTS96 [M]
```

The `[M]` indicates Maven project nature.

## If Still Not Working

1. **Close Eclipse**
2. **Delete** `.project` and `.classpath` files in each module directory (if they exist)
3. **Re-open Eclipse**
4. **Re-import** parent project: `File` → `Import` → `Maven` → `Existing Maven Projects`
5. Select `C:\Users\fred\git\multiPlugins\pom.xml`
6. Eclipse should now detect all modules automatically
