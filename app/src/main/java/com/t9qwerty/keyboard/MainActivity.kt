package com.t9qwerty.keyboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.app.Activity

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val settings = getSharedPreferences("keyboard_settings", MODE_PRIVATE)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad); gravity = Gravity.CENTER_VERTICAL }
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        box.addView(TextView(this).apply { text = "T9 QWERTY Keyboard\n\nVersion $version\nA native Android keyboard with connected T9 search keys and an optional normal separated-key layout."; textSize = 20f })
        box.addView(Button(this).apply { text = "View update notes"; setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cohpie-oss/t9-qwerty-keyboard/blob/main/CHANGELOG.md"))) } })
        box.addView(Button(this).apply { text = "1. Enable keyboard in Android Settings"; setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) } })
        box.addView(Button(this).apply { text = "2. Choose T9 QWERTY Keyboard"; setOnClickListener {
            val id = ComponentName(this@MainActivity, T9InputMethodService::class.java).flattenToShortString()
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS).putExtra(Settings.EXTRA_INPUT_METHOD_ID, id))
        } })
        box.addView(Button(this).apply { text = "Report a bug on GitHub"; setOnClickListener {
            val report = """
                ## What happened?

                <!-- Please describe the problem and the steps that caused it. -->

                ## Device details

                - App version: $version
                - Android: ${android.os.Build.VERSION.RELEASE}
                - Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
                - Dark keyboard: ${settings.getBoolean("dark_keyboard", false)}
                - Compact layout: ${settings.getBoolean("compact_layout", false)} (${settings.getString("compact_alignment", "center")})
                - Number row: ${settings.getBoolean("show_number_row", true)}
            """.trimIndent()
            val url = Uri.parse("https://github.com/cohpie-oss/t9-qwerty-keyboard/issues/new?title=" + Uri.encode("Bug report: ") + "&body=" + Uri.encode(report))
            startActivity(Intent(Intent.ACTION_VIEW, url))
        } })
        val sizeLabel = TextView(this).apply { text = "Keyboard size: ${settings.getInt("keyboard_scale", 100)}%"; textSize = 18f }
        box.addView(sizeLabel)
        box.addView(SeekBar(this).apply { max = 100; progress = settings.getInt("keyboard_scale", 100) - 50; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { val percent = progress + 50; settings.edit().putInt("keyboard_scale", percent).apply(); sizeLabel.text = "Keyboard size: $percent%" }; override fun onStartTrackingTouch(seekBar: SeekBar?) {} ; override fun onStopTrackingTouch(seekBar: SeekBar?) {} }) })
        box.addView(CheckBox(this).apply { text = "Automatically capitalize new sentences"; textSize = 17f; isChecked = settings.getBoolean("auto_capitalize", true); setOnCheckedChangeListener { _, checked -> settings.edit().putBoolean("auto_capitalize", checked).apply() } })
        box.addView(CheckBox(this).apply { text = "Double space inserts a full stop"; textSize = 17f; isChecked = settings.getBoolean("double_space_period", true); setOnCheckedChangeListener { _, checked -> settings.edit().putBoolean("double_space_period", checked).apply() } })
        box.addView(CheckBox(this).apply { text = "Dark keyboard"; textSize = 17f; isChecked = settings.getBoolean("dark_keyboard", false); setOnCheckedChangeListener { _, checked -> settings.edit().putBoolean("dark_keyboard", checked).apply() } })
        box.addView(CheckBox(this).apply { text = "Show number row"; textSize = 17f; isChecked = settings.getBoolean("show_number_row", true); setOnCheckedChangeListener { _, checked -> settings.edit().putBoolean("show_number_row", checked).apply() } })
        box.addView(CheckBox(this).apply { text = "Auto-space after selecting a suggestion"; textSize = 17f; isChecked = settings.getBoolean("auto_space_suggestions", true); setOnCheckedChangeListener { _, checked -> settings.edit().putBoolean("auto_space_suggestions", checked).apply() } })
        box.addView(CheckBox(this).apply { text = "Compact one-handed layout"; textSize = 17f; isChecked = settings.getBoolean("compact_layout", false); setOnCheckedChangeListener { _, checked -> settings.edit().putBoolean("compact_layout", checked).apply() } })
        box.addView(TextView(this).apply { text = "Compact layout position"; textSize = 18f })
        val alignment = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf("lhs" to "LHS", "center" to "Centre", "rhs" to "RHS").forEachIndexed { index, (value, label) -> alignment.addView(RadioButton(this).apply { id = 700 + index; text = label; textSize = 16f; isChecked = settings.getString("compact_alignment", "center") == value }) }
        alignment.setOnCheckedChangeListener { _, id -> settings.edit().putString("compact_alignment", when (id) { 700 -> "lhs"; 702 -> "rhs"; else -> "center" }).apply() }
        box.addView(alignment)
        box.addView(TextView(this).apply { text = "Changes take effect the next time you hide and reopen the keyboard. LHS/RHS positions the compact layout for one-handed typing." })
        val removedBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val hiddenWords = getSharedPreferences("hidden_words", MODE_PRIVATE)
        fun showRemovedWords() {
            removedBox.removeAllViews()
            val words = hiddenWords.all.filterValues { it == true }.keys.sorted()
            if (words.isEmpty()) {
                removedBox.addView(TextView(this).apply { text = "\nRemoved suggestions: none" })
            } else {
                removedBox.addView(TextView(this).apply { text = "\nRemoved suggestions"; textSize = 18f })
                removedBox.addView(Button(this).apply { text = "Restore all removed suggestions"; setOnClickListener { hiddenWords.edit().clear().apply(); showRemovedWords() } })
                words.forEach { word -> removedBox.addView(Button(this).apply { text = "Restore: $word"; setOnClickListener { hiddenWords.edit().remove(word).apply(); showRemovedWords() } }) }
            }
        }
        showRemovedWords()
        box.addView(removedBox)
        setContentView(ScrollView(this).apply { addView(box) })
    }
}
