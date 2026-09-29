# Multi Plugins - Maven Multi-Module Project

This repository contains three related [Icy](https://icy.bioimageanalysis.org/) plugins and libraries for analyzing fly behavior and feeding experiments. Common experiment, image-processing, persistence, charting, and export code is shared through `multiTools`.

## Modules

```
multiPlugins/
|-- pom.xml                    # Parent Maven project
|-- multiTools/                # Shared library
|   `-- src/main/java/plugins/fmp/multitools/
|       |-- experiment/        # Experiment, cage, spot, and capillary models
|       |-- series/            # Image-series and kymograph processing
|       |-- service/           # Detection, tracking, and analysis services
|       |-- tools/             # Charts, Excel export, registration, and utilities
|       |-- transfer/          # Experiment data transfer support
|       `-- workinprogress_gpu/# Experimental GPU code; not production functionality
|-- multiCAFE/                 # Capillary-feeding analysis plugin
|   `-- src/main/java/plugins/fmp/multicafe/
|       |-- dlg/               # Analysis workflows and dialogs
|       `-- viewer1D/          # One-dimensional data viewers
`-- multiSPOTS/                # Liquid-spot feeding analysis plugin
    `-- src/main/java/plugins/fmp/multiSPOTS/
        `-- dlg/               # Analysis workflows and dialogs
```

`multiCAFE` and `multiSPOTS` both depend on `multiTools`. The experimental GPU classes are retained from earlier investigations but are unfinished and are not part of the supported analysis workflow.

## Requirements

- Maven
- A JDK capable of compiling Java 8 source and target bytecode
- Access to the dependency repositories declared in the parent POM

The project currently targets Java 8 for compatibility with Icy 2.5.x installations.

## Building

Build and test all modules from the repository root:

```bash
mvn clean install
```

Build one plugin together with any required upstream modules:

```bash
mvn -pl multiCAFE -am clean install
mvn -pl multiSPOTS -am clean install
```

The generated JAR files are placed in each module's `target/` directory.

## Eclipse Setup

1. Select **File > Import > Existing Maven Projects**.
2. Choose this repository's root directory (the directory containing the parent `pom.xml`).
3. Import the parent and all three discovered modules.

Keeping the modules in one workspace allows changes in `multiTools` to be resolved immediately by both plugins and supports refactoring across module boundaries.

## Package Naming

- Shared code: `plugins.fmp.multitools.*`
- MultiCAFE-specific code: `plugins.fmp.multicafe.*`
- multiSPOTS-specific code: `plugins.fmp.multiSPOTS.*`

## License

GNU GPLv3. See the Maven project metadata for organization and developer information.
