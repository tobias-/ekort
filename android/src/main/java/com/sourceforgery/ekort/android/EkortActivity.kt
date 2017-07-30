package com.sourceforgery.ekort.android

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.os.AsyncTask
import android.os.Bundle
import android.os.PersistableBundle
import android.support.design.widget.NavigationView
import android.support.design.widget.Snackbar
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.sourceforgery.swedbank.ECardAPI
import com.sourceforgery.swedbank.ECardAPIMock
import com.sourceforgery.swedbank.PastTransaction
import kotlinx.android.synthetic.main.activity_ekort.*
import kotlinx.android.synthetic.main.app_bar_ekort.*
import kotlinx.android.synthetic.main.content_ekort.*
import kotlinx.android.synthetic.main.nav_header_ekort.*
import kotlinx.android.synthetic.main.past_transaction_list_item.*

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

        fab.setOnClickListener {
            Snackbar.make(it, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

        create_ecard.setOnClickListener {
            val transactionLimit = max_transaction_amount.text.toString().toInt()
            val cumulativeLimit = max_total_amount.toString().toInt()
            val validForMonths = months_validity.text.toString().toInt()
            eCardAPI!!.createCard(transactionLimit, cumulativeLimit, validForMonths)
        }

        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()
        nav_view.setNavigationItemSelectedListener(this)
    }

    override fun onPostCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        System.err.println("Fjksdffssdkljf000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000dfjkdslkjfsdjlfsdjklfsd")
        super.onPostCreate(savedInstanceState, persistentState)
        textViewBank.text = intent.getCharSequenceExtra("bankName") ?: "Swedbank"
        textViewBirthday.text = intent.getCharSequenceExtra("birthday") ?: "450524"
        loadTransactions()
    }

    private fun loadTransactions() {
        System.err.println("aaaaaaaaaaaaaaaFjksdffsdfjkdslkjfsdjlfsdjklfsd")
        showProgress(ekort_progress)
        LoadTransactions().execute()
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun showProgress(viewToBeShown: View) {
        val shortAnimTime = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()

        create_ecard_form.visibility = if (viewToBeShown != create_ecard_form) View.GONE else View.VISIBLE
        create_ecard_form.animate()
                .setDuration(shortAnimTime)
                .alpha((if (viewToBeShown != create_ecard_form) 0 else 1).toFloat())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        create_ecard_form.visibility = if (viewToBeShown != create_ecard_form) View.GONE else View.VISIBLE
                    }
                })
        table.visibility = if (viewToBeShown != table) View.GONE else View.VISIBLE
        table.animate()
                .setDuration(shortAnimTime)
                .alpha((if (viewToBeShown != table) 0 else 1).toFloat())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        table.visibility = if (viewToBeShown != table) View.GONE else View.VISIBLE
                    }
                })

        ekort_progress.visibility = if (viewToBeShown == ekort_progress) View.VISIBLE else View.GONE
        ekort_progress.animate()
                .setDuration(shortAnimTime)
                .alpha((if (viewToBeShown == ekort_progress) 1 else 0).toFloat())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        ekort_progress.visibility = if (viewToBeShown == ekort_progress) View.VISIBLE else View.GONE
                    }
                })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.ekort, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
            R.id.action_settings -> return true
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_past_transactions -> {

            }
            R.id.nav_active_cards -> {

            }
            R.id.nav_create_card -> {
                showProgress(create_ecard_form)
            }
        }
        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    @SuppressLint("StaticFieldLeak")
    inner class LoadTransactions : AsyncTask<Void, Void, List<PastTransaction>?>() {

        override fun doInBackground(vararg p0: Void?): List<PastTransaction>? = eCardAPI!!.getPastTransactions(0)


        override fun onPostExecute(result: List<PastTransaction>?) {
            showProgress(table)
            if (result != null) {
                table.adapter = PastTransactionsAdapter(layoutInflater, result)
                table.layoutManager = LinearLayoutManager(this@EkortActivity)
            } else {
                throw RuntimeException("What happened here?")
            }
        }
    }

}
