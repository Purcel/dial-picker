/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * copyright 2023, Purcel Iulian
 */

package com.iulu.dialpicker

import kotlin.math.abs

fun <T> Array<T>.rotate(by: Int) {
    val localBy = by % size
    if (localBy > 0)
        for (i in 0 until localBy) {
            val a: T = this[0]
            for (j in 0 until (size - 1)) {
                this[j] = this[j + 1]
            }
            this[size - 1] = a
        }
    else if (localBy < 0)
        for (i in localBy until 0) {
            val a: T = this[size - 1]
            for (j in (size - 1) downTo 1) {
                this[j] = this[j - 1]
            }
            this[0] = a
        }
}

class RollerType(private val max: Int) {
    var value: Int = 0
        set(value) { field = roller(value) }

    fun valueWithOffset(offSet: Int): Int = roller(value + offSet)
    private fun roller(value: Int): Int = when {
        value > 0 -> value % max
        value < 0 -> {
            val a = (abs(value) % max)
            max - if (a != 0) a else max
        }

        else -> 0
    }
}

fun Array<Int>.circularAdd(value: Int, max: UInt) {
    val cs = RollerType(max.toInt())
    for (i in indices) {
        cs.value = this[i] + value
        this[i] = cs.value
    }
}

