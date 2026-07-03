package com.jack.meuholerite.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jack.meuholerite.MainActivity
import com.jack.meuholerite.R
import com.jack.meuholerite.utils.GlobalMessage
import com.jack.meuholerite.utils.GlobalMessageManager
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_SERVICE", "Novo token gerado: $token")
        newFunction(this) // Passa o contexto para sincronizar com nome real
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "Aviso"
        val content = remoteMessage.data["content"] ?: remoteMessage.notification?.body ?: ""
        val imageUrl = remoteMessage.data["imageUrl"] ?: remoteMessage.notification?.imageUrl?.toString()
        val messageId = remoteMessage.data["messageId"] ?: remoteMessage.messageId ?: System.currentTimeMillis().toString()
        
        // Extrai campos de botão enviados pelo Painel Admin
        val buttonText = remoteMessage.data["buttonText"]
        val buttonUrl = remoteMessage.data["buttonUrl"]

        // Se for notificação de promoção, salva o ID para evitar duplicata do LaunchedEffect
        val promoId = remoteMessage.data["promoId"]
        if (!promoId.isNullOrBlank()) {
            val appPrefs = getSharedPreferences("meu_holerite_prefs", MODE_PRIVATE)
            val existing = appPrefs.getString("notified_promo_ids", "")?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            if (promoId !in existing) {
                val updated = (existing + promoId).joinToString(",")
                appPrefs.edit().putString("notified_promo_ids", updated).apply()
            }
        } else {
            // Apenas mensagens globais (não promo) salvam no GlobalMessageManager
            val manager = GlobalMessageManager(applicationContext)
            manager.saveMessage(GlobalMessage(
                id = messageId, 
                title = title, 
                content = content, 
                imageUrl = imageUrl,
                buttonText = buttonText,
                buttonUrl = buttonUrl,
                timestamp = System.currentTimeMillis()
            ))
        }

        if (remoteMessage.notification != null || remoteMessage.data.isNotEmpty()) {
            sendNotification(title, content, messageId, imageUrl, buttonText, buttonUrl)
        }
    }

    private fun sendNotification(
        title: String,
        messageBody: String,
        messageId: String,
        imageUrl: String?,
        buttonText: String?,
        buttonUrl: String?
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("FROM_NOTIFICATION", true)
            putExtra("MSG_ID", messageId)
            putExtra("MSG_TITLE", title)
            putExtra("MSG_CONTENT", messageBody)
            putExtra("MSG_IMAGE_URL", imageUrl)
            putExtra("MSG_BUTTON_TEXT", buttonText)
            putExtra("MSG_BUTTON_URL", buttonUrl)
            putExtra("MSG_TIMESTAMP", System.currentTimeMillis())
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, messageId.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = "GLOBAL_MESSAGES"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (!imageUrl.isNullOrEmpty()) {
            val bitmap = getBitmapFromUrl(imageUrl)
            if (bitmap != null) {
                notificationBuilder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap))
            }
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Mensagens Globais", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }
        notificationManager.notify(messageId.hashCode(), notificationBuilder.build())
    }

    private fun getBitmapFromUrl(imageUrl: String): Bitmap? {
        return try {
            val url = URL(imageUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            BitmapFactory.decodeStream(connection.inputStream)
        } catch (e: Exception) { null }
    }
}

/**
 * Sincroniza o usuário atual com o Firestore para uso no Painel Admin.
 */
fun newFunction(context: Context? = null) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    
    if (user != null) {
        Log.d("FCM_SERVICE", "Sincronizando usuário: ${user.email}")
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                
                // Subscreve ao tópico global para notificações gerais
                FirebaseMessaging.getInstance().subscribeToTopic("global")
                    .addOnCompleteListener { subTask ->
                        if (subTask.isSuccessful) Log.d("FCM_SERVICE", "Subscrito ao tópico 'global'")
                    }
                val prefs = context?.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                val notifEnabled = prefs?.getBoolean("promo_notif_enabled", true) ?: true
                if (notifEnabled) {
                    FirebaseMessaging.getInstance().subscribeToTopic("promocoes")
                        .addOnCompleteListener { subTask ->
                            if (subTask.isSuccessful) Log.d("FCM_SERVICE", "Subscrito ao tópico 'promocoes'")
                        }
                }
                // Tenta obter o nome das preferências (mais preciso que o displayName do Google)
                val savedName = prefs?.getString("user_name", null)
                val savedCompany = prefs?.getString("user_company", "")?.trim().orEmpty()
                val finalName = if (!savedName.isNullOrBlank()) savedName else (user.displayName ?: "Usuário")
                val companyTopic = buildCompanyTopic(savedCompany)

                val userData = hashMapOf(
                    "name" to finalName,
                    "email" to (user.email ?: ""),
                    "fcmToken" to token,
                    "company" to savedCompany,
                    "companyTopic" to companyTopic,
                    "lastLogin" to System.currentTimeMillis(),
                    "platform" to "android"
                )

                if (companyTopic != null) {
                    val previousTopic = prefs?.getString("company_topic", null)
                    if (!previousTopic.isNullOrBlank() && previousTopic != companyTopic) {
                        FirebaseMessaging.getInstance().unsubscribeFromTopic(previousTopic)
                    }
                    FirebaseMessaging.getInstance().subscribeToTopic(companyTopic)
                        .addOnCompleteListener { subTask ->
                            if (subTask.isSuccessful) Log.d("FCM_SERVICE", "Subscrito ao tópico '$companyTopic'")
                        }
                    prefs?.edit()?.putString("company_topic", companyTopic)?.apply()
                }

                FirebaseFirestore.getInstance().collection("users")
                    .document(user.email ?: user.uid)
                    .set(userData, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("FCM_SERVICE", "Usuário sincronizado com sucesso.")
                    }
                    .addOnFailureListener { e ->
                        Log.e("FCM_SERVICE", "Erro ao sincronizar: ${e.message}")
                    }
            }
        }
    }
}

private fun buildCompanyTopic(companyName: String): String? {
    if (companyName.isBlank()) return null

    val noAccents = Normalizer.normalize(companyName, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
    val normalized = noAccents
        .lowercase(Locale.ROOT)
        .replace("[^a-z0-9]+".toRegex(), "_")
        .trim('_')

    if (normalized.isBlank()) return null
    return "empresa_$normalized".take(900)
}
