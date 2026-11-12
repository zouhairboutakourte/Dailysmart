package com.dailysmart.app.v2

private data class DailyQuoteEntry(
    val english: String,
    val french: String,
    val arabic: String
)

/** Original, offline daily inspiration. The same date always selects the same quote. */
object DailyQuoteProvider {
    private val quotes = listOf(
        DailyQuoteEntry(
            "Small steps, repeated daily, create meaningful change.",
            "De petits pas répétés chaque jour créent de grands changements.",
            "خطوات صغيرة تتكرر كل يوم تصنع تغييراً كبيراً."
        ),
        DailyQuoteEntry(
            "Your money needs a plan, not pressure.",
            "Votre argent a besoin d’un plan, pas de pression.",
            "فلوسك محتاجة تخطيط، ماشي الضغط."
        ),
        DailyQuoteEntry(
            "Progress begins when you show up again.",
            "Le progrès commence quand vous recommencez.",
            "التقدم كيبدا ملي كتعاود تحاول."
        ),
        DailyQuoteEntry(
            "A clear priority can make a busy day feel lighter.",
            "Une priorité claire rend une journée chargée plus légère.",
            "أولوية واضحة كتخفف عليك نهار عامر."
        ),
        DailyQuoteEntry(
            "Save a little today to give tomorrow more choices.",
            "Épargnez un peu aujourd’hui pour avoir plus de choix demain.",
            "وفر شوية اليوم باش تكون عندك اختيارات أكثر غدا."
        ),
        DailyQuoteEntry(
            "Consistency matters more than a perfect start.",
            "La régularité compte plus qu’un départ parfait.",
            "الاستمرار أهم من البداية المثالية."
        ),
        DailyQuoteEntry(
            "You do not need a perfect day to make progress.",
            "Vous n’avez pas besoin d’une journée parfaite pour avancer.",
            "ما محتاجش نهار مثالي باش تحقق التقدم."
        ),
        DailyQuoteEntry(
            "Spend with intention; make room for what matters.",
            "Dépensez avec intention et gardez de la place pour l’essentiel.",
            "صرف بوعي وخلي المساحة للحوايج اللي مهمة."
        ),
        DailyQuoteEntry(
            "One completed task is better than ten postponed plans.",
            "Une tâche terminée vaut mieux que dix projets reportés.",
            "مهمة كملتيها حسن من عشرة خطط مؤجلة."
        ),
        DailyQuoteEntry(
            "Start where you are and improve one thing at a time.",
            "Commencez là où vous êtes et améliorez une chose à la fois.",
            "بدا منين نتا وحسن حاجة وحدة كل مرة."
        )
    )

    fun quoteFor(dayOfYear: Int, language: String): String {
        val index = Math.floorMod(dayOfYear - 1, quotes.size)
        val quote = quotes[index]
        return when (language) {
            "fr" -> quote.french
            "ar" -> quote.arabic
            else -> quote.english
        }
    }
}
