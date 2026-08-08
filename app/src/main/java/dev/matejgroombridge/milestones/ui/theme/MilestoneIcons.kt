package dev.matejgroombridge.milestones.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The curated set of icons the user can pick from for a milestone. Stored on
 * a [dev.matejgroombridge.milestones.data.model.Milestone] by string key, so
 * adding or removing icons later doesn't break the persisted JSON — unknown
 * keys gracefully fall back to [defaultEntry].
 *
 * Ordering is thematic (achievement → measurement → activity → craft →
 * money → misc) so the picker grid reads as grouped rather than random.
 */
data class MilestoneIconEntry(val key: String, val label: String, val icon: ImageVector)

object MilestoneIcons {

    val catalog: List<MilestoneIconEntry> = listOf(
        // Achievement
        MilestoneIconEntry("trophy", "Trophy", Icons.Outlined.EmojiEvents),
        MilestoneIconEntry("medal", "Medal", Icons.Outlined.MilitaryTech),
        MilestoneIconEntry("star", "Star", Icons.Outlined.Star),
        MilestoneIconEntry("flag", "Goal", Icons.Outlined.Flag),
        MilestoneIconEntry("rocket", "Launch", Icons.Outlined.RocketLaunch),
        MilestoneIconEntry("fire", "Streak", Icons.Outlined.Whatshot),
        MilestoneIconEntry("bolt", "Power", Icons.Outlined.Bolt),
        // Measurement
        MilestoneIconEntry("chart", "Chart", Icons.Outlined.BarChart),
        MilestoneIconEntry("insights", "Insights", Icons.Outlined.Insights),
        MilestoneIconEntry("timer", "Timer", Icons.Outlined.Timer),
        MilestoneIconEntry("lap", "Lap", Icons.Outlined.Timelapse),
        MilestoneIconEntry("speed", "Speed", Icons.Outlined.Speed),
        MilestoneIconEntry("ruler", "Distance", Icons.Outlined.Straighten),
        MilestoneIconEntry("weight", "Weight", Icons.Outlined.MonitorWeight),
        // Activity
        MilestoneIconEntry("run", "Run", Icons.AutoMirrored.Outlined.DirectionsRun),
        MilestoneIconEntry("bike", "Bike", Icons.AutoMirrored.Outlined.DirectionsBike),
        MilestoneIconEntry("hike", "Hike", Icons.Outlined.Hiking),
        MilestoneIconEntry("swim", "Swim", Icons.Outlined.Pool),
        MilestoneIconEntry("fitness", "Lift", Icons.Outlined.FitnessCenter),
        MilestoneIconEntry("basketball", "Hoops", Icons.Outlined.SportsBasketball),
        MilestoneIconEntry("soccer", "Soccer", Icons.Outlined.SportsSoccer),
        MilestoneIconEntry("terrain", "Climb", Icons.Outlined.Terrain),
        MilestoneIconEntry("park", "Outdoors", Icons.Outlined.Park),
        MilestoneIconEntry("yoga", "Yoga", Icons.Outlined.SelfImprovement),
        MilestoneIconEntry("spa", "Spa", Icons.Outlined.Spa),
        MilestoneIconEntry("mind", "Mindful", Icons.Outlined.Psychology),
        // Craft & learning
        MilestoneIconEntry("book", "Book", Icons.Outlined.Book),
        MilestoneIconEntry("menu_book", "Read", Icons.AutoMirrored.Outlined.MenuBook),
        MilestoneIconEntry("school", "Study", Icons.Outlined.School),
        MilestoneIconEntry("write", "Write", Icons.Outlined.Create),
        MilestoneIconEntry("brush", "Art", Icons.Outlined.Brush),
        MilestoneIconEntry("camera", "Photo", Icons.Outlined.Camera),
        MilestoneIconEntry("code", "Code", Icons.Outlined.Code),
        MilestoneIconEntry("music", "Music", Icons.Outlined.MusicNote),
        MilestoneIconEntry("headphones", "Listen", Icons.Outlined.Headphones),
        MilestoneIconEntry("game", "Game", Icons.Outlined.SportsEsports),
        // Money & work
        MilestoneIconEntry("money", "Money", Icons.Outlined.AttachMoney),
        MilestoneIconEntry("payments", "Revenue", Icons.Outlined.Payments),
        MilestoneIconEntry("savings", "Savings", Icons.Outlined.Savings),
        MilestoneIconEntry("work", "Work", Icons.Outlined.Work),
        // Misc
        MilestoneIconEntry("flight", "Travel", Icons.Outlined.Flight),
        MilestoneIconEntry("globe", "World", Icons.Outlined.Language),
        MilestoneIconEntry("water", "Water", Icons.Outlined.WaterDrop),
        MilestoneIconEntry("coffee", "Coffee", Icons.Outlined.LocalCafe),
        MilestoneIconEntry("restaurant", "Eat", Icons.Outlined.Restaurant),
        MilestoneIconEntry("heart", "Love", Icons.Outlined.FavoriteBorder),
        MilestoneIconEntry("pet", "Pet", Icons.Outlined.Pets),
        MilestoneIconEntry("sun", "Morning", Icons.Outlined.WbSunny),
        MilestoneIconEntry("moon", "Night", Icons.Outlined.NightsStay),
    )

    private val byKey: Map<String, MilestoneIconEntry> = catalog.associateBy { it.key }

    val defaultEntry: MilestoneIconEntry get() = catalog.first()

    fun entry(key: String): MilestoneIconEntry = byKey[key] ?: defaultEntry

    fun icon(key: String): ImageVector = entry(key).icon
}
