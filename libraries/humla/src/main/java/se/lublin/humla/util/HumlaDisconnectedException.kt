package se.lublin.humla.util

class HumlaDisconnectedException : RuntimeException {
    constructor() : super("Caller attempted to use the protocol while disconnected.")
    constructor(reason: String) : super(reason)
}
