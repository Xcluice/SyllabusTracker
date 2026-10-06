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
    Subject("m", "Mathematics", "📐", Color(0xFF6366F1), true, listOf(
        g("Algebra · 5M", i("Linear Equations in Two Variables", "5M")),
        g("Coordinate Geometry · 5M", i("Cartesian plane & coordinates", "5M")),
        g("Geometry · 10M", i("Circles (cyclic quadrilaterals, chords)", "10M")),
        g("Mensuration · 15M", i("Heron's Formula", "5M"), i("Surface Areas & Volumes (sphere, hemisphere, cone)", "10M")),
        g("Statistics · 5M", i("Bar graphs, histograms, frequency polygons", "5M"))
    )),
    Subject("s", "Science", "🔬", Color(0xFF10B981), true, listOf(
        g("Physics", i("1. Gravitation"), i("2. Sound")),
        g("Chemistry", i("1. Atoms and Molecules"), i("2. Structure of Atom")),
        g("Biology", i("1. Improvement of Natural Resources"), i("2. Drug Abuse and STD"))
    )),
    Subject("ss", "Social Science", "🌏", Color(0xFFF59E0B), false, listOf(
        g("Geography", i("3. Drainage"), i("4. Climate")),
        g("History", i("3. Nazism and the Rise of Hitler"), i("4. Forest Society and Colonialism"), i("5. Pastoralists in the Modern World")),
        g("Political Science", i("4. Working of Institutions")),
        g("Disaster Management / Economics", i("3. Natural Disasters"), i("4. Man-made Disasters"))
    )),
    Subject("u", "Urdu", "✍️", Color(0xFFA855F7), false, listOf(
        g("نثر (Prose)", i("ماحولیاتی آلودگی"), i("ڈراما — لاٹری کا ٹکٹ"), i("افسانہ — فگار کا مارا"), i("ناول — میں ایک شہر تھا سرینگر"), i("خطوط — میر مہدی مجروح کے نام"), i("خاکہ — نذیر احمد کی کہانی کچھ میری کچھ ان کی زبانی")),
        g("شاعری (Poetry)", i("غزل — شوریدہ کاشمیری ، فیض احمد فیض"), i("نظم — کشمیر"), i("مثنوی — دنیا کی ناپائیداری"))
    )),
    Subject("e", "General English", "📖", Color(0xFFEC4899), false, listOf(
        g("Reading · 10M", i("Seen poetry extract", "3M"), i("Unseen passage (300–400 words)", "4M"), i("Case study with visual/data", "3M")),
        g("Grammar · 5M", i("Passage editing: tenses, passive, SV agreement, articles, adjectives, adverbs", "5M")),
        g("Writing Skills · 10M", i("Notice writing", "2M"), i("Letter (formal / informal)", "3M"), i("Speech / article / slogan", "2M"), i("Poster or story", "3M")),
        g("Literature: Prose & Drama · 15M", i("The Tempest (I & II)"), i("The Last Leaf"), i("The Happy Prince"), i("If I Were You"), i("Old Man at the Bridge"), i("The Fun They Had"), i("How a Client was Saved")),
        g("Literature: Poetry", i("On Killing a Tree"), i("Cart Driver"), i("To the Cuckoo"), i("Palanquin Bearers"), i("The Child's Prayer"))
    ))
)
