package cz.svitaninymburk.projects.reservations.plugins

import cz.svitaninymburk.projects.reservations.api.ApiError
import cz.svitaninymburk.projects.reservations.api.EventsResponse
import cz.svitaninymburk.projects.reservations.api.MobilePaymentInfo
import cz.svitaninymburk.projects.reservations.api.RefreshResponse
import cz.svitaninymburk.projects.reservations.api.code
import cz.svitaninymburk.projects.reservations.api.httpStatus
import cz.svitaninymburk.projects.reservations.api.localized
import cz.svitaninymburk.projects.reservations.api.jwtUserId
import cz.svitaninymburk.projects.reservations.api.requireAdmin
import cz.svitaninymburk.projects.reservations.api.respondEither
import cz.svitaninymburk.projects.reservations.attendance.SetAttendanceRequest
import cz.svitaninymburk.projects.reservations.auth.LoginRequest
import cz.svitaninymburk.projects.reservations.auth.RefreshTokenRequest
import cz.svitaninymburk.projects.reservations.qr.CzechIbanGenerator
import cz.svitaninymburk.projects.reservations.qr.QrCodeService
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.CreateSeriesReservationRequest
import cz.svitaninymburk.projects.reservations.service.AttendanceService
import cz.svitaninymburk.projects.reservations.service.AuthServiceInterface
import cz.svitaninymburk.projects.reservations.service.AuthenticatedReservationServiceInterface
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import cz.svitaninymburk.projects.reservations.service.RefreshTokenServiceInterface
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlin.uuid.Uuid
import org.koin.ktor.ext.inject

fun Route.mobilePublicAuthRoutes() {
    val authService by inject<AuthServiceInterface>()
    val refreshService by inject<RefreshTokenServiceInterface>()

    route("/api/v1/auth") {
        post("login") {
            val request = call.receive<LoginRequest>()
            call.respondEither(authService.login(request))
        }

        post("refresh") {
            val request = call.receive<RefreshTokenRequest>()
            refreshService.refreshToken(request.refreshToken).fold(
                ifLeft = { err -> call.respond(err.httpStatus(), ApiError(err.code(), err.localized())) },
                ifRight = { token -> call.respond(HttpStatusCode.OK, RefreshResponse(token)) },
            )
        }
    }
}

fun Route.mobileSecuredRoutes() {
    val authService by inject<AuthServiceInterface>()
    val eventService by inject<EventServiceInterface>()
    val reservationService by inject<ReservationServiceInterface>()
    val authReservationService by inject<AuthenticatedReservationServiceInterface>()
    val qrCodeService by inject<QrCodeService>()
    val attendanceService by inject<AttendanceService>()

    route("/api/v1") {
        get("me") {
            call.respondEither(authService.getCurrentUser())
        }

        get("wallet") {
            val email = call.principal<JWTPrincipal>()!!.payload.getClaim("email").asString()
            authService.getMyWalletCode().fold(
                ifLeft = { call.respondEither(authService.getMyWalletCode()) },
                ifRight = { code -> call.respondEither(reservationService.getWalletInfo(code, email)) },
            )
        }

        route("events") {
            get {
                val instancesResult = eventService.getAllInstances()
                val seriesResult = eventService.getAllSeries()
                instancesResult.fold(
                    ifLeft = { call.respondEither(instancesResult) },
                    ifRight = { instance ->
                        seriesResult.fold(
                            ifLeft = { call.respondEither(seriesResult) },
                            ifRight = { ser -> call.respond(HttpStatusCode.OK, EventsResponse(instance, ser)) },
                        )
                    },
                )
            }

            get("instances/{id}") {
                val id = Uuid.parse(call.parameters["id"]!!)
                call.respondEither(eventService.getInstance(id))
            }

            get("series/{id}") {
                val id = Uuid.parse(call.parameters["id"]!!)
                call.respondEither(eventService.getSeriesDetail(id))
            }
        }

        route("reservations") {
            post("instance") {
                val req = call.receive<CreateInstanceReservationRequest>()
                call.respondEither(reservationService.reserveInstance(req, call.jwtUserId()))
            }

            post("series") {
                val req = call.receive<CreateSeriesReservationRequest>()
                call.respondEither(reservationService.reserveSeries(req, call.jwtUserId()))
            }

            get("mine") {
                call.respondEither(authReservationService.getReservations(call.jwtUserId()!!))
            }

            post("{id}/cancel") {
                val id = Uuid.parse(call.parameters["id"]!!)
                call.respondEither(reservationService.cancelReservation(reservationId = id))
            }

            get("{id}/payment") {
                val id = Uuid.parse(call.parameters["id"]!!)
                reservationService.getDetail(id).fold(
                    ifLeft = { error -> call.respond(error.httpStatus(), ApiError(error.code(), error.localized())) },
                    ifRight = { detail ->
                        val accountNumber = System.getenv("BANK_ACCOUNT_NUMBER") ?: ""
                        val amount = detail.reservation.unpaidAmount
                        val vs = detail.reservation.variableSymbol
                        val spayd = qrCodeService.generateSpaydString(accountNumber, amount, vs, null)
                        val iban = CzechIbanGenerator.toIban(accountNumber)
                        call.respond(HttpStatusCode.OK, MobilePaymentInfo(spayd, amount, vs, iban, accountNumber))
                    },
                )
            }
        }

        route("admin") {
            get("events/{id}/attendance") {
                if (!call.requireAdmin(authService)) return@get
                val id = Uuid.parse(call.parameters["id"]!!)
                call.respondEither(attendanceService.getAttendance(id))
            }

            post("attendance/{reservationId}") {
                if (!call.requireAdmin(authService)) return@post
                val req = call.receive<SetAttendanceRequest>()
                call.respondEither(attendanceService.setAttendance(req.reservationId, req.checkedIn))
            }
        }
    }
}
