package cz.svitaninymburk.projects.reservations.i18n.cs

import cz.svitaninymburk.projects.reservations.i18n.AppStrings


object CsStrings : AppStrings {
    override val appName = "Rezervace"
    override val logIn = "Přihlásit se"
    override val reserve = "Rezervovat"
    override val dashboard = "Přehled"
    override val contact = "Kontakt"
    override val myReservations = "Moje rezervace"

    // Dashboard
    override val schedule = "Program"
    override val catalog = "Nabídka"
    override val listView = "Seznam"
    override val calendarView = "Kalendář"

    // Headers
    override val allEvents = "Všechny akce"
    override val openCourses = "Otevřené kroužky"
    override val upcomingEvents = "Nadcházející akce"
    override val individualEvents = "Jednotlivé termíny"

    // States
    override val noEvents = "Žádné události"
    override val noEventsFoundForFilter = "Pro tento výběr nejsou vypsané žádné termíny."
    override val calendarUnderConstruction = "Kalendář je zatím ve výstavbě :)"

    // Filter
    override val filter = "Filtr"
    override val clearFilterTooltip = "Kliknutím zrušíte filtr"

    // Cards
    override val course = "Kroužek"
    override val courseLessons = "lekcí"
    override val courseSignUp = "Přihlásit se"
    override val partOfCourse = "Součást kroužku"
    override val showDates = "Termíny"
    override val detail = "Detail"
    override val priceLabel = "Cena"
    override val maxCapacity = "Max"

    // Forms
    override val nameLabel = "Jméno"
    override val nameHint = "Jan"
    override val surnameLabel = "Příjmení"
    override val surnameHint = "Novák"
    override val emailLabel = "Email"
    override val emailHint = "jan.novak@email.cz"
    override val phoneLabel = "Telefon"
    override val phoneHint = "+420 123 456 789"
    override val seatCountLabel = "Počet míst"
    override val seatCountHint = "1"

    private val months = listOf(
        "Leden", "Únor", "Březen", "Duben", "Květen", "Červen",
        "Červenec", "Srpen", "Září", "Říjen", "Listopad", "Prosinec"
    )
    private val days = listOf(
        "Pondělí", "Úterý", "Středa", "Čtvrtek", "Pátek", "Sobota", "Neděle"
    )
    private val shortDays = listOf(
        "Po", "Út", "St", "Čt", "Pá", "So", "Ne"
    )

    override fun monthName(index: Int) = months.getOrElse(index) { "" }
    override fun dayName(index: Int) = days.getOrElse(index) { "" }
    override fun shortDayName(index: Int) = shortDays.getOrElse(index) { "" }
}