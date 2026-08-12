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
import android.view.inputmethod.EditorInfo
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
    private var lastSpaceTap = 0L
    private var spaceTapCount = 0
    private var wordSuggestions = emptyList<String>()
    private var replacementBefore = 0
    private var replacementAfter = 0
    private var suppressWordSuggestionsUntil = 0L
    private val commonWordRank = mapOf("to" to 100)
    private val wordUsage by lazy { getSharedPreferences("word_usage", Context.MODE_PRIVATE) }
    private val hiddenWords by lazy { getSharedPreferences("hidden_words", Context.MODE_PRIVATE) }
    private val keyboardSettings by lazy { getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE) }
    private val commitManual = Runnable { if (pendingManual.isNotEmpty()) { currentInputConnection?.commitText(pendingManual, 1); pendingManual = ""; lastKey = null } }
    private val repeatBackspace = object : Runnable { override fun run() { if (!backspaceHeld) return; press('⌫'); handler.postDelayed(this, 65) } }

    override fun onCreate() { super.onCreate(); dictionary = assets.open("words.txt").bufferedReader().readLines().map { it.trim().lowercase(Locale.US) }.filter { it.matches(Regex("[a-z]+(?:'[a-z]+)*")) }.distinct(); indexedDictionary = dictionary.map { it to encode(it) } }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) { super.onStartInputView(info, restarting); setInputView(onCreateInputView()) }

    override fun onCreateInputView(): View {
        if (punctuationMode) return createPunctuationView()
        if (manual) return createAlphabeticView()
        return keyboardContainer().apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(24, 28, 38)); setPadding(dp(6), dp(6), dp(6), dp(6))
            status = TextView(context).apply { setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER; text = "" }
            suggestions = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
            addKeyboardRow(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(suggestions) }, dp(46))
            if (showNumberRow()) addKeyboardRow(numberRow())
            addKeyboardRow(groupRow(listOf("qwe" to ('1' to 3f), "rtyu" to ('2' to 4f), "iop" to ('3' to 3f))))
            addKeyboardRow(groupRow(listOf("asd" to ('4' to 3f), "fgh" to ('5' to 3f), "jkl" to ('6' to 3f))))
            addKeyboardRow(groupRow(listOf("zx" to ('7' to 2f), "cvb" to ('8' to 3f), "nm" to ('9' to 2f)), controls = true))
            addKeyboardRow(bottomRow())
            refresh()
        }
    }

    private fun numberRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        (1..9).map { it.toString() }.plus("0").forEach { digit -> addView(Button(this@T9InputMethodService).apply { text = digit; textSize = 15f; isAllCaps = false; minHeight = 0; minimumHeight = 0; setPadding(0, 0, 0, 0); setOnClickListener { commitCurrent(); currentInputConnection?.commitText(digit, 1) }; styleButton(this) }, LinearLayout.LayoutParams(0, dp(30), 1f)) }
    }
    private fun groupRow(groups: List<Pair<String, Pair<Char, Float>>>, controls: Boolean = false) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        if (controls) addView(key("⇧", 'U'), LinearLayout.LayoutParams(0, dp(29), 1.2f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
        groups.forEach { (label, spec) -> addView(groupKey(label, spec.first), LinearLayout.LayoutParams(0, dp(29), spec.second).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) }) }
        if (controls) addView(key("⌫", '⌫'), LinearLayout.LayoutParams(0, dp(29), 1.2f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
    }
    private fun bottomRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        val margin = dp(2)
        addView(punctuationKey(), LinearLayout.LayoutParams(0, dp(27), 1.25f).apply { setMargins(margin, margin, margin, margin) })
        addView(key("space", ' '), LinearLayout.LayoutParams(0, dp(27), 6f).apply { setMargins(margin, margin, margin, margin) })
        addView(key("↵", '↵'), LinearLayout.LayoutParams(0, dp(27), 1.25f).apply { setMargins(margin, margin, margin, margin) })
    }
    private fun punctuationKey() = Button(this).apply { text = "."; textSize = 14f; isAllCaps = false; minHeight = 0; minimumHeight = 0; setPadding(0, 0, 0, 0); setOnClickListener { val now = System.currentTimeMillis(); if (now - lastPunctuationTap < 350) { currentInputConnection?.deleteSurroundingText(1, 0); punctuationMode = true; setInputView(onCreateInputView()) } else press('.'); lastPunctuationTap = now }; setOnLongClickListener { showPunctuationMenu(this); true }; styleButton(this) }
    private fun showPunctuationMenu(anchor: View) {
        val menu = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.DKGRAY); setPadding(dp(4), dp(4), dp(4), dp(4)) }
        val popup = PopupWindow(menu, -2, -2, true).apply { elevation = dp(6).toFloat() }
        listOf(",", "?", "!", "'", "\"", ":", ";", "-", "(", ")").forEach { mark -> menu.addView(Button(this).apply { text = mark; textSize = 16f; setOnClickListener { commitCurrent(); currentInputConnection?.commitText(mark, 1); popup.dismiss() } }, LinearLayout.LayoutParams(dp(42), dp(42))) }
        popup.showAsDropDown(anchor, -dp(180), -dp(82))
    }
    private fun groupKey(letters: String, action: Char) = LetterGroupButton(if (shifted) letters.uppercase(Locale.US) else letters).apply { setOnClickListener { press(action) } }
    private fun key(label: String, action: Char) = Button(this).apply { text = label; textSize = if (label.length > 5) 13f else 12f; typeface = Typeface.MONOSPACE; isAllCaps = false; minHeight = 0; minimumHeight = 0; setPadding(0, 0, 0, 0); if (action == '⌫') { setOnTouchListener { _, event -> when (event.action) { MotionEvent.ACTION_DOWN -> { backspaceHeld = true; press('⌫'); handler.postDelayed(repeatBackspace, 350) }; MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { backspaceHeld = false; handler.removeCallbacks(repeatBackspace) } }; true } } else { setOnClickListener { if (action == ' ') onSpaceTapped() else press(action) }; if (action == '↵') setOnLongClickListener { startActivity(android.content.Intent(this@T9InputMethodService, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); true } }; styleButton(this) }
    private inner class LetterGroupButton(private val letters: String) : View(this@T9InputMethodService) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (darkKeyboard()) Color.WHITE else Color.rgb(38, 50, 56); textSize = dp(14).toFloat(); typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL); textAlign = Paint.Align.CENTER }
        init { isClickable = true; background = GradientDrawable().apply { setColor(if (darkKeyboard()) Color.rgb(55, 60, 70) else Color.rgb(238, 241, 243)); cornerRadius = dp(3).toFloat() }; contentDescription = letters; setOnTouchListener { _, event -> if (event.action == MotionEvent.ACTION_UP) performClick(); true } }
        override fun performClick(): Boolean { super.performClick(); return true }
        override fun onDraw(canvas: Canvas) { super.onDraw(canvas); val baseline = height / 2f - (paint.ascent() + paint.descent()) / 2f; letters.forEachIndexed { index, letter -> canvas.drawText(letter.toString(), width * (index + 1f) / (letters.length + 1f), baseline, paint) } }
    }
    private fun createPunctuationView(): View = keyboardContainer().apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(24, 28, 38)); setPadding(dp(6), dp(6), dp(6), dp(6))
        listOf(listOf("1","2","3","4","5","6","7","8","9","0"), listOf("!","@","#","$","%","^","&","*","(",")"), listOf("-","_","=","+","[","]","{","}","\\","|"), listOf(";",":","'","\"",",",".","?","/")) .forEach { marks -> addKeyboardRow(punctuationRow(marks)) }
        val bottom = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(key("ABC", 'A'), LinearLayout.LayoutParams(0, dp(30), 1.4f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) }); bottom.addView(key("space", ' '), LinearLayout.LayoutParams(0, dp(30), 5f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) }); bottom.addView(key("⌫", '⌫'), LinearLayout.LayoutParams(0, dp(30), 1.4f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) }); bottom.addView(key("↵", '↵'), LinearLayout.LayoutParams(0, dp(30), 1.4f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) }); addKeyboardRow(bottom)
    }
    private fun createAlphabeticView(): View = keyboardContainer().apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(24, 28, 38)); setPadding(dp(6), dp(6), dp(6), dp(6))
        addKeyboardRow(View(context), dp(46))
        if (showNumberRow()) addKeyboardRow(numberRow())
        addKeyboardRow(normalLetterRow("qwertyuiop"))
        addKeyboardRow(normalLetterRow("asdfghjkl"))
        val last = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        last.addView(key("⇧", 'U'), LinearLayout.LayoutParams(0, dp(29), 1.2f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) })
        "zxcvbnm".forEach { letter -> last.addView(groupKey(letter.toString(), letter), LinearLayout.LayoutParams(0, dp(29), 1f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) }) }
        last.addView(key("⌫", '⌫'), LinearLayout.LayoutParams(0, dp(29), 1.2f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) })
        addKeyboardRow(last); addKeyboardRow(bottomRow())
    }
    private fun normalLetterRow(letters: String) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; letters.forEach { letter -> addView(groupKey(letter.toString(), letter), LinearLayout.LayoutParams(0, dp(29), 1f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) }) } }
    private fun punctuationRow(marks: List<String>) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; marks.forEach { mark -> addView(Button(this@T9InputMethodService).apply { text = mark; textSize = 16f; typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER; isAllCaps = false; minHeight = 0; minimumHeight = 0; setPadding(0, 0, 0, 0); setOnClickListener { currentInputConnection?.commitText(mark, 1); lastCommitWasWord = false }; styleButton(this) }, LinearLayout.LayoutParams(0, dp(30), 1f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) }) } }
    private fun dp(value: Int) = (value * resources.displayMetrics.density * (keyboardSettings.getInt("keyboard_scale", 100) / 100f)).roundToInt().coerceAtLeast(1)
    private fun showNumberRow() = keyboardSettings.getBoolean("show_number_row", true)
    private fun compactLayout() = keyboardSettings.getBoolean("compact_layout", false)
    private fun keyboardRowWidth() = if (compactLayout()) (resources.displayMetrics.widthPixels * 0.82f).roundToInt() else -1
    private fun LinearLayout.addKeyboardRow(view: View, height: Int = -2) = addView(view, LinearLayout.LayoutParams(keyboardRowWidth(), height))
    private fun keyboardContainer() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = when (keyboardSettings.getString("compact_alignment", "center")) { "lhs" -> Gravity.START; "rhs" -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL }
        setBackgroundColor(Color.rgb(24, 28, 38))
        val sidePadding = dp(6)
        setPadding(sidePadding, sidePadding, sidePadding, sidePadding)
        setOnApplyWindowInsetsListener { view, insets ->
            val navigationBottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) insets.getInsets(WindowInsets.Type.navigationBars()).bottom else insets.systemWindowInsetBottom
            view.setPadding(sidePadding, sidePadding, sidePadding, sidePadding + navigationBottom)
            insets
        }
        requestApplyInsets()
    }
    private fun darkKeyboard() = keyboardSettings.getBoolean("dark_keyboard", false)
    private fun styleButton(button: Button) { button.setTextColor(if (darkKeyboard()) Color.WHITE else Color.rgb(25, 25, 25)); button.background = GradientDrawable().apply { setColor(if (darkKeyboard()) Color.rgb(55, 60, 70) else Color.rgb(238, 241, 243)); cornerRadius = dp(18).toFloat() } }

    private fun press(key: Char) {
        clearWordSuggestions()
        when (key) {
            in '1'..'9' -> if (manual) manualPress(key) else { clearWordSuggestions(); pattern += key; refresh() }
            '⌫' -> { if (manual) { handler.removeCallbacks(commitManual); if (pendingManual.isNotEmpty()) pendingManual = "" else currentInputConnection?.deleteSurroundingText(1, 0) } else if (pattern.isNotEmpty()) { pattern = pattern.dropLast(1); refresh() } else currentInputConnection?.deleteSurroundingText(1, 0) }
            'L' -> toggleKeyboardMode()
            ' ' -> { commitCurrent(); insertSpaceOrPeriod(); lastCommitWasWord = false }
            '↵' -> { commitCurrent(); currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER)); lastCommitWasWord = false }
            '.' -> { commitCurrent(); currentInputConnection?.commitText(".", 1); lastCommitWasWord = false }
            'A' -> { punctuationMode = false; setInputView(onCreateInputView()) }
            'U' -> { shifted = !shifted; setInputView(onCreateInputView()) }
            in 'a'..'z' -> { currentInputConnection?.commitText(outputWord(key.toString()), 1); lastCommitWasWord = true }
        }
    }
    private fun manualPress(key: Char) {
        val letters = groups[key] ?: return; handler.removeCallbacks(commitManual)
        if (lastKey == key) tapIndex = (tapIndex + 1) % letters.length else { if (pendingManual.isNotEmpty()) currentInputConnection?.commitText(pendingManual, 1); tapIndex = 0 }
        val letter = letters[tapIndex].toString()
        pendingManual = if (pendingManual.isEmpty()) outputWord(letter) else letter; lastKey = key; handler.postDelayed(commitManual, 700); refresh()
    }
    private fun onSpaceTapped() { val now = System.currentTimeMillis(); spaceTapCount = if (now - lastSpaceTap < 350) spaceTapCount + 1 else 1; lastSpaceTap = now; if (spaceTapCount == 3) { currentInputConnection?.deleteSurroundingText(2, 0); spaceTapCount = 0; toggleKeyboardMode() } else press(' ') }
    private fun toggleKeyboardMode() { handler.removeCallbacks(commitManual); pattern = ""; clearWordSuggestions(); if (pendingManual.isNotEmpty()) currentInputConnection?.commitText(pendingManual, 1); pendingManual = ""; manual = !manual; lastKey = null; setInputView(onCreateInputView()) }
    private fun shouldAutoCapitalize(): Boolean { if (!keyboardSettings.getBoolean("auto_capitalize", true)) return false; val before = currentInputConnection?.getTextBeforeCursor(256, 0)?.toString().orEmpty(); val last = before.trimEnd().lastOrNull(); return last == null || last == '.' || last == '!' || last == '?' }
    private fun outputWord(word: String) = if (shifted || shouldAutoCapitalize()) word.replaceFirstChar { it.uppercase(Locale.US) } else word
    private fun insertSpaceOrPeriod() { val before = currentInputConnection?.getTextBeforeCursor(256, 0)?.toString().orEmpty(); val beforeSpace = before.dropLastWhile { it.isWhitespace() }.lastOrNull(); if (keyboardSettings.getBoolean("double_space_period", true) && before.endsWith(" ") && beforeSpace?.isLetterOrDigit() == true) { currentInputConnection?.deleteSurroundingText(1, 0); currentInputConnection?.commitText(". ", 1) } else currentInputConnection?.commitText(" ", 1) }
    private fun commitCurrent() { if (manual) { handler.removeCallbacks(commitManual); if (pendingManual.isNotEmpty()) currentInputConnection?.commitText(pendingManual, 1); pendingManual = "" } else if (pattern.isNotEmpty()) { val word = matches().firstOrNull(); if (word != null) { currentInputConnection?.commitText(outputWord(word), 1); recordUsage(word); lastCommitWasWord = true }; pattern = ""; clearWordSuggestions() }; refresh() }
    private fun matches(): List<String> {
        val exact = indexedDictionary.filter { it.second == pattern }
        val candidates = (if (exact.isNotEmpty()) exact else indexedDictionary.filter { it.second.startsWith(pattern) }).filterNot { hiddenWords.getBoolean(it.first, false) }
        return candidates.sortedWith(compareByDescending<Pair<String, String>> { usageScore(it.first) }.thenBy { it.first.length }.thenBy { it.first }).take(40).map { it.first }
    }
    private fun encode(word: String) = word.mapNotNull { wordDigits[it] }.joinToString("")
    private fun usageScore(word: String) = commonWordRank.getOrDefault(word, 0) + wordUsage.getInt(word, 0)
    private fun recordUsage(word: String) { wordUsage.edit().putInt(word, wordUsage.getInt(word, 0) + 1).apply() }
    private fun clearWordSuggestions() { wordSuggestions = emptyList(); replacementBefore = 0; replacementAfter = 0; suppressWordSuggestionsUntil = System.currentTimeMillis() + 250 }
    private fun commitSuggestedWord(word: String) { val before = currentInputConnection?.getTextBeforeCursor(64, 0)?.toString().orEmpty(); if (lastCommitWasWord || before.lastOrNull()?.isLetterOrDigit() == true) currentInputConnection?.commitText(" ", 1); currentInputConnection?.commitText(outputWord(word), 1); val autoSpace = keyboardSettings.getBoolean("auto_space_suggestions", true); if (autoSpace) currentInputConnection?.commitText(" ", 1); recordUsage(word); lastCommitWasWord = !autoSpace }
    private fun replaceCurrentWord(word: String) { val before = replacementBefore; val after = replacementAfter; clearWordSuggestions(); currentInputConnection?.deleteSurroundingText(before, after); currentInputConnection?.commitText(outputWord(word), 1); recordUsage(word); lastCommitWasWord = true; refresh() }
    private fun codeMatches(word: String): List<String> { val code = encode(word); return indexedDictionary.filter { it.second == code && it.first != word && !hiddenWords.getBoolean(it.first, false) }.sortedWith(compareByDescending<Pair<String, String>> { usageScore(it.first) }.thenBy { it.first }).take(40).map { it.first } }
    private fun showCodeMatchesAtCursor() { if (System.currentTimeMillis() < suppressWordSuggestionsUntil || manual || punctuationMode || pattern.isNotEmpty()) return; val connection = currentInputConnection ?: return; val selected = connection.getSelectedText(0)?.toString().orEmpty(); val before = connection.getTextBeforeCursor(64, 0)?.toString().orEmpty(); val after = connection.getTextAfterCursor(64, 0)?.toString().orEmpty(); val left = if (selected.isNotEmpty()) "" else before.takeLastWhile { it.isLetter() || it == '\'' }; val right = if (selected.isNotEmpty()) "" else after.takeWhile { it.isLetter() || it == '\'' }; val word = (if (selected.isNotEmpty()) selected else left + right).lowercase(Locale.US); if (!word.matches(Regex("[a-z]+(?:'[a-z]+)*"))) return; replacementBefore = if (selected.isNotEmpty()) 0 else left.length; replacementAfter = if (selected.isNotEmpty()) 0 else right.length; wordSuggestions = codeMatches(word); refresh() }
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) { super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd); showCodeMatchesAtCursor() }
    private fun refresh() { status?.text = ""; suggestions?.removeAllViews(); val words = if (!manual && pattern.isNotEmpty()) matches() else wordSuggestions; words.forEach { word -> suggestions?.addView(Button(this).apply { text = outputWord(word); textSize = 15f; isAllCaps = false; isSingleLine = true; ellipsize = TextUtils.TruncateAt.END; minimumWidth = 0; minWidth = 0; setPadding(dp(14), 0, dp(14), 0); setOnClickListener { if (pattern.isNotEmpty()) { commitSuggestedWord(word); pattern = ""; clearWordSuggestions(); refresh() } else replaceCurrentWord(word) }; setOnLongClickListener { hiddenWords.edit().putBoolean(word, true).apply(); wordSuggestions = wordSuggestions.filterNot { it == word }; Toast.makeText(this@T9InputMethodService, "Removed $word", Toast.LENGTH_SHORT).show(); refresh(); true }; styleButton(this) }, LinearLayout.LayoutParams(-2, -1).apply { setMargins(dp(2), 0, dp(2), 0) }) } }
}
