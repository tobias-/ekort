package com.sourceforgery.ekort.android

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.sourceforgery.swedbank.ActiveECard
import android.R.attr.label
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast


class ActiveECardsAdapter(private val activeECards: List<ActiveECard>, private val context: Context) : RecyclerView.Adapter<ActiveECardsViewHolder>() {
    private val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun onBindViewHolder(holder: ActiveECardsViewHolder, position: Int) {
        holder.setFrom(activeECards[position], context)
    }

    override fun getItemCount(): Int {
        return activeECards.size
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ActiveECardsViewHolder {
        return ActiveECardsViewHolder(layoutInflater.inflate(R.layout.active_cards_list_item, parent, false))
    }
}

class ActiveECardsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val expiryMmYy = itemView.findViewById<TextView>(R.id.expiry_mmyy)
    private val pan = itemView.findViewById<Button>(R.id.pan)
    private val cvv = itemView.findViewById<TextView>(R.id.cvv)
    private val cumulativeLimit = itemView.findViewById<TextView>(R.id.cumulative_amount)
    private val openToBuy = itemView.findViewById<TextView>(R.id.open_amount)

    fun setFrom(activeECard: ActiveECard, context: Context) {
        expiryMmYy.text = activeECard.expiry
        pan.text = activeECard.creditCardNumber.substring(activeECard.creditCardNumber.length - 4)
        pan.setOnClickListener {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("CreditCard Number", activeECard.creditCardNumber)
            clipboardManager.setPrimaryClip(clip)
            Toast.makeText(context, R.string.cc_copied_to_clipboard, Toast.LENGTH_LONG).show()
        }
        cvv.text = activeECard.cvv
        cumulativeLimit.text = activeECard.prettyAmount(activeECard.cumulativeLimit)
        openToBuy.text = activeECard.prettyAmount(activeECard.openToBuy)
    }
}