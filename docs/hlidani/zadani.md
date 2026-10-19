# Hlídání dětí: rezervace libovolného časového intervalu

Zadání pro implementaci v repozitáři `svitani-reservations` (rezervace.svitaninymburk.cz).

## Jak postupovat

- Pracuj po fázích (viz konec dokumentu). **Na začátku každé fáze** napiš krátký plán: které soubory měníš a proč. **Na konci každé fáze** spusť testy a zastav se kvůli review. Do další fáze nepokračuj bez mého potvrzení.
- Než začneš psát kód, projdi existující implementaci akcí a kurzů. Nové věci mají vypadat stejně jako stávající, ne jako cizí modul.
- Když narazíš na rozpor mezi tímto zadáním a tím, co je v kódu, nebo na rozhodnutí, které tu není, zeptej se. Nevymýšlej to sám.
- Testy: `./gradlew jvmTest` a webové testy. Přesné názvy tasků zjistíš přes `./gradlew tasks --group verification`.

## Konvence projektu (dodržuj)

- **Stack:** Kotlin Multiplatform, Ktor + Koin (`DI.kt`), Exposed nad SQLite, Kilua RPC (`@RpcService` rozhraní v `src/commonMain/.../service/`), Kilua UI s Tailwind/DaisyUI v `src/webMain`. Sdílený model je v modulu `shared`.
- **Chyby:** Arrow `Either` / `Raise`, sealed hierarchie v `shared/.../error/`. Lokalizace chyb je v `ErrorStrings` / `CsErrorStrings`.
- **Repozitáře:** vždy trojice `Interface.kt` + `InMemory.kt` + `Database.kt`. Schéma zakládá `MigrationUtils` v `plugins/Database.kt`.
- **UI:** obrazovka má `Screen`/komponentu, `…Model.kt` a `usecase/…UseCases.kt`. Use cases jsou čisté funkce a k nim patří `…UseCasesSpec` ve `src/webTest`.
- **Texty** jdou do `i18n/Strings.kt`, a to cs i en. **Komentáře v kódu piš česky**, stejně jako ve zbytku projektu.
- **Audit:** nové admin a rezervační akce loguj přes `AuditService` stejně jako u stávajících rezervací.

## Kontext a rozhodnutí (už rozhodnuto, neměň bez konzultace)

Svítání chce nabídnout hlídání dětí. Admin nastaví dny a časová okna (třeba út a čt 8–12). Rodič si v rámci těchto oken rezervuje **libovolný interval** (třeba 9:00–11:30) pro **N dětí**.

- **Nemodelovat jako `EventInstance`.** Stovky instancí by zaplevelily kalendář, admin rozvrh, audit i e-maily.
- **Nestavět samostatný rezervační systém.** Rezervace hlídání je běžná `Reservation`, jen s novou variantou reference. Díky tomu se znovu využije platba (VS, párování přes Fio), peněženka, storno, e-maily, claim, „Moje rezervace“ a admin seznam rezervací.
- **Dostupnost se neukládá jako sloty.** Ukládají se pravidla a výjimky, volné úseky se počítají za běhu.
- **Obsazenost se nepočítá do counteru** (žádný `occupiedSpots`). Spočítá se z aktivních rezervací v transakci. Souběh hlídá `Mutex` per služba. Aplikace běží v jedné instanci nad SQLite.
- **„Libovolný interval“** znamená začátek i konec zarovnaný na krok služby (15 nebo 30 min, nastaví admin) a délku mezi min. a max. Kapacita se kontroluje na **každém kroku** intervalu.
- **Počet dětí = `seatCount`.** Kapacita = max. počet dětí současně.
- **Obecné pojmenování:** `SlotService`, „časové sloty“. Funkčnost ale implementuj jen pro tento případ (YAGNI).
- **Mimo rozsah první verze:** waitlist, docházka, změna času existující rezervace (řeší se stornem a novou rezervací) a vlastní pole zvlášť pro každé dítě (zatím jedno textové pole „Jména a věk dětí“).

## Doménový model (`shared`, nový balíček např. `…reservations.slot`)

