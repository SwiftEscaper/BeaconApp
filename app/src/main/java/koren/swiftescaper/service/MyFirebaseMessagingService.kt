package koren.swiftescaper.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // Firebase 클라우드 메시지가 수신되었을 때 호출되는 메서드
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: " + remoteMessage.from) // 메시지의 출처를 로그에 출력

        // 메시지에 데이터 페이로드가 있는 경우
        if (remoteMessage.data.size > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.data)
        }

        // 메시지에 알림 페이로드가 있는 경우
        if (remoteMessage.notification != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.notification!!.body)
        }
    }

    // 새 FCM 토큰이 생성되었을 때 호출되는 메서드
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")

        // 서버로 토큰을 전송하는 로직을 구현
    }

    companion object {
        private const val TAG = "MyFirebaseMsgService" // 로그 태그 상수
    }
}
