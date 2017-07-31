package com.sourceforgery.ekort.android

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.sourceforgery.swedbank.PastTransaction

class PastTransactionsAdapter(private val viewInflater: LayoutInflater, private val pastTransactions: List<PastTransaction>) : RecyclerView.Adapter<PastTransactionsViewHolder>() {


    override fun onBindViewHolder(holder: PastTransactionsViewHolder, position: Int) {
        holder.setFrom(pastTransactions[position])
    }

    override fun getItemCount(): Int {
        return pastTransactions.size
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): PastTransactionsViewHolder {
        return PastTransactionsViewHolder(viewInflater.inflate(R.layout.past_transaction_list_item, null))
    }
}

class PastTransactionsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val transactionDate = itemView.findViewById<TextView>(R.id.transaction_date)
    private val merchantName = itemView.findViewById<TextView>(R.id.merchant_name)
    private val transactionAmount = itemView.findViewById<TextView>(R.id.transaction_amount)
    private val transactionLimit= itemView.findViewById<TextView>(R.id.transaction_limit)
    private val issueDate = itemView.findViewById<TextView>(R.id.issue_date)

    fun setFrom(pastTransaction: PastTransaction) {
        transactionDate.text = pastTransaction.transactionDate
        merchantName.text = pastTransaction.merchantCity
        transactionAmount.text = pastTransaction.transactionAmount
        transactionLimit.text = pastTransaction.transactionLimit
        issueDate.text = pastTransaction.issueDate
    }
}