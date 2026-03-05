# Crash: Fragment unknown to FragmentNavigator - https://issuetracker.google.com/issues/403993348

**Exception:** `java.lang.IllegalArgumentException: The fragment X is unknown to the FragmentNavigator. Please use the navigate() function to add fragments to the FragmentNavigator managed FragmentManager.`  
**Location:** `FragmentNavigator$onAttach$2.onBackStackChangeCommitted (FragmentNavigator.kt:208)`

---

## Environment

- **Device:** Xiaomi 15 (2409BRN2CY), Android 15 (API 35)
- **Libraries:**
  - `androidx.fragment:fragment-ktx:1.8.9`
  - `androidx.navigation:navigation-fragment-ktx:2.9.7`
  - `com.google.android.material:material:1.13.0`
  - `androidx.activity:activity-ktx:1.12.4`
  - `androidx.transition:transition:1.7.0`
- **Manifest:** `android:enableOnBackInvokedCallback="true"`

---

## Description

When a predictive back gesture is in progress (seeking phase) and the activity is paused
(e.g. user presses the power button), `FragmentManager.dispatchPause()` cascades through
nested FragmentManagers. Each level calls `dispatchStateChange()`, which at line 3327
calls `execPendingActions(true)`. If there is a pending `BackStackRecord` at that point,
it is executed and `onBackStackChangeCommitted` is dispatched for a fragment that has
already been removed from `NavigatorState` by the ongoing predictive back transition,
causing `FragmentNavigator` to throw `IllegalArgumentException`.

---

## How to reproduce

This project provides three independent reproduction methods:

### Option A — Button (programmatic, one tap)

1. Launch the app.
2. Tap **"Go to Detail"**.
3. Tap **"▶ Option A: Trigger crash (button)"**.

**What it does:** Registers an `OnBackStackChangedListener` (same mechanism as Option B),
then starts a predictive back gesture programmatically via
`OnBackPressedDispatcher.dispatchOnBackStarted()` + `dispatchOnBackProgressed()` (the
gesture is NOT committed — no `onBackPressed()` call). The listener fires from
`onBackStackChangeStarted()` and calls `Activity.moveTaskToBack(true)`, sending the app
to background while the gesture is mid-flight. The OS delivers `PauseActivityItem`,
triggering `onPause` → `dispatchPause` → crash.

### Option B — Arm + swipe back (gesture, no power button)

1. Launch the app.
2. Tap **"Go to Detail"**.
3. Tap **"🎯 Option B: Arm, then swipe back"**.
4. Swipe back from the left edge of the screen.

**What it does:** Registers an `OnBackStackChangedListener` that calls
`Activity.moveTaskToBack(true)` from `onBackStackChangeStarted()`. When the user starts
a predictive back swipe, the listener fires immediately, sending the app to background
while the transition is in the seeking phase. Same crash path as Option A.

### Option C — Physical (swipe + power button, no code assist)

1. Launch the app.
2. Tap **"Go to Detail"**.
3. Start a predictive back swipe from the left edge and **release** to commit.
4. **Immediately press the POWER button** while the exit transition animation is playing
   (the window is ~300ms, the duration of the `MaterialSharedAxis` transition).
5. Power the screen back on.

**What happens:** The user commits the gesture (exit transition starts animating), then
presses the power button. The OS pauses the activity while the transition animation is
still running. `dispatchPause` → `dispatchStateChange(3327)` → `execPendingActions` → crash.

---

## Stacktraces from production (Firebase Crashlytics)

### Variant 1 — crash during `dispatchPause`

