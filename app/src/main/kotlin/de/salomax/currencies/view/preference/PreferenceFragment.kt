package de.salomax.currencies.view.preference

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import de.salomax.currencies.BuildConfig
import de.salomax.currencies.R
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.util.toHumanReadableNumber
import de.salomax.currencies.viewmodel.preference.PreferenceViewModel
import de.salomax.currencies.widget.EditTextSwitchPreference
import de.salomax.currencies.widget.LongSummaryPreference
import java.util.Calendar

@Suppress("unused")
class PreferenceFragment: PreferenceFragmentCompat() {

    private lateinit var viewModel: PreferenceViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.fitsSystemWindows = true
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs, rootKey)
        viewModel = ViewModelProvider(this)[PreferenceViewModel::class.java]

        // transaction fee
        val feePreference = findPreference<EditTextSwitchPreference>(getString(R.string.fee_key))
        feePreference?.setOnPreferenceChangeListener { _, newValue ->
            // fee amount changed
            if (newValue is String)
                try {
                    viewModel.setFee(
                        if (newValue.isEmpty()) 0f
                        else newValue.toFloat()
                    )
                } catch (e: NumberFormatException) {
                    viewModel.setFee(0f)
                }
            // fee enabled/disabled
            else if (newValue is Boolean)
                viewModel.setFeeEnabled(newValue)
            true
        }
        viewModel.getFee().observe(this) {
            feePreference?.summary = it.toHumanReadableNumber(requireContext(), showPositiveSign = true, suffix = "%")
            feePreference?.text = it.toString()
        }

        // conversion preview
        findPreference<SwitchPreferenceCompat>(getString(R.string.previewConversion_key))?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setPreviewConversionEnabled(newValue.toString().toBoolean())
                true
            }
        }

        // keypad type
        findPreference<ListPreference>(getString(R.string.keypadType_key))?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setKeypadType(newValue.toString().toInt())
                true
            }
        }

        // -------------------------------------------------------------------------------------

        // theme
        findPreference<ListPreference>(getString(R.string.theme_key))?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setTheme(newValue.toString().toInt())
                true
            }
        }

        // pure black
        findPreference<SwitchPreferenceCompat>(getString(R.string.pure_black_key))?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setPureBlackEnabled(newValue.toString().toBoolean())
                true
            }
        }

        // language
        findPreference<LanguagePickerPreference>(getString(R.string.language_key))?.apply {
            // listen for changes
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setLanguage(newValue.toString())
                true
            }
        }

        // -------------------------------------------------------------------------------------

        // OpenExchangerates: API Key
        val openExchangeratesApiKeyPreference = findPreference<EditTextPreference>(getString(R.string.api_open_exchangerates_id_key))
        openExchangeratesApiKeyPreference?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setOpenExchangeratesApiKey(newValue.toString().trim())
                true
            }
            dialogMessage = getText(R.string.api_open_exchangerates_api_key_message)
        }
        viewModel.getOpenExchangeratesApiKey().observe(this) { id ->
            openExchangeratesApiKeyPreference?.summaryProvider = Preference.SummaryProvider<EditTextPreference> {
                if (id.isNullOrBlank()) getText(R.string.api_open_exchangerates_api_key_missing)
                else id
            }
        }
        // api provider
        findPreference<ProviderPickerPreference>(getString(R.string.api_key))?.apply {
            // initialize values
            val providers = ApiProvider.entries
            entries = providers.map { it.getName() }.toTypedArray()         // names
            entryValues = providers.map { it.id.toString() }.toTypedArray() // ids
            // listen for changes
            setOnPreferenceChangeListener { _, newValue ->
                val provider = ApiProvider.fromId(newValue.toString().toIntOrNull() ?: -1)
                // persist new provider
                viewModel.setApiProvider(provider)
                // update visibility of api key input
                openExchangeratesApiKeyPreference?.isVisible = provider == ApiProvider.OPEN_EXCHANGERATES
                true
            }
            // set default, if empty (empty means, there was no mapping for the stored value)
            if (entry == null) {
                val defaultProvider = ApiProvider.fromId(-1)
                viewModel.setApiProvider(defaultProvider)
                value = defaultProvider.id.toString()
            }
            // set initial visibility of api key input
            openExchangeratesApiKeyPreference?.isVisible =
                ApiProvider.fromId(value.toIntOrNull() ?: -1) == ApiProvider.OPEN_EXCHANGERATES
        }
        // change text according to selected api
        viewModel.getApiProvider().observe(this) {
            findPreference<LongSummaryPreference>(getString(R.string.key_apiProvider))?.apply {
                title =
                    resources.getString(R.string.api_about_title, it.getName())
                summary =
                    it.getDescriptionLong(context)
            }
            findPreference<LongSummaryPreference>(getString(R.string.key_refreshPeriod))?.summary =
                it.getDescriptionUpdateInterval(requireContext())
        }

        // -------------------------------------------------------------------------------------

        // open source code repo
        findPreference<Preference>(getString(R.string.sourcecode_key))?.apply {
            setOnPreferenceClickListener {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/sal0max/currencies")
                    )
                )
                true
            }
        }

        // donate
        findPreference<Preference>(getString(R.string.donate_key))?.apply {
            // hide for Play Store - Google is a cunt
            @Suppress("KotlinConstantConditions")
            isVisible = when (BuildConfig.FLAVOR) {
                "play" -> false
                "fdroid" -> true
                else -> true
            }
            // go to PayPal, when clicked
            setOnPreferenceClickListener {
                startActivity(createIntent("https://www.paypal.com/donate?hosted_button_id=2JCY7E99V9DGC"))
                true
            }
        }

        // rate
        findPreference<Preference>(getString(R.string.rate_key))?.apply {
            // hide for F-Droid - no rating mechanism there
            @Suppress("KotlinConstantConditions")
            isVisible = when (BuildConfig.FLAVOR) {
                "play" -> true
                else -> false
            }
            // open play store
            setOnPreferenceClickListener {
                // play store
                try {
                    startActivity(createIntent("market://details?id=de.salomax.currencies"))
                }
                // browser
                catch (e: ActivityNotFoundException) {
                    startActivity(createIntent("https://play.google.com/store/apps/details?id=de.salomax.currencies"))
                }
                true
            }
        }

        // -------------------------------------------------------------------------------------

        // changelog
        findPreference<Preference>(getString(R.string.changelog_key))?.apply {
            setOnPreferenceClickListener {
                ChangelogDialog().show(childFragmentManager, null)
                true
            }
        }

        // about
        findPreference<Preference>(getString(R.string.version_key))?.apply {
            title = BuildConfig.VERSION_NAME
            summary = getString(R.string.version_summary, Calendar.getInstance().get(Calendar.YEAR).toString())
        }
    }

    private fun createIntent(url: String): Intent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NO_HISTORY
                    or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        )
        return intent
    }

}
