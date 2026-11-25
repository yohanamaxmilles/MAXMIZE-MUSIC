package com.example.data.dj

import com.example.data.local.db.GenreRuleOverrideEntity
import com.example.data.model.AutomixConfig
import com.example.data.model.Track
import com.example.data.model.TrackAnalysis

enum class GenreFamily(val displayName: String) {
    HOUSE("House"),
    TECHNO("Techno"),
    TRANCE("Trance"),
    BASS("Bass"),
    ELECTRONIC("Electronic"),
    HIP_HOP("Hip-Hop"),
    RNB_SOUL("R&B / Soul"),
    POP("Pop"),
    AFRO("Afro"),
    CARIBBEAN_LATIN("Reggae / Latin"),
    ROCK_ALT("Rock / Alt"),
    ACOUSTIC_HERITAGE("Jazz / Classical / Chill"),
    UNKNOWN("Unknown"),
}

data class GenreClassification(
    val primaryGenre: String,
    val subgenre: String,
    val secondaryGenre: String,
    val family: GenreFamily,
    val confidence: Float,
)

data class GenreCompatibilityResult(
    val allowed: Boolean,
    val score: Int,
    val reason: String,
)

object GenreEngine {

    val SUPPORTED_GENRES: List<String> = listOf(
        // Electronic
        "House", "Deep House", "Tech House", "Afro House", "Progressive House",
        "Techno", "Hard Techno", "Melodic Techno", "Minimal Techno",
        "Trance", "Psytrance",
        "Drum and Bass", "Dubstep", "Future Bass", "Electro", "EDM",
        // Hip-Hop / Urban / Pop
        "Hip-Hop", "Trap", "Drill", "Rap", "R&B", "Soul", "Funk", "Disco",
        "Pop", "Dance Pop", "Indie Pop",
        // African & Global
        "Afrobeats", "Afrobeat", "Bongo Flava", "Amapiano", "Afropop",
        "Reggae", "Dancehall", "Reggaeton", "Latin", "Salsa",
        // Rock / Acoustic / Classics
        "Rock", "Alternative", "Indie", "Metal",
        "Jazz", "Classical", "Ambient", "Acoustic", "Lo-fi", "Gospel", "Country",
    )

    data class GenreSpec(
        val canonical: String,
        val family: GenreFamily,
        val aliases: List<String>,
    )

