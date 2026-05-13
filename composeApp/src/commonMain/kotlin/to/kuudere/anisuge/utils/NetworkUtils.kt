package to.kuudere.anisuge.utils

fun Throwable.isNetworkError(): Boolean {
    val causeChain = generateSequence(this) { it.cause }.toList()
    return causeChain.any {
        val msg = it.message ?: ""
        msg.contains("UnknownHostException", ignoreCase = true) ||
        msg.contains("ConnectException", ignoreCase = true) ||
        msg.contains("SocketTimeoutException", ignoreCase = true) ||
        msg.contains("NoRouteToHostException", ignoreCase = true) ||
        msg.contains("Failed to connect", ignoreCase = true) ||
        msg.contains("Unable to resolve host", ignoreCase = true) ||
        msg.contains("No route to host", ignoreCase = true) ||
        msg.contains("Connection refused", ignoreCase = true)
    }
}
