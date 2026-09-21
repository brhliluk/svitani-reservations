package cz.svitaninymburk.projects.reservations.ui.admin.events.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.admin.AuditLogPage
import cz.svitaninymburk.projects.reservations.audit.AuditCategory
import cz.svitaninymburk.projects.reservations.audit.AuditEvent
import cz.svitaninymburk.projects.reservations.audit.AuditOutcome
import cz.svitaninymburk.projects.reservations.i18n.AppStrings
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.admin.events.detail.usecase.AUDIT_PAGE_SIZE
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Pagination
import cz.svitaninymburk.projects.reservations.ui.util.pageCount
import cz.svitaninymburk.projects.reservations.util.humanReadable
import dev.kilua.core.IComponent
import app.softwork.routingcompose.Router
import dev.kilua.html.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid

/** Popisek kategorie do filtru. */
fun categoryLabel(category: AuditCategory?, strings: AppStrings): String = when (category) {
    null -> strings.auditFilterAll
    AuditCategory.RESERVATION -> strings.auditCategoryReservation
    AuditCategory.EMAIL -> strings.auditCategoryEmail
    AuditCategory.PAYMENT -> strings.auditCategoryPayment
}

/**
 * Lekce, na kterou má smysl z řádku odkázat.
 *
 * Vrací `instanceId` jen tehdy, když se liší od právě otevřené akce — na detailu
 * lekce by jinak každý řádek nabízel odkaz sám na sebe.
 */
fun auditLessonLink(event: AuditEvent, currentEventId: String): Uuid? =
    event.instanceId?.takeIf { it.toString() != currentEventId }

/**
 * Sloučí do jedné buňky to, co u záznamu dává smysl číst pohromadě:
 * u mailu adresáta, u platby částku, k tomu volný popis.
 */
fun auditDetailText(event: AuditEvent): String = listOfNotNull(
    event.recipient,
    event.walletCode,
    event.amount?.takeIf { it != 0.0 }?.let { "${it.toInt()} Kč" },
    event.detail,
).joinToString(" · ")

@Composable
fun IComponent.AuditLogCard(
    currentEventId: String,
    page: AuditLogPage?,
    errorMessage: String?,
    isExpanded: Boolean,
    isLoading: Boolean,
    currentPage: Int,
    category: AuditCategory?,
    resendingId: Uuid?,
    onToggle: () -> Unit,
    onPageChange: (Int) -> Unit,
    onCategoryChange: (AuditCategory?) -> Unit,
    onResend: (AuditEvent) -> Unit,
) {
    val currentStrings by strings
    val router = Router.current

    div(className = "card bg-base-100 shadow-sm") {
        div(className = "card-body") {

            div(className = "flex items-center justify-between gap-4 flex-wrap") {
                div {
                    h2(className = "card-title") { +currentStrings.auditTitle }
                    p(className = "text-sm text-base-content/60") { +currentStrings.auditSubtitle }
                }
                button(className = "btn btn-sm btn-outline") {
                    onClick { onToggle() }
                    span(className = if (isExpanded) "icon-[heroicons--chevron-up] size-4" else "icon-[heroicons--chevron-down] size-4")
                    +(if (isExpanded) currentStrings.auditHide else currentStrings.auditShow)
                }
            }

            if (!isExpanded) return@div

            div(className = "flex gap-2 flex-wrap mt-4") {
                listOf(null, AuditCategory.RESERVATION, AuditCategory.PAYMENT, AuditCategory.EMAIL).forEach { option ->
                    val active = option == category
                    button(className = if (active) "btn btn-xs btn-primary" else "btn btn-xs btn-ghost") {
                        onClick { onCategoryChange(option) }
                        +categoryLabel(option, currentStrings)
                    }
                }
            }

            when {
                isLoading && page == null -> Loading()

                errorMessage != null -> div(className = "alert alert-error mt-4") {
                    span(className = "icon-[heroicons--x-circle] size-6")
                    span { +errorMessage }
                }

                page == null || page.totalCount == 0L -> div(className = "text-center text-base-content/50 py-10") {
                    +currentStrings.auditEmpty
                }

                else -> {
                    div(className = "overflow-x-auto mt-4") {
                        table(className = "table table-zebra table-sm w-full") {
                            thead {
                                tr {
                                    th { +currentStrings.auditColumnTime }
                                    th { +currentStrings.auditColumnEvent }
                                    th { +currentStrings.auditColumnWho }
                                    th { +currentStrings.auditColumnDetail }
                                    th()
                                }
                            }
                            tbody {
                                page.items.forEach { event ->
                                    tr {
                                        td(className = "whitespace-nowrap text-base-content/70") {
                                            +event.occurredAt
                                                .toLocalDateTime(TimeZone.currentSystemDefault())
                                                .humanReadable
                                        }
                                        td {
                                            div(className = "flex items-center gap-2") {
                                                +currentStrings.auditEventLabel(event.type)
                                                if (event.outcome == AuditOutcome.FAILURE) {
                                                    div(className = "badge badge-error badge-sm") {
                                                        +currentStrings.auditOutcomeFailure
                                                    }
                                                }
                                            }
                                        }
                                        td {
                                            div { +event.subjectLabel }
                                            div(className = "text-xs text-base-content/50") { +event.actorLabel }
                                        }
                                        td(className = "text-sm text-base-content/60 max-w-md") { +auditDetailText(event) }
                                        td(className = "whitespace-nowrap text-right") {
                                            if (event.isResendable) {
                                                AuditLinkButton(
                                                    tooltip = currentStrings.auditResend,
                                                    icon = "icon-[heroicons--paper-airplane]",
                                                    isLoading = resendingId == event.id,
                                                ) { onResend(event) }
                                            }
                                            event.reservationId?.let { reservationId ->
                                                AuditLinkButton(currentStrings.auditOpenReservation) {
                                                    router.navigate("/reservation/$reservationId")
                                                }
                                            }
                                            auditLessonLink(event, currentEventId)?.let { lessonId ->
                                                AuditLinkButton(currentStrings.auditOpenLesson) {
                                                    router.navigate("/admin/events/instance/$lessonId")
                                                }
                                            }
                                            event.walletCode?.let { code ->
                                                AuditLinkButton(currentStrings.auditOpenWallet) {
                                                    router.navigate("/admin/wallets/$code")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Pagination(
                        page = currentPage,
                        totalPages = pageCount(page.totalCount, AUDIT_PAGE_SIZE),
                        onPageChange = onPageChange,
                        compact = true,
                    )
                }
            }
        }
    }
}


/** „Otevřít související detail" — stejná ikonka i třídy jako v ParticipantsCard. */
@Composable
private fun IComponent.AuditLinkButton(
    tooltip: String,
    icon: String = "icon-[heroicons--arrow-top-right-on-square]",
    isLoading: Boolean = false,
    onOpen: () -> Unit,
) {
    button(className = "btn btn-ghost btn-xs tooltip tooltip-left") {
        attribute("data-tip", tooltip)
        disabled(isLoading)
        onClick { onOpen() }
        if (isLoading) span(className = "loading loading-spinner loading-xs")
        else span(className = "$icon size-4")
    }
}
