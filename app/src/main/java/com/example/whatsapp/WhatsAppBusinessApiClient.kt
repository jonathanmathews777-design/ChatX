package com.example.whatsapp

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Url

@JsonClass(generateAdapter = true)
data class CloudApiTextBody(
    @Json(name = "preview_url") val previewUrl: Boolean = false,
    val body: String
)

@JsonClass(generateAdapter = true)
data class CloudApiTemplateLanguage(
    val code: String = "en_US"
)

@JsonClass(generateAdapter = true)
data class CloudApiTemplateBody(
    val name: String,
    val language: CloudApiTemplateLanguage = CloudApiTemplateLanguage()
)

@JsonClass(generateAdapter = true)
data class CloudApiMediaLink(
    val link: String,
    val caption: String? = null
)

@JsonClass(generateAdapter = true)
data class CloudApiSendMessageRequest(
    @Json(name = "messaging_product") val messagingProduct: String = "whatsapp",
    @Json(name = "recipient_type") val recipientType: String = "individual",
    val to: String,
    val type: String, // "text", "template", "image", "document", "video"
    val text: CloudApiTextBody? = null,
    val template: CloudApiTemplateBody? = null,
    val image: CloudApiMediaLink? = null,
    val document: CloudApiMediaLink? = null,
    val video: CloudApiMediaLink? = null
)

@JsonClass(generateAdapter = true)
data class CloudApiMessageId(
    val id: String
)

@JsonClass(generateAdapter = true)
data class CloudApiSendMessageResponse(
    @Json(name = "messaging_product") val messagingProduct: String? = null,
    val messages: List<CloudApiMessageId> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WebhookInboundEvent(
    val id: String,
    val from: String,
    val contactName: String? = null,
    val type: String = "text",
    val body: String,
    val mediaUrl: String? = null,
    val timestampMs: Long
)

@JsonClass(generateAdapter = true)
data class WebhookRelayResponse(
    val events: List<WebhookInboundEvent> = emptyList()
)

interface MetaWhatsAppCloudService {
    @POST("v21.0/{phoneNumberId}/messages")
    suspend fun sendMessage(
        @Path("phoneNumberId") phoneNumberId: String,
        @Header("Authorization") bearerToken: String,
        @Body payload: CloudApiSendMessageRequest
    ): CloudApiSendMessageResponse

    @GET
    suspend fun fetchWebhookEvents(@Url fullWebhookUrl: String): WebhookRelayResponse
}

object WhatsAppBusinessApiClient {
    fun create(): MetaWhatsAppCloudService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://graph.facebook.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(MetaWhatsAppCloudService::class.java)
    }
}
