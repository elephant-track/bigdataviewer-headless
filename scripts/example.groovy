import java.io.File
import java.nio.file.Files
import java.nio.file.Path

import ij.IJ
import ij.Macro
import ij.macro.Interpreter
import org.elephant.bdv.ij.ExportImagePlusPluginHeadless


def main() {
    Interpreter.setBatchMode(true)
    IJ.createImage( "Stack", "8-bit", 16, 16, 1, 8, 2 ).show()
    def tempDir = Files.createTempDirectory( null )
    Macro.setOptions( "export_path=" + tempDir.toString() + File.separator + "tmp.xml" )
    new ExportImagePlusPluginHeadless().run()
    // Clean up
    tempDir.toFile().eachFileRecurse( File::delete )
    Files.delete( tempDir )
}

main()
