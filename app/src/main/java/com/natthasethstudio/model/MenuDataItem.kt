package com.natthasethstudio.sethpos.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MenuDataItem(
    val name: String,
    val price: Double,
    val imageUrl: String?,
    val storeId: String,
    val category: String = ""
) : Parcelable
