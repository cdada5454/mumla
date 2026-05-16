package se.lublin.mumla.app

import android.app.Activity
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode.OK
import com.android.billingclient.api.BillingClient.ProductType.INAPP
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState.PENDING
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit.DAYS
import se.lublin.mumla.R

class StartupAction : IStartupAction {
    private var billingClient: BillingClient? = null

    private fun showToast(activity: Activity, text: String) {
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                return@runOnUiThread
            }
            Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
        }
    }

    override fun execute(activity: Activity) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)

        val oldStartupCount = preferences.getInt(PREF_STARTUP_COUNT, 0)
        val startupCount = if (oldStartupCount == Int.MAX_VALUE) 1 else oldStartupCount + 1
        preferences.edit().putInt(PREF_STARTUP_COUNT, startupCount).apply()

        if (DialogUtils.maybeShowNewsDialog(activity)) {
            return
        }

        if (preferences.getBoolean(PREF_HAS_DONATED, false)) {
            val lastVerification = preferences.getLong(PREF_LAST_DONATION_VERIFY_TIMESTAMP, 0)
            if (System.currentTimeMillis() - lastVerification > VERIFY_INTERVAL) {
                verifyPurchase(activity, preferences)
            }
            return
        }

        billingClient = BillingClient.newBuilder(activity)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .setListener { result, purchases ->
                if (result.responseCode != OK || purchases == null) {
                    return@setListener
                }
                for (purchase in purchases) {
                    if (purchase.products.contains(DONATION_PRODUCT_ID)) {
                        handleAndAckPurchase(activity, purchase) {
                            preferences.edit().putBoolean(PREF_HAS_DONATED, true).apply()
                            showToast(activity, activity.getString(R.string.donate_thanks_goog))
                        }
                    }
                }
            }
            .build()

        billingClient!!.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != OK) {
                    showToast(
                        activity,
                        String.format("Failed to setup billing: %s (code %d)", result.debugMessage, result.responseCode),
                    )
                    return
                }

                val params = QueryPurchasesParams.newBuilder().setProductType(INAPP).build()
                billingClient!!.queryPurchasesAsync(params) { queryResult, purchases ->
                    if (queryResult.responseCode != OK) {
                        showToast(
                            activity,
                            String.format(
                                "Failed to query purchases: %s (code %d)",
                                queryResult.debugMessage,
                                queryResult.responseCode,
                            ),
                        )
                        return@queryPurchasesAsync
                    }
                    var foundAndHandled = false
                    for (purchase in purchases) {
                        if (purchase.products.contains(DONATION_PRODUCT_ID)) {
                            handleAndAckPurchase(activity, purchase) {
                                preferences.edit().putBoolean(PREF_HAS_DONATED, true).apply()
                                showToast(activity, activity.getString(R.string.donate_thanks_goog))
                            }
                            foundAndHandled = true
                            break
                        }
                    }
                    if (!foundAndHandled && (startupCount % 5 == 1 || startupCount % 5 == 3)) {
                        showDonationDialog(activity)
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
            }
        })
    }

    private fun verifyPurchase(activity: Activity, preferences: SharedPreferences) {
        val client = BillingClient.newBuilder(activity)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .setListener { _, _ -> }
            .build()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != OK) {
                    return
                }
                client.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(INAPP).build(),
                ) { queryResult, purchases ->
                    if (queryResult.responseCode != OK) {
                        return@queryPurchasesAsync
                    }
                    var stillFound = false
                    for (purchase in purchases) {
                        if (purchase.products.contains(DONATION_PRODUCT_ID) && purchase.isAcknowledged) {
                            stillFound = true
                            break
                        }
                    }
                    if (!stillFound) {
                        showToast(activity, "Your donation was refunded or revoked")
                        preferences.edit().putBoolean(PREF_HAS_DONATED, false).apply()
                    }
                    preferences.edit().putLong(PREF_LAST_DONATION_VERIFY_TIMESTAMP, System.currentTimeMillis()).apply()
                    client.endConnection()
                }
            }

            override fun onBillingServiceDisconnected() {
            }
        })
    }

    private fun showDonationDialog(activity: Activity) {
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                return@runOnUiThread
            }
            val icons = intArrayOf(
                R.drawable.ic_donate_heart_goog,
                R.drawable.ic_donate_tag_faces_goog,
                R.drawable.ic_donate_handshake_goog,
                R.drawable.ic_donate_heart_smile_goog,
                R.drawable.ic_donate_waving_hand_goog,
            )
            val randomIconRes = icons[ThreadLocalRandom.current().nextInt(icons.size)]
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.donate_dialog_title_goog)
                .setMessage(R.string.donate_dialog_message_goog)
                .setIcon(randomIconRes)
                .setCancelable(false)
                .setPositiveButton(R.string.donate_dialog_positivebutton_goog) { _, _ -> launchPurchaseFlow(activity) }
                .setNegativeButton(R.string.donate_dialog_negativebutton_goog) { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    private fun launchPurchaseFlow(activity: Activity) {
        val client = billingClient
        if (client == null || !client.isReady) {
            showToast(activity, "Billing client is not ready")
            return
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(DONATION_PRODUCT_ID)
                        .setProductType(INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { queryResult, productDetails ->
            if (queryResult.responseCode != OK || productDetails.isEmpty()) {
                showToast(
                    activity,
                    String.format(
                        "Failed to query product details: %s (code %d)",
                        queryResult.debugMessage,
                        queryResult.responseCode,
                    ),
                )
                return@queryProductDetailsAsync
            }
            activity.runOnUiThread {
                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails[0])
                                .build(),
                        ),
                    )
                    .build()
                client.launchBillingFlow(activity, flowParams)
            }
        }
    }

    private fun handleAndAckPurchase(activity: Activity, purchase: Purchase, onAckSuccess: Runnable) {
        if (purchase.isAcknowledged) {
            onAckSuccess.run()
            return
        }

        if (purchase.purchaseState == PENDING) {
            showToast(activity, activity.getString(R.string.donate_purchase_pending_goog))
            return
        }

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient!!.acknowledgePurchase(params) { result ->
            if (result.responseCode != OK) {
                showToast(
                    activity,
                    String.format(
                        "Failed to acknowledge purchase: %s (code %d)",
                        result.debugMessage,
                        result.responseCode,
                    ),
                )
                return@acknowledgePurchase
            }
            onAckSuccess.run()
        }
    }

    companion object {
        private const val DONATION_PRODUCT_ID = "mumla_donation_1"
        private const val PREF_STARTUP_COUNT = "startupCount"
        private const val PREF_HAS_DONATED = "hasDonated"
        private const val PREF_LAST_DONATION_VERIFY_TIMESTAMP = "lastDonationVerifyTimestamp"
        private val VERIFY_INTERVAL = DAYS.toMillis(2)
    }
}
