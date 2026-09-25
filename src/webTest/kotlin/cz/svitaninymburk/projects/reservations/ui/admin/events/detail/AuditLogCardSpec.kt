package cz.svitaninymburk.projects.reservations.ui.admin.events.detail

import cz.svitaninymburk.projects.reservations.audit.AuditActorType
import cz.svitaninymburk.projects.reservations.audit.AuditCategory
import cz.svitaninymburk.projects.reservations.audit.AuditEvent
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.i18n.cs.CsStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        reservationId: Uuid? = null,
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
        reservationId = reservationId,
    )

    @Test
    fun detailSpojujePrijemceCastkuAPopis() {
        assertEquals(
            "kdo@example.com · 300 Kč · Spárováno z FIO",
            auditDetailText(event(recipient = "kdo@example.com", amount = 300.0, detail = "Spárováno z FIO"), CsStrings),
        )
    }

    @Test
    fun prazdnaPoleSeVynechaji() {
        assertEquals("", auditDetailText(event(), CsStrings))
        assertEquals("Storno", auditDetailText(event(detail = "Storno"), CsStrings))
    }

    /** Nula je u storna zdarma běžná a "0 Kč" by čtenáře jen mátlo. */
    @Test
    fun nuloveCastceSeNepiseMena() {
        assertEquals("Storno", auditDetailText(event(amount = 0.0, detail = "Storno"), CsStrings))
    }

    @Test
    fun filtrMaPopisekProKazdouKategorii() {
        assertEquals(CsStrings.auditFilterAll, categoryLabel(null, CsStrings))
        assertEquals(CsStrings.auditCategoryEmail, categoryLabel(AuditCategory.EMAIL, CsStrings))
        assertEquals(CsStrings.auditCategoryPayment, categoryLabel(AuditCategory.PAYMENT, CsStrings))
        assertEquals(CsStrings.auditCategoryReservation, categoryLabel(AuditCategory.RESERVATION, CsStrings))
        assertEquals(CsStrings.auditCategoryManagement, categoryLabel(AuditCategory.MANAGEMENT, CsStrings))
    }

    /** Filtr býval vyjmenovaný ručně — nová kategorie by se v něm neobjevila a nešla by vybrat. */
    @Test
    fun filtrNabiziVsechnyKategorie() {
        assertEquals(null, AUDIT_FILTER_OPTIONS.first())
        assertEquals(AuditCategory.entries.toSet(), AUDIT_FILTER_OPTIONS.filterNotNull().toSet())
    }

    /** Správa akce má vlastní filtr, nesmí se míchat do rezervací. */
    @Test
    fun zmenyAkceMajiVlastniKategoriiAPopisky() {
        val management = listOf(
            AuditEventType.DEFINITION_CREATED, AuditEventType.DEFINITION_UPDATED, AuditEventType.DEFINITION_DELETED,
            AuditEventType.EVENT_CREATED, AuditEventType.EVENT_UPDATED, AuditEventType.EVENT_PUBLISHED,
            AuditEventType.EVENT_UNPUBLISHED, AuditEventType.EVENT_DELETED,
            AuditEventType.SERIES_CREATED, AuditEventType.SERIES_UPDATED, AuditEventType.SERIES_PUBLISHED,
            AuditEventType.SERIES_UNPUBLISHED, AuditEventType.SERIES_DELETED,
            AuditEventType.LESSON_CREATED, AuditEventType.LESSON_UPDATED, AuditEventType.LESSON_PUBLISHED,
            AuditEventType.LESSON_UNPUBLISHED, AuditEventType.LESSON_DELETED,
        )
        management.forEach { type ->
            assertEquals(AuditCategory.MANAGEMENT, type.category, "$type patří do správy akce")
            assertTrue(CsStrings.auditEventLabel(type) != type.name, "cs popisek pro $type je holý enum")
        }
        assertEquals("Kurz smazán", CsStrings.auditEventLabel(AuditEventType.SERIES_DELETED))
        assertEquals(
            "Course deleted",
            cz.svitaninymburk.projects.reservations.i18n.en.EnStrings.auditEventLabel(AuditEventType.SERIES_DELETED),
        )
        assertFalse(event(type = AuditEventType.EVENT_DELETED, reservationId = Uuid.random(), recipient = "a@b.cz").isResendable)
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
        val text = auditDetailText(event(walletCode = "SVIT-AB12-CD34", amount = 300.0, detail = "z toho 200 Kč zpět z kreditu"), CsStrings)
        assertEquals("SVIT-AB12-CD34 · 300 Kč · z toho 200 Kč zpět z kreditu", text)
    }

    @Test
    fun neodeslanePotvrzeniJdePoslatZnovu() {
        val zaznam = event(
            type = AuditEventType.EMAIL_RESERVATION_CONFIRMATION,
            recipient = "anezka@example.com",
            reservationId = Uuid.random(),
        )
        assertTrue(zaznam.isResendable)
    }

    /** Odkaz na reset hesla nese jednorázový token — poslat ho znovu by vydalo nový. */
    @Test
    fun mailSJednorazovymTokenemSePreposlatNeda() {
        val zaznam = event(
            type = AuditEventType.EMAIL_PASSWORD_RESET,
            recipient = "kdo@example.com",
            reservationId = Uuid.random(),
        )
        assertFalse(zaznam.isResendable)
    }

    /** Bez rezervace není z čeho mail poskládat — kopie odeslané zprávy se neuchovává. */
    @Test
    fun zaznamBezRezervaceSePreposlatNeda() {
        val zaznam = event(type = AuditEventType.EMAIL_RESERVATION_CONFIRMATION, recipient = "kdo@example.com")
        assertFalse(zaznam.isResendable)
    }

    @Test
    fun zaznamMimoKategoriiMailuTlacitkoNema() {
        assertFalse(event(type = AuditEventType.RESERVATION_CREATED, reservationId = Uuid.random()).isResendable)
    }
}
