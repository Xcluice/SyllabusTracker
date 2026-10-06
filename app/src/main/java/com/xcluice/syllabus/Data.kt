package com.xcluice.syllabus

import androidx.compose.ui.graphics.Color

class Item(val name: String, val marks: String = "") {
    val rtl = name.any { it in '\u0600'..'\u06FF' }
}
class Group(val title: String, val items: List<Item>)
class Subject(val id: String, val name: String, val emoji: String, val color: Color, val preDone: Boolean, val groups: List<Group>)

private fun i(n: String, m: String = "") = Item(n, m)
private fun g(t: String, vararg x: Item) = Group(t, x.toList())

val SUBJECTS = listOf(
    Subject("sci", "General Science", "🔬", Color(0xFF10B981), false, listOf(
        g("Physics", i("1. Gravitation"), i("2. Sound")),
        g("Chemistry", i("1. Atoms and Molecules"), i("2. Structure of Atom")),
        g("Biology", i("1. Improvement of Natural Resources"), i("2. Drug Abuse and STD"))
    )),
    Subject("urd", "Urdu", "✍️", Color(0xFFA855F7), false, listOf(
        g("نثر (Prose)", i("ماحولیاتی آلودگی"), i("ڈراما — لاٹری کا ٹکٹ"), i("افسانہ — فگار کا مارا"), i("ناول — میں ایک شہر تھا سرینگر"), i("خطوط — میر مہدی مجروح کے نام"), i("خاکہ — نذیر احمد کی کہانی کچھ میری کچھ ان کی زبانی")),
        g("شاعری (Poetry)", i("غزل — شوریدہ کاشمیری ، فیض احمد فیض"), i("نظم — کشمیر"), i("مثنوی — دنیا کی ناپائیداری"))
    )),
    Subject("soc", "Social Science", "🌏", Color(0xFFF59E0B), false, listOf(
        g("Geography", i("3. Drainage"), i("4. Climate")),
        g("History", i("3. Nazism and the Rise of Hitler"), i("4. Forest Society and Colonialism"), i("5. Pastoralists in the Modern World")),
        g("Political Science", i("4. Working of Institutions")),
        g("Disaster Management / Economics", i("3. Natural Disasters"), i("4. Man-made Disasters"))
    ))
)
