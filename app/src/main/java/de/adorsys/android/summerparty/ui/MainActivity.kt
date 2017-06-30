package de.adorsys.android.summerparty.ui

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.design.widget.TabLayout
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.widget.Toast
import de.adorsys.android.summerparty.R
import de.adorsys.android.summerparty.data.ApiManager
import de.adorsys.android.summerparty.data.Cocktail
import de.adorsys.android.summerparty.data.Customer
import de.adorsys.android.summerparty.data.Order
import de.adorsys.android.summerparty.data.mock.UserFactory
import de.adorsys.android.summerparty.data.mutable.MutableOrder
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

class MainActivity : AppCompatActivity(), CocktailFragment.OnListFragmentInteractionListener {
    // TODO get real user from login instead of creating one
    private var user: Customer? = null
    private var viewPager: ViewPager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // init views
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        val tabLayout = findViewById(R.id.tabs) as TabLayout
        viewPager = findViewById(R.id.container) as ViewPager

        setSupportActionBar(toolbar)
        // Create the adapter that will return a fragment for each of the
        // primary sections of the activity.
        val sectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager)
        // Set up the ViewPager with the sections adapter.
        viewPager!!.adapter = sectionsPagerAdapter
        viewPager!!.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))
        tabLayout.addOnTabSelectedListener(TabLayout.ViewPagerOnTabSelectedListener(viewPager))

        // TODO create user via user login instead of using the mock content
        val customer = UserFactory.create()
        ApiManager.INSTANCE.createCustomer(customer,
                object : Callback<Customer> {
                    override fun onResponse(call: Call<Customer>?, response: Response<Customer>?) {
                        user = response?.body()
                        getOrdersForUser()
                    }

                    override fun onFailure(call: Call<Customer>?, t: Throwable?) {
                        Log.i("CUSTOMER", t?.message)
                    }
                })

        ApiManager.INSTANCE.getCocktails(
                object : Callback<List<Cocktail>> {
                    override fun onResponse(call: Call<List<Cocktail>>?, response: Response<List<Cocktail>>?) {
                        val cocktailResponse: List<Cocktail>? = response?.body()
                        // Update adapter's cocktail list
                        cocktailResponse?.let { (viewPager?.adapter as SectionsPagerAdapter).setCocktails(it) }
                        viewPager?.adapter?.notifyDataSetChanged()
                    }

                    override fun onFailure(call: Call<List<Cocktail>>?, t: Throwable?) {
                        Log.i("COCKTAILS", t?.message)
                    }
                })
    }

    private fun getOrdersForUser() {
        if (user != null) {
            ApiManager.INSTANCE.getOrdersForCustomer((user as Customer).id,
                    object : Callback<List<Order>> {
                        override fun onResponse(call: Call<List<Order>>?, response: Response<List<Order>>?) {
                            val cocktailResponse: List<Order>? = response?.body()
                            // Update adapter's order list
                            cocktailResponse?.let { (viewPager?.adapter as SectionsPagerAdapter).setOrders(it) }
                            viewPager?.adapter?.notifyDataSetChanged()
                        }

                        override fun onFailure(call: Call<List<Order>>?, t: Throwable?) {
                            Log.i("CUSTOMER_ORDERS", t?.message)
                        }
                    })
        }
    }

    override fun onListFragmentInteraction(item: Cocktail) {
        val viewContainer = findViewById(R.id.main_content)
        if (viewContainer != null && item.available) {
            val cocktail = ArrayList<String>(1)
            cocktail.add(item.id)

            val currentOrder = MutableOrder(cocktail, if (user == null) "" else (user as Customer).id)
            Snackbar.make(viewContainer, getString(R.string.order_cocktail, item.name), Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.action_cart) {
                        ApiManager.INSTANCE.createOrder(
                                currentOrder,
                                object : Callback<Void> {
                                    override fun onResponse(call: Call<Void>?, response: Response<Void>?) {
                                        if (response != null && response.isSuccessful) {
                                            Toast.makeText(this@MainActivity, "Successfully created order", Toast.LENGTH_SHORT).show()
                                            getOrdersForUser()
                                        }
                                    }

                                    override fun onFailure(call: Call<Void>?, t: Throwable?) {
                                        Log.i("ORDER_CREATE", t?.message)
                                    }
                                })
                    }
                    .show()
        } else if (viewContainer != null) {
            Snackbar.make(viewContainer, getString(R.string.cocktail_out_of_stock, item.name), Snackbar.LENGTH_LONG)
                    .show()
        }
    }
}
