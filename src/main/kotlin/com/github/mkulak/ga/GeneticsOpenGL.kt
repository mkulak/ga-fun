package com.github.mkulak.ga

import com.github.mkulak.ga.GeneticsConfig.geneSize
import com.github.mkulak.ga.GeneticsConfig.populationSize
import com.github.mkulak.ga.GeneticsConfig.vertices
import de.matthiasmann.twl.utils.PNGDecoder
import kotlinx.support.jdk7.use
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL14.*
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.NULL
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.File
import java.nio.ByteBuffer
import java.nio.IntBuffer
import javax.imageio.ImageIO
import kotlin.system.measureNanoTime


class GeneticsOpenGL(origImagePath: String) {
    private var window: Long = 0
    var textureId = 0
    private val width = 512
    private val height = 512
    val origBuf = BufferUtils.createByteBuffer(width * height * 4)
    val curBuf = BufferUtils.createByteBuffer(width * height * 4)

    var improvementsCount = 0
    var totalCyclesCount = 1

    var iterateT = 0L
    var renderT = 0L
    var createChildT = 0L
    var start = System.nanoTime()
    var shouldDump = false

    init {
        System.setProperty("java.awt.headless", "true")
        init()
        loadTexture(origImagePath)

        glDrawBuffer(GL_LEFT)
        glReadBuffer(GL_LEFT)

        glPushMatrix()
        glTranslatef(-1f, -1f, 0f)
//        glScalef(0.125f, 0.125f, 1f)
        renderOrig()
        glReadPixels(0, height, width, height, GL_RGBA, GL_UNSIGNED_BYTE, origBuf)
        bufToFile(width, height, origBuf.asIntBuffer(), "1")
        glPopMatrix()

        glDrawBuffer(GL_FRONT)
        glReadBuffer(GL_FRONT)

        renderOrig()
        renderRedSquare()
        swapAndCheckEvents()
        renderOrig()
        renderRedSquare()
    }

    fun start() {
        val calcDiff = this::calcDiff
        var population = Array<Individual>(populationSize) { newIndividual(calcDiff) }
        var currentFittest = population[0]
        renderFittest(currentFittest.dna)
        while (!shouldClose()) {
            totalCyclesCount += 1
            iterateT = measureNanoTime {
                population = iterate(population, calcDiff)
            }
            val newFittest = population[0]
            if (newFittest.difference < currentFittest.difference) {
                improvementsCount += 1
                currentFittest = newFittest
                renderFittest(currentFittest.dna)
//                glfwSwapBuffers(window)
//                renderFittest(currentFittest.dna)
//                println(currentFittest.difference)
            }
//        println("iterateT: $iterateT renderT: $renderT createChildT: $createChildT")
            swapAndCheckEvents()
        }
    }

    fun init() {
        GLFWErrorCallback.createPrint(System.err).set()
        if (!glfwInit()) throw IllegalStateException("Unable to initialize GLFW")
        glfwDefaultWindowHints() // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE) // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE) // the window will be resizable

        window = glfwCreateWindow(width, height, "Hello World!", NULL, NULL)
        if (window == NULL) throw RuntimeException("Failed to create the GLFW window")

        glfwSetKeyCallback(window, this::handleKeys)//will be called every time a key is pressed, repeated or released

        stackPush().use { stack ->
            val pWidth = stack.mallocInt(1) // int*
            val pHeight = stack.mallocInt(1) // int*
            glfwGetWindowSize(window, pWidth, pHeight) // Get the window size passed to glfwCreateWindow
            val vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor())
            glfwSetWindowPos(window, (vidmode.width() - pWidth.get(0)) / 2, (vidmode.height() - pHeight.get(0)) / 2)
        }

        glfwMakeContextCurrent(window)
//        glfwSwapInterval(1) // Enable v-sync
        glfwShowWindow(window)