```
java.lang.RuntimeException: Unable to pause activity
  {xx.yyy.zzz/xx.yyy.zzz.ui.main.MainActivity}:
  java.lang.IllegalArgumentException: The fragment VehicleDetailFragment{66e561f}
    is unknown to the FragmentNavigator.
    at android.app.ActivityThread.performPauseActivityIfNeeded (ActivityThread.java:6436)

Caused by: java.lang.IllegalArgumentException:
    at androidx.navigation.fragment.FragmentNavigator$onAttach$2
        .onBackStackChangeCommitted (FragmentNavigator.kt:208)
    at androidx.fragment.app.FragmentManager.executeOpsTogether (FragmentManager.java:2188)
    at androidx.fragment.app.FragmentManager.execPendingActions (FragmentManager.java:2052)
    at androidx.fragment.app.FragmentManager.dispatchStateChange (FragmentManager.java:3327)
    at androidx.fragment.app.FragmentManager.dispatchPause (FragmentManager.java:3255)
    at androidx.fragment.app.Fragment.performPause (Fragment.java:3323)
    at androidx.fragment.app.FragmentStateManager.pause (FragmentStateManager.java:692)
    at androidx.fragment.app.FragmentStateManager.moveToExpectedState (FragmentStateManager.java:318)
    at androidx.fragment.app.FragmentStore.moveToExpectedState (FragmentStore.java:114)
    at androidx.fragment.app.FragmentManager.moveToState (FragmentManager.java:1685)
    at androidx.fragment.app.FragmentManager.dispatchStateChange (FragmentManager.java:3319)
    at androidx.fragment.app.FragmentManager.dispatchPause (FragmentManager.java:3255)
      ... (repeats for 5 nested FM levels) ...
    at androidx.fragment.app.FragmentController.dispatchPause (FragmentController.java:296)
    at androidx.fragment.app.FragmentActivity.onPause (FragmentActivity.java:284)
    at android.app.Activity.performPause (Activity.java:9624)
    at android.app.ActivityThread.performPauseActivityIfNeeded (ActivityThread.java:6423)
```

### Variant 2 — crash during `dispatchResume` (transition animation completion)

```
java.lang.IllegalArgumentException: The fragment VehicleDetailFragment{ff7cb8a}
  is unknown to the FragmentNavigator.
    at androidx.navigation.fragment.FragmentNavigator$onAttach$2
        .onBackStackChangeCommitted (FragmentNavigator.kt:208)
    at androidx.fragment.app.FragmentManager.executeOpsTogether (FragmentManager.java:2188)
    at androidx.fragment.app.FragmentManager.execPendingActions (FragmentManager.java:2052)
    at androidx.fragment.app.FragmentManager.dispatchStateChange (FragmentManager.java:3327)
    at androidx.fragment.app.FragmentManager.dispatchResume (FragmentManager.java:3251)
    at androidx.fragment.app.Fragment.performResume (Fragment.java:3219)
    at androidx.fragment.app.FragmentStateManager.resume (FragmentStateManager.java:666)
      ...
    at androidx.fragment.app.SpecialEffectsController$FragmentStateManagerOperation
        .complete$fragment_release (SpecialEffectsController.kt:846)
    at androidx.fragment.app.SpecialEffectsController$Operation
        .completeEffect (SpecialEffectsController.kt:740)
    at androidx.fragment.app.DefaultSpecialEffectsController$TransitionEffect
        .onStart$lambda$6$lambda$5 (DefaultSpecialEffectsController.java:786)
    at androidx.transition.Transition.notifyFromTransition (Transition.java:2345)
    at androidx.transition.TransitionSet.setCurrentPlayTimeMillis (TransitionSet.java:611)
    at androidx.dynamicanimation.animation.DynamicAnimation.doAnimationFrame (DynamicAnimation.java:687)
    at android.view.Choreographer.doCallbacks (Choreographer.java:1367)
```

## Key observations

- The crash originates at `FragmentManager.dispatchStateChange()` line 3327, which calls
  `execPendingActions(true)` after the try block that performs `moveToState()`. This
  `execPendingActions` call finds and executes a pending `BackStackRecord`.

- `executeOpsTogether()` at line 2188 dispatches `onBackStackChangeCommitted` to all
  registered `OnBackStackChangedListener`s. `FragmentNavigator`'s listener looks up the
  fragment in `NavigatorState` entries and calls `requireNotNull(entry)`, which throws
  because the entry was already removed by the ongoing predictive back transition.

- The crash affects fragments using `MaterialSharedAxis` transitions (which support
  predictive back seeking). The same fragments (`VehicleDetailFragment`,
  `RoutesOnMapFragment`) appear in multiple crash reports.

- The app uses deeply nested `FragmentManager` hierarchy (Activity → DashboardFragment →
  VehiclesFragment → nested NavHostFragment → VehicleDetailFragment). The `dispatchPause`
  cascade through 5 nested FM levels amplifies the chance of `execPendingActions` finding
  a pending record.
