@file:JvmName("PngDrawer")

package com.vlados.pngdrawer

import org.docopt.Docopt
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

const val ALL = "all"
var preserveAlpha = false
var exceptColor: Color? = null
var imagePath: String = ""
var fromColor: String = ALL
var toColor: Color = Color.BLACK

fun main(vararg args: String) {
    parseArgs(args.asList())

    println("Reading image...")
    val image = readImage(imagePath) ?: run {
        println("Read image is null")
        exitProcess(1)
    }

    println("Changing color...")
    image.changeColor(fromColor, toColor)

    println("Writing image...")
    writeImage(image, imagePath)

    println("Done!")
}

fun readImage(path: String): BufferedImage? {
    return try {
        ImageIO.read(File(path))
    } catch (ex: Throwable) {
        null
    }
}

fun BufferedImage.changeColor(fromColorStr: String, toColor: Color) {
    if (preserveAlpha) {
        val hasAlpha = this.colorModel.hasAlpha()
        if (!hasAlpha) {
            println("Image doesn't have alpha channel! Fallback to default alpha is 255")
        }
    }

    when (fromColorStr) {
        ALL -> {
            this.forEachPixel { image, x, y ->
                if (image.getRGB(x, y).isNotExceptColor(exceptColor)) {
                    image.setRgb(x, y, toColor, preserveAlpha)
                }
            }
        }
        else -> {
            this.forEachPixel { image, x, y ->
                val rgb = image.getRGB(x, y)
                val fromRgb = decode(fromColorStr).rgb
                if (rgb or 0xff000000.toInt() == fromRgb or 0xff000000.toInt() && rgb.isNotExceptColor(exceptColor)) {
                    image.setRgb(x, y, toColor, preserveAlpha)
                }
            }
        }
    }
}

fun Int.isNotExceptColor(exceptColor: Color?): Boolean {
    return this != exceptColor?.rgb
}

fun BufferedImage.setRgb(x: Int, y: Int, toColor: Color, preserveAlpha: Boolean) {
    if (!preserveAlpha) {
        setRGB(x, y, toColor.rgb)
    } else {
        val toColorWithAlpha = Color(toColor.red, toColor.green, toColor.blue, Color(getRGB(x, y), colorModel.hasAlpha()).alpha)
        setRGB(x, y, toColorWithAlpha.rgb)
    }
}

fun BufferedImage.forEachPixel(block: (image: BufferedImage, x: Int, y: Int) -> Unit) {
    for (y in 0 until height) {
        for (x in 0 until width) {
            block(this, x, y)
        }
    }
}

fun writeImage(image: BufferedImage, path: String) {
    try {
        ImageIO.write(image, "png", File(path).getCopyFile())
    } catch (ex: Throwable) {
        ex.printStackTrace()
    }
}

fun File.getCopyFile(): File {
    fun findAcceptableName(file: File, number: Int): File {
        val fileToTest = File(file.parentFile, file.nameWithoutExtension + "_$number." + file.extension)
        return if (fileToTest.exists()) findAcceptableName(file, number + 1) else fileToTest
    }
    val copy = File(this.parentFile, this.nameWithoutExtension + "_copy." + this.extension)
    return if (copy.exists()) findAcceptableName(copy, 1) else copy
}

fun parseArgs(args: List<String>) {
    val parsedArgs = Docopt(USAGE).withVersion(VERSION).parse(args)
    println(parsedArgs)

    preserveAlpha = parsedArgs["--preserve-alpha"] as Boolean
    val except = parsedArgs["--except"] as String
    if (except != "none") {
        exceptColor = decode(except)
    }
    imagePath = parsedArgs[IMAGE] as String
    fromColor = parsedArgs[SOURCE] as String
    toColor = decode(parsedArgs[DEST] as String)
}

fun decode(colorString: String): Color {
    var colorStr = colorString
    if (colorStr.startsWith("#")) {
        colorStr = colorStr.substring(1)
    }

    var colorLong: Long = colorStr.toLong(16)
    when (colorStr.length) {
        6, 3 -> colorLong = colorLong or 0x00000000ff000000
    }
    var color: Int = colorLong.toInt()
    return Color(color shr 16 and 255, color shr 8 and 255, color and 255, color shr 24 and 255)
}

const val VERSION = "0.0.2"

const val IMAGE = "<image>"
const val SOURCE = "<source>"
const val DEST = "<dest>"

val USAGE = """png_drawer
            |
            |Usage:
            |   png_drawer [-p | --preserve-alpha] [-e <color> | --except=<color>] $IMAGE $SOURCE $DEST
            |   
            |Options:
            |   -p, --preserve-alpha          Preserve source pixel alpha
            |   -e <color>, --except=<color>  This color will not be changed [default: none]
            |   
            |Arguments:
            |   $IMAGE   image file name
            |   $SOURCE  color that will be changed from (with # sign). Can be 'all'
            |             'all' - change color for all pixels
            |   $DEST    color that will be changed to (with # sign)
        
    """.trimMargin()