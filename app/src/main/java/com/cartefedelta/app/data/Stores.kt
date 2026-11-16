package com.cartefedelta.app.data

/** Negozio preimpostato: solo nome e colore (nessun logo). */
data class Store(val key: String, val name: String, val color: Long, val category: String)

object Stores {
    val all: List<Store> = listOf(
        // Supermercati
        Store("conad", "Conad", 0xFFE30613, "Supermercati"),
        Store("coop", "Coop", 0xFFE2001A, "Supermercati"),
        Store("esselunga", "Esselunga", 0xFF0072BC, "Supermercati"),
        Store("carrefour", "Carrefour", 0xFF004E9F, "Supermercati"),
        Store("lidl", "Lidl", 0xFF0050AA, "Supermercati"),
        Store("eurospin", "Eurospin", 0xFF003D7C, "Supermercati"),
        Store("pam", "Pam Panorama", 0xFFD9001B, "Supermercati"),
        Store("despar", "Despar / Eurospar", 0xFF00843D, "Supermercati"),
        Store("famila", "Famila", 0xFFE30613, "Supermercati"),
        Store("bennet", "Bennet", 0xFFE4002B, "Supermercati"),
        Store("iper", "Iper La grande i", 0xFFE20613, "Supermercati"),
        Store("tigros", "Tigros", 0xFFE30613, "Supermercati"),
        Store("penny", "Penny", 0xFFCD1719, "Supermercati"),
        Store("md", "MD", 0xFFFFD100, "Supermercati"),
        Store("aldi", "Aldi", 0xFF00005F, "Supermercati"),
        Store("crai", "Crai", 0xFFE30613, "Supermercati"),
        Store("sigma", "Sigma", 0xFFE30613, "Supermercati"),
        Store("deco", "Decò", 0xFFE2001A, "Supermercati"),
        Store("unes", "Unes / U2", 0xFF00A0E1, "Supermercati"),
        Store("todis", "Todis", 0xFF009640, "Supermercati"),
        Store("in", "In's Mercato", 0xFFE30613, "Supermercati"),
        Store("ipercoop", "Ipercoop", 0xFFE2001A, "Supermercati"),
        Store("selex", "Mercatò / Selex", 0xFF00A651, "Supermercati"),
        Store("naturasi", "NaturaSì", 0xFF6AA84F, "Supermercati"),
        Store("carrefour_market", "Carrefour Market", 0xFF004E9F, "Supermercati"),
        Store("simply", "Simply", 0xFFE30613, "Supermercati"),
        Store("gigante", "Il Gigante", 0xFFE30613, "Supermercati"),
        Store("poli", "Poli / Regina", 0xFF009FE3, "Supermercati"),
        Store("dimeglio", "Dimeglio", 0xFFE30613, "Supermercati"),
        Store("ali", "Alì", 0xFFE30613, "Supermercati"),
        Store("aeo", "Super A&O", 0xFF00843D, "Supermercati"),
        Store("basko", "Basko", 0xFF0067B1, "Supermercati"),
        Store("gros", "Gros", 0xFFE30613, "Supermercati"),
        // Casa e persona
        Store("tigota", "Tigotà", 0xFFE5007E, "Casa e persona"),
        Store("acquaesapone", "Acqua & Sapone", 0xFF0095DA, "Casa e persona"),
        Store("caddys", "Caddy's", 0xFFE4007C, "Casa e persona"),
        Store("risparmio", "Risparmio Casa", 0xFFE30613, "Casa e persona"),
        Store("sephora", "Sephora", 0xFF000000, "Casa e persona"),
        Store("douglas", "Douglas", 0xFF0F3A3F, "Casa e persona"),
        Store("kiko", "Kiko", 0xFF000000, "Casa e persona"),
        Store("pinalli", "Pinalli", 0xFF000000, "Casa e persona"),
        Store("farmacia", "Farmacia", 0xFF00A651, "Casa e persona"),
        // Fai da te, elettronica, altro
        Store("ikea", "IKEA Family", 0xFF0058A3, "Altri negozi"),
        Store("leroy", "Leroy Merlin", 0xFF78BE20, "Altri negozi"),
        Store("obi", "OBI", 0xFFFF7E00, "Altri negozi"),
        Store("brico", "Brico", 0xFFE30613, "Altri negozi"),
        Store("mediaworld", "MediaWorld", 0xFFDF0000, "Altri negozi"),
        Store("unieuro", "Unieuro", 0xFF0057A8, "Altri negozi"),
        Store("euronics", "Euronics", 0xFF002D72, "Altri negozi"),
        Store("decathlon", "Decathlon", 0xFF0082C3, "Altri negozi"),
        Store("feltrinelli", "laFeltrinelli", 0xFFE30613, "Altri negozi"),
        Store("mondadori", "Mondadori", 0xFFE30613, "Altri negozi"),
        Store("giunti", "Giunti al Punto", 0xFFE30613, "Altri negozi"),
        Store("zara", "Zara", 0xFF000000, "Altri negozi"),
        Store("ovs", "OVS", 0xFF00205B, "Altri negozi"),
        Store("hm", "H&M", 0xFFE50010, "Altri negozi"),
        Store("calzedonia", "Calzedonia", 0xFF000000, "Altri negozi"),
        Store("arcaplanet", "Arcaplanet", 0xFF00A0DF, "Altri negozi"),
        Store("isola", "Isola dei Tesori", 0xFF009FE3, "Altri negozi"),
        Store("eni", "Eni Live", 0xFFFFD100, "Altri negozi"),
        Store("q8", "Q8 Star", 0xFF0066B3, "Altri negozi"),
        Store("trenitalia", "CartaFRECCIA", 0xFFC8102E, "Altri negozi"),
        Store("biblioteca", "Biblioteca", 0xFF6D4C41, "Altri negozi"),
        Store("palestra", "Palestra", 0xFF37474F, "Altri negozi"),
    ).sortedWith(compareBy({ it.category }, { it.name.lowercase() }))

    fun byKey(key: String?): Store? = all.firstOrNull { it.key == key }

    /** Colori tra cui scegliere per le tessere personalizzate. */
    val palette: List<Long> = listOf(
        0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1, 0xFF3949AB, 0xFF1E88E5,
        0xFF039BE5, 0xFF00ACC1, 0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFC0CA33,
        0xFFFDD835, 0xFFFFB300, 0xFFFB8C00, 0xFFF4511E, 0xFF6D4C41, 0xFF546E7A,
        0xFF212121
    )
}
