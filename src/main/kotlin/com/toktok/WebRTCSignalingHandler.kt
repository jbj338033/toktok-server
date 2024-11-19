package com.toktok

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@Component
class WebRTCSignalingHandler(@Qualifier("objectMapper") private val objectMapper: ObjectMapper) : TextWebSocketHandler() {
    private val waitingUsers = ConcurrentLinkedQueue<WebSocketSession>()
    private val activeConnections = ConcurrentHashMap<String, WebSocketSession>()
    private val userPairs = ConcurrentHashMap<String, String>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val waitingUser = waitingUsers.poll()
        if (waitingUser == null) {
            // 대기열에 아무도 없으면 현재 사용자를 대기열에 추가
            waitingUsers.offer(session)
            activeConnections[session.id] = session
        } else {
            // 대기 중인 사용자가 있으면 매칭
            matchUsers(waitingUser, session)
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            val signalData = objectMapper.readValue<SignalData>(message.payload)
            val targetSession = activeConnections[userPairs[session.id]]

            targetSession?.sendMessage(TextMessage(objectMapper.writeValueAsString(signalData)))
        } catch (e: Exception) {
            println("Error handling message: ${e.message}")
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        // 연결이 끊긴 사용자의 페어도 연결 해제
        val pairedUserId = userPairs[session.id]
        pairedUserId?.let { paired ->
            activeConnections[paired]?.close()
            userPairs.remove(paired)
            activeConnections.remove(paired)
        }

        waitingUsers.remove(session)
        userPairs.remove(session.id)
        activeConnections.remove(session.id)
    }

    private fun matchUsers(user1: WebSocketSession, user2: WebSocketSession) {
        // 두 사용자를 페어로 등록
        userPairs[user1.id] = user2.id
        userPairs[user2.id] = user1.id
        activeConnections[user1.id] = user1
        activeConnections[user2.id] = user2

        // user1을 initiator로 지정
        val initiatorMessage = objectMapper.writeValueAsString(SignalData("match", "initiator"))
        val receiverMessage = objectMapper.writeValueAsString(SignalData("match", "receiver"))

        // 각각 다른 메시지 전송
        user1.sendMessage(TextMessage(initiatorMessage))
        user2.sendMessage(TextMessage(receiverMessage))
    }
}