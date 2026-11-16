package com.example.data.model

import com.example.data.remote.MotoRouteAndWeatherRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

data class BikerLocation(
    val id: String,
    val name: String,
    val category: String, // "BIKER_BREW", "SCENIC_HUB", "BIKE_NIGHT_VENUE"
    val cityRegion: String,
    val lat: Double,
    val lng: Double,
    val description: String,
    val brewSpecialty: String,
    val amenities: List<String>,
    val isPassportStop: Boolean = false,
    val passportStampCode: String = "",
    val passportStopNumber: Int = 0,
    val eventScheduleLabel: String = ""
)

@IgnoreExtraProperties
data class BikerEvent(
    @DocumentId val id: String = "",
    val packId: String = "",
    val creatorId: String = "",
    val creatorName: String = "",
    val title: String = "",
    val description: String = "",
    val eventType: String = "BIKER_BREW", // "BIKER_BREW", "BIKE_NIGHT", "RIDE_OUT", "SCENIC_MEET"
    val locationId: String = "",
    val locationName: String = "",
    val brewSpecialty: String = "",
    val scheduledLabel: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val attendeeUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @get:PropertyName("isPassportEvent")
    @field:PropertyName("isPassportEvent")
    val isPassportEvent: Boolean = false,
    val status: String = "upcoming", // "upcoming", "completed"
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PassportStamp(
    @DocumentId val id: String = "",
    val userId: String = "",
    val locationId: String = "",
    val locationName: String = "",
    val stampCode: String = "",
    val brewNote: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val verifiedGps: Boolean = false,
    @ServerTimestamp val stampedAt: Timestamp? = null
)

data class PassportRankInfo(
    val title: String,
    val badgeTag: String,
    val nextGoalCount: Int,
    val rewardPerk: String
)

object BikerBrewCatalog {
    const val PASSPORT_SEASON_TITLE = "2026 Biker & Brew Passport"
    const val CHECK_IN_RADIUS_KM = 2.5

    val curatedLocations: List<BikerLocation> = listOf(
        BikerLocation(
            id = "brew_peak_roasters",
            name = "Peak Roasters Moto Café",
            category = "BIKER_BREW",
            cityRegion = "Sheffield Trailhead",
            lat = 53.3832,
            lng = -1.4728,
            description = "Dedicated flat gravel-free bike bays, helmet cubbies, and single-origin espresso bar.",
            brewSpecialty = "Double Piston Cortado & Warm Brioche",
            amenities = listOf("Moto Parking", "Helmet Lockers", "Tire Air", "Espresso Bar"),
            isPassportStop = true,
            passportStampCode = "BB-01",
            passportStopNumber = 1,
            eventScheduleLabel = "Sat & Sun • 08:00 AM"
        ),
        BikerLocation(
            id = "brew_ladybower_outpost",
            name = "Ladybower Reservoir Espresso Outpost",
            category = "BIKER_BREW",
            cityRegion = "Derwent Valley",
            lat = 53.3775,
            lng = -1.7014,
            description = "Waterside coffee trailer and Sunday morning convoy staging pullout.",
            brewSpecialty = "Nitro Cold Brew & Maple Waffle",
            amenities = listOf("Convoy Staging", "Lake View", "USB Charge", "Outdoor Bench"),
            isPassportStop = true,
            passportStampCode = "BB-02",
            passportStopNumber = 2,
            eventScheduleLabel = "Sundays • 09:30 AM"
        ),
        BikerLocation(
            id = "brew_castleton_kettle",
            name = "Copper Kettle Ridge Hub",
            category = "BIKER_BREW",
            cityRegion = "Castleton & Winnats Pass",
            lat = 53.3426,
            lng = -1.7783,
            description = "Classic limestone pass coffee house sitting right below Winnats Pass hairpins.",
            brewSpecialty = "Dark Roast Flat White & Bacon Roll",
            amenities = listOf("Paved Bike Bays", "Chain Lube Corner", "Restrooms", "Patio"),
            isPassportStop = true,
            passportStampCode = "BB-03",
            passportStopNumber = 3,
            eventScheduleLabel = "Wed Bike Night • 06:00 PM"
        ),
        BikerLocation(
            id = "brew_snake_summit",
            name = "Snake Summit Brew & Lookout",
            category = "BIKER_BREW",
            cityRegion = "A57 Snake Pass",
            lat = 53.4339,
            lng = -1.8732,
            description = "High-elevation moorland brew van and sweeping ridge check-in overlook.",
            brewSpecialty = "Salted Caramel Mocha & Flapjack",
            amenities = listOf("Scenic Overlook", "Wind Shelter", "SOS Cell Booster"),
            isPassportStop = true,
            passportStampCode = "BB-04",
            passportStopNumber = 4,
            eventScheduleLabel = "Fri Twilight Meet • 05:30 PM"
        ),
        BikerLocation(
            id = "brew_matlock_apex",
            name = "Apex Espresso & Wrench",
            category = "BIKE_NIGHT_VENUE",
            cityRegion = "Matlock Bath Promenade",
            lat = 53.1286,
            lng = -1.5579,
            description = "Famous riverside motorcycle promenade with late-evening espresso & custom bike showcase.",
            brewSpecialty = "Octane Honey Latte & Smoked Brisket Bun",
            amenities = listOf("Promenade Parking", "Live Music", "Tool Loan", "Helmet Check"),
            isPassportStop = true,
            passportStampCode = "BB-05",
            passportStopNumber = 5,
            eventScheduleLabel = "Thurs Bike Night • 06:30 PM"
        ),
        BikerLocation(
            id = "brew_buxton_torque",
            name = "Torque & Tonic Roastery",
            category = "SCENIC_HUB",
            cityRegion = "Buxton / Cat & Fiddle",
            lat = 53.2584,
            lng = -1.9125,
            description = "Artisan roastery at the gateway to the Cat & Fiddle twisties with heated gear drying hooks.",
            brewSpecialty = "Ethiopian Pour-Over & Almond Croissant",
            amenities = listOf("Gear Hooks", "Fast Wi-Fi", "EV/USB Dock", "24h Fuel Nearby"),
            isPassportStop = true,
            passportStampCode = "BB-06",
            passportStopNumber = 6,
            eventScheduleLabel = "Saturdays • 10:00 AM"
        )
    )

    val passportStops: List<BikerLocation> = curatedLocations.filter { it.isPassportStop }

    val curatedCommunityEvents: List<BikerEvent> = listOf(
        BikerEvent(
            id = "evt_biker_brew_kickoff",
            packId = "curated",
            creatorId = "motopack_curated",
            creatorName = "Biker & Brew Guild",
            title = "Biker & Brew Passport: 6-Stop Tour",
            description = "Collect all 6 café stamps across the Peak & Valley loops to earn Grand Brew Road Captain status.",
            eventType = "BIKER_BREW",
            locationId = "brew_peak_roasters",
            locationName = "Peak Roasters Moto Café",
            brewSpecialty = "Free Double Espresso upgrade with Passport Stamp #1",
            scheduledLabel = "This Weekend • 08:00 AM – 04:00 PM",
            lat = 53.3832,
            lng = -1.4728,
            attendeeUids = listOf("seed_1", "seed_2", "seed_3"),
            isPassportEvent = true,
            status = "upcoming"
        ),
        BikerEvent(
            id = "evt_dawn_canyon_roast",
            packId = "curated",
            creatorId = "motopack_curated",
            creatorName = "Dawn Patrol Riders",
            title = "Dawn Espresso & Ladybower Loop",
            description = "Sunrise pack roll-out to Ladybower Outpost for nitro cold brew before traffic builds.",
            eventType = "RIDE_OUT",
            locationId = "brew_ladybower_outpost",
            locationName = "Ladybower Reservoir Espresso Outpost",
            brewSpecialty = "Nitro Cold Brew & Maple Waffle",
            scheduledLabel = "Sunday • 07:30 AM Roll-Out",
            lat = 53.3775,
            lng = -1.7014,
            attendeeUids = listOf("seed_1", "seed_4"),
            isPassportEvent = true,
            status = "upcoming"
        ),
        BikerEvent(
            id = "evt_matlock_bike_night",
            packId = "curated",
            creatorId = "motopack_curated",
            creatorName = "Midlands Moto Collective",
            title = "Throttle & Roast Bike Night",
            description = "Riverfront custom & touring meetup with late espresso bar, live acoustic set & safety check.",
            eventType = "BIKE_NIGHT",
            locationId = "brew_matlock_apex",
            locationName = "Apex Espresso & Wrench",
            brewSpecialty = "Octane Honey Latte",
            scheduledLabel = "Thursday • 06:30 PM",
            lat = 53.1286,
            lng = -1.5579,
            attendeeUids = listOf("seed_2", "seed_5", "seed_6", "seed_7"),
            isPassportEvent = true,
            status = "upcoming"
        ),
        BikerEvent(
            id = "evt_winnats_scenic_meet",
            packId = "curated",
            creatorId = "motopack_curated",
            creatorName = "Ridge Runners",
            title = "Winnats Pass Twisties & Pour-Over",
            description = "Scenic photo stop & mid-ride roast break for packs running the Castleton ridge circuit.",
            eventType = "SCENIC_MEET",
            locationId = "brew_castleton_kettle",
            locationName = "Copper Kettle Ridge Hub",
            brewSpecialty = "Dark Roast Flat White",
            scheduledLabel = "Wednesday • 05:45 PM",
            lat = 53.3426,
            lng = -1.7783,
            attendeeUids = listOf("seed_3"),
            isPassportEvent = true,
            status = "upcoming"
        )
    )

    fun computePassportRank(stampCount: Int): PassportRankInfo = when {
        stampCount >= passportStops.size -> PassportRankInfo(
            title = "Grand Brew Road Captain",
            badgeTag = "PASSPORT FINISHER",
            nextGoalCount = passportStops.size,
            rewardPerk = "All 6 Biker & Brew stops unlocked!"
        )
        stampCount >= 3 -> PassportRankInfo(
            title = "Roast & Throttle",
            badgeTag = "TIER 3 • $stampCount/${passportStops.size}",
            nextGoalCount = passportStops.size,
            rewardPerk = "Collect ${passportStops.size - stampCount} more stamps for Finisher Crest"
        )
        stampCount >= 1 -> PassportRankInfo(
            title = "Caffeine Cruiser",
            badgeTag = "TIER 2 • $stampCount/${passportStops.size}",
            nextGoalCount = 3,
            rewardPerk = "Collect ${3 - stampCount} more stamp(s) for Roast & Throttle"
        )
        else -> PassportRankInfo(
            title = "Espresso Prospect",
            badgeTag = "TIER 1 • 0/${passportStops.size}",
            nextGoalCount = 1,
            rewardPerk = "Stamp your 1st Biker & Brew stop to begin the Passport"
        )
    }

    fun distanceKmTo(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): Double =
        MotoRouteAndWeatherRepository.haversineKm(fromLat, fromLng, toLat, toLng)
}
