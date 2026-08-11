package com.cuentasclaras.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cuentasclaras.app.presentation.auth.AuthViewModel
import com.cuentasclaras.app.presentation.auth.LoginScreen
import com.cuentasclaras.app.presentation.auth.RegisterScreen
import com.cuentasclaras.app.presentation.expense.ExpenseDetailScreen
import com.cuentasclaras.app.presentation.expense.ExpenseEditorScreen
import com.cuentasclaras.app.presentation.group.CreateGroupScreen
import com.cuentasclaras.app.presentation.group.GroupScreen
import com.cuentasclaras.app.presentation.group.JoinGroupScreen
import com.cuentasclaras.app.presentation.home.HomeScreen
import com.cuentasclaras.app.presentation.splash.SplashScreen

object Routes {
    const val Splash = "splash"
    const val Login = "login"
    const val Register = "register"
    const val Home = "home"
    const val CreateGroup = "create_group"
    const val JoinGroup = "join_group"
    const val Group = "group/{groupId}"
    const val CreateExpense = "group/{groupId}/expense/new"
    const val ExpenseDetail = "group/{groupId}/expense/{expenseId}"
    const val EditExpense = "group/{groupId}/expense/{expenseId}/edit"

    fun group(groupId: String) = "group/$groupId"
    fun createExpense(groupId: String) = "group/$groupId/expense/new"
    fun expenseDetail(groupId: String, expenseId: String) = "group/$groupId/expense/$expenseId"
    fun editExpense(groupId: String, expenseId: String) = "group/$groupId/expense/$expenseId/edit"
}

@Composable
fun CuentasClarasNavHost() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val session by authViewModel.sessionState.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.Splash) {
        composable(Routes.Splash) {
            SplashScreen(
                sessionState = session,
                onAuthenticated = {
                    navController.navigate(Routes.Home) {
                        popUpTo(Routes.Splash) { inclusive = true }
                    }
                },
                onUnauthenticated = {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Splash) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.Login) {
            LoginScreen(
                viewModel = authViewModel,
                onLoggedIn = {
                    navController.navigate(Routes.Home) {
                        popUpTo(Routes.Login) { inclusive = true }
                    }
                },
                onGoToRegister = { navController.navigate(Routes.Register) },
            )
        }
        composable(Routes.Register) {
            RegisterScreen(
                viewModel = authViewModel,
                onRegistered = {
                    navController.navigate(Routes.Home) {
                        popUpTo(Routes.Login) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.Home) {
            HomeScreen(
                onOpenGroup = { groupId -> navController.navigate(Routes.group(groupId)) },
                onCreateGroup = { navController.navigate(Routes.CreateGroup) },
                onJoinGroup = { navController.navigate(Routes.JoinGroup) },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Home) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.CreateGroup) {
            CreateGroupScreen(
                onCreated = { groupId ->
                    navController.navigate(Routes.group(groupId)) {
                        popUpTo(Routes.Home)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.JoinGroup) {
            JoinGroupScreen(
                onJoined = { groupId ->
                    navController.navigate(Routes.group(groupId)) {
                        popUpTo(Routes.Home)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.Group,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { entry ->
            val groupId = entry.arguments?.getString("groupId").orEmpty()
            GroupScreen(
                groupId = groupId,
                onBack = { navController.popBackStack() },
                onAddExpense = { navController.navigate(Routes.createExpense(groupId)) },
                onOpenExpense = { expenseId ->
                    navController.navigate(Routes.expenseDetail(groupId, expenseId))
                },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.Login) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.CreateExpense,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { entry ->
            val groupId = entry.arguments?.getString("groupId").orEmpty()
            ExpenseEditorScreen(
                groupId = groupId,
                expenseId = null,
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.ExpenseDetail,
            arguments = listOf(
                navArgument("groupId") { type = NavType.StringType },
                navArgument("expenseId") { type = NavType.StringType },
            ),
        ) { entry ->
            val groupId = entry.arguments?.getString("groupId").orEmpty()
            val expenseId = entry.arguments?.getString("expenseId").orEmpty()
            ExpenseDetailScreen(
                groupId = groupId,
                expenseId = expenseId,
                onEdit = { navController.navigate(Routes.editExpense(groupId, expenseId)) },
                onDeleted = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.EditExpense,
            arguments = listOf(
                navArgument("groupId") { type = NavType.StringType },
                navArgument("expenseId") { type = NavType.StringType },
            ),
        ) { entry ->
            val groupId = entry.arguments?.getString("groupId").orEmpty()
            val expenseId = entry.arguments?.getString("expenseId").orEmpty()
            ExpenseEditorScreen(
                groupId = groupId,
                expenseId = expenseId,
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
