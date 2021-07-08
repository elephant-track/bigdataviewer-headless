# bigdataviewer-headless

Run [ExportImagePlusPlugin](https://imagej.net/plugins/bdv/#exporting-from-imagej-stacks) in headless mode.

## Getting started

### 1. Install

```
git clone git@github.com:elephant-track/bigdataviewer-headless.git
```

```
cd bigdataviewer-headless
mvn clean package
```

Please replace `$FIJI_HOME` depending on your environment.

```
cp target/bigdataviewer-headless-0.0.1-SNAPSHOT.jar $FIJI_HOME/jars
```

### 2. Prepare an ImageJ macro (e.g. Groovy)

```
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

import ij.IJ
import ij.Macro
import ij.macro.Interpreter
import ij.WindowManager
import org.elephant.bdv.ij.ExportImagePlusPluginHeadless


def main() {
    def tempDir = Files.createTempDirectory( null )
    WindowManager.setTempCurrentImage( IJ.createImage( "Stack", "8-bit", 16, 16, 1, 8, 2 ) )
    Macro.setOptions( "export_path=" + tempDir.toString() + File.separator + "tmp.xml" )
    new ExportImagePlusPluginHeadless().run()
    // Clean up
    tempDir.toFile().eachFileRecurse( File::delete )
    Files.delete( tempDir )
}

main()

```

You can find [an example](scripts/ctc2bdv.groovy) of converting a data set in Cell Tracking Challenge format to BDV format.

### 3. Run a macro in headless mode

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