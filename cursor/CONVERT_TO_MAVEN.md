# Converting multiPlugins to Maven Project in Eclipse

## Quick Solution

1. **Right-click on `multiPlugins` project** in Package Explorer
2. Select **Configure** → **Convert to Maven Project**
3. Eclipse will detect the `pom.xml` and configure it as a Maven project

## Alternative Method (if above doesn't work)

1. **Right-click on `multiPlugins`** → **Delete** (but DON'T check "Delete project contents on disk")
2. **File** → **Import** → **Maven** → **Existing Maven Projects**
3. **Root Directory**: `C:\Users\fred\git\multiPlugins`
4. Make sure **`pom.xml`** is checked in the import dialog
5. Click **Finish**

## Verify

After conversion, you should see:
- `multiPlugins` has a **M** icon (Maven project)
- Right-click → **Maven** menu should be available
- You can run **Maven** → **Update Project...**