    private val TAXONOMY = listOf(
        GenreSpec("Deep House", GenreFamily.HOUSE, listOf("deep house", "deephouse", "soulful house")),
        GenreSpec("Tech House", GenreFamily.HOUSE, listOf("tech house", "techhouse")),
        GenreSpec("Afro House", GenreFamily.HOUSE, listOf("afro house", "afrohouse", "tribal house")),
        GenreSpec("Progressive House", GenreFamily.HOUSE, listOf("progressive house", "prog house", "melodic house")),
        GenreSpec("House", GenreFamily.HOUSE, listOf("house", "tropical house", "future house", "club house")),

        GenreSpec("Hard Techno", GenreFamily.TECHNO, listOf("hard techno", "hardtechno", "industrial techno", "schranz")),
        GenreSpec("Melodic Techno", GenreFamily.TECHNO, listOf("melodic techno", "afterlife")),
        GenreSpec("Minimal Techno", GenreFamily.TECHNO, listOf("minimal techno", "minimal")),
        GenreSpec("Techno", GenreFamily.TECHNO, listOf("techno", "acid techno")),

        GenreSpec("Psytrance", GenreFamily.TRANCE, listOf("psytrance", "psy trance", "goa trance", "psychedelic trance")),
        GenreSpec("Trance", GenreFamily.TRANCE, listOf("trance", "uplifting trance", "progressive trance", "vocal trance")),

        GenreSpec("Drum and Bass", GenreFamily.BASS, listOf("drum and bass", "drum & bass", "dnb", "d&b", "liquid dnb", "jungle")),
        GenreSpec("Dubstep", GenreFamily.BASS, listOf("dubstep", "riddim", "brostep")),
        GenreSpec("Future Bass", GenreFamily.BASS, listOf("future bass")),
        GenreSpec("Midtempo Bass", GenreFamily.BASS, listOf("midtempo bass", "midtempo", "dark midtempo", "industrial bass", "rezz style", "rezz")),
        GenreSpec("3-Step", GenreFamily.AFRO, listOf("3-step", "3 step", "three step", "afro 3-step", "afro three step")),

        GenreSpec("Electro", GenreFamily.ELECTRONIC, listOf("electro", "electro house", "synthwave")),
        GenreSpec("EDM", GenreFamily.ELECTRONIC, listOf("edm", "big room", "festival", "dance", "electronica", "electronic", "club")),

        GenreSpec("Trap", GenreFamily.HIP_HOP, listOf("trap")),
        GenreSpec("Drill", GenreFamily.HIP_HOP, listOf("drill", "uk drill", "ny drill")),
        GenreSpec("Hip-Hop", GenreFamily.HIP_HOP, listOf("hip-hop", "hip hop", "hiphop", "boom bap", "rap")),

        GenreSpec("R&B", GenreFamily.RNB_SOUL, listOf("r&b", "rnb", "rhythm and blues", "neo soul", "neo-soul")),
        GenreSpec("Soul", GenreFamily.RNB_SOUL, listOf("soul", "motown")),
        GenreSpec("Funk", GenreFamily.RNB_SOUL, listOf("funk")),
        GenreSpec("Disco", GenreFamily.RNB_SOUL, listOf("disco", "nu-disco", "nu disco")),

        GenreSpec("Dance Pop", GenreFamily.POP, listOf("dance pop", "dance-pop", "synth pop", "synthpop", "electropop", "electro pop")),
        GenreSpec("Pop", GenreFamily.POP, listOf("pop", "k-pop", "kpop", "indie pop", "mainstream pop")),

        GenreSpec("Bongo Flava", GenreFamily.AFRO, listOf("bongo flava", "bongoflava", "bongo", "tanzania")),
        GenreSpec("Amapiano", GenreFamily.AFRO, listOf("amapiano", "piano")),
        GenreSpec("Afrobeats", GenreFamily.AFRO, listOf("afrobeats", "afrobeat", "afro beat", "afropop", "afro pop", "afro fusion", "gengetone")),

        GenreSpec("Reggae", GenreFamily.CARIBBEAN_LATIN, listOf("reggae", "roots reggae", "dub", "lovers rock")),
        GenreSpec("Dancehall", GenreFamily.CARIBBEAN_LATIN, listOf("dancehall", "ragga")),
        GenreSpec("Reggaeton", GenreFamily.CARIBBEAN_LATIN, listOf("reggaeton", "dembow", "urbano")),
        GenreSpec("Latin", GenreFamily.CARIBBEAN_LATIN, listOf("latin", "salsa", "bachata", "latin pop", "merengue")),

        GenreSpec("Metal", GenreFamily.ROCK_ALT, listOf("metal", "heavy metal", "death metal", "metalcore")),
        GenreSpec("Rock", GenreFamily.ROCK_ALT, listOf("rock", "hard rock", "punk", "classic rock")),
        GenreSpec("Alternative", GenreFamily.ROCK_ALT, listOf("alternative", "indie", "indie rock", "grunge")),

        GenreSpec("Jazz", GenreFamily.ACOUSTIC_HERITAGE, listOf("jazz", "smooth jazz", "bebop", "swing")),
        GenreSpec("Classical", GenreFamily.ACOUSTIC_HERITAGE, listOf("classical", "orchestral", "symphony", "piano solo", "opera")),
        GenreSpec("Ambient", GenreFamily.ACOUSTIC_HERITAGE, listOf("ambient", "chillout", "chill out", "downtempo", "new age")),
        GenreSpec("Lo-fi", GenreFamily.ACOUSTIC_HERITAGE, listOf("lo-fi", "lofi", "lo fi")),
        GenreSpec("Acoustic", GenreFamily.ACOUSTIC_HERITAGE, listOf("acoustic", "folk", "singer-songwriter")),
        GenreSpec("Gospel", GenreFamily.ACOUSTIC_HERITAGE, listOf("gospel", "christian", "worship", "praise")),
        GenreSpec("Country", GenreFamily.ACOUSTIC_HERITAGE, listOf("country", "americana")),
    )

