# Project Context Database

## Project Overview

**Project Name:** multiPlugins  
**Location:** `C:\Users\fred\git\multiPlugins`  
**Type:** Maven Multi-Module Project  
**Purpose:** Parent project containing ICY plugins that share common code

## Project Structure

### Parent Project
- **Group ID:** `plugins.fmp`
- **Artifact ID:** `multiPlugins`
- **Version:** `2.2.0`
- **Packaging:** `pom` (parent POM)

### Modules

1. **multiTools** - Shared library module
   - Contains all common code shared across plugins
   - Package: `plugins.fmp.multitools.*`
   - Sub-packages:
     - `experiment` - Experiment, Cage, Spot, Capillary classes (87 files)
     - `series` - Series processing (41 files)
     - `service` - Services like KymographBuilder (6 files)
     - `tools` - Charts, Excel export, utilities, image transforms, ROI2D, etc.
     - `workinprogress_gpu` - GPU-related code (OpenCL)

2. **multiCAFE** - MultiCAFE plugin module
   - Package: `plugins.fmp.multicafe.*`
   - Plugin-specific code: dialogs (dlg/), viewers (viewer1D/), GPU work
   - Depends on: multiTools

3. **multiSPOTS** - MultiSPOTS plugin module
   - Package: `plugins.fmp.multispots.*`
   - Plugin-specific code: dialogs (dlg/), experiment (experiment/), series (series/), tools
   - Depends on: multiTools

4. **multiSPOTS96** - MultiSPOTS96 plugin module
   - Package: `plugins.fmp.multispots96.*`
   - Most comprehensive plugin with extensive codebase
   - Plugin-specific code: dialogs, experiment (55 files), series (31 files), tools
   - Includes test suite
   - Depends on: multiTools

## Key Technologies & Dependencies

### ICY Framework
- **Parent POM:** `org.bioimageanalysis.icy:pom-icy:2.1.0`
- **Core:** `icy-kernel:2.5.1`
- **Math Libraries:** `flanagan:1.1.1`, `parallel-colt:5.5.2`, `vecmath:1.6.1`
- **Graphics:** `jfree-common:1.0.24`, `jfreechart:1.5.3`
- **GPU:** `javacl:1.0.6` (Java OpenCL wrapper)
- **Utilities:** `nherve-toolbox:1.3.2`, `mask-editor:1.2.1`

### Third-Party Libraries
- **Excel Export:** Apache POI (`poi-ooxml:4.1.1`)
- **CSV:** Apache Commons CSV (`commons-csv:1.9.0`)
- **Image Processing:** OpenCV (`opencv:4.5.1-2`)
- **Logging:** SLF4J (`slf4j-api:1.7.36`, `slf4j-simple:1.7.36`)

### Repository
- **Icy Nexus:** `https://icy-nexus.pasteur.fr/repository/Icy/`

## Package Naming Conventions

- **Shared code (multiTools):** `plugins.fmp.multitools.*`
- **Plugin-specific code:**
  - multiCAFE: `plugins.fmp.multicafe.*`
  - multiSPOTS: `plugins.fmp.multispots.*`
  - multiSPOTS96: `plugins.fmp.multispots96.*`

## Build System

### Maven Commands

**Build all modules:**
```bash
cd C:\Users\fred\git\multiPlugins
mvn clean install
```

**Build specific module:**
```bash
cd C:\Users\fred\git\multiPlugins\multiCAFE
mvn clean install
```

Maven automatically builds dependencies (multiTools) first.

### Output Artifacts

Each module produces its own JAR:
- `multiTools/target/multiTools-2.2.3.jar`
- `multiCAFE/target/multiCAFE-2.2.3.jar`
- `multiSPOTS/target/multiSPOTS-<version>.jar`
- `multiSPOTS96/target/multiSPOTS96-<version>.jar`

## Development Environment

### IDE Setup (Eclipse)

1. **Import parent project:**
   - File → Import → Existing Maven Projects
   - Select `C:\Users\fred\git\multiPlugins\pom.xml`
   - Eclipse automatically imports all modules

2. **Modules appear in Package Explorer:**
   - multiPlugins (parent)
   - multiTools
   - multiCAFE
   - multiSPOTS
   - multiSPOTS96

### Code Organization

- **Source:** `src/main/java/`
- **Resources:** `src/main/resources/`
- **Tests:** `src/test/java/` (present in multiSPOTS96)

