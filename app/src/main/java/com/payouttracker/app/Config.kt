package com.payouttracker.app

/**
 * The Gmail address of the admin. Put your own Gmail here and in firestore.rules (same address).
 * Only this account sees the admin tools; the database rules enforce it on the server.
 */
const val ADMIN_EMAIL = "imabdullahaladil1998@gmail.com"

const val LINK_TELEGRAM = "https://t.me/austinaries24"

fun isAdminEmail(email: String): Boolean = email.equals(ADMIN_EMAIL, ignoreCase = true)
