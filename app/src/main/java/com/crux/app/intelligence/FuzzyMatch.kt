package com.crux.app.intelligence

import com.crux.app.domain.model.Task
import kotlin.math.max

/**
 * Local fuzzy matching for the destructive verbs (intelligence.md safety contract). The model never
 * gives us a task id or a title to act on — only a `query` string. We match that against OPEN tasks
 * here, on-device, so a hallucinated title can never touch the wrong task.
 *
 * Thresholds, straight from the contract:
 *  - one clear winner (score ≥ 0.75, and the runner-up trailing by ≥ 0.10) → act.
 *  - several plausible (any ≥ 0.50) → a pick list, the user chooses.
 *  - nothing ≥ 0.50 → not found.
 */
sealed interface FuzzyResult {
    data class One(val task: Task) : FuzzyResult
    data class Many(val candidates: List<Task>) : FuzzyResult
    data object None : FuzzyResult
}

private const val ACT_THRESHOLD = 0.75
private const val ACT_MARGIN = 0.10
private const val CANDIDATE_THRESHOLD = 0.50
private const val MAX_CANDIDATES = 6

fun matchTask(query: String, open: List<Task>): FuzzyResult {
    if (open.isEmpty()) return FuzzyResult.None
    val ranked = open
        .map { it to titleScore(query, it.title) }
        .sortedByDescending { it.second }
    val (best, bestScore) = ranked.first()
    if (bestScore < CANDIDATE_THRESHOLD) return FuzzyResult.None

    val runnerUp = ranked.getOrNull(1)?.second ?: 0.0
    if (bestScore >= ACT_THRESHOLD && bestScore - runnerUp >= ACT_MARGIN) {
        return FuzzyResult.One(best)
    }
    val candidates = ranked.filter { it.second >= CANDIDATE_THRESHOLD }
        .take(MAX_CANDIDATES)
        .map { it.first }
    return FuzzyResult.Many(candidates)
}

/**
 * Similarity of [query] to a task [title] in 0..1. The query is what the user *said* — usually a few
 * words picked out of a longer title — so the dominant signal is recall: how many of the query's words
 * the title contains ("sov deck" fully inside "send the sov deck to the client"). A whole-string
 * edit-distance term rides underneath to rescue typos and reward closeness in length. Pure and
 * deterministic — the unit-tested core.
 */
fun titleScore(query: String, title: String): Double {
    val q = normalize(query)
    val t = normalize(title)
    if (q.isEmpty() || t.isEmpty()) return 0.0
    if (q == t) return 1.0

    val qTokens = q.split(' ').filter { it.isNotEmpty() }
    val tTokens = t.split(' ').filter { it.isNotEmpty() }.toSet()
    val recall = if (qTokens.isEmpty()) 0.0 else qTokens.count { it in tTokens }.toDouble() / qTokens.size

    val editSim = 1.0 - levenshtein(q, t).toDouble() / max(q.length, t.length)
    return 0.7 * recall + 0.3 * editSim
}

/** Lowercase, strip everything but letters/digits to spaces, collapse runs, trim. */
private fun normalize(s: String): String =
    s.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
        .trim().replace(Regex("\\s+"), " ")

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prev = IntArray(b.length + 1) { it }
    var curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
        curr[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
        }
        val tmp = prev; prev = curr; curr = tmp
    }
    return prev[b.length]
}
