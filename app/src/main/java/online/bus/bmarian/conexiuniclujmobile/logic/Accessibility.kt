package online.bus.bmarian.conexiuniclujmobile.logic

import online.bus.bmarian.conexiuniclujmobile.data.Vehicle

fun wheelchairAccess(vehicle: Vehicle): Boolean? = told(vehicle.wheelchairAccessible, "WHEELCHAIR_ACCESSIBLE")

fun bikeAccess(vehicle: Vehicle): Boolean? = told(vehicle.bikeAccessible, "BIKE_ACCESSIBLE")

private fun told(value: String, yes: String): Boolean? = if (value.isEmpty() || value == "UNKNOWN") null else value == yes
