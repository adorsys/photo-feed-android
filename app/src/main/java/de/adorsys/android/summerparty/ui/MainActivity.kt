package de.adorsys.android.summerparty.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.design.widget.TabLayout
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.view.MenuItemCompat
import android.support.v4.view.ViewPager
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.firebase.iid.FirebaseInstanceId
import de.adorsys.android.summerparty.R
import de.adorsys.android.summerparty.data.ApiManager
import de.adorsys.android.summerparty.data.Cocktail
import de.adorsys.android.summerparty.data.Customer
import de.adorsys.android.summerparty.data.Order
import de.adorsys.android.summerparty.data.mutable.MutableCustomer
import de.adorsys.android.summerparty.ui.adapter.SectionsPagerAdapter
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

class MainActivity : BaseActivity(), CocktailFragment.OnListFragmentInteractionListener {
    companion object {
        val KEY_USER_ID = "preferences_key_user_id"
        val KEY_USER_NAME = "preferences_key_user_name"
        val KEY_PREFS_FILENAME = "de.adorsys.android.summerparty.prefs"
        val KEY_FIREBASE_RECEIVER = "firebase_receiver"
        val KEY_FIREBASE_RELOAD = "reload"
        val KEY_FIREBASE_TOKEN = "firebase_token"
        val KEY_FIRST_START = "first_start"
        val REQUEST_CODE_CART = 23
        val REQUEST_CODE_NAME = 24
    }

