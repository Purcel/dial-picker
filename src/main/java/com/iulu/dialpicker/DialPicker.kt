/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * copyright 2023, Purcel Iulian
 */

package com.iulu.dialpicker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.color.MaterialColors
import kotlin.math.abs

class DialPicker @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var gradientTopBottomColor = 0
    private var gradientMiddleColor = 0
    private var dialTextColor = 0
    private var dialTextSize = 0f
    private var dialTextLeading = 0f
    private var descriptionTextSize = 0f
    private var descriptionStartPadding = 0
    private var shadowDx = 0f
    private var shadowDy = 0f

    private var dialMagnetDampingRatio: Float = 0f
    private var dialMagnetStiffness: Float = 0f
    private var dialMagnetEngageSpeed: Float = 0f
    private var dialFriction: Float = 0f

    private var gradientViewWindow: Float = 0f
        get() = (dialPickerHeight / 2f) * field
    private var description = ""

    init {
        val backgroundColor =
            MaterialColors.getColor(context, android.R.attr.windowBackground, Color.WHITE)
        val foregroundColor =
            MaterialColors.getColor(context, android.R.attr.colorForeground, Color.BLACK)

        val ta = context.obtainStyledAttributes(attrs, R.styleable.DialPicker, 0, 0)
        gradientTopBottomColor =
            ta.getColor(R.styleable.DialPicker_gradientTopBottomColor, backgroundColor)
        gradientMiddleColor =
            ta.getColor(R.styleable.DialPicker_gradientMiddleColor, Color.TRANSPARENT)
        gradientViewWindow = ta.getFloat(R.styleable.DialPicker_gradientViewWindows, .9f)
        dialTextColor = ta.getColor(R.styleable.DialPicker_dialTextColor, foregroundColor)
        dialTextSize = ta.getDimension(R.styleable.DialPicker_dialTextSize, 30f.toPx)
        dialTextLeading = ta.getDimension(R.styleable.DialPicker_dialTextLeading, 70f.toPx)
        description = ta.getText(R.styleable.DialPicker_description)?.toString() ?: "Min"
        descriptionTextSize = ta.getDimension(R.styleable.DialPicker_descriptionTextSize, 20f.toPx)
        descriptionStartPadding =
            ta.getDimensionPixelSize(R.styleable.DialPicker_descriptionStartPadding, 10.toPx.toInt())
        shadowDx = ta.getFloat(R.styleable.DialPicker_shadowDx, 1f)
        shadowDy = ta.getFloat(R.styleable.DialPicker_shadowDy, 2f)
        dialMagnetDampingRatio = ta.getFloat(R.styleable.DialPicker_dialMagnetDampingRatio, 0.8f)
        dialMagnetStiffness = ta.getFloat(R.styleable.DialPicker_dialMagnetStiffness, 80f)
        dialMagnetEngageSpeed = ta.getFloat(R.styleable.DialPicker_dialMagnetEngageSpeed, 1000f)
        dialFriction = ta.getFloat(R.styleable.DialPicker_dialFriction, 1f)
        ta.recycle()
    }

    /**
     * List of elements.
     * */
    var list: ArrayList<String> = arrayListOf("01", "02", "03", "04", "05")
        set(value) {
            if (value.size < 5)
                throw Error("The minimum List size for DialPicker is 5 the current size is ${value.size}")
            field = value
        }

    private val centerPositionCounter: RollerType by lazy {
        RollerType(list.size)
    }

    /**
     * Position of the dial (Return the list index).
     * */
    var position: Int
        set(value) {
            centerPositionCounter.value = value
        }
        get() = centerPositionCounter.value

    private var onSnap: (element: String) -> Unit = {}
    private var onClickListener: OnClickListener? = null

    /**
     * Set onPositionChange listener.
     * @param listener Listener
     * */
    fun setOnSnapListener(listener: (element: String) -> Unit) {
        onSnap = listener
    }

    override fun setOnClickListener(l: OnClickListener?) {
        onClickListener = l
    }

    private val posArray = arrayOf(
        1f * dialTextLeading, 2f * dialTextLeading,
        3f * dialTextLeading, 4f * dialTextLeading, 5f * dialTextLeading
    )
    private var dialTextHeight = 0
    private var dialTextExactCenterY = 0f
    private val dialTextSpace: Float by lazy {
        dialTextLeading - dialTextHeight
    }
    private val dialPickerTop: Float by lazy {
        dialPickerYCenter - dialPickerHeight / 2f
    }
    private val dialPickerBottom: Float by lazy {
        dialPickerYCenter + dialPickerHeight / 2f
    }
    private val dialPickerHeight: Float = posArray[posArray.size - 1]
    private var dialTextLength = 0
    private var dialPickerYCenter: Float = 0f
    private var stickyPos: Float = 0f

    private var my: Float = 0f
    private var mx: Float = 0f

    private val descriptionXAnchor: Float by lazy {
        paddingStart + dialTextLength.toFloat() + descriptionStartPadding
    }
    private var descriptionWidth: Int = 0
    private var descriptionExactCenterY: Float = 0f

    private val upperGradient: LinearGradient by lazy {
        LinearGradient(
            0f, dialPickerYCenter - gradientViewWindow, 0f, dialPickerYCenter,
            gradientTopBottomColor, gradientMiddleColor,
            Shader.TileMode.CLAMP
        )
    }
    private val paintUpperGradient: Paint by lazy {
        Paint().apply {
            isDither = true
            shader = upperGradient
        }
    }

    private val bottomGradient: LinearGradient by lazy {
        LinearGradient(
            0f, dialPickerYCenter + gradientViewWindow, 0f, dialPickerYCenter,
            gradientTopBottomColor, gradientMiddleColor,
            Shader.TileMode.CLAMP
        )
    }
    private val paintBottomGradient: Paint by lazy {
        Paint().apply {
            isDither = true
            shader = bottomGradient
        }
    }

    private var snappingState: Boolean = true
    private fun snap() {
        if (isHapticFeedbackEnabled)
            DialHaptics.click(context)
        onSnap(list[centerPositionCounter.value])
    }

    private val magnetValueHolder = FloatValueHolder()
    private val magnetForce = SpringForce().apply {
        dampingRatio = dialMagnetDampingRatio
        stiffness = dialMagnetStiffness
    }

    private var fingerUpY = 0f
    private val magnet = SpringAnimation(magnetValueHolder).apply {
        spring = magnetForce
        addUpdateListener { _, value, _ ->
            snappingState = false
            my = value
            invalidate()
        }
        addEndListener { _, _, _, _ ->
            snappingState = true
            fingerUpY = my
        }
    }

    private val flingValueHolder = FloatValueHolder()
    private val flingAnimation = FlingAnimation(flingValueHolder).apply {
        friction = dialFriction
        addUpdateListener { _, value, velocity ->
            if (velocity < dialMagnetEngageSpeed && velocity > -dialMagnetEngageSpeed) {
                cancel()
                magnet.setStartVelocity(velocity)
                magnet.setStartValue(my)
                magnet.animateToFinalPosition(stickyPos)
                magnet.start()
            }
            my = value
            invalidate()
        }
        addEndListener { _, _, _, _ ->
            fingerUpY = my
        }
    }

    private val rawPositionCounter: RollerType by lazy {
        RollerType(list.size).apply { value = -2 }
    }

    private val roller = arrayOf(0, 1, 2, 3, 4)

    private fun moveDialUp(times: Int) {
        for (t in -1 until times) {
            posArray[0] += dialPickerHeight
            posArray.rotate(1)
            roller[0] = rawPositionCounter.valueWithOffset(5)
            roller.rotate(1)
            stickyPos -= dialTextLeading
            rawPositionCounter.value++
            centerPositionCounter.value++
        }
    }

    private fun moveDialDown(times: Int) {
        for (t in -1 until times) {
            centerPositionCounter.value--
            rawPositionCounter.value--
            posArray[posArray.size - 1] -= dialPickerHeight
            posArray.rotate(-1)
            roller[roller.size - 1] = rawPositionCounter.value
            roller.rotate(-1)
            stickyPos += dialTextLeading
        }
    }

    private val paintDialText: Paint by lazy {
        Paint().apply {
            color = dialTextColor
            textSize = dialTextSize
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                setShadowLayer(elevation, shadowDx, shadowDy, outlineAmbientShadowColor)
        }
    }

    private val paintDescription: Paint by lazy {
        Paint().apply {
            color = dialTextColor
            textSize = descriptionTextSize
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                setShadowLayer(elevation, shadowDx, shadowDy, outlineAmbientShadowColor)
        }
    }

    private fun drawDial(canvas: Canvas?) {
        val cy = my - dialTextExactCenterY
        canvas?.drawText(
            list[roller[0]],
            mx,
            cy + posArray[0],
            paintDialText
        )
        canvas?.drawText(
            list[roller[1]],
            mx,
            cy + posArray[1],
            paintDialText
        )
        canvas?.drawText(
            list[roller[2]],
            mx,
            cy + posArray[2],
            paintDialText
        )
        canvas?.drawText(
            list[roller[3]],
            mx,
            cy + posArray[3],
            paintDialText
        )
        canvas?.drawText(
            list[roller[4]],
            mx,
            cy + posArray[4],
            paintDialText
        )
    }

    private fun drawGradient(canvas: Canvas?) {
        canvas?.drawRect(0f, 0f, width.toFloat(), dialPickerYCenter, paintUpperGradient)
        canvas?.drawRect(
            0f,
            dialPickerYCenter,
            width.toFloat(),
            height.toFloat(),
            paintBottomGradient
        )
    }

    private fun drawDescription(canvas: Canvas?) {
        canvas?.drawText(
            description,
            descriptionXAnchor,
            dialPickerYCenter - descriptionExactCenterY,
            paintDescription
        )
    }

    override fun onDraw(canvas: Canvas?) {

        val movingTop = my + posArray[0]
        val timesUp = (abs(movingTop / dialTextLeading)).toInt()
        if (movingTop <= dialPickerTop) {
            moveDialUp(timesUp)
            if (snappingState)
                snap()
        }

        val movingBottom = my + posArray[posArray.size - 1]
        val timesDown = (abs((movingBottom - dialPickerHeight) / dialTextLeading)).toInt()
        if (movingBottom >= dialPickerBottom) {
            moveDialDown(timesDown)
            if (snappingState)
                snap()
        }

        drawDial(canvas)
        drawDescription(canvas)
        drawGradient(canvas)

        super.onDraw(canvas)
    }

    private val textBounds = Rect()
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val longestDialElement: String = list.getTrueWidestString(textBounds, paintDialText)
        paintDialText.getTextBounds(longestDialElement, 0, longestDialElement.length, textBounds)
        dialTextLength = textBounds.width()
        dialTextHeight = textBounds.height()
        dialTextExactCenterY = textBounds.exactCenterY()

        paintDescription.getTextBounds(description, 0, description.length, textBounds)
        descriptionWidth = textBounds.width()
        descriptionExactCenterY = textBounds.exactCenterY()

        onSnap(list[centerPositionCounter.value])

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        var width = 0
        var height = 0

        when (widthMode) {
            MeasureSpec.AT_MOST -> {
                width =
                    paddingStart + dialTextLength + descriptionStartPadding + descriptionWidth + paddingEnd + 5 //Magic number! Yey! 🙌
            }

            MeasureSpec.EXACTLY -> {
                width = widthSize
            }

            MeasureSpec.UNSPECIFIED -> {
                width =
                    paddingStart + dialTextLength + descriptionStartPadding + descriptionWidth + paddingEnd + 5 //Magic number! Yey! 🙌
            }
        }

        when (heightMode) {
            MeasureSpec.AT_MOST -> {
                height = dialPickerHeight.toInt() + paddingTop + paddingBottom
            }

            MeasureSpec.EXACTLY -> {
                height = heightSize
            }

            MeasureSpec.UNSPECIFIED -> {
                height = dialPickerHeight.toInt() + paddingTop + paddingBottom
            }
        }
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        dialPickerYCenter = h / 2f + paddingTop

        rawPositionCounter.value = centerPositionCounter.valueWithOffset(-2)
        roller.circularAdd(rawPositionCounter.value, list.size.toUInt())

        mx = paddingStart + dialTextLength / 2f
        my = dialPickerTop - dialTextHeight / 2f - dialTextSpace / 2f
        stickyPos = dialPickerTop - dialTextLeading / 2f
        fingerUpY = my
    }

    private val velocityTracker = VelocityTracker.obtain()
    private var fingerDownY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event!!.action) {
            MotionEvent.ACTION_DOWN -> {
                flingAnimation.cancel()
                magnet.cancel()
                fingerDownY = event.y
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                my = ((event.y - fingerDownY) + fingerUpY)
                velocityTracker.addMovement(event)
                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                fingerUpY = my
                velocityTracker.addMovement(event)
                velocityTracker.computeCurrentVelocity(1000, 4000f)
                flingAnimation.setStartValue(fingerUpY)
                flingAnimation.setStartVelocity(velocityTracker.yVelocity)
                flingAnimation.start()
                onClickListener?.onClick(this)
                return false
            }
        }
        return true
    }

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable("superSate", super.onSaveInstanceState())
        bundle.putInt("position", position)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        var mState = state
        if (mState is Bundle) {
            mState.apply {
                position = getInt("position")
                mState = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                    getParcelable("superState")
                else
                    getParcelable("superState", Parcelable::class.java)
            }
        }
        super.onRestoreInstanceState(mState)
    }
}
