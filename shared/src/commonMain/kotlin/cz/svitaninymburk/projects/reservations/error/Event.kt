package cz.svitaninymburk.projects.reservations.error

import cz.svitaninymburk.projects.reservations.i18n.ErrorStrings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @SerialName("event") sealed interface EventError : AppError {
    @Serializable @SerialName("get_dashboard_data") sealed interface GetDashboardData: EventError
    @Serializable @SerialName("get_instances") sealed interface GetInstances: EventError, GetDashboardData
    @Serializable @SerialName("get_series") sealed interface GetSeries: EventError, GetDashboardData
    @Serializable @SerialName("get_definitions") sealed interface GetDefinitions: EventError, GetDashboardData

    @Serializable @SerialName("create_instance") sealed interface CreateEventInstance: EventError

    @Serializable @SerialName("get_instance") sealed interface GetInstance: EventError
    @Serializable @SerialName("get_series_detail") sealed interface GetSeriesDetail: EventError

    @Serializable data class EventDefinitionNotFound(val id: String): CreateEventInstance
    @Serializable data class EventInstanceNotFound(val id: String): GetInstance
    @Serializable data class EventSeriesNotFound(val id: String): GetSeriesDetail

    @Serializable data object FailedToGetInstances: GetInstances
    @Serializable data object FailedToGetSeries: GetSeries
    @Serializable data object FailedToGetDefinitions: GetDefinitions
}

fun EventError.localizedMessage(strings: ErrorStrings): String = when (this) {
    is EventError.EventInstanceNotFound -> strings.errorEventInstanceNotFoundId(id)
    is EventError.EventDefinitionNotFound -> strings.errorEventDefinitionNotFoundId(id)
    is EventError.EventSeriesNotFound -> strings.errorEventSeriesNotFound
    is EventError.FailedToGetDefinitions -> strings.errorFailedToGetDefinitions
    is EventError.FailedToGetInstances -> strings.errorFailedToGetInstances
    is EventError.FailedToGetSeries -> strings.errorFailedToGetSeries
}