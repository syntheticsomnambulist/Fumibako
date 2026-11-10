package dev.aiharness.core.planner

/** Names for the regions Nager.Date lists most (the rest show their code). */
internal object HolidayRegions {
    val NAMES: Map<String, String> = buildMap {
        mapOf(
            "AL" to "Alabama", "AK" to "Alaska", "AZ" to "Arizona", "AR" to "Arkansas", "CA" to "California",
            "CO" to "Colorado", "CT" to "Connecticut", "DE" to "Delaware", "DC" to "District of Columbia", "FL" to "Florida",
            "GA" to "Georgia", "HI" to "Hawaii", "ID" to "Idaho", "IL" to "Illinois", "IN" to "Indiana", "IA" to "Iowa",
            "KS" to "Kansas", "KY" to "Kentucky", "LA" to "Louisiana", "ME" to "Maine", "MD" to "Maryland",
            "MA" to "Massachusetts", "MI" to "Michigan", "MN" to "Minnesota", "MS" to "Mississippi", "MO" to "Missouri",
            "MT" to "Montana", "NE" to "Nebraska", "NV" to "Nevada", "NH" to "New Hampshire", "NJ" to "New Jersey",
            "NM" to "New Mexico", "NY" to "New York", "NC" to "North Carolina", "ND" to "North Dakota", "OH" to "Ohio",
            "OK" to "Oklahoma", "OR" to "Oregon", "PA" to "Pennsylvania", "RI" to "Rhode Island", "SC" to "South Carolina",
            "SD" to "South Dakota", "TN" to "Tennessee", "TX" to "Texas", "UT" to "Utah", "VT" to "Vermont",
            "VA" to "Virginia", "WA" to "Washington", "WV" to "West Virginia", "WI" to "Wisconsin", "WY" to "Wyoming",
            "PR" to "Puerto Rico", "GU" to "Guam", "VI" to "U.S. Virgin Islands", "AS" to "American Samoa",
        ).forEach { (k, v) -> put("US-$k", v) }
        mapOf(
            "AB" to "Alberta", "BC" to "British Columbia", "MB" to "Manitoba", "NB" to "New Brunswick",
            "NL" to "Newfoundland and Labrador", "NS" to "Nova Scotia", "NT" to "Northwest Territories", "NU" to "Nunavut",
            "ON" to "Ontario", "PE" to "Prince Edward Island", "QC" to "Quebec", "SK" to "Saskatchewan", "YT" to "Yukon",
        ).forEach { (k, v) -> put("CA-$k", v) }
        mapOf(
            "ACT" to "Australian Capital Territory", "NSW" to "New South Wales", "NT" to "Northern Territory",
            "QLD" to "Queensland", "SA" to "South Australia", "TAS" to "Tasmania", "VIC" to "Victoria", "WA" to "Western Australia",
        ).forEach { (k, v) -> put("AU-$k", v) }
        mapOf(
            "BW" to "Baden-Württemberg", "BY" to "Bavaria", "BE" to "Berlin", "BB" to "Brandenburg", "HB" to "Bremen",
            "HH" to "Hamburg", "HE" to "Hesse", "MV" to "Mecklenburg-Vorpommern", "NI" to "Lower Saxony",
            "NW" to "North Rhine-Westphalia", "RP" to "Rhineland-Palatinate", "SL" to "Saarland", "SN" to "Saxony",
            "ST" to "Saxony-Anhalt", "SH" to "Schleswig-Holstein", "TH" to "Thuringia",
        ).forEach { (k, v) -> put("DE-$k", v) }
        mapOf("ENG" to "England", "NIR" to "Northern Ireland", "SCT" to "Scotland", "WLS" to "Wales").forEach { (k, v) -> put("GB-$k", v) }
        mapOf(
            "AUK" to "Auckland", "BOP" to "Bay of Plenty", "CAN" to "Canterbury", "CIT" to "Chatham Islands", "GIS" to "Gisborne",
            "HKB" to "Hawke's Bay", "MBH" to "Marlborough", "MWT" to "Manawatū-Whanganui", "NSN" to "Nelson",
            "NTL" to "Northland", "OTA" to "Otago", "STL" to "Southland", "TKI" to "Taranaki", "WGN" to "Wellington",
            "WKO" to "Waikato", "WTC" to "West Coast",
        ).forEach { (k, v) -> put("NZ-$k", v) }
    }
}
