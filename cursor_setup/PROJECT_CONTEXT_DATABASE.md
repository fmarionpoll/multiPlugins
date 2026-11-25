# Project Context Database

## Project Overview

**Project Name:** multiPlugins  
**Location:** `C:\Users\fred\git\multiPlugins`  
**Type:** Maven Multi-Module Project  
**Purpose:** Parent project containing ICY plugins that share common code

Maven artifact and directory names are `multiPlugins`, `multiTools`, `multiCAFE`, and `multiSPOTS`. The older `m_multiPlugins`, `m_multiTools`, `m_multiCAFE`, and `m_multiSPOTS` names are retired. Java packages were not renamed with the directories: shared code remains `plugins.fmp.multitools`, MultiCAFE remains `plugins.fmp.multicafe`, and MultiSPOTS remains `plugins.fmp.multiSPOTS`.

## Project Structure

### Parent Project
- **Group ID:** `plugins.fmp`
- **Artifact ID:** `multiPlugins`
- **Version:** `2.4.5`
- **Packaging:** `pom` (parent POM)

### Modules

1. **multiTools** - Shared library module
   - Contains all common code shared across plugins
   - Package: `plugins.fmp.multitools.*`
   - Sub-packages:
     - `experiment` - Experiment, Cage, Spot, Capillary classes
     - `series` - Series processing
     - `service` - Services such as kymograph building and spot measurement
     - `tools` - Charts, Excel export, utilities, image transforms, ROI2D, etc.
     - `workinprogress_gpu` - GPU-related code (OpenCL)

2. **multiCAFE** - MultiCAFE plugin module
   - Package: `plugins.fmp.multicafe.*`
   - Plugin-specific code: dialogs (`dlg/`), including capillary, kymograph, level, cage, and export panels
   - Depends on: multiTools

3. **multiSPOTS** - MultiSPOTS plugin module
   - Package: `plugins.fmp.multiSPOTS.*`
   - Plugin-specific code: dialogs (`dlg/`) for spot definition, image-filter measures, color measures, line measures, fly position, and export
   - Depends on: multiTools

multiSPOTS96 is retired and is not a module of this workspace. Do not treat it as current code.

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
  - multiSPOTS: `plugins.fmp.multiSPOTS.*`

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
- `multiTools/target/multiTools-2.4.5.jar`
- `multiCAFE/target/multiCAFE-2.4.5.jar`
- `multiSPOTS/target/multiSPOTS-2.4.5.jar`

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

### Code Organization

- **Source:** `src/main/java/`
- **Resources:** `src/main/resources/`
- **Tests:** `src/test/java/` in the modules that include them

## Migration History

### Code Consolidation

Previously, each plugin had duplicate `fmp_*` packages. These have been consolidated:

- **Before:** `plugins.fmp.multicafe.fmp_experiment`, `plugins.fmp.multispots.fmp_experiment`, etc.
- **After:** `plugins.fmp.multitools.experiment` (single shared implementation)

### Package and module migration

- All `fmp_*` packages moved to multiTools
- Package names changed from `plugins.fmp.<plugin>.fmp_*` to `plugins.fmp.multitools.*`
- Maven directories were later renamed from `m_multiTools`, `m_multiCAFE`, and `m_multiSPOTS` to `multiTools`, `multiCAFE`, and `multiSPOTS`
- The parent directory is `multiPlugins`, artifact `multiPlugins`
- `C:\Users\fred\git\m_multiPlugins`, `C:\Users\fred\git\MultiCAFE`, and `C:\Users\fred\git\multiSPOTS` are not present. This workspace is the working tree.

## Project Status

### Completed
- Multi-module Maven structure created
- multiTools shared library extracted and consolidated
- multiCAFE and multiSPOTS are modules of this parent
- Package refactoring completed
- Module directories renamed from the `m_multi*` names

## Important Directories

### Documentation
- `cursor/` - Setup and troubleshooting notes:
  - `ADD_NEW_MODULES.md`
  - `CONVERT_TO_MAVEN.md`
  - `DISK_STRUCTURE_EXPLANATION.md`
  - `ECLIPSE_MODULES_NOT_SHOWING.md`
  - `ECLIPSE_TROUBLESHOOTING.md`
  - `MULTI_MODULE_MAVEN_SETUP.md`
- `cursor_setup/` - Current working notes for this workspace, including this file

### Source Code Organization

**multiTools** (Shared Library):
- `experiment/` - Core data models (Experiment, Cage, Spot, Capillary)
- `series/` - Time series processing
- `service/` - Services (kymograph building, spot and capillary detection)
- `tools/` - Utilities:
  - `chart/` - Charting components
  - `imageTransform/` - Image transformation utilities
  - `JComponents/` - Custom Swing components
  - `toExcel/` - Excel export functionality
  - `ROI2D/` - ROI (Region of Interest) utilities
  - `results/` - Results handling
  - `overlay/`, `polyline/`, `NHDistance/`, `registration/`

**Plugin-Specific Code:**
- Each plugin has its own `dlg/` directory for UI
- Shared experiment, series, and measurement code lives in multiTools
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
- Test suites live under `src/test/java/` in the modules that include them

## License & Organization

- **License:** GNU GPLv3
- **Organization:** IDEEV UMR EGCE (CNRS-IRD-Paris-Saclay)
- **Developer:** Frederic Marion-Poll

## Notes

- Avoid redundant comments (user preference)
- `C:\Users\fred\git\multiPlugins` is the active development location
