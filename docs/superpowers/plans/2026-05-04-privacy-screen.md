# Privacy Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a GDPR-compliant `/privacy` screen for Svitání Nymburk, wire it to a footer visible on all user-facing pages, and surface a privacy acknowledgment in the registration dialog.

**Architecture:** Fill the existing empty `PrivacyScreen()` composable, add i18n strings for all policy text, move the existing dashboard footer into `UserShell` (so every user screen gets it), add a `/privacy` route, and add a one-line privacy note to the registration dialog.

**Tech Stack:** Kotlin Multiplatform, Kilua (Compose for Web), Tailwind CSS + DaisyUI, i18n via `AppStrings` interface with `CsStrings` / `EnStrings` implementations.

---

## File Map

| Action | File |
|--------|------|
| Modify | `src/webMain/kotlin/cz/svitaninymburk/projects/reservations/i18n/Strings.kt` |
| Modify | `src/webMain/kotlin/cz/svitaninymburk/projects/reservations/i18n/cs/Strings.kt` |
| Modify | `src/webMain/kotlin/cz/svitaninymburk/projects/reservations/i18n/en/Strings.kt` |
| Modify | `src/webMain/kotlin/cz/svitaninymburk/projects/reservations/ui/Privacy.kt` |
| Modify | `src/webMain/kotlin/cz/svitaninymburk/projects/reservations/ui/Main.kt` |
| Modify | `src/webMain/kotlin/cz/svitaninymburk/projects/reservations/ui/dashboard/Layout.kt` |
| Modify | `src/webMain/kotlin/cz/svitaninymburk/projects/reservations/ui/auth/Registration.kt` |

---

### Task 1: Add privacy i18n strings

**Files:**
- Modify: `src/webMain/kotlin/cz/svitaninymburk/projects/reservations/i18n/Strings.kt`
- Modify: `src/webMain/kotlin/cz/svitaninymburk/projects/reservations/i18n/cs/Strings.kt`
- Modify: `src/webMain/kotlin/cz/svitaninymburk/projects/reservations/i18n/en/Strings.kt`

- [ ] **Step 1: Add declarations to the `AppStrings` interface**

In `Strings.kt`, append before the closing `}` of the interface (after `fun shortDayName(index: Int): String`):

```kotlin
    // Privacy Policy
    val privacyTitle: String
    val privacyControllerHeading: String
    val privacyControllerBody: String
    val privacyDataHeading: String
    val privacyDataBody: String
    val privacyPurposeHeading: String
    val privacyPurposeBody: String
    val privacyRecipientsHeading: String
    val privacyRecipientsBody: String
    val privacyRetentionHeading: String
    val privacyRetentionBody: String
    val privacyRightsHeading: String
    val privacyRightsBody: String
    val privacyContactHeading: String
    val privacyContactBody: String
    val privacyPolicyLink: String
    val registrationPrivacyNote: String
```

- [ ] **Step 2: Add Czech implementations to `CsStrings`**

In `cs/Strings.kt`, append before the closing `}` of the `CsStrings` object:

