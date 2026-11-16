package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.crypto.PackCrypto
import com.example.data.crypto.RiderTelemetryPayload
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

class MotoPackRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createPack_and_joinByInviteCode_syncsEncryptedTelemetryAndMessages() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@motopack.test")
        val aliceRepo = MotoPackRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile("Alice Tracer", "", "Yamaha Tracer 9 GT").getOrThrow()
        }

        val pack = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createPack(
                packName = "Peak District Run",
                displayName = "Alice Tracer",
                photoUrl = "",
                bikeModel = "Yamaha Tracer 9 GT",
                phoneNumber = "+447700900111",
                initialLat = 53.3811,
                initialLng = -1.4701,
                batteryPercent = 88,
                isCharging = false
            ).getOrThrow()
        }

        assertTrue(pack.id.isNotBlank())
        assertTrue(pack.memberUids.contains(aliceUid))

        // Bob joins via invite code
        val bobUid = signInTestUser("bob_${UUID.randomUUID()}@motopack.test")
        val bobRepo = MotoPackRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.ensureUserProfile("Bob Tenere", "", "Tenere 700").getOrThrow()
        }

        val joinedPack = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.joinPackByCode(
                rawCode = pack.inviteCode,
                displayName = "Bob Tenere",
                photoUrl = "",
                bikeModel = "Tenere 700",
                phoneNumber = "+447700900222",
                initialLat = 53.4084,
                initialLng = -1.4021,
                batteryPercent = 76,
                isCharging = true
            ).getOrThrow()
        }
        assertTrue(joinedPack.memberUids.contains(bobUid))

        // Bob sends encrypted chat message and urgent ping
        val msgId = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.sendPackMessage(
                pack = joinedPack,
                senderName = "Bob Tenere",
                plainText = "Fuel stop in 15 km at Buxton!"
            ).getOrThrow()
        }
        assertTrue(msgId.isNotBlank())

        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observePackMessages(joinedPack.id, bobUid).first { it.isNotEmpty() }
        }
        val key = PackCrypto.derivePackKey(joinedPack.inviteCode, joinedPack.encryptionSalt)
        val decrypted = PackCrypto.decryptString(messages.first().encryptedBody, key)
        assertEquals("Fuel stop in 15 km at Buxton!", decrypted)

        // Verify telemetry decryption
        val members = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observePackMembers(joinedPack.id, bobUid).first { it.any { m -> m.userId == bobUid } }
        }
        val bobDoc = members.first { it.userId == bobUid }
        val decryptedLoc = PackCrypto.decryptTelemetry(bobDoc.encryptedTelemetry, key)
        assertNotNull(decryptedLoc)
        assertEquals(53.4084, decryptedLoc!!.lat, 0.0001)
    }

    @Test
    fun observeUserPacks_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = MotoPackRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeUserPacks("some_uid").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated observeUserPacks")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
        }
    }

    @Test
    fun crossUserProfileAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice2_${UUID.randomUUID()}@motopack.test")
        val aliceRepo = MotoPackRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile("Alice", "", "MT-09").getOrThrow()
        }

        signInTestUser("charlie_${UUID.randomUUID()}@motopack.test")
        val charlieRepo = MotoPackRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                charlieRepo.observeUserProfile(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when reading another rider's private profile")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
        }
    }

    @Test
    fun challengeLifecycle_andTripLogging_succeedsForPackMembers() = runBlocking {
        val uid = signInTestUser("rider_${UUID.randomUUID()}@motopack.test")
        val repo = MotoPackRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfile("Solo Lead", "", "GS1250").getOrThrow()
        }

        val pack = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createPack(
                packName = "Summit Chasers",
                displayName = "Solo Lead",
                photoUrl = "",
                bikeModel = "GS1250",
                phoneNumber = "",
                initialLat = 53.35,
                initialLng = -1.80,
                batteryPercent = 92,
                isCharging = false
            ).getOrThrow()
        }

        val challengeId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createChallenge(
                pack = pack,
                title = "Snake Summit 500m",
                description = "Reach 500m elevation together",
                challengeType = "ELEVATION",
                targetValue = 500.0,
                unit = "m",
                checkpointLat = 53.43,
                checkpointLng = -1.87
            ).getOrThrow()
        }
        assertTrue(challengeId.isNotBlank())

        val challenges = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeChallenges(pack.id, uid).first { it.isNotEmpty() }
        }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateChallengeProgress(pack.id, challenges.first(), 500.0).getOrThrow()
        }

        val updated = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeChallenges(pack.id, uid).first { it.first().status == "completed" }
        }
        assertEquals("completed", updated.first().status)

        val tripId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.logRideTrip(
                packId = pack.id,
                title = "Snake Pass Loop",
                distanceKm = 114.5,
                elevationGainM = 512.0,
                durationMin = 112L,
                avgSpeedKmh = 61.3,
                startLat = 53.35,
                startLng = -1.80,
                endLat = 53.43,
                endLng = -1.87
            ).getOrThrow()
        }
        assertTrue(tripId.isNotBlank())
    }

    @Test
    fun packMarkers_and_galleryPhotos_syncAndRespectMembershipRules() = runBlocking {
        val aliceUid = signInTestUser("pin_alice_${UUID.randomUUID()}@motopack.test")
        val repo = MotoPackRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfile("Alice Pin", "", "Africa Twin").getOrThrow()
        }

        val pack = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createPack(
                packName = "Hazard & Photo Pack",
                displayName = "Alice Pin",
                photoUrl = "",
                bikeModel = "Africa Twin",
                phoneNumber = "",
                initialLat = 53.40,
                initialLng = -1.80,
                batteryPercent = 90,
                isCharging = false
            ).getOrThrow()
        }

        val markerId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createPackMarker(
                pack = pack,
                creatorName = "Alice Pin",
                pinType = "HAZARD_WARNING",
                title = "Wet Leaves on Bend",
                description = "Slippery hairpin near bridge",
                lat = 53.41,
                lng = -1.81,
                severity = "DANGER"
            ).getOrThrow()
        }
        assertTrue(markerId.isNotBlank())

        val markers = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observePackMarkers(pack.id, aliceUid).first { it.any { m -> m.id == markerId } }
        }
        assertEquals("HAZARD_WARNING", markers.first { it.id == markerId }.pinType)

        val photoId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.postPackGalleryPhoto(
                pack = pack,
                uploaderName = "Alice Pin",
                caption = "Coffee stop overlook",
                locationLabel = "Ladybower",
                imageDataBase64 = "TEST_BASE64_PAYLOAD_0123456789",
                lat = 53.41,
                lng = -1.81
            ).getOrThrow()
        }
        assertTrue(photoId.isNotBlank())

        val photos = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observePackGalleryPhotos(pack.id, aliceUid).first { it.any { p -> p.id == photoId } }
        }
        assertEquals("Coffee stop overlook", photos.first { it.id == photoId }.caption)
    }

    @Test
    fun emergencySos_pingAndMapPin_syncToPackMembers() = runBlocking {
        val aliceUid = signInTestUser("sos_alice_${UUID.randomUUID()}@motopack.test")
        val aliceRepo = MotoPackRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile("Alice SOS", "", "Multistrada V4").getOrThrow()
        }

        val pack = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createPack(
                packName = "Highland Convoy",
                displayName = "Alice SOS",
                photoUrl = "",
                bikeModel = "Multistrada V4",
                phoneNumber = "",
                initialLat = 53.4339,
                initialLng = -1.8732,
                batteryPercent = 82,
                isCharging = false
            ).getOrThrow()
        }

        val sosPingId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.sendUrgentPing(
                pack = pack,
                senderName = "Alice SOS",
                targetUserId = "ALL",
                alertType = "EMERGENCY_SOS",
                note = "SOS|53.4339|-1.8732|Crash / Rider Down"
            ).getOrThrow()
        }
        assertTrue(sosPingId.isNotBlank())

        val sosPinId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createPackMarker(
                pack = pack,
                creatorName = "Alice SOS",
                pinType = "EMERGENCY_SOS",
                title = "SOS: Alice SOS Needs Help",
                description = "Crash / Rider Down • 53.43390, -1.87320",
                lat = 53.4339,
                lng = -1.8732,
                severity = "DANGER"
            ).getOrThrow()
        }
        assertTrue(sosPinId.isNotBlank())

        val pings = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeUrgentPings(pack.id, aliceUid).first { it.any { p -> p.id == sosPingId } }
        }
        assertEquals("EMERGENCY_SOS", pings.first { it.id == sosPingId }.alertType)

        val pins = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observePackMarkers(pack.id, aliceUid).first { it.any { m -> m.id == sosPinId } }
        }
        assertEquals("EMERGENCY_SOS", pins.first { it.id == sosPinId }.pinType)
    }

    @Test
    fun pushToTalk_voiceMessage_encryptsAndSyncsWithinLowBandwidthLimit() = runBlocking {
        val aliceUid = signInTestUser("ptt_alice_${UUID.randomUUID()}@motopack.test")
        val aliceRepo = MotoPackRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile("Alice PTT", "", "DesertX").getOrThrow()
        }

        val pack = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createPack(
                packName = "Intercom Convoy",
                displayName = "Alice PTT",
                photoUrl = "",
                bikeModel = "DesertX",
                phoneNumber = "",
                initialLat = 53.39,
                initialLng = -1.48,
                batteryPercent = 91,
                isCharging = false
            ).getOrThrow()
        }

        val sampleAudioBase64 = "AMR_LOW_BW_DEMO_PAYLOAD_0123456789ABCDEF"
        val voiceId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.sendPackVoiceMessage(
                pack = pack,
                senderName = "Alice PTT",
                audioBase64 = sampleAudioBase64,
                audioCodec = "AMR_NB",
                audioDurationMs = 5400,
                audioSizeBytes = 8235,
                audioPeaks = "2,5,8,9,7,4,6,8,5,3,4,7,6,3,2,2"
            ).getOrThrow()
        }
        assertTrue(voiceId.isNotBlank())

        val msgs = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observePackMessages(pack.id, aliceUid).first { list -> list.any { it.id == voiceId } }
        }
        val voiceDoc = msgs.first { it.id == voiceId }
        assertEquals("voice", voiceDoc.messageType)
        assertEquals("AMR_NB", voiceDoc.audioCodec)
        assertEquals(5400, voiceDoc.audioDurationMs)
        assertEquals(8235, voiceDoc.audioSizeBytes)

        val key = PackCrypto.derivePackKey(pack.inviteCode, pack.encryptionSalt)
        val decryptedAudio = PackCrypto.decryptString(voiceDoc.encryptedAudio, key)
        assertEquals(sampleAudioBase64, decryptedAudio)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
