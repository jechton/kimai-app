package app.tick.kimai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

private const val H = 3600L

/** A 40h week worked Monday to Friday, 8h a day. */
private val week = WeekTotals(doneSeconds = 0, targetSeconds = 40 * H, paceWeights = listOf(1L, 1, 1, 1, 1, 0, 0))
private val monday = LocalDate.of(2024, 1, 1)
private val wednesday = monday.plusDays(2)

/** Mirrors the app: weekLeft counts today's work, pacePlan gets the total before today began. */
private fun statusOn(
    doneBeforeToday: Long,
    doneToday: Long,
): PaceStatus {
    val plan = week.pacePlan(monday, wednesday, doneBeforeToday, doneToday)!!
    return plan.status(week.targetSeconds - doneBeforeToday - doneToday, doneToday)
}

class PaceTest {
    @Test
    fun `on schedule with today still to do`() {
        assertEquals(PaceStatus(PaceMessage.MoreToday(8 * H), PaceState.PENDING), statusOn(16 * H, 0))
    }

    @Test
    fun `today's goal met is on pace`() {
        assertEquals(PaceStatus(PaceMessage.OnPace, PaceState.MET), statusOn(16 * H, 8 * H))
    }

    @Test
    fun `more than today's goal is ahead`() {
        assertEquals(PaceStatus(PaceMessage.Ahead(2 * H), PaceState.MET), statusOn(16 * H, 10 * H))
    }

    @Test
    fun `a missed day is behind and missed`() {
        assertEquals(PaceStatus(PaceMessage.Behind(16 * H), PaceState.MISSED), statusOn(8 * H, 0))
    }

    @Test
    fun `finishing the catch-up plan today is still behind pace`() {
        // 8h done, 16h due by Tuesday: today's spread goal is 10h 40m, but 5h 20m is still owed.
        val goal = (32 * H) / 3
        assertEquals(PaceStatus(PaceMessage.Behind(24 * H - 8 * H - goal), PaceState.MISSED), statusOn(8 * H, goal))
    }

    @Test
    fun `target reached`() {
        assertEquals(PaceStatus(PaceMessage.TargetReached, PaceState.MET), PacePlan(0, 0, true, 0, 0).status(0, 0))
    }

    @Test
    fun `no work days left but time still short`() {
        assertEquals(
            PaceStatus(PaceMessage.Short(H), PaceState.MISSED),
            PacePlan(0, 0, false, 0, 0).status(H, 0),
        )
    }

    @Test
    fun `no plan outside the current week or without a target`() {
        assertNull(week.pacePlan(monday, monday.plusDays(7), 0))
        assertNull(week.copy(targetSeconds = 0).pacePlan(monday, wednesday, 0))
    }

    @Test
    fun `day goals on schedule are even`() {
        assertEquals(listOf(8 * H, 8 * H, 8 * H, 8 * H, 8 * H, 0, 0), week.dayGoals(monday, wednesday, 16 * H))
    }

    @Test
    fun `day goals spread a missed day over today and later work days`() {
        val share = 32 * H / 3
        assertEquals(listOf(8 * H, 8 * H, share, share, share, 0, 0), week.dayGoals(monday, wednesday, 8 * H))
    }

    @Test
    fun `hours worked past today's goal come off later days`() {
        val goals = week.dayGoals(monday, wednesday, 16 * H, doneToday = 10 * H)
        assertEquals(listOf(8 * H, 8 * H, 8 * H, 7 * H, 7 * H, 0, 0), goals)
    }

    @Test
    fun `today's day goal matches the pace plan`() {
        val plan = week.pacePlan(monday, wednesday, 8 * H)!!
        assertEquals(plan.todayGoal, week.dayGoals(monday, wednesday, 8 * H)!![2])
    }

    @Test
    fun `no day goals outside the current week`() {
        assertNull(week.dayGoals(monday, monday.plusDays(7), 0))
    }
}

class WeekProgressTest {
    private val zone = "UTC"
    private val totals = week.copy(zoneId = zone)
    private val noon = wednesday.atTime(12, 0).atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun addsOnlyTodaysPartOfARunningTimer() {
        // Timer began Tuesday 22:00, now Wednesday 12:00: 12h run, 12h of it today.
        val timer = TimerState(running = true, beginMillis = noon - 14 * H * 1000)
        val p = totals.progress(monday, List(7) { 0L }, timer, noon)!!
        assertEquals(2, p.idx)
        assertEquals(12 * H, p.done[2])
    }

    @Test
    fun nullOutsideTheWeek() {
        assertNull(totals.progress(monday.plusDays(7), List(7) { 0L }, TimerState(), noon))
    }
}
