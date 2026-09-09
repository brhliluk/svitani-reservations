package cz.svitaninymburk.projects.reservations.service

import arrow.core.right
import cz.svitaninymburk.projects.reservations.error.EmailError
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonOptOut
import cz.svitaninymburk.projects.reservations.service.WalletService
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class SeriesLessonOptOutServiceTest {

    /**
     * A fixed user ID used as the "logged-in caller" in tests.
     * Tests set registeredUserId on reservations to this value so
     * the ownership check in cancelReservation passes.
     */
    private val testCallerId: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000001")

    /**
     * Subclass that bypasses the Ktor call context and returns a fixed caller ID,
     * allowing unit tests to run without a real HTTP request.
     */
    private inner class TestReservationService(
        instanceRepo: InMemoryEventInstanceRepository,
        seriesRepo: InMemoryEventSeriesRepository,
        defRepo: InMemoryEventDefinitionRepository,
        reservationRepo: InMemoryReservationRepository,
        optOutRepo: InMemorySeriesLessonOptOutRepository,
        walletSvc: WalletService,
        private val callerId: Uuid? = testCallerId,
    ) : ReservationService(
        eventInstanceRepository = instanceRepo,
        eventSeriesRepository = seriesRepo,
        eventDefinitionRepository = defRepo,
        reservationRepository = reservationRepo,
        emailService = ConsoleEmailService(),
        lectorEmailService = ConsoleEmailService(),
        qrCodeService = StubQrCodeGenerator(),
        paymentTrigger = PaymentTrigger(),
        appBaseUrl = "https://test.example.com",
        seriesLessonOptOutRepository = optOutRepo,
        walletService = walletSvc,
        walletEmailService = ConsoleEmailService(),
        appSettingsProvider = AppSettingsProvider.forTest(AppSettings(
            bankAccountNumber = "", fioToken = "", senderEmail = "",
            gmailAppPassword = "", senderDisplayName = "",
        )),
    ) {
        override suspend fun currentCallerUserId(): Uuid? = callerId
    }

    private fun makeService(
        instanceRepo: InMemoryEventInstanceRepository = InMemoryEventInstanceRepository(),
        seriesRepo: InMemoryEventSeriesRepository = InMemoryEventSeriesRepository(),
        defRepo: InMemoryEventDefinitionRepository = InMemoryEventDefinitionRepository(),
        reservationRepo: InMemoryReservationRepository = InMemoryReservationRepository(),
        optOutRepo: InMemorySeriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
        walletSvc: WalletService = WalletService(InMemoryWalletRepository()),
        callerId: Uuid? = testCallerId,
    ) = TestReservationService(
        instanceRepo = instanceRepo,
        seriesRepo = seriesRepo,
        defRepo = defRepo,
        reservationRepo = reservationRepo,
        optOutRepo = optOutRepo,
        walletSvc = walletSvc,
        callerId = callerId,
    )

    private fun makeSeries(id: Uuid = Uuid.random()) = EventSeries(
        id = id,
        definitionId = Uuid.random(),
        title = "Test Series",
        description = "",
        price = 500.0,
        capacity = 10,
        occupiedSpots = 1,
        startDate = LocalDate(2026, 1, 1),
        endDate = LocalDate(2026, 12, 31),
        lessonCount = 10,
    )

    private fun makeInstance(
        seriesId: Uuid,
        id: Uuid = Uuid.random(),
        startDateTime: LocalDateTime = LocalDateTime(2099, 12, 1, 10, 0),
        isCancelled: Boolean = false,
        ownerEmails: List<String> = emptyList(),
    ) = EventInstance(
        id = id,
        definitionId = Uuid.random(),
        seriesId = seriesId,
        title = "Test Lesson",
        description = "",
        startDateTime = startDateTime,
        endDateTime = startDateTime.let { LocalDateTime(it.year, it.month, it.dayOfMonth, it.hour + 1, 0) },
        price = 100.0,
        capacity = 10,
        occupiedSpots = 1,
        isCancelled = isCancelled,
        ownerEmails = ownerEmails,
    )

    private fun makeSeriesReservation(seriesId: Uuid, id: Uuid = Uuid.random()) = Reservation(
        id = id,
        reference = Reference.Series(seriesId),
        registeredUserId = testCallerId,
        contactName = "Jan Novak",
        contactEmail = "jan@test.com",
        seatCount = 1,
        totalPrice = 500.0,
        status = Reservation.Status.CONFIRMED,
        createdAt = Clock.System.now(),
        customValues = emptyMap(),
        paymentType = PaymentInfo.Type.BANK_TRANSFER,
    )

    /** Rezervace bez účtu — chrání ji jen znalost UUID, volající je anonymní. */
    private fun makeAnonymousSeriesReservation(
        seriesId: Uuid,
        id: Uuid = Uuid.random(),
        paidAmount: Double = 0.0,
        seatCount: Int = 1,
        status: Reservation.Status = Reservation.Status.CONFIRMED,
    ) = makeSeriesReservation(seriesId, id).copy(
        registeredUserId = null,
        contactEmail = "host@test.com",
        paidAmount = paidAmount,
        seatCount = seatCount,
        status = status,
    )

    private fun makeInstanceReservation(instanceId: Uuid, id: Uuid = Uuid.random()) = Reservation(
        id = id,
        reference = Reference.Instance(instanceId),
        registeredUserId = testCallerId,
        contactName = "Jana Novakova",
        contactEmail = "jana@test.com",
        seatCount = 1,
        totalPrice = 100.0,
        status = Reservation.Status.CONFIRMED,
        createdAt = Clock.System.now(),
        customValues = emptyMap(),
        paymentType = PaymentInfo.Type.BANK_TRANSFER,
    )

    @Test
    fun `opt out nesaha na ulozeny citac lekce`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()

        val series = makeSeries()
        seriesRepo.create(series)

        val instance = makeInstance(series.id, startDateTime = LocalDateTime(2099, 12, 1, 10, 0))
        instanceRepo.create(instance)

        val reservation = makeSeriesReservation(series.id)
        reservationRepo.save(reservation)

        val service = makeService(
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
        )

        val result = service.cancelReservation(reservation.id, instance.id)
        assertTrue(result.isRight(), "Expected Right but got: $result")

        val savedOptOut = optOutRepo.findByReservationAndInstance(reservation.id, instance.id)
        assertNotNull(savedOptOut, "omluvenka se musí uložit — ta je nově tím odečtem")
        assertEquals(reservation.id, savedOptOut.reservationId)
        assertEquals(instance.id, savedOptOut.instanceId)

        assertEquals(
            instance.occupiedSpots,
            instanceRepo.get(instance.id)?.occupiedSpots,
            "uložený čítač lekce se omluvou nemění; obsazenost se dopočítává ze SeriesLessonLoad",
        )
        assertEquals(
            series.occupiedSpots,
            seriesRepo.get(series.id)?.occupiedSpots,
            "přihláška na kurz trvá dál, jen na jednu lekci nedorazí",
        )
    }

    @Test
    fun `opt out posune cekatele z poradniku lekce`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()

        val series = makeSeries()
        seriesRepo.create(series)

        val instance = makeInstance(series.id, startDateTime = LocalDateTime(2099, 12, 1, 10, 0))
        instanceRepo.create(instance)

        val reservation = makeSeriesReservation(series.id)
        reservationRepo.save(reservation)

        // Čekatel v pořadníku té konkrétní lekce.
        val cekatel = reservationRepo.save(
            makeInstanceReservation(instance.id).copy(status = Reservation.Status.WAITLISTED)
        )

        val service = makeService(
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
        )

        val result = service.cancelReservation(reservation.id, instance.id)
        assertTrue(result.isRight(), "Expected Right but got: $result")

        assertEquals(
            Reservation.Status.PENDING_PAYMENT,
            reservationRepo.findById(cekatel.id)?.status,
            "uvolněné místo má dostat první čekatel v pořadníku lekce",
        )
    }

    @Test
    fun `opt out fails when reservation is not a series reservation`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()

        val instanceId = Uuid.random()
        val instance = makeInstance(Uuid.random(), id = instanceId)
        instanceRepo.create(instance)

        val reservation = makeInstanceReservation(instanceId)
        reservationRepo.save(reservation)

        val service = makeService(
            instanceRepo = instanceRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
        )

        val result = service.cancelReservation(reservation.id, instanceId)
        assertTrue(result.isLeft(), "Expected Left but got: $result")
        result.onLeft { error ->
            assertEquals(ReservationError.NotASeriesReservation, error)
        }
        Unit
    }

    @Test
    fun `opt out fails when instance does not belong to the series`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()

        val series = makeSeries()
        seriesRepo.create(series)

        val otherSeriesId = Uuid.random()
        val instanceFromOtherSeries = makeInstance(otherSeriesId)
        instanceRepo.create(instanceFromOtherSeries)

        val reservation = makeSeriesReservation(series.id)
        reservationRepo.save(reservation)

        val service = makeService(
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
        )

        val result = service.cancelReservation(reservation.id, instanceFromOtherSeries.id)
        assertTrue(result.isLeft(), "Expected Left but got: $result")
        result.onLeft { error ->
            assertEquals(ReservationError.InstanceNotInSeries, error)
        }
        Unit
    }

    @Test
    fun `opt out fails when already opted out`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()

        val series = makeSeries()
        seriesRepo.create(series)

        val instance = makeInstance(series.id, startDateTime = LocalDateTime(2099, 12, 1, 10, 0))
        instanceRepo.create(instance)

        val reservation = makeSeriesReservation(series.id)
        reservationRepo.save(reservation)

        // Pre-save an opt-out record to simulate already opted out
        optOutRepo.save(
            SeriesLessonOptOut(
                id = Uuid.random(),
                reservationId = reservation.id,
                instanceId = instance.id,
                optedOutAt = Clock.System.now(),
                isLateCancellation = false,
            )
        )

        val service = makeService(
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
        )

        val result = service.cancelReservation(reservation.id, instance.id)
        assertTrue(result.isLeft(), "Expected Left but got: $result")
        result.onLeft { error ->
            assertEquals(ReservationError.AlreadyOptedOut, error)
        }
        Unit
    }

    // --- Omluvenky hostů (rezervace bez účtu) ---

    @Test
    fun `host bez uctu se muze odhlasit z lekce`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()

        val series = makeSeries()
        seriesRepo.create(series)
        val instance = makeInstance(series.id)
        instanceRepo.create(instance)
        val reservation = makeAnonymousSeriesReservation(series.id)
        reservationRepo.save(reservation)

        // callerId = null: anonymní volající, jen se znalostí UUID rezervace
        val service = makeService(
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
            callerId = null,
        )

        val result = service.cancelReservation(reservation.id, instance.id)
        assertTrue(result.isRight(), "host se musí umět odhlásit, dostal: $result")
        assertNotNull(
            optOutRepo.findByReservationAndInstance(reservation.id, instance.id),
            "omluvenka se musí uložit",
        )
        Unit
    }

    @Test
    fun `anonymni volajici se nedostane k registrovane rezervaci`() = runBlocking {
        // Nejdůležitější pojistka celé změny: uvolnění brány pro hosty nesmí
        // registrovaným uživatelům zhoršit ochranu, kterou dnes mají.
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()

        val series = makeSeries()
        seriesRepo.create(series)
        val instance = makeInstance(series.id)
        instanceRepo.create(instance)
        val reservation = makeSeriesReservation(series.id)  // registeredUserId = testCallerId
        reservationRepo.save(reservation)

        val service = makeService(
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
            callerId = null,
        )

        val result = service.cancelReservation(reservation.id, instance.id)
        assertTrue(result.isLeft(), "anonym nesmí sáhnout na registrovanou rezervaci, dostal: $result")
        result.onLeft { assertEquals(ReservationError.ReservationNotFound, it) }
        assertEquals(
            emptyList(), optOutRepo.findByReservation(reservation.id),
            "nesmí vzniknout žádná omluvenka",
        )
        Unit
    }

    @Test
    fun `prihlaseny uzivatel se nemuze odhlasit z cizi rezervace`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()

        val series = makeSeries()
        seriesRepo.create(series)
        val instance = makeInstance(series.id)
        instanceRepo.create(instance)
        val reservation = makeSeriesReservation(series.id)
        reservationRepo.save(reservation)

        val service = makeService(
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
            callerId = Uuid.parse("00000000-0000-0000-0000-0000000000ff"),
        )

        val result = service.cancelReservation(reservation.id, instance.id)
        assertTrue(result.isLeft(), "cizí přihlášený nesmí projít, dostal: $result")
        result.onLeft { assertEquals(ReservationError.ReservationNotFound, it) }
        Unit
    }

    @Test
    fun `zrusena rezervace se nemuze omlouvat z lekci`() = runBlocking {
        // Storno už vrátilo celou zaplacenou částku; bez téhle kontroly by se z
        // lekcí dal inkasovat kredit ještě jednou.
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()

        val series = makeSeries()
        seriesRepo.create(series)
        val instance = makeInstance(series.id)
        instanceRepo.create(instance)
        val reservation = makeAnonymousSeriesReservation(
            series.id, paidAmount = 500.0, status = Reservation.Status.CANCELLED,
        )
        reservationRepo.save(reservation)

        val service = makeService(
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
            callerId = null,
        )

        val result = service.cancelReservation(reservation.id, instance.id)
        assertTrue(result.isLeft(), "zrušená rezervace nesmí projít, dostal: $result")
        result.onLeft { assertEquals(ReservationError.ReservationNotFound, it) }
        Unit
    }

    @Test
    fun `kredit za omluvenky nepresahne zaplacenou castku`() = runBlocking {
        // lessonRefundAmount je volná admin hodnota nezávislá na ceně kurzu —
        // 3 lekce po 200 Kč na rezervaci za 500 Kč se musí zastavit na 500.
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()
        val walletRepo = InMemoryWalletRepository()

        val series = makeSeries().copy(lessonRefundAmount = 200.0)
        seriesRepo.create(series)
        val lessons = (1..3).map { i ->
            makeInstance(series.id, startDateTime = LocalDateTime(2099, 12, i, 10, 0)).also { instanceRepo.create(it) }
        }
        val reservation = makeAnonymousSeriesReservation(series.id, paidAmount = 500.0)
        reservationRepo.save(reservation)

        val service = makeService(
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
            walletSvc = WalletService(walletRepo),
            callerId = null,
        )

        val credited = lessons.map { lesson ->
            val result = service.cancelReservation(reservation.id, lesson.id)
            assertTrue(result.isRight(), "omluvenka musí projít, dostala: $result")
            result.getOrNull()?.walletCreditAmount ?: 0.0
        }

        assertEquals(listOf(200.0, 200.0, 100.0), credited, "třetí omluvenka doplní jen zbytek do 500")
        assertEquals(
            500.0, walletRepo.findAnonymousByEmail(reservation.contactEmail)?.balance,
            "součet kreditů se musí zastavit na zaplacené částce",
        )
        Unit
    }

    @Test
    fun `kredit se nasobi poctem mist`() = runBlocking {
        // Omluvenka uvolní všechna místa rezervace, takže musí vrátit i kredit za všechna.
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()

        val series = makeSeries().copy(lessonRefundAmount = 100.0)
        seriesRepo.create(series)
        val instance = makeInstance(series.id)
        instanceRepo.create(instance)
        val reservation = makeAnonymousSeriesReservation(series.id, paidAmount = 1500.0, seatCount = 3)
        reservationRepo.save(reservation)

        val service = makeService(
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
            callerId = null,
        )

        val result = service.cancelReservation(reservation.id, instance.id)
        assertTrue(result.isRight(), "omluvenka musí projít, dostala: $result")
        assertEquals(300.0, result.getOrNull()?.walletCreditAmount, "3 místa × 100 Kč")
        Unit
    }

    @Test
    fun `opakovane omluvenky hosta jdou do jedne penezenky`() = runBlocking {
        // resolveAnonymousWallet bez kódu zakládá pokaždé novou peněženku — kredit
        // by se roztříštil do několika kódů, ke kterým se host prakticky nedostane.
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()
        val walletRepo = InMemoryWalletRepository()

        val series = makeSeries().copy(lessonRefundAmount = 100.0)
        seriesRepo.create(series)
        val first = makeInstance(series.id, startDateTime = LocalDateTime(2099, 12, 1, 10, 0))
        val second = makeInstance(series.id, startDateTime = LocalDateTime(2099, 12, 8, 10, 0))
        instanceRepo.create(first)
        instanceRepo.create(second)
        val reservation = makeAnonymousSeriesReservation(series.id, paidAmount = 1000.0)
        reservationRepo.save(reservation)

        val service = makeService(
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
            walletSvc = WalletService(walletRepo),
            callerId = null,
        )

        val firstCode = service.cancelReservation(reservation.id, first.id).getOrNull()?.walletCode
        val secondCode = service.cancelReservation(reservation.id, second.id).getOrNull()?.walletCode

        assertNotNull(firstCode, "první omluvenka musí vrátit kód peněženky")
        assertEquals(firstCode, secondCode, "druhá omluvenka musí jít do téže peněženky")
        assertEquals(
            200.0, walletRepo.findAnonymousByEmail(reservation.contactEmail)?.balance,
            "kredit se má sečíst v jedné peněžence",
        )
        Unit
    }

    @Test
    fun `omluvenka hosta s cizim kodem penezenky nic nezapise`() = runBlocking {
        // Peněženku řešíme před zápisem omluvenky — neshoda e-mailu je chyba
        // k opakování, po ní musí jít zkusit to znovu se správným kódem.
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()
        val walletRepo = InMemoryWalletRepository()

        val series = makeSeries().copy(lessonRefundAmount = 100.0)
        seriesRepo.create(series)
        val instance = makeInstance(series.id)
        instanceRepo.create(instance)
        val reservation = makeAnonymousSeriesReservation(series.id, paidAmount = 1000.0)
        reservationRepo.save(reservation)

        val walletService = WalletService(walletRepo)
        val cizi = walletService.resolveAnonymousWallet(null, "nekdo.jiny@test.com", force = false).getOrNull()!!

        val service = makeService(
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
            walletSvc = walletService,
            callerId = null,
        )

        val odmitnuto = service.cancelReservation(reservation.id, instance.id, walletCode = cizi.code, force = false)
        assertTrue(odmitnuto.isLeft(), "cizí peněženka bez force musí selhat, dostal: $odmitnuto")
        odmitnuto.onLeft { assertEquals(ReservationError.WalletEmailMismatch, it) }
        assertEquals(
            emptyList(), optOutRepo.findByReservation(reservation.id),
            "po neúspěchu nesmí zůstat omluvenka — jinak by druhý pokus spadl na AlreadyOptedOut",
        )

        val potvrzeno = service.cancelReservation(reservation.id, instance.id, walletCode = cizi.code, force = true)
        assertTrue(potvrzeno.isRight(), "s force musí projít, dostal: $potvrzeno")
        assertEquals(cizi.code, potvrzeno.getOrNull()?.walletCode)
        Unit
    }

    @Test
    fun `registrovana rezervace dal chodi pres uzivatelskou penezenku`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()
        val walletRepo = InMemoryWalletRepository()

        val series = makeSeries().copy(lessonRefundAmount = 100.0)
        seriesRepo.create(series)
        val instance = makeInstance(series.id)
        instanceRepo.create(instance)
        val reservation = makeSeriesReservation(series.id).copy(paidAmount = 500.0)
        reservationRepo.save(reservation)

        val service = makeService(
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
            walletSvc = WalletService(walletRepo),
        )

        val result = service.cancelReservation(reservation.id, instance.id)
        assertTrue(result.isRight(), "majiteli musí omluvenka projít, dostal: $result")
        assertEquals(100.0, result.getOrNull()?.walletCreditAmount)
        assertNotNull(
            walletRepo.findByRegisteredUserId(testCallerId),
            "kredit má jít do peněženky svázané s účtem, ne do anonymní",
        )
        Unit
    }

    @Test
    fun `omluvenka pred zaplacenim neukrajuje ze stropu`() = runBlocking {
        // Odhlášení proběhlo, dokud nebylo zaplaceno, takže žádný kredit nedostalo.
        // Strop se počítá ze skutečně vyplacených částek, ne z počtu omluvenek —
        // jinak by o ten kredit člověk po doplacení přišel.
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()
        val walletRepo = InMemoryWalletRepository()

        val series = makeSeries().copy(lessonRefundAmount = 150.0)
        seriesRepo.create(series)
        val first = makeInstance(series.id, startDateTime = LocalDateTime(2099, 12, 1, 10, 0))
        val second = makeInstance(series.id, startDateTime = LocalDateTime(2099, 12, 8, 10, 0))
        instanceRepo.create(first)
        instanceRepo.create(second)

        val reservation = makeAnonymousSeriesReservation(series.id, paidAmount = 0.0)
        reservationRepo.save(reservation)

        val service = makeService(
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
            walletSvc = WalletService(walletRepo),
            callerId = null,
        )

        val nezaplacena = service.cancelReservation(reservation.id, first.id)
        assertTrue(nezaplacena.isRight(), "omluvenka musí projít i bez platby, dostala: $nezaplacena")
        assertEquals(null, nezaplacena.getOrNull()?.walletCreditAmount, "nezaplaceno = žádný kredit")

        // Doplatí kurz a odhlásí se z další lekce.
        reservationRepo.save(reservation.copy(paidAmount = 300.0))

        val poZaplaceni = service.cancelReservation(reservation.id, second.id)
        assertEquals(
            150.0, poZaplaceni.getOrNull()?.walletCreditAmount,
            "plný kredit — dřívější bezplatná omluvenka nesmí strop snižovat",
        )
        Unit
    }
}
