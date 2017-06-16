package com.github.mkulak.ga

fun main(args: Array<String>) {
    val pic = if (args.size > 1) args[1] else "lisa_256.png"
    when (args.firstOrNull()) {
        "awt" -> GeneticsAwt(pic).start()
        "opengl" -> GeneticsOpenGL(pic).start()
        null -> println("""Specify "awt" or "opengl" as first parameter""")
    }
}
