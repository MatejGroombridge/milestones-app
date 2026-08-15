package dev.matejgroombridge.milestones.data.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Covers the two things that would be expensive to get wrong: reading v0.1.0
 * data written by the released app, and the kind × cadence matrix that decides
 * what a milestone's number actually is.
 *
 * The model layer is deliberately free of Android dependencies, so all of this
 * runs on the JVM.
 */
class MilestoneModelTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private fun day(year: Int, month: Int, dayOfMonth: Int): Long =
        LocalDate.of(year, month, dayOfMonth).toEpochDay()

    /**
     * Exactly what v0.1.0 wrote: a `records` array, a scalar `target`, and no
     * `kind`, `cadence` or `targets` keys at all.
     */
    private val v010Json = """
        [
          {
            "id": "m1",
            "name": "Furthest run",
            "description": "",
            "iconKey": "run",
            "colorKey": "mint",
            "unit": { "type": "number", "suffix": "km", "decimals": 2 },
            "direction": "HigherIsBetter",
            "target": 30.0,
            "archived": false,
            "createdAtEpochDay": 20000,
            "records": [
              { "id": "r1", "value": 12.5, "epochDay": 20000, "note": "" },
              { "id": "r2", "value": 21.4, "epochDay": 20100, "note": "half" }
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun `v0_1_0 json still parses`() {
        val parsed = json.decodeFromString(ListSerializer(Milestone.serializer()), v010Json)
        val milestone = parsed.single()

        assertEquals("Furthest run", milestone.name)
        // `records` is the wire name the first release used; the property is
        // now `entries`, held together by @SerialName.
        assertEquals(2, milestone.entries.size)
        assertEquals(21.4, milestone.entries[1].value, 0.0001)
        assertEquals(MilestoneUnit.Numeric("km", 2), milestone.unit)
    }

    @Test
    fun `v0_1_0 defaults to a lifetime record`() {
        val milestone = json.decodeFromString(ListSerializer(Milestone.serializer()), v010Json)
            .single()

        assertEquals(MilestoneKind.Record, milestone.kind)
        assertEquals(MilestoneCadence.Lifetime, milestone.cadence)
    }

    @Test
    fun `legacy target is folded into the targets list`() {
        val milestone = json.decodeFromString(ListSerializer(Milestone.serializer()), v010Json)
            .single()
            .migrated()

        assertNull("legacy field should be cleared once folded", milestone.legacyTarget)
        val target = milestone.activeTargetIn(null)
        assertNotNull(target)
        assertEquals(30.0, target!!.value, 0.0001)
        assertNull("a lifetime target has no period", target.periodKey)
        assertFalse("30km is not reached from 21.4km", target.isReached)
    }

    @Test
    fun `migration is idempotent`() {
        val once = json.decodeFromString(ListSerializer(Milestone.serializer()), v010Json)
            .single().migrated()
        val twice = once.migrated()

        assertEquals(1, twice.targets.size)
        assertEquals(once, twice)
    }

    @Test
    fun `migrated milestone survives a round trip`() {
        val original = json.decodeFromString(ListSerializer(Milestone.serializer()), v010Json)
            .single().migrated()

        val encoded = json.encodeToString(ListSerializer(Milestone.serializer()), listOf(original))
        val decoded = json.decodeFromString(ListSerializer(Milestone.serializer()), encoded)
            .single().migrated()

        assertEquals(original, decoded)
    }

    // --- kind x cadence ------------------------------------------------------

    private fun milestone(
        kind: MilestoneKind,
        cadence: MilestoneCadence,
        direction: MilestoneDirection = MilestoneDirection.HigherIsBetter,
        entries: List<MilestoneEntry> = emptyList(),
    ) = Milestone(
        id = "m",
        name = "test",
        kind = kind,
        cadence = cadence,
        direction = direction,
        createdAtEpochDay = day(2025, 1, 1),
        entries = entries,
    )

    @Test
    fun `lifetime record takes the best of every entry`() {
        val stats = milestone(
            kind = MilestoneKind.Record,
            cadence = MilestoneCadence.Lifetime,
            entries = listOf(
                MilestoneEntry("a", 10.0, day(2025, 6, 1)),
                MilestoneEntry("b", 25.0, day(2026, 2, 1)),
            ),
        ).statsOn(day(2026, 8, 1))

        assertEquals(25.0, stats.value!!, 0.0001)
        assertEquals(2, stats.periodEntryCount)
    }

    @Test
    fun `yearly record only sees the current year`() {
        val stats = milestone(
            kind = MilestoneKind.Record,
            cadence = MilestoneCadence.Yearly,
            entries = listOf(
                MilestoneEntry("a", 100.0, day(2025, 6, 1)),
                MilestoneEntry("b", 60.0, day(2026, 2, 1)),
            ),
        ).statsOn(day(2026, 8, 1))

        assertEquals("last year's 100 must not leak in", 60.0, stats.value!!, 0.0001)
        assertEquals(1, stats.periodEntryCount)
        assertEquals(100.0, stats.allTimeValue!!, 0.0001)
    }

    @Test
    fun `a yearly record accepts a value that beats this year but not all time`() {
        val m = milestone(
            kind = MilestoneKind.Record,
            cadence = MilestoneCadence.Yearly,
            entries = listOf(
                MilestoneEntry("a", 100.0, day(2025, 6, 1)),
                MilestoneEntry("b", 60.0, day(2026, 2, 1)),
            ),
        )
        // The whole point of a yearly scoreboard: January resets what you're
        // chasing, so 70 is a new record for 2026 even though 2025 hit 100.
        assertTrue(m.accepts(70.0, day(2026, 8, 1)))
        assertFalse(m.accepts(55.0, day(2026, 8, 1)))
    }

    @Test
    fun `lower is better rejects a higher value`() {
        val m = milestone(
            kind = MilestoneKind.Record,
            cadence = MilestoneCadence.Lifetime,
            direction = MilestoneDirection.LowerIsBetter,
            entries = listOf(MilestoneEntry("a", 1470.0, day(2026, 1, 1))),
        )
        assertTrue(m.accepts(1400.0, day(2026, 8, 1)))
        assertFalse(m.accepts(1500.0, day(2026, 8, 1)))
        assertFalse("matching the record is not beating it", m.accepts(1470.0, day(2026, 8, 1)))
    }

    @Test
    fun `tally sums its entries and accepts anything`() {
        val m = milestone(
            kind = MilestoneKind.Tally,
            cadence = MilestoneCadence.Yearly,
            entries = listOf(
                MilestoneEntry("a", 1.0, day(2026, 1, 5)),
                MilestoneEntry("b", 1.0, day(2026, 3, 5)),
                MilestoneEntry("c", 1.0, day(2025, 3, 5)),
            ),
        )
        val stats = m.statsOn(day(2026, 8, 1))

        assertEquals(2.0, stats.value!!, 0.0001)
        assertEquals(3.0, stats.allTimeValue!!, 0.0001)
        assertTrue(m.accepts(1.0, day(2026, 8, 1)))
    }

    @Test
    fun `an empty tally is zero rather than absent`() {
        val stats = milestone(MilestoneKind.Tally, MilestoneCadence.Lifetime).statsOn(day(2026, 8, 1))
        assertEquals(0.0, stats.value!!, 0.0001)
    }

    // --- targets -------------------------------------------------------------

    @Test
    fun `reaching a target stamps it and frees the next one`() {
        val today = day(2026, 8, 1)
        val m = milestone(
            kind = MilestoneKind.Tally,
            cadence = MilestoneCadence.Yearly,
            entries = listOf(MilestoneEntry("a", 30.0, day(2026, 5, 1))),
        ).withTarget(30.0, today).settleTargets(today)

        val periodKey = m.currentPeriodKey(today)
        assertNull("nothing left to chase", m.activeTargetIn(periodKey))
        assertEquals(today, m.lastReachedTargetIn(periodKey)!!.reachedOnEpochDay)
        assertTrue(m.statsOn(today).awaitingNewTarget)
    }

    @Test
    fun `deleting the entry that cleared a target reopens it`() {
        val today = day(2026, 8, 1)
        val cleared = milestone(
            kind = MilestoneKind.Tally,
            cadence = MilestoneCadence.Yearly,
            entries = listOf(MilestoneEntry("a", 30.0, day(2026, 5, 1))),
        ).withTarget(30.0, today).settleTargets(today)

        val reopened = cleared.withoutEntry("a").settleTargets(today)
        val target = reopened.activeTargetIn(reopened.currentPeriodKey(today))

        assertNotNull("the goal is live again", target)
        assertFalse(target!!.isReached)
    }

    @Test
    fun `a target cleared in a past year is not reopened by the rollover`() {
        val lastYear = day(2025, 6, 1)
        val thisYear = day(2026, 8, 1)
        val m = milestone(
            kind = MilestoneKind.Tally,
            cadence = MilestoneCadence.Yearly,
            entries = listOf(MilestoneEntry("a", 30.0, lastYear)),
        ).withTarget(30.0, lastYear).settleTargets(lastYear)
            // A year later the current period is empty, but history shouldn't
            // rewrite itself.
            .settleTargets(thisYear)

        assertTrue(m.targetsIn(2025).single().isReached)
        assertNull("this year starts with no target", m.activeTargetIn(2026))
    }

    @Test
    fun `progress toward a lower-is-better target measures ground covered`() {
        val today = day(2026, 8, 1)
        val stats = milestone(
            kind = MilestoneKind.Record,
            cadence = MilestoneCadence.Lifetime,
            direction = MilestoneDirection.LowerIsBetter,
            entries = listOf(
                MilestoneEntry("a", 1800.0, day(2026, 1, 1)),
                MilestoneEntry("b", 1500.0, day(2026, 4, 1)),
            ),
        ).withTarget(1440.0, today).statsOn(today)

        // 30:00 -> 25:00 against a 24:00 goal is 300 of 360 seconds covered.
        assertEquals(0.833f, stats.targetProgress!!, 0.01f)
    }

    @Test
    fun `setting a new target replaces the active one rather than stacking`() {
        val today = day(2026, 8, 1)
        val m = milestone(MilestoneKind.Tally, MilestoneCadence.Lifetime)
            .withTarget(10.0, today)
            .withTarget(20.0, today)

        assertEquals(1, m.targets.size)
        assertEquals(20.0, m.activeTargetIn(null)!!.value, 0.0001)
    }
}
