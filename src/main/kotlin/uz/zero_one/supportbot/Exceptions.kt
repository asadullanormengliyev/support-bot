package uz.zero_one.supportbot

class RecordNotFoundException(message: String) : RuntimeException(message) {
    override val message: String?
        get() = super.message
}