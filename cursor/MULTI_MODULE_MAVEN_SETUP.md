# Multi-Module Maven Project Setup - Analysis and Documentation

## Overview

This document describes the multi-module Maven project structure created for managing shared code across multiple ICY plugins: multiCAFE, multiSPOTS, and multiSPOTS96.

## Project Structure

```
C:\Users\fred\git\multiPlugins\
├── pom.xml                    # Parent POM (packaging=pom)
├── cursor/                    # Documentation and analysis
│   └── MULTI_MODULE_MAVEN_SETUP.md
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
├── multiSPOTS/                # MultiSPOTS plugin module
│   ├── pom.xml
│   └── src/main/java/plugins/fmp/multispots/
├── multiSPOTS96/              # MultiSPOTS96 plugin module
│   ├── pom.xml
│   └── src/main/java/plugins/fmp/multispots96/
└── README.md                  # Project documentation
```

## Architecture Decision

### Why Multi-Module Maven Project?

**Chosen Approach:** Maven Multi-Module Project in Single Eclipse Workspace

**Rationale:**
1. **Development Ease**: Primary concern was making development easier
2. **Immediate Feedback**: Changes in multiTools instantly visible in dependent projects
3. **Single Build Command**: `mvn clean install` builds everything in correct order
4. **Easier Refactoring**: Eclipse can track dependencies across modules
5. **Version Synchronization**: Parent POM manages versions centrally
6. **Better for Frequent Updates**: No need to publish intermediate versions
7. **Simpler Debugging**: Can step through code across modules

### Alternative Considered: External Shared Plugin

**Why Not Chosen:**
- More complex build setup (Maven dependencies between plugins)
- ICY plugin dependency management can be tricky
- Need to publish multiTools to ICY server first
- Breaking changes require coordinated updates
- Development workflow: must build/install multiTools before testing other plugins
- Eclipse workspace: need to import multiple projects separately

## Package Naming Strategy

**Option A (Chosen):** Keep `fmp_*` naming in multiTools
- Package: `plugins.fmp.multitools.fmp_experiment.*`
- Imports: `import plugins.fmp.multitools.fmp_experiment.Experiment;`
- **Rationale**: Maintains existing structure and minimizes refactoring

**Option B (Not Chosen):** Flatten to `multitools.*`
- Package: `plugins.fmp.multitools.experiment.*`
- **Why Not**: Would require more extensive refactoring

## Migration Process

### Phase 1: Create Multi-Module Structure ✅
- Created parent `multiPlugins/` directory
- Created parent `pom.xml` with `<packaging>pom</packaging>`
- Created `multiTools/` module
- Copied all `fmp_*` packages to `multiTools/src/main/java/plugins/fmp/multitools/`

### Phase 2: Update Package Declarations ✅
- Updated all package declarations from `plugins.fmp.multicafe.fmp_*` to `plugins.fmp.multitools.fmp_*`
- Updated all imports in multiTools module

### Phase 3: Create Plugin Modules ✅
- Created `multiCAFE/` module
- Copied plugin-specific code (dlg/, viewer1D/, etc.)
- Removed old `fmp_*` directories from plugin
- Updated `pom.xml` to depend on multiTools
- Updated all imports to use `plugins.fmp.multitools.fmp_*`

### Phase 4: Add Additional Plugins ✅
- Created `multiSPOTS/` and `multiSPOTS96/` modules
- Added modules to parent `pom.xml`
- Created module `pom.xml` files

## Maven Configuration

### Parent POM (`multiPlugins/pom.xml`)

```xml
<groupId>plugins.fmp</groupId>
<artifactId>multiPlugins</artifactId>
<version>2.2.3</version>
<packaging>pom</packaging>

<modules>
    <module>multiTools</module>
    <module>multiCAFE</module>
    <module>multiSPOTS</module>
    <module>multiSPOTS96</module>
</modules>
```

**Key Features:**
- Inherits from ICY parent POM (`pom-icy`)
- Manages all dependency versions in `<dependencyManagement>`
- Defines all modules
- Centralizes version management

### Module POM Structure

Each plugin module:
- References parent via `<parent>` with `<relativePath>../pom.xml</relativePath>`
- Declares dependency on `multiTools`:
  ```xml
  <dependency>
      <groupId>plugins.fmp</groupId>
      <artifactId>multiTools</artifactId>
      <version>${project.version}</version>
  </dependency>
  ```
- Inherits dependency versions from parent
- Only declares plugin-specific dependencies

## Eclipse Setup

### Initial Import

1. **File** → **Import** → **Maven** → **Existing Maven Projects**
2. **Root Directory**: `C:\Users\fred\git\multiPlugins`
3. Eclipse automatically detects:
   - `multiPlugins` (parent)
   - `multiTools` (module)
   - `multiCAFE` (module)
   - `multiSPOTS` (module)
   - `multiSPOTS96` (module)

### If Parent Not Recognized as Maven Project

1. **Right-click** `multiPlugins` → **Configure** → **Convert to Maven Project**
2. Or re-import: **File** → **Import** → **Maven** → **Existing Maven Projects**

### Benefits in Eclipse

- **Single Workspace**: All projects visible together
- **Immediate Feedback**: Changes in multiTools instantly visible
- **Cross-Module Refactoring**: Eclipse tracks dependencies
- **Unified Build**: Right-click parent → **Run As** → **Maven build** → `clean install`

## Building

### Build All Modules

```bash
cd C:\Users\fred\git\multiPlugins
mvn clean install
```

Maven automatically builds in correct order:
1. `multiTools` (no dependencies)
2. `multiCAFE`, `multiSPOTS`, `multiSPOTS96` (depend on multiTools)

