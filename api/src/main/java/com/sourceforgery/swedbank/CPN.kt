package com.sourceforgery.swedbank

data class CPN constructor(val data: Map<String, String>) {
    val avv = data["AVV"] ?: throw IllegalArgumentException("No AVV")
    val from = data["From"] ?: throw IllegalArgumentException("No From")
    val pan = data["PAN"] ?: throw IllegalArgumentException("No PAN")
    val expiryMonth = data["ExpiryMonth"] ?: throw IllegalArgumentException("No ExpiryMonth")
    val expiryYear = data["ExpiryYear"] ?: throw IllegalArgumentException("No ExpiryYear")
}