```kotlin
    // Privacy Policy
    override val privacyTitle = "Zásady ochrany osobních údajů"
    override val privacyControllerHeading = "Správce osobních údajů"
    override val privacyControllerBody = "Správcem vašich osobních údajů je spolek Spolu v Nymburce, z.s., IČO: 70877122, se sídlem Komenského 1254/21, 28802 Nymburk. Kontaktní e-mail: info@svitaninymburk.cz."
    override val privacyDataHeading = "Jaké údaje zpracováváme"
    override val privacyDataBody = "Zpracováváme tyto osobní údaje: jméno, příjmení, e-mailová adresa, telefonní číslo, údaje o rezervacích (termín, počet míst, typ platby) a údaje o platbách (výše platby, variabilní symbol)."
    override val privacyPurposeHeading = "Účel a právní základ zpracování"
    override val privacyPurposeBody = "Vaše osobní údaje zpracováváme za účelem vytvoření a správy uživatelského účtu, zpracování rezervací a zasílání potvrzovacích e-mailů. Právní základ je plnění smlouvy dle čl. 6 odst. 1 písm. b) GDPR. Účetní záznamy uchováváme na základě zákonné povinnosti dle čl. 6 odst. 1 písm. c) GDPR."
    override val privacyRecipientsHeading = "Příjemci osobních údajů"
    override val privacyRecipientsBody = "Vaše údaje mohou být předány: Google LLC (Gmail/Google Workspace) – doručování e-mailů; FIO banka, a.s. – párování příchozích plateb s rezervacemi."
    override val privacyRetentionHeading = "Doba uchování osobních údajů"
    override val privacyRetentionBody = "Údaje uživatelského účtu uchováváme po dobu trvání účtu nebo 3 roky od poslední aktivity. Záznamy o rezervacích a platbách uchováváme 5 let dle zákona č. 563/1991 Sb., o účetnictví."
    override val privacyRightsHeading = "Vaše práva"
    override val privacyRightsBody = "Máte právo na přístup ke svým osobním údajům (čl. 15), opravu (čl. 16), výmaz (čl. 17), omezení zpracování (čl. 18), přenositelnost (čl. 20) a právo vznést námitku (čl. 21). Stížnost můžete podat u Úřadu pro ochranu osobních údajů (ÚOOÚ), Pplk. Sochora 27, 170 00 Praha 7, www.uoou.cz."
    override val privacyContactHeading = "Kontakt"
    override val privacyContactBody = "Pro uplatnění svých práv nebo dotazy k ochraně osobních údajů nás kontaktujte na: info@svitaninymburk.cz."
    override val privacyPolicyLink = "Zásady ochrany osobních údajů"
    override val registrationPrivacyNote = "Registrací berete na vědomí naše"
```

- [ ] **Step 3: Add English implementations to `EnStrings`**

In `en/Strings.kt`, append before the closing `}` of the `EnStrings` object:

```kotlin
    // Privacy Policy
    override val privacyTitle = "Privacy Policy"
    override val privacyControllerHeading = "Data Controller"
    override val privacyControllerBody = "The data controller is Spolu v Nymburce, z.s., ID No.: 70877122, registered at Komenského 1254/21, 28802 Nymburk, Czech Republic. Contact email: info@svitaninymburk.cz."
    override val privacyDataHeading = "What data we collect"
    override val privacyDataBody = "We process the following personal data: first name, last name, email address, phone number, reservation data (date, number of seats, payment type) and payment data (amount, variable symbol)."
    override val privacyPurposeHeading = "Purpose and legal basis"
    override val privacyPurposeBody = "We process your personal data to create and manage your user account, process reservations, and send confirmation emails. The legal basis is the performance of a contract under Art. 6(1)(b) GDPR. Accounting records are retained on the basis of a legal obligation under Art. 6(1)(c) GDPR."
    override val privacyRecipientsHeading = "Data recipients"
    override val privacyRecipientsBody = "Your data may be shared with: Google LLC (Gmail/Google Workspace) – for email delivery; FIO banka, a.s. – for pairing incoming payments with reservations."
    override val privacyRetentionHeading = "Data retention"
    override val privacyRetentionBody = "User account data is retained for the duration of the account or 3 years from the last activity. Reservation and payment records are retained for 5 years under Czech Accounting Act No. 563/1991 Coll."
    override val privacyRightsHeading = "Your rights"
    override val privacyRightsBody = "You have the right to access your personal data (Art. 15), rectification (Art. 16), erasure (Art. 17), restriction of processing (Art. 18), data portability (Art. 20), and the right to object (Art. 21). You may lodge a complaint with the Czech Data Protection Authority (ÚOOÚ), Pplk. Sochora 27, 170 00 Praha 7, www.uoou.cz."
    override val privacyContactHeading = "Contact"
    override val privacyContactBody = "To exercise your rights or for questions about data processing, contact us at: info@svitaninymburk.cz."
    override val privacyPolicyLink = "Privacy Policy"
    override val registrationPrivacyNote = "By registering you acknowledge our"
```

- [ ] **Step 4: Verify compilation**

```bash
cd /home/lukasbrhlik/Projekty/Reservations && ./gradlew compileKotlinJs
```

Expected: `BUILD SUCCESSFUL`. If you see `error: 'X' is not implemented`, you missed an override in CsStrings or EnStrings — add it.

- [ ] **Step 5: Commit**

