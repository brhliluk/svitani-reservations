package cz.svitaninymburk.projects.reservations.ui.util

/**
 * "15. 8." — den a měsíc, kdy kredit v peněžence propadá. Sdílí to admin detail
 * peněženky i veřejné vyhledání zůstatku, takže formát nesmí být opsaný dvakrát.
 */
fun walletResetDateLabel(day: Int, month: Int): String = "$day. $month."
