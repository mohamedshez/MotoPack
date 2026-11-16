package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.BikerBrewCatalog
import com.example.data.model.MaintenanceTaskType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class EventsAndGarageRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun passportStamps_and_bikerEvents_syncAndEnforceOwnerIsolation() = runBlocking {
        val aliceUid = signInTestUser("events_alice_${UUID.randomUUID()}@motopack.test")
        val aliceCore = MotoPackRepository(firestore, auth)
        val aliceEvents = BikerEventsRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceCore.ensureUserProfile("Alice Brew", "", "Triumph Tiger 900").getOrThrow()
        }

        // 1. Stamp a curated Biker & Brew stop
        val stop = BikerBrewCatalog.curatedLocations.first()
        val stamp = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceEvents.stampPassportStop(
                location = stop,
                brewNote = "Double espresso + warm brioche after morning canyon run",
                verifiedGps = true
            ).getOrThrow()
        }
        assertEquals(stop.id, stamp.locationId)
        assertEquals(stop.passportStampCode, stamp.stampCode)

        val observedStamps = withTimeout(FLOW_TIMEOUT_MS) {
            aliceEvents.observePassportStamps(aliceUid).first { it.any { s -> s.locationId == stop.id } }
        }
        assertTrue(observedStamps.any { it.locationId == stop.id && it.verifiedGps })

        // 2. Host a personal Biker & Brew event + toggle RSVP
        val personalEventId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceEvents.createBikerEvent(
                activePack = null,
                creatorName = "Alice Brew",
                title = "Sunday Espresso Convoy",
                description = "Meet at Peak Roasters before Winnats Pass.",
                eventType = "BIKER_BREW",
                locationName = stop.name,
                brewSpecialty = stop.brewSpecialty,
                scheduledLabel = "Sun • 08:30 AM",
                lat = stop.lat,
                lng = stop.lng,
                isPassportEvent = true,
                locationId = stop.id
            ).getOrThrow()
        }
        assertTrue(personalEventId.isNotBlank())

        val personalEvents = withTimeout(FLOW_TIMEOUT_MS) {
            aliceEvents.observePersonalEvents(aliceUid).first { it.any { e -> e.id == personalEventId } }
        }
        val createdPersonal = personalEvents.first { it.id == personalEventId }
        assertTrue(createdPersonal.isPassportEvent)
        assertTrue(aliceUid in createdPersonal.attendeeUids)

        // Toggle RSVP off
        val nowAttending = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceEvents.toggleEventRsvp(createdPersonal, null).getOrThrow()
        }
        assertEquals(false, nowAttending)

        // 3. Create a pack and host a pack event
        val pack = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceCore.createPack(
                packName = "Coffee & Curves",
                displayName = "Alice Brew",
                photoUrl = "",
                bikeModel = "Triumph Tiger 900",
                phoneNumber = "",
                initialLat = stop.lat,
                initialLng = stop.lng,
                batteryPercent = 90,
                isCharging = false
            ).getOrThrow()
        }

        val packEventId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceEvents.createBikerEvent(
                activePack = pack,
                creatorName = "Alice Brew",
                title = "Pack Dawn Brew Run",
                description = "First stop espresso, then Snake Summit.",
                eventType = "RIDE_OUT",
                locationName = stop.name,
                brewSpecialty = stop.brewSpecialty,
                scheduledLabel = "Sat • 07:30 AM",
                lat = stop.lat,
                lng = stop.lng,
                isPassportEvent = true,
                locationId = stop.id
            ).getOrThrow()
        }
        val packEvents = withTimeout(FLOW_TIMEOUT_MS) {
            aliceEvents.observePackEvents(pack.id, aliceUid).first { it.any { e -> e.id == packEventId } }
        }
        assertEquals("Pack Dawn Brew Run", packEvents.first { it.id == packEventId }.title)

        // 4. Cross-user isolation: Bob cannot read Alice's passport stamps or personal events
        signInTestUser("events_bob_${UUID.randomUUID()}@motopack.test")
        val bobEvents = BikerEventsRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobEvents.observePassportStamps(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's passport stamps")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
        }

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobEvents.observePersonalEvents(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's personal biker events")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
        }
    }

    @Test
    fun garageLifecycle_and_crossUserAndUnauthenticatedIsolation() = runBlocking {
        val aliceUid = signInTestUser("garage_alice_${UUID.randomUUID()}@motopack.test")
        val aliceGarage = GarageRepository(firestore, auth)

        // 1. Add primary bike with starter mileage schedule
        val bike = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceGarage.addBikeWithStarterSchedule(
                name = "Alpine Tracer",
                makeModel = "Yamaha Tracer 9 GT",
                year = 2024,
                currentOdometerKm = 12000.0,
                engineCc = 890,
                tirePsiFront = 36,
                tirePsiRear = 42,
                isPrimary = true
            ).getOrThrow()
        }
        assertTrue(bike.id.isNotBlank())

        val bikes = withTimeout(FLOW_TIMEOUT_MS) {
            aliceGarage.observeBikes(aliceUid).first { it.any { b -> b.id == bike.id } }
        }
        assertEquals(12000.0, bikes.first { it.id == bike.id }.currentOdometerKm, 0.01)

        val starterItems = withTimeout(FLOW_TIMEOUT_MS) {
            aliceGarage.observeMaintenanceItems(aliceUid).first { it.size >= 3 }
        }
        val oilReminder = starterItems.first { it.taskType == MaintenanceTaskType.OIL_CHANGE.code }

        // 2. Add custom maintenance reminder and update bike odometer
        val customItemId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceGarage.addMaintenanceReminder(
                bike = bike,
                taskType = MaintenanceTaskType.CUSTOM,
                title = "Fork Seal Inspection",
                intervalKm = 10000.0,
                notes = "Inspect stanchions for weeping oil"
            ).getOrThrow()
        }
        assertTrue(customItemId.isNotBlank())

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceGarage.updateBikeOdometer(bike.id, 14500.0).getOrThrow()
        }

        // 3. Log completed oil service at 14,800 km -> advances odometer and resets reminder nextDueOdometerKm
        val logId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceGarage.completeServiceAndLogHistory(
                bike = bike.copy(currentOdometerKm = 14500.0),
                reminder = oilReminder,
                taskType = MaintenanceTaskType.OIL_CHANGE,
                title = "Yamalube Full Synthetic Oil + Filter",
                odometerKm = 14800.0,
                costAmount = 78.50,
                shopOrDiy = "DIY Garage",
                notes = "Torqued sump plug to 43 Nm"
            ).getOrThrow()
        }
        assertTrue(logId.isNotBlank())

        val logs = withTimeout(FLOW_TIMEOUT_MS) {
            aliceGarage.observeServiceLogs(aliceUid).first { it.any { l -> l.id == logId } }
        }
        assertEquals(78.50, logs.first { it.id == logId }.costAmount, 0.01)

        val updatedBike = withTimeout(FLOW_TIMEOUT_MS) {
            aliceGarage.observeBikes(aliceUid).first {
                it.firstOrNull { b -> b.id == bike.id }?.currentOdometerKm == 14800.0
            }
        }
        assertEquals(14800.0, updatedBike.first { it.id == bike.id }.currentOdometerKm, 0.01)

        val updatedItems = withTimeout(FLOW_TIMEOUT_MS) {
            aliceGarage.observeMaintenanceItems(aliceUid).first { list ->
                list.any { it.id == oilReminder.id && it.nextDueOdometerKm == 14800.0 + oilReminder.intervalKm }
            }
        }
        assertEquals(
            14800.0 + oilReminder.intervalKm,
            updatedItems.first { it.id == oilReminder.id }.nextDueOdometerKm,
            0.01
        )

        // 4. Cross-user isolation
        signInTestUser("garage_bob_${UUID.randomUUID()}@motopack.test")
        val bobGarage = GarageRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobGarage.observeBikes(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's garage bikes")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
        }

        // 5. Unauthenticated isolation
        auth.signOut()
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobGarage.observeServiceLogs(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated caller reads service logs")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
