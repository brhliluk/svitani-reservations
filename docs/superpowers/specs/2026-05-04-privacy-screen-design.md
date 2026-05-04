# Privacy Screen Design

**Date:** 2026-05-04
**Status:** Approved

## Overview

Add a GDPR-compliant privacy policy screen to the Reservations web app for Svitání Nymburk. The screen lives at `/privacy`, uses the existing `UserShell` layout, and is linked from a new footer and from the registration dialog.

## Data Controller

- **Name:** Spolu v Nymburce, z.s.
- **IČO:** 70877122
- **Address:** Komenského 1254/21, 28802 Nymburk
- **Contact:** info@svitaninymburk.cz
- **Legal form:** Spolek (civic association), founded 8. 11. 2000

## Privacy Policy Content Sections (Czech, with English translations)

1. **Správce osobních údajů** — full legal identity above
2. **Jaké údaje zpracováváme** — jméno, příjmení, e-mail, telefonní číslo, údaje o rezervacích a platbách
3. **Účel a právní základ** — plnění smlouvy (čl. 6 odst. 1 písm. b) GDPR) pro rezervace a účet; plnění zákonné povinnosti (písm. c) pro účetní záznamy
4. **Příjemci údajů** — Gmail/Google Workspace (doručování e-mailů), FIO banka (párování plateb)
5. **Doba uchování** — uživatelský účet: do odvolání nebo 3 roky nečinnosti; rezervace a platební záznamy: 5 let (zákon č. 563/1991 Sb.)
6. **Vaše práva** — přístup (čl. 15), oprava (čl. 16), výmaz (čl. 17), omezení zpracování (čl. 18), přenositelnost (čl. 20), námitka (čl. 21); právo podat stížnost u ÚOOÚ (uoou.cz)
7. **Kontakt** — info@svitaninymburk.cz

## Architecture

### `Privacy.kt` (fill in existing stub)
- `PrivacyScreen()` composable renders a max-width content column with the seven sections above
- All text via i18n strings (no hardcoded Czech/English)
- Styled with Tailwind/DaisyUI consistent with rest of app

### i18n (`AppStrings`, `CsStrings`, `EnStrings`)
New string keys added for all privacy policy text: section headings and body paragraphs. Follows the existing pattern of adding to the interface + both implementations.

### Routing (`Main.kt`)
New `/privacy` route added to the non-admin `browserRouter` block, wrapped in `UserShell`. No authentication required.

### Footer (new, in `Main.kt`)
Added to the `div(className = "min-h-screen flex flex-col ...")` wrapper. The existing `flex-col` + `main(className = "flex-grow")` structure naturally pushes it to the bottom. Contains:
- Copyright line (reuses existing `copyright` string key)
- Link "Ochrana osobních údajů" / "Privacy Policy" navigating to `/privacy`

### Registration dialog (`Registration.kt`)
A note below the submit button: "Registrací berete na vědomí naše [Zásady ochrany osobních údajů]" (link to `/privacy`). Surfaces the privacy policy at the point of data collection. Uses "berete na vědomí" (acknowledge), not "souhlasíte" (consent), because the legal basis is Art. 6(1)(b) contract performance, not consent. Text via i18n.

## Data Retention (Czech law defaults)

| Data | Retention |
|------|-----------|
| User account (name, surname, email) | Until deletion request or 3 years of inactivity |
| Reservation records | 5 years (zákon č. 563/1991 Sb.) |
| Payment records | 5 years (zákon č. 563/1991 Sb.) |

## Out of Scope

- Cookie consent banner (app uses JWT tokens, no tracking cookies identified)
- DPO appointment (not required for civic associations of this size)
- Automated decision-making disclosure (not applicable)