```kotlin
@Serializable
data class SlotService(
    val id: Uuid,
    val title: String,
    val description: String,
    val step: Duration,                 // krok zarovnání, 15 nebo 30 min
    val minDuration: Duration,
    val maxDuration: Duration,
    val pricePerHour: Double,           // za jedno dítě
    val capacity: Int,                  // max. dětí současně
    val maxSeatsPerBooking: Int,
    val allowedPaymentTypes: List<PaymentType>,
    val customFields: List<CustomFieldDefinition>,
    val ownerEmails: List<String>,
    val reservationDeadline: Duration?, // jak dlouho před začátkem se zavírá
    val bookingHorizon: Duration,       // jak daleko dopředu jde rezervovat
    val isPublished: Boolean,
)

@Serializable
data class AvailabilityRule(
    val id: Uuid,
    val serviceId: Uuid,
    val dayOfWeek: DayOfWeek,
    val from: LocalTime,
    val to: LocalTime,
    val validFrom: LocalDate,
    val validTo: LocalDate?,
)

@Serializable
data class AvailabilityException(
    val id: Uuid,
    val serviceId: Uuid,
    val date: LocalDate,
    val closed: Boolean,                // true = ten den zavřeno
    val from: LocalTime? = null,        // jinak náhradní okno pro ten den
    val to: LocalTime? = null,
)

@Serializable
data class SlotSegment(val start: LocalDateTime, val end: LocalDateTime, val free: Int, val capacity: Int)

@Serializable
data class DayAvailability(val date: LocalDate, val segments: List<SlotSegment>)
```

Jeden den může mít víc pravidel, tedy víc oken. Výjimka pro daný den pravidla nahrazuje.

**Čistá funkce dostupnosti** (vedle `RecurrenceUtils.kt`, testovatelná v `jvmTest`):
`expandAvailability(service, rules, exceptions, bookedIntervals, from, to): List<DayAvailability>`. Rozloží okna na kroky a od kapacity odečte souběžně rezervovaná místa.

## Napojení na rezervace

V `shared/.../reservation/Reservation.kt`:
- `Reference.Slot(id = serviceId, start: LocalDateTime, end: LocalDateTime)` s `@SerialName("slot")`.
- `ReservationTarget.Slot(service: SlotService, start, end)`:
  - `price` = `pricePerHour`
  - `maxCapacity` = minimum volných míst přes interval
  - `startDateTime` / `endDateTime` = interval
  - `allowMultipleSeats` = `maxSeatsPerBooking > 1`
- `CreateSlotReservationRequest(serviceId, start, end, …)` implementující `ReservationRequestData` (včetně `walletCode` a `acknowledgedDuplicate` jako u ostatních requestů).

Persistence (`repository/reservation/Database.kt`):
- `ReferenceDbDiscriminator.SLOT`, `reference_id` = id služby.
- Nové nullable sloupce `slot_start`, `slot_end`.

Na `Reference` a `ReservationTarget` je v kódu kolem 75 `when` větví zhruba ve 12 souborech, mimo jiné `service/Reservation.kt`, `Payment.kt`, `Email.kt`, `EmailResend.kt`, `WaitlistPromotion.kt`, `DuplicateReservation.kt`, `ReservationClaim.kt`, `Admin.kt`, `AuditContext.kt` a UI `ReservationDetailModel.kt` a `ReservationSubmitUseCases.kt`. Kompilátor je ukáže všechny. Pro `Slot`:
- Waitlist a `lessonShare` neplatí (`null` nebo no-op).
- Storno a refund jdou stejnou cestou jako u jednorázové akce.
- Duplicitní kontrola hlídá překryv intervalů téže služby se stejným kontaktem.

**Cena:** v `shared/.../event/PriceCalculator.kt` přidej do `calculateTotalPrice` parametr `timeMultiplier: Double = 1.0`, který se uplatní v pass 1 (multiplikativně, před aditivními modifikátory). Pro slot ho naplň délkou intervalu v hodinách. Stávající volání se nesmí změnit, doplň testy do `PriceCalculatorSpec`. Ve formuláři vlastních polí u slotové služby nenabízej `TimeRange` s `TimeMultiplier`, jinak by se hodiny násobily dvakrát.

## Backend

Nové repozitáře: `SlotServiceRepository`, `AvailabilityRuleRepository`, `AvailabilityExceptionRepository` (trojice soubory), registrace v `DI.kt`, mock data v `MockDataLoader.kt`.

