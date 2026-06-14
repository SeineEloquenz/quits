package nz.eloque.quits.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A fresh opaque id for a locally-created entity. */
@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()

/** A short human-shareable join code (used as the group's sync capability). */
@OptIn(ExperimentalUuidApi::class)
fun newJoinCode(): String = Uuid.random().toString().filter { it.isLetterOrDigit() }.take(6).uppercase()
