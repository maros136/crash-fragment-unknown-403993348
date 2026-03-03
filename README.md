# Crash Reproducer - https://issuetracker.google.com/issues/403993348

**`IllegalArgumentException: The fragment X is unknown to the FragmentNavigator`**  
`androidx.navigation.fragment.FragmentNavigator$onAttach$2.onBackStackChangeCommitted`

## Root cause

Inside `FragmentManager.handleOnBackPressed()`:

```java
mHandlingTransitioningOp = true;
execPendingActions(true);          // ← commits the predictive back pop
mHandlingTransitioningOp = false;
```

Inside `execPendingActions` → `executeOpsTogether`:
1. `onBackStackChangeCommitted(fragment, pop=true)` fires — **1st call**  
   → `FragmentNavigator` removes the entry from `NavigatorState`

2. **Power button pressed** at this exact instant:  
   OS dispatches `onPause` synchronously on the main thread  
   → `dispatchPause()` → `execPendingActions(true)` **(reentrant)**  
   → Because `mHandlingTransitioningOp == true`, the `!mHandlingTransitioningOp && mTransitioningOp != null` guard that would normally *cancel* the gesture is **skipped**  
   → `mTransitioningOp` is committed a second time  
   → `onBackStackChangeCommitted(fragment, pop=true)` fires again — **2nd call**  
   → `entry == null` (already removed in step 1)  
   → `requireNotNull(entry)` → **`IllegalArgumentException`**

## Reproducer mechanism

`MainActivity` exposes `simulatePause()` / `simulateResume()` which call the
(otherwise protected) `onPause()` / `onResume()` directly.

A one-shot `OnBackStackChangedListener` calls `simulatePause()` from inside
`onBackStackChangeCommitted` — the exact moment when `mHandlingTransitioningOp == true`.
This makes the reentrant `execPendingActions` commit `mTransitioningOp` a second time
instead of cancelling it, reproducing the power-button race exactly.

## Steps to reproduce

### Physical (most reliable — confirmed working)
1. Build and install on any Android 14+ device.
2. Tap **Go to Detail**.
3. Start a predictive back swipe from the left edge.
4. While mid-swipe, press the **power button**.

### Option A — Button (programmatic)
1. Build and install on any Android 14+ device or emulator.
2. Tap **Go to Detail**.
3. Tap **"▶ Trigger crash (button)"**.

### Option B — Gesture (programmatic)
1. Build and install on any Android 14+ device or emulator.
2. Tap **Go to Detail**.
3. Tap **"🎯 Arm for gesture"**.
4. Swipe back from the left edge and release.

## Expected crash

```
java.lang.IllegalArgumentException: The fragment DetailFragment{...}
    is unknown to the FragmentNavigator.
    at androidx.navigation.fragment.FragmentNavigator$onAttach$2
           .onBackStackChangeCommitted(FragmentNavigator.kt:208)
    at androidx.fragment.app.FragmentManager
           .executeOpsTogether(FragmentManager.java:2188)
    at androidx.fragment.app.FragmentManager
           .removeRedundantOperationsAndExecute(FragmentManager.java:2115)
    at androidx.fragment.app.FragmentManager
           .execPendingActions(FragmentManager.java:2052)
    at androidx.fragment.app.FragmentManager
           .dispatchStateChange(FragmentManager.java:3327)
    at androidx.fragment.app.FragmentManager
           .dispatchPause(FragmentManager.java:3255)
```

## Versions

- `androidx.fragment:fragment:1.8.9`
- `androidx.navigation:navigation-fragment:2.9.7`
- `com.google.android.material:material:1.13.0`
- `androidx.activity:activity-ktx:1.12.4`