        GL.createCapabilities()
        glEnable(GL_TEXTURE_2D)
        glEnable(GL_BLEND)
        glBlendEquation(GL_FUNC_ADD)
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        glDisable(GL_CULL_FACE)
        glDisable(GL_DEPTH_TEST)
    }

    fun loadTexture(path: String) {
        val input = GeneticsOpenGL::class.java.classLoader.getResourceAsStream(path)
        val dec = PNGDecoder(input)
        val width = dec.width
        val height = dec.height
        val bpp = 4 //we will decode to RGBA format, i.e. 4 components or "bytes per pixel"
        val buf = BufferUtils.createByteBuffer(bpp * width * height)
        dec.decode(buf, width * bpp, PNGDecoder.Format.RGBA)
        buf.flip()

        textureId = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, textureId)
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf)
    }

    fun calcDiff(dna: FloatArray): Long {
        var diff: Long = 0
        renderT = measureNanoTime {
            diff = renderAndDiff(dna)
        }
        return diff
    }

    fun renderAndDiff(dna: FloatArray): Long {
        glPushMatrix()
        glTranslatef(-1f, -1f, 0f)
        renderDna(dna)
        curBuf.clear()
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, curBuf)
        if (shouldDump) {
            bufToFile(width, height, origBuf.asIntBuffer(), "1")
            bufToFile(width, height, curBuf.asIntBuffer(), "2")
            shouldDump = false
//            println("diff: " + diff(origBuf, curBuf))
        }
        glPopMatrix()
        return diff(origBuf, curBuf)
    }

    fun diff(a: ByteBuffer, b: ByteBuffer): Long {
//        println("a.limit: ${a.limit()} b.limit: ${b.limit()}")
        var res = 0L
        repeat(a.limit()) {
            val d = a.get().toInt() - b.get()
            res += d * d
        }
        a.flip()
        b.flip()
        return res
    }

    fun renderFittest(currentFittest: FloatArray) {
        glPushMatrix()
        renderDna(currentFittest)
        glPopMatrix()
    }

    private fun renderOrig() {
        glPushMatrix()
        glTranslatef(-1f, 0f, 0f)
        glColor4f(1f, 1f, 1f, 1f)
        glBegin(GL_QUADS)
        run {
            glTexCoord2f(0f, 1f)
            glVertex2f(0f, 0f)

            glTexCoord2f(1f, 1f)
            glVertex2f(1f, 0f)

            glTexCoord2f(1f, 0f)
            glVertex2f(1f, 1f)

            glTexCoord2f(0f, 0f)
            glVertex2f(0f, 1f)
        }
        glEnd()
        glPopMatrix()
    }

    private fun renderRedSquare() {
        glPushMatrix()
        glTranslatef(0f, -1f, 0f)
        glColor4f(0.89f, 0.38f, 0.31f, 1f)
        glBegin(GL_QUADS)
        run {
            glVertex2f(0f, 0f)
            glVertex2f(1f, 0f)
            glVertex2f(1f, 1f)
            glVertex2f(0f, 1f)
        }
        glEnd()
        glPopMatrix()
    }

    private fun renderDna(dna: FloatArray) {
        glColor4f(0f, 0f, 0f, 1f)
        glBegin(GL_QUADS)
        run {
            glVertex2f(0f, 0f)
            glVertex2f(1f, 0f)
            glVertex2f(1f, 1f)
            glVertex2f(0f, 1f)
        }
        glEnd()

        glBegin(GL_TRIANGLES)
        var i = 0
        while (i < dna.size) {
            glColor4f(dna[i], dna[i + 1], dna[i + 2], dna[i + 3])
            var j = 4
            repeat(vertices) {
                glVertex2f(dna[i + j], dna[i + j + 1])
                j += 2
            }
            i += geneSize
        }
        glEnd()
    }

    fun swapAndCheckEvents() {
        glfwPollEvents() // Poll for window events. The key callback above will only be invoked during this call.
        glfwSwapBuffers(window)
    }

    fun shouldClose() = glfwWindowShouldClose(window)

    private fun handleKeys(window: Long, key: Int, scancode: Int, action: Int, mods: Int) {
        if (action == GLFW_RELEASE) {
            when (key) {
                GLFW_KEY_ESCAPE -> {
                    glfwSetWindowShouldClose(window, true)
                }
                GLFW_KEY_Q -> {
                    glfwSetWindowShouldClose(window, true)
                }
                GLFW_KEY_I -> {
                    val renderTime = (System.nanoTime() - start) / totalCyclesCount
                    println("renderTime: $renderTime")
                    start = System.nanoTime()
                    totalCyclesCount = 0
                }
                GLFW_KEY_R -> {
                    println("R pressed")
                    shouldDump = true
                }
            }
        }
    }

    private fun bufToFile(w: Int, h: Int, buf: IntBuffer, name: String) {
        val bytes = IntArray(w * h)
        buf.get(bytes, 0, bytes.size)
        buf.flip()
        val image = toImage(bytes, w, h)
        val file = File("$name.png")
        ImageIO.write(image, "png", file)
        println(file.absolutePath)
    }

    fun toImage(data: IntArray, w: Int, h: Int): BufferedImage {
        for (i in data.indices) {
            val v = data[i]
            data[i] = color(v.b1(), v.b4(), v.b3(), v.b2())
        }
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val array = (image.raster.dataBuffer as DataBufferInt).data
        System.arraycopy(data, 0, array, 0, array.size)
//        val flip = AffineTransform.getScaleInstance(1.0, -1.0).apply { translate(0.0, (-image.height).toDouble()) }
//        return AffineTransformOp(flip, AffineTransformOp.TYPE_NEAREST_NEIGHBOR).filter(image, null)
        return image
    }

    fun renderToFile(individual: Individual): Unit {
        val buf = BufferUtils.createByteBuffer(width * height * 4)
        glPushMatrix()
        glTranslatef(-1f, -1f, 0f)
        renderDna(individual.dna)
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buf)
        bufToFile(width, height, buf.asIntBuffer(), "opengl")
        println("diff: "+ diff(origBuf, buf))
        glfwSwapBuffers(window)
        renderDna(individual.dna)
        glPopMatrix()
        while(!shouldClose()) {
            swapAndCheckEvents()
            Thread.sleep(300)
        }
    }

    fun destroy() {
        glfwFreeCallbacks(window)
        glfwDestroyWindow(window)
        glfwTerminate()
        glfwSetErrorCallback(null).free()
    }
}
