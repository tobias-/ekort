package com.sourceforgery.swedbank

class CPN private constructor(data: Map<String, String>) {
    private val avv: String
    private val from: String
    private val pan: String
    private val expiryMonth: String
    private val expiryYear: String

    init {
        avv = data["AVV"]
        from = data["From"]
        pan = data["PAN"]
        expiryMonth = data["ExpiryMonth"]
        expiryYear = data["ExpiryYear"]
    }

    companion object {

        fun from(data: Map<String, String>): CPN {
            return CPN(data)
        }
    }
}
