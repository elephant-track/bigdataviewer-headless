/*-
 * #%L
 * Fiji plugins for starting BigDataViewer and exporting data.
 * %%
 * Copyright (C) 2014 - 2021 BigDataViewer developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package org.elephant.bdv.ij;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.scijava.command.Command;

import bdv.export.ExportMipmapInfo;
import bdv.export.ExportScalePyramid.AfterEachPlane;
import bdv.export.ExportScalePyramid.LoopbackHeuristic;
import bdv.export.ProgressWriter;
import bdv.export.ProposeMipmaps;
import bdv.export.SubTaskProgressWriter;
import bdv.export.WriteSequenceToHdf5;
import bdv.ij.export.imgloader.ImagePlusImgLoader;
import bdv.ij.export.imgloader.ImagePlusImgLoader.MinMaxOption;
import bdv.ij.util.PluginHelper;
import bdv.ij.util.ProgressWriterIJ;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.hdf5.Partition;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.WindowManager;
import ij.util.Tools;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;

/**
 * ImageJ plugin to export the current image to xml/hdf5.
 *
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 * @author Ko Sugawara
 */
public class ExportImagePlusPluginHeadless implements Command
{

	@Override
	public void run()
	{
		final String options = Macro.getOptions();
		System.out.println( options );
		String seqFilename = null;
		if ( options != null && options.contains( "export_path=" ) )
		{
			seqFilename = Tools.split( options.split( "export_path=" )[ 1 ] )[ 0 ];
		}
		if ( seqFilename == null )
		{
			IJ.showMessage( "export_path is required." );
			return;
		}

		if ( ij.Prefs.setIJMenuBar )
			System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		// get the current image
		final ImagePlus imp = WindowManager.getCurrentImage();

		// make sure there is one
		if ( imp == null )
		{
			IJ.showMessage( "Please open an image first." );
			return;
		}

		// check the image type
		switch ( imp.getType() )
		{
		case ImagePlus.GRAY8:
		case ImagePlus.GRAY16:
		case ImagePlus.GRAY32:
			break;
		default:
			IJ.showMessage( "Only 8, 16, 32-bit images are supported currently!" );
			return;
		}

		// get calibration and image size
		final double pw = imp.getCalibration().pixelWidth;
		final double ph = imp.getCalibration().pixelHeight;
		final double pd = imp.getCalibration().pixelDepth;
		String punit = imp.getCalibration().getUnit();
		if ( punit == null || punit.isEmpty() )
			punit = "px";
		final FinalVoxelDimensions voxelSize = new FinalVoxelDimensions( punit, pw, ph, pd );
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getNSlices();
		final FinalDimensions size = new FinalDimensions( w, h, d );

		// propose reasonable mipmap settings
		final ExportMipmapInfo autoMipmapSettings = ProposeMipmaps.proposeMipmaps( new BasicViewSetup( 0, "", size, voxelSize ) );

		// show dialog to get output paths, resolutions, subdivisions, min-max option
		final Parameters params = getParameters( imp.getDisplayRangeMin(), imp.getDisplayRangeMax(), autoMipmapSettings, seqFilename );
		if ( params == null )
			return;

		final ProgressWriter progressWriter = new ProgressWriterIJ();
		progressWriter.out().println( "starting export..." );

		// create ImgLoader wrapping the image
		final ImagePlusImgLoader< ? > imgLoader;
		switch ( imp.getType() )
		{
		case ImagePlus.GRAY8:
			imgLoader = ImagePlusImgLoader.createGray8( imp, params.minMaxOption, params.rangeMin, params.rangeMax );
			break;
		case ImagePlus.GRAY16:
			imgLoader = ImagePlusImgLoader.createGray16( imp, params.minMaxOption, params.rangeMin, params.rangeMax );
			break;
		case ImagePlus.GRAY32:
		default:
			imgLoader = ImagePlusImgLoader.createGray32( imp, params.minMaxOption, params.rangeMin, params.rangeMax );
			break;
		}

		final int numTimepoints = imp.getNFrames();
		final int numSetups = imp.getNChannels();

		// create SourceTransform from the images calibration
		final AffineTransform3D sourceTransform = new AffineTransform3D();
		sourceTransform.set( pw, 0, 0, 0, 0, ph, 0, 0, 0, 0, pd, 0 );

		// write hdf5
		final HashMap< Integer, BasicViewSetup > setups = new HashMap<>( numSetups );
		for ( int s = 0; s < numSetups; ++s )
		{
			final BasicViewSetup setup = new BasicViewSetup( s, String.format( "channel %d", s + 1 ), size, voxelSize );
			setup.setAttribute( new Channel( s + 1 ) );
			setups.put( s, setup );
		}
		final ArrayList< TimePoint > timepoints = new ArrayList<>( numTimepoints );
		for ( int t = 0; t < numTimepoints; ++t )
			timepoints.add( new TimePoint( t ) );
		final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( new TimePoints( timepoints ), setups, imgLoader, null );

		Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo;
		perSetupExportMipmapInfo = new HashMap<>();
		final ExportMipmapInfo mipmapInfo = new ExportMipmapInfo( params.resolutions, params.subdivisions );
		for ( final BasicViewSetup setup : seq.getViewSetupsOrdered() )
			perSetupExportMipmapInfo.put( setup.getId(), mipmapInfo );

		// LoopBackHeuristic:
		// - If saving more than 8x on pixel reads use the loopback image over
		// original image
		// - For virtual stacks also consider the cache size that would be
		// required for all original planes contributing to a "plane of
		// blocks" at the current level. If this is more than 1/4 of
		// available memory, use the loopback image.
		final boolean isVirtual = imp.getStack().isVirtual();
		final long planeSizeInBytes = imp.getWidth() * imp.getHeight() * imp.getBytesPerPixel();
		final long ijMaxMemory = IJ.maxMemory();
		final int numCellCreatorThreads = Math.max( 1, PluginHelper.numThreads() - 1 );
		final LoopbackHeuristic loopbackHeuristic = new LoopbackHeuristic()
		{
			@Override
			public boolean decide( final RandomAccessibleInterval< ? > originalImg, final int[] factorsToOriginalImg, final int previousLevel, final int[] factorsToPreviousLevel, final int[] chunkSize )
			{
				if ( previousLevel < 0 )
					return false;

				if ( Intervals.numElements( factorsToOriginalImg ) / Intervals.numElements( factorsToPreviousLevel ) >= 8 )
					return true;

				if ( isVirtual )
				{
					final long requiredCacheSize = planeSizeInBytes * factorsToOriginalImg[ 2 ] * chunkSize[ 2 ];
					if ( requiredCacheSize > ijMaxMemory / 4 )
						return true;
				}

				return false;
			}
		};

		final AfterEachPlane afterEachPlane = new AfterEachPlane()
		{
			@Override
			public void afterEachPlane( final boolean usedLoopBack )
			{
				if ( !usedLoopBack && isVirtual )
				{
					final long free = Runtime.getRuntime().freeMemory();
					final long total = Runtime.getRuntime().totalMemory();
					final long max = Runtime.getRuntime().maxMemory();
					final long actuallyFree = max - total + free;

					if ( actuallyFree < max / 2 )
						imgLoader.clearCache();
				}
			}

		};

		final ArrayList< Partition > partitions;
		if ( params.split )
		{
			final String xmlFilename = params.seqFile.getAbsolutePath();
			final String basename = xmlFilename.endsWith( ".xml" ) ? xmlFilename.substring( 0, xmlFilename.length() - 4 ) : xmlFilename;
			partitions = Partition.split( timepoints, seq.getViewSetupsOrdered(), params.timepointsPerPartition, params.setupsPerPartition, basename );

			for ( int i = 0; i < partitions.size(); ++i )
			{
				final Partition partition = partitions.get( i );
				final ProgressWriter p = new SubTaskProgressWriter( progressWriter, 0, 0.95 * i / partitions.size() );
				WriteSequenceToHdf5.writeHdf5PartitionFile( seq, perSetupExportMipmapInfo, params.deflate, partition, loopbackHeuristic, afterEachPlane, numCellCreatorThreads, p );
			}
			WriteSequenceToHdf5.writeHdf5PartitionLinkFile( seq, perSetupExportMipmapInfo, partitions, params.hdf5File );
		}
		else
		{
			partitions = null;
			WriteSequenceToHdf5.writeHdf5File( seq, perSetupExportMipmapInfo, params.deflate, params.hdf5File, loopbackHeuristic, afterEachPlane, numCellCreatorThreads, new SubTaskProgressWriter( progressWriter, 0, 0.95 ) );
		}

		// write xml sequence description
		final Hdf5ImageLoader hdf5Loader = new Hdf5ImageLoader( params.hdf5File, partitions, null, false );
		final SequenceDescriptionMinimal seqh5 = new SequenceDescriptionMinimal( seq, hdf5Loader );

		final ArrayList< ViewRegistration > registrations = new ArrayList<>();
		for ( int t = 0; t < numTimepoints; ++t )
			for ( int s = 0; s < numSetups; ++s )
				registrations.add( new ViewRegistration( t, s, sourceTransform ) );

		final File basePath = params.seqFile.getParentFile();
		final SpimDataMinimal spimData = new SpimDataMinimal( basePath, seqh5, new ViewRegistrations( registrations ) );

		try
		{
			new XmlIoSpimDataMinimal().save( spimData, params.seqFile.getAbsolutePath() );
			progressWriter.setProgress( 1.0 );
		}
		catch ( final Exception e )
		{
			throw new RuntimeException( e );
		}
		progressWriter.out().println( "done" );
	}

