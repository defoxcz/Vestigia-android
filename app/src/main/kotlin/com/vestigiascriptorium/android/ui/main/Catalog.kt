package com.vestigiascriptorium.android.ui.main

internal data class Aid(
    val id: String,
    val titleCs: String,
    val titleEn: String,
    val descriptionCs: String,
    val descriptionEn: String,
    val category: Int,
    val downloadMb: String,
    val installedMb: String,
)

// Localised display names; current sizes and checksums come from catalogue metadata.
internal val aids = listOf(
    Aid("places", "Historická topografie", "Historical topography", "Historická místa a jejich proměny. Opora při hledání původu rodiny.", "Historical places and their changes. A reference for tracing family origins.", 0, "52", "167"),
    Aid("saints", "Kalendář světců", "Calendar of saints", "Sváteční dny a jména světců pro čtení historických datací.", "Feast days and saints’ names for interpreting historical dates.", 0, "2,1", "5,2"),
    Aid("historical-units", "Historické míry a váhy", "Historical weights and measures", "Přehled historických jednotek pro práci s archivními prameny.", "Historical units of measurement for working with archival sources.", 0, "0,15", "0,48"),
    Aid("manuscript-abbreviations", "Písařské zkratky", "Scribal abbreviations", "Pomocník při čtení zkratek v rukopisech a starých zápisech.", "A reference for reading abbreviations in manuscripts and old records.", 1, "0,01", "0,07"),
    Aid("heraldry", "Obecní heraldika", "Municipal heraldry", "Erby obcí jako pomůcka pro identifikaci míst a pramenů.", "Municipal coats of arms for identifying places and sources.", 2, "20", "53"),
    Aid("heraldry-nobility", "Šlechtická heraldika", "Noble heraldry", "Erby šlechtických rodů.", "Coats of arms of noble families.", 2, "5,5", "17,6"),
    Aid("heraldry-ecclesiastical", "Církevní heraldika", "Ecclesiastical heraldry", "Erby církevních institucí a osob.", "Arms of ecclesiastical institutions and individuals.", 2, "0,8", "2,4"),
    Aid("heraldry-chivalric-orders", "Rytířské a řeholní řády", "Chivalric and religious orders", "Heraldické znaky řádů.", "Heraldic emblems of orders.", 2, "0,13", "0,42"),
    Aid("heraldry-burgher-patrician", "Měšťanská heraldika", "Burgher heraldry", "Erby měšťanských a patricijských rodin.", "Arms of burgher and patrician families.", 2, "0,01", "0,14"),
    Aid("heraldry-personal-coats-of-arms", "Osobní erby", "Personal coats of arms", "Referenční katalog osobních erbů.", "A reference catalogue of personal arms.", 2, "27", "80"),
    Aid("numismatics", "Numismatika", "Numismatics", "Referenční katalog pro studium historických mincí.", "A reference catalogue for the study of historical coins.", 3, "249", "1 094"),
    Aid("sigillography", "Sfragistika", "Sigillography", "Podklady pro studium pečetí a jejich držitelů.", "Reference material for the study of seals and their owners.", 3, "0,4", "1,6"),
    Aid("epigraphy-noble-tombs", "Šlechtické náhrobníky", "Noble tomb monuments", "Epigrafické podklady k historickým náhrobníkům.", "Epigraphic references for historical tomb monuments.", 3, "6,1", "26"),
)

// Kept solely to offer explicit removal of a package installed by an older Android build.
internal val managedAids=aids+Aid("heraldry-templates","Heraldické šablony (desktop)","Heraldic templates (desktop)","","",2,"","")

internal class Copy(val cs: Boolean) {
    fun t(cs: String, en: String) = if (this.cs) cs else en
    fun title(aid: Aid) = t(aid.titleCs, aid.titleEn)
    fun description(aid: Aid) = t(aid.descriptionCs, aid.descriptionEn)
    val categories get() = listOf(t("Genealogie", "Genealogy"), t("Rukopisy", "Manuscripts"), t("Heraldika", "Heraldry"), t("Další obory", "Other fields"))
    fun size(bytes: Long): String = String.format(if(cs) java.util.Locale.forLanguageTag("cs") else java.util.Locale.ENGLISH,"%.1f MB",bytes/1_000_000.0)
    fun error(code: String?) = when(code) {
        "space" -> t("V zařízení není dost místa pro stažení a instalaci.","Not enough free space to download and install.")
        "integrity", "archive" -> t("Kontrola balíčku selhala. Původní pomůcka zůstala zachovaná.","Package validation failed. The existing reference was preserved.")
        "unsupported" -> t("Tato verze databáze vyžaduje novější aplikaci.","This database version requires a newer app.")
        "storage" -> t("Soubory pomůcky se nepodařilo upravit. Zkuste operaci znovu.","Unable to modify the reference files. Please try again.")
        "changed" -> t("Katalog se mezitím změnil. Obnovte jej a zkuste stažení znovu.","The catalogue changed. Refresh it and try again.")
        "unavailable" -> t("Balíček není dostupný. Obnovte katalog a zkuste to znovu.","Package unavailable. Refresh the catalogue and try again.")
        else -> t("Připojení selhalo. Zkontrolujte síť a zkuste stažení znovu.","Connection failed. Check your network and retry.")
    }
    fun status(value: String?) = when (value) {
        "queued" -> t("Ve frontě", "Queued")
        "active" -> t("Stahování", "Downloading")
        "paused" -> t("Pozastaveno", "Paused")
        "installed" -> t("Nainstalováno", "Installed")
        "error" -> t("Chyba", "Error")
        "verifying" -> t("Ověřování", "Verifying")
        "installing" -> t("Instalace", "Installing")
        "update" -> t("Dostupná aktualizace", "Update available")
        "retired" -> t("Pouze pro desktop", "Desktop only")
        "busy" -> t("Zpracovávám…", "Processing…")
        else -> t("Ke stažení", "Available to download")
    }
}
