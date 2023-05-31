/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * copyright 2023, Purcel Iulian
 */

package com.iulu.dialpicker

import android.content.Context
import android.os.Build.VERSION_CODES.*
import android.os.Build.VERSION.*
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object DialHaptics {

    fun click(context: Context) {
        val vib: Vibrator
        val vibratorManager: VibratorManager

        if(SDK_INT >= S) {
            vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vib = vibratorManager.defaultVibrator
        } else  vib = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        if (SDK_INT >= Q)
            vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        else
            vib.vibrate(VibrationEffect.createOneShot(30, 255))
    }

}