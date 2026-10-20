# Hlídání dětí: rozhodnutí a stav

Doplněk k [zadani.md](zadani.md). Co je tady, má **přednost před zadáním**. Jde o rozhodnutí
z fáze 0 (19. 10. 2026) a o odchylky, na kterých jsme se domluvili.

## Stav

- **Fáze 0 (průzkum): hotovo.**
- **Modularizace: hotovo a nasazeno** (commit `f4cbea4`). Jádro je v modulu `:core`, kořenový
  projekt je jen aplikace. Viz „Moduly“ níž.
- **Fáze 1: hotovo.** Modul `:slots` (model, `expandAvailability`, testy), přepínač
  `-Pfeatures.slots` a `Features.kt`.
- **Fáze 2: hotovo.** Tabulky a repozitáře modulu, sloupce `slot_start`/`slot_end`,
  napojení přes `ServerFeature`, mock data.
- **Fáze 3: hotovo.** `Reference.Slot`, `ReservationTarget.Slot`, všechna `when`,
  `ReservationTargetResolver`, `timeMultiplier` v ceně.
- **Fáze 4: hotovo, čeká na review.** `SlotServiceInterface` (`listServices`, `getAvailability`,
  `reserveSlot`) se všemi validacemi pod `Mutex`, e-maily a pozvánka s intervalem, testy validací
  a souběhu. Admin RPC je přesunuté do fáze 6.
- **Další krok: fáze 5** (veřejné UI).

## Rozhodnutí

| Téma | Rozhodnutí |
|---|---|
| Jen pro adminy | **Nepublikovaná služba** (`isPublished = false`). Nepublikovanou službu vidí a rezervuje jen admin (`isAdminCaller()`), stejně jako dnes nepublikované akce (`ensureBookable`, `EventService`). Zveřejnění = přepnout `isPublished`. Žádný feature flag. Rezervace z proklikávání jsou ostré (VS, e-maily, Fio), proto vlastní e-maily a pak storno. Vstup do UI se ukáže, jen když API vrátí aspoň jednu službu. |
| Vstup v UI | **Odkaz v hlavičce** vedle „Přehled“ a vlastní stránka `/hlidani`. **Ne** třetí záložka: `DashboardTab`/`DashboardTabs` ze zadání v kódu neexistuje, rozcestník má jen přepínač `ViewMode { LIST, CALENDAR }`. |
| Souběh | **Jen `Mutex` služby** přes validaci i uložení, bez obalující DB transakce (aplikace běží v jedné instanci). Repozitáře si dál otevírají vlastní transakci přes `dbQuery`. |
| Dohledání cíle rezervace | **Sjednotit** převod `Reference` → `ReservationTarget` z dnešních 7 kopií do jednoho resolveru v jádru (fáze 3). Kopie: `ReservationService.getDetail`, `cancelReservation`, `AuthenticatedReservationService.claimReservation`, `ReservationClaimService.targetOf`, `PaymentPairingService.targetFor`, `EmailResendService.targetFor`, `WaitlistPromoter`. |
| Storno | **Stejně jako u jednorázové akce**: zákazník do začátku intervalu, kredit do peněženky jen do 18:00 den předem (`refundDeadlineFor`), admin kdykoli. |
| Peněženka | **Ano**, jako u akcí (`walletCode`, sdílený tok strhne zůstatek). |
| Zaokrouhlení ceny | **Přesně**, bez zaokrouhlení (např. 2 h 15 min × 150 Kč = 337,50 Kč). |
| Přihlášení | **Není nutné**, rezervace jde i bez účtu jako u akcí. Claim k účtu funguje stejně. |
| `ReservationTarget.Slot` | **Datový cíl bez `SlotService`**: jádro nesmí znát typy z modulu. Ploché položky: id služby, název, `start`/`end`, cena za hodinu, **`freeSeats`** (minimum volných míst přes interval, vyplní server), max. míst na rezervaci, platby, vlastní pole, e-maily lektorů. `maxCapacity = freeSeats`. |
| Audit | `AuditSubject` u hlídání nese jen `reservationId` a název. Sloupec `slot_service_id` se nepřidává. |
| Duplicity | Nová hodnota `DuplicateScope.SLOT_OVERLAP` s vlastním textem: aktivní rezervace téže služby se stejným e-mailem a překryvem intervalu. |
| Nezarovnaný čas (19. 10.) | **Kroky + poznámka.** Rezervace je dál zarovnaná na krok (admin může zvolit 15 min). Skutečné časy (např. přivedu 9:25, vyzvednu 10:05) napíše rodič do volitelného pole „Poznámka“, které nová služba dostane do vlastních polí jako výchozí. Platí se a blokuje celý krok. |

