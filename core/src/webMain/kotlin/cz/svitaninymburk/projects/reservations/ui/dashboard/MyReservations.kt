package cz.svitaninymburk.projects.reservations.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.ui.components.SeriesLessonsSection
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.MyReservationPaymentMethod
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.cardOpensOnTitleOnly
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.myReservationPaymentMethod
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.StatusBadge
import cz.svitaninymburk.projects.reservations.ui.util.reservationStatusBadge
import cz.svitaninymburk.projects.reservations.ui.util.totalPriceLabel
import cz.svitaninymburk.projects.reservations.util.humanReadable
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h1
import dev.kilua.html.main
import dev.kilua.html.span
import kotlin.uuid.Uuid

@Composable
fun IComponent.MyReservationsScreen(userId: Uuid, onBackClick: () -> Unit) {
    val currentStrings by strings

    div(className = "min-h-screen bg-base-200 flex flex-col font-sans") {
        main(className = "flex-1 w-full max-w-5xl mx-auto px-3 py-4 sm:px-4 sm:py-8 flex flex-col gap-4 sm:gap-6") {

            button(className = "btn btn-ghost btn-sm self-start gap-2 min-h-11") {
                onClick { onBackClick() }
                span(className = "icon-[heroicons--arrow-left] size-5")
                +currentStrings.backToDashboard
            }

            div(className = "flex items-center gap-3") {
                span(className = "icon-[heroicons--ticket] size-7 text-primary")
                h1(className = "text-2xl sm:text-3xl font-bold text-base-content") {
                    +currentStrings.myReservations
                }
            }

            MyReservationsList(userId)
        }
    }
}

@Composable
fun IComponent.MyReservationsList(userId: Uuid) {
    val currentStrings by strings
    val router = Router.current
    val scope = rememberCoroutineScope()
    val model = remember(userId) { buildMyReservationsModel(scope, userId) }

    LaunchedEffect(userId) { model.load() }

    when (val state = model.uiState) {
        is MyReservationsUiState.Loading -> Loading()
        is MyReservationsUiState.Error -> {
            div(className = "alert alert-error") {
                span(className = "icon-[heroicons--exclamation-circle] size-6")
                span { +state.message }
                button(className = "btn btn-sm min-h-11") {
                    onClick { model.retry() }
                    +currentStrings.retry
                }
            }
        }
        is MyReservationsUiState.Success -> {
            if (state.items.isEmpty()) {
                div(className = "alert bg-base-100 shadow-sm animate-fade-in") {
                    span(className = "icon-[heroicons--information-circle] size-6 text-info")
                    +currentStrings.noUpcomingReservations
                }
            } else {
                div(className = "flex flex-col gap-3 animate-fade-in") {
                    state.items.forEach { item ->
                        ReservationCard(item, onCardClick = { router.navigate("/reservation/${item.id}") })
                    }
                }
            }
        }
    }
}

@Composable
private fun IComponent.ReservationCard(item: MyReservationListItem, onCardClick: () -> Unit) {
    val currentStrings by strings
    val scope = rememberCoroutineScope()
    val model = remember(item.id) { buildReservationCardModel(scope, item.id, item.isSeries) }

    val badge = reservationStatusBadge(item.status, item.paymentType, item.totalPrice)
    val titleOnly = cardOpensOnTitleOnly(item)

    div(className = "card bg-base-100 shadow-sm border border-base-200 hover:shadow-md transition-shadow") {
        div(className = "card-body p-4 sm:p-5 gap-3") {

            // Card header — clickable title area for navigation
            div(className = "${if (!titleOnly) "cursor-pointer" else ""}") {
                if (!titleOnly) onClick { onCardClick() }
                div(className = "flex flex-col sm:flex-row justify-between gap-2 sm:items-start") {
                    div(className = "flex flex-col gap-1 min-w-0") {
                        div(
                            className = "font-bold text-base sm:text-lg text-base-content truncate ${if (titleOnly) "cursor-pointer hover:text-primary transition-colors" else ""}"
                        ) {
                            if (titleOnly) onClick { onCardClick() }
                            +item.eventTitle
                        }
                        div(className = "flex items-center gap-2 text-sm text-base-content/60") {
                            span(className = "icon-[heroicons--calendar] size-4 shrink-0")
                            span { +item.startDateTime.humanReadable }
                        }
                    }
                    div(className = "flex flex-col items-start sm:items-end gap-1 shrink-0") {
                        StatusBadge(badge)
                    }
                }
            }

            div(className = "flex flex-wrap items-center justify-between gap-x-4 gap-y-1 text-sm") {
                div(className = "flex items-center gap-1 text-base-content/70") {
                    span(className = "icon-[heroicons--user-group] size-4")
                    +"${item.seatCount} ${currentStrings.persons}"
                }
                div(className = "flex items-center gap-3") {
                    myReservationPaymentMethod(item)?.let { method ->
                        span(className = "text-base-content/60") {
                            when (method) {
                                MyReservationPaymentMethod.CASH -> +currentStrings.paymentMethodCash
                                MyReservationPaymentMethod.TRANSFER -> +currentStrings.bankTransfer
                            }
                        }
                    }
                    span(className = "font-bold text-primary") {
                        +totalPriceLabel(item.totalPrice, currentStrings)
                    }
                }
            }

            // Series expand button
            if (item.isSeries) {
                div(className = "border-t border-base-200 pt-2 mt-1") {
                    button(className = "btn btn-ghost btn-xs gap-1 text-base-content/60 hover:text-primary") {
                        onClick { model.toggleExpanded() }
                        span(className = "icon-[heroicons--${if (model.isExpanded) "chevron-up" else "chevron-down"}] size-4")
                        +if (model.isExpanded) currentStrings.showLess else currentStrings.courseLessons
                    }
                }

                // Expanded lessons area
                if (model.isExpanded) {
                    div(className = "flex flex-col gap-2 mt-2 animate-fade-in") {
                        when (val state = model.lessonsState) {
                            is LessonsLoadState.Idle, is LessonsLoadState.Loading -> {
                                div(className = "flex justify-center py-3") {
                                    span(className = "loading loading-spinner loading-sm text-primary")
                                }
                            }
                            is LessonsLoadState.Error -> {
                                div(className = "alert alert-error alert-sm py-2 text-sm") {
                                    span(className = "icon-[heroicons--exclamation-circle] size-4")
                                    span { +state.message }
                                }
                            }
                            is LessonsLoadState.Success -> {
                                SeriesLessonsSection(
                                    reservationId = item.id,
                                    view = state.view,
                                    onLessonsChanged = { model.reloadLessons() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
