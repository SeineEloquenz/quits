package nz.eloque.quits.domain

import platform.Foundation.NSLocale
import platform.Foundation.currencyCode
import platform.Foundation.currentLocale

actual fun deviceCurrencyCode(): String? = NSLocale.currentLocale.currencyCode
