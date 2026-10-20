package cz.svitaninymburk.projects.reservations.slot.ui

import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.slot.SlotServiceInterface
import cz.svitaninymburk.projects.reservations.slot.SlotServiceInterfaceImpl

actual fun slotService(): SlotServiceInterface = SlotServiceInterfaceImpl(RpcSerializersModules)
