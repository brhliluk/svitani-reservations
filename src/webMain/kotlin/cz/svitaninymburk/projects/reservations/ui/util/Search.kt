package cz.svitaninymburk.projects.reservations.ui.util

/**
 * Prázdný nebo mezerový dotaz není hledání — přehled ho musí brát jako "žádný
 * filtr", ne jako hledání prázdného řetězce. Sdílí to každá admin tabulka
 * s vyhledávacím polem.
 */
fun searchQueryOf(input: String): String? = input.takeIf { it.isNotBlank() }
