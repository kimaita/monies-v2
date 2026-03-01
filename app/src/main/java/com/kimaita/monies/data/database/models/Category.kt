package com.kimaita.monies.data.database.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
@Immutable
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String? = null
)

fun getDefaultCategories(): List<Category> {
    return listOf(
        Category(name = "Airtime & Data"),
        Category(name = "Transport"),
        Category(name = "Fuel"),
        Category(name = "Groceries"),
        Category(name = "Shopping"),
        Category(name = "Meals"),
        Category(name = "Gifts"),
        Category(name = "Rent"),
        Category(name = "Internet"),
        Category(name = "Electricity"),
        Category(name = "Entertainment"),
        Category(name = "Health"),
        Category(name = "Education"),
        Category(name = "Miscellaneous"),
    )
}