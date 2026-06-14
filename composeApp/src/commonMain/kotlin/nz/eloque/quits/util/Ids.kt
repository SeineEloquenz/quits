package nz.eloque.quits.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A fresh opaque id for a locally-created entity. */
@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()