	protected static class Parameters
	{
		final boolean setMipmapManual;

		final int[][] resolutions;

		final int[][] subdivisions;

		final File seqFile;

		final File hdf5File;

		final MinMaxOption minMaxOption;

		final double rangeMin;

		final double rangeMax;

		final boolean deflate;

		final boolean split;

		final int timepointsPerPartition;

		final int setupsPerPartition;

		public Parameters(
				final boolean setMipmapManual, final int[][] resolutions, final int[][] subdivisions,
				final File seqFile, final File hdf5File,
				final MinMaxOption minMaxOption, final double rangeMin, final double rangeMax, final boolean deflate,
				final boolean split, final int timepointsPerPartition, final int setupsPerPartition )
		{
			this.setMipmapManual = setMipmapManual;
			this.resolutions = resolutions;
			this.subdivisions = subdivisions;
			this.seqFile = seqFile;
			this.hdf5File = hdf5File;
			this.minMaxOption = minMaxOption;
			this.rangeMin = rangeMin;
			this.rangeMax = rangeMax;
			this.deflate = deflate;
			this.split = split;
			this.timepointsPerPartition = timepointsPerPartition;
			this.setupsPerPartition = setupsPerPartition;
		}
	}

	static boolean lastSetMipmapManual = false;

	static String lastSubsampling = "{1,1,1}, {2,2,1}, {4,4,2}";

