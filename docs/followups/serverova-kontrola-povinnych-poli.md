# Serverová kontrola povinných vlastních polí

Stav: **udělat později.** Nalezeno 20. 10. 2026 při fázi 4 hlídání (`docs/hlidani/`).

## O co jde

Vlastní pole akce, kurzu nebo služby hlídání mohou být povinná
(`CustomFieldDefinition.isRequired`). Hlídá je ale **jen webový formulář**
(`ui/reservation/CustomFields.kt`: hvězdička, `required`, zvýraznění chyby). Server
povinnost vůbec nekontroluje. `reserveInstance`, `reserveSeries`, pořadníky
i `createSlotReservation` uloží rezervaci s chybějící hodnotou.

Ověřeno naostro: rezervace hlídání poslaná přímo na RPC prošla bez povinného pole
„Jména a věk dětí“.

## Proč na tom záleží

- Rezervace může přijít mimo webový formulář: z mobilní appky, ze starší verze klienta
  v mezipaměti, nebo z ručně poslaného požadavku.
- Lektor pak dostane přihlášku bez údajů, které si vyžádal (jméno a věk dítěte, souhlas
  s podmínkami…).

## Návrh

- Jedna kontrola ve sdíleném toku, aby platila pro všechny typy rezervací. Nejspíš
  v `createReservationFlow` a `joinWaitlistFlow` (`core/…/service/Reservation.kt`), kde je
  k dispozici `target.customFields` i `requestData.customValues`.
- Prázdná hodnota se posoudí podle typu pole: prázdný text, chybějící číslo, nezaškrtnutý
  povinný souhlas (`BooleanValue(false)`), neúplný časový rozsah.
- Nová chyba `ReservationError.RequiredFieldMissing(key)` s texty cs/en. Web ji v praxi
  neuvidí, protože formulář nepustí dál; je hlavně pro API a appku.
- Ověřit, co posílá Android appka (`ReservationFormViewModel`). Pokud povinná pole
  nekontroluje, po nasazení začne dostávat chybu místo tichého uložení.
- Rozmyslet staré klienty: pole přidaná do akce až po načtení formuláře by nová kontrola
  odmítla, i když je uživatel neviděl. Asi stačí srozumitelná hláška a nové načtení formuláře.