    // Strict incompatible pairs blocked when strictGenreMatching = true
    private val STRICTLY_INCOMPATIBLE_FAMILIES = setOf(
        setOf(GenreFamily.POP, GenreFamily.TECHNO),
        setOf(GenreFamily.POP, GenreFamily.TRANCE),
        setOf(GenreFamily.POP, GenreFamily.BASS),
        setOf(GenreFamily.CARIBBEAN_LATIN, GenreFamily.TECHNO),
        setOf(GenreFamily.CARIBBEAN_LATIN, GenreFamily.TRANCE),
        setOf(GenreFamily.CARIBBEAN_LATIN, GenreFamily.ROCK_ALT),
        setOf(GenreFamily.AFRO, GenreFamily.TECHNO),
        setOf(GenreFamily.AFRO, GenreFamily.TRANCE),
        setOf(GenreFamily.AFRO, GenreFamily.BASS),
        setOf(GenreFamily.HIP_HOP, GenreFamily.TRANCE),
        setOf(GenreFamily.RNB_SOUL, GenreFamily.TECHNO),
        setOf(GenreFamily.RNB_SOUL, GenreFamily.TRANCE),
        setOf(GenreFamily.ACOUSTIC_HERITAGE, GenreFamily.TECHNO),
        setOf(GenreFamily.ACOUSTIC_HERITAGE, GenreFamily.TRANCE),
        setOf(GenreFamily.ACOUSTIC_HERITAGE, GenreFamily.BASS),
        setOf(GenreFamily.ACOUSTIC_HERITAGE, GenreFamily.ELECTRONIC),
    )

    private val STRICTLY_INCOMPATIBLE_SUBGENRES = setOf(
        setOf("Pop", "Psytrance"),
        setOf("Pop", "Hard Techno"),
        setOf("Pop", "Metal"),
        setOf("Deep House", "Hard Techno"),
        setOf("Deep House", "Psytrance"),
        setOf("Deep House", "Metal"),
        setOf("Reggae", "Hard Techno"),
        setOf("Reggae", "Psytrance"),
        setOf("Reggae", "Metal"),
        setOf("Bongo Flava", "Hard Techno"),
        setOf("Bongo Flava", "Psytrance"),
        setOf("Bongo Flava", "Metal"),
        setOf("Classical", "Trap"),
        setOf("Classical", "Drill"),
        setOf("Classical", "Dubstep"),
        setOf("Gospel", "Hard Techno"),
        setOf("Gospel", "Psytrance"),
        setOf("Gospel", "Metal"),
        setOf("Jazz", "Hard Techno"),
        setOf("Jazz", "Psytrance"),
        setOf("Jazz", "Dubstep"),
    )

    fun canonicalize(rawGenre: String): GenreSpec? {
        val norm = rawGenre.trim().lowercase()
        if (norm.isBlank() || norm == "unknown" || norm == "unknown genre" || norm == "<unknown>") return null
        // Exact canonical match first
        TAXONOMY.firstOrNull { it.canonical.equals(norm, ignoreCase = true) }?.let { return it }
        // Multi-genre tag e.g. "Hip-Hop/Rap" or "Electronic; Deep House"
        val longestAlias = TAXONOMY
            .flatMap { spec -> spec.aliases.map { alias -> spec to alias } }
            .sortedByDescending { it.second.length }
        longestAlias.firstOrNull { (_, alias) -> norm == alias || norm.contains(alias) }?.let { return it.first }
        return null
    }

    fun familyFor(genreName: String): GenreFamily {
        return canonicalize(genreName)?.family ?: GenreFamily.UNKNOWN
    }

