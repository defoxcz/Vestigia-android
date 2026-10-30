package com.vestigiascriptorium.android.genealogy

import java.time.LocalDate
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.ResolverStyle
import java.util.Locale

/** Storage is ISO date or four-digit year. Precision is never inferred as January 1. */
internal object GenealogyDates {
    fun full(value: String): LocalDate?=try {if(value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) LocalDate.parse(value).takeIf {it.year in 1..9999} else null} catch(_: Exception) {null}
    fun canonical(value: String)=value.isEmpty() || yearOnly(value)!=null || full(value)!=null
    fun yearOnly(value: String)=value.takeIf {it.matches(Regex("[0-9]{4}"))}?.toIntOrNull()?.takeIf {it in 1..9999}
    fun year(value: String): Int?=yearOnly(value) ?: full(value)?.year ?: Regex("(?<![0-9])[0-9]{3,4}(?![0-9])").findAll(value).map {it.value.toInt()}.filter {it>0}.toSet().singleOrNull()
    private fun pattern(locale: Locale)=DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT,null,IsoChronology.INSTANCE,locale).replace(Regex("y+"),"uuuu")
    fun format(value: String,locale: Locale=Locale.getDefault(Locale.Category.FORMAT)): String=full(value)?.format(DateTimeFormatter.ofPattern(pattern(locale),locale)) ?: value
    fun parse(text: String,mode: String,locale: Locale): String? {
        val value=text.trim()
        if(value.isEmpty()) return ""
        if(mode=="year") return yearOnly(value)?.toString()?.padStart(4,'0')
        if(mode!="date") return null
        return try {
            LocalDate.parse(value.replace(Regex("\\s+"),""),DateTimeFormatter.ofPattern(pattern(locale).replace(Regex("\\s+"),"").replace(Regex("d+"),"d").replace(Regex("M+"),"M"),locale).withResolverStyle(ResolverStyle.STRICT))
                .takeIf {it.year in 1..9999}?.toString()
        } catch(_: Exception) {null}
    }
    fun ordered(from: String,to: String): Boolean {
        if(from.isEmpty() || to.isEmpty())return true
        val first=full(from) ?: yearOnly(from)?.let {LocalDate.of(it,1,1)} ?: return true
        val last=full(to) ?: yearOnly(to)?.let {LocalDate.of(it,12,31)} ?: return true
        return first<=last
    }
}
