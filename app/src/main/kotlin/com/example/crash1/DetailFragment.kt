package com.example.crash1

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.BackEventCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.transition.MaterialSharedAxis
import com.example.crash1.databinding.FragmentDetailBinding

/**
 * Crash — IllegalArgumentException: fragment unknown to FragmentNavigator
 *
 * Real crash stacktrace (Firebase):
 *   FragmentNavigator$onAttach$2.onBackStackChangeCommitted (FragmentNavigator.kt:208)
 *   FragmentManager.executeOpsTogether (FragmentManager.java:2188)
 *   FragmentManager.execPendingActions (FragmentManager.java:2052)
 *   FragmentManager.dispatchStateChange (FragmentManager.java:3327)
 *   FragmentManager.dispatchPause / dispatchResume
 *   ... (nested FM cascade) ...
 *   FragmentActivity.onPause
 *   ActivityThread.performPauseActivityIfNeeded
 *
 * Three independent reproduction methods:
 *   A) Button — registers listener + starts gesture programmatically (no commit, gesture mid-flight)
 *   B) Arm + gesture — swipe back triggers moveTaskToBack from onBackStackChangeStarted
 *   C) Physical — swipe back + release + press POWER button during exit transition
 */
class DetailFragment : Fragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = FragmentDetailBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvStatus.text =
            "A) Trigger crash — programmatic gesture + moveTaskToBack\n" +
            "B) Arm + swipe back — moveTaskToBack on gesture start\n" +
            "C) Physical — swipe back + release + press POWER button"

        binding.btnTriggerCrash.text = "▶  Option A: Trigger crash (button)"
        binding.btnArmGesture.text = "🎯  Option B: Arm, then swipe back"

        binding.btnTriggerCrash.setOnClickListener { triggerWithButton() }
        binding.btnArmGesture.setOnClickListener { armForGesture() }
    }

    private fun log(msg: String) {
        Log.e("CRASH1", msg)
        _binding?.tvStatus?.text = msg
    }

    // ── Option A — Button (programmatic) ─────────────────────────────────
    // Combines the mechanism of Option B into a single button tap:
    // registers the same onBackStackChangeStarted listener, then starts
    // a predictive back gesture programmatically. The listener fires during
    // dispatchOnBackStarted and calls moveTaskToBack, sending the app to
    // background while the gesture is mid-flight.
    @SuppressLint("RestrictedApi")
    private fun triggerWithButton() {
        binding.btnTriggerCrash.isEnabled = false
        binding.btnArmGesture.isEnabled = false

        // Install the same listener as Option B
        val fm = parentFragmentManager
        fm.addOnBackStackChangedListener(object : FragmentManager.OnBackStackChangedListener {
            private var fired = false
            override fun onBackStackChanged() {}
            override fun onBackStackChangeStarted(fragment: Fragment, pop: Boolean) {
                if (!pop || fired) return
                fired = true
                fm.removeOnBackStackChangedListener(this)
                log("Option A: onBackStackChangeStarted → moveTaskToBack NOW")
                requireActivity().moveTaskToBack(true)
            }
        })

        val dispatcher = requireActivity().onBackPressedDispatcher

        log("Option A: starting predictive back gesture...")
        dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
        dispatcher.dispatchOnBackProgressed(BackEventCompat(0.5f, 0f, 0f, BackEventCompat.EDGE_LEFT))
    }

    // ── Option B — Arm + gesture ─────────────────────────────────────────
    // Registers a listener on onBackStackChangeStarted that immediately calls
    // moveTaskToBack(true). When the user swipes back, the gesture starts,
    // the listener fires, and the app goes to background mid-gesture.
    // The OS PauseActivityItem arrives while the predictive back transition
    // is in the seeking phase → dispatchPause → execPendingActions → crash.
    private var armed = false

    private fun armForGesture() {
        if (armed) { log("Already armed — swipe back now!"); return }
        armed = true
        binding.btnArmGesture.isEnabled = false
        binding.btnTriggerCrash.isEnabled = false

        val fm = parentFragmentManager
        fm.addOnBackStackChangedListener(object : FragmentManager.OnBackStackChangedListener {
            private var fired = false
            override fun onBackStackChanged() {}

            override fun onBackStackChangeStarted(fragment: Fragment, pop: Boolean) {
                if (!pop || fired) return
                fired = true
                fm.removeOnBackStackChangedListener(this)
                log("Option B: onBackStackChangeStarted → moveTaskToBack NOW")
                requireActivity().moveTaskToBack(true)
            }
        })

        log("✅ ARMED (Option B)\n\n" +
            "Swipe back from left edge now.\n" +
            "moveTaskToBack fires on gesture start → crash on onPause.\n\n" +
            "For Option C (physical): navigate to Detail again,\n" +
            "swipe back + release, then press POWER button within ~300ms.")
    }

    override fun onPause() {
        super.onPause()
        Log.e("CRASH1", "DetailFragment.onPause()")
    }

    override fun onResume() {
        super.onResume()
        Log.e("CRASH1", "DetailFragment.onResume()")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