    fun classifyTrack(track: Track, analysis: TrackAnalysis?): GenreClassification {
        // 1. User or tag metadata first (highest trust)
        val fromTag = canonicalize(track.genre)
        val fromTitleHint = extractTitleRemixGenreHint(track.title)
        if (fromTag != null) {
            val sub = fromTitleHint?.takeIf { it.family == fromTag.family }?.canonical ?: fromTag.canonical
            return GenreClassification(
                primaryGenre = fromTag.family.displayName,
                subgenre = sub,
                secondaryGenre = fromTitleHint?.canonical?.takeIf { it != sub }.orEmpty(),
                family = fromTag.family,
                confidence = 0.90f,
            )
        }
        if (fromTitleHint != null) {
            return GenreClassification(
                primaryGenre = fromTitleHint.family.displayName,
                subgenre = fromTitleHint.canonical,
                secondaryGenre = "",
                family = fromTitleHint.family,
                confidence = 0.78f,
            )
        }

        // 2. Analysis already holds a non-unknown classification
        if (analysis != null && analysis.subgenre.isNotBlank() && analysis.subgenre != "Unknown") {
            val fam = familyFor(analysis.subgenre)
            return GenreClassification(
                primaryGenre = analysis.primaryGenre.ifBlank { fam.displayName },
                subgenre = analysis.subgenre,
                secondaryGenre = analysis.secondaryGenre,
                family = fam,
                confidence = analysis.genreConfidence.coerceIn(0.25f, 0.95f),
            )
        }

        // 3. Conservative audio DSP classification fallback (honest moderate confidence)
        if (analysis != null) {
            return inferFromAudioFeatures(analysis)
        }

        return GenreClassification(
            primaryGenre = "Unknown",
            subgenre = "Unknown",
            secondaryGenre = "",
            family = GenreFamily.UNKNOWN,
            confidence = 0.1f,
        )
    }

    private fun extractTitleRemixGenreHint(title: String): GenreSpec? {
        val lower = title.lowercase()
        val markers = listOf("midtempo bass", "dark midtempo", "industrial bass", "rezz style", "3-step", "3 step", "three step", "amapiano", "deep house", "afro house", "tech house", "hard techno",
            "psytrance", "bongo flava", "dancehall", "reggaeton", "dubstep", "dnb", "acoustic", "lo-fi")
        for (m in markers) {
            if (lower.contains(m)) return canonicalize(m)
        }
        return null
    }

    /**
     * Tempo, energy and spectral brightness describe sound and rhythm, not genre identity.
     * In particular, BPM-only rules confuse fast techno with DnB and mislabel midtempo
     * bass as DnB. Until a validated subgenre model is installed, audio-only guesses
     * stay Unknown. Reliable tags and explicit title hints are handled above.
     */
    fun inferFromAudioFeatures(a: TrackAnalysis): GenreClassification {
        return GenreClassification(
            primaryGenre = "Unknown",
            subgenre = "Unknown",
            secondaryGenre = "",
            family = GenreFamily.UNKNOWN,
            confidence = 0.15f,
        )
    }

