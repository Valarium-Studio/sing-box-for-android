package io.nekohasekai.sfa.ui.main

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.libbox.DeprecatedNoteIterator
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.databinding.FragmentDashboardBinding
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ktx.launchCustomTab
import io.nekohasekai.sfa.ui.MainActivity
import io.nekohasekai.sfa.ui.dashboard.OverviewFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val activity: MainActivity? get() = super.getActivity() as MainActivity?
    private var binding: FragmentDashboardBinding? = null

    // Если TabLayout больше не нужен, можно удалить mediator целиком.
    private var mediator: TabLayoutMediator? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = FragmentDashboardBinding.inflate(inflater, container, false)
        this.binding = binding
        onCreate()
        return binding.root
    }

    private val adapter by lazy { Adapter(this) }

    private fun themedColor(@AttrRes attr: Int): Int =
        MaterialColors.getColor(requireContext(), attr, 0)

    private fun onCreate() {
        val activity = activity ?: return
        val binding = binding ?: return

        binding.dashboardPager.adapter = adapter
        binding.dashboardPager.offscreenPageLimit = Page.values().size

        activity.serviceStatus.observe(viewLifecycleOwner) { st ->
            val fab = binding.fab
            val label = binding.fabLabel
            val spinner = binding.progress

            when (st) {
                Status.Stopped -> {
                    // FAB: «включить»
                    fab.setImageResource(R.drawable.ic_play_arrow_24)
                    fab.backgroundTintList =
                        ColorStateList.valueOf(themedColor(com.google.android.material.R.attr.colorPrimary))
                    label.setText(R.string.label_connect)
                    spinner.isVisible = false
                    fab.isEnabled = true
                }
                Status.Starting -> {
                    // Идёт подключение
                    fab.setImageResource(R.drawable.ic_play_arrow_24)
                    fab.backgroundTintList =
                        ColorStateList.valueOf(themedColor(com.google.android.material.R.attr.colorTertiary))
                    label.setText(R.string.label_connecting)
                    spinner.isVisible = true
                    fab.isEnabled = false
                }
                Status.Started -> {
                    // FAB: «выключить»
                    fab.setImageResource(R.drawable.ic_stop_24)
                    fab.backgroundTintList =
                        ColorStateList.valueOf(themedColor(com.google.android.material.R.attr.colorError))
                    label.setText(R.string.label_disconnect)
                    spinner.isVisible = false
                    fab.isEnabled = true

                    checkDeprecatedNotes()
                    enablePager()
                }
                Status.Stopping -> {
                    label.setText(R.string.label_disconnecting)
                    spinner.isVisible = true
                    fab.isEnabled = false
                }
                else -> Unit
            }
        }

        binding.fab.setOnClickListener { v ->
            // лёгкая отдача + микро-анимация
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(80).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }.start()

            when (activity.serviceStatus.value) {
                Status.Stopped -> { v.isEnabled = false; activity.startService() }
                Status.Started -> BoxService.stop()
                else -> Unit
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val activityBinding = activity?.binding ?: return
        val binding = binding ?: return

        // Если на дашборде осталась только одна страница (Overview),
        // TabLayout можно вовсе не показывать и не привязывать.
        // Закомментируй ниже 3 строки, если убираешь вкладки полностью.
        if (mediator != null) return
        mediator = TabLayoutMediator(
            activityBinding.dashboardTabLayout,
            binding.dashboardPager
        ) { tab, position ->
            tab.setText(Page.values()[position].titleRes)
        }.apply { attach() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediator?.detach()
        mediator = null
        binding?.dashboardPager?.adapter = null
        binding = null
    }

    private fun checkDeprecatedNotes() {
        GlobalScope.launch(Dispatchers.IO) {
            runCatching {
                val notes = Libbox.newStandaloneCommandClient().deprecatedNotes
                if (notes.hasNext()) {
                    withContext(Dispatchers.Main) {
                        loopShowDeprecatedNotes(notes)
                    }
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    activity?.errorDialogBuilder(it)?.show()
                }
            }
        }
    }

    private fun loopShowDeprecatedNotes(notes: io.nekohasekai.libbox.DeprecatedNoteIterator) {
        // полностью приглушаем всплывашки
        while (notes.hasNext()) notes.next()
    }

    private fun enablePager() {
        val activity = activity ?: return
        val binding = binding ?: return
        activity.binding.dashboardTabLayout.isVisible = true
        binding.dashboardPager.isUserInputEnabled = true
    }

    @Suppress("SameParameterValue")
    private fun disablePager() {
        val activity = activity ?: return
        val binding = binding ?: return
        activity.binding.dashboardTabLayout.isVisible = false
        binding.dashboardPager.isUserInputEnabled = false
        binding.dashboardPager.setCurrentItem(0, false)
    }

    enum class Page(@StringRes val titleRes: Int, val fragmentClass: Class<out Fragment>) {
        Overview(R.string.title_overview, OverviewFragment::class.java);
    }

    class Adapter(parent: Fragment) : FragmentStateAdapter(parent) {
        override fun getItemCount() = Page.entries.size
        override fun createFragment(position: Int) =
            Page.entries[position].fragmentClass.getConstructor().newInstance()
    }
}
