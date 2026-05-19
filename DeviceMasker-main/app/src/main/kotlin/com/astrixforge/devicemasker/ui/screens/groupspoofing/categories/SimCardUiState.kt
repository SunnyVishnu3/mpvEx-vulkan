package com.astrixforge.devicemasker.ui.screens.groupspoofing.categories

import androidx.compose.runtime.Immutable
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.common.models.Carrier

internal data class SimCardUiState(
    val isSimEnabled: Boolean,
    val phoneEnabled: Boolean,
    val phoneValue: String,
    val imsiEnabled: Boolean,
    val imsiValue: String,
    val iccidEnabled: Boolean,
    val iccidValue: String,
    val simCountryValue: String,
    val networkCountryValue: String,
    val mccMncValue: String,
    val carrierNameValue: String,
    val simOperatorValue: String,
    val networkOperatorValue: String,
)

@Immutable internal data class CarrierOptions(val items: List<Carrier>)

internal fun SpoofGroup?.toSimCardUiState(): SimCardUiState =
    SimCardUiState(
        isSimEnabled = this?.isTypeEnabled(SpoofType.CARRIER_NAME) ?: false,
        phoneEnabled = this?.isTypeEnabled(SpoofType.PHONE_NUMBER) ?: false,
        phoneValue = this?.getValue(SpoofType.PHONE_NUMBER).orEmpty(),
        imsiEnabled = this?.isTypeEnabled(SpoofType.IMSI) ?: false,
        imsiValue = this?.getValue(SpoofType.IMSI).orEmpty(),
        iccidEnabled = this?.isTypeEnabled(SpoofType.ICCID) ?: false,
        iccidValue = this?.getValue(SpoofType.ICCID).orEmpty(),
        simCountryValue = this?.getValue(SpoofType.SIM_COUNTRY_ISO).orEmpty(),
        networkCountryValue = this?.getValue(SpoofType.NETWORK_COUNTRY_ISO).orEmpty(),
        mccMncValue = this?.getValue(SpoofType.CARRIER_MCC_MNC).orEmpty(),
        carrierNameValue = this?.getValue(SpoofType.CARRIER_NAME).orEmpty(),
        simOperatorValue = this?.getValue(SpoofType.SIM_OPERATOR_NAME).orEmpty(),
        networkOperatorValue = this?.getValue(SpoofType.NETWORK_OPERATOR).orEmpty(),
    )

internal val CarrierSpoofTypes =
    listOf(
        SpoofType.CARRIER_NAME,
        SpoofType.CARRIER_MCC_MNC,
        SpoofType.SIM_COUNTRY_ISO,
        SpoofType.NETWORK_COUNTRY_ISO,
        SpoofType.SIM_OPERATOR_NAME,
        SpoofType.NETWORK_OPERATOR,
    )
