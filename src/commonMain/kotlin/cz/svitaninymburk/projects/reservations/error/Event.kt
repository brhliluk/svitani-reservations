package cz.svitaninymburk.projects.reservations.error

sealed interface EventError : AppError {
    sealed interface CreateEventDefinition: EventError
    sealed interface UpdateEventDefinition: EventError
    sealed interface DeleteEventDefiniton: EventError

    sealed interface CreateEventInstance: EventError
    sealed interface UpdateEventInstance: EventError
    sealed interface DeleteEventInstance: EventError

    data class EventDefinitionNotFound(val id: String): CreateEventInstance, UpdateEventDefinition, DeleteEventDefiniton
    data class EventInstanceNotFound(val id: String): UpdateEventInstance, DeleteEventInstance
}

val EventError.localizedMessage: String get() = when (this) {
    is EventError.EventInstanceNotFound -> "Událost s id $id nenalezena"
    is EventError.EventDefinitionNotFound -> "Šablona události s id $id nenalezena"
}