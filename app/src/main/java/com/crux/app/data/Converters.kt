package com.crux.app.data

import androidx.room.TypeConverter
import com.crux.app.domain.model.ParsedBy
import com.crux.app.domain.model.RecurrenceType
import com.crux.app.domain.model.Source
import com.crux.app.domain.model.TaskStatus

/** Enums are stored as their names. Room does not convert enums itself. */
class Converters {
    @TypeConverter fun recurrenceToString(v: RecurrenceType?): String? = v?.name
    @TypeConverter fun stringToRecurrence(v: String?): RecurrenceType? =
        v?.let { RecurrenceType.valueOf(it) }

    @TypeConverter fun statusToString(v: TaskStatus): String = v.name
    @TypeConverter fun stringToStatus(v: String): TaskStatus = TaskStatus.valueOf(v)

    @TypeConverter fun sourceToString(v: Source): String = v.name
    @TypeConverter fun stringToSource(v: String): Source = Source.valueOf(v)

    @TypeConverter fun parsedByToString(v: ParsedBy): String = v.name
    @TypeConverter fun stringToParsedBy(v: String): ParsedBy = ParsedBy.valueOf(v)
}
