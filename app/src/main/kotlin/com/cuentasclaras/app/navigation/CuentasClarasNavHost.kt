package com.cuentasclaras.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cuentasclaras.app.data.auth.SessionState
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
    const val JoinGroup = "join_group?code={code}"
    const val Group = "group/{groupId}?focusInvite={focusInvite}"
    const val CreateExpense = "group/{groupId}/expense/new"
    const val ExpenseDetail = "group/{groupId}/expense/{expenseId}"
    const val EditExpense = "group/{groupId}/expense/{expenseId}/edit"

    const val FlashMessageKey = "flash_message"

    fun group(groupId: String, focusInvite: Boolean = false) =
        "group/$groupId?focusInvite=$focusInvite"

    fun joinGroup(code: String? = null): String {
        val value = code.orEmpty()
        return "join_group?code=$value"
    }

    fun createExpense(groupId: String) = "group/$groupId/expense/new"
    fun expenseDetail(groupId: String, expenseId: String) = "group/$groupId/expense/$expenseId"
    fun editExpense(groupId: String, expenseId: String) = "group/$groupId/expense/$expenseId/edit"
}

@Composable
fun CuentasClarasNavHost(
    pendingJoinCode: String? = null,
    onPendingJoinCodeConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val session by authViewModel.sessionState.collectAsStateWithLifecycle()
    var queuedJoinCode by remember { mutableStateOf(pendingJoinCode) }

    LaunchedEffect(pendingJoinCode) {
        if (!pendingJoinCode.isNullOrBlank()) {
            queuedJoinCode = pendingJoinCode
        }
    }

    // Deep link while the app is already open and authenticated.
    LaunchedEffect(queuedJoinCode, session) {
        val code = queuedJoinCode ?: return@LaunchedEffect
        if (session !is SessionState.SignedIn) return@LaunchedEffect
        val route = navController.currentDestination?.route ?: return@LaunchedEffect
        if (route == Routes.Splash || route == Routes.Login || route == Routes.Register) {
            return@LaunchedEffect
        }
        navController.navigate(Routes.joinGroup(code)) {
            launchSingleTop = true
        }
        queuedJoinCode = null
        onPendingJoinCodeConsumed()
    }

    NavHost(navController = navController, startDestination = Routes.Splash) {
        composable(Routes.Splash) {
            SplashScreen(
                sessionState = session,
                onAuthenticated = {
                    val code = queuedJoinCode
                    if (!code.isNullOrBlank()) {
                        navController.navigate(Routes.joinGroup(code)) {
                            popUpTo(Routes.Splash) { inclusive = true }
                        }
                        queuedJoinCode = null
                        onPendingJoinCodeConsumed()
                    } else {
                        navController.navigate(Routes.Home) {
                            popUpTo(Routes.Splash) { inclusive = true }
                        }
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
                    val code = queuedJoinCode
                    if (!code.isNullOrBlank()) {
                        navController.navigate(Routes.joinGroup(code)) {
                            popUpTo(Routes.Login) { inclusive = true }
                        }
                        queuedJoinCode = null
                        onPendingJoinCodeConsumed()
                    } else {
                        navController.navigate(Routes.Home) {
                            popUpTo(Routes.Login) { inclusive = true }
                        }
                    }
                },
                onGoToRegister = { navController.navigate(Routes.Register) },
            )
        }
        composable(Routes.Register) {
            RegisterScreen(
                viewModel = authViewModel,
                onRegistered = {
                    val code = queuedJoinCode
                    if (!code.isNullOrBlank()) {
                        navController.navigate(Routes.joinGroup(code)) {
                            popUpTo(Routes.Login) { inclusive = true }
                        }
                        queuedJoinCode = null
                        onPendingJoinCodeConsumed()
                    } else {
                        navController.navigate(Routes.Home) {
                            popUpTo(Routes.Login) { inclusive = true }
                        }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.Home) {
            HomeScreen(
                onOpenGroup = { groupId -> navController.navigate(Routes.group(groupId)) },
                onCreateGroup = { navController.navigate(Routes.CreateGroup) },
                onJoinGroup = { navController.navigate(Routes.joinGroup()) },
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
                    navController.navigate(Routes.group(groupId, focusInvite = true)) {
                        popUpTo(Routes.Home)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.JoinGroup,
            arguments = listOf(
                navArgument("code") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val initialCode = entry.arguments?.getString("code")?.takeIf { it.isNotBlank() }
            JoinGroupScreen(
                initialCode = initialCode,
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
            arguments = listOf(
                navArgument("groupId") { type = NavType.StringType },
                navArgument("focusInvite") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            val groupId = entry.arguments?.getString("groupId").orEmpty()
            val focusInvite = entry.arguments?.getBoolean("focusInvite") == true
            val flashMessage by entry.savedStateHandle
                .getStateFlow<String?>(Routes.FlashMessageKey, null)
                .collectAsStateWithLifecycle()
            GroupScreen(
                groupId = groupId,
                focusInvite = focusInvite,
                flashMessage = flashMessage,
                onFlashConsumed = { entry.savedStateHandle[Routes.FlashMessageKey] = null },
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
        ) {
            ExpenseEditorScreen(
                groupId = it.arguments?.getString("groupId").orEmpty(),
                expenseId = null,
                onDone = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(Routes.FlashMessageKey, "Gasto guardado")
                    navController.popBackStack()
                },
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
            val flashMessage by entry.savedStateHandle
                .getStateFlow<String?>(Routes.FlashMessageKey, null)
                .collectAsStateWithLifecycle()
            ExpenseDetailScreen(
                groupId = groupId,
                expenseId = expenseId,
                flashMessage = flashMessage,
                onFlashConsumed = { entry.savedStateHandle[Routes.FlashMessageKey] = null },
                onEdit = { navController.navigate(Routes.editExpense(groupId, expenseId)) },
                onDeleted = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(Routes.FlashMessageKey, "Gasto eliminado")
                    navController.popBackStack()
                },
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
                onDone = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(Routes.FlashMessageKey, "Gasto actualizado")
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