Nové RPC rozhraní `SlotServiceInterface`:
- `listPublished(): List<SlotService>`
- `getAvailability(serviceId, from: LocalDate, to: LocalDate): List<DayAvailability>`, kde rozsah omezí horizont služby (veřejné, i bez přihlášení)
- `reserveSlot(request: CreateSlotReservationRequest, userId: Uuid?): Either<ReservationError.CreateReservation, Reservation>`
- admin: CRUD služby, pravidel a výjimek; `getDayOverview(serviceId, date)` vrátí segmenty a rezervace daného dne

**Validace v `reserveSlot`** proběhne celá na serveru pod `Mutex` služby a v jedné transakci. Nic z klienta se nebere jako ověřené:
1. Služba existuje a je publikovaná.
2. `start`/`end` jsou zarovnané na `step`, `end > start` a délka je v rozmezí min–max.
3. Celý interval leží v jednom okně daného dne (pravidla po aplikaci výjimek).
4. `now` je před uzávěrkou a interval je v horizontu. Čas ber přes `AppTimeZone`, stejně jako stávající uzávěrky.
5. `seatCount ≤ maxSeatsPerBooking` a na **každém** kroku platí `free ≥ seatCount`. Aktivní stavy ber podle `INACTIVE_RESERVATION_STATUSES`.
6. Cenu spočítej znovu přes `calculateTotalPrice(..., timeMultiplier = hodiny)`.
7. Zbytek (VS, peněženka, free → rovnou potvrzená, e-mail, audit) projde **sdíleným tokem** z `service/Reservation.kt`. Neduplikuj ho. Pokud `createReservationFlow` vyžaduje úpravu, aby přijal `ReservationTarget.Slot`, udělej ji co nejmenší.

E-maily a `ICalGenerator`: interval místo začátku akce. Nové chybové typy přidej do `error/Reservation.kt` (například mimo okno, nezarovnáno, plno v části intervalu) s cs/en texty.

**Mobilní API** (`plugins/MobileApi.kt`) se nesmí rozbít. `MyReservationListItem` používá `/api/v1/reservations/mine`, takže `isSeries` **ponech** a jen **přidej** pole `kind: ReservationKind` (`INSTANCE`, `SERIES`, `SLOT`) s defaultem. Ověř, že serializace odpovědí mobilního API nevrací `Reference` ani `ReservationTarget` se slotem klientům, kteří ho neznají. Pokud ano, navrhni řešení a zeptej se.

## Veřejné UI (`src/webMain/.../ui/slots/`)

- **Vstup:** třetí záložka `DashboardTab.SLOTS` v `DashboardTabs` (`ui/dashboard/Layout.kt`) a route `/hlidani` v `Main.kt`, která otevře dashboard s touto záložkou. Při více publikovaných službách nejdřív výběr služby, při jedné rovnou ona. `CalendarView` neměň.
- **Obrazovka** má tři kroky na jedné stránce, v tomto pořadí:
  1. **Den:** vodorovný pás dní v horizontu, jen dny s nějakým oknem, s indikátorem „volno“/„plno“ pro zvolený počet dětí.
  2. **Počet dětí:** stepper 1 až `maxSeatsPerBooking`. Je **před** výběrem času, protože určuje dostupnost. Změna počtu výběr času zruší.
  3. **Čas:** svislá osa kroků (řádek = krok, `min-h-11`) s pruhem obsazenosti a textem „volno N“. První klepnutí nastaví začátek, další klepnutí na pozdější řádek rozšíří konec. Klepnutí před začátek začne nový výběr. Interval nesmí přejít přes nedostupný krok, jinak se ukáže inline chyba. Vybrané řádky mají `bg-primary/10 text-primary`.
- **Sticky souhrn dole:** „út 14. 10. · 9:00–11:30 · 2 děti · 2,5 h · 375 Kč“ a tlačítko „Pokračovat“. To otevře stávající `ReservationModal` (`ui/reservation/Form.kt`) s `ReservationTarget.Slot`.
- **Úpravy `ReservationModal` pro slot:**
  - Nadpis s intervalem.
  - Počet míst jen ke čtení s odkazem „změnit“, který modal zavře.
  - Řádek ceny „X Kč × N dětí × H h“.
  - Vlastní pole, platby a peněženka beze změny.