### Build Specific Module

```bash
cd C:\Users\fred\git\multiPlugins\multiCAFE
mvn clean install
```

Maven will automatically build `multiTools` first if needed.

## Shared Code Organization

### What's in multiTools?

All `fmp_*` packages containing:
- **fmp_experiment/**: Core domain models
  - `Experiment`, `Cage`, `Spot`, `Capillary`
  - `SequenceCamData`, `SequenceKymos`
  - Persistence classes
  - Computation classes

- **fmp_tools/**: Utilities and tools
  - Chart building (`ChartCageBuild`, `ChartCagePanel`, etc.)
  - Excel export (`XLSExport*` classes)
  - ROI utilities (`ROI2D*` classes)
  - Image processing utilities
  - JComponents (UI components)

- **fmp_series/**: Series processing
  - Image processing pipelines
  - Detection algorithms
  - Registration utilities

- **fmp_service/**: Services
  - `KymographBuilder`
  - `LevelDetector`
  - `GulpDetector`
  - `ExperimentService`

- **fmp_resource/**: Resource utilities
  - `ResourceUtilFMP`

### What Stays in Plugin Modules?

Plugin-specific code:
- **dlg/**: UI dialogs and panels specific to each plugin
- **viewer1D/**: Plugin-specific viewers
- **workinprogress_gpu/**: Experimental code
- Main plugin class (e.g., `MultiCAFE.java`)

## Import Migration

### Before (Old Structure)

```java
import plugins.fmp.multicafe.fmp_experiment.Experiment;
import plugins.fmp.multicafe.fmp_tools.Logger;
import plugins.fmp.multicafe.fmp_service.KymographBuilder;
```

### After (New Structure)

```java
import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_tools.Logger;
import plugins.fmp.multitools.fmp_service.KymographBuilder;
```

**Migration Method:**
- Used PowerShell to replace all imports:
  ```powershell
  Get-ChildItem -Path "src" -Recurse -Filter "*.java" | ForEach-Object {
      (Get-Content $_.FullName -Raw) -replace 
      'import plugins\.fmp\.multicafe\.fmp_', 
      'import plugins.fmp.multitools.fmp_' | 
      Set-Content $_.FullName -NoNewline
  }
  ```

## Deployment Considerations

### ICY Plugin Distribution

Each module produces its own JAR:
- `multiTools/target/multiTools-2.2.3.jar`
- `multiCAFE/target/multiCAFE-2.2.3.jar`
- `multiSPOTS/target/multiSPOTS-2.2.3.jar`
- `multiSPOTS96/target/multiSPOTS96-2.2.3.jar`

### Distribution Options

**Option 1: Fat JAR (Recommended)**
- Bundle multiTools JAR inside each plugin JAR
- Users install one plugin, everything works
- Use Maven Shade plugin to merge classes

**Option 2: Separate Distribution**
- Distribute multiTools as separate plugin on ICY server
- Plugins declare dependency on multiTools
- More modular but requires users to install both

**Option 3: Merged Classes**
- Use Maven Shade plugin to merge classes into single JAR per plugin
- No separate multiTools JAR needed
- Simplest for end users

## Troubleshooting

### Eclipse Only Shows multiTools

**Solution:**
1. Right-click `multiPlugins` → **Maven** → **Update Project...**
2. Check "Force Update of Snapshots/Releases"
3. Ensure all modules are checked
4. Click **OK**

### Parent Not Recognized as Maven Project

**Solution:**
1. Right-click `multiPlugins` → **Configure** → **Convert to Maven Project**
2. Or re-import the parent project

### Build Errors: Missing multiTools

**Solution:**
- Build `multiTools` first: `cd multiTools && mvn clean install`
- Or build from parent: `cd multiPlugins && mvn clean install`

### Import Errors in Eclipse

**Solution:**
1. Right-click project → **Maven** → **Update Project...**
2. Check "Force Update"
3. **Project** → **Clean** → Clean all projects
4. **Project** → **Build All**

## Future Considerations

### Adding New Plugins

1. Create module directory: `multiPlugins/newPlugin/`
2. Copy source code
3. Create `pom.xml` (copy from existing module)
4. Update imports: replace `plugins.fmp.newplugin.fmp_*` → `plugins.fmp.multitools.fmp_*`
5. Remove old `fmp_*` directories
6. Add to parent `pom.xml` modules list
7. Refresh Maven project in Eclipse

### Version Management

- All modules share version from parent: `${project.version}`
- To update version: change in parent `pom.xml`
- All modules inherit the version automatically

### Shared Code Evolution

- Changes to `multiTools` affect all plugins immediately
- Test changes in one plugin to verify compatibility
- Consider backward compatibility when modifying shared code

## Benefits Achieved

✅ **Single Eclipse Workspace**: All projects visible together  
✅ **Immediate Feedback**: Changes in multiTools instantly visible  
✅ **Single Build Command**: `mvn clean install` builds everything  
✅ **Easier Refactoring**: Eclipse tracks dependencies across modules  
✅ **Version Synchronization**: Parent POM manages versions  
✅ **Better for Frequent Updates**: No intermediate publishing needed  
✅ **Simpler Debugging**: Can step through code across modules  
✅ **Centralized Dependency Management**: All versions in parent POM  

## Conclusion

The multi-module Maven structure successfully addresses the development workflow needs:
- Shared code is centralized in `multiTools`
- All plugins depend on the shared library
- Changes to shared code are immediately visible across all plugins
- Single workspace and build process simplify development
- Structure is scalable for adding more plugins in the future

The setup follows Maven best practices and integrates seamlessly with Eclipse IDE for an optimal development experience.
