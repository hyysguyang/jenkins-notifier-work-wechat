package com.lifecosys.jenkins.plugin

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.http.HttpHeaders
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import java.io.File


/**
 *
 * @author [Young Gu](mailto:hyysguyang@gmail.com)
 */


class WorkWechatRobotClient(val robotKey: String = "",
                            val objectMapper: ObjectMapper = ObjectMapper(),
                            val httpClient: CloseableHttpClient = HttpClients.createDefault()) {
    val robotUrl = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=$robotKey"
    val uploadMediaUrl = "https://qyapi.weixin.qq.com/cgi-bin/webhook/upload_media?key=$robotKey&type=file"

    fun sendBuildMessage(buildName: String, atUsers: String, buildUrl: String, changeLogs: String) {
        val bodyJson = """
                      {
                        "msgtype": "markdown",
                        "markdown": {
                        "content": "
#### Fission CI 构建 <font color=\"warning\">失败</font>
**项目**: $buildName
**开发**: $atUsers
**详情**: [查看构建日志]($buildUrl)
**描述**: $atUsers, 你提交的代码有<font color=\"warning\">Bug</font>, 导致<font color=\"warning\">测试失败</font>, 请尽快修复.
**提交日志**: ${changeLogs}
                            "
                        }
                    }
                    """

        sendMessage(bodyJson)
    }


    fun sendFileMessage(file: File, fileName: String = file.name) {
        val mediaId = uploadFile(file, fileName)
        sendMedia(mediaId)
    }

    fun sendMedia(mediaId: String) {
        val json = """
            {
                "msgtype": "file",
                "file": {
                     "media_id": "$mediaId"
                }
            }
        """.trimIndent()
        sendMessage(json)
    }

    fun sendMessage(json: String) {
        val request = RequestBuilder
                .post(robotUrl)
                .addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString())
                .addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                .setEntity(StringEntity(json, ContentType.APPLICATION_JSON))
                .build()

        var closeableHttpResponse = httpClient.execute(request)
        val status = closeableHttpResponse.statusLine.statusCode
        val wechatFileUploadResponse = EntityUtils.toString(closeableHttpResponse.entity)
        var jsonNode = objectMapper.readTree(wechatFileUploadResponse)
        jsonNode

        closeableHttpResponse.close()
    }

    fun uploadFile(file: File, fileName: String = "build.log"): String {

        val data = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .addBinaryBody("media", file, ContentType.DEFAULT_BINARY, fileName)
                .build()


        val request = RequestBuilder
                .post(uploadMediaUrl)
                .setEntity(data)
                .build()

        var closeableHttpResponse = httpClient.execute(request)

        val status = closeableHttpResponse.statusLine.statusCode
        val wechatFileUploadResponse = EntityUtils.toString(closeableHttpResponse.entity)
        println("Uploading completed: $wechatFileUploadResponse")
        var jsonNode = objectMapper.readTree(wechatFileUploadResponse)

        var mediaId = jsonNode.at("/media_id").textValue()
        return mediaId
    }


}

