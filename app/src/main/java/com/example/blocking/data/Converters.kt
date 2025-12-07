package com.example.blocking.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromSiteType(value: SiteType): String {
        return value.name
    }
    
    @TypeConverter
    fun toSiteType(value: String): SiteType {
        return SiteType.valueOf(value)
    }
}