```bash
cd /home/lukasbrhlik/Projekty/Reservations
git add src/webMain/kotlin/cz/svitaninymburk/projects/reservations/i18n/Strings.kt \
        src/webMain/kotlin/cz/svitaninymburk/projects/reservations/i18n/cs/Strings.kt \
        src/webMain/kotlin/cz/svitaninymburk/projects/reservations/i18n/en/Strings.kt
git commit -m "feat: add privacy policy i18n strings (cs + en)"
```

---

### Task 2: Implement `PrivacyScreen`

**Files:**
- Modify: `src/webMain/kotlin/cz/svitaninymburk/projects/reservations/ui/Privacy.kt`

- [ ] **Step 1: Replace the empty `PrivacyScreen` body**

Replace the entire contents of `Privacy.kt` with:

```kotlin
package cz.svitaninymburk.projects.reservations.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.html.div
import dev.kilua.html.h1
import dev.kilua.html.h2
import dev.kilua.html.p

@Composable
fun IComponent.PrivacyScreen() {
    val strings by strings

    div(className = "max-w-3xl mx-auto px-4 py-10") {
        h1(className = "text-2xl font-bold mb-8") { +strings.privacyTitle }

        PrivacySection(strings.privacyControllerHeading, strings.privacyControllerBody)
        PrivacySection(strings.privacyDataHeading, strings.privacyDataBody)
        PrivacySection(strings.privacyPurposeHeading, strings.privacyPurposeBody)
        PrivacySection(strings.privacyRecipientsHeading, strings.privacyRecipientsBody)
        PrivacySection(strings.privacyRetentionHeading, strings.privacyRetentionBody)
        PrivacySection(strings.privacyRightsHeading, strings.privacyRightsBody)
        PrivacySection(strings.privacyContactHeading, strings.privacyContactBody)
    }
}

@Composable
private fun IComponent.PrivacySection(heading: String, body: String) {
    div(className = "mb-6") {
        h2(className = "text-lg font-semibold mb-2") { +heading }
        p(className = "text-base-content/80 leading-relaxed") { +body }
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd /home/lukasbrhlik/Projekty/Reservations && ./gradlew compileKotlinJs
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
cd /home/lukasbrhlik/Projekty/Reservations
git add src/webMain/kotlin/cz/svitaninymburk/projects/reservations/ui/Privacy.kt
git commit -m "feat: implement PrivacyScreen with GDPR sections"
```

---

### Task 3: Add `/privacy` route, move footer to `UserShell`, add privacy link

**Files:**
- Modify: `src/webMain/kotlin/cz/svitaninymburk/projects/reservations/ui/Main.kt`
- Modify: `src/webMain/kotlin/cz/svitaninymburk/projects/reservations/ui/dashboard/Layout.kt`

The existing footer lives inside `DashboardLayout` in `Layout.kt`. Moving it to `UserShell` ensures every user-facing page (dashboard, reservation detail, my reservations, reset password, privacy) shows the same footer. The dashboard currently has it; the others don't — this fixes the inconsistency.

- [ ] **Step 1: Remove the footer from `DashboardLayout`**

In `Layout.kt`, delete these lines (they are at the bottom of the `DashboardLayout` composable, just before `ReservationModal`):

```kotlin
        footer(className = "footer footer-center p-8 text-base-content/50") {
            aside {
                a(href="#", className = "link link-hover") { +currentStrings.contact }
                p { +currentStrings.copyright }
            }
        }
```

- [ ] **Step 2: Add footer + privacy link to `UserShell` in `Main.kt`**

`UserShell` is at the bottom of `Main.kt`. Its current body is:

```kotlin
    AppHeader(
        user = user,
        onShowMessage = onShowMessage,
        onLogin = onLogin,
        onLogout = onLogout,
        onOpenMyReservations = onOpenMyReservations,
    )
    main(className = "flex-grow") { content() }
```

Replace it with:

```kotlin
    val currentStrings by strings
    AppHeader(
        user = user,
        onShowMessage = onShowMessage,
        onLogin = onLogin,
        onLogout = onLogout,
        onOpenMyReservations = onOpenMyReservations,
    )
    main(className = "flex-grow") { content() }
    footer(className = "footer footer-center p-8 text-base-content/50") {
        aside {
            a(href = "#", className = "link link-hover") { +currentStrings.contact }
            a(href = "/privacy", className = "link link-hover") { +currentStrings.privacyPolicyLink }
            p { +currentStrings.copyright }
        }
    }
```

Also add the required import at the top of `Main.kt` (if not already present):

