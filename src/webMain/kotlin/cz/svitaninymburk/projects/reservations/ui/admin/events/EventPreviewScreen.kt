package cz.svitaninymburk.projects.reservations.ui.admin.events

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.ui.events.Event
import cz.svitaninymburk.projects.reservations.ui.events.SeriesCard
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationModal
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.span
import kotlin.uuid.Uuid

@Composable
fun IComponent.EventPreviewScreen(eventId: String, isSeries: Boolean, currentUser: User) {
    val router = Router.current
    val scope = rememberCoroutineScope()
    val currentStrings by strings

    val uuid = try { Uuid.parse(eventId) } catch (_: IllegalArgumentException) { null }
    if (uuid == null) {
        router.navigate("/admin/events")
        return
    }

    val model = remember(uuid, isSeries) { buildEventPreviewModel(scope, router, uuid, isSeries) }
    LaunchedEffect(uuid, isSeries) { model.load() }

    div(className = "min-h-screen bg-base-200 flex flex-col") {

        PreviewBanner(isPublished = model.isPublished) {
            router.navigate(if (isSeries) "/admin/events/series/$eventId" else "/admin/events/instance/$eventId")
        }

        div(className = "flex-1 w-full max-w-2xl mx-auto px-4 py-8") {
            when (val s = model.state) {
                is EventPreviewState.Loading -> Loading()
                is EventPreviewState.Error -> div(className = "alert alert-error") { span { +s.message } }
                is EventPreviewState.Instance -> Event(
                    event = s.event,
                    onClick = { model.openReservation(ReservationTarget.Instance(s.event)) },
                    onWaitlistClick = if (s.event.waitlistCapacity > 0) {
                        { model.openReservation(ReservationTarget.Instance(s.event), asWaitlist = true) }
                    } else null,
                )

                is EventPreviewState.Series -> SeriesCard(
                    series = s.detail.series,
                    onSignUpClick = { model.openReservation(ReservationTarget.Series(s.detail.series)) },
                )
            }
        }

        ReservationModal(
            target = model.reservationTarget,
            user = currentUser,
            isSubmitting = model.isSubmitting,
            asWaitlist = model.isWaitlistSignup,
            onClose = { model.closeReservation() },
            onSubmit = { target, data -> model.submitReservation(target, data, currentUser.id) },
        )

        Toast(
            message = model.toast?.message,
            type = ToastType.Error,
            onDismiss = { model.dismissToast() },
        )
    }
}

/** Lišta nahoře: připomíná, že tohle je náhled, a vede zpátky do administrace. */
@Composable
private fun IComponent.PreviewBanner(isPublished: Boolean, onBack: () -> Unit) {
    val currentStrings by strings
    val bannerText = if (isPublished) currentStrings.previewBannerPublished else currentStrings.previewBannerUnpublished
    val bannerClass = if (isPublished) "alert alert-success" else "alert alert-warning"

    div(className = "sticky top-0 z-50 w-full") {
        div(className = "$bannerClass rounded-none flex items-center gap-3 px-4 py-2") {
            span(className = "icon-[heroicons--eye] size-5 shrink-0")
            span(className = "flex-1 font-medium text-sm") { +bannerText }
            button(className = "btn btn-sm btn-ghost gap-1") {
                onClick { onBack() }
                span(className = "icon-[heroicons--arrow-left] size-4")
                +currentStrings.backToAdmin
            }
        }
    }
}
