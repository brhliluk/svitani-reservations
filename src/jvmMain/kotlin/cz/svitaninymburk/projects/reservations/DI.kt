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
import org.koin.dsl.bind
import org.koin.dsl.module


val appModule = module {
    single {
        JwtTokenService.Companion.JwtConfig(
            secret = System.getenv("JWT_CONFIG_SECRET") ?: "tajne-heslo-pro-vyvoj-123456", // Musí být dostatečně dlouhé pro HMAC256
            issuer = System.getenv("JWT_CONFIG_ISSUER") ?: "http://0.0.0.0:8080/",
            audience = System.getenv("JWT_CONFIG_AUDIENCE") ?: "moje-rezervace-app",
            realm = "Access to Reservation System"
        )
    }

    single { HttpClient() }

    single { PaymentTrigger() }

    single { GoogleAuthService(clientId = "vas-google-client-id") }

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
//    single { GmailEmailService(System.getenv("GMAIL_USERNAME") ?: "username", System.getenv("GMAIL_APP_PASSWORD") ?: "password", get()) } bind EmailService::class
    single { ConsoleEmailService() } bind EmailService::class
    single { QrCodeService(accountNumber = System.getenv("BANK_ACCOUNT_NUMBER") ?: "19-2000145399/0800") }
    single { BackendQrCodeGenerator(get()) }
    single { ReservationService(get(), get(), get(), get(), get(), get()) } bind ReservationServiceInterface::class
    single { AuthenticatedReservationService(get(), get(), get()) } bind AuthenticatedReservationServiceInterface::class
    single { PaymentPairingService(get(), get(), get(), get(), System.getenv("FIO_TOKEN") ?: "fio-token") }
    single { AdminService(get()) }
    single { UserService(get()) }
}
