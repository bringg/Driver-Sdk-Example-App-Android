package com.bringg.android.example.driversdk.tasklist

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.PermissionChecker
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.selection.MutableSelection
import androidx.recyclerview.selection.Selection
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.SelectionTracker.Builder
import androidx.recyclerview.selection.SelectionTracker.SelectionObserver
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.RecyclerView
import com.bringg.android.example.driversdk.R
import com.bringg.android.example.driversdk.R.plurals
import com.bringg.android.example.driversdk.authentication.AuthenticatedFragment
import com.bringg.android.example.driversdk.clusters.ClusterListAdapter
import com.bringg.android.example.driversdk.clusters.ClusterViewHolder
import com.bringg.android.example.driversdk.clusters.ClusterViewModel
import com.bringg.android.example.driversdk.databinding.TaskListFragmentBinding
import com.bringg.android.example.driversdk.homelist.HomeListAdapter
import com.bringg.android.example.driversdk.tasklist.TaskViewHolder.ClickListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import driver_sdk.content.ResultCallback
import driver_sdk.driver.model.result.CreateGroupTaskResult
import driver_sdk.driver.model.result.UnGroupTaskResult
import driver_sdk.models.Cluster
import driver_sdk.models.Task
import driver_sdk.tasks.TaskCancelResult

class TaskListFragment : AuthenticatedFragment() {

    private val TAG = "TaskListFragment"
    private val START_SHIFT_WITH_LOCATION_PERMISSION_REQUEST_CODE = 5

    private var _binding: TaskListFragmentBinding? = null
    private val binding get() = _binding!!
    private val clusterViewModel: ClusterViewModel by activityViewModels()
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>
    private lateinit var taskSelectionTracker: SelectionTracker<Long>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = TaskListFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initHomeStatesView()
        initTaskListView()
        initClustersView()
        initBottomSheet()

