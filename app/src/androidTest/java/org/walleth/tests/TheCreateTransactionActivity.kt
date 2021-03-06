package org.walleth.tests

import android.app.Activity.RESULT_OK
import android.app.Instrumentation
import android.content.Intent
import android.support.test.espresso.Espresso
import android.support.test.espresso.action.ViewActions
import android.support.test.espresso.action.ViewActions.click
import android.support.test.espresso.assertion.ViewAssertions
import android.support.test.espresso.intent.Intents.intending
import android.support.test.espresso.intent.matcher.IntentMatchers.hasComponent
import android.support.test.espresso.matcher.ViewMatchers
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.kethereum.erc681.generateURL
import org.kethereum.functions.getTokenTransferTo
import org.kethereum.functions.getTokenTransferValue
import org.kethereum.functions.isTokenTransfer
import org.kethereum.model.Address
import org.ligi.trulesk.TruleskIntentRule
import org.walleth.R
import org.walleth.activities.CreateTransactionActivity
import org.walleth.activities.qrscan.QRScanActivity
import org.walleth.data.balances.Balance
import org.walleth.data.tokens.Token
import org.walleth.data.tokens.TokenTransfer
import org.walleth.data.tokens.getEthTokenForChain
import org.walleth.data.tokens.toERC681
import org.walleth.functions.decimalsAsMultiplicator
import org.walleth.infrastructure.TestApp
import org.walleth.testdata.DEFAULT_TEST_ADDRESS2
import org.walleth.testdata.DEFAULT_TEST_ADDRESS3
import java.math.BigInteger

val testToken = Token("Test", "TEST", Address("0x01"), 15, TestApp.networkDefinitionProvider.getCurrent().chain, true, false, false, 1)
val eth = getEthTokenForChain(TestApp.networkDefinitionProvider.getCurrent())

class TheCreateTransactionActivity {

    @get:Rule
    var rule = TruleskIntentRule(CreateTransactionActivity::class.java, autoLaunch = false)

    @Before
    fun setup() {
        TestApp.testDatabase.transactions.deleteAll()
    }

