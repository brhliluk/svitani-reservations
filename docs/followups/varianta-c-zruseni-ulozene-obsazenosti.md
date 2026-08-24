# Varianta C — zrušit uložený čítač obsazenosti

Stav: **chceme udělat, ale ne hned.** Čeká na to, až odvozená obsazenost chvíli poběží v produkci.

Navazuje na: `docs/superpowers/specs/2026-08-22-odvozena-obsazenost-lekci-design.md` (sekce „Zamítnuté varianty"), nasazeno 2026-08-24.

## O co jde

Zrušit `occupied_spots` jako uložený sloupec — u lekcí (`event_instances`) i u kurzů
(`event_series`) — a obsazenost počítat výhradně z rezervací. S ním zmizí
`incrementOccupiedSpots` / `decrementOccupiedSpots` a odpadne i celý dekorátor
`SeriesAwareEventInstanceRepository`: nebude co obohacovat při čtení ani co strhávat
při zápisu, protože nebude co ukládat.

Do stejné vlny patří `occupied_waitlist`. Trpí tímtéž — je to ručně udržovaný čítač,
který se dá rozjet, a startovní přepočet ho dnes jen léčí místo aby ho zrušil.

## Proč to chceme

Dnešní model má jednu chybu, která existuje **jen proto, že je co přepisovat.**

`strip` počítá zátěž kurzu znovu v okamžiku zápisu. Když se mezi načtení a uložení
vejde nová přihláška do kurzu, přijdeš o přímou rezervaci:

```
čtení:        uloženo 1 + zátěž 2  =  3
mezitím:      někdo se přihlásí do kurzu → zátěž 3
admin uloží:  strip zapíše max(0, 3 − 3) = 0     ← přímá rezervace je pryč
```

Startovní přepočet to při dalším restartu spraví, takže to není urgentní. Ale je to
třída chyby, kterou odvozený model ruší z definice — není sloupec, není co
zklobrovat.

Druhý důvod je prostší: dnes existují **čtyři** místa, která musí počítat obsazenost
shodně — `loadFrom`, `enrich`/`strip` v dekorátoru, SQL guard a startovní přepočet.
Ověřili jsme, že se shodují, a jedno kolo revize se přesně na tuhle shodu muselo
zaměřit. Varianta C to sráží na jedno místo.

## Co to bude stát

**Ochrana proti souběhu se musí přestěhovat.** Dnes je to jeden atomický `UPDATE`
s podmínkou `occupied_spots + N + zátěž <= capacity`. Bez uloženého sloupce není co
updatovat, takže kontrola musí dovnitř transakce, která vkládá rezervaci. To je zásah
i do rezervačního toku **běžných akcí bez série**, kterých se původní chyba vůbec
netýkala — a přesně proto jsme to v srpnu 2026 neudělali.

Vedlejší efekt, který za to mluví: `createReservation` dnes commituje zabrání místa
a uložení rezervace ve **dvou** transakcích. Sloučení do jedné tuhle trhlinu zavírá.

## Předpoklady, než se do toho pustíme

1. Odvozená obsazenost musí mít za sebou reálný provoz — plné kurzy, omluvenky,
   drop-in rezervace do plné lekce. Při nasazení 2026-08-24 byly kurzy obsazené
   na 0–2 místa z 6–15 a omluvenek bylo nula, takže o modelu nic nedokazoval.
2. Startovní přepočet nesmí nic opravovat. Když po týdnech provozu při startu
   nemění žádnou hodnotu, znamená to, že se zápisové cesty chovají správně a je
   bezpečné je nahradit.

## Co se dá udělat dřív a nezávisle

- `MobileApiSmokeTest` a spol. nepokrývají řetěz omluvenka → posun čekatele přes
  reálný dekorátor a Exposed guard; ověřeno zatím jen čtením kódu. Test na kapacitě 1
  by to uzavřel (na kapacitě 10 projde, i kdyby omluvenka neuvolnila nic).
- `./gradlew allTests` nebuildí `:androidApp`, přestože konzumuje `shared/`. Změna
  ve sdíleném modulu s android konzumentem by tímhle gatem prošla nezachycená.
