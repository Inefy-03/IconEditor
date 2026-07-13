package com.bocchi.iconeditor.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplayTransitionEffects
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

@Composable
fun <T : Any> PredictiveNavDisplay(
    backStack: List<T>,
    modifier: Modifier = Modifier,
    predictiveBackEnabled: Boolean,
    onBack: () -> Unit,
    entryProvider: (T) -> NavEntry<T>,
) {
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            NavEntryDecorator { entry ->
                BackHandler(
                    enabled = !predictiveBackEnabled && backStack.size > 1,
                    onBack = onBack,
                )
                entry.Content()
            },
        ),
        entryProvider = entryProvider,
    )
    val sceneState = rememberSceneState(
        entries = entries,
        sceneStrategies = listOf(SinglePaneSceneStrategy()),
        sceneDecoratorStrategies = emptyList(),
        sharedTransitionScope = null,
        onBack = onBack,
    )
    val currentScene = sceneState.currentScene
    val eventState = rememberNavigationEventState(
        currentInfo = SceneInfo(currentScene),
        backInfo = sceneState.previousScenes.map(::SceneInfo),
    )

    NavigationBackHandler(
        state = eventState,
        isBackEnabled = predictiveBackHandlerEnabled(
            predictiveBackEnabled = predictiveBackEnabled,
            hasPreviousEntries = currentScene.previousEntries.isNotEmpty(),
        ),
        onBackCompleted = onBack,
    )
    NavDisplay(
        sceneState = sceneState,
        navigationEventState = eventState,
        modifier = modifier,
        transitionEffects = NavDisplayTransitionEffects(
            popDirectionFollowsSwipeEdge = false,
        ),
    )
}

internal fun predictiveBackHandlerEnabled(
    predictiveBackEnabled: Boolean,
    hasPreviousEntries: Boolean,
): Boolean = predictiveBackEnabled && hasPreviousEntries
