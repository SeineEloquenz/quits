@file:OptIn(ExperimentalTime::class)

package nz.eloque.quits.util

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Current wall-clock time in epoch milliseconds. */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
