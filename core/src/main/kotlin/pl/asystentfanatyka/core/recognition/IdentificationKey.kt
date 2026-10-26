package pl.asystentfanatyka.core.recognition

/**
 * Klucz do oznaczania gatunku krok po kroku na podstawie cech widocznych gołym okiem.
 * Działa offline i nie wymaga modelu AI. Liście mogą wskazywać kilka gatunków, gdy cechy
 * zewnętrzne nie pozwalają ich pewnie rozróżnić (np. łosoś i troć).
 */
sealed interface KeyNode {
    data class Question(val id: String, val text: String, val hint: String?, val options: List<Option>) : KeyNode
    data class Answer(val speciesIds: List<String>, val note: String?) : KeyNode
}

data class Option(val label: String, val next: KeyNode)

object IdentificationKey {

    private fun a(vararg ids: String, note: String? = null) = KeyNode.Answer(ids.toList(), note)
    private fun q(id: String, text: String, hint: String? = null, vararg options: Option) = KeyNode.Question(id, text, hint, options.toList())
    private infix fun String.to(node: KeyNode) = Option(this, node)

    private val salmonids = q(
        "salmonids_spots", "Jakie ma ubarwienie i kropki?", null,
        "Tęczowy lub różowy pas wzdłuż boku, gęste kropki na płetwie ogonowej" to a("pstrag_teczowy"),
        "Czerwone kropki w jasnych obwódkach, ogon zwykle bez kropek" to a("pstrag_potokowy"),
        "Srebrzysta, głównie czarne kropki (często w kształcie X)" to a(
            "troc", "losos", "troc_jeziorowa",
            note = "Łososia i troci nie da się pewnie odróżnić po jednym zdjęciu. Porównaj długość szczęki względem oka i grubość nasady ogona. Gdy nie masz pewności – wypuść rybę."
        ),
        "Bardzo duża, walcowata, z rzek górskich (Dunajec, Poprad, San)" to a("glowacica")
    )

    private val adipose = q(
        "adipose", "Jaki kształt ma płetwa grzbietowa i ubarwienie?", null,
        "Bardzo wysoka, długa płetwa grzbietowa („chorągiewka”)" to a("lipien"),
        "Srebrzysta, bez wyraźnych kropek, mały pysk" to q(
            "coregonids", "Który koniec pyska wystaje?", "Spójrz na rybę z boku przy zamkniętym pysku.",
            "Dolna szczęka wystaje (pysk górny), ryba zwykle 15–25 cm" to a("sielawa"),
            "Górna szczęka wystaje lub są równe, ryba większa" to a("sieja")
        ),
        "Ma wyraźne kropki na ciele" to salmonids
    )

    private val barbels = q(
        "barbels", "Ile wąsików i gdzie?", null,
        "Jeden wąsik na podbródku" to a("mietus"),
        "Dwa krótkie wąsiki w kącikach pyska, ciało śliskie, oliwkowe" to a("lin"),
        "Cztery wąsiki, pysk dolny, ciało wydłużone" to a("brzana"),
        "Cztery wąsiki, ciało wysokie, długa płetwa grzbietowa" to a("karp"),
        "Sześć wąsów (dwa bardzo długie), brak łusek, brak płetwy tłuszczowej" to a("sum"),
        "Osiem wąsików, płetwa tłuszczowa, brak łusek" to a(
            "sumik_karlowaty", "sumik_czarny",
            note = "Oba sumiki są inwazyjne – złowionego osobnika nie wolno wypuścić."
        )
    )

    private val twoDorsals = q(
        "two_dorsals", "Które cechy pasują najlepiej?", null,
        "Płetwy brzuszne zrośnięte w przyssawkę, ryba przydenna" to q(
            "gobies", "Który z opisów pasuje?", null,
            "Czarna plama na tylnej części pierwszej płetwy grzbietowej" to a("babka_bycza"),
            "Rurkowate otwory nosowe sięgające nad wargę, ryba mała" to a("babka_rurkonosa"),
            "Wyraźne ciemne pasy poprzeczne, kark bez łusek" to a("babka_lysa"),
            "Smukła, piaskowa z plamami, bez czarnej plamy na płetwie" to a("babka_szczupla"),
            "Nie wiem" to a("babka_bycza", "babka_lysa", "babka_rurkonosa", "babka_szczupla", note = "Wszystkie babki są inwazyjne – nie wypuszczaj.")
        ),
        "Wyraźne pręgi, czerwone płetwy brzuszne, czarna plama na końcu 1. płetwy grzbietowej" to a("okon"),
        "Kły w pysku, wydłużone ciało, szkliste oczy, płetwy bez czerwieni" to a("sandacz"),
        "Krępa, ciemna, duży pysk, płetwy zaokrąglone, brzuszne oddzielne" to a("trawianka")
    )

