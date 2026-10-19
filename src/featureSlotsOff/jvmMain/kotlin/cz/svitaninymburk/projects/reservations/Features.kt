package cz.svitaninymburk.projects.reservations

// Verze pro build bez hlídání (`-Pfeatures.slots=false`). Protějšek s hlídáním
// je v src/featureSlotsOn; obě musí mít stejné deklarace.

/** Zapnuté volitelné moduly; loguje se při startu, ať je z journalu vidět, co běží. */
internal val enabledFeatures: List<String> = emptyList()
