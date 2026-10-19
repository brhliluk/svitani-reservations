package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonOptOut
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.event.EventSeries
import arrow.core.Either
import arrow.core.right
import cz.svitaninymburk.projects.reservations.bank.BankTransaction
import cz.svitaninymburk.projects.reservations.error.EmailError
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.payment.InMemoryPaymentEventRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentEvent
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json

class PaymentPairingServiceTest {

    private val json = Json { ignoreUnknownKeys = true }

    class MockEmailService : EmailService {
        val sentConfirmations = mutableListOf<Reservation>()
        val sentPartialPayments = mutableListOf<Triple<Reservation, BankTransaction, ByteArray>>()

        override suspend fun sendReservationConfirmation(
            toEmail: String,
            reservation: Reservation,
            target: ReservationTarget,
            bankAccount: String,
            qrCodeImage: ByteArray?,
            icalBytes: ByteArray
        ): Either<EmailError.SendReservationConfirmation, Unit> = Unit.right()

        override suspend fun sendCancellationNotice(toEmail: String, eventTitle: String, reservationId: Uuid, locale: String): Either<EmailError.SendCancellation, Unit> = Unit.right()

        override suspend fun sendPaymentReceivedConfirmation(reservation: Reservation): Either<EmailError.SendPaymentConfirmation, Unit> {
            sentConfirmations.add(reservation)
            return Unit.right()
        }

        override suspend fun sendPaymentNotPaidInFull(
            reservation: Reservation,
            paymentInfo: BankTransaction,
            bankAccount: String,
            qrCodeImage: ByteArray
        ): Either<EmailError.SendPaymentNotPaidInFull, Unit> {
            sentPartialPayments.add(Triple(reservation, paymentInfo, qrCodeImage))
            return Unit.right()
        }

        override suspend fun sendPasswordResetEmail(toEmail: String, resetToken: String): Either<EmailError.SendPasswordReset, Unit> = Unit.right()

        override suspend fun sendReservationClaimEmail(
            toEmail: String,
            reservations: List<MyReservationListItem>,
            claimToken: String,
            locale: String,
        ): Either<EmailError.SendReservationClaim, Unit> = Unit.right()

        override suspend fun sendLessonRescheduledNotification(
            toEmail: String,
            contactName: String,
            seriesTitle: String,
            oldDateTime: LocalDateTime,
            newDateTime: LocalDateTime,
            locale: String
        ): Either<EmailError.SendLessonRescheduled, Unit> = Unit.right()

        override suspend fun sendLessonCancelledNotification(
            toEmail: String,
            contactName: String,
            seriesTitle: String,
            lessonDateTime: LocalDateTime,
            locale: String
        ): Either<EmailError.SendLessonCancelled, Unit> = Unit.right()

        override suspend fun sendRemainingLessonsCancelledNotification(
            toEmail: String,
            contactName: String,
            seriesTitle: String,
            lessonDateTimes: List<LocalDateTime>,
            locale: String
        ): Either<EmailError.SendLessonCancelled, Unit> = Unit.right()

        override suspend fun sendLessonOptOutNotice(
            toEmail: String,
            eventTitle: String,
            lessonDate: LocalDate,
            isLateCancellation: Boolean,
            locale: String
        ): Either<EmailError.SendCancellation, Unit> = Unit.right()

        override suspend fun sendWaitlistConfirmation(
            toEmail: String,
            eventTitle: String,
            contactName: String,
            reservationId: Uuid,
            locale: String,
        ): Either<EmailError.SendWaitlistConfirmation, Unit> = Unit.right()

        override suspend fun sendWaitlistPromotion(
            toEmail: String,
            reservation: Reservation,
            target: ReservationTarget,
            bankAccount: String,
            qrCodeImage: ByteArray?,
            icalBytes: ByteArray,
        ): Either<EmailError.SendWaitlistPromotion, Unit> = Unit.right()
    }

    class StubQrCodeGenerator : QrCodeGeneratorService {
        override val accountNumber = "2800981651/2010"
        override fun generateQrPng(reservation: Reservation, target: ReservationTarget?): ByteArray =
            "mock-qr-${reservation.unpaidAmount.toInt()}".encodeToByteArray()
    }

    private fun makeReservation(vs: String, totalPrice: Double, paidAmount: Double = 0.0) = Reservation(
        id = Uuid.random(),
        reference = Reference.Instance(Uuid.random()),
        contactName = "Lukas Novak",
        contactEmail = "lukas@test.com",
        seatCount = 1,
        totalPrice = totalPrice,
        paidAmount = paidAmount,
        status = Reservation.Status.PENDING_PAYMENT,
        createdAt = Clock.System.now(),
        customValues = emptyMap(),
        paymentType = PaymentType.BANK_TRANSFER,
        variableSymbol = vs
    )