    private val chubLike = q(
        "chub_like", "Jaka jest krawędź płetwy odbytowej?", "Płetwa odbytowa leży na spodzie ciała przed ogonem.",
        "Wyraźnie wypukła (zaokrąglona), duża głowa, łuski z ciemną siateczką" to a("klen"),
        "Prosta lub lekko wklęsła, ryba wysoka, mały pysk, płetwy czerwonawe" to a("jaz"),
        "Wklęsła, ryba smukła i niewielka, oko żółtawe" to a("jelec"),
        "Nie potrafię ocenić" to a("klen", "jaz", "jelec", note = "Zrób zdjęcie z boku z rozłożoną płetwą odbytową.")
    )

    private val slender = q(
        "slender", "Która cecha pasuje najlepiej?", null,
        "Płetwy jaskrawoczerwone, płetwa grzbietowa ZA nasadą płetw brzusznych, pysk skierowany w górę" to a("wzdrega"),
        "Czerwona tęczówka oka, płetwa grzbietowa NAD nasadą płetw brzusznych" to a("ploc"),
        "Mała, bardzo srebrzysta, pysk górny, łuski łatwo odpadają" to a("ukleja"),
        "Walcowata, duże łuski z obrzeżeniem, oczy nisko, ryba duża" to a("amur"),
        "Mała (do 10 cm), ciemny pas wzdłuż boku, mały pysk górny" to a("czebaczek_amurski"),
        "Szeroki pysk / żadna z powyższych" to chubLike
    )

    private val deep = q(
        "deep", "Która cecha pasuje najlepiej?", null,
        "Pysk górny, skierowany ku górze; ciało smuklejsze" to a("rozpior"),
        "Długa płetwa grzbietowa, brak wąsików, duże łuski" to a("karas_srebrzysty"),
        "Pysk dolny z wydłużonym „nosem”" to a("certa"),
        "Płetwy piersiowe i brzuszne czerwonawe u nasady, duże oko" to a("krap"),
        "Płetwy szare/ciemne, małe oko, ciało brązowozłote" to a("leszcz"),
        "Nie potrafię odróżnić leszcza od krąpia" to a(
            "leszcz", "krap",
            note = "Policz łuski w linii bocznej: leszcz ok. 51–60, krąp ok. 44–50. Sprawdź kolor nasady płetw piersiowych."
        )
    )

    private val noAdiposeSingleDorsal = q(
        "body_shape", "Jaki kształt ma pysk i ciało?", null,
        "Długi, spłaszczony „kaczy” pysk, płetwa grzbietowa daleko z tyłu" to a("szczupak"),
        "Pysk dolny, poprzeczny, z ostrą rogową krawędzią" to a("swinka"),
        "Bardzo duży pysk, żuchwa wystaje z guzkiem" to a("bolen"),
        "Ciało wysokie, mocno bocznie spłaszczone" to deep,
        "Ciało wrzecionowate, typowa „biała ryba”" to slender
    )

    val root: KeyNode = q(
        "shape", "Jaki ogólny kształt ma ryba?", null,
        "Wężowaty, bez płetw brzusznych" to a("wegorz"),
        "Rzędy kostnych tarcz na ciele, wydłużony ryj" to a("jesiotr_ostronosy"),
        "Typowy kształt ryby" to q(
            "barbels_present", "Czy ryba ma wąsiki przy pysku?", null,
            "Tak" to barbels,
            "Nie" to q(
                "adipose_present", "Czy za płetwą grzbietową jest mała płetwa tłuszczowa (bez promieni)?",
                "Płetwa tłuszczowa to niewielki płat skóry między płetwą grzbietową a ogonem.",
                "Tak" to adipose,
                "Nie" to q(
                    "two_dorsals_present", "Czy ryba ma dwie oddzielne płetwy grzbietowe (pierwszą kolczastą)?", null,
                    "Tak" to twoDorsals,
                    "Nie" to noAdiposeSingleDorsal
                )
            )
        )
    )

    /** Wszystkie gatunki osiągalne w kluczu – używane w testach spójności z bazą. */
    fun allSpeciesIds(node: KeyNode = root): Set<String> = when (node) {
        is KeyNode.Answer -> node.speciesIds.toSet()
        is KeyNode.Question -> node.options.flatMap { allSpeciesIds(it.next) }.toSet()
    }

    fun findQuestion(id: String, node: KeyNode = root): KeyNode.Question? = when (node) {
        is KeyNode.Answer -> null
        is KeyNode.Question -> if (node.id == id) node else node.options.firstNotNullOfOrNull { findQuestion(id, it.next) }
    }
}
