package com.musictracker.data

import java.util.Locale

/** Small helpers for comparing song names written in different ways. */
object SongText {

    private val brackets = Regex("""[(\[{].*?[)\]}]""")
    private val noise = Regex(
        """\b(official|music|lyric|lyrics|video|audio|full|song|hd|4k|remastered|remaster)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val notLetter = Regex("""[^\p{L}\p{M}\p{N}]+""")

    /** Key that treats "Tumi Robe Nirobe (Official Video)" and "tumi robe nirobe" as the same song. */
    fun key(title: String, artist: String): String = clean(title) + "|" + clean(artist)

    /** Lower-case name without brackets, "official video" and punctuation. */
    fun clean(s: String): String {
        val noBrackets = brackets.replace(s, " ")
        val base = noBrackets.ifBlank { s }
        return notLetter.replace(noise.replace(base.lowercase(Locale.ROOT), " "), " ").trim()
    }

    /**
     * Bangla, Hindi or English/other, from the strongest clue available:
     * 1. the script (Bangla or Devanagari letters),
     * 2. genre or tags found online ([hints], e.g. "Bollywood", "bengali"),
     * 3. common Bangla or Hindi words written in English letters in the title ("Tumi", "Tera"),
     * 4. well-known singers, then what language this artist's other songs are in ([artistLanguage]).
     */
    fun detectLanguage(
        title: String,
        artist: String,
        hints: List<String> = emptyList(),
        artistLanguage: String? = null,
    ): String {
        scriptLanguage("$title $artist")?.let { return it }
        hintLanguage(hints)?.let { return it }
        wordLanguage(title)?.let { return it }
        artistKey(artist).let { a ->
            if (a in BANGLA_ARTISTS) return BANGLA
            if (a in HINDI_ARTISTS) return HINDI
        }
        if (artistLanguage == BANGLA || artistLanguage == HINDI) return artistLanguage
        return OTHER
    }

    private fun scriptLanguage(text: String): String? {
        var bangla = 0
        var hindi = 0
        for (c in text) {
            when (c.code) {
                in 0x0980..0x09FF -> bangla++
                in 0x0900..0x097F -> hindi++
            }
        }
        return when {
            bangla == 0 && hindi == 0 -> null
            bangla >= hindi -> BANGLA
            else -> HINDI
        }
    }

    private fun hintLanguage(hints: List<String>): String? {
        val text = hints.joinToString(" ").lowercase(Locale.ROOT)
        return when {
            BANGLA_HINTS.any { it in text } -> BANGLA
            HINDI_HINTS.any { it in text } -> HINDI
            else -> null
        }
    }

    /** Counts Bangla and Hindi words in the title; short words count half, so one stray "ke" is not enough. */
    private fun wordLanguage(title: String): String? {
        var bangla = 0.0
        var hindi = 0.0
        for (word in notLetter.split(title.lowercase(Locale.ROOT))) {
            val weight = if (word.length >= 3) 1.0 else 0.5
            if (word in BANGLA_WORDS) bangla += weight
            if (word in HINDI_WORDS) hindi += weight
        }
        return when {
            bangla >= 1 && bangla > hindi -> BANGLA
            hindi >= 1 && hindi > bangla -> HINDI
            else -> null
        }
    }

    private fun artistKey(artist: String) = notLetter.replace(artist.lowercase(Locale.ROOT), " ").trim()

    const val BANGLA = "Bangla"
    const val HINDI = "Hindi"
    const val OTHER = "English/other"

    private val BANGLA_HINTS = listOf("bangla", "bengali", "bangladesh", "kolkata", "rabindra", "nazrul", "baul", "tollywood bengali")
    private val HINDI_HINTS = listOf("bollywood", "hindi", "filmi", "indian pop", "indian film")

    // Common words of Bangla and Hindi lyrics as people type them in English letters.
    // Words both languages share (prem, maya, priya, din) are left out, and so are English look-alikes
    // (main, mon, sun, ban, robe, ache, jake).
    private val BANGLA_WORDS = setOf(
        "ami", "tumi", "amar", "tomar", "amay", "tomay", "amake", "tomake", "amra", "tomra", "amader", "tomader",
        "bhalobashi", "bhalobasha", "bhalobasho", "bhalobese", "valobashi", "valobasha", "valobasi", "bhalobasi",
        "mone", "moner", "hridoy", "kotha", "kothay", "achhe", "achi", "chilo", "chhilo", "hobe", "hoye",
        "keno", "kemon", "shudhu", "sudhu", "jodi", "ekhon", "akash", "brishti", "nodi", "rater", "chokhe",
        "chokher", "tomari", "amari", "priyo", "bondhu", "jibon", "jiboner", "hariye", "harano", "phire", "eshe",
        "esho", "jabo", "jete", "debo", "nirobe", "gaan", "ganer", "shono", "kichu", "kichhu", "ekta",
        "ekti", "ekla", "cholo", "bhindeshi", "shopne", "shopno", "mayabi", "tobu", "noy", "nei", "bolbo",
        "ekdin", "tumio", "amio", "kokhono", "shara", "shobai", "sathe", "shathe", "hoyeche", "kore",
    )
    private val HINDI_WORDS = setOf(
        "hai", "hain", "hoon", "tum", "tumhe", "tumse", "tumhari", "tumhara", "tera", "teri", "tere", "mera",
        "meri", "mere", "mereya", "mujhe", "mujhko", "dil", "pyaar", "pyar", "ishq", "mohabbat", "zindagi",
        "sanam", "saathiya", "yaara", "jaana", "kya", "kyun", "kyon", "nahi", "nahin", "kabhi", "hum", "humko",
        "aaj", "raat", "sapna", "sajna", "mahi", "dilbar", "kesariya", "channa", "rabba", "khuda", "ankhon",
        "aankhein", "baarish", "bahon", "saiyaan", "tujhe", "tujhse", "aaja", "jaane", "deewana", "deewani",
        "phir", "hota", "hoga", "kahin", "kaise", "kaun", "dekha", "raatan", "lambiyan", "naina", "piya",
        "jaunga", "jaungi", "diyan", "gallan", "hi", "ho", "ve", "pehla", "pehli", "sawan", "bhi",
        "aur", "mein", "tu", "karo", "lagda", "lage", "dekho", "suno", "jab", "tak",
    )
    // Lower-case names, as the artist field usually shows them.
    private val BANGLA_ARTISTS = setOf(
        "tahsan", "james", "habib wahid", "minar", "anupam roy", "shironamhin", "artcell", "warfaze", "ayub bachchu",
        "fuad", "arnob", "nachiketa", "rupam islam", "fossils", "chirkutt", "hridoy khan", "imran mahmudul", "kona",
        "balam", "pritom hasan", "topu", "shawon gaanwala", "kureghor", "lalon", "coke studio bangla", "chandrabindoo",
        "bappa mazumder", "nemesis", "aurthohin", "miles", "lumbini", "joler gaan", "ghuri", "tanjib sarowar",
        "shreya ghoshal bangla", "momtaz", "anjan dutt", "hemanta mukherjee", "manna dey bengali", "iman chakraborty",
        "jisan khan shuvo", "samz vai", "shiekh sadi", "arfin rumey", "porshi", "konal", "muza",
    )
    private val HINDI_ARTISTS = setOf(
        "arijit singh", "atif aslam", "jubin nautiyal", "neha kakkar", "badshah", "pritam", "vishal mishra",
        "darshan raval", "kk", "sonu nigam", "lata mangeshkar", "kishore kumar", "armaan malik", "b praak",
        "sachet tandon", "anuv jain", "mohit chauhan", "udit narayan", "alka yagnik", "kumar sanu",
        "yo yo honey singh", "shankar mahadevan", "sunidhi chauhan", "javed ali", "ankit tiwari", "shaan",
        "tanishk bagchi", "sachin jigar", "vishal shekhar", "amit trivedi", "asees kaur", "tulsi kumar",
        "palak muchhal", "mithoon", "salim sulaiman", "raghav chaitanya", "rahat fateh ali khan", "mohammed rafi",
        "jasleen royal", "stebin ben", "shilpa rao", "seedhe maut", "divine", "raftaar", "lucky ali",
    )

    val LANGUAGES = listOf(BANGLA, HINDI, OTHER)

    /**
     * Free YouTube link for a song: a search for "title artist". Needs no API key or account,
     * and opens in the YouTube app when it is installed, otherwise in the browser.
     */
    fun youtubeUrl(title: String, artist: String): String {
        val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        return "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(query, "UTF-8")
    }
}
