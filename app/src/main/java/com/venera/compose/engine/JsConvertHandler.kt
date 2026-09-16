package com.venera.compose.engine

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class JsConvertHandler {

    fun handle(data: Map<String, Any?>): Any? {
        val type = data["type"] as? String ?: return null
        val isEncode = data["isEncode"] == true
        return try {
            when (type) {
                "utf8" -> {
                    if (isEncode) {
                        val str = data["value"]?.toString() ?: ""
                        wrapBytes(str.toByteArray(Charsets.UTF_8))
                    } else {
                        val bytes = extractBytes(data["value"]) ?: ByteArray(0)
                        String(bytes, Charsets.UTF_8)
                    }
                }
                "gbk" -> {
                    val charset = Charset.forName("GBK")
                    if (isEncode) {
                        val str = data["value"]?.toString() ?: ""
                        wrapBytes(str.toByteArray(charset))
                    } else {
                        val bytes = extractBytes(data["value"]) ?: ByteArray(0)
                        String(bytes, charset)
                    }
                }
                "base64" -> {
                    if (isEncode) {
                        val bytes = extractBytes(data["value"]) ?: ByteArray(0)
                        Base64.encodeToString(bytes, Base64.NO_WRAP)
                    } else {
                        val str = data["value"]?.toString() ?: ""
                        wrapBytes(Base64.decode(str, Base64.DEFAULT))
                    }
                }
                "md5" -> {
                    val bytes = extractBytes(data["value"]) ?: ByteArray(0)
                    wrapBytes(MessageDigest.getInstance("MD5").digest(bytes))
                }
                "sha1" -> {
                    val bytes = extractBytes(data["value"]) ?: ByteArray(0)
                    wrapBytes(MessageDigest.getInstance("SHA-1").digest(bytes))
                }
                "sha256" -> {
                    val bytes = extractBytes(data["value"]) ?: ByteArray(0)
                    wrapBytes(MessageDigest.getInstance("SHA-256").digest(bytes))
                }
                "sha512" -> {
                    val bytes = extractBytes(data["value"]) ?: ByteArray(0)
                    wrapBytes(MessageDigest.getInstance("SHA-512").digest(bytes))
                }
                "hmac" -> {
                    val keyBytes = extractBytes(data["key"]) ?: ByteArray(0)
                    val valBytes = extractBytes(data["value"]) ?: ByteArray(0)
                    val hash = (data["hash"] as? String ?: "sha256").lowercase()
                    val algo = when (hash) {
                        "md5" -> "HmacMD5"
                        "sha1" -> "HmacSHA1"
                        "sha256" -> "HmacSHA256"
                        "sha512" -> "HmacSHA512"
                        else -> "HmacSHA256"
                    }
                    val mac = Mac.getInstance(algo)
                    mac.init(SecretKeySpec(keyBytes, algo))
                    val result = mac.doFinal(valBytes)
                    if (data["isString"] == true) {
                        result.joinToString("") { "%02x".format(it) }
                    } else {
                        wrapBytes(result)
                    }
                }
                "aes-ecb" -> {
                    val keyBytes = extractBytes(data["key"]) ?: ByteArray(0)
                    val valBytes = extractBytes(data["value"]) ?: ByteArray(0)
                    val cipher = Cipher.getInstance("AES/ECB/NoPadding")
                    cipher.init(if (isEncode) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
                    wrapBytes(cipher.doFinal(valBytes))
                }
                "aes-cbc" -> {
                    val keyBytes = extractBytes(data["key"]) ?: ByteArray(0)
                    val ivBytes = extractBytes(data["iv"]) ?: ByteArray(0)
                    val valBytes = extractBytes(data["value"]) ?: ByteArray(0)
                    val cipher = Cipher.getInstance("AES/CBC/NoPadding")
                    cipher.init(
                        if (isEncode) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
                        SecretKeySpec(keyBytes, "AES"),
                        IvParameterSpec(ivBytes)
                    )
                    wrapBytes(cipher.doFinal(valBytes))
                }
                "aes-cfb" -> {
                    val keyBytes = extractBytes(data["key"]) ?: ByteArray(0)
                    val ivBytes = extractBytes(data["iv"]) ?: ByteArray(0)
                    val valBytes = extractBytes(data["value"]) ?: ByteArray(0)
                    val blockSize = (data["blockSize"] as? Number)?.toInt() ?: 128
                    val cipher = Cipher.getInstance("AES/CFB$blockSize/NoPadding")
                    cipher.init(
                        if (isEncode) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
                        SecretKeySpec(keyBytes, "AES"),
                        IvParameterSpec(ivBytes)
                    )
                    wrapBytes(cipher.doFinal(valBytes))
                }
                "aes-ofb" -> {
                    val keyBytes = extractBytes(data["key"]) ?: ByteArray(0)
                    val valBytes = extractBytes(data["value"]) ?: ByteArray(0)
                    val blockSize = (data["blockSize"] as? Number)?.toInt() ?: 128
                    val cipher = Cipher.getInstance("AES/OFB$blockSize/NoPadding")
                    cipher.init(
                        if (isEncode) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
                        SecretKeySpec(keyBytes, "AES")
                    )
                    wrapBytes(cipher.doFinal(valBytes))
                }
                "rsa" -> {
                    val keyStr = data["key"]?.toString() ?: ""
                    val valBytes = extractBytes(data["value"]) ?: ByteArray(0)
                    val keyBytes = Base64.decode(keyStr, Base64.DEFAULT)
                    val privateKey = parsePrivateKey(keyBytes)
                    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                    cipher.init(Cipher.DECRYPT_MODE, privateKey)
                    wrapBytes(processCipherBlocks(cipher, valBytes, cipher.blockSize))
                }
                else -> null
            }
        } catch (e: Exception) {
            android.util.Log.e("VeneraJS", "Convert error for type: $type", e)
            null
        }
    }

    private fun extractBytes(obj: Any?): ByteArray? {
        if (obj == null) return null
        if (obj is ByteArray) return obj
        if (obj is Map<*, *>) {
            val b64 = obj["__bytes_base64__"] as? String
            if (b64 != null) {
                return Base64.decode(b64, Base64.DEFAULT)
            }
        }
        if (obj is String) {
            return obj.toByteArray(Charsets.UTF_8)
        }
        if (obj is List<*>) {
            val ba = ByteArray(obj.size)
            for (i in obj.indices) {
                ba[i] = ((obj[i] as? Number)?.toInt() ?: 0).toByte()
            }
            return ba
        }
        return null
    }

    private fun wrapBytes(bytes: ByteArray): Map<String, String> {
        return mapOf("__bytes_base64__" to Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    private fun processCipherBlocks(cipher: Cipher, input: ByteArray, blockSize: Int): ByteArray {
        val bs = if (blockSize > 0) blockSize else 256
        val out = ByteArrayOutputStream()
        var offset = 0
        while (offset < input.size) {
            val len = Math.min(bs, input.size - offset)
            val chunk = cipher.doFinal(input, offset, len)
            out.write(chunk)
            offset += len
        }
        return out.toByteArray()
    }

    private fun parsePrivateKey(der: ByteArray): PrivateKey {
        return try {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
        } catch (e: Exception) {
            // PKCS#1 fallback: wrap raw PKCS#1 RSA private key in PKCS#8 DER envelope
            val pkcs8Wrapper = wrapPkcs1InPkcs8(der)
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8Wrapper))
        }
    }

    private fun wrapPkcs1InPkcs8(pkcs1: ByteArray): ByteArray {
        val rsaOid = byteArrayOf(
            0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
        )
        val octetStringHeader = createDerHeader(0x04, pkcs1.size)
        val innerLength = 3 + rsaOid.size + octetStringHeader.size + pkcs1.size
        val seqHeader = createDerHeader(0x30, innerLength)

        val out = ByteArrayOutputStream()
        out.write(seqHeader)
        out.write(byteArrayOf(0x02, 0x01, 0x00)) // version = 0
        out.write(rsaOid)
        out.write(octetStringHeader)
        out.write(pkcs1)
        return out.toByteArray()
    }

    private fun createDerHeader(tag: Int, length: Int): ByteArray {
        return if (length < 128) {
            byteArrayOf(tag.toByte(), length.toByte())
        } else if (length < 256) {
            byteArrayOf(tag.toByte(), 0x81.toByte(), length.toByte())
        } else {
            byteArrayOf(tag.toByte(), 0x82.toByte(), (length shr 8).toByte(), (length and 0xff).toByte())
        }
    }
}
