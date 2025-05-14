package gorbushkabot

fun formatPhone(rawPhone: String): String {
    val digits = rawPhone.replace(Regex("\\D+"), "")
    return if (digits[0] == '7') "+$digits" else digits
}