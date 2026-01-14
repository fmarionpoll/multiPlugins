# Adding multiSPOTS and multiSPOTS96 Modules

## Steps to Add New Plugin Modules

### 1. Update Parent POM

Edit `multiPlugins/pom.xml` and uncomment/add the modules:

```xml
<modules>
    <module>multiTools</module>
    <module>multiCAFE</module>
    <module>multiSPOTS</module>
    <module>multiSPOTS96</module>
</modules>
```

### 2. Copy Plugin Source Code

From PowerShell (run from `C:\Users\fred\git\`):

```powershell
# Copy multiSPOTS
Copy-Item -Path "multiSPOTS\src" -Destination "multiPlugins\multiSPOTS\src" -Recurse

# Copy multiSPOTS96
Copy-Item -Path "multiSPOTS96\src" -Destination "multiPlugins\multiSPOTS96\src" -Recurse
```

### 3. Create POM Files

Copy `multiPlugins/multiCAFE/pom.xml` as a template:

**For multiSPOTS:**
- Copy to `multiPlugins/multiSPOTS/pom.xml`
- Change `<artifactId>multiCAFE</artifactId>` to `<artifactId>multiSPOTS</artifactId>`
- Update description

**For multiSPOTS96:**
- Copy to `multiPlugins/multiSPOTS96/pom.xml`
- Change `<artifactId>multiCAFE</artifactId>` to `<artifactId>multiSPOTS96</artifactId>`
- Update description

### 4. Update Imports in Source Code

For each plugin, replace imports:

**In multiSPOTS:**
- Replace: `import plugins.fmp.multispots.fmp_*`
- With: `import plugins.fmp.multitools.fmp_*`

**In multiSPOTS96:**
- Replace: `import plugins.fmp.multispots96.fmp_*`
- With: `import plugins.fmp.multitools.fmp_*`

You can use PowerShell to do this automatically:

```powershell
# For multiSPOTS
Get-ChildItem -Path "multiPlugins\multiSPOTS\src" -Recurse -Filter "*.java" | ForEach-Object {
    (Get-Content $_.FullName -Raw) -replace 'import plugins\.fmp\.multispots\.fmp_', 'import plugins.fmp.multitools.fmp_' | Set-Content $_.FullName -NoNewline
}

# For multiSPOTS96
Get-ChildItem -Path "multiPlugins\multiSPOTS96\src" -Recurse -Filter "*.java" | ForEach-Object {
    (Get-Content $_.FullName -Raw) -replace 'import plugins\.fmp\.multispots96\.fmp_', 'import plugins.fmp.multitools.fmp_' | Set-Content $_.FullName -NoNewline
}
```

### 5. Remove Old fmp_* Directories

Remove the old shared code directories from each plugin:

```powershell
# For multiSPOTS
Remove-Item -Path "multiPlugins\multiSPOTS\src\main\java\plugins\fmp\multispots\fmp_*" -Recurse -Force

# For multiSPOTS96
Remove-Item -Path "multiPlugins\multiSPOTS96\src\main\java\plugins\fmp\multispots96\fmp_*" -Recurse -Force
```

### 6. Refresh in Eclipse

Right-click on the **multiPlugins** project in Eclipse → **Maven** → **Update Project...**

Or simply:
- Right-click on **multiPlugins** → **Refresh**
- Eclipse will automatically detect the new modules

The new modules (multiSPOTS and multiSPOTS96) will appear in your Package Explorer automatically!

## Verification

After adding modules, verify:
1. All modules appear in Package Explorer under multiPlugins
2. No import errors (red X marks)
3. Build works: Right-click multiPlugins → Run As → Maven build → `clean install`
