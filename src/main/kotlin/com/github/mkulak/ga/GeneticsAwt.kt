package com.github.mkulak.ga

import com.github.mkulak.ga.GeneticsConfig.dnaSize
import com.github.mkulak.ga.GeneticsConfig.geneSize
import com.github.mkulak.ga.GeneticsConfig.populationSize
import com.github.mkulak.ga.GeneticsConfig.vertices
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.system.measureNanoTime


class GeneticsAwt(origImagePath: String) {
    var iw = 100
    var ih = 100

    var improvementsCount = 0
    var totalCyclesCount = 1

    val start = System.currentTimeMillis()

    val origView = readImage(origImagePath)

    var w = origView.width
    var h = origView.height

    val gfxConfig = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration

    val fittestView = gfxConfig.createCompatibleImage(w, h, Transparency.TRANSLUCENT)

    val orig = gfxConfig.createCompatibleImage(iw, ih, Transparency.TRANSLUCENT)
    val current = gfxConfig.createCompatibleImage(iw, ih, Transparency.TRANSLUCENT)

    var iterateT = 0L
    var renderT = 0L
//cycleT: 226  iterateT: 211 renderT: 6 createChildT: 6 diffT: 0

    init {
        println("sun.java2d.opengl " + System.getProperty("sun.java2d.opengl"))
        constructView(origView, fittestView)
        orig.graphics.drawImage(origView, 0, 0, iw, ih, null)
    }

    fun start() {
        val calcDiff = this::calcDiff
        var population = Array<Individual>(populationSize) { newIndividual(calcDiff) }
        var currentFittest = population[0]
        while (true) {
            totalCyclesCount += 1
            iterateT = measureNanoTime {
                population = iterate(population, calcDiff)
            }
            val newFittest = population[0]
            if (newFittest.difference < currentFittest.difference) {
                improvementsCount += 1
                currentFittest = newFittest
                renderState(fittestView, currentFittest.dna, w, h)
            }
//        println("iterateT: $iterateT renderT: $renderT createChildT: $createChildT")
        }
    }

    fun calcDiff(dna: FloatArray): Long {
        renderT = measureNanoTime {
            renderState(current, dna, iw, ih)
        }
        return diff(orig, current)
    }

    val xsBuffer = IntArray(vertices)
    val ysBuffer = IntArray(vertices)

    fun renderState(image: BufferedImage, dna: FloatArray, w: Int, h: Int) {
        val graphics = image.graphics
        graphics.color = Color.BLACK
        graphics.fillRect(0, 0, w, h)
        var i = 0
        while (i < dnaSize) {
            graphics.color = Color(dna[i], dna[i + 1], dna[i + 2], dna[i + 3])
            var j = 4
            repeat(vertices) {
                xsBuffer[it] = (dna[i + j] * w).toInt()
                ysBuffer[it] = (dna[i + j + 1] * h).toInt()
                j += 2
            }
            graphics.fillPolygon(xsBuffer, ysBuffer, vertices)
            i += geneSize
        }
    }

    fun renderToFile(individual: Individual, w: Int, h: Int, file: File): Unit {
        val image = gfxConfig.createCompatibleImage(w, h, Transparency.TRANSLUCENT)
        renderState(image, individual.dna, w, h)
        ImageIO.write(image, "png", file)
        println("diff: " + diff(orig, image))
    }

    private fun constructView(orig: BufferedImage, best: BufferedImage) {
        val label = JLabel()
        val origView = ImageView(orig)
        val bestView = ImageView(best)
        JFrame("Genetic").apply {
            val imgPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(origView)
                add(bestView)
            }
            val labelPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(label)
            }
            contentPane = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(imgPanel)
                add(labelPanel)
            }
            pack()
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            isVisible = true
        }
        Timer(50) {
            val elapsed = System.currentTimeMillis() - start
            val speed = elapsed / totalCyclesCount
            label.text = "best: $improvementsCount total: $totalCyclesCount speed: $speed ms/gen elapsed: ${elapsed / 1000}"
            bestView.repaint()
        }.start()
    }

    class ImageView(val image: BufferedImage) : JPanel() {

        init {
            preferredSize = Dimension(image.width, image.height)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            g.drawImage(image, 0, 0, null)
        }
    }

    fun diff(img1: BufferedImage, img2: BufferedImage): Long {
        var sum = 0L
        for (x in 0..img1.width - 1) {
            for (y in 0..img1.height - 1) {
                sum += img1.getRGB(x, y) colorDistance img2.getRGB(x, y)
            }
        }
        return sum
    }
}
