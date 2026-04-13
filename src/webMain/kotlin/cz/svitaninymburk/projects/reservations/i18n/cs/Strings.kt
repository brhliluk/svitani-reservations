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
    override val backToDashboard = "Zpět na přehled"

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
    override val filterIsActive: (String) -> String = { "Filtr: $it" }

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

    // Reservation Detail
    override val reservationSummary = "Souhrn rezervace"
    override val status = "Stav"
    override val name = "Jméno"
    override val totalPrice = "Cena celkem"
    override val cancelReservation = "Zrušit rezervaci"
    override val qrPayment = "Platba QR kódem"
    override val shareOrDownload = "Kliknutím sdílet / stáhnout"
    override val accountNumber = "Číslo účtu"
    override val variableSymbol = "VS"
    override val reservationCancelledMessage = "Tato rezervace je zrušena."
    override val reservationPaidMessage = "Vše je uhrazeno. Těšíme se na vás!"
    override val copied = "Zkopírováno"

    // Reservation Statuses
    override val reservationCreated = "Rezervace vytvořena"
    override val waitingForPayment = "Čekáme na platbu"
    override val unpaid = "Nezaplaceno"
    override val reservationConfirmed = "Rezervace potvrzena"
    override val everythingIsOK = "Vše je v pořádku"
    override val paid = "Zaplaceno"
    override val reservationCancelled = "Rezervace zrušena"
    override val reservationNotValid = "Tato rezervace není platná"
    override val cancelled = "Stornováno"
    override val reservationRejected = "Rezervace zamítnuta"
    override val reservationNotApproved = "Organizátor rezervaci neschválil"
    override val rejected = "Zamítnuto"
    override val copyright = "© 2024 Reservation System"

    // Reservation Modal
    override val reservationFor: (String) -> String = { "Rezervace: $it" }
    override val formTotalPrice = "Celková cena"
    override val free = "Zdarma"
    override val currency = "Kč"
    override val persons = "osob"
    override val moreDetails = "Další údaje"
    override val paymentType = "Typ platby"
    override val bankTransfer = "Převodem"
    override val onSite = "Hotově na místě"
    override val cancel = "Zrušit"
    override val close = "zavřít"

    // Calendar
    override val more: (Int) -> String = { "+$it další" }

    // Admin Dashboard
    override val dashboardWelcome = "Vítejte zpět! Takhle to aktuálně vypadá s vašimi rezervacemi."
    override val dashboardTodayParticipants = "Dnešní účastníci"
    override val dashboardPendingPayment = "Čeká na platbu"
    override val dashboardPendingPaymentsDesc: (Int) -> String = { "Celkem $it rezervací" }
    override val dashboardFreeSpots = "Volná místa"
    override val dashboardFreeSpotsDesc = "Na akcích v tomto týdnu"
    override val dashboardUpcomingEvents = "Nejbližší události"
    override val dashboardNoUpcomingEvents = "Žádné nadcházející události."
    override val dashboardPendingReservations = "Poslední neuhrazené rezervace"
    override val dashboardAllPaid = "Všechny rezervace jsou uhrazené!"

    // Admin Reservations Screen
    override val allReservations = "Všechny rezervace"
    override val reservationsSubtitle = "Přehled a správa všech přihlášek"
    override val search = "Hledat"
    override val searchPlaceholder = "Hledat jméno, e-mail nebo VS..."
    override val clearSearch = "Zrušit vyhledávání"
    override val tableHeaderEvent = "Událost / Kurz"
    override val noReservationsForSearch: (String) -> String = { "Nebyly nalezeny žádné rezervace pro '$it'." }
    override val noReservations = "Zatím neexistují žádné rezervace."

    // Admin Layout / Sidebar
    override val adminPanel = "Admin Panel"
    override val adminNavTitle = "Administrace"
    override val navEvents = "Události a Kurzy"
    override val navReservations = "Rezervace"
    override val navUsers = "Uživatelé"
    override val logOut = "Odhlásit se"

    // Admin Events Screen
    override val adminEventsSubtitle = "Správa katalogu a otevírání nových termínů"
    override val createNew = "Vytvořit novou"
    override val emptyTemplates = "Zatím tu nic není. Vytvořte první šablonu!"
    override val noDates = "Bez termínů"
    override val datesCount: (Int) -> String = { "$it termínů" }
    override val addDate = "Termín"
    override val adminCourse = "Kurz"
    override val noInstancesMessage = "Žádné termíny. Vytvořte první kliknutím na + Termín nebo + Kurz."
    override val badgeOneTime = "Jednorázovka"
    override val capacityFull = "PLNO"
    override val showLess = "Zobrazit méně"
    override val showMore: (Int) -> String = { "Zobrazit dalších $it" }
    override val loadingError: (String) -> String = { "Chyba načítání: $it" }

    // Shared Admin Form Elements
    override val descriptionLabel = "Popis"
    override val basicInfoHeading = "Základní údaje"
    override val defaultPriceLabel = "Výchozí cena"
    override val capacityPersonLabel = "Kapacita osob"
    override val defaultDurationLabel = "Výchozí délka"
    override val durationLabel = "Délka"
    override val hours = "h"
    override val minutes = "min"
    override val allowedPaymentsLabel = "Povolené platby"
    override val paymentOnSite = "Na místě"
    override val templateSelectionLabel = "Ze které šablony vycházíme?"
    override val templatePlaceholder = "-- Vyberte šablonu --"
    override val noTemplatesHeading = "Žádné šablony"
    override val createTemplateButton = "Vytvořit šablonu"
    override val errorToast: (String) -> String = { "Chyba: $it" }
    override val toastTemplatesLoadError: (String) -> String = { "Chyba při načítání šablon: $it" }

    // Event Definition Form
    override val eventNameLabel = "Název (např. Jóga pro začátečníky)"
    override val newTemplateTitle = "Nová šablona události"
    override val newTemplateSubtitle = "Vytvořte základní definici, ze které pak budete vypisovat termíny."
    override val recurrenceHeading = "Opakování"
    override val recurrenceTypeLabel = "Typ opakování"
    override val recurrenceNone = "Žádné"
    override val recurrenceDaily = "Denně"
    override val recurrenceWeekly = "Týdně"
    override val recurrenceMonthly = "Měsíčně"
    override val recurrenceEndLabel = "Opakovat do"
    override val customFieldsHeading = "Vlastní pole rezervačního formuláře"
    override val addFieldButton = "Přidat pole"
    override val addTextField = "Textové pole"
    override val addNumberField = "Číselné pole"
    override val addBooleanField = "Zaškrtávátko (Ano/Ne)"
    override val addTimeRangeField = "Časový úsek"
    override val noCustomFieldsMessage = "Zatím žádná vlastní pole. Lidé vyplní jen Jméno, E-mail a Telefon."
    override val fieldKeyLabel = "Klíč (pro systém)"
    override val fieldLabelLabel = "Zobrazený text (Label)"
    override val fieldRequired = "Povinné pole"
    override val fieldTypeText = "Typ: Text"
    override val fieldTypeNumber = "Typ: Číslo"
    override val fieldTypeBoolean = "Typ: Ano/Ne"
    override val fieldTypeTimeRange = "Typ: Časový úsek"
    override val validationNameRequired = "Název je povinný"
    override val validationRecurrenceEndRequired = "Zadejte datum konce opakování."
    override val validationRecurrenceDateFormat = "Neplatný formát data konce opakování."
    override val templateSavedToast = "Šablona úspěšně uložena!"

    // Event Instance Form
    override val newInstanceTitle = "Vypsat nový termín"
    override val newInstanceSubtitle = "Vyberte šablonu, nastavte datum a čas. Údaje můžete pro tento termín libovolně upravit."
    override val noTemplatesInstanceMessage = "Než vypíšete termín, musíte vytvořit alespoň jednu šablonu události."
    override val dateLabelField = "Datum konání"
    override val timeLabelField = "Čas začátku"
    override val overrideHeading = "Úpravy pro tento konkrétní termín"
    override val overrideDescription = "Tato pole jsou předvyplněná podle šablony. Pokud je změníte, změna se projeví pouze u tohoto jednoho termínu."
    override val instanceTitleLabel = "Název termínu"
    override val recurrencePreviewHeading: (Int) -> String = { "Náhled termínů ($it)" }
    override val recurrencePreviewError = "Datum konce je před datem začátku. Žádné termíny nevzniknou."
    override val createInstanceButton = "Vypsat termín"
    override val createInstancesButton: (Int) -> String = { "Vypsat $it termínů" }
    override val validationDateTimeRequired = "Musíte vybrat datum a čas konání."
    override val validationDateTimeFormat = "Neplatný formát data nebo času."
    override val validationNoDates = "Žádné termíny ke vytvoření."
    override val toastInstanceCreateError: (String, String) -> String = { dt, err -> "Chyba při vytváření termínu $dt: $err" }
    override val toastInstancesCreated: (Int) -> String = { "Vytvořeno $it termínů!" }
    override val toastInstanceCreated = "Termín byl úspěšně vypsán!"

    // Event Series Form
    override val newSeriesTitle = "Vytvořit kurz"
    override val newSeriesSubtitle = "Nastavte období a počet lekcí. Detaily jsou předvyplněné ze šablony."
    override val noTemplatesSeriesMessage = "Než vytvoříte kurz, musíte nejprve vytvořit šablonu události."
    override val startDateLabel = "Datum začátku"
    override val endDateLabel = "Datum konce"
    override val lessonCountLabel = "Počet lekcí"
    override val autoFillAlert = "Datum konce a počet lekcí byly předvyplněny ze šablony. Můžete je upravit."
    override val seriesOverrideHeading = "Úpravy pro tento kurz"
    override val seriesOverrideDescription = "Předvyplněno ze šablony. Změny se projeví pouze u tohoto kurzu."
    override val seriesTitleLabel = "Název kurzu"
    override val fullCoursePriceLabel = "Cena (celý kurz)"
    override val createSeriesButton = "Vytvořit kurz"
    override val validationDatesRequired = "Musíte vyplnit datum začátku a konce."
    override val validationSeriesTitleRequired = "Název kurzu je povinný."
    override val validationStartDateFormat = "Neplatný formát data začátku."
    override val validationEndDateFormat = "Neplatný formát data konce."
    override val validationEndBeforeStart = "Datum konce musí být po datu začátku."
    override val toastSeriesCreated = "Kurz byl úspěšně vytvořen!"

    // Event Create Choose
    override val chooseTypeTitle = "Jak chcete vypsat šablonu?"
    override val chooseTypeSubtitle = "Vyberte typ termínu"
    override val chooseTypeSubtitleWith: (String) -> String = { "Šablona: $it" }
    override val instanceCardTitle = "Jednorázový termín"
    override val instanceCardDescription = "Jednorázová akce s konkrétním datem a časem."
    override val seriesCardTitle = "Kurz / více lekcí"
    override val seriesCardDescription = "Opakující se kurz s více lekcemi v daném období."

    // Event Detail
    override val occupancyStatTitle = "Obsazenost"
    override val capacityFilled = "Kapacita naplněna"
    override val spotsRemaining: (Int) -> String = { "Zbývá $it míst" }
    override val revenueStatTitle = "Vybráno (Potvrzené)"
    override val revenueStatDesc = "Celkem od zaplacených"
    override val tableHeaderParticipant = "Účastník"
    override val tableHeaderSeats = "Místa"
    override val tableHeaderPaymentStatus = "Stav platby"
    override val tableHeaderActions = "Akce"
    override val noParticipants = "Zatím žádní přihlášení účastníci."
    override val statusOnSiteBadge = "Na místě"
    override val statusWaiting = "Čeká"
    override val paymentMethodCash = "Hotově"
    override val tooltipAcceptCash = "Přijmout hotovost"
    override val tooltipMarkPaid = "Označit jako zaplacené"
    override val buttonCollect = "Vybrat"
    override val tooltipCancelReservation = "Zrušit rezervaci"
    override val modalConfirmPaymentTitle = "Potvrdit platbu"
    override val modalCancelReservationTitle = "Zrušit rezervaci"
    override val modalConfirmPaymentMsgPre = "Opravdu chcete označit rezervaci pro účastníka "
    override val modalConfirmPaymentMsgPost = " jako zaplacenou?"
    override val modalCancelMsgPre = "Opravdu chcete zrušit rezervaci pro účastníka "
    override val modalCancelMsgPost = "? Tato akce je nevratná."
    override val modalBack = "Zpět"
    override val modalConfirmAction = "Ano, potvrdit"
    override val modalConfirmCancelAction = "Ano, zrušit"
    override val toastPaymentConfirmed: (String) -> String = { "Platba od $it potvrzena!" }
    override val toastReservationCancelled: (String) -> String = { "Rezervace od $it byla zrušena!" }
    override val invalidEventId = "Neplatné ID události."

    // On-site payment detail
    override val onSitePaymentInfo = "Platbu prosím uhraďte hotově na místě."

    // Admin Users Screen
    override val allUsers = "Uživatelé"
    override val usersSubtitle = "Přehled a správa uživatelských účtů"
    override val usersSearchPlaceholder = "Hledat jméno nebo e-mail..."
    override val tableHeaderAuthType = "Přihlášení"
    override val tableHeaderReservations = "Rezervace"
    override val noUsers = "Zatím žádní uživatelé."
    override val noUsersForSearch: (String) -> String = { "Nebyli nalezeni žádní uživatelé pro '$it'." }
    override val authTypeEmail = "E-mail"
    override val authTypeGoogle = "Google"
    override val tooltipChangeRole = "Změnit roli"
    override val tooltipDeleteUser = "Smazat uživatele"
    override val modalChangeRoleTitle = "Změnit roli"
    override val modalChangeRoleMsgPre = "Opravdu chcete změnit roli uživatele "
    override val modalChangeRoleMsgMid = " na "
    override val modalChangeRoleMsgPost = "?"
    override val modalDeleteUserTitle = "Smazat uživatele"
    override val modalDeleteUserMsgPre = "Opravdu chcete smazat účet uživatele "
    override val modalDeleteUserMsgPost = "? Tato akce je nevratná."
    override val toastRoleChanged: (String) -> String = { "Role uživatele $it byla změněna." }
    override val toastUserDeleted: (String) -> String = { "Účet uživatele $it byl smazán." }
    override val roleAdmin = "Administrátor"
    override val roleUser = "Uživatel"

    // Attendance Screen
    override val attendanceButton = "Prezenční listina"
    override val attendancePrintHeader: (String) -> String = { "Prezence: $it" }
    override val printList = "Tisknout"
    override val emptyRowsLabel = "Prázdné řádky pro dopsání:"
    override val tableHeaderPresence = "Přítomnost / Podpis"

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