	static String lastChunkSizes = "{32,32,4}, {16,16,8}, {8,8,8}";

	static int lastMinMaxChoice = 2;

	static double lastMin = 0;

	static double lastMax = 65535;

	static boolean lastSplit = false;

	static int lastTimepointsPerPartition = 0;

	static int lastSetupsPerPartition = 0;

	static boolean lastDeflate = true;

	protected Parameters getParameters( final double impMin, final double impMax, final ExportMipmapInfo autoMipmapSettings, String seqFilename )
	{
		if ( lastMinMaxChoice == 0 ) // use ImageJs...
		{
			lastMin = impMin;
			lastMax = impMax;
		}

		// parse mipmap resolutions and cell sizes
		final int[][] resolutions = PluginHelper.parseResolutionsString( lastSubsampling );
		final int[][] subdivisions = PluginHelper.parseResolutionsString( lastChunkSizes );
		if ( resolutions.length == 0 )
		{
			IJ.showMessage( "Cannot parse subsampling factors " + lastSubsampling );
			throw new RuntimeException( "Cannot parse subsampling factors " + lastSubsampling );
		}
		if ( subdivisions.length == 0 )
		{
			IJ.showMessage( "Cannot parse hdf5 chunk sizes " + lastChunkSizes );
			throw new RuntimeException( "Cannot parse hdf5 chunk sizes " + lastChunkSizes );
		}
		else if ( resolutions.length != subdivisions.length )
		{
			IJ.showMessage( "subsampling factors and hdf5 chunk sizes must have the same number of elements" );
			throw new RuntimeException( "subsampling factors and hdf5 chunk sizes must have the same number of elements" );
		}

		final MinMaxOption minMaxOption;
		if ( lastMinMaxChoice == 0 )
			minMaxOption = MinMaxOption.TAKE_FROM_IMAGEPROCESSOR;
		else if ( lastMinMaxChoice == 1 )
			minMaxOption = MinMaxOption.COMPUTE;
		else
			minMaxOption = MinMaxOption.SET;

		if ( !seqFilename.endsWith( ".xml" ) )
			seqFilename += ".xml";
		final File seqFile = new File( seqFilename );
		final File parent = seqFile.getParentFile();
		if ( parent == null || !parent.exists() || !parent.isDirectory() )
		{
			IJ.showMessage( "Invalid export filename " + seqFilename );
			throw new RuntimeException( "Invalid export filename " + seqFilename );
		}
		final String hdf5Filename = seqFilename.substring( 0, seqFilename.length() - 4 ) + ".h5";
		final File hdf5File = new File( hdf5Filename );

		return new Parameters( lastSetMipmapManual, resolutions, subdivisions, seqFile, hdf5File, minMaxOption, lastMin, lastMax, lastDeflate, lastSplit, lastTimepointsPerPartition, lastSetupsPerPartition );
	}
}
