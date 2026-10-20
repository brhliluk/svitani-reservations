package cz.svitaninymburk.projects.reservations.slot.ui.i18n

import cz.svitaninymburk.projects.reservations.slot.ScheduleProblem
import cz.svitaninymburk.projects.reservations.slot.SlotServiceProblem

object CsSlotAdminStrings : SlotAdminStrings {
    override val navLabel = "Hlídání"
    override val listTitle = "Hlídání"
    override val listSubtitle = "Služby, kde si rodiče rezervují libovolný úsek v otevírací době."
    override val newService = "Nová služba"
    override val noServicesYet = "Zatím tu není žádná služba."
    override val published = "Zveřejněno"
    override val hidden = "Skryto"
    override fun serviceSummary(price: String, capacity: Int, stepMinutes: Long) =
        "$price za hodinu a dítě · kapacita $capacity · krok $stepMinutes min"
    override val loadError = "Nepodařilo se načíst data."
    override val retry = "Zkusit znovu"

    override val backToList = "Zpět na hlídání"
    override val tabDay = "Den"
    override val tabSchedule = "Rozvrh"
    override val tabSettings = "Nastavení"
    override val publish = "Zveřejnit"
    override val unpublish = "Skrýt"
    override val deleteService = "Smazat službu"
    override val deleteTitle = "Smazat službu?"
    override fun deleteText(title: String, reservationCount: Int) = buildString {
        append("Služba „$title“ se smaže i s rozvrhem.")
        if (reservationCount > 0) {
            append(" Zruší se i aktivní rezervace (budoucích: $reservationCount) a rodiče dostanou e-mail.")
        }
    }
    override val refundCredit = "Vrátit zaplacené jako kredit do peněženky"
    override val confirmDelete = "Smazat"
    override val saved = "Uloženo."
    override val publishedToast = "Služba je zveřejněná."
    override val hiddenToast = "Služba je skrytá, vidí ji jen admin."
    override val deletedToast = "Služba je smazaná."

    override val previousDay = "Předchozí den"
    override val nextDay = "Další den"
    override val today = "Dnes"
    override val print = "Tisk"
    override val closedThatDay = "Tento den je zavřeno."
    override val reservationsHeading = "Rezervace"
    override val noReservationsThatDay = "Na tento den nejsou žádné rezervace."
    override fun occupancy(occupied: Int, capacity: Int) = "$occupied/$capacity"
    override fun dayTotal(reservations: Int, children: String) = "Rezervací: $reservations · $children"

    override val periodsHeading = "Otevírací doba"
    override val periodsHint = "Každé období má vlastní týden. Období se nesmí překrývat."
    override val validFrom = "Platí od"
    override val validTo = "Platí do"
    override val addPeriod = "Přidat období"
    override val removePeriod = "Odebrat období"
    override val addWindow = "Přidat čas"
    override val removeWindow = "Odebrat čas"
    override val closedDay = "zavřeno"
    override val exceptionsHeading = "Výjimky"
    override val exceptionsHint = "Výjimka nahradí rozvrh daného dne: buď je zavřeno, nebo platí jiný čas."
    override val exceptionDate = "Datum"
    override val exceptionClosed = "Celý den zavřeno"
    override val addException = "Přidat výjimku"
    override val removeException = "Odebrat výjimku"
    override val noExceptions = "Žádné výjimky."
    override val noPeriods = "Zatím není nastavená žádná otevírací doba."
    override val saveSchedule = "Uložit rozvrh"
    override val affectedTitle = "Změna se dotkne rezervací"
    override fun affectedText(count: Int) =
        "Po uložení bude $count aktivních rezervací mimo otevírací dobu. Nic se samo neruší — " +
            "případně je zrušte v přehledu rezervací."
    override val saveAnyway = "Přesto uložit"
    override fun scheduleProblem(problem: ScheduleProblem) = when (problem) {
        ScheduleProblem.WINDOW_EMPTY -> "Čas musí končit později, než začíná."
        ScheduleProblem.PERIOD_DATES -> "Období musí končit později, než začíná."
        ScheduleProblem.PERIODS_OVERLAP -> "Období se překrývají."
        ScheduleProblem.EXCEPTION_WINDOW_MISSING -> "Výjimka, která není zavřeno, potřebuje čas."
        ScheduleProblem.INCOMPLETE -> "Vyplňte všechna data a časy."
    }

    override val titleLabel = "Název"
    override val descriptionLabel = "Popis"
    override val stepLabel = "Krok (zarovnání začátku a konce)"
    override fun stepOption(minutes: Long) = "$minutes min"
    override val minDurationLabel = "Nejkratší rezervace"
    override val maxDurationLabel = "Nejdelší rezervace"
    override val minDurationFieldHint = "0 = bez minima, stačí jeden krok."
    override val maxDurationFieldHint = "0 = bez omezení."
    override val minutesSuffix = "min"
    override val priceLabel = "Cena za hodinu a dítě"
    override val capacityLabel = "Kapacita (dětí současně)"
    override val maxSeatsLabel = "Nejvíc dětí na rezervaci"
    override val deadlineLabel = "Uzávěrka (hodin před začátkem)"
    override val deadlineHint = "0 = rezervovat jde až do začátku."
    override val horizonLabel = "Rezervace dopředu (dní)"
    override val formFieldsHeading = "Pole rezervačního formuláře"
    override val formFieldsHint = "Např. jména a věk dětí nebo poznámka, kdy dítě přivedete. Cenu hodinami násobí hlídání samo."
    override val defaultChildrenField = "Jména a věk dětí"
    override val defaultNoteField = "Poznámka (např. kdy dítě přivedete a vyzvednete)"
    override val save = "Uložit"
    override val continueToSchedule = "Pokračovat na časové nastavení"
    override fun serviceProblem(problem: SlotServiceProblem) = when (problem) {
        SlotServiceProblem.TITLE_BLANK -> "Vyplňte název."
        SlotServiceProblem.STEP_INVALID -> "Krok musí být 15, 30 nebo 60 minut."
        SlotServiceProblem.MIN_DURATION_INVALID -> "Nejkratší rezervace musí být celý počet kroků (0 = bez minima)."
        SlotServiceProblem.MAX_DURATION_INVALID -> "Nejdelší rezervace musí být aspoň nejkratší, aspoň jeden krok a celý počet kroků (0 = bez omezení)."
        SlotServiceProblem.PRICE_NEGATIVE -> "Cena nesmí být záporná."
        SlotServiceProblem.CAPACITY_INVALID -> "Kapacita musí být aspoň 1."
        SlotServiceProblem.MAX_SEATS_INVALID -> "Počet dětí na rezervaci musí být od 1 do kapacity."
        SlotServiceProblem.HORIZON_INVALID -> "Rezervovat musí jít aspoň den dopředu."
        SlotServiceProblem.DEADLINE_NEGATIVE -> "Uzávěrka nesmí být záporná."
        SlotServiceProblem.NO_PAYMENT_TYPE -> "Vyberte aspoň jeden způsob platby."
        SlotServiceProblem.OWNER_EMAIL_INVALID -> "Zkontrolujte e-maily lektorů."
        SlotServiceProblem.TIME_MULTIPLIER_FIELD -> "Časové pole nesmí násobit cenu, hlídání ji násobí samo."
    }
    override val serviceNotFound = "Služba nenalezena."
}
