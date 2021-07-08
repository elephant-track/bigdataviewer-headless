import ij.IJ
import ij.Macro
import ij.macro.Interpreter
import ij.plugin.Concatenator
import org.elephant.bdv.ij.ExportImagePlusPluginHeadless

#@ File (label = "Input directory", style = "directory") input
#@ File (label = "Output file (.xml)", style = "file") output
#@ String (label = "File suffix", value = ".tif", persist=false) suffix
#@ Float (label = "Size X", value = 0, persist=false) sizeX
#@ Float (label = "Size Y", value = 0, persist=false) sizeY
#@ Float (label = "Size Z", value = 0, persist=false) sizeZ
#@ String (label = "Unit (e.g. µm)", value = "", persist=false) unit

def main() {
    def count = 0
    def imp, imp1, imp2
    input.listFiles().sort{ it.name }.each {
		if (it.name.endsWith(suffix)) {
            println "Processing " + it.name
            imp2 = IJ.openImage(input.getAbsolutePath() + File.separator + it.name)
            if (count++ == 0)
                imp = imp2
            else
                imp = Concatenator.run(imp1, imp2)
            imp1 = imp
		}
	}
	def dims = imp.getDimensions()
    def cal = imp.getCalibration()
	println "Original Image Dimensions"
    printDimensions(dims, cal)
	def orgSizeX = Float.parseFloat(IJ.d2s(cal.pixelWidth, 7))
    def orgSizeY = Float.parseFloat(IJ.d2s(cal.pixelHeight, 7))
    def orgSizeZ = Float.parseFloat(IJ.d2s(cal.pixelDepth, 7))
    def orgUnit = cal.getUnits()
	if (sizeX <= 0)
		sizeX = orgSizeX
	if (sizeY <= 0)
		sizeY = orgSizeY
	if (dims[3] == 1 || sizeZ <= 0)
		sizeZ = orgSizeZ
	if (unit == "")
		unit = orgUnit
    cal.pixelWidth = sizeX
    cal.pixelHeight = sizeY
    cal.pixelDepth = sizeZ
    cal.setUnit(unit)
    cal = imp.getCalibration()
	println "Final Image Dimensions"
    printDimensions(dims, cal)
	println "Generate BDV files in " + output.getParentFile()
	output.getParentFile().mkdirs()
    Macro.abort = false
    Macro.setOptions("export_path=" + output)
    Interpreter.setBatchMode(true)
    imp.show()
    new ExportImagePlusPluginHeadless().run()
    Interpreter.setBatchMode(false)
    Macro.setOptions(null)
}

def printDimensions(def dims, def cal) {
	println "   Width: " + dims[0] + " (" + IJ.d2s(cal.pixelWidth, 7) + " " + cal.getUnits() + ")"
	println "   Height: " + dims[1] + " (" + IJ.d2s(cal.pixelHeight, 7) + " " + cal.getUnits() + ")"
	println "   Slices: " + dims[3]  + " (" + IJ.d2s(cal.pixelDepth, 7) + " " + cal.getUnits() + ")"
	println "   Frames: " + dims[4]
	println "   Chanels: " + dims[2]
}

main()
