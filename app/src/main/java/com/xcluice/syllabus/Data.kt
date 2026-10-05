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
        g("Physics · 13M", i("Gravitation", "5M"), i("Work, Energy and Power", "4M"), i("Sound", "4M")),
        g("Chemistry · 13M", i("Atoms and Molecules", "7M"), i("Structure of the Atom", "6M")),
        g("Biology · 14M", i("Improvement in Food Resources", "7M"), i("Prevention of Drug Abuse and STDs", "7M"))
    )),
    Subject("ss", "Social Science", "🌏", Color(0xFFF59E0B), false, listOf(
        g("History · 12M", i("Nazism and the Rise of Hitler", "5M"), i("Forest Society and Colonialism", "4M"), i("Pastoralists in the Modern World", "3M")),
        g("Geography · 9M", i("Climate", "4M"), i("Natural Vegetation and Wild Life", "2M"), i("Population", "3M")),
        g("Political Science · 10M", i("Working of Institutions", "4M"), i("Democratic Rights", "4M"), i("Electoral Politics in J&K", "2M")),
        g("Economics & Disaster Mgmt · 9M", i("Money and Banking / Indian Economy"), i("Natural Disasters"), i("Man-Made Disasters"))
    )),
    Subject("u", "Urdu", "✍️", Color(0xFFA855F7), false, listOf(
        g("Comprehension · 9M", i("شعری جز", "3M"), i("غیر درسی نثری اقتباس", "3M"), i("تصویر پر مبنی تفہیمی سوالات", "3M")),
        g("Grammar & Writing · 15M", i("حروف کی قسمیں", "3M"), i("تذکیر و تانیث، واحد جمع", "4M"), i("مرکب، جملے کی اقسام، ترکیبِ نحوی", "2M"), i("ای میل / برقی پیغام", "3M"), i("رسمی یا غیر رسمی خطوط", "3M")),
        g("Prose · 6M", i("لاٹری کا ٹکٹ"), i("درد کا مارا ہے"), i("میں ایک شہر تھا پونچھ"), i("میر مہدی مجروح کے نام"), i("نذیر احمد کی کہانی")),
        g("Poetry · 10M", i("غزلیات"), i("نظمیں (کشمیر، قبر، بزمِ انجم)"), i("مثنوی: دنیا کی ناپائیداری"), i("شعری صنعتیں"))
    )),
    Subject("e", "General English", "📖", Color(0xFFEC4899), false, listOf(
        g("Reading · 10M", i("Seen poetry extract", "3M"), i("Unseen passage (300–400 words)", "4M"), i("Case study with visual/data", "3M")),
        g("Grammar · 5M", i("Passage editing: tenses, passive, SV agreement, articles, adjectives, adverbs", "5M")),
        g("Writing Skills · 10M", i("Notice writing", "2M"), i("Letter (formal / informal)", "3M"), i("Speech / article / slogan", "2M"), i("Poster or story", "3M")),
        g("Literature: Prose & Drama · 15M", i("The Tempest (I & II)"), i("The Last Leaf"), i("The Happy Prince"), i("If I Were You"), i("Old Man at the Bridge"), i("The Fun They Had"), i("How a Client was Saved")),
        g("Literature: Poetry", i("On Killing a Tree"), i("Cart Driver"), i("To the Cuckoo"), i("Palanquin Bearers"), i("The Child's Prayer"))
    ))
)
