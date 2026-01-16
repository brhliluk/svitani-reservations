package cz.svitaninymburk.projects.reservations.bank

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class FioResponse(
    val accountStatement: FioAccountStatement
)

@Serializable
data class FioAccountStatement(
    val transactionList: FioTransactionList?
)

@Serializable
data class FioTransactionList(
    val transaction: List<JsonObject>
)

data class BankTransaction(
    val remoteId: String, // ID transakce v bance
    val amount: Double,
    val currency: String,
    val variableSymbol: String?,
    val date: String
)

fun parseFioTransactions(jsonResponse: FioResponse): List<BankTransaction> {
    val rawList = jsonResponse.accountStatement.transactionList?.transaction ?: return emptyList()

    return rawList.mapNotNull { rawTx ->
        try {
            val amount = rawTx["column1"]?.jsonObject?.get("value")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val currency = rawTx["column14"]?.jsonObject?.get("value")?.jsonPrimitive?.content ?: "CZK"
            val vs = rawTx["column5"]?.jsonObject?.get("value")?.jsonPrimitive?.content // VS
            val txId = rawTx["column22"]?.jsonObject?.get("value")?.jsonPrimitive?.content ?: ""
            val date = rawTx["column0"]?.jsonObject?.get("value")?.jsonPrimitive?.content ?: ""

            if (amount > 0) BankTransaction(txId, amount, currency, vs, date)
            else null
        } catch (_: Exception) { null }
    }
}