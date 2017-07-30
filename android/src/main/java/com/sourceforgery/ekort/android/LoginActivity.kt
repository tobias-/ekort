package com.sourceforgery.ekort.android

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.sourceforgery.swedbank.*
import kotlinx.android.synthetic.main.activity_login.*
import okhttp3.logging.HttpLoggingInterceptor
import android.content.DialogInterface
import android.support.v7.app.AlertDialog


/**
 * A login screen that offers login via email/password.
 */
class LoginActivity : AppCompatActivity() {

    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private var mAuthTask: UserLoginTask? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        // Set up the login form.
        val sharedPref = getSharedPreferences(getString(R.string.preference_file_key), MODE_PRIVATE)
        val oldPersonNumber = sharedPref.getString(getString(R.string.person_number), "")
        if (oldPersonNumber != null) {
            personNumber.setText(oldPersonNumber)
        }
        personNumber.setOnEditorActionListener(TextView.OnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                attemptLogin()
                return@OnEditorActionListener true
            }
            false
        })
        signInButton.setOnClickListener { attemptLogin() }
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private fun attemptLogin() {
        if (mAuthTask != null) {
            return
        }

        // Reset errors.
        personNumber.error = null

        // Store values at the time of the login attempt.
        val personNumberStr = personNumber.text.toString()

        var cancel = false
        var focusView: View? = null

        // Check for a valid password, if the user entered one.
        if (TextUtils.isEmpty(personNumberStr)) {
            personNumber.error = getString(R.string.error_field_required)
            focusView = personNumber
            cancel = true
        } else if (!isPersonNumberValid(personNumberStr)) {
            personNumber.error = getString(R.string.error_invalid_person_number)
            focusView = personNumber
            cancel = true
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView?.requestFocus()
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true)
            val sharedPref = getSharedPreferences(getString(R.string.preference_file_key), MODE_PRIVATE)
            sharedPref.edit().putString(getString(R.string.person_number), personNumberStr).apply()
            mAuthTask = UserLoginTask(personNumberStr)
            mAuthTask!!.execute(null as Void?)
        }
    }

    companion object {
        private val PERSON_NUMBER_REGEX = Regex("(19|20)?([0-9]{6})-?([0-9]{4})")
    }

    private fun isPersonNumberValid(personNumber: String): Boolean {
        return PERSON_NUMBER_REGEX.matchEntire(personNumber) != null
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    private fun showProgress(show: Boolean) {
        val shortAnimTime = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()

        login_form.visibility = if (show) View.GONE else View.VISIBLE
        login_form.animate()
                .setDuration(shortAnimTime)
                .alpha((if (show) 0 else 1).toFloat())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        login_form.visibility = if (show) View.GONE else View.VISIBLE
                    }
                })

        login_progress.visibility = if (show) View.VISIBLE else View.GONE
        login_progress.animate()
                .setDuration(shortAnimTime)
                .alpha((if (show) 1 else 0).toFloat())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        login_progress.visibility = if (show) View.VISIBLE else View.GONE
                    }
                })
    }

    fun updateProgress(status: LoginStatus) {
        val progressText = getString(when (status) {
            LoginStatus.NOT_STARTED -> R.string.login_NOT_STARTED
            LoginStatus.INIT_EKORT_COMPLETE -> R.string.login_INIT_EKORT_COMPLETE
            LoginStatus.STARTED -> R.string.login_STARTED
            LoginStatus.PORTAL_INIT_COMPLETE -> R.string.login_PORTAL_INIT_COMPLETE
            LoginStatus.LOGIN_1_COMPLETE -> R.string.login_LOGIN_1_COMPLETE
            LoginStatus.LOGIN_2_COMPLETE -> R.string.login_LOGIN_2_COMPLETE
            LoginStatus.ERROR -> R.string.login_ERROR
            LoginStatus.STARTED_POLLING -> R.string.login_STARTED_POLLING
            LoginStatus.POLL_COMPLETE -> R.string.login_POLL_COMPLETE
            LoginStatus.PRE_CLIENT_COMPLETE -> R.string.login_PRE_CLIENT_COMPLETE
            LoginStatus.LOGIN_3_COMPLETE -> R.string.login_LOGIN_3_COMPLETE
            LoginStatus.SELECT_ISSUER_STARTED -> R.string.login_SELECT_ISSUER_STARTED
            LoginStatus.SELECT_ISSUER_COMPLETE -> R.string.login_SELECT_ISSUER_COMPLETE
        })
        login_progress_text.text = progressText
    }


    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    @SuppressLint("StaticFieldLeak")
    inner class UserLoginTask internal constructor(private val personNumber: String) : AsyncTask<Void, LoginStatus, List<ECardClient.Account>?>() {

        var error: String? = null

        override fun doInBackground(vararg params: Void): List<ECardClient.Account>? {
            try {
                debugLevel = HttpLoggingInterceptor.Level.NONE
                logger = object : HttpLoggingInterceptor.Logger {
                    override fun log(message: String?) {
                        Log.d("OkHttp3", message)
                    }
                }
                val eCardClient = ECardClient(personNumber)
                eCardClient.statusUpdatedListener = object : StatusUpdateListener {
                    override fun statusUpdated(status: LoginStatus) {
                        publishProgress(status)
                    }
                }
                eCardClient.loginWithoutPoll()
                openBankIdApp()
                return eCardClient.pollAndGetAccounts()
            } catch (e: InterruptedException) {
                return null
            } catch (e: Exception) {
                error = e.message
                return null
            }
        }

        override fun onProgressUpdate(vararg values: LoginStatus) {
            updateProgress(values.last())
        }

        private fun openBankIdApp() {
            val intent = Intent()
            intent.`package` = "com.bankid.bus"
            intent.action = Intent.ACTION_VIEW
            intent.type = "bankid"
            intent.data = Uri.parse("bankid://www.bankid.com?redirect=null")
            startActivityForResult(intent, 0)
        }

        override fun onPostExecute(success: List<ECardClient.Account>?) {

            if (success != null) {
                val accounts = success.map { "${it.personNumber} ${it.bankName} ${it.name}" }.toTypedArray()

                val builder = AlertDialog.Builder(this@LoginActivity)
                builder.setTitle(getString(R.string.pick_issuer))
                builder.setCancelable(false)
                builder.setItems(accounts, { dialog, which ->
                    showProgress(false)
                    AccountSelectTask().execute(success[which])
                })
                builder.show()
            } else {
                showProgress(false)
                this@LoginActivity.personNumber.error = error
                this@LoginActivity.personNumber.requestFocus()
            }
            mAuthTask = null
        }

        override fun onCancelled() {
            mAuthTask = null
            showProgress(false)
        }
    }

    @SuppressLint("StaticFieldLeak")
    inner class AccountSelectTask internal constructor() : AsyncTask<ECardClient.Account, Void, ECardAPI?>() {
        private var error: String? = null

        override fun doInBackground(vararg arg: ECardClient.Account?): ECardAPI? {
            try {
                return arg[0]!!.selectIssuer()
            } catch (e: Exception) {
                this.error = e.message
                return null
            }
        }

        override fun onPostExecute(success: ECardAPI?) {
            if (success != null) {
                val intent = Intent(this@LoginActivity, EkortActivity::class.java)
                intent.putExtra("webServletUrl", success.webServletUrl.toString())
                intent.putExtra("cookies", success.serializeCookies())
                startActivity(intent)
                finish()
            } else {
                this@LoginActivity.personNumber.error = error
                this@LoginActivity.personNumber.requestFocus()
            }
        }
    }
}
