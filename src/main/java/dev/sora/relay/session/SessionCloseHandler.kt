package dev.sora.relay.session

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler

class SessionCloseHandler(private val callback: (CharSequence) -> Unit): BedrockPacketHandler {

    override fun onDisconnect(reason: CharSequence) {
        callback(reason)
    }
}