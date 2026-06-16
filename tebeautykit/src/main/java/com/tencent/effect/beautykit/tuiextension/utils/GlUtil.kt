/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.effect.beautykit.tuiextension.utils

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES20.GL_TRUE
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.opengles.GL10

/**
 * Some OpenGL utility functions.
 */
object GlUtil {

    private val TAG = GlUtil::class.java.name

    /**
     * Identity matrix for general use.  Don't modify or life will get weird.
     */
    @JvmField
    val IDENTITY_MATRIX = FloatArray(16).also {
        Matrix.setIdentityM(it, 0)
    }

    private const val SIZEOF_FLOAT = 4

    /**
     * Creates a new program from the supplied vertex and fragment shaders.
     *
     * @return A handle to the program, or 0 on failure.
     */
    @JvmStatic
    fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) {
            return 0
        }
        val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (pixelShader == 0) {
            return 0
        }

        var program = GLES20.glCreateProgram() // create empty OpenGL ES Program
        checkGlError("glCreateProgram")
        if (program == 0) {
            Log.e("GLUtil", "Could not create program")
        }
        GLES20.glAttachShader(program, vertexShader) // add the vertex shader to program
        checkGlError("glAttachShader")
        GLES20.glAttachShader(program, pixelShader) // add the fragment shader to program
        checkGlError("glAttachShader")
        GLES20.glLinkProgram(program) // creates OpenGL ES program executables
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GL_TRUE) {
            Log.e("GLUtil", "Could not link program: ")
            Log.e("GLUtil", GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            program = 0
        }
        return program
    }

    @JvmStatic
    fun readShaderFromResource(context: Context, resourceId: Int): String {
        val builder = StringBuilder()
        var inputStream: java.io.InputStream? = null
        var isr: InputStreamReader? = null
        var br: BufferedReader? = null
        try {
            inputStream = context.resources.openRawResource(resourceId)
            isr = InputStreamReader(inputStream)
            br = BufferedReader(isr)
            var line: String?
            while (br.readLine().also { line = it } != null) {
                builder.append(line + "\n")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                inputStream?.close()
                isr?.close()
                br?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return builder.toString()
    }

    /**
     * Compiles the provided shader source.
     *
     * @return A handle to the shader, or 0 on failure.
     */
    @JvmStatic
    fun loadShader(shaderType: Int, source: String): Int {
        var shader = GLES20.glCreateShader(shaderType)
        checkGlError("glCreateShader type=$shaderType")
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e("GLUtil", "Could not compile shader $shaderType:")
            Log.e("GLUtil", " " + GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            shader = 0
        }
        return shader
    }

    /**
     * Checks to see if a GLES error has been raised.
     */
    @JvmStatic
    fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            val msg = "$op: glError 0x${Integer.toHexString(error)}"
            Log.e("render", msg)
//            throw RuntimeException(msg)
        }
    }

    /**
     * Checks to see if the location we obtained is valid.  GLES returns -1 if a label
     * could not be found, but does not set the GL error.
     *
     * Throws a RuntimeException if the location is invalid.
     */
    @JvmStatic
    fun checkLocation(location: Int, label: String) {
        if (location < 0) {
            throw RuntimeException("Unable to locate '$label' in program")
        }
    }

    /**
     * Creates a texture from raw data.
     *
     * @param data   Image data, in a "direct" ByteBuffer.
     * @param width  Texture width, in pixels (not bytes).
     * @param height Texture height, in pixels.
     * @param format Image data format (use constant appropriate for glTexImage2D(), e.g. GL_RGBA).
     * @return Handle to texture.
     */
    @JvmStatic
    fun createTexture(data: ByteBuffer, width: Int, height: Int, format: Int): Int {
        val textureHandles = IntArray(1)

        GLES20.glGenTextures(1, textureHandles, 0)
        val textureHandle = textureHandles[0]
        checkGlError("glGenTextures")

        // Bind the texture handle to the 2D texture target.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle)

        // Configure min/mag filtering, i.e. what scaling method do we use if what we're rendering
        // is smaller or larger than the source image.
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat())
        checkGlError("loadImageTexture")

        // Load the data from the buffer into the texture handle.
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, format, width, height, 0, format,
            GLES20.GL_UNSIGNED_BYTE, data
        )
        checkGlError("loadImageTexture")

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        return textureHandle
    }

    @JvmStatic
    fun createTexture(bitmap: Bitmap?): Int {
        val texture = IntArray(1)
        if (bitmap != null && !bitmap.isRecycled) {
            GLES20.glGenTextures(1, texture, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST.toFloat())
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat())
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat())
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            return texture[0]
        }
        return 0
    }

    @JvmStatic
    fun createTexture(width: Int, height: Int, config: Int): Int {
        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        // Bind the texture handle to the 2D texture target.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
        // Configure min/mag filtering, i.e. what scaling method do we use if what we're rendering
        // is smaller or larger than the source image.
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, config, width, height, 0, GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE, null
        )

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        return texture[0]
    }

    @JvmStatic
    fun createOESTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        checkGlError("glGenTextures1")

        val texId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        checkGlError("glBindTexture2 $texId")

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        checkGlError("glTexParameter3")

        return texId
    }

    /**
     * Allocates a direct float buffer, and populates it with the float array data.
     */
    @JvmStatic
    fun createFloatBuffer(coords: FloatArray): FloatBuffer {
        // Allocate a direct ByteBuffer, using 4 bytes per float, and copy coords into it.
        val bb = ByteBuffer.allocateDirect(coords.size * SIZEOF_FLOAT)
        bb.order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(coords)
        fb.position(0)
        return fb
    }

    /**
     * Writes GL version info to the log.
     */
    @JvmStatic
    fun logVersionInfo() {
        Log.e("GLUtil", "vendor  : " + GLES20.glGetString(GLES20.GL_VENDOR))
        Log.e("GLUtil", "renderer: " + GLES20.glGetString(GLES20.GL_RENDERER))
        Log.e("GLUtil", "version : " + GLES20.glGetString(GLES20.GL_VERSION))
    }

    @JvmStatic
    fun saveBitmapToDisk(bitmap: Bitmap, name: String) {
        try {
            val file = File("/sdcard/$name.png")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun saveTexture(texture: Int, width: Int, height: Int, name: String) {
        val bitmap = readTexture(texture, width, height)
        saveBitmapToDisk(bitmap, name)
    }

    @JvmStatic
    fun readTexture(texture: Int, width: Int, height: Int): Bitmap {
        val frame = IntArray(1)
        GLES20.glGenFramebuffers(1, frame, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frame[0])
        checkGlError("glBindFramebuffer")

        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D,
            texture, 0
        )
        checkGlError("glFramebufferTexture2D")

        val data = ByteArray(width * height * 4)
        val buffer = ByteBuffer.wrap(data)
        GLES20.glPixelStorei(GLES20.GL_PACK_ALIGNMENT, GLES20.GL_TRUE)
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        checkGlError("glReadPixels")

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glDeleteFramebuffers(1, frame, 0)
        checkGlError("glBindFramebuffer")

        return bitmap
    }

    @JvmStatic
    fun releaseTexture(id: Int) {
        if (id >= 0) {
            GLES20.glDeleteTextures(1, intArrayOf(id), 0)
        }
    }
}