```kotlin
import dev.kilua.html.footer
import dev.kilua.html.a
```

- [ ] **Step 3: Add the `/privacy` route to the non-admin `browserRouter`**

Inside the non-admin `browserRouter { ... }` block in `Main.kt`, add the `/privacy` route alongside the other routes (e.g. after the `/reset-password` route):

```kotlin
            route("/privacy") {
                view {
                    val router = Router.current
                    UserShell(
                        user = currentUser,
                        onShowMessage = ::showToast,
                        onLogin = { refreshUser() },
                        onLogout = { doLogout() },
                        onOpenMyReservations = { router.navigate("/my-reservations") },
                    ) {
                        PrivacyScreen()
                    }
                }
            }
```

Also add the import for `PrivacyScreen` at the top of `Main.kt` if the file doesn't already import from the `ui` package at that level (it's in the same package `cz.svitaninymburk.projects.reservations.ui`, so no import needed — both `Main.kt` and `Privacy.kt` are in `...ui`).

- [ ] **Step 4: Verify compilation**

```bash
cd /home/lukasbrhlik/Projekty/Reservations && ./gradlew compileKotlinJs
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual verification via dev server**

Start the dev server:

```bash
cd /home/lukasbrhlik/Projekty/Reservations && ./gradlew jsViteRun
```

Check:
- Navigate to `http://localhost:3000/privacy` — all 7 GDPR sections visible, header present.
- Navigate to `http://localhost:3000/` — footer shows "Zásady ochrany osobních údajů" link.
- Click the footer link — navigates to `/privacy`.
- Verify dashboard, my-reservations, and reservation detail pages all show the footer.

- [ ] **Step 6: Commit**

```bash
cd /home/lukasbrhlik/Projekty/Reservations
git add src/webMain/kotlin/cz/svitaninymburk/projects/reservations/ui/Main.kt \
        src/webMain/kotlin/cz/svitaninymburk/projects/reservations/ui/dashboard/Layout.kt
git commit -m "feat: add /privacy route and footer with privacy link to all user screens"
```

---

### Task 4: Add privacy acknowledgment note to registration dialog

**Files:**
- Modify: `src/webMain/kotlin/cz/svitaninymburk/projects/reservations/ui/auth/Registration.kt`

- [ ] **Step 1: Add the privacy note below the submit button**

In `Registration.kt`, the `modal-action` div (around line 165) is followed by the "Už máte účet?" div. Insert a new `p` block between them:

After this block:

```kotlin
            div(className = "modal-action") {
                button(className = "btn btn-primary w-full") {
                       disabled(isLoading || !isFormValid)
                    if (isLoading) span(className = "loading loading-spinner")
                    +"Vytvořit účet"
                    onClick { performRegister() }
                }
            }
```

Add:

```kotlin
            p(className = "text-xs text-center text-base-content/60 mt-1") {
                +currentStrings.registrationPrivacyNote
                +" "
                a(href = "/privacy", className = "link link-primary") {
                    target("_blank")
                    +currentStrings.privacyPolicyLink
                }
                +"."
            }
```

Also add the import at the top of `Registration.kt` (after the existing `import dev.kilua.html.*` — if `*` is used, `a` is already covered):

```kotlin
import dev.kilua.html.a  // only needed if Registration.kt does not use import dev.kilua.html.*
```

Note: `Registration.kt` already has `import dev.kilua.html.*` on line 16, so no new import is needed.

- [ ] **Step 2: Verify compilation**

```bash
cd /home/lukasbrhlik/Projekty/Reservations && ./gradlew compileKotlinJs
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Manual verification via dev server**

Start dev server if not already running:

```bash
cd /home/lukasbrhlik/Projekty/Reservations && ./gradlew jsViteRun
```

Check:
- Open registration dialog (click "Přihlásit se" → switch to register).
- Below the "Vytvořit účet" button, small grey text: "Registrací berete na vědomí naše Zásady ochrany osobních údajů."
- Clicking the link opens `/privacy` in a new tab.

- [ ] **Step 4: Commit**

```bash
cd /home/lukasbrhlik/Projekty/Reservations
git add src/webMain/kotlin/cz/svitaninymburk/projects/reservations/ui/auth/Registration.kt
git commit -m "feat: add privacy policy acknowledgment to registration dialog"
```
