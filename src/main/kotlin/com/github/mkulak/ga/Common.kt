package com.github.mkulak.ga

import javax.imageio.ImageIO

data class Individual(val dna: FloatArray, val difference: Long)

infix fun Individual.eq(other: Individual) = dna.contentEquals(other.dna)

inline fun norm(v: Float) = if (v < 0) 0f else if (v > 1) 1f else v

inline fun randomFloat() = Math.random().toFloat()

inline infix fun Int.colorDistance(other: Int): Long {
    val da = alpha() - other.alpha()
    val dr = red() - other.red()
    val dg = green() - other.green()
    val db = blue() - other.blue()
    return dr.toLong() * dr + dg * dg + db * db + da * da
}

inline fun Int.alpha(): Int = b1()
inline fun Int.red(): Int = b2()
inline fun Int.green(): Int = b3()
inline fun Int.blue(): Int = b4()

inline fun Int.b1(): Int = (this and 0xff000000.toInt()) shr 24
inline fun Int.b2(): Int = (this and 0x00ff0000) shr 16
inline fun Int.b3(): Int = (this and 0x0000ff00) shr 8
inline fun Int.b4(): Int = this and 0x000000ff

inline fun color(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) + (r shl 16) + (g shl 8) + b

inline fun randomInt(bound: Int, notEqual: Int): Int {
    while (true) {
        val r = (Math.random() * bound).toInt()
        if (r != notEqual) {
            return r
        }
    }
}

inline fun Float.tryMutate(): Float =
        if (Math.random() < GeneticsConfig.mutationChance) {
            var v = this + randomFloat() * GeneticsConfig.mutationAmount * 2 - GeneticsConfig.mutationAmount
            if (v < 0f) v = 0f
            if (v > 1f) v = 1f
            v
        } else this

object GeneticsConfig {
    val populationSize = 50
    val selectionCutoff = 0.15
    val mutationChance = 0.01
    val mutationAmount = 0.1f
    val polygons = 125
    val vertices = 3
    val geneSize = (4 + vertices * 2);
    val dnaSize = geneSize * polygons
}

fun randomDna(): FloatArray {
    val dna = FloatArray(GeneticsConfig.dnaSize)
    var i = 0
    while (i < dna.size) {
        dna[i] = randomFloat()                                      //R
        dna[i + 1] = randomFloat()                                  //G
        dna[i + 2] = randomFloat()                                  //B
        dna[i + 3] = Math.max(0.2f, randomFloat() * randomFloat())  //A
        val x = randomFloat()
        val y = randomFloat()
        var j = 0
        while (j < GeneticsConfig.vertices * 2) {
            dna[i + 4 + j] = norm(x + randomFloat() - 0.5f)
            dna[i + 5 + j] = norm(y + randomFloat() - 0.5f)
            j += 2
        }
        i += GeneticsConfig.geneSize
    }
    return dna
}

fun newIndividual(calcDiff: (FloatArray) -> Long): Individual {
    val dna = randomDna()
    return Individual(dna, calcDiff(dna))
}

fun readImage(path: String) = ImageIO.read(Individual::class.java.classLoader.getResourceAsStream(path))

fun iterate(population: Array<Individual>, calcDiff: (FloatArray) -> Long): Array<Individual> {
    val offspring = ArrayList<Individual>();

    val selectCount = (GeneticsConfig.populationSize * GeneticsConfig.selectionCutoff).toInt()
    val randCount = Math.ceil(1 / GeneticsConfig.selectionCutoff).toInt()

    repeat(selectCount) { i ->
        repeat(randCount) {
            val randIndividual = randomInt(selectCount, i)
            offspring.add(createChild(population[i], population[randIndividual], calcDiff))
        }
    }
    repeat(GeneticsConfig.populationSize - offspring.size) {
        offspring.add(population[it])
//        offspring.add(newIndividual())
    }

    val newPopulation = offspring.toTypedArray()
    newPopulation.sortBy { it.difference }
    return newPopulation
}


fun createChild(father: Individual, mother: Individual, calcDiff: (FloatArray) -> Long): Individual {
    val childDna = FloatArray(GeneticsConfig.dnaSize)
    var i = 0
    while (i < GeneticsConfig.dnaSize) {
        val ancestorDna = if (Math.random() < 0.5) father.dna else mother.dna
        repeat(GeneticsConfig.geneSize) {
            childDna[i + it] = ancestorDna[i + it].tryMutate()
        }
        i += GeneticsConfig.geneSize
    }
    return Individual(childDna, calcDiff(childDna))
}







