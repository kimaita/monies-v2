package com.kimaita.monies.data.database.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subjects",
    indices = [Index(value = ["rawName", "number"], unique = true)]
)
@Immutable
data class Subject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** The name as parsed; the parser's lookup key. */
    val rawName: String,
    /** Editable display name. */
    val name: String,
    val number: String = "",
    val mergedInto: Long? = null,
    val categoryId: Int? = null,
)
