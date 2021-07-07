# bigdataviewer-headless

Run [ExportImagePlusPlugin](https://imagej.net/plugins/bdv/#exporting-from-imagej-stacks) in headless mode.

## Usage

### 1. Prepare a ImageJ macro (e.g. Groovy)

```
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

import ij.IJ
import ij.Macro
import org.elephant.util.ExportImagePlusPluginHeadless


def main() {
    IJ.run( "Confocal Series (2.2MB)" )
    def tempDir = Files.createTempDirectory( null )
    Macro.setOptions( "export_path=" + tempDir.toString() + File.separator + "tmp.xml" )
    new ExportImagePlusPluginHeadless().run()
    // Clean up
    tempDir.toFile().eachFileRecurse( File::delete )
    Files.delete( tempDir )
}

main()
```

### 2. Run a macro in headless mode

(Windows)

```
ImageJ-win64.exe --ij2 --headless --console --run example.groovy
```

(macOSX)

```
ImageJ-macosx --ij2 --headless --console --run example.groovy
```

(Linux)

```
ImageJ-linux64 --ij2 --headless --console --run example.groovy
```