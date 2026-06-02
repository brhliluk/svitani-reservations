package cz.svitaninymburk.projects.reservations.ui.wallet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.reservation.detail.CopyToClipboardButton
import cz.svitaninymburk.projects.reservations.wallet.WalletInfo
import dev.kilua.core.IComponent
import dev.kilua.form.text.text
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h1
import dev.kilua.html.label
import dev.kilua.html.p
import dev.kilua.html.span
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch

@Composable
fun IComponent.WalletScreen(initialCode: String = "", initialEmail: String = "") {
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val reservationService = getService<ReservationServiceInterface>(RpcSerializersModules)

    var code by remember { mutableStateOf(initialCode) }
    var email by remember { mutableStateOf(initialEmail) }
    var isLoading by remember { mutableStateOf(false) }
    var walletInfo by remember { mutableStateOf<WalletInfo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    div(className = "min-h-screen bg-base-200 flex items-center justify-center p-4") {
        div(className = "card w-full max-w-md bg-base-100 shadow-xl") {
            div(className = "card-body gap-6") {

                div {
                    h1(className = "card-title text-2xl") { +currentStrings.walletLookupTitle }
                    p(className = "text-base-content/70 text-sm mt-1") { +currentStrings.walletLookupSubtitle }
                }

                div(className = "flex flex-col gap-4") {
                    div(className = "form-control w-full") {
                        label(className = "label pb-1") {
                            span(className = "label-text font-medium") { +currentStrings.walletCode }
                        }
                        text(value = code, className = "input input-bordered w-full font-mono") {
                            placeholder("SVIT-XXXX-XXXX")
                            onInput { code = value ?: "" }
                        }
                    }

                    div(className = "form-control w-full") {
                        label(className = "label pb-1") {
                            span(className = "label-text font-medium") { +currentStrings.walletLookupEmailLabel }
                        }
                        text(value = email, className = "input input-bordered w-full") {
                            placeholder("vas@email.cz")
                            onInput { email = value ?: "" }
                        }
                    }

                    if (errorMessage != null) {
                        div(className = "alert alert-error text-sm py-2") {
                            span(className = "icon-[heroicons--exclamation-circle] size-5")
                            span { +errorMessage!! }
                        }
                    }

                    button(className = "btn btn-primary w-full") {
                        disabled(isLoading || code.isBlank() || email.isBlank())
                        if (isLoading) span(className = "loading loading-spinner loading-sm")
                        onClick {
                            isLoading = true
                            errorMessage = null
                            walletInfo = null
                            scope.launch {
                                reservationService.getWalletInfo(code.trim(), email.trim()).fold(
                                    ifRight = { info ->
                                        walletInfo = info
                                        isLoading = false
                                    },
                                    ifLeft = { error ->
                                        errorMessage = error.localizedMessage(currentStrings)
                                        isLoading = false
                                    }
                                )
                            }
                        }
                        +currentStrings.walletLookupSubmit
                    }
                }

                if (walletInfo != null) {
                    val info = walletInfo!!
                    div(className = "divider my-0")
                    div(className = "flex flex-col gap-4") {
                        if (!info.emailMatches) {
                            div(className = "alert alert-warning py-2 px-3 text-sm") {
                                span(className = "icon-[heroicons--exclamation-triangle] size-4 flex-shrink-0")
                                span { +currentStrings.walletEmailMismatchWarning }
                            }
                        }

                        CopyToClipboardButton(currentStrings.walletCode, info.code)

                        div(className = "stats bg-base-200 rounded-box w-full") {
                            div(className = "stat p-4") {
                                div(className = "stat-title text-xs") { +currentStrings.walletBalance }
                                div(className = "stat-value text-success text-2xl") {
                                    +"${info.balance.toInt()} ${currentStrings.currency}"
                                }
                                div(className = "stat-desc") {
                                    +"${currentStrings.walletExpiresOn}: ${info.seasonResetDay}. ${info.seasonResetMonth}."
                                }
                            }
                        }

                        if (info.balance > 0) {
                            div(className = "text-sm text-base-content/70") {
                                +currentStrings.walletUseHint
                            }
                        }
                    }
                }
            }
        }
    }
}