    fun evaluateCompatibility(
        current: Track,
        currentAnalysis: TrackAnalysis?,
        candidate: Track,
        candidateAnalysis: TrackAnalysis?,
        config: AutomixConfig,
        customRules: List<GenreRuleOverrideEntity> = emptyList(),
    ): GenreCompatibilityResult {
        val cA = classifyTrack(current, currentAnalysis)
        val cB = classifyTrack(candidate, candidateAnalysis)

        // Unknown genres cannot trigger strict rejection unless explicitly filtered
        if (cA.family == GenreFamily.UNKNOWN || cB.family == GenreFamily.UNKNOWN) {
            return GenreCompatibilityResult(
                allowed = true,
                score = 62,
                reason = "Genre unverified (audio tempo/key guided)",
            )
        }

        // 1. Check user custom allow/block rule overrides first (symmetric)
        val customRule = customRules.firstOrNull { r ->
            (r.genreA.equals(cA.subgenre, true) && r.genreB.equals(cB.subgenre, true)) ||
                (r.genreA.equals(cB.subgenre, true) && r.genreB.equals(cA.subgenre, true))
        }
        if (customRule != null) {
            return if (customRule.allowed) {
                GenreCompatibilityResult(true, 88, "Custom rule: ${cA.subgenre} -> ${cB.subgenre}")
            } else {
                GenreCompatibilityResult(false, 0, "Blocked by custom rule: ${cA.subgenre} / ${cB.subgenre}")
            }
        }

        // 2. Identical subgenre
        if (cA.subgenre.equals(cB.subgenre, ignoreCase = true)) {
            return GenreCompatibilityResult(true, 100, "Exact genre match (${cB.subgenre})")
        }

        // 3. Explicit subgenre incompatibility check
        val subPair = setOf(cA.subgenre, cB.subgenre)
        if (STRICTLY_INCOMPATIBLE_SUBGENRES.any { it.map(String::lowercase).toSet() == subPair.map(String::lowercase).toSet() }) {
            val allowed = !config.strictGenreMatching || config.openFormatBridging
            return GenreCompatibilityResult(
                allowed = allowed,
                score = if (config.openFormatBridging) 38 else 10,
                reason = if (!allowed) {
                    "Incompatible genres (${cA.subgenre} -> ${cB.subgenre})"
                } else {
                    "Open-format bridge (${cA.subgenre} -> ${cB.subgenre})"
                },
            )
        }

        // 4. Same hierarchical family (e.g., Deep House -> Afro House, Afrobeats -> Bongo Flava)
        if (cA.family == cB.family) {
            return GenreCompatibilityResult(true, 88, "Same family (${cA.family.displayName})")
        }

        // 5. Explicit family incompatibility check
        val famPair = setOf(cA.family, cB.family)
        if (famPair in STRICTLY_INCOMPATIBLE_FAMILIES) {
            val allowed = !config.strictGenreMatching || config.openFormatBridging
            return GenreCompatibilityResult(
                allowed = allowed,
                score = if (config.openFormatBridging) 40 else 15,
                reason = if (!allowed) {
                    "Incompatible families (${cA.subgenre} -> ${cB.subgenre})"
                } else {
                    "Open-format crossover (${cA.subgenre} -> ${cB.subgenre})"
                },
            )
        }

        // 6. Natural DJ crossover bridges (House<->EDM/Disco/Afro, Hip-Hop<->R&B/Pop/Dancehall, Pop<->Dance Pop/R&B)
        val crossScore = computeFriendlyFamilyScore(cA.family, cB.family)
        if (config.strictGenreMatching && !config.openFormatBridging && crossScore < 60) {
            return GenreCompatibilityResult(
                allowed = false,
                score = crossScore,
                reason = "Strict genre mismatch (${cA.subgenre} -> ${cB.subgenre})",
            )
        }

        return GenreCompatibilityResult(
            allowed = true,
            score = crossScore,
            reason = "Compatible crossover (${cA.subgenre} -> ${cB.subgenre})",
        )
    }

    private fun computeFriendlyFamilyScore(a: GenreFamily, b: GenreFamily): Int {
        val pair = setOf(a, b)
        return when (pair) {
            setOf(GenreFamily.HOUSE, GenreFamily.ELECTRONIC),
            setOf(GenreFamily.HOUSE, GenreFamily.AFRO),
            setOf(GenreFamily.HOUSE, GenreFamily.RNB_SOUL),
            setOf(GenreFamily.TECHNO, GenreFamily.TRANCE),
            setOf(GenreFamily.TECHNO, GenreFamily.ELECTRONIC),
            setOf(GenreFamily.HIP_HOP, GenreFamily.RNB_SOUL),
            setOf(GenreFamily.HIP_HOP, GenreFamily.AFRO),
            setOf(GenreFamily.HIP_HOP, GenreFamily.CARIBBEAN_LATIN),
            setOf(GenreFamily.AFRO, GenreFamily.CARIBBEAN_LATIN),
            setOf(GenreFamily.AFRO, GenreFamily.RNB_SOUL),
            setOf(GenreFamily.POP, GenreFamily.RNB_SOUL),
            setOf(GenreFamily.POP, GenreFamily.AFRO),
            setOf(GenreFamily.POP, GenreFamily.ELECTRONIC),
            setOf(GenreFamily.POP, GenreFamily.CARIBBEAN_LATIN),
            setOf(GenreFamily.POP, GenreFamily.HIP_HOP) -> 76
            setOf(GenreFamily.HOUSE, GenreFamily.POP),
            setOf(GenreFamily.ELECTRONIC, GenreFamily.BASS),
            setOf(GenreFamily.ELECTRONIC, GenreFamily.TRANCE),
            setOf(GenreFamily.POP, GenreFamily.ROCK_ALT),
            setOf(GenreFamily.RNB_SOUL, GenreFamily.ACOUSTIC_HERITAGE) -> 66
            else -> 44
        }
    }
}
