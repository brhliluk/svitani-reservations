package cz.svitaninymburk.projects.reservations

// Verze pro build s hlídáním (`-Pfeatures.slots=true`, výchozí). Protějšek bez hlídání
// je v src/featureSlotsOff; obě musí mít stejné deklarace.

/** Zapnuté volitelné moduly; loguje se při startu, ať je z journalu vidět, co běží. */
internal val enabledFeatures: List<String> = listOf("slots")
