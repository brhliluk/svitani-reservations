package cz.svitaninymburk.projects.reservations.ui.admin.payments.usecase

import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface

const val PAYMENTS_PAGE_SIZE = 20

/**
 * Přehled plateb je jen čtení — žádné akce, žádný toast, takže tady kromě
 * dotazu není co vytáhnout. Stránkování řeší `pageCount` z `ui/util/Paging.kt`.
 */
class AdminPaymentsQueries(private val admin: AdminServiceInterface) {
    suspend fun payments(page: Int, pageSize: Int) = admin.getPaymentEvents(page, pageSize)
}