    private fun makeFioResponseJson(amount: Double, vs: String) = """
    {
      "accountStatement": {
        "transactionList": {
          "transaction": [
            {
              "column1": { "value": "$amount" },
              "column14": { "value": "CZK" },
              "column5": { "value": "$vs" },
              "column22": { "value": "fio_tx_id_999" },
              "column0": { "value": "2026-06-11" }
            }
          ]
        }
      }
    }
    """.trimIndent()

    private fun setupService(
        jsonResponse: String,
        reservationRepo: InMemoryReservationRepository,
        emailService: MockEmailService,
        paymentEventRepo: InMemoryPaymentEventRepository,
        eventSeriesRepo: InMemoryEventSeriesRepository = InMemoryEventSeriesRepository(),
        lessonOptOutRefunds: LessonOptOutRefunds? = null,
    ): PaymentPairingService {
        val mockEngine = MockEngine { _ ->
            respond(
                content = jsonResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val settingsProvider = AppSettingsProvider.forTest(
            AppSettings(
                bankAccountNumber = "2800981651/2010",
                fioToken = "mock-fio-token",
                senderEmail = "svitani@test.com",
                gmailAppPassword = "app-password",
                senderDisplayName = "Rodinné centrum Svítání"
            )
        )
        return PaymentPairingService(
            httpClient = httpClient,
            reservationRepo = reservationRepo,
            emailService = emailService,
            qrCodeService = StubQrCodeGenerator(),
            settings = settingsProvider,
            paymentEventRepository = paymentEventRepo,
            eventInstanceRepository = InMemoryEventInstanceRepository(),
            eventSeriesRepository = eventSeriesRepo,
            lessonOptOutRefunds = lessonOptOutRefunds,
        )
    }

    @Test
    fun `bank payment settles credit for a lesson opt-out made before payment`() = runBlocking {
        val reservationRepo = InMemoryReservationRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()
        val walletRepo = InMemoryWalletRepository()
        val walletService = WalletService(walletRepo)
        val settings = AppSettingsProvider.forTest(AppSettings(
            bankAccountNumber = "", fioToken = "", senderEmail = "",
            gmailAppPassword = "", senderDisplayName = "",
        ))

        val series = EventSeries(
            id = Uuid.random(), definitionId = Uuid.random(), title = "Kurz", description = "",
            price = 1000.0, capacity = 10, startDate = LocalDate(2099, 12, 1), endDate = LocalDate(2099, 12, 31),
            lessonCount = 4, lessonRefundAmount = 150.0,
        )
        seriesRepo.create(series)
        val reservation = makeReservation("7777", 1000.0).copy(reference = Reference.Series(series.id))
        reservationRepo.save(reservation)
        optOutRepo.save(
            SeriesLessonOptOut(
                id = Uuid.random(), reservationId = reservation.id, instanceId = Uuid.random(),
                optedOutAt = Clock.System.now(), isLateCancellation = false, refundedAmount = 0.0,
            )
        )

        val service = setupService(
            jsonResponse = makeFioResponseJson(1000.0, "7777"),
            reservationRepo = reservationRepo,
            emailService = MockEmailService(),
            paymentEventRepo = InMemoryPaymentEventRepository(),
            eventSeriesRepo = seriesRepo,
            lessonOptOutRefunds = LessonOptOutRefunds(
                optOutRepo, seriesRepo, walletService,
                RefundService(walletService, ConsoleEmailService(), settings),
            ),
        )

        assertTrue(service.checkAndPairPayments().isRight())

        assertEquals(150.0, walletRepo.findAnonymousByEmail("lukas@test.com")?.balance)
        assertEquals(150.0, optOutRepo.findByReservation(reservation.id).single().refundedAmount)
    }

    @Test
    fun `exact payment confirms reservation`() = runBlocking {
        val reservationRepo = InMemoryReservationRepository()
        val emailService = MockEmailService()
        val paymentEventRepo = InMemoryPaymentEventRepository()

        val reservation = makeReservation("1111", 300.0)
        reservationRepo.save(reservation)

        val service = setupService(
            jsonResponse = makeFioResponseJson(300.0, "1111"),
            reservationRepo = reservationRepo,
            emailService = emailService,
            paymentEventRepo = paymentEventRepo
        )

        val result = service.checkAndPairPayments()
        assertTrue(result.isRight())

        val dbRes = reservationRepo.findById(reservation.id)
        assertEquals(Reservation.Status.CONFIRMED, dbRes?.status)
        assertEquals(300.0, dbRes?.paidAmount)

        assertEquals(1, emailService.sentConfirmations.size)
        assertEquals(0, emailService.sentPartialPayments.size)

        val events = paymentEventRepo.insertedEvents()
        assertEquals(1, events.size)
        assertEquals(300.0, events[0].amount)
        assertEquals(PaymentEvent.Source.AUTO_FIO, events[0].source)
    }

    @Test
    fun `overpayment confirms reservation and updates paidAmount`() = runBlocking {
        val reservationRepo = InMemoryReservationRepository()
        val emailService = MockEmailService()
        val paymentEventRepo = InMemoryPaymentEventRepository()

        // 1 CZK reservation, paid with 300 CZK
        val reservation = makeReservation("2222", 1.0)
        reservationRepo.save(reservation)

        val service = setupService(
            jsonResponse = makeFioResponseJson(300.0, "2222"),
            reservationRepo = reservationRepo,
            emailService = emailService,
            paymentEventRepo = paymentEventRepo
        )

        val result = service.checkAndPairPayments()
        assertTrue(result.isRight())

        val dbRes = reservationRepo.findById(reservation.id)
        assertEquals(Reservation.Status.CONFIRMED, dbRes?.status)
        assertEquals(300.0, dbRes?.paidAmount)

        assertEquals(1, emailService.sentConfirmations.size)
        assertEquals(0, emailService.sentPartialPayments.size)

        val events = paymentEventRepo.insertedEvents()
        assertEquals(1, events.size)
        assertEquals(300.0, events[0].amount)
    }

    @Test
    fun `partial payment updates reservation but does not confirm`() = runBlocking {
        val reservationRepo = InMemoryReservationRepository()
        val emailService = MockEmailService()
        val paymentEventRepo = InMemoryPaymentEventRepository()

        // 300 CZK reservation, paid with 100 CZK
        val reservation = makeReservation("3333", 300.0)
        reservationRepo.save(reservation)

        val service = setupService(
            jsonResponse = makeFioResponseJson(100.0, "3333"),
            reservationRepo = reservationRepo,
            emailService = emailService,
            paymentEventRepo = paymentEventRepo
        )

        val result = service.checkAndPairPayments()
        assertTrue(result.isRight())

        val dbRes = reservationRepo.findById(reservation.id)
        assertEquals(Reservation.Status.PENDING_PAYMENT, dbRes?.status)
        assertEquals(100.0, dbRes?.paidAmount)

        assertEquals(0, emailService.sentConfirmations.size)
        assertEquals(1, emailService.sentPartialPayments.size)

        val (emailRes, tx, qr) = emailService.sentPartialPayments[0]
        assertEquals(100.0, emailRes.paidAmount)
        assertEquals(200.0, emailRes.unpaidAmount)
        assertEquals(100.0, tx.amount)
        assertEquals("mock-qr-200", qr.decodeToString())

        val events = paymentEventRepo.insertedEvents()
        assertEquals(1, events.size)
        assertEquals(100.0, events[0].amount)
    }

    @Test
    fun `multiple partial payments accumulate and confirm on reaching total`() = runBlocking {
        val reservationRepo = InMemoryReservationRepository()
        val emailService = MockEmailService()
        val paymentEventRepo = InMemoryPaymentEventRepository()

        // 300 CZK reservation
        val reservation = makeReservation("4444", 300.0)
        reservationRepo.save(reservation)

        // 1. First partial payment: 100 CZK
        val service1 = setupService(
            jsonResponse = makeFioResponseJson(100.0, "4444"),
            reservationRepo = reservationRepo,
            emailService = emailService,
            paymentEventRepo = paymentEventRepo
        )
        val result1 = service1.checkAndPairPayments()
        assertTrue(result1.isRight())

        val dbRes1 = reservationRepo.findById(reservation.id)!!
        assertEquals(Reservation.Status.PENDING_PAYMENT, dbRes1.status)
        assertEquals(100.0, dbRes1.paidAmount)

        // 2. Second partial payment: 150 CZK (total 250 CZK)
        val service2 = setupService(
            jsonResponse = makeFioResponseJson(150.0, "4444"),
            reservationRepo = reservationRepo,
            emailService = emailService,
            paymentEventRepo = paymentEventRepo
        )
        val result2 = service2.checkAndPairPayments()
        assertTrue(result2.isRight())

        val dbRes2 = reservationRepo.findById(reservation.id)!!
        assertEquals(Reservation.Status.PENDING_PAYMENT, dbRes2.status)
        assertEquals(250.0, dbRes2.paidAmount)

        // 3. Final payment: 50 CZK (total 300 CZK)
        val service3 = setupService(
            jsonResponse = makeFioResponseJson(50.0, "4444"),
            reservationRepo = reservationRepo,
            emailService = emailService,
            paymentEventRepo = paymentEventRepo
        )
        val result3 = service3.checkAndPairPayments()
        assertTrue(result3.isRight())

        val dbRes3 = reservationRepo.findById(reservation.id)!!
        assertEquals(Reservation.Status.CONFIRMED, dbRes3.status)
        assertEquals(300.0, dbRes3.paidAmount)

        assertEquals(1, emailService.sentConfirmations.size)
        assertEquals(2, emailService.sentPartialPayments.size)

        val events = paymentEventRepo.insertedEvents()
        assertEquals(3, events.size)
        assertEquals(100.0, events[0].amount)
        assertEquals(150.0, events[1].amount)
        assertEquals(50.0, events[2].amount)
    }
}