    @Test
    fun rejectsEmptyAddress() {
        rule.launchActivity()
        Espresso.onView(ViewMatchers.withId(R.id.fab)).perform(ViewActions.closeSoftKeyboard(), ViewActions.click())

        Espresso.onView(ViewMatchers.withText(R.string.create_tx_error_address_must_be_specified)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        rule.screenShot("address_empty")
        Truth.assertThat(rule.activity.isFinishing).isFalse()
    }

    @Test
    fun rejectsDifferentChainId() {
        val chainIdForTransaction = TestApp.mySettings.chain + 1
        rule.launchActivity(Intent.getIntentOld("ethereum:0x12345@" + chainIdForTransaction))

        Espresso.onView(ViewMatchers.withText(R.string.wrong_network)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(ViewMatchers.withText(rule.activity.getString(R.string.please_switch_network, TestApp.networkDefinitionProvider.getCurrent().getNetworkName(), chainIdForTransaction)))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        rule.screenShot("chainId_not_valid")
        Truth.assertThat(rule.activity.isFinishing).isFalse()
    }

    @Test
    fun acceptsDifferentChainId() {
        val chainIdForTransaction = TestApp.networkDefinitionProvider.getCurrent().chain.id
        rule.launchActivity(Intent.getIntentOld("ethereum:0x12345@" + chainIdForTransaction))

        Espresso.onView(ViewMatchers.withText(R.string.wrong_network)).check(ViewAssertions.doesNotExist())
        Espresso.onView(ViewMatchers.withText(rule.activity.getString(R.string.please_switch_network, TestApp.networkDefinitionProvider.getCurrent().getNetworkName(), chainIdForTransaction)))
                .check(ViewAssertions.doesNotExist())

        rule.screenShot("please_change_chain")
        Truth.assertThat(rule.activity.isFinishing).isFalse()
    }

    @Test
    fun acceptsSimpleAddress() {
        rule.launchActivity(Intent.getIntentOld("0x12345"))
        Espresso.onView(ViewMatchers.withId(R.id.to_address)).check(ViewAssertions.matches(ViewMatchers.withText("0x12345")))
    }

    @Test
    fun usesCorrectValuesForETHTransaction1() {
        TestApp.currentTokenProvider.currentToken = eth
        TestApp.testDatabase.balances.upsert(Balance(TestApp.currentAddressProvider.getCurrent(), eth.address, TestApp.networkDefinitionProvider.getCurrent().chain, 1L, BigInteger.TEN * BigInteger("1" + "0".repeat(18))))

        rule.launchActivity(Intent.getIntentOld("ethereum:0x123456?value=1"))

        Espresso.onView(ViewMatchers.withId(R.id.fab)).perform(ViewActions.closeSoftKeyboard(), ViewActions.click())

        val allTransactionsForAddress = TestApp.testDatabase.transactions.getAllTransactionsForAddress(listOf(Address("0x123456")))
        Truth.assertThat(allTransactionsForAddress).hasSize(1)
        Truth.assertThat(allTransactionsForAddress.get(0).transaction.to?.hex).isEqualTo("0x123456")
        Truth.assertThat(allTransactionsForAddress.get(0).transaction.value).isEqualTo(BigInteger("1"))

    }

    @Test
    fun usesCorrectValuesForETHTransaction2() {
        TestApp.currentTokenProvider.currentToken = testToken
        TestApp.testDatabase.balances.upsert(Balance(TestApp.currentAddressProvider.getCurrent(), eth.address, TestApp.networkDefinitionProvider.getCurrent().chain, 1L, BigInteger.TEN * BigInteger("1" + "0".repeat(18))))
        rule.launchActivity(Intent.getIntentOld("ethereum:0x123456?value=1"))

        Espresso.onView(ViewMatchers.withId(R.id.fab)).perform(ViewActions.closeSoftKeyboard(), ViewActions.click())

        val allTransactionsForAddress = TestApp.testDatabase.transactions.getAllTransactionsForAddress(listOf(Address("0x123456")))
        Truth.assertThat(allTransactionsForAddress).hasSize(1)
        Truth.assertThat(allTransactionsForAddress.get(0).transaction.to?.hex).isEqualTo("0x123456")
        Truth.assertThat(allTransactionsForAddress.get(0).transaction.value).isEqualTo(BigInteger("1"))

    }

    @Test
    fun usesCorrectValuesForCurrentTokenTransfer() {
        TestApp.testDatabase.tokens.addIfNotPresent(listOf(testToken))
        TestApp.currentTokenProvider.currentToken = testToken

        val toAddress = DEFAULT_TEST_ADDRESS2
        val uri = TokenTransfer(toAddress, testToken, BigInteger.TEN).toERC681().generateURL()

        TestApp.testDatabase.balances.upsert(Balance(TestApp.currentAddressProvider.getCurrent(), eth.address, TestApp.networkDefinitionProvider.getCurrent().chain, 1L, BigInteger.TEN * BigInteger("1" + "0".repeat(18))))
        TestApp.testDatabase.balances.upsert(Balance(TestApp.currentAddressProvider.getCurrent(), testToken.address, TestApp.networkDefinitionProvider.getCurrent().chain, 1L, BigInteger.TEN * BigInteger("1" + "0".repeat(18))))

        rule.launchActivity(Intent.getIntentOld(uri))
        Espresso.onView(ViewMatchers.withId(R.id.fab)).perform(ViewActions.closeSoftKeyboard(), ViewActions.click())

        val allTransactionsForAddress = TestApp.testDatabase.transactions.getAllTransactionsForAddress(listOf(toAddress))
        Truth.assertThat(allTransactionsForAddress).hasSize(0)

        val allTransactionsForToken = TestApp.testDatabase.transactions.getAllTransactionsForAddress(listOf(testToken.address))
        Truth.assertThat(allTransactionsForToken).hasSize(1)
        Truth.assertThat(allTransactionsForToken.get(0).transaction.isTokenTransfer()).isTrue()
        Truth.assertThat(allTransactionsForToken.get(0).transaction.getTokenTransferTo()).isEqualTo(toAddress)
        Truth.assertThat(allTransactionsForToken.get(0).transaction.getTokenTransferValue()).isEqualTo(BigInteger.TEN)
    }

    @Test
    fun usesCorrectValuesForNewTokenTransfer() {
        val eth = getEthTokenForChain(TestApp.networkDefinitionProvider.getCurrent())
        TestApp.currentTokenProvider.currentToken = eth
        TestApp.testDatabase.tokens.addIfNotPresent(listOf(testToken))
        TestApp.testDatabase.balances.upsert(Balance(TestApp.currentAddressProvider.getCurrent(), eth.address, TestApp.networkDefinitionProvider.getCurrent().chain, 1L, BigInteger.TEN * eth.decimalsAsMultiplicator().toBigInteger()))
        TestApp.testDatabase.balances.upsert(Balance(TestApp.currentAddressProvider.getCurrent(), testToken.address, TestApp.networkDefinitionProvider.getCurrent().chain, 1L, BigInteger.TEN * testToken.decimalsAsMultiplicator().toBigInteger()))

        val toAddress = DEFAULT_TEST_ADDRESS2
        val uri = TokenTransfer(toAddress, testToken, BigInteger.TEN).toERC681().generateURL()


        rule.launchActivity(Intent.getIntentOld(uri))
        Espresso.onView(ViewMatchers.withId(R.id.fab)).perform(ViewActions.closeSoftKeyboard(), ViewActions.click())

        val allTransactionsForAddress = TestApp.testDatabase.transactions.getAllTransactionsForAddress(listOf(toAddress))
        Truth.assertThat(allTransactionsForAddress).hasSize(0)

        val allTransactionsForToken = TestApp.testDatabase.transactions.getAllTransactionsForAddress(listOf(testToken.address))
        Truth.assertThat(allTransactionsForToken).hasSize(1)
        Truth.assertThat(allTransactionsForToken.get(0).transaction.isTokenTransfer()).isTrue()
        Truth.assertThat(allTransactionsForToken.get(0).transaction.getTokenTransferTo()).isEqualTo(toAddress)
        Truth.assertThat(allTransactionsForToken.get(0).transaction.getTokenTransferValue()).isEqualTo(BigInteger.TEN)
    }

    @Test
    fun doesNotAcceptUnknownTokenTransfer() {
        TestApp.currentTokenProvider.currentToken = getEthTokenForChain(TestApp.networkDefinitionProvider.getCurrent())

        val toAddress = DEFAULT_TEST_ADDRESS2
        val uri = TokenTransfer(toAddress, testToken, BigInteger.TEN).toERC681().generateURL()

        rule.launchActivity(Intent.getIntentOld(uri))

        Espresso.onView(ViewMatchers.withText(R.string.unknown_token)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        rule.screenShot("unknown_token")
        Truth.assertThat(rule.activity.isFinishing).isFalse()
    }

    @Test
    fun doesNotChangeTokenOnToAddressScan() {
        TestApp.currentTokenProvider.currentToken = testToken
        TestApp.testDatabase.tokens.addIfNotPresent(listOf(testToken))

        val uri = TokenTransfer(DEFAULT_TEST_ADDRESS2, testToken, BigInteger.TEN).toERC681()
                .generateURL()
        rule.launchActivity(Intent.getIntentOld(uri))

        val result = Instrumentation.ActivityResult(RESULT_OK, Intent().putExtra("SCAN_RESULT", DEFAULT_TEST_ADDRESS3.hex))
        intending(hasComponent(QRScanActivity::class.java.canonicalName)).respondWith(result)

        Espresso.onView(ViewMatchers.withId(R.id.scan_button)).perform(click())

        Espresso.onView(ViewMatchers.withText(testToken.symbol)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

    }

}