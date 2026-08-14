package com.cuentasclaras.domain.model

/**
 * Curated Material Icon keys for expense categories.
 * Persist [value]; map to Compose icons in the app layer.
 */
enum class CategoryIcon(val value: String) {
    BOLT("bolt"),
    WATER_DROP("water_drop"),
    LOCAL_GAS_STATION("local_gas_station"),
    WIFI("wifi"),
    CREDIT_CARD("credit_card"),
    RESTAURANT("restaurant"),
    DIRECTIONS_CAR("directions_car"),
    HOME("home"),
    SHOPPING_CART("shopping_cart"),
    MEDICAL_SERVICES("medical_services"),
    PHONE("phone"),
    SCHOOL("school"),
    PETS("pets"),
    FITNESS_CENTER("fitness_center"),
    MOVIE("movie"),
    SPORTS_ESPORTS("sports_esports"),
    FLIGHT("flight"),
    LOCAL_CAFE("local_cafe"),
    LOCAL_GROCERY_STORE("local_grocery_store"),
    CLEANING_SERVICES("cleaning_services"),
    BUILD("build"),
    CHILD_CARE("child_care"),
    ATTACH_MONEY("attach_money"),
    RECEIPT_LONG("receipt_long"),
    CATEGORY("category"),
    ;

    companion object {
        const val DEFAULT_VALUE = "category"

        val ALLOWED_VALUES: Set<String> = entries.map { it.value }.toSet()

        fun fromValue(raw: String?): CategoryIcon {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.value == normalized } ?: CATEGORY
        }

        fun requireValid(raw: String): CategoryIcon {
            val normalized = raw.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
                ?: throw IllegalArgumentException("Invalid category icon: $raw")
        }
    }
}
