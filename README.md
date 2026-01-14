# Multi Plugins - Maven Multi-Module Project

This is the parent Maven project containing all ICY plugins that share common code.

## Structure

```
multiPlugins/
├── pom.xml                    # Parent POM (manages all modules)
├── multiTools/                # Shared library module
│   ├── pom.xml
│   └── src/main/java/plugins/fmp/multitools/
│       ├── fmp_experiment/    # Experiment, Cage, Spot, Capillary classes
│       ├── fmp_tools/         # Charts, Excel export, utilities
│       ├── fmp_series/        # Series processing
│       ├── fmp_service/       # Services (KymographBuilder, etc.)
│       └── fmp_resource/       # Resource utilities
├── multiCAFE/                 # MultiCAFE plugin module
│   ├── pom.xml
│   └── src/main/java/plugins/fmp/multicafe/
│       ├── dlg/               # UI dialogs (plugin-specific)
│       ├── viewer1D/          # Plugin-specific viewers
│       └── workinprogress_gpu/
├── multiSPOTS/                # TODO: Add multiSPOTS module
└── multiSPOTS96/              # TODO: Add multiSPOTS96 module
```

## Package Naming

- **Shared code (multiTools)**: `plugins.fmp.multitools.fmp_*`
- **Plugin-specific code**: `plugins.fmp.multicafe.*`, `plugins.fmp.multispots.*`, etc.

## Building

### Build all modules:
```bash
cd multiPlugins
mvn clean install
```

### Build specific module:
```bash
cd multiPlugins/multiCAFE
mvn clean install
```

Maven will automatically build dependencies (multiTools) first.

## Eclipse Setup

1. **Import the parent project:**
   - File → Import → Existing Maven Projects
   - Select `C:\Users\fred\git\multiPlugins\pom.xml`
   - Eclipse will automatically import all modules

2. **All modules will appear in Package Explorer:**
   - multiPlugins (parent)
   - multiTools
   - multiCAFE
   - (multiSPOTS and multiSPOTS96 when added)

3. **Benefits:**
   - Changes in multiTools are immediately visible in dependent projects
   - Single workspace for all plugins
   - Easy refactoring across modules
   - Single build command for all projects

## Adding multiSPOTS and multiSPOTS96

### Steps to add a new plugin module:

1. **Copy plugin structure:**
   ```powershell
   # From C:\Users\fred\git\multiSPOTS
   Copy-Item -Path "C:\Users\fred\git\multiSPOTS\src" -Destination "C:\Users\fred\git\multiPlugins\multiSPOTS\src" -Recurse
   ```

2. **Create pom.xml for the module:**
   - Copy `multiCAFE/pom.xml` as template
   - Change `<artifactId>multiCAFE</artifactId>` to `multiSPOTS`
   - Update description
   - Keep the multiTools dependency

3. **Update imports:**
   - Replace `import plugins.fmp.multispots.fmp_*` with `import plugins.fmp.multitools.fmp_*`
   - Remove old `fmp_*` directories from the plugin's src

4. **Add module to parent pom.xml:**
   ```xml
   <modules>
       <module>multiTools</module>
       <module>multiCAFE</module>
       <module>multiSPOTS</module>  <!-- Add this -->
   </modules>
   ```

5. **Refresh Maven project in Eclipse**

## Migration Notes

- All `fmp_*` packages have been moved to `multiTools`
- Package names changed from `plugins.fmp.multicafe.fmp_*` to `plugins.fmp.multitools.fmp_*`
- Imports in plugin code have been updated automatically
- Old `fmp_*` directories should be removed from plugin source (already done for multiCAFE)

## Deployment

Each module produces its own JAR file for ICY server distribution:
- `multiTools/target/multiTools-2.2.3.jar`
- `multiCAFE/target/multiCAFE-2.2.3.jar`

For end users, you can either:
1. **Bundle multiTools inside each plugin JAR** (fat JAR - recommended)
2. **Distribute multiTools separately** on ICY server

To create fat JARs, add Maven Shade plugin to each plugin's pom.xml.
