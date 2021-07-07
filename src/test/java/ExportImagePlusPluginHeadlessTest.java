import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.elephant.bdv.ij.ExportImagePlusPluginHeadless;
import org.junit.Test;

import ij.IJ;
import ij.Macro;
import ij.macro.Interpreter;

public class ExportImagePlusPluginHeadlessTest
{

	@Test
	public void testRun() throws IOException
	{
		Interpreter.batchMode = true;
		Thread.currentThread().setName( "Run$_" + Thread.currentThread().getName() );
		IJ.createImage( "Stack", "8-bit", 16, 16, 1, 8, 2 ).show();
		final Path tempDir = Files.createTempDirectory( null );
		Macro.setOptions( "export_path=" + tempDir.toString() + File.separator + "tmp.xml" );
		new ExportImagePlusPluginHeadless().run();
		// Clean up
		Files.walk( tempDir ).map( Path::toFile ).forEach( File::delete );
		Files.delete( tempDir );
	}

}