        binding.taskListSwipeToRefresh.setOnRefreshListener {
            viewModel.data.refreshTaskList()
        }
    }

    private fun initClustersView() = with(binding.clustersExpandable) {
        setOnClickListener { toggleLayout() }
        val adapter = ClusterListAdapter(object : ClusterViewHolder.ClickListener {
            override fun onClusterItemClick(cluster: Cluster) {
                clusterViewModel.onWaypointsChanged(cluster.wayPoints.toList())
                findNavController().navigate(TaskListFragmentDirections.actionTaskListToClusterFragment())
            }
        })
        secondLayout.findViewById<RecyclerView>(R.id.rv_cluster_list).adapter = adapter
        viewModel.data.clusters.observe(viewLifecycleOwner) {
            Log.i(TAG, "clusters updated, clusters=$it")
            adapter.submitList(it)
        }
    }

    private fun initHomeStatesView() = with(binding.homeStateExpandable) {
        setOnClickListener { toggleLayout() }
        val homeListAdapter = HomeListAdapter()
        secondLayout.findViewById<RecyclerView>(R.id.home_state_recycler).adapter = homeListAdapter
        viewModel.data.homeMap.observe(viewLifecycleOwner) {
            Log.i(TAG, "home states changed, states=$it")
            homeListAdapter.submitList(it.toList())
        }
    }

    private fun initTaskListView() {
        val taskList = viewModel.data.taskList
        val adapter = TaskListAdapter(object : ClickListener {
            override fun onTaskItemClick(task: Task) {
                findNavController().navigate(TaskListFragmentDirections.actionTaskListToTaskFragment(task.getId()))
            }
        })
        binding.rvTaskList.adapter = adapter
        initSelectionTracker(adapter)
        taskList.observe(viewLifecycleOwner) {
            Log.i(TAG, "task list update, tasks=$it")
            binding.taskListSwipeToRefresh.isRefreshing = false
            binding.taskListFragmentEmpty.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
            taskSelectionTracker.clearSelection()
            adapter.submitList(it)
        }
    }

    private fun initSelectionTracker(adapter: TaskListAdapter) = with(binding.rvTaskList) {
        taskSelectionTracker = Builder(
            "taskListSelection",
            this,
            TaskItemKeyProvider(adapter),
            TaskItemDetailsLookup(this),
            StorageStrategy.createLongStorage()
        ).withSelectionPredicate(
            SelectionPredicates.createSelectAnything()
        ).build()
        taskSelectionTracker.addObserver(
            object : SelectionObserver<Long>() {
                override fun onSelectionChanged() {
                    super.onSelectionChanged()
                    onSelectedOrdersChanged(taskSelectionTracker.selection)
                }
            })
        adapter.tracker = taskSelectionTracker
    }

    private fun initBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.taskListFragmentBottomSheet.root)
        initShiftButton()
        initLogoutButton()
        onSelectedOrdersChanged(MutableSelection())
    }

    private fun initLogoutButton() = with(binding.taskListFragmentBottomSheet.taskListLogoutButton) {
        setOnClickListener { viewModel.logout() }
    }

    private fun initShiftButton() = with(binding.taskListFragmentBottomSheet.taskListShiftStateButton) {
        viewModel.data.online.observe(viewLifecycleOwner, { isUserOnline ->
            setText(if (isUserOnline) R.string.end_shift else R.string.start_shift)
            setOnClickListener {
                taskSelectionTracker.clearSelection()
                if (isUserOnline) {
                    viewModel.endShift()
                } else {
                    if (PermissionChecker.checkSelfPermission(it.context, Manifest.permission.ACCESS_FINE_LOCATION) != PermissionChecker.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), START_SHIFT_WITH_LOCATION_PERMISSION_REQUEST_CODE)
                        return@setOnClickListener
                    }
                    viewModel.startShift()
                }
            }
        })
    }

    private fun onSelectedOrdersChanged(selectedOrderIds: Selection<Long>) = with(binding.taskListFragmentBottomSheet) {
        Log.i(TAG, "selected items=$selectedOrderIds")
        if (selectedOrderIds.isEmpty) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        } else {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        refreshCancelButton(selectedOrderIds)
        refreshGroupButton(selectedOrderIds)
        refreshUnGroupButton(selectedOrderIds)
    }

    private fun refreshCancelButton(selectedOrderIds: Selection<Long>) = with(binding.taskListFragmentBottomSheet.btnCancelOrder) {
        isEnabled = !selectedOrderIds.isEmpty
        text = resources.getQuantityString(plurals.cancel_task_button, selectedOrderIds.size(), selectedOrderIds.size())
        setOnClickListener {
            selectedOrderIds.forEach {
                taskSelectionTracker.deselect(it)
                viewModel.cancelTask(it, "reason to cancel", "custom reason", object : ResultCallback<TaskCancelResult> {
                    override fun onResult(result: TaskCancelResult) {
                        Log.i(TAG, "cancel result=$result")
                    }
                })
            }
        }
    }

    private fun refreshGroupButton(selectedOrderIds: Selection<Long>) = with(binding.taskListFragmentBottomSheet.btnGroupOrders) {
        isEnabled = selectedOrderIds.size() > 1
        text = resources.getQuantityString(plurals.merge_orders_button, selectedOrderIds.size(), selectedOrderIds.size())
        if (isEnabled) {
            val taskIds = selectedOrderIds.toList()
            setOnClickListener {
                taskIds.forEach {
                    taskSelectionTracker.deselect(it)
                }
                viewModel.createGroup(taskIds).observe(viewLifecycleOwner) {
                    Log.i(TAG, "got create group result for order ids=$taskIds, result=$it")
                    val text = when (it) {
                        is CreateGroupTaskResult.Success -> "Orders merged successfully, new order: ${it.task}"
                        is CreateGroupTaskResult.Error -> "Failed to merge orders, error=${it.error}"
                    }
                    Snackbar.make(this, text, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refreshUnGroupButton(selectedOrderIds: Selection<Long>) = with(binding.taskListFragmentBottomSheet.btnUngroupOrders) {
        val groupedTasks = viewModel.data.taskList.value?.filter { selectedOrderIds.contains(it.getId()) && it.groupUUID.isNotEmpty() } ?: emptyList()
        text = resources.getQuantityString(plurals.un_merge_orders_button, groupedTasks.size, groupedTasks.size)
        isEnabled = groupedTasks.isNotEmpty()
        if (isEnabled) {
            val taskIds = groupedTasks.map { it.getId() }.toList()
            setOnClickListener {
                taskIds.forEach { taskId ->
                    taskSelectionTracker.deselect(taskId)
                    viewModel.unGroup(taskId).observe(viewLifecycleOwner) {
                        Log.i(TAG, "got ungroup result for order ids=$taskIds, result=$it")
                        val text = when (it) {
                            is UnGroupTaskResult.Success -> "Order unmerged successfully, new orders: ${it.tasks}"
                            is UnGroupTaskResult.Error -> "Failed to unmerge orders, error=${it.error}"
                        }
                        Snackbar.make(this, text, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (START_SHIFT_WITH_LOCATION_PERMISSION_REQUEST_CODE == requestCode && grantResults[0] == PermissionChecker.PERMISSION_GRANTED) {
            viewModel.startShift()
        }
    }
}