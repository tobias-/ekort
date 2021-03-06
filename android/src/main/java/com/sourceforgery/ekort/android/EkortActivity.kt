package com.sourceforgery.ekort.android

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.os.AsyncTask
import android.os.Bundle
import android.os.PersistableBundle
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.sourceforgery.swedbank.ActiveECard
import com.sourceforgery.swedbank.ECardAPI
import com.sourceforgery.swedbank.ECardAPIMock
import com.sourceforgery.swedbank.PastTransaction
import kotlinx.android.synthetic.main.active_cards_layout.*
import kotlinx.android.synthetic.main.activity_ekort.*
import kotlinx.android.synthetic.main.app_bar_ekort.*
import kotlinx.android.synthetic.main.content_ekort.*
import kotlinx.android.synthetic.main.create_ecard_layout.*
import kotlinx.android.synthetic.main.nav_header_ekort.*
import kotlinx.android.synthetic.main.past_transaction_layout.*

class EkortActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private var eCardAPI: ECardAPI? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ekort)
        setSupportActionBar(toolbar)
        val sessionState = intent.getStringExtra("sessionState")
        if (sessionState != null) {
            eCardAPI = ECardAPI.unpack(sessionState)
        } else {
            Toast.makeText(this, getString(R.string.entering_mock_mode), Toast.LENGTH_SHORT).show()
            eCardAPI = ECardAPIMock()
        }

        create_ecard.setOnClickListener {
            val transactionLimit = max_transaction_amount.text.toString().toInt()
            val cumulativeLimit = max_total_amount.text.toString().toInt()
            val validForMonths = months_validity.text.toString().toInt()
            CreateECard(transactionLimit, cumulativeLimit, validForMonths).execute()
        }

        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()
        nav_view.setNavigationItemSelectedListener(this)
    }

    override fun onPostCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onPostCreate(savedInstanceState, persistentState)
        textViewBank.text = intent.getCharSequenceExtra("bankName") ?: "Swedbank"
        textViewBirthday.text = intent.getCharSequenceExtra("birthday") ?: "450524"
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun showScreen(viewToBeShown: View) {

        fixVisibility(create_ecard_form, viewToBeShown, R.string.create_card_title)
        fixVisibility(past_transactions, viewToBeShown, R.string.past_transactions_title)
        fixVisibility(ekort_progress, viewToBeShown, R.string.app_title)
        fixVisibility(active_ecards, viewToBeShown, R.string.active_ecards_title)
    }

    private fun fixVisibility(thisView: View, viewToBeShown: View, title: Int) {
        val show = viewToBeShown == thisView
        val alreadyShown = thisView.visibility == View.VISIBLE
        if (alreadyShown != show) {
            val shortAnimTime = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
            thisView.visibility = if (show) View.VISIBLE else View.GONE
            thisView.animate()
                    .setDuration(shortAnimTime)
                    .alpha((if (show) 1 else 0).toFloat())
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            thisView.visibility = if (show) View.VISIBLE else View.GONE
                        }
                    })
            if (show) {
                setTitle(title)
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_past_transactions -> {
                LoadPastTransactions().execute()
            }
            R.id.nav_active_cards -> {
                LoadActiveECards().execute()
            }
            R.id.nav_create_card -> {
                showScreen(create_ecard_form)
            }
        }
        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    @SuppressLint("StaticFieldLeak")
    inner class LoadPastTransactions : AsyncTask<Void, Void, List<PastTransaction>?>() {

        override fun onPreExecute() {
            showScreen(ekort_progress)
        }

        override fun doInBackground(vararg p0: Void?): List<PastTransaction>? = eCardAPI!!.getPastTransactions(0)


        override fun onPostExecute(result: List<PastTransaction>?) {
            if (result != null) {
                past_transactions_table.adapter = PastTransactionsAdapter(layoutInflater, result)
                past_transactions_table.layoutManager = LinearLayoutManager(this@EkortActivity)
                showScreen(past_transactions)
            } else {
                throw RuntimeException("What happened here?")
            }
        }
    }


    @SuppressLint("StaticFieldLeak")
    inner class LoadActiveECards : AsyncTask<Void, Void, List<ActiveECard>?>() {

        override fun onPreExecute() {
            showScreen(ekort_progress)
        }

        override fun doInBackground(vararg p0: Void?): List<ActiveECard> = eCardAPI!!.getActiveECards(0)


        override fun onPostExecute(result: List<ActiveECard>?) {
            if (result != null) {
                showScreen(active_ecards)
                active_cards_table.adapter = ActiveECardsAdapter(result, this@EkortActivity)
                active_cards_table.layoutManager = LinearLayoutManager(this@EkortActivity)
            } else {
                throw RuntimeException("What happened here?")
            }
        }
    }



    @SuppressLint("StaticFieldLeak")
    inner class CreateECard(val transactionLimit: Int, val cumulativeLimit: Int, val validForMonths: Int) : AsyncTask<Void, Void, List<ActiveECard>?>() {

        override fun onPreExecute() {
            showScreen(ekort_progress)
        }

        override fun doInBackground(vararg p0: Void?): List<ActiveECard> {
            eCardAPI!!.createCard(transactionLimit, cumulativeLimit, validForMonths)
            return eCardAPI!!.getActiveECards(0)
        }


        override fun onPostExecute(result: List<ActiveECard>?) {
            if (result != null) {
                showScreen(active_ecards)
                active_cards_table.adapter = ActiveECardsAdapter(result, this@EkortActivity)
                active_cards_table.layoutManager = LinearLayoutManager(this@EkortActivity)
            } else {
                throw RuntimeException("What happened here?")
            }
        }
    }
}
