package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.slot.slotsServerFeature

// Verze pro build s hlídáním (`-Pfeatures.slots=true`, výchozí). Protějšek bez hlídání
// je v src/featureSlotsOff; obě musí mít stejné deklarace.

internal val serverFeatures: List<ServerFeature> = listOf(slotsServerFeature)
