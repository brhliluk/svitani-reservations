package cz.svitaninymburk.projects.reservations.slot.ui

import cz.svitaninymburk.projects.reservations.slot.SlotAdminServiceInterface
import cz.svitaninymburk.projects.reservations.slot.SlotServiceInterface

/**
 * RPC hlídání z prohlížeče. `getService()` z jádra o službách modulu neví a vygenerovaný
 * klient vzniká jen v jsMain/wasmJsMain, proto expect/actual (viz docs/hlidani/rozhodnuti.md).
 */
expect fun slotService(): SlotServiceInterface

/** Admin RPC hlídání; routy jsou jen pro admina. */
expect fun slotAdminService(): SlotAdminServiceInterface