## Moduly (odchylka od zadání)

Zadání počítalo s modelem v `shared` a se `Slot` přímo v jádru. Chceme ale jít nasadit **bez
hlídání**, proto:

- **`:core`** (`core/src/…`) je jádro: RPC rozhraní, server, UI a testy. Zná jen obecný pojem
  rezervace časového úseku:
  - `Reference.Slot(id, start, end)` s `@SerialName("slot")`,
  - datový `ReservationTarget.Slot`,
  - diskriminátor `SLOT` a sloupce `slot_start`/`slot_end`,
  - rozhraní (např. `SlotBookingProvider`), přes které se jádro ptá modulu na cíl rezervace.
    Bez modulu se rezervace hlídání chová jako rezervace na smazanou akci.
- **`:slots`** (nový modul, závisí na `:core`) obsahuje všechno ostatní:
  - `SlotService`, pravidla, výjimky a `expandAvailability`,
  - tabulky a repozitáře (trojice soubory),
  - RPC rozhraní a jejich implementace, Koin modul,
  - veřejné i admin UI, i18n a testy.
  - Model **nepatří do `shared`**, Android appka ho nepotřebuje.
- **Aplikace** (kořenový projekt) se skládá přes `Features.kt`. Soubor má dvě verze:
  - `src/featureSlotsOn/…` přidá Koin moduly, tabulky, serverové routy, webové routy,
    položku v admin navigaci a odkaz v hlavičce,
  - `src/featureSlotsOff/…` je prázdná,
  - vybírá se přes `-Pfeatures.slots` (výchozí `true`) a `:slots` se pak přidá jako závislost
    jen podmíněně.
  - Build i testy musí projít i s `-Pfeatures.slots=false`.
- Jádro potřebuje pro modul **místa napojení**: seznam tabulek pro `MigrationUtils`, Koin moduly,
  RPC routy (veřejné i admin), webové routy, admin navigaci a odkaz v hlavičce. Dnes je
  `ALL_TABLES`, `initRpcKoin`, `configureRouting`, `MainLayout` i `AdminNavLinks` napevno v jádru,
  takže se musí otevřít parametrem nebo registrem.

### Kilua RPC v modulu (ověřeno pokusem)

- Plugin generuje pro **každý modul** globální `dev.kilua.rpc.getServiceManager()` a `getService()`
  a ty o službách jiných modulů nevědí. V modulu proto:
  - server používá vygenerovaný `XServiceInterfaceManager` napřímo
    (`applyRoutes(XServiceInterfaceManager, RpcSerializersModules)`),
  - web používá vygenerovaný `XServiceInterfaceImpl(RpcSerializersModules)`. Ten vzniká jen
    v `jsMain`/`wasmJsMain`, ve `webMain` je proto potřeba `expect fun xService()` a k ní
    `actual` v obou cílech.
- V modulu vypnout tasky `jarWithJs`/`jvmRun` stejně jako v `core/build.gradle.kts`.
- Ve fat jaru budou `dev/kilua/rpc/Generated*Kt` z `:core` i ze `:slots` (stejné jméno třídy).
  Funguje to, protože se volání vkládají přímo (`inline`), ale je to křehké. Zvážit vyřazení
  těchto tříd z jaru modulu.
- `@RpcService` nikdy do kořenového projektu (ten KSP neaplikuje).
- Tailwind třídy ze zdrojáků modulu se do CSS dostanou bez nastavení (ověřeno).

## Rozhodnutí z fáze 1 (k potvrzení při review)

- Model je v `slots/src/commonMain/…/slot/Slot.kt`, `expandAvailability` v `Availability.kt` vedle něj.
  Rezervace vstupují jako `BookedInterval(start, end, seats)`.
- Kroky jsou zarovnané **od půlnoci**. Z nezarovnaného okna (8:10–9:50 při 30 min) zůstanou celé
  kroky 8:30–9:30.
- Překrývající se nebo **navazující** okna téhož dne se slijí (8–10 a 10–12 = 8–12), takže
  rezervace může přejít přes jejich styk.
- Výjimka nahrazuje všechna pravidla dne. Víc výjimek na jeden den: stačí jedna „zavřeno“ a je
  zavřeno, jinak platí všechna jejich okna. Výjimka bez „zavřeno“ a bez času = zavřeno.
- Volná místa se nesnižují pod 0 (přeplnění po snížení kapacity).
- `expandAvailability` nezná „teď“: uzávěrku a horizont ořízne volající (fáze 4). Dny bez kroku
  ve výsledku nejsou.
