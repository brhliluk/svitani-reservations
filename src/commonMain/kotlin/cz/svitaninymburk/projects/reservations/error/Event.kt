package cz.svitaninymburk.projects.reservations.error

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @SerialName("event") sealed interface EventError : AppError {
    @Serializable @SerialName("get_dashboard_data") sealed interface GetDashboardData: EventError
    @Serializable @SerialName("get_instances") sealed interface GetInstances: EventError, GetDashboardData
    @Serializable @SerialName("get_series") sealed interface GetSeries: EventError, GetDashboardData
    @Serializable @SerialName("get_definitions") sealed interface GetDefinitions: EventError, GetDashboardData

    @Serializable @SerialName("create_definition") sealed interface CreateEventDefinition: EventError
    @Serializable @SerialName("update_definition") sealed interface UpdateEventDefinition: EventError
    @Serializable @SerialName("delete_definition") sealed interface DeleteEventDefiniton: EventError

    @Serializable @SerialName("create_instance") sealed interface CreateEventInstance: EventError
    @Serializable @SerialName("update_instance") sealed interface UpdateEventInstance: EventError
    @Serializable @SerialName("delete_instance") sealed interface DeleteEventInstance: EventError

    @Serializable data class EventDefinitionNotFound(val id: String): CreateEventInstance, UpdateEventDefinition, DeleteEventDefiniton
    @Serializable data class EventInstanceNotFound(val id: String): UpdateEventInstance, DeleteEventInstance

    @Serializable data object FailedToGetInstances: GetInstances
    @Serializable data object FailedToGetSeries: GetSeries
    @Serializable data object FailedToGetDefinitions: GetDefinitions
}

val EventError.localizedMessage: String get() = when (this) {
    is EventError.EventInstanceNotFound -> "Událost s id $id nenalezena"
    is EventError.EventDefinitionNotFound -> "Šablona události s id $id nenalezena"
    is EventError.FailedToGetDefinitions -> "Nepodařilo se získat definice"
    is EventError.FailedToGetInstances -> "Nepodařilo se získat události"
    is EventError.FailedToGetSeries -> "Nepodařilo se získat kurzy"
}