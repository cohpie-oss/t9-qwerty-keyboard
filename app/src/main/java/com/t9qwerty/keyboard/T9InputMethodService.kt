package com.t9qwerty.keyboard

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.text.InputType
import android.text.TextUtils
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

class T9InputMethodService : InputMethodService() {
    private val groups = mapOf('1' to "qwe", '2' to "rtyu", '3' to "iop", '4' to "asd", '5' to "fgh", '6' to "jkl", '7' to "zx", '8' to "cvb", '9' to "nm")
    private val handler = Handler(Looper.getMainLooper())
    private val wordDigits by lazy { groups.flatMap { (number, letters) -> letters.map { it to number } }.toMap() }
    private var dictionary = emptyList<String>()
    private var indexedDictionary = emptyList<Pair<String, String>>()
    private var pattern = ""
    private var manual = false
    private var lastKey: Char? = null
    private var tapIndex = 0
    private var pendingManual = ""
    private var suggestions: LinearLayout? = null
    private var status: TextView? = null
    private var punctuationMode = false
    private var lastPunctuationTap = 0L
    private var lastCommitWasWord = false
    private var backspaceHeld = false
    private var shifted = false
    private var punctuationGestureActive = false
    private var punctuationMenuButtons = emptyList<Button>()
    private var wordSuggestions = emptyList<String>()
    private var replacementBefore = 0
    private var replacementAfter = 0
    private var suppressWordSuggestionsUntil = 0L
    private var standardPrediction = emptyList<String>()
    private var standardReplaceBefore = 0
    private var lastDirectKeyAt = 0L
    private var numericMode = false
    private val commonWordRank = mapOf("to" to 100, "the" to 90, "and" to 85, "you" to 80, "for" to 75, "with" to 70, "this" to 65, "that" to 65, "hello" to 60, "thanks" to 60, "please" to 55, "lol" to 55, "omg" to 50, "brb" to 45, "idk" to 45, "btw" to 45, "asap" to 40)
    private val wordUsage by lazy { getSharedPreferences("word_usage", Context.MODE_PRIVATE) }
    private val hiddenWords by lazy { getSharedPreferences("hidden_words", Context.MODE_PRIVATE) }
    private val keyboardSettings by lazy { getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE) }
    private val commitManual = Runnable { if (pendingManual.isNotEmpty()) { currentInputConnection?.commitText(pendingManual, 1); pendingManual = ""; lastKey = null } }
    private val repeatBackspace = object : Runnable { override fun run() { if (!backspaceHeld) return; press('⌫'); handler.postDelayed(this, 65) } }

    override fun onCreate() { super.onCreate(); val britishAndCommon = listOf("colour", "colours", "favourite", "favourites", "flavour", "flavours", "honour", "honours", "centre", "centres", "theatre", "theatres", "metre", "metres", "organise", "organised", "organising", "realise", "realised", "realising", "recognise", "recognised", "recognising", "travelling", "travelled", "cancelled", "cheque", "grey", "mum", "mate", "cheers", "yeah", "yep", "nope", "gonna", "wanna", "im", "i'm", "dont", "don't", "cant", "can't", "wont", "won't", "ive", "i've", "youre", "you're", "theyre", "they're", "we're", "weve", "we've", "lol", "omg", "brb", "idk", "btw", "asap"); dictionary = (assets.open("words.txt").bufferedReader().readLines().map { it.trim().lowercase(Locale.US) }.filter { it.matches(Regex("[a-z]+(?:'[a-z]+)*")) } + britishAndCommon).distinct(); indexedDictionary = dictionary.map { it to encode(it) } }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) { super.onStartInputView(info, restarting); val inputClass = info.inputType and InputType.TYPE_MASK_CLASS; numericMode = inputClass == InputType.TYPE_CLASS_NUMBER || inputClass == InputType.TYPE_CLASS_PHONE || inputClass == InputType.TYPE_CLASS_DATETIME; setInputView(onCreateInputView()) }

    override fun onCreateInputView(): View {
        if (numericMode) return createNumericView()
        if (punctuationMode) return createPunctuationView()
        if (manual) return createAlphabeticView()
        return keyboardContainer().apply {
            addSuggestionStrip()
            if (showNumberRow()) addKeyboardRow(numberRow())
            if (compactLayout()) {
                addKeyboardRow(compactT9Row(listOf("qwe" to '1', "rtyu" to '2', "iop" to '3')))
                addKeyboardRow(compactT9Row(listOf("asd" to '4', "fgh" to '5', "jkl" to '6')))
                addKeyboardRow(compactT9Row(listOf("zx" to '7', "cvb" to '8', "nm" to '9')))
                addKeyboardRow(compactControlsRow())
            } else {
                addKeyboardRow(groupRow(listOf("qwe" to ('1' to 3f), "rtyu" to ('2' to 4f), "iop" to ('3' to 3f))))
                addKeyboardRow(groupRow(listOf("asd" to ('4' to 3f), "fgh" to ('5' to 3f), "jkl" to ('6' to 3f))))
                addKeyboardRow(groupRow(listOf("zx" to ('7' to 2f), "cvb" to ('8' to 3f), "nm" to ('9' to 2f)), controls = true))
            }
            addKeyboardRow(bottomRow())
            refresh()
        }
    }
    private fun LinearLayout.addSuggestionStrip() {
        status = TextView(context).apply { setTextColor(if (darkKeyboard()) Color.LTGRAY else Color.DKGRAY); textSize = 12f; gravity = Gravity.CENTER; text = "Suggestions" }
        suggestions = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        addKeyboardRow(LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; addView(status, LinearLayout.LayoutParams(-1, dp(14))); addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(suggestions) }, LinearLayout.LayoutParams(-1, dp(32))) }, dp(46))
    }

    private fun createNumericView(): View = keyboardContainer().apply {
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("-", "0", "⌫"))
        rows.forEach { row -> addKeyboardRow(LinearLayout(this@T9InputMethodService).apply { orientation = LinearLayout.HORIZONTAL; row.forEach { mark -> addView(if (mark == "⌫") key(mark, '⌫') else Button(this@T9InputMethodService).apply { text = mark; textSize = 19f; isAllCaps = false; setOnClickListener { currentInputConnection?.commitText(mark, 1) }; styleButton(this) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }) } }) }
        addKeyboardRow(LinearLayout(this@T9InputMethodService).apply { orientation = LinearLayout.HORIZONTAL; addView(key(".", '.'), LinearLayout.LayoutParams(0, dp(32), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }); addView(key("↵", '↵'), LinearLayout.LayoutParams(0, dp(32), 2f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }) })
    }

    private fun numberRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        (1..9).map { it.toString() }.plus("0").forEach { digit -> addView(Button(this@T9InputMethodService).apply { text = digit; textSize = 15f; isAllCaps = false; minHeight = 0; minimumHeight = 0; setPadding(0, 0, 0, 0); setOnClickListener { commitCurrent(); currentInputConnection?.commitText(digit, 1) }; styleButton(this) }, LinearLayout.LayoutParams(0, dp(30), 1f)) }
    }
    private fun groupRow(groups: List<Pair<String, Pair<Char, Float>>>, controls: Boolean = false) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        if (controls) addView(key("⇧", 'U'), LinearLayout.LayoutParams(0, dp(29), 1.2f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
        groups.forEach { (label, spec) -> addView(groupKey(label, spec.first, !showNumberRow(), if (!showNumberRow()) spec.first else null), LinearLayout.LayoutParams(0, dp(29), spec.second).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) }) }
        if (controls) addView(key("⌫", '⌫'), LinearLayout.LayoutParams(0, dp(29), 1.2f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
    }
    private fun compactT9Row(keys: List<Pair<String, Char>>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        keys.forEach { (letters, number) -> addView(groupKey(letters, number, !showNumberRow(), if (!showNumberRow()) number else null), LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }) }
    }
    private fun compactControlsRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        listOf("⇧" to 'U', "⌫" to '⌫').forEach { (label, action) -> addView(key(label, action), LinearLayout.LayoutParams(0, dp(30), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }) }
    }
    private fun bottomRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        val margin = dp(2)
        addView(punctuationKey(), LinearLayout.LayoutParams(0, dp(27), 1.25f).apply { setMargins(margin, margin, margin, margin) })
        addView(spaceKey(), LinearLayout.LayoutParams(0, dp(27), 6f).apply { setMargins(margin, margin, margin, margin) })
        addView(key("↵", '↵'), LinearLayout.LayoutParams(0, dp(27), 1.25f).apply { setMargins(margin, margin, margin, margin) })
    }
    private fun spaceKey() = Button(this).apply { text = "space"; textSize = 13f; typeface = Typeface.MONOSPACE; isAllCaps = false; minHeight = 0; minimumHeight = 0; setPadding(0, 0, 0, 0); var startX = 0f; setOnTouchListener { _, event -> when (event.action) { MotionEvent.ACTION_DOWN -> { startX = event.x; isPressed = true }; MotionEvent.ACTION_UP -> { isPressed = false; if (kotlin.math.abs(event.x - startX) >= dp(36)) toggleKeyboardMode() else press(' ') }; MotionEvent.ACTION_CANCEL -> isPressed = false }; true }; styleButton(this) }
    private fun punctuationKey() = Button(this).apply { text = "."; textSize = 14f; isAllCaps = false; minHeight = 0; minimumHeight = 0; setPadding(0, 0, 0, 0); var holding = false; val hold = Runnable { holding = true; showPunctuationGesture() }; setOnTouchListener { _, event -> when (event.action) { MotionEvent.ACTION_DOWN -> { holding = false; postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong()); isPressed = true }; MotionEvent.ACTION_MOVE -> if (holding) updatePunctuationHighlight(event.rawX); MotionEvent.ACTION_UP -> { removeCallbacks(hold); isPressed = false; if (holding) chooseHeldPunctuation(event.rawX) else handlePunctuationTap() }; MotionEvent.ACTION_CANCEL -> { removeCallbacks(hold); punctuationGestureActive = false; refresh(); isPressed = false } }; true }; styleButton(this) }
    private fun handlePunctuationTap() { val now = System.currentTimeMillis(); if (now - lastPunctuationTap < 350) { currentInputConnection?.deleteSurroundingText(1, 0); punctuationMode = true; setInputView(onCreateInputView()) } else press('.'); lastPunctuationTap = now }
    private fun showPunctuationGesture() {
        val marks = listOf(",", "?", "!", "'", "\"", ":", ";", "-", "(", ")")
        punctuationGestureActive = true
        status?.text = "Slide to a symbol, then release"
        suggestions?.removeAllViews()
        punctuationMenuButtons = marks.map { mark ->
            Button(this).apply {
                text = mark; textSize = 16f; isAllCaps = false; setTextColor(Color.WHITE)
                background = GradientDrawable().apply { setColor(Color.rgb(62, 68, 80)); cornerRadius = dp(12).toFloat() }
                suggestions?.addView(this, LinearLayout.LayoutParams(0, dp(32), 1f).apply { setMargins(dp(1), 0, dp(1), 0) })
            }
        }
    }
    private fun punctuationIndex(rawX: Float) = ((rawX / resources.displayMetrics.widthPixels * punctuationMenuButtons.size).toInt()).coerceIn(0, punctuationMenuButtons.lastIndex)
    private fun updatePunctuationHighlight(rawX: Float) { val selected = punctuationIndex(rawX); punctuationMenuButtons.forEachIndexed { index, button -> button.background = GradientDrawable().apply { setColor(if (index == selected) Color.rgb(100, 130, 190) else Color.rgb(62, 68, 80)); cornerRadius = dp(12).toFloat() } } }
    private fun chooseHeldPunctuation(rawX: Float) { val marks = listOf(",", "?", "!", "'", "\"", ":", ";", "-", "(", ")"); val mark = marks[punctuationIndex(rawX)]; punctuationGestureActive = false; commitCurrent(); commitPunctuation(mark) }
    private fun groupKey(letters: String, action: Char, numberOnLongPress: Boolean = false, numberHint: Char? = null) = LetterGroupButton(if (shifted) letters.uppercase(Locale.US) else letters, numberHint, if (numberOnLongPress) ({ commitCurrent(); currentInputConnection?.commitText((numberHint ?: action).toString(), 1) }) else null).apply { setOnClickListener { press(action) } }
    private fun key(label: String, action: Char) = Button(this).apply { text = label; textSize = if (label.length > 5) 13f else 12f; typeface = Typeface.MONOSPACE; isAllCaps = false; minHeight = 0; minimumHeight = 0; setPadding(0, 0, 0, 0); if (action == '⌫') { setOnTouchListener { _, event -> when (event.action) { MotionEvent.ACTION_DOWN -> { backspaceHeld = true; press('⌫'); handler.postDelayed(repeatBackspace, 350) }; MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { backspaceHeld = false; handler.removeCallbacks(repeatBackspace) } }; true } } else { setOnClickListener { press(action) }; if (action == '↵') setOnLongClickListener { startActivity(android.content.Intent(this@T9InputMethodService, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); true } }; styleButton(this) }
    private inner class LetterGroupButton(private val letters: String, private val numberHint: Char? = null, private val onNumberLongPress: (() -> Unit)? = null) : View(this@T9InputMethodService) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (darkKeyboard()) Color.WHITE else Color.rgb(38, 50, 56); textSize = dp(14).toFloat(); typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL); textAlign = Paint.Align.CENTER }
        private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (darkKeyboard()) Color.rgb(180, 190, 205) else Color.rgb(135, 145, 155); textSize = dp(9).toFloat(); typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL); textAlign = Paint.Align.LEFT }
        private var longPressTriggered = false
        private val longPress = Runnable { if (onNumberLongPress != null) { onNumberLongPress.invoke(); longPressTriggered = true } }
        init { isClickable = true; isLongClickable = onNumberLongPress != null; background = GradientDrawable().apply { setColor(if (darkKeyboard()) Color.rgb(55, 60, 70) else Color.rgb(238, 241, 243)); cornerRadius = dp(3).toFloat() }; contentDescription = letters; setOnTouchListener { _, event -> when (event.action) { MotionEvent.ACTION_DOWN -> { longPressTriggered = false; isPressed = true; if (onNumberLongPress != null) postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong()) }; MotionEvent.ACTION_UP -> { removeCallbacks(longPress); isPressed = false; if (!longPressTriggered) performClick() }; MotionEvent.ACTION_CANCEL -> { removeCallbacks(longPress); isPressed = false } }; true } }
        override fun performClick(): Boolean { super.performClick(); return true }
        override fun onDraw(canvas: Canvas) { super.onDraw(canvas); val baseline = height / 2f - (paint.ascent() + paint.descent()) / 2f; numberHint?.let { canvas.drawText(it.toString(), dp(5).toFloat(), dp(12).toFloat(), hintPaint) }; val start = if (numberHint == null) 0f else width * 0.08f; val usable = width - start; letters.forEachIndexed { index, letter -> canvas.drawText(letter.toString(), start + usable * (index + 1f) / (letters.length + 1f), baseline, paint) } }
    }
    private fun createPunctuationView(): View = keyboardContainer().apply {
        listOf(listOf("1","2","3","4","5","6","7","8","9","0"), listOf("!","@","#","$","%","^","&","*","(",")"), listOf("-","_","=","+","[","]","{","}","\\","|"), listOf(";",":","'","\"",",",".","?","/")) .forEach { marks -> addKeyboardRow(punctuationRow(marks)) }
        val bottom = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(key("ABC", 'A'), LinearLayout.LayoutParams(0, dp(30), 1.4f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) }); bottom.addView(key("space", ' '), LinearLayout.LayoutParams(0, dp(30), 5f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) }); bottom.addView(key("⌫", '⌫'), LinearLayout.LayoutParams(0, dp(30), 1.4f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) }); bottom.addView(key("↵", '↵'), LinearLayout.LayoutParams(0, dp(30), 1.4f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) }); addKeyboardRow(bottom)
    }
    private fun createAlphabeticView(): View = keyboardContainer().apply {
        addSuggestionStrip()
        if (showNumberRow()) addKeyboardRow(numberRow())
        addKeyboardRow(normalLetterRow("qwertyuiop", showNumberHints = !showNumberRow()))
        addKeyboardRow(normalLetterRow("asdfghjkl"))
        val last = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        last.addView(key("⇧", 'U'), LinearLayout.LayoutParams(0, dp(29), 1.2f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) })
        "zxcvbnm".forEach { letter -> last.addView(groupKey(letter.toString(), letter), LinearLayout.LayoutParams(0, dp(29), 1f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) }) }
        last.addView(key("⌫", '⌫'), LinearLayout.LayoutParams(0, dp(29), 1.2f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) })
        addKeyboardRow(last); addKeyboardRow(bottomRow())
    }
    private fun normalLetterRow(letters: String, showNumberHints: Boolean = false) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; letters.forEachIndexed { index, letter -> val number = if (showNumberHints) (index + 1).toString().last() else null; addView(groupKey(letter.toString(), letter, showNumberHints, number), LinearLayout.LayoutParams(0, dp(29), 1f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) }) } }
    private fun punctuationRow(marks: List<String>) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; marks.forEach { mark -> addView(Button(this@T9InputMethodService).apply { text = mark; textSize = 16f; typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER; isAllCaps = false; minHeight = 0; minimumHeight = 0; setPadding(0, 0, 0, 0); setOnClickListener { commitPunctuation(mark) }; styleButton(this) }, LinearLayout.LayoutParams(0, dp(30), 1f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) }) } }
    private fun dp(value: Int) = (value * resources.displayMetrics.density * (keyboardSettings.getInt("keyboard_scale", 100) / 100f)).roundToInt().coerceAtLeast(1)
    private fun showNumberRow() = keyboardSettings.getBoolean("show_number_row", true)
    private fun compactLayout() = keyboardSettings.getBoolean("compact_layout", false)
    private fun keyboardRowWidth() = if (compactLayout()) (resources.displayMetrics.widthPixels * 0.82f).roundToInt() else -1
    private fun LinearLayout.addKeyboardRow(view: View, height: Int = -2) = addView(view, LinearLayout.LayoutParams(keyboardRowWidth(), height))
    private fun keyboardContainer() = KeyboardContainer()
    private inner class KeyboardContainer : LinearLayout(this@T9InputMethodService) {
        private val sidePadding = dp(6)
        init { orientation = VERTICAL; gravity = when (keyboardSettings.getString("compact_alignment", "center")) { "lhs" -> Gravity.START; "rhs" -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL }; setBackgroundColor(Color.rgb(24, 28, 38)); setPadding(sidePadding, sidePadding, sidePadding, sidePadding); requestApplyInsets() }
        override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets { val navigationBottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) insets.getInsets(WindowInsets.Type.navigationBars()).bottom else insets.systemWindowInsetBottom; setPadding(sidePadding, sidePadding, sidePadding, sidePadding + navigationBottom); return insets }
    }
    private fun darkKeyboard() = keyboardSettings.getBoolean("dark_keyboard", false)
    private fun styleButton(button: Button) { button.setTextColor(if (darkKeyboard()) Color.WHITE else Color.rgb(25, 25, 25)); button.background = GradientDrawable().apply { setColor(if (darkKeyboard()) Color.rgb(55, 60, 70) else Color.rgb(238, 241, 243)); cornerRadius = dp(18).toFloat() } }

    private fun press(key: Char) {
        clearWordSuggestions()
        when (key) {
            in '1'..'9' -> if (manual) manualPress(key) else { clearWordSuggestions(); pattern += key; refresh() }
            '⌫' -> { lastDirectKeyAt = System.currentTimeMillis(); val selected = currentInputConnection?.getSelectedText(0)?.length ?: 0; if (selected > 0) { currentInputConnection?.commitText("", 1); standardPrediction = emptyList(); clearWordSuggestions(); refresh() } else if (manual) { handler.removeCallbacks(commitManual); if (pendingManual.isNotEmpty()) pendingManual = "" else currentInputConnection?.deleteSurroundingText(1, 0); updateStandardPredictions() } else if (pattern.isNotEmpty()) { pattern = pattern.dropLast(1); refresh() } else currentInputConnection?.deleteSurroundingText(1, 0) }
            'L' -> toggleKeyboardMode()
            ' ' -> { commitCurrent(); standardPrediction = emptyList(); insertSpaceOrPeriod(); lastCommitWasWord = false; refresh() }
            '↵' -> { commitCurrent(); currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER)); lastCommitWasWord = false }
            '.' -> { commitCurrent(); commitPunctuation(".") }
            'A' -> { punctuationMode = false; setInputView(onCreateInputView()) }
            'U' -> { shifted = !shifted; setInputView(onCreateInputView()) }
            in 'a'..'z' -> { lastDirectKeyAt = System.currentTimeMillis(); currentInputConnection?.commitText(outputWord(key.toString()), 1); lastCommitWasWord = true; updateStandardPredictions() }
        }
    }
    private fun manualPress(key: Char) {
        val letters = groups[key] ?: return; handler.removeCallbacks(commitManual)
        if (lastKey == key) tapIndex = (tapIndex + 1) % letters.length else { if (pendingManual.isNotEmpty()) currentInputConnection?.commitText(pendingManual, 1); tapIndex = 0 }
        val letter = letters[tapIndex].toString()
        pendingManual = if (pendingManual.isEmpty()) outputWord(letter) else letter; lastKey = key; handler.postDelayed(commitManual, 700); refresh()
    }
    private fun toggleKeyboardMode() { handler.removeCallbacks(commitManual); pattern = ""; clearWordSuggestions(); if (pendingManual.isNotEmpty()) currentInputConnection?.commitText(pendingManual, 1); pendingManual = ""; manual = !manual; lastKey = null; setInputView(onCreateInputView()) }
    private fun shouldAutoCapitalize(): Boolean { if (!keyboardSettings.getBoolean("auto_capitalize", true)) return false; val before = currentInputConnection?.getTextBeforeCursor(256, 0)?.toString().orEmpty(); val last = before.trimEnd().lastOrNull(); return last == null || last == '.' || last == '!' || last == '?' }
    private fun outputWord(word: String) = if (shifted || shouldAutoCapitalize()) word.replaceFirstChar { it.uppercase(Locale.US) } else word
    private fun insertSpaceOrPeriod() { val before = currentInputConnection?.getTextBeforeCursor(256, 0)?.toString().orEmpty(); val beforeSpace = before.dropLastWhile { it.isWhitespace() }.lastOrNull(); if (keyboardSettings.getBoolean("double_space_period", true) && before.endsWith(" ") && beforeSpace?.isLetterOrDigit() == true) { currentInputConnection?.deleteSurroundingText(1, 0); currentInputConnection?.commitText(". ", 1) } else currentInputConnection?.commitText(" ", 1) }
    private fun commitCurrent() { if (manual) { handler.removeCallbacks(commitManual); if (pendingManual.isNotEmpty()) currentInputConnection?.commitText(pendingManual, 1); pendingManual = "" } else if (pattern.isNotEmpty()) { val word = matches().firstOrNull(); if (word != null) { currentInputConnection?.commitText(outputWord(word), 1); recordUsage(word); lastCommitWasWord = true }; pattern = ""; clearWordSuggestions() }; refresh() }
    private fun matches(): List<String> {
        val exact = indexedDictionary.filter { it.second == pattern }
        val candidates = (if (exact.isNotEmpty()) exact else indexedDictionary.filter { it.second.startsWith(pattern) }).filterNot { hiddenWords.getBoolean(it.first, false) }
        val result = candidates.sortedWith(compareByDescending<Pair<String, String>> { usageScore(it.first) }.thenBy { it.first.length }.thenBy { it.first }).take(40).map { it.first }
        if (result.isNotEmpty()) return result
        val close = indexedDictionary.asSequence().filter { !hiddenWords.getBoolean(it.first, false) && kotlin.math.abs(it.second.length - pattern.length) <= 1 }.map { it.first to codeDistance(it.second, pattern) }.filter { it.second <= maxOf(1, pattern.length / 3) }.sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { kotlin.math.abs(it.first.length - pattern.length) }.thenByDescending { usageScore(it.first) }).take(12).map { it.first }.toList()
        return if (close.isNotEmpty()) close else dictionary.filterNot { hiddenWords.getBoolean(it, false) }.sortedWith(compareByDescending<String> { usageScore(it) }.thenBy { it.length }).take(8)
    }
    private fun encode(word: String) = word.mapNotNull { wordDigits[it] }.joinToString("")
    private fun codeDistance(left: String, right: String): Int { var previous = IntArray(right.length + 1) { it }; left.forEachIndexed { i, l -> val current = IntArray(right.length + 1); current[0] = i + 1; right.forEachIndexed { j, r -> current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (l == r) 0 else 1) }; previous = current }; return previous[right.length] }
    private fun usageScore(word: String) = commonWordRank.getOrDefault(word, 0) + wordUsage.getInt(word, 0)
    private fun recordUsage(word: String) { wordUsage.edit().putInt(word, wordUsage.getInt(word, 0) + 1).apply() }
    private fun clearWordSuggestions() { wordSuggestions = emptyList(); replacementBefore = 0; replacementAfter = 0; suppressWordSuggestionsUntil = System.currentTimeMillis() + 250 }
    private fun commitSuggestedWord(word: String) { val before = currentInputConnection?.getTextBeforeCursor(64, 0)?.toString().orEmpty(); if (lastCommitWasWord || before.lastOrNull()?.isLetterOrDigit() == true) currentInputConnection?.commitText(" ", 1); currentInputConnection?.commitText(outputWord(word), 1); val autoSpace = keyboardSettings.getBoolean("auto_space_suggestions", true); if (autoSpace) currentInputConnection?.commitText(" ", 1); recordUsage(word); lastCommitWasWord = !autoSpace }
    private fun replaceCurrentWord(word: String) { val before = replacementBefore; val after = replacementAfter; clearWordSuggestions(); currentInputConnection?.deleteSurroundingText(before, after); currentInputConnection?.commitText(outputWord(word), 1); recordUsage(word); lastCommitWasWord = true; refresh() }
    private fun codeMatches(word: String): List<String> { val code = encode(word); return indexedDictionary.filter { it.second == code && it.first != word && !hiddenWords.getBoolean(it.first, false) }.sortedWith(compareByDescending<Pair<String, String>> { usageScore(it.first) }.thenBy { it.first }).take(40).map { it.first } }
    private fun updateStandardPredictions() { if (!manual || punctuationMode) return; val before = currentInputConnection?.getTextBeforeCursor(64, 0)?.toString().orEmpty(); val prefix = before.takeLastWhile { it.isLetter() || it == '\'' }.lowercase(Locale.US); if (prefix.length < 1) { standardPrediction = emptyList(); standardReplaceBefore = 0; refresh(); return }; standardReplaceBefore = prefix.length; val prefixMatches = dictionary.filter { it.startsWith(prefix) && it != prefix && !hiddenWords.getBoolean(it, false) }.sortedWith(compareByDescending<String> { usageScore(it) }.thenBy { it.length }.thenBy { it }).take(10); standardPrediction = if (prefixMatches.isNotEmpty()) prefixMatches else dictionary.filter { it.firstOrNull() == prefix.first() && kotlin.math.abs(it.length - prefix.length) <= 2 && levenshtein(it, prefix) <= maxOf(1, prefix.length / 3) && !hiddenWords.getBoolean(it, false) }.sortedWith(compareByDescending<String> { usageScore(it) }.thenBy { levenshtein(it, prefix) }.thenBy { kotlin.math.abs(it.length - prefix.length) }.thenBy { it }).take(8); refresh() }
    private fun levenshtein(left: String, right: String): Int { if (left == right) return 0; if (left.length > 18 || right.length > 18) return 99; var previous = IntArray(right.length + 1) { it }; left.forEachIndexed { i, l -> val current = IntArray(right.length + 1); current[0] = i + 1; right.forEachIndexed { j, r -> current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (l == r) 0 else 1) }; previous = current }; return previous[right.length] }
    private fun commitStandardPrediction(word: String) { val replace = standardReplaceBefore; standardPrediction = emptyList(); standardReplaceBefore = 0; currentInputConnection?.deleteSurroundingText(replace, 0); currentInputConnection?.commitText(outputWord(word), 1); recordUsage(word); lastCommitWasWord = true; refresh() }
    private fun showCodeMatchesAtCursor() { if (System.currentTimeMillis() < suppressWordSuggestionsUntil || System.currentTimeMillis() - lastDirectKeyAt < 350 || punctuationMode || pattern.isNotEmpty()) return; val connection = currentInputConnection ?: return; val selected = connection.getSelectedText(0)?.toString().orEmpty(); val before = connection.getTextBeforeCursor(64, 0)?.toString().orEmpty(); val after = connection.getTextAfterCursor(64, 0)?.toString().orEmpty(); val left = if (selected.isNotEmpty()) "" else before.takeLastWhile { it.isLetter() || it == '\'' }; val right = if (selected.isNotEmpty()) "" else after.takeWhile { it.isLetter() || it == '\'' }; val word = (if (selected.isNotEmpty()) selected else left + right).lowercase(Locale.US); if (!word.matches(Regex("[a-z]+(?:'[a-z]+)*"))) return; standardPrediction = emptyList(); replacementBefore = if (selected.isNotEmpty()) 0 else left.length; replacementAfter = if (selected.isNotEmpty()) 0 else right.length; wordSuggestions = codeMatches(word); refresh() }
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) { super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd); showCodeMatchesAtCursor() }
    private fun commitPunctuation(mark: String) { val before = currentInputConnection?.getTextBeforeCursor(64, 0)?.toString().orEmpty(); if (before.endsWith(" ")) currentInputConnection?.deleteSurroundingText(1, 0); currentInputConnection?.commitText("$mark ", 1); lastCommitWasWord = false; standardPrediction = emptyList(); refresh() }
    private fun refresh() { if (punctuationGestureActive) return; val words = if (!manual && pattern.isNotEmpty()) matches() else if (manual && standardPrediction.isNotEmpty()) standardPrediction else wordSuggestions; status?.text = when { !manual && pattern.isNotEmpty() -> "T9 suggestions"; manual && standardPrediction.isNotEmpty() -> "Predictions"; wordSuggestions.isNotEmpty() -> "Same T9 code"; else -> "Suggestions" }; suggestions?.removeAllViews(); words.forEach { word -> suggestions?.addView(Button(this).apply { text = outputWord(word); textSize = 15f; isAllCaps = false; isSingleLine = true; ellipsize = TextUtils.TruncateAt.END; minimumWidth = 0; minWidth = 0; setPadding(dp(14), 0, dp(14), 0); setOnClickListener { if (pattern.isNotEmpty()) { commitSuggestedWord(word); pattern = ""; clearWordSuggestions(); refresh() } else if (manual && standardPrediction.isNotEmpty()) commitStandardPrediction(word) else replaceCurrentWord(word) }; setOnLongClickListener { hiddenWords.edit().putBoolean(word, true).apply(); wordSuggestions = wordSuggestions.filterNot { it == word }; standardPrediction = standardPrediction.filterNot { it == word }; Toast.makeText(this@T9InputMethodService, "Removed $word", Toast.LENGTH_SHORT).show(); refresh(); true }; styleButton(this) }, LinearLayout.LayoutParams(-2, -1).apply { setMargins(dp(2), 0, dp(2), 0) }) } }
}