- `Features.kt` je zatím jen v `jvmMain`. Webová verze přibude s místy napojení v UI.

## Rozhodnutí z fáze 2 (k potvrzení při review)

- **Místo napojení na serveru:** `ServerFeature` v jádru (jméno, tabulky, Koin moduly, mock data).
  Aplikace předá seznam do `reservationsServer(features)` a jádro modul zapojí. Ve fázi 4 se přidají
  RPC routy. Jádro při startu zaloguje „Volitelné moduly: slots“.
- **`ReferenceDbDiscriminator.SLOT` až ve fázi 3** spolu s `Reference.Slot`. Bez něj by čtení řádku
  `SLOT` nemělo na co mapovat. Sloupec je `varchar`, takže nová hodnota schéma nemění.
- `APP_TIMEZONE` a `nowInAppTimeZone` v jádru jsou nově veřejné, aby modul počítal čas stejně.
- Tabulky `slot_services`, `slot_availability_rules`, `slot_availability_exceptions`. Délky se
  ukládají v ms a časy dne jako text „HH:MM“, stejně jako u kurzů. E-maily lektorů jsou JSON sloupec
  služby, ne `event_owner_emails` jádra.
- **Výjimky nemají unikátní index na (služba, den).** Víc výjimek na den řeší `expandAvailability`
  (potvrzeno 19. 10.).
- Pravidla se ukládají celou sadou (`replaceForService`), protože admin ukládá týden jako celek.
- `delete` služby maže pravidla a výjimky výslovně. SQLite bez `PRAGMA foreign_keys` cizí klíče
  nevynucuje a `CASCADE` by nezabral.
- Mock data: nepublikovaná služba „Hlídání dětí“, út a čt 8–12, nejbližší čtvrtek zavřeno.

## Rozhodnutí z fáze 3 (k potvrzení při review)

- `Reference.Slot` a `ReservationTarget.Slot` jsou ve `shared`, protože obě hierarchie jsou
  zapečetěné. `ReservationTarget.Slot` má proti tabulce výš navíc **`capacity`**: e-mail lektorovi
  hlásí obsazenost „X/Y“. Obsazenost úseku = `capacity − freeSeats`, tedy nejvyšší v jeho průběhu.
- **`ReservationTargetResolver`** v jádru nahradil převod reference na cíl v 8 službách (7 ze
  seznamu výš a `GmailEmailService.titleFor`). Úseky se ptá rozhraní **`SlotBookingProvider`**, které
  modul zaregistruje do Koinu (ve fázi 4). Bez něj je cíl `null`, tedy jako smazaná akce.
  Služby mají resolver jako poslední parametr s výchozí hodnotou, takže testy se neměnily.
- `ReservationRepository.findByReference` je přepsaný na `when`. Úsek se hledá i podle času, stejně
  jako v InMemory. Nový dotaz `findActiveSlotReservations(serviceId, from, to)` vrací aktivní
  rezervace služby, které se s rozsahem překrývají.
- `createReservationFlow` má `timeMultiplier`, ale **zůstává `private`**. `internal` mezi moduly
  nefunguje, takže veřejný vstup pro modul přibude ve fázi 4 spolu s `reserveSlot`.
- `DuplicateScope.SLOT_OVERLAP` až ve fázi 4, kde se poprvé použije.
- E-mail lektorovi u úseku zatím ukazuje jen začátek (`LectorTarget.Occasion`). Texty s intervalem
  přijdou ve fázi 4. Pozvánka do kalendáře už má interval (`ICalGenerator.forTarget`).
- Web: `ReservationSubmitUseCase` má volitelný `slotSubmitter`. RPC hlídání je v modulu, takže ho
  ve fázi 5 dodá modul. Bez něj skončí odeslání jako rezervace neexistující akce.
- `MyReservationListItem.kind` přibyl s výchozí hodnotou odvozenou z `isSeries`. Web zatím ukazuje
  úsek jako kartu akce, úprava je ve fázi 5.
- `AuditBackfill` plní `instanceId` jen u rezervací na termín, ne u úseků.
- Android: větev pro úsek v `ReservationFormViewModel` hodí chybu, protože appka úsek nikdy
  nenačte. **Android build lokálně neprojde** (`AndroidSdkHandler`, chybí JAXB pod JDK 25),
  nezávisle na téhle změně, takže appka ověřená kompilací není.

## Rozhodnutí z fáze 4 (k potvrzení při review)

- **Admin RPC** (CRUD služby, rozvrhu, výjimek a denní přehled) až ve fázi 6 spolu s admin UI.
  Ve fázi 4 je jen veřejné `SlotServiceInterface`.
