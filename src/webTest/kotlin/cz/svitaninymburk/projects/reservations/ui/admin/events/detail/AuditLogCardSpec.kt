package cz.svitaninymburk.projects.reservations.ui.admin.events.detail

import cz.svitaninymburk.projects.reservations.audit.AuditActorType
import cz.svitaninymburk.projects.reservations.audit.AuditCategory
import cz.svitaninymburk.projects.reservations.audit.AuditEvent
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.i18n.cs.CsStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AuditLogCardSpec {

    private fun event(
        type: AuditEventType = AuditEventType.RESERVATION_CREATED,
        recipient: String? = null,
        amount: Double? = null,
        detail: String? = null,
        instanceId: Uuid? = null,
        walletCode: String? = null,
    ) = AuditEvent(
        id = Uuid.random(),
        occurredAt = Clock.System.now(),
        type = type,
        actorType = AuditActorType.CUSTOMER,
        actorLabel = "kdo@example.com",
        subjectLabel = "Jana Nováková",
        recipient = recipient,
        amount = amount,
        detail = detail,
        instanceId = instanceId,
        walletCode = walletCode,
    )

    @Test
    fun detailSpojujePrijemceCastkuAPopis() {
        assertEquals(
            "kdo@example.com · 300 Kč · Spárováno z FIO",
            auditDetailText(event(recipient = "kdo@example.com", amount = 300.0, detail = "Spárováno z FIO")),
        )
    }

    @Test
    fun prazdnaPoleSeVynechaji() {
        assertEquals("", auditDetailText(event()))
        assertEquals("Storno", auditDetailText(event(detail = "Storno")))
    }

    /** Nula je u storna zdarma běžná a "0 Kč" by čtenáře jen mátlo. */
    @Test
    fun nuloveCastceSeNepiseMena() {
        assertEquals("Storno", auditDetailText(event(amount = 0.0, detail = "Storno")))
    }

    @Test
    fun filtrMaPopisekProKazdouKategorii() {
        assertEquals(CsStrings.auditFilterAll, categoryLabel(null, CsStrings))
        assertEquals(CsStrings.auditCategoryEmail, categoryLabel(AuditCategory.EMAIL, CsStrings))
        assertEquals(CsStrings.auditCategoryPayment, categoryLabel(AuditCategory.PAYMENT, CsStrings))
        assertEquals(CsStrings.auditCategoryReservation, categoryLabel(AuditCategory.RESERVATION, CsStrings))
    }

    /** Bez popisku by v tabulce svítil holý název enumu. */
    @Test
    fun kazdyTypMaPopisekVObouJazycich() {
        AuditEventType.entries.forEach { type ->
            assertTrue(CsStrings.auditEventLabel(type).isNotBlank(), "chybí cs popisek pro $type")
            assertTrue(
                cz.svitaninymburk.projects.reservations.i18n.en.EnStrings.auditEventLabel(type).isNotBlank(),
                "chybí en popisek pro $type",
            )
        }
    }

    // --- Prokliky ---

    /** Na detailu lekce by odkaz „otevřít lekci" vedl sám na sebe. */
    @Test
    fun odkazNaVlastniLekciSeNenabizi() {
        val lekce = Uuid.random()
        assertEquals(null, auditLessonLink(event(instanceId = lekce), lekce.toString()))
    }

    @Test
    fun odkazNaJinouLekciSeNabizi() {
        val jina = Uuid.random()
        assertEquals(jina, auditLessonLink(event(instanceId = jina), Uuid.random().toString()))
    }

    @Test
    fun zaznamBezLekceOdkazNema() {
        assertEquals(null, auditLessonLink(event(), Uuid.random().toString()))
    }

    @Test
    fun kodPenezenkySeUkazeVDetailu() {
        val text = auditDetailText(event(walletCode = "SVIT-AB12-CD34", amount = 300.0, detail = "z toho 200 Kč zpět z kreditu"))
        assertEquals("SVIT-AB12-CD34 · 300 Kč · z toho 200 Kč zpět z kreditu", text)
    }
}
