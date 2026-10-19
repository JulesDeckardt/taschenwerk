package de.taschenwerk.app.util

/** Erkennt englische Übungsnamen, damit sie mit englischer Stimme gesprochen werden. */
object Voice {
    private val words = setOf(
        "crunch", "crunches", "squat", "squats", "burpee", "burpees", "plank", "planks", "pushup", "pushups",
        "push", "up", "ups", "pull", "sit", "situp", "situps", "lunge", "lunges", "jumping", "jack", "jacks",
        "mountain", "climber", "climbers", "deadlift", "deadlifts", "bench", "press", "high", "knees", "russian",
        "twist", "twists", "bicycle", "bicycles", "leg", "legs", "raise", "raises", "wall", "dips", "dip", "jump",
        "jumps", "rest", "work", "kettlebell", "swing", "swings", "superman", "bridge", "bridges", "glute", "side",
        "skater", "skaters", "flutter", "kicks", "kick", "hollow", "hold", "star", "box", "curl", "curls", "row",
        "rows", "shoulder", "chest", "fly", "flys", "flies", "hip", "thrust", "thrusts", "calf", "split", "bear",
        "crawl", "inchworm", "inchworms", "boxing", "shadow", "rope", "skipping", "sprint", "sprints", "run",
        "running", "walk", "stretch", "warm", "cool", "down", "core", "abs", "arms", "toe", "toes", "touch",
        "touches", "v", "reverse", "dead", "bug", "bugs", "bird", "dog", "cat", "cow", "cobra", "lateral",
        "front", "back", "extension", "extensions", "triceps", "biceps", "goblet", "sumo", "pistol", "air",
        "knee", "tuck", "tucks", "scissor", "scissors", "windshield", "wipers", "heel", "taps", "tap"
    )

    fun isEnglish(name: String): Boolean {
        val tokens = name.lowercase().split(Regex("[^a-zäöüß]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        if (tokens.any { it.any { c -> c in "äöüß" } }) return false
        val hits = tokens.count { it in words }
        return hits * 2 >= tokens.size
    }
}