## Migration History

### Code Consolidation

Previously, each plugin had duplicate `fmp_*` packages. These have been consolidated:

- **Before:** `plugins.fmp.multicafe.fmp_experiment`, `plugins.fmp.multispots.fmp_experiment`, etc.
- **After:** `plugins.fmp.multitools.experiment` (single shared implementation)

### Package Migration

- All `fmp_*` packages moved to `multiTools`
- Package names changed from `plugins.fmp.<plugin>.fmp_*` to `plugins.fmp.multitools.*`
- Imports in plugin code updated automatically
- Old `fmp_*` directories removed from plugin sources

## Project Status

### Completed
- ✅ Multi-module Maven structure created
- ✅ multiTools shared library extracted and consolidated
- ✅ multiCAFE migrated to new structure
- ✅ multiSPOTS and multiSPOTS96 added to parent POM
- ✅ Package refactoring completed

### Known Structure
- Original projects still exist at their original locations (`C:\Users\fred\git\MultiCAFE\`, etc.)
- `multiPlugins/` contains **copies** of the code (not links)
- Changes in `multiPlugins/` do not affect original projects
- Two separate codebases exist during transition period

## Important Directories

### Documentation
- `cursor/` - Contains setup and troubleshooting documentation:
  - `ADD_NEW_MODULES.md`
  - `CONVERT_TO_MAVEN.md`
  - `DISK_STRUCTURE_EXPLANATION.md`
  - `ECLIPSE_MODULES_NOT_SHOWING.md`
  - `ECLIPSE_TROUBLESHOOTING.md`
  - `MULTI_MODULE_MAVEN_SETUP.md`

### Source Code Organization

**multiTools** (Shared Library):
- `experiment/` - Core data models (Experiment, Cage, Spot, Capillary)
- `series/` - Time series processing
- `service/` - Services (KymographBuilder, etc.)
- `tools/` - Utilities:
  - `chart/` - Charting components
  - `imageTransform/` - Image transformation utilities
  - `JComponents/` - Custom Swing components
  - `toExcel/` - Excel export functionality
  - `ROI2D/` - ROI (Region of Interest) utilities
  - `results/` - Results handling
  - `overlay/`, `polyline/`, `NHDistance/`, `registration/`

**Plugin-Specific Code:**
- Each plugin has its own `dlg/` (dialogs) directory for UI
- Plugin-specific viewers, experiment handling, and tools
- No `fmp_*` directories (moved to multiTools)

## Development Guidelines

### Adding New Modules

1. Copy plugin structure to `multiPlugins/<moduleName>/`
2. Create `pom.xml` based on `multiCAFE/pom.xml` template
3. Update imports: replace `import plugins.fmp.<plugin>.fmp_*` with `import plugins.fmp.multitools.*`
4. Remove old `fmp_*` directories from plugin source
5. Add module to parent `pom.xml` `<modules>` section
6. Refresh Maven project in Eclipse

### Code Organization Rules

- **Shared code** → `multiTools`
- **Plugin-specific code** → respective plugin module
- **Common utilities** → `multiTools/tools/`
- **Plugin UI** → `pluginName/dlg/`

### Deployment

For end users:
1. **Fat JAR approach (recommended):** Bundle multiTools inside each plugin JAR
   - Add Maven Shade plugin to each plugin's `pom.xml`
2. **Separate distribution:** Distribute multiTools separately on ICY server

## Common Tasks

### Building
- All modules: `mvn clean install` from root
- Specific module: `cd <module>` then `mvn clean install`

### Refactoring
- Changes in multiTools are immediately visible in dependent projects
- Single workspace for all plugins enables easy cross-module refactoring

### Testing
- Test suites present in `multiSPOTS96/src/test/java/`
- Can be extended to other modules as needed

## License & Organization

- **License:** GNU GPLv3
- **Organization:** IDEEV UMR EGCE (CNRS-IRD-Paris-Saclay)
- **Developer:** Frederic Marion-Poll

## Notes

- Avoid redundant comments (user preference)
- Original projects at `C:\Users\fred\git\MultiCAFE\`, `C:\Users\fred\git\multiSPOTS\`, `C:\Users\fred\git\multiSPOTS96\` are separate and unchanged
- `multiPlugins/` is the active development location
- Code in `multiPlugins/` is a copy, not linked to originals
