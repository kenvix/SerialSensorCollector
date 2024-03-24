package com.kenvix.sensorcollector.utils

inline fun <reified T> Sequence<T>.toArray(size: Int): Array<T> {
    val iter = iterator()
    return Array(size) { iter.next() }
}

fun Sequence<Int>.toIntArray(size: Int): IntArray {
    val iter = iterator()
    return IntArray(size) { iter.next() }
}

