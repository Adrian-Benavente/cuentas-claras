package com.cuentasclaras.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.cuentasclaras.domain.model.CategoryIcon

object CategoryIcons {
    fun imageVector(icon: CategoryIcon): ImageVector = when (icon) {
        CategoryIcon.BOLT -> Icons.Filled.Bolt
        CategoryIcon.WATER_DROP -> Icons.Filled.WaterDrop
        CategoryIcon.LOCAL_GAS_STATION -> Icons.Filled.LocalGasStation
        CategoryIcon.WIFI -> Icons.Filled.Wifi
        CategoryIcon.CREDIT_CARD -> Icons.Filled.CreditCard
        CategoryIcon.RESTAURANT -> Icons.Filled.Restaurant
        CategoryIcon.DIRECTIONS_CAR -> Icons.Filled.DirectionsCar
        CategoryIcon.HOME -> Icons.Filled.Home
        CategoryIcon.SHOPPING_CART -> Icons.Filled.ShoppingCart
        CategoryIcon.MEDICAL_SERVICES -> Icons.Filled.MedicalServices
        CategoryIcon.PHONE -> Icons.Filled.Phone
        CategoryIcon.SCHOOL -> Icons.Filled.School
        CategoryIcon.PETS -> Icons.Filled.Pets
        CategoryIcon.FITNESS_CENTER -> Icons.Filled.FitnessCenter
        CategoryIcon.MOVIE -> Icons.Filled.Movie
        CategoryIcon.SPORTS_ESPORTS -> Icons.Filled.SportsEsports
        CategoryIcon.FLIGHT -> Icons.Filled.Flight
        CategoryIcon.LOCAL_CAFE -> Icons.Filled.LocalCafe
        CategoryIcon.LOCAL_GROCERY_STORE -> Icons.Filled.LocalGroceryStore
        CategoryIcon.CLEANING_SERVICES -> Icons.Filled.CleaningServices
        CategoryIcon.BUILD -> Icons.Filled.Build
        CategoryIcon.CHILD_CARE -> Icons.Filled.ChildCare
        CategoryIcon.ATTACH_MONEY -> Icons.Filled.AttachMoney
        CategoryIcon.RECEIPT_LONG -> Icons.AutoMirrored.Filled.ReceiptLong
        CategoryIcon.CATEGORY -> Icons.Filled.Category
    }
}
