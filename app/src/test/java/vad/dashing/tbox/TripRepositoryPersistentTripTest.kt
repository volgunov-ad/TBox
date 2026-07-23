package vad.dashing.tbox

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.trip.TripRecord
import vad.dashing.tbox.trip.TripRepository
import vad.dashing.tbox.trip.TripRules

class TripRepositoryPersistentTripTest {

    @After
    fun reset() {
        TripRepository.resetForUnitTests()
    }

    @Test
    fun ensurePersistentTrip_createsLiveDaily() = runBlocking {
        TripRepository.ensurePersistentTrip(defaultName = "Daily", nowMs = 1_000L)
        val live = TripRepository.persistentTrip()
        assertNotNull(live)
        assertTrue(live!!.isPersistent)
        assertTrue(live.originPersistent)
        assertNull(live.endTimeEpochMs)
        assertEquals("Daily", live.name)
        assertNull(TripRepository.activeTrip.first())
    }

    @Test
    fun livePersistent_isNotActiveTrip() = runBlocking {
        TripRepository.ensurePersistentTrip(defaultName = "Daily", nowMs = 1_000L)
        TripRepository.appendTrip(
            TripRecord(id = "cur", startTimeEpochMs = 2_000L, endTimeEpochMs = null)
        )
        assertEquals("cur", TripRepository.activeTrip.first()?.id)
        assertTrue(TripRepository.persistentTrip()!!.isPersistent)
    }

    @Test
    fun startTrip_doesNotCloseLivePersistent() = runBlocking {
        TripRepository.ensurePersistentTrip(defaultName = "Daily", nowMs = 1_000L)
        val liveId = TripRepository.persistentTrip()!!.id
        TripRepository.startTrip(
            TripRecord(id = "cur", startTimeEpochMs = 2_000L, endTimeEpochMs = null)
        )
        val live = TripRepository.trips.first().first { it.id == liveId }
        assertTrue(live.isPersistent)
        assertNull(live.endTimeEpochMs)
        assertEquals("cur", TripRepository.activeTrip.first()?.id)
    }

    @Test
    fun removeTrip_cannotDeleteLivePersistent() = runBlocking {
        TripRepository.ensurePersistentTrip(defaultName = "Daily", nowMs = 1_000L)
        val id = TripRepository.persistentTrip()!!.id
        TripRepository.removeTrip(id)
        assertEquals(id, TripRepository.persistentTrip()?.id)
    }

    @Test
    fun resetPersistentTrip_archivesAndCreatesNew() = runBlocking {
        TripRepository.ensurePersistentTrip(defaultName = "Daily", nowMs = 1_000L)
        TripRepository.updatePersistentTrip {
            it.copy(name = "My daily", distanceKm = 12f, parkingTimeMs = 5_000L)
        }
        val oldId = TripRepository.persistentTrip()!!.id
        TripRepository.resetPersistentTrip(
            nowMs = 2_000L,
            defaultName = "Daily",
            odometerStartKm = 100u,
        )
        val archived = TripRepository.trips.first().first { it.id == oldId }
        assertFalse(archived.isPersistent)
        assertTrue(archived.originPersistent)
        assertEquals(2_000L, archived.endTimeEpochMs)
        assertEquals(12f, archived.distanceKm)
        val live = TripRepository.persistentTrip()!!
        assertNotEquals(oldId, live.id)
        assertTrue(live.isPersistent)
        assertEquals("My daily", live.name)
        assertEquals(0f, live.distanceKm)
        assertEquals(100u, live.odometerStartKm)
    }

    @Test
    fun resetArchive_neverResumesAsCurrent() = runBlocking {
        TripRepository.ensurePersistentTrip(defaultName = "Daily", nowMs = 1_000L)
        TripRepository.updatePersistentTrip { it.copy(distanceKm = 3f) }
        val oldId = TripRepository.persistentTrip()!!.id
        val nowWall = System.currentTimeMillis()
        TripRepository.resetPersistentTrip(nowMs = nowWall, defaultName = "Daily")
        val splitMs = 5 * 60_000L
        val candidate = TripRules.findResumeCandidate(
            TripRepository.trips.first(),
            nowMs = nowWall + 1_000L,
            splitWindowMs = splitMs,
        )
        assertTrue(candidate == null || candidate.id != oldId)
        assertFalse(TripRepository.tryResumeLastTripAfterServiceStart(splitMs).resumed)
        assertNull(TripRepository.activeTrip.first())
        assertTrue(TripRepository.trips.first().first { it.id == oldId }.originPersistent)
    }

    @Test
    fun latestFinishedTrip_skipsDailyArchive() {
        val archive = TripRecord(
            id = "arch",
            startTimeEpochMs = 10L,
            endTimeEpochMs = 200L,
            originPersistent = true,
        )
        val normal = TripRecord(
            id = "n",
            startTimeEpochMs = 20L,
            endTimeEpochMs = 100L,
        )
        assertEquals("n", TripRepository.latestFinishedTrip(listOf(archive, normal))?.id)
    }

    @Test
    fun applyListLimit_keepsLivePersistentBeyondMax() = runBlocking {
        TripRepository.ensurePersistentTrip(defaultName = "Daily", nowMs = 1L)
        val liveId = TripRepository.persistentTrip()!!.id
        val max = TripRepository.MAX_TRIPS
        for (i in 1..max) {
            TripRepository.appendTrip(
                TripRecord(
                    id = "t$i",
                    startTimeEpochMs = i.toLong() + 10L,
                    endTimeEpochMs = i.toLong() + 11L,
                )
            )
        }
        val trips = TripRepository.trips.first()
        assertTrue(trips.any { it.id == liveId && it.isPersistent })
        assertEquals(max, trips.count { !it.isPersistent })
    }

    @Test
    fun findResumeCandidate_ignoresLivePersistentEvenIfOpen() {
        val daily = TripRecord(
            id = "d",
            startTimeEpochMs = 500L,
            endTimeEpochMs = null,
            isPersistent = true,
            originPersistent = true,
        )
        val ended = TripRecord(
            id = "e",
            startTimeEpochMs = 1L,
            endTimeEpochMs = 100L,
        )
        val splitMs = 5 * 60_000L
        assertNull(
            TripRules.findResumeCandidate(
                listOf(daily, ended),
                nowMs = 100L + splitMs + 1,
                splitWindowMs = splitMs,
            )
        )
        assertNull(
            TripRules.findResumeCandidate(
                listOf(daily),
                nowMs = 999L,
                splitWindowMs = splitMs,
            )
        )
    }
}
