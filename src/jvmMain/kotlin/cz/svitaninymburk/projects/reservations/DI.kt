package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.auth.BCryptHashingService
import cz.svitaninymburk.projects.reservations.auth.GoogleAuthService
import cz.svitaninymburk.projects.reservations.auth.HashingService
import cz.svitaninymburk.projects.reservations.auth.JwtTokenService
import cz.svitaninymburk.projects.reservations.qr.QrCodeService
import cz.svitaninymburk.projects.reservations.repository.auth.ExposedRefreshTokenRepository
import cz.svitaninymburk.projects.reservations.repository.auth.InMemoryRefreshTokenRepository
import cz.svitaninymburk.projects.reservations.repository.auth.RefreshTokenRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.event.ExposedEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.ExposedEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.ExposedEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ExposedReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.repository.user.ExposedUserRepository
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.repository.user.UserRepository
import cz.svitaninymburk.projects.reservations.service.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import org.koin.dsl.bind
import org.koin.dsl.module


val appModule = module {
    single {
        JwtTokenService.Companion.JwtConfig(
            secret = System.getenv("JWT_CONFIG_SECRET") ?: error("JWT_CONFIG_SECRET required"),
            issuer = System.getenv("JWT_CONFIG_ISSUER") ?: error("JWT_CONFIG_ISSUER required"),
            audience = System.getenv("JWT_CONFIG_AUDIENCE") ?: error("JWT_CONFIG_AUDIENCE required"),
            realm = "Access to Reservation System"
        )
    }

    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(AppJson)
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }
    }

    single { PaymentTrigger() }

    single { GoogleAuthService(clientId = System.getenv("GOOGLE_CLIENT_ID") ?: "not-configured") }

    single { JwtTokenService(get()) }
    single<HashingService> { BCryptHashingService() }

    // Auth & Users
    single<RefreshTokenRepository> { ExposedRefreshTokenRepository() }
    single<UserRepository> { ExposedUserRepository() }

    // Events
    single<EventDefinitionRepository> { ExposedEventDefinitionRepository() }
    single<EventSeriesRepository> { ExposedEventSeriesRepository() }
    single<EventInstanceRepository> { ExposedEventInstanceRepository() }

    // Reservations
    single<ReservationRepository> { ExposedReservationRepository() }

    single { AuthService(get(), get(), get(), get(), get(), get()) } bind AuthServiceInterface::class
    single { AuthRefreshTokenService(get(), get(), get()) } bind RefreshTokenServiceInterface::class
    single { RefreshTokenService(get(), get()) }
    single { EventService(get(), get(), get()) } bind EventServiceInterface::class
    single { AuthenticatedEventService(get(), get()) } bind AuthenticatedEventServiceInterface::class
    single {
        GmailEmailService(
            username = System.getenv("GMAIL_USERNAME") ?: error("GMAIL_USERNAME env var is required"),
            appPassword = System.getenv("GMAIL_APP_PASSWORD") ?: error("GMAIL_APP_PASSWORD env var is required"),
            appBaseUrl = System.getenv("APP_BASE_URL") ?: error("APP_BASE_URL env var is required"),
            eventRepository = get(),
        )
    } bind EmailService::class
    single { QrCodeService(accountNumber = System.getenv("BANK_ACCOUNT_NUMBER") ?: "2003487968/2010") }
    single { BackendQrCodeGenerator(get()) }
    single { ReservationService(get(), get(), get(), get(), get(), get()) } bind ReservationServiceInterface::class
    single { AuthenticatedReservationService(get(), get(), get()) } bind AuthenticatedReservationServiceInterface::class
    single { PaymentPairingService(get(), get(), get(), get(), System.getenv("FIO_TOKEN") ?: "0eZMDyWlNRyiUI4Wd0HBHQysLs0IwgEtgGdsNWBME6CsJLwwy6QgZtSc5HzIyIuJ") }
    single { AdminService(get()) }
    single { AdminDashboardService(get(), get(), get(), get(), get(), get()) } bind AdminServiceInterface::class
    single { UserService(get()) }
}
