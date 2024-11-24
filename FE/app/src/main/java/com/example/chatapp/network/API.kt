package com.example.chatapp.network

import android.content.Context
import android.util.Log
import com.example.chatapp.R
import com.example.chatapp.model.Conversation
import com.example.chatapp.model.Message
import com.example.chatapp.model.User
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlin.contracts.contract

object API {
    private val client = OkHttpClient()

    fun createUser(context: Context,username:String,email:String, password:String,):Boolean{
        val builder = Request.Builder()
        builder.url("${context.getString(R.string.url)}create-user")
        val json = """
            {
                "username": "$email",
                "credentials": [
                    {
                        "temporary": false,
                        "type": "password",
                        "value": "$password"
                    }
                ],
                "attributes": 
                    {"imageUrl": "https://up.yimg.com/ib/th?id=OIP.55xrJdT3ckz5UX55xcVb7QHaLH&pid=Api&rs=1&c=1&qlt=95&w=83&h=124"}
                ,
                "email": "$email",
                "emailVerified": false,
                "enabled": true,
                "firstName": "${username.split(" ")[0]}",
                "lastName": "${if (username.contains(" ")) username.substring(username.indexOf(" ")+1) else ""}"
            }
        """
        val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), json)
        builder.post(requestBody)
        val request = builder.build()
        try {
            val response = client.newCall(request).execute()
            Log.e("check",response.code.toString())
            Log.e("check",response.message.toString())
            return response.isSuccessful
        }catch (e:Exception){
            Log.e("check",e.message.toString())
            return false
        }
    }

    fun getAuthenticatedToken(context: Context, username: String, password: String):String{
        val builder = Request.Builder()
        builder.url("${context.getString(R.string.url)}get-access-token")
        val formBody = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .build()
        val request = builder.post(formBody).build()
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful){
                val json = JSONObject(response.body!!.string())
                return json.getString("access_token")
            } else return ""
        }catch (e:Exception){
            return ""
        }
    }

    fun getAuthenticatedUser(context: Context, token: String):User{
        var user = User()

        val builder = Request.Builder()
        builder.url("${context.getString(R.string.url)}api/users/get-authenticated-user?forceResync=false")
        builder.addHeader("Authorization", "Bearer $token")
        val request = builder.build()
        try {
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body!!.string())
            user = readUserFromJson(json)
        } catch (e:Exception){
            Log.e("loginError", e.message.toString())
        }
        return user
    }

    fun getAllConversation(context: Context,token:String):ArrayList<Conversation> {
        val arrayList = arrayListOf<Conversation>()
        Log.e("token",token)
        val builder = Request.Builder()
        builder.addHeader("Authorization", "Bearer $token")
        builder.url("${context.getString(R.string.url)}api/conversations")
        val request = builder.build()
        try {
            val response = client.newCall(request).execute()
            val json = JSONArray(response.body!!.string())
            for (i in 0..json.length()-1){
                arrayList.add(readConversationFromJson(jsonObject = json.getJSONObject(i)))
            }
        } catch (e: Exception) {
            Log.e("error", e.message!!)
        }

        return arrayList
    }


    fun readUserFromJson(jsonObject: JSONObject):User{
        val user = User()
        if (jsonObject.has("email")) user.email = jsonObject.getString("email")
        user.userName = jsonObject.getString("firstName")+" "+jsonObject.getString("lastName")
        user.imageUrl = jsonObject.getString("imageUrl")
        user.id = jsonObject.getString("publicId")
        if (jsonObject.has("lastSeen")) user.lastSeen = jsonObject.getString("lastSeen")
        return user
    }

    fun readConversationFromJson(jsonObject: JSONObject):Conversation{
        val conversation = Conversation()

        conversation.name = jsonObject.getString("name")
        conversation.id = jsonObject.getString("publicId")
        val memberList = jsonObject.getJSONArray("members")
        for (i in 0..memberList.length()-1){
            conversation.memberList.add(readUserFromJson(memberList.getJSONObject(i)))
        }

        val messageList = jsonObject.getJSONArray("messages")
        for (i in 0..messageList.length()-1){
            conversation.messageList.add(readMessageFromJson(memberList.getJSONObject(i)))
        }

        return conversation
    }

    fun readMessageFromJson(jsonObject: JSONObject):Message{
        val message = Message()
        message.textContent = jsonObject.getString("textContent")
        message.sendDate = jsonObject.getString("sendDate")
        message.publicId = jsonObject.getString("publicId")
        message.conversationId = jsonObject.getString("conversationId")
        message.type = jsonObject.getString("type")
        var mediaContent = jsonObject.getJSONArray("mediaContent")
        for (i in 0..mediaContent.length()-1){
            message.mediaContent.add(mediaContent[i].toString())
        }
        message.mimeType = jsonObject.getString("mimeType")
        message.senderId = jsonObject.getString("senderId")
        return message
    }

    fun writeMessageToJson(message: Message):JSONObject {
        val jsonObject = JSONObject()

        jsonObject.put("textContent",message.textContent)
        jsonObject.put("sendDate",message.sendDate)
        jsonObject.put("publicId",message.publicId)
        jsonObject.put("conversationId",message.conversationId)
        jsonObject.put("type",message.type)
        jsonObject.put("mediaContent",message.mediaContent)
        jsonObject.put("mimeType",message.mimeType)
        jsonObject.put("senderId",message.senderId)

        return jsonObject
    }

    fun searchUser(context: Context,query:String,token: String):ArrayList<User>{
        var userList = arrayListOf<User>()
        Log.e("token",token)
        val builder = Request.Builder()
        builder.url("${context.getString(R.string.url)}api/users/search?query=${query}&page=0&size=10&sort=firstName,asc")
        builder.addHeader("Authorization", "Bearer $token")
        val request = builder.build()
        try {
            val response = client.newCall(request).execute()
            val json = JSONArray(response.body!!.string())
            for (i in 0..json.length()-1){
                userList.add(readUserFromJson(json.getJSONObject(i)))
            }
        } catch (e:Exception){
            Log.e("error",e.message.toString())
        }
        return userList
    }

    fun createConversation(context: Context,name:String, userIdList:ArrayList<String>,token: String){
        val builder = Request.Builder()
        builder.url("${context.getString(R.string.url)}api/conversations")
        builder.addHeader("Authorization", "Bearer $token")
        val json = JSONObject()
        json.put("name",name)
        json.put("member",userIdList)
        val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), json.toString())
        builder.post(requestBody)
        val request = builder.build()
        try {
            val response = client.newCall(request).execute()
            Log.e("check",response.isSuccessful.toString())
            Log.e("check",response.code.toString())
        }catch (e:Exception){
            Log.e("createConverError",e.message.toString())
        }
    }

}