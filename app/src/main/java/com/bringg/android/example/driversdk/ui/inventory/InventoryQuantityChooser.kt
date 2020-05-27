package com.bringg.android.example.driversdk.ui.inventory

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.bringg.android.example.driversdk.R
import driver_sdk.models.Inventory
import kotlinx.android.synthetic.main.inventory_quantity_chooser.view.*

class InventoryQuantityChooser @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var inventory: Inventory
    private var btnAcceptAllChecked: Button
    private var btnRejectAllChecked: Button
    private var btnPartialChecked: Button

    init {
        val root = View.inflate(context, R.layout.inventory_quantity_chooser, this)
        btnAcceptAllChecked = root.findViewById(R.id.btn_accept_all)
        btnRejectAllChecked = root.findViewById(R.id.btn_reject_all)
        btnPartialChecked = root.findViewById(R.id.btn_partial)

        btnAcceptAllChecked.setOnClickListener {
            checkAcceptAll()
//            DriverSdkProvider.driverSdk().inventory.acceptAll(inventory.id)
        }
        btnRejectAllChecked.setOnClickListener {
            checkRejectAll()
//            DriverSdkProvider.driverSdk().inventory.rejectAll(inventory.id)
        }
        btnPartialChecked.setOnClickListener {
            checkPartial()
            val qty = txt_partial.editText?.text.toString().toIntOrNull()
            if (qty == null) {
                txt_partial.error = "invalid quantity"
                return@setOnClickListener
            }
            txt_partial.error = null
//            DriverSdkProvider.driverSdk().inventory.accept(inventory.id, qty)
        }
    }

    private fun checkAcceptAll() {
        markAsChecked(btnAcceptAllChecked)
        markAsUnchecked(btnRejectAllChecked)
    }

    private fun checkRejectAll() {
        markAsChecked(btnRejectAllChecked)
        markAsUnchecked(btnAcceptAllChecked)
    }

    private fun checkPartial() {
        markAsUnchecked(btnRejectAllChecked)
        markAsUnchecked(btnAcceptAllChecked)
    }

    private fun markAsUnchecked(uncheckedButton: Button) {
        uncheckedButton.isEnabled = true
    }

    private fun markAsChecked(checkedButton: Button) {
        checkedButton.isEnabled = false
    }

    fun setItem(item: Inventory) {
        inventory = item
        when {
            item.isFullyAccepted -> {
                checkAcceptAll()
            }
            item.isFullyRejected -> {
                checkRejectAll()
            }
            else -> {
                checkPartial()
            }
        }
    }

    companion object {
        const val TAG = "QuantityChooser"
    }
}