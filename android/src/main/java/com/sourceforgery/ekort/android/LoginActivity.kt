package com.sourceforgery.ekort.android

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.sourceforgery.swedbank.ECardClient
import kotlinx.android.synthetic.main.activity_login.*
import okhttp3.logging.HttpLoggingInterceptor


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

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    @SuppressLint("StaticFieldLeak")
    inner class UserLoginTask internal constructor(private val personNumber: String) : AsyncTask<Void, Void, List<ECardClient.Account>?>() {

        var error: String? = null

        override fun doInBackground(vararg params: Void): List<ECardClient.Account>? {
            try {
                ECardClient.debugLevel = HttpLoggingInterceptor.Level.BODY
                ECardClient.logger = object : HttpLoggingInterceptor.Logger {
                    override fun log(message: String?) {
                        Log.d("OkHttp3", message)
                    }
                }
                return ECardClient.login(personNumber)
            } catch (e: InterruptedException) {
                return null
            } catch (e: Exception) {
                error = e.message
                return null
            }
        }

        override fun onPostExecute(success: List<ECardClient.Account>?) {
            mAuthTask = null
            showProgress(false)

            if (success != null) {
                finish()
            } else {
                this@LoginActivity.personNumber.error = error
                this@LoginActivity.personNumber.requestFocus()
            }
        }

        override fun onCancelled() {
            mAuthTask = null
            showProgress(false)
        }
    }
}
