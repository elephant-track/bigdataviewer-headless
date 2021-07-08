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
    def temp = WindowManager.getTempCurrentImage()
    WindowManager.setTempCurrentImage( IJ.createImage( "Stack", "8-bit", 16, 16, 1, 8, 2 ) )
    Macro.setOptions( "export_path=" + tempDir.toString() + File.separator + "tmp.xml" )
    new ExportImagePlusPluginHeadless().run()
    Macro.setOptions(null)
    WindowManager.setTempCurrentImage( temp )
    // Clean up
    tempDir.toFile().eachFileRecurse( File::delete )
    Files.delete( tempDir )
}

main()
