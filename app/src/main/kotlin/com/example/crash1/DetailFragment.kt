package com.example.crash1

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.fragment.findNavController
import com.google.android.material.transition.MaterialSharedAxis
import com.example.crash1.databinding.FragmentDetailBinding

/**
 * Crash — IllegalArgumentException: fragment unknown to FragmentNavigator
 *
 * ROOT CAUSE (confirmed by reading FragmentManager.java + FragmentNavigator.kt):
 *
 * Inside [handleOnBackPressed]:
 *   mHandlingTransitioningOp = true
 *   execPendingActions(true)                    ← commits the pop
 *     → executeOpsTogether
 *       → onBackStackChangeCommitted(frag, pop=true)   ← 1st call, entry removed
 *       [power button fires here — OS dispatches onPause on main thread]
 *       → dispatchPause → execPendingActions(true)     ← reentrant, mHandlingTransitioningOp=true
 *           → executeOpsTogether
 *             → onBackStackChangeCommitted(frag, pop=true)  ← 2nd call, entry==null → CRASH
 *
 * The reentrance comes from [dispatchPause] being called on the main thread while
 * [handleOnBackPressed] is still executing (power button pressed mid-gesture).
 *
 * PROGRAMMATIC APPROACH — inject a one-shot [OnBackStackChangedListener] that calls
 * [simulatePause] from within [onBackStackChangeCommitted]. At that exact moment,
 * [mHandlingTransitioningOp] is true, so the reentrant [execPendingActions] from
 * [dispatchPause] does NOT cancel [mTransitioningOp] — it processes it a second time,
 * firing [onBackStackChangeCommitted] again for the already-gone entry → CRASH.
 *
 * NO REFLECTION — [simulatePause] calls the protected [Activity.onPause] via
 * [MainActivity.simulatePause] which is a plain public method.
 */
class DetailFragment : Fragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!
    private var armed = false
    private val handler = Handler(Looper.getMainLooper())

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
        binding.tvStatus.text = "Choose an option below.\n\nPhysical: swipe back + press power button."
        binding.btnTriggerCrash.setOnClickListener { triggerWithButton() }
        binding.btnArmGesture.setOnClickListener { armForGesture() }
    }

    // ── shared: inject the one-shot listener ─────────────────────────────────────────────────

    /**
     * Registers a one-shot [OnBackStackChangedListener] that fires [simulatePause] from
     * inside [onBackStackChangeCommitted]. At that moment [mHandlingTransitioningOp] is
     * `true`, so the reentrant [execPendingActions] from [dispatchPause] does NOT cancel
     * [mTransitioningOp] — it re-executes it, firing [onBackStackChangeCommitted] a second
     * time for the already-removed entry → CRASH.
     */
    private fun injectOneShotPauseTrigger() {
        val activity = requireActivity() as MainActivity
        parentFragmentManager.addOnBackStackChangedListener(
            object : FragmentManager.OnBackStackChangedListener {
                private var fired = false
                override fun onBackStackChanged() {}
                override fun onBackStackChangeStarted(fragment: Fragment, pop: Boolean) {}
                override fun onBackStackChangeCancelled() {}
                override fun onBackStackChangeCommitted(fragment: Fragment, pop: Boolean) {
                    if (fired) return
                    fired = true
                    // Remove ourselves so we don't fire on future pops
                    parentFragmentManager.removeOnBackStackChangedListener(this)
                    // Simulate the power button press:
                    // mHandlingTransitioningOp is true right now (we are inside
                    // handleOnBackPressed → execPendingActions → executeOpsTogether).
                    // simulatePause → dispatchPause → execPendingActions runs reentrant.
                    // Because mHandlingTransitioningOp==true, the "cancel gesture" branch
                    // is skipped → mTransitioningOp is committed a second time →
                    // onBackStackChangeCommitted fires again → entry==null → CRASH
                    activity.simulatePause()
                    activity.simulateResume()
                }
            }
        )
    }

    // ── Option A: button ─────────────────────────────────────────────────────────────────────

    private fun triggerWithButton() {
        binding.tvStatus.text = "Injecting pause trigger — popping back stack..."
        injectOneShotPauseTrigger()
        // The predictive back path requires mTransitioningOp to be set.
        // dispatchOnBackStarted → enqueueAction → mExecCommit sets mTransitioningOp.
        // We then trigger the full gesture sequence programmatically.
        val dispatcher = requireActivity().onBackPressedDispatcher
        dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
        handler.post {
            // mExecCommit has run → mTransitioningOp set → now commit the gesture
            dispatcher.dispatchOnBackProgressed(
                BackEventCompat(0.5f, 0f, 0f, BackEventCompat.EDGE_LEFT)
            )
            dispatcher.onBackPressed()
            // onBackPressed → handleOnBackPressed → execPendingActions → executeOpsTogether
            // → onBackStackChangeCommitted (1st) → our listener fires simulatePause
            // → dispatchPause → execPendingActions (reentrant) → 2nd onBackStackChangeCommitted
            // → entry==null → CRASH
        }
    }

    // ── Option B: gesture ────────────────────────────────────────────────────────────────────

    private fun armForGesture() {
        if (armed) { binding.tvStatus.text = "Already armed — swipe back from left edge."; return }
        armed = true
        binding.btnArmGesture.isEnabled = false
        injectOneShotPauseTrigger()
        binding.tvStatus.text = "✅ ARMED\n\nSwipe back from left edge and release.\nThe pause will be simulated automatically inside onBackStackChangeCommitted.\n\nOr: swipe back + press power button (physical)."
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
        _binding = null
    }
}
