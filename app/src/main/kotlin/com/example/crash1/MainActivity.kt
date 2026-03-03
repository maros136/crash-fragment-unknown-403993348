package com.example.crash1

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment

/**
 * Crash reproducer
 *
 * Fatal Exception: java.lang.IllegalArgumentException
 * "The fragment VehicleDetailFragment{ff7cb8a} (...) is unknown to the FragmentNavigator.
 *  Please use the navigate() function to add fragments to the FragmentNavigator
 *  managed FragmentManager."
 * at androidx.navigation.fragment.FragmentNavigator$onAttach$2.onBackStackChangeCommitted
 *    (FragmentNavigator.kt:208)
 *
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  ROOT CAUSE                                                                 │
 * │                                                                             │
 * │  When a predictive back gesture commits, FragmentNavigator's               │
 * │  OnBackStackChangedListener fires onBackStackChangeCommitted() and removes  │
 * │  the popped entry from NavigatorState. That same commit also calls          │
 * │  execPendingActions() which may trigger dispatchPause() if the activity is  │
 * │  simultaneously being paused (e.g. power button press). dispatchPause →     │
 * │  execPendingActions fires onBackStackChangeCommitted AGAIN for the same     │
 * │  fragment. By then the entry is already gone from NavigatorState, so        │
 * │  requireNotNull(entry) in FragmentNavigator → IllegalArgumentException.     │
 * └─────────────────────────────────────────────────────────────────────────────┘
 *
 * HOW TO REPRODUCE (physical device, Android 14+):
 * 1. Launch the app.
 * 2. Tap "Go to Detail".
 * 3. Start a predictive back swipe gesture from the left edge (swipe slowly).
 * 4. While mid-swipe (or the moment you release to commit), press the POWER BUTTON.
 * 5. The activity pauses while the back stack pop is in flight → double-fire →
 *    IllegalArgumentException crash.
 *
 * HOW TO REPRODUCE (programmatic, more reliable):
 * 1. Launch the app.
 * 2. Tap "Go to Detail".
 * 3. Tap "Trigger crash programmatically".
 *    This button simulates the race by:
 *    a) Popping the back stack via NavController (simulates the predictive back commit)
 *    b) Immediately calling moveTaskToBack(true) on the SAME frame (simulates power button)
 *    The two operations race on the main thread — FragmentManager ends up calling
 *    onBackStackChangeCommitted twice for the same entry.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    /**
     * Exposes [onPause] as a public API for the reproducer.
     * Called from [DetailFragment.handleOnBackStarted] to simulate the power button press
     * mid-swipe — exactly the real-world scenario that causes Crash.
     */
    fun simulatePause() {
        onPause()
    }

    /**
     * Exposes [onResume] to return to normal state after [simulatePause].
     */
    fun simulateResume() {
        onResume()
    }
}
