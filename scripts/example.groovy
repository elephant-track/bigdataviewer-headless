import java.io.File
import java.nio.file.Files
import java.nio.file.Path

import ij.IJ
import ij.Macro
import org.elephant.bdv.ij.ExportImagePlusPluginHeadless


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