- `getAvailability` vrací jen kroky, které jde ještě rezervovat: začátek nejdřív za uzávěrku od
  „teď“ a konec nejpozději na konci horizontu. **Horizont se tak počítá ke konci úseku** a uzávěrka
  k začátku. Každý souvislý výběr z vrácených kroků pak validací projde (kromě kapacity a délky).
- Chyby: nové `SlotNotAligned` (i konec ≤ začátek nebo jiný den), `SlotDurationOutOfRange`,
  `SlotOutsideOpeningHours`, `SlotBeyondBookingHorizon`, `SlotCapacityExceeded` a
  `DuplicateScope.SLOT_OVERLAP`, vše s texty cs/en. Znovu použité: `ReservationNotFound`
  (neznámá nebo skrytá služba), `InvalidSeatCount` (0 i nad maximum na rezervaci),
  `EventAlreadyStarted`, `ReservationDeadlinePassed`, `PaymentTypeNotAllowed`.
- Pořadí validací: služba → počet míst → zarovnání → délka → okno → začátek/uzávěrka/horizont →
  kapacita na každém kroku. Platbu a duplicitu ověří jádro v `createSlotReservation`, pořád pod
  zámkem modulu.
- Modul má dvě třídy: `SlotAvailability` (obsazenost, zároveň `SlotBookingProvider`) a
  `SlotBookingService` (RPC). Jedna třída by v Koinu vytvořila cyklus resolver → modul →
  `ReservationService` → resolver.
- Jádro: veřejné `ReservationService.createSlotReservation`, `isAdminCall()` v `util/CallContext.kt`
  a `ServerFeature.publicRoutes`, kde jsou routy pod volitelným JWT.
- **Fat jar:** `dev/kilua/rpc/Generated*Kt` se z jaru modulu vyřazují (`slots/build.gradle.kts`),
  v aplikaci zůstane jen kopie z jádra. Funguje to, protože modul z nich potřebuje jen prázdné
  `registerRpcServiceExceptions()`.
- KSP v modulu vyžaduje metadata webové části jádra. Jejich kompilace padala na
  `navigator.languages.length` v `i18n/Strings.kt`, opraveno na `navigator.languages.toList()`.
- Cíl existující rezervace (`targetFor`) počítá volná místa včetně té rezervace, tedy stav „teď“.
- **Nález mimo hlídání:** server nekontroluje povinná vlastní pole (ani u akcí), hlídá je jen
  formulář. Odloženo, viz `docs/followups/serverova-kontrola-povinnych-poli.md`.

## Nálezy z fáze 0 pro fázi 3

- `ExposedReservationRepository.findByReference` dnes všechno, co není `Instance`, bere jako
  `SERIES` (přes `if`, takže to kompilátor nehlídá). Přepsat na `when`. Pro obsazenost hlídání
  přidat dotaz „aktivní rezervace služby v rozsahu“. InMemory porovnává celou referenci včetně
  časů.
- `createReservationFlow` (`core/…/service/Reservation.kt`) je privátní a cenu počítá sám.
  Změnit na `internal`, přidat `timeMultiplier: Double = 1.0` a předat ho do
  `calculateTotalPrice`. Validace hlídání pak proběhne pod `Mutex` v modulu a zavolá tenkou
  internal/veřejnou metodu jádra.
- `androidApp` sdílí `shared`. Kdyby se `ReservationTarget` rozšířil, má vyčerpávající `when`
  v `ReservationFormViewModel`, kam je potřeba přidat větev pro slot (appka ho nepodporuje).
- **Mobilní API** je v pořádku:
  - `Reference` ani `ReservationTarget` se ke klientovi nedostanou (`reserve/*` vrací vlastní
    typy, `mine` vrací list itemy, `payment` vrací `MobilePaymentInfo`),
  - Android parsuje s `ignoreUnknownKeys`, takže nové pole `kind: ReservationKind` je bezpečné.
- `/admin/schedule` se stránkuje v databázi podle instancí. Denní bloky hlídání dopočítat pro dny
  aktuální stránky (rozhodnout ve fázi 6).
- Cena: `timeMultiplier` se uplatní v pass 1 vedle `TimeMultiplier` z vlastních polí. Aditivní
  modifikátory se hodinami nenásobí. U služby hlídání nenabízet ve vlastních polích `TimeRange`
  s `TimeMultiplier`.

## Postup

Fáze podle zadání. Na začátku každé fáze krátký plán, na konci `./gradlew allTests` (ne `test`,
ten přeskočí webové testy) a zastavit se kvůli review. Commit, push a deploy jen na výslovný pokyn.
