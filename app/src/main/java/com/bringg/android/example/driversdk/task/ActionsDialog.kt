package com.bringg.android.example.driversdk.task

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.bringg.android.example.driversdk.BringgSdkViewModel
import com.bringg.android.example.driversdk.R
import driver_sdk.content.ResultCallback
import driver_sdk.driver.model.result.NoteResult
import driver_sdk.logging.BringgLog
import driver_sdk.models.enums.ImageType
import java.util.UUID
import kotlinx.android.synthetic.main.fragment_task_actions_dialog.*
import kotlinx.android.synthetic.main.list_item_task_action.view.*
import org.json.JSONArray
import org.json.JSONObject

class ActionsDialog : DialogFragment() {

    val viewModel: BringgSdkViewModel by activityViewModels()
    private val args: ActionsDialogArgs by navArgs()
    private val TAG = "ActionsDialog"

    private val SUBMIT_NOTE = "Submit Note"
    private val SUBMIT_IMAGE = "Submit Image"
    private val SUBMIT_FORM = "Submit Form"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_task_actions_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rv_actions.adapter = TaskActionsAdapter(
            listOf(SUBMIT_NOTE, SUBMIT_IMAGE, SUBMIT_FORM)
        ) {
            clickTaskAction(it)
        }
    }

    private fun clickTaskAction(it: View) {
        val waypointId = viewModel.data.task(args.taskId).value?.currentWayPointId
        if (waypointId == null) {
            BringgLog.error(TAG, "Can't submit action, waypoint is null")
            return
        }
        when (it.txt_action_name.text) {
            SUBMIT_NOTE -> submitNote(waypointId)
            SUBMIT_IMAGE -> submitImage(waypointId)
            SUBMIT_FORM -> submitForm(waypointId)
        }
    }

    private fun submitNote(waypointId: Long) {
        viewModel.submitNote(args.taskId, waypointId, text = "my note",
            callback = object : ResultCallback<NoteResult> {
                override fun onResult(result: NoteResult) {
                    if (result.success) {
                        Toast.makeText(context, "Note submitted successfully", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, result.error!!.name, Toast.LENGTH_LONG).show()
                    }
                }
            })
    }

    private fun submitImage(waypointId: Long) {
        viewModel.submitImage(args.taskId, waypointId, imageType = ImageType.PHOTO,
            bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565),
            imageDeletionUri = null,
            callback = object : ResultCallback<NoteResult> {
                override fun onResult(result: NoteResult) {
                    if (result.success) {
                        Toast.makeText(context, "Image submitted successfully", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, result.error!!.name, Toast.LENGTH_LONG).show()
                    }
                }
            })
    }

    private fun submitForm(waypointId: Long) {
        val jsonArray = JSONArray()
        for (i in 0 until 10) {
            jsonArray.put(UUID.randomUUID().toString())
        }
        val jsonObject = JSONObject().apply {
            put("string_value", UUID.randomUUID().toString())
            put("boolean_value", true)
            put("another_boolean", false)
            put("int", Int.MAX_VALUE)
            put("long", Long.MAX_VALUE)
            put("float", Float.MAX_VALUE)
            put("double", Double.MAX_VALUE)
            put("array", listOf(1, 2, 3))
            put("another_array", listOf("a", "b", "c"))
            put("json_array", jsonArray)
            put("some_other_value", args)
        }
        viewModel.submitForm(waypointId, jsonObject).observe(viewLifecycleOwner) {
            Toast.makeText(context, "Submit json result=$it", Toast.LENGTH_LONG).show()
        }
    }

    class TaskActionsAdapter(private val actions: List<String>, private val itemClickListener: View.OnClickListener) : RecyclerView.Adapter<TaskActionViewHolder>(
    ) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            TaskActionViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_task_action, parent, false), itemClickListener)

        override fun getItemCount() = actions.size

        override fun onBindViewHolder(holder: TaskActionViewHolder, position: Int) = holder.bind(actions[position])

    }

    class TaskActionViewHolder(itemView: View, itemClickListener: View.OnClickListener) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.setOnClickListener(itemClickListener)
        }

        fun bind(taskActionItem: String) {
            itemView.txt_action_name.text = taskActionItem
        }
    }
}