- **Styl:** jako agenda view kalendáře (`bg-base-100 rounded-xl shadow-sm border border-base-200`), ikony `heroicons`.
- **Use cases** (`usecase/SlotSelectionUseCases.kt`), všechny čisté funkce s testy ve `SlotSelectionUseCasesSpec`:
  - `isStepSelectable(segment, seats)`
  - `extendSelection(current, tappedIndex, segments, seats, service)`, která vrací nový výběr nebo chybu
  - `selectionHours`
  - `selectionPrice` (volá sdílený `calculateTotalPrice`)
  - `dayHasAvailability(day, seats)`
- **Detail rezervace a „Moje rezervace“:** zobrazení „Hlídání · út 14. 10. 9:00–11:30“.

## Admin UI (`src/webMain/.../ui/admin/slots/`)

- Položka „Hlídání“ v `AdminNavLinks` (`ui/admin/Screen.kt`). Route `/admin/slots` vede na seznam služeb, `/admin/slots/{id}` na detail se třemi částmi:
  - **Den:** navigace po dnech, stejná časová osa jako veřejná (obsazenost / kapacita) a pod ní seznam rezervací seřazený podle začátku („9:00–11:30 · Nováková · 2 děti · zaplaceno“). Proklik vede do stávajícího admin detailu rezervace. Přidej verzi vhodnou pro tisk.
  - **Rozvrh:** řádky Po–Ne, každý s 0..n časovými rozsahy, platnost od–do, seznam výjimek s datepickerem (`kilua.tempus.dominus`). Při uložení změny, která vyřadí existující aktivní rezervace, ukaž varování s jejich počtem a seznamem. **Nic automaticky neruš.**
  - **Nastavení:** pole `SlotService`. Vlastní pole přes `CustomFieldsBuilderSection`, platby a e-maily přes `EventFormComponents`.
- `/admin/schedule`: jeden souhrnný blok na den s oknem hlídání („Hlídání 8–12 · max. 6/8“), který odkazuje na denní přehled.
- `AdminReservationsScreen`: filtr podle typu reference včetně slotů.

## Fáze a akceptační kritéria

0. **Průzkum.** Projdi zmíněné soubory a vypiš místa `when` na `Reference`/`ReservationTarget` a jak by se pro `Slot` řešila. Popiš, jak teď funguje `createReservationFlow` a kde ho napojíš. Kód zatím nepiš, čekej na potvrzení.
1. **Model a dostupnost.** Model ve `shared` a `expandAvailability`. Testy pokrývají: víc oken za den, výjimku „zavřeno“, výjimku s jinými hodinami, `validFrom`/`validTo`, rezervace přes hranici kroků, plný krok.
2. **Persistence.** Tabulky a repozitáře (všechny tři varianty), sloupce `slot_start`/`slot_end`, `SLOT` discriminator, DI, mock data. Aplikace nastartuje nad existující DB bez ruční migrace.
3. **Napojení rezervací.** `Reference.Slot`, `ReservationTarget.Slot`, opravené všechny `when`, `timeMultiplier` v ceně. Všechny stávající testy procházejí.
4. **Rezervační služba.** `SlotServiceInterface`, `reserveSlot` se všemi validacemi, e-mail, iCal, audit. Testy v `jvmTest` pokrývají každé validační pravidlo a souběh: dva paralelní požadavky na poslední místo, uspěje právě jeden.
5. **Veřejné UI.** Záložka, `/hlidani`, výběr, modal, „Moje rezervace“. Use case testy procházejí, `MyReservationListItem` je zpětně kompatibilní.
6. **Admin UI.** Služba, rozvrh, výjimky, denní přehled, souhrn v rozvrhu, filtr v rezervacích.
7. **Dokončení.** Anglické texty, OpenAPI (`resources/openapi/documentation.yaml`), pokud se mění mobilní API, a krátký popis funkce v README.

## Otevřené otázky (zeptej se, až na ně narazíš)

- Storno: platí pro hlídání stejná storno pravidla a uzávěrka jako pro akce, nebo vlastní?
- Lze hlídání platit z peněženky (permanentky)?
- Zaokrouhlování ceny u intervalů typu 2 h 15 min: přesně, nebo na celé koruny nahoru?
- Musí být rodič přihlášený, nebo stačí rezervace bez účtu jako u akcí?
