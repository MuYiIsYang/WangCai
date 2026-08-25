package com.ai.wangcai.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromBowlType(value: BowlType): String = value.name

    @TypeConverter
    fun toBowlType(value: String): BowlType = BowlType.valueOf(value)

    @TypeConverter
    fun fromConsumptionType(value: ConsumptionType): String = value.name

    @TypeConverter
    fun toConsumptionType(value: String): ConsumptionType = ConsumptionType.valueOf(value)

    @TypeConverter
    fun fromExcretionType(value: ExcretionType): String = value.name

    @TypeConverter
    fun toExcretionType(value: String): ExcretionType = ExcretionType.valueOf(value)
}