    private var dialog: AlertDialog? = null
    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handleIntent(intent, true)
        }
    }

    private var user: Customer? = null
    private var viewPager: ViewPager? = null
    private var progressBar: View? = null
    private var viewContainer: View? = null
    private var menuItem: MenuItem? = null
    private var cartOptionsItemCount: TextView? = null
    private var preferences: SharedPreferences? = null
    private var pendingCocktails: ArrayList<Cocktail> = ArrayList()
    private var firebaseToken: String? = null


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // init views
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        val tabLayout = findViewById(R.id.tabs) as TabLayout
        progressBar = findViewById(R.id.progressBar)
        viewPager = findViewById(R.id.container) as ViewPager
        viewContainer = findViewById(R.id.main_content)
        preferences = getSharedPreferences(KEY_PREFS_FILENAME, Context.MODE_PRIVATE)

        setSupportActionBar(toolbar)
        // Create the adapter that will return a fragment for each of the
        // primary sections of the activity.
        val sectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager)
        // Set up the ViewPager with the sections adapter.
        viewPager!!.adapter = sectionsPagerAdapter
        viewPager!!.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))
        tabLayout.addOnTabSelectedListener(TabLayout.ViewPagerOnTabSelectedListener(viewPager))

        getCocktails()
        if (preferences!!.contains(KEY_USER_ID)) {
            getUser()
        } else if (firstStart()) {
            progressBar?.visibility = View.VISIBLE
            viewPager?.visibility = View.GONE
        }
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this).registerReceiver((messageReceiver),
                IntentFilter(KEY_FIREBASE_RECEIVER))
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menuItem = menu.findItem(R.id.action_cart)
        MenuItemCompat.setActionView(menuItem, R.layout.view_action_cart)
        val cartOptionsItemContainer = MenuItemCompat.getActionView(menuItem) as ViewGroup
        cartOptionsItemContainer.setOnClickListener {
            if (preferences!!.contains(KEY_USER_ID)) {
                val intent = Intent(this@MainActivity, CartActivity::class.java)
                intent.putExtra(CartActivity.EXTRA_COCKTAILS, pendingCocktails)
                intent.putExtra(CartActivity.EXTRA_USER_ID, user?.id)
                this@MainActivity.startActivityForResult(intent, REQUEST_CODE_CART)
            } else {
                firebaseToken = FirebaseInstanceId.getInstance().token
                startActivityForResult(Intent(this, CreateUserActivity::class.java), REQUEST_CODE_NAME)
            }
        }
        cartOptionsItemCount = cartOptionsItemContainer.findViewById(R.id.action_cart_count_text) as TextView
        updateCartMenuItem()
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_CART && resultCode == Activity.RESULT_OK) {
            pendingCocktails.clear()
            updateCartMenuItem()
            getOrdersForUser(true)
        }

        if (requestCode == REQUEST_CODE_NAME && resultCode == Activity.RESULT_OK && data != null) {
            createAndPersistUser(data.getStringExtra(CreateUserActivity.KEY_USERNAME), firebaseToken)
        } else if (requestCode == REQUEST_CODE_NAME && resultCode == Activity.RESULT_CANCELED) {
            preferences!!.edit().putBoolean(KEY_FIRST_START, true).apply()
            finish()
        }
    }

    override fun onListFragmentInteraction(item: Cocktail) {
        if (viewContainer != null && item.available) {
            pendingCocktails.add(item)
            updateCartMenuItem()
        } else if (viewContainer != null) {
            Snackbar.make(viewContainer!!, getString(R.string.cocktail_out_of_stock, item.name), Snackbar.LENGTH_LONG).show()
        }
    }


    private fun createAndPersistUser(username: String, firebaseToken: String?) {
        Log.d("TAG_USER", username)
        ApiManager.INSTANCE.createCustomer(MutableCustomer(username, firebaseToken),
                object : Callback<Customer> {
                    override fun onResponse(call: Call<Customer>?, response: Response<Customer>?) {
                        val customer = response?.body()
                        (preferences as SharedPreferences).edit().putString(MainActivity.KEY_USER_ID, customer?.id).apply()
                        (preferences as SharedPreferences).edit().putString(MainActivity.KEY_USER_NAME, customer?.name).apply()
                        user = customer
                    }

                    override fun onFailure(call: Call<Customer>?, t: Throwable?) {
                        Log.i("TAG_USER", t?.message)
                    }
                })
    }

    private fun getCocktails() {
        ApiManager.INSTANCE.getCocktails(
                object : Callback<List<Cocktail>> {
                    override fun onResponse(call: Call<List<Cocktail>>?, response: Response<List<Cocktail>>?) {
                        val cocktailResponse: List<Cocktail>? = response?.body()
                        // Update adapter's cocktail list
                        cocktailResponse?.let { (viewPager?.adapter as SectionsPagerAdapter).setCocktails(it) }
                        viewPager?.adapter?.notifyDataSetChanged()
                    }

                    override fun onFailure(call: Call<List<Cocktail>>?, t: Throwable?) {
                        Log.i("TAG_COCKTAILS", t?.message)
                    }
                })
    }

    private fun getUser() {
        // Update adapter's cocktail list
        ApiManager.INSTANCE.getCustomer(preferences!!.getString(KEY_USER_ID, null),
                object : Callback<Customer> {
                    override fun onResponse(call: Call<Customer>?, response: Response<Customer>?) {
                        user = response?.body()
                        if (user == null) {
                            // backend has hard-reset the database
                            (preferences as SharedPreferences).edit().clear().apply()
                            getUser()
                            return
                        }
                        (preferences as SharedPreferences).edit().putString(KEY_USER_NAME, this@MainActivity.user?.name).apply()
                        getOrdersForUser(false)
                    }

                    override fun onFailure(call: Call<Customer>?, t: Throwable?) {
                        Log.i("TAG_USER", t?.message)
                    }
                })
    }

    private fun getOrdersForUser(goToOrders: Boolean) {
        if (user == null) {
            return
        }
        ApiManager.INSTANCE.getOrdersForCustomer(user!!.id,
                object : Callback<List<Order>> {
                    override fun onResponse(call: Call<List<Order>>?, response: Response<List<Order>>?) {
                        val cocktailResponse: List<Order>? = response?.body()
                        // Update adapter's order list
                        cocktailResponse?.let { (viewPager?.adapter as SectionsPagerAdapter).setOrders(it) }
                        viewPager?.adapter?.notifyDataSetChanged()
                        if (goToOrders) { viewPager?.currentItem = 1 }
                    }

                    override fun onFailure(call: Call<List<Order>>?, t: Throwable?) {
                        Log.i("TAG_CUSTOMER_ORDERS", t?.message)
                    }
                })
    }

    private fun updateCartMenuItem() {
        menuItem?.isVisible = !pendingCocktails.isEmpty()
        cartOptionsItemCount?.text = pendingCocktails.size.toString()
    }

    private fun firstStart(): Boolean {
        if ((preferences as SharedPreferences).contains(KEY_FIRST_START)) {
            return false
        } else {
            preferences!!.edit().putBoolean(KEY_FIRST_START, true).apply()
            return true
        }
    }

    private fun handleIntent(intent: Intent?, showDialog: Boolean) {
        progressBar?.visibility = View.GONE
        viewPager?.visibility = View.VISIBLE
        if (intent == null) {
            return
        }
        if (intent.getBooleanExtra(KEY_FIREBASE_RELOAD, false)) {
            if (showDialog && (dialog == null || !(dialog as AlertDialog).isShowing)) {
                val dialogBuilder = AlertDialog.Builder(this@MainActivity)
                        .setIcon(R.drawable.ic_cocktail_icon)
                        .setTitle(R.string.notification_content_title)
                        .setMessage(R.string.notification_content_text)
                        .setPositiveButton(android.R.string.ok) { _, _ -> getOrdersForUser(true) }
                dialog = dialogBuilder.create()
                dialog?.show()
            } else if (!showDialog) {
                getOrdersForUser(true)
            }
        }
        if (intent.getStringExtra(KEY_FIREBASE_TOKEN) != null) {
            firebaseToken = intent.getStringExtra(KEY_FIREBASE_TOKEN)
            startActivityForResult(Intent(this@MainActivity, CreateUserActivity::class.java), REQUEST_CODE_NAME)
        }
    }
}
