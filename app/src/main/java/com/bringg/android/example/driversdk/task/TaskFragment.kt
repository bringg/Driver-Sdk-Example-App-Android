package com.bringg.android.example.driversdk.task

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bringg.android.example.driversdk.R
import com.bringg.android.example.driversdk.authentication.AuthenticatedFragment
import com.google.android.material.tabs.TabLayoutMediator
import driver_sdk.driver.model.result.ExtrasUpdateResult
import driver_sdk.models.Waypoint
import driver_sdk.util.ext.TAG
import kotlinx.android.synthetic.main.task_fragment.*
import org.json.JSONObject


class TaskFragment : AuthenticatedFragment() {

    private val args: TaskFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.task_fragment, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val taskLiveData = viewModel.data.task(args.taskId)
        val findNavController = findNavController()
        taskLiveData.observe(viewLifecycleOwner, Observer { task ->
            Log.v("Task updated", "task=$task")
            if (task == null || !task.isAvailable) {
                findNavController.navigateUp()
            } else {
                task.wayPoints.forEachIndexed { index: Int, wp: Waypoint ->
                    if (!wp.isDone) {
                        vp_task_waypoints.currentItem = index
                        return@Observer
                    }
                }
                findNavController.navigateUp()
            }
        })
        val adapter = WaypointAdapter(this, taskLiveData)
        vp_task_waypoints.adapter = adapter
        TabLayoutMediator(tabLayout, vp_task_waypoints) { tab, position ->
            tab.text = if (taskLiveData.value?.currentWayPointIndex == position) "Current Destination" else "Destination ${position + 1}"
        }.attach()
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.task_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.cancel_task -> {
                showCancelDialog()
                return true
            }
            R.id.task_actions -> {
                showTaskActionsDialog()
                return true
            }
            R.id.update_extras -> {
                setTaskExtras()
                return true
            }

        }
        return false
    }

    private fun showCancelDialog() {
        findNavController().navigate(TaskFragmentDirections.actionTaskFragmentToDialogCancelTask(args.taskId))
    }

    private fun showTaskActionsDialog() {
        findNavController().navigate(
            TaskFragmentDirections.actionTaskFragmentToDialogActions(args.taskId)
        )
    }

    private fun setTaskExtras() {
        val currExtras = viewModel.data.extras.taskExtras(args.taskId).value
        val newExtras: JSONObject
        if (currExtras != null) {
            newExtras = currExtras
            newExtras.put("key", "value")
        } else {
            newExtras = JSONObject().put("key", "value")
        }

        viewModel.updateExtras(args.taskId, newExtras).observe(this, { result ->
            when (result) {
                is ExtrasUpdateResult.Success -> Log.i(TAG, "Update task extras task id=${args.taskId} success")
                is ExtrasUpdateResult.Error -> Log.i(TAG, "Failed to update task extras task id=${args.taskId} error=${result.error}")
            }
        })
    }
}
