package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.auth.BCryptHashingService
import cz.svitaninymburk.projects.reservations.auth.GoogleAuthService
import cz.svitaninymburk.projects.reservations.auth.HashingService
import cz.svitaninymburk.projects.reservations.auth.JwtTokenService
import cz.svitaninymburk.projects.reservations.repository.auth.InMemoryRefreshTokenRepository
import cz.svitaninymburk.projects.reservations.repository.auth.RefreshTokenRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.repository.user.UserRepository
import cz.svitaninymburk.projects.reservations.service.*
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.math.sin


val appModule = module {
    single {
        JwtTokenService.Companion.JwtConfig(
            secret = System.getenv("JWT_CONFIG_SECRET") ?: "tajne-heslo-pro-vyvoj-123456", // Musí být dostatečně dlouhé pro HMAC256
            issuer = System.getenv("JWT_CONFIG_ISSUER") ?: "http://0.0.0.0:8080/",
            audience = System.getenv("JWT_CONFIG_AUDIENCE") ?: "moje-rezervace-app",
            realm = "Access to Reservation System"
        )
    }

    single { PaymentTrigger() }

    single { GoogleAuthService(clientId = "vas-google-client-id") }

    single { JwtTokenService(get()) }
    single<HashingService> { BCryptHashingService() }

    single<UserRepository> { InMemoryUserRepository() }
    single<EventDefinitionRepository> { InMemoryEventDefinitionRepository() }
    single<EventInstanceRepository> { InMemoryEventInstanceRepository() }
    single<ReservationRepository> { InMemoryReservationRepository() }
    single<RefreshTokenRepository> { InMemoryRefreshTokenRepository() }

    single { AuthService(get(), get(), get(), get(), get()) }
    single { AuthRefreshTokenService(get(), get(), get()) }
    single { RefreshTokenService(get(), get()) }
    single { EventService(get(), get()) }
    single { GmailEmailService(System.getenv("GMAIL_USERNAME") ?: "username", System.getenv("GMAIL_APP_PASSWORD") ?: "password", get()) } bind EmailService::class
    single { QrCodeService(accountNumber = System.getenv("BANK_ACCOUNT_NUMBER") ?: "123456-123456789/0100") }
    single { ReservationService(get(), get(), get(), get(), get()) }
    single { AuthenticatedReservationService(get(), get()) }
    single { PaymentPairingService(get(), get(), get(), get(), System.getenv("FIO_TOKEN") ?: "fio-token") }
    single { AdminService(get()) }
    single { UserService(get()) }
}
