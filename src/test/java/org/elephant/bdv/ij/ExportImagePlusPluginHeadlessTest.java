package org.elephant.bdv.ij;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import ij.IJ;
import ij.Macro;
import ij.WindowManager;

public class ExportImagePlusPluginHeadlessTest
{

	@Test
	public void testRun() throws IOException, ClassNotFoundException
	{
		// This is only required in test run
		// https://github.com/imagej/ij1-patcher/blob/master/src/main/java/net/imagej/patcher/LegacyExtensions.java#L799-L816
		Thread.currentThread().setName( "Run$_" + Thread.currentThread().getName() );
		WindowManager.setTempCurrentImage( IJ.createImage( "Stack", "8-bit", 16, 16, 1, 8, 2 ) );
		final Path tempDir = Files.createTempDirectory( null );
		Macro.setOptions( "export_path=" + tempDir.toString() + File.separator + "tmp.xml" );
		new ExportImagePlusPluginHeadless().run();
		// Clean up
		Files.walk( tempDir ).map( Path::toFile ).forEach( File::delete );
		Files.delete( tempDir );
	}

}
