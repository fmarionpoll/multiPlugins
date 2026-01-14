# Eclipse Troubleshooting - Modules Not Showing

If Eclipse only shows `multiTools` under `multiPlugins` after adding modules, try these steps:

## Solution 1: Force Refresh Maven Projects

1. **Right-click on `multiPlugins` project** → **Maven** → **Update Project...**
2. Check **"Force Update of Snapshots/Releases"**
3. Check **"Refresh workspace automatically"**
4. Click **OK**

## Solution 2: Re-import the Parent Project

1. **Close Eclipse** (or just close the multiPlugins project)
2. **Delete** the `.project` and `.classpath` files in `multiPlugins/` directory (if they exist)
3. **Re-import**: File → Import → Maven → Existing Maven Projects
4. Select `C:\Users\fred\git\multiPlugins\pom.xml`
5. Make sure all projects are checked in the import dialog
6. Click **Finish**

## Solution 3: Import Modules Individually (if above doesn't work)

If Eclipse still doesn't detect them automatically:

1. **File** → **Import** → **Maven** → **Existing Maven Projects**
2. **Root Directory**: `C:\Users\fred\git\multiPlugins\multiSPOTS`
3. Click **Finish**
4. Repeat for `multiSPOTS96`

They will still be part of the multi-module structure and will build together.

## Solution 4: Check Maven Settings

1. **Window** → **Preferences** → **Maven**
2. Make sure:
   - ✅ "Download repository index updates on startup" is checked
   - ✅ "Update Maven projects on startup" is checked
3. Click **Apply and Close**
4. **Right-click** `multiPlugins` → **Maven** → **Update Project...**

## Solution 5: Verify POM Files Are Valid

Check for errors in:
- `multiPlugins/pom.xml` (parent)
- `multiPlugins/multiSPOTS/pom.xml`
- `multiPlugins/multiSPOTS96/pom.xml`

Common issues:
- Missing `<packaging>jar</packaging>` in module POMs
- Incorrect `<relativePath>` in parent reference
- Version mismatches between parent and modules

## Solution 6: Clean and Rebuild

1. **Right-click** `multiPlugins` → **Maven** → **Clean**
2. **Right-click** `multiPlugins` → **Maven** → **Update Project...**
3. **Right-click** `multiPlugins` → **Refresh**

## Expected Result

After successful import, you should see in Package Explorer:

```
multiPlugins
├── multiTools
├── multiCAFE
├── multiSPOTS
└── multiSPOTS96
```

All should be at the same level, nested under the parent `multiPlugins` project.
