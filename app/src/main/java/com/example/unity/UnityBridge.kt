package com.example.unity

import android.content.Context
import android.view.View
import android.util.Log

/**
 * Clean architectural bridge for integrating the Unity 3D character player.
 * Uses reflection so that the native Android app can compile successfully on AI Studio
 * even without importing the `:unityLibrary` binary files, while being 100% prepared
 * to load and communicate with the real Unity Talking Tom cat assets when on-device.
 */
object UnityBridge {
    private const val TAG = "UnityBridge"
    private const val UNITY_PLAYER_CLASS = "com.unity3d.player.UnityPlayer"

    /**
     * Determines if the exported Unity game engine is added to the Gradle build context.
     */
    fun isUnityLibraryAvailable(): Boolean {
        return try {
            Class.forName(UNITY_PLAYER_CLASS)
            true
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Instantiates the 3D Unity Player view and inserts it into the layout.
     * Returns null if the Unity SDK is not imported.
     */
    fun createUnityViewInstance(context: Context): View? {
        if (!isUnityLibraryAvailable()) {
            Log.d(TAG, "Unity library not detected. Running custom pseudo-3D vector simulation.")
            return null
        }
        return try {
            val unityClass = Class.forName(UNITY_PLAYER_CLASS)
            // Constructor signature: UnityPlayer(Context)
            val constructor = unityClass.getConstructor(Context::class.java)
            val unityPlayerInstance = constructor.newInstance(context)
            
            // UnityPlayer inherits from FrameLayout (which extends View), so this cast is safe.
            unityPlayerInstance as View
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to instantiate UnityPlayer reflectively: ${t.message}", t)
            null
        }
    }

    /**
     * Calls UnitySendMessage on com.unity3d.player.UnityPlayer.
     * Maps to: UnityPlayer.UnitySendMessage("Target_GameObject", "Method", "Json/StringParam")
     */
    fun sendActionTo3DCat(actionTag: String) {
        if (!isUnityLibraryAvailable()) {
            Log.d(TAG, "[MOCK UNITY DIALOGUE] actionTag: $actionTag")
            return
        }
        try {
            val unityClass = Class.forName(UNITY_PLAYER_CLASS)
            val method = unityClass.getMethod(
                "UnitySendMessage",
                String::class.java,
                String::class.java,
                String::class.java
            )
            // Call: UnitySendMessage("3D_Cartoon_Cat", "TriggerAILine", actionTag)
            method.invoke(null, "3D_Cartoon_Cat", "TriggerAILine", actionTag)
            Log.d(TAG, "Dynamic 3D Action sent to Unity -> TriggerAILine('$actionTag')")
        } catch (t: Throwable) {
            Log.e(TAG, "Error invoking Unity message: ${t.message}", t)
        }
    }

    /**
     * Mutes/unmutes Unity's audio listener so it doesn't feed back into the Android microphone.
     */
    fun setUnityAudioMuted(muted: Boolean) {
        if (!isUnityLibraryAvailable()) {
            Log.d(TAG, "[MOCK UNITY AUDIO] setUnityAudioMuted: $muted")
            return
        }
        try {
            val unityClass = Class.forName(UNITY_PLAYER_CLASS)
            val method = unityClass.getMethod(
                "UnitySendMessage",
                String::class.java,
                String::class.java,
                String::class.java
            )
            val stateStr = if (muted) "true" else "false"
            method.invoke(null, "AudioManager", "MuteEngineSounds", stateStr)
            Log.d(TAG, "Sent Audio state to Unity -> AudioManager.MuteEngineSounds('$stateStr')")
        } catch (t: Throwable) {
            Log.e(TAG, "Error invoking Unity Audio mute: ${t.message}")
        }
    }

    /**
     * Routes essential Android lifecycle hooks to Unity Player to maintain optimal 
     * memory allocation and garbage collection.
     */
    fun onPause(unityPlayerView: View?) {
        invokeLifecycleMethod(unityPlayerView, "pause")
    }

    fun onResume(unityPlayerView: View?) {
        invokeLifecycleMethod(unityPlayerView, "resume")
    }

    fun onDestroy(unityPlayerView: View?) {
        invokeLifecycleMethod(unityPlayerView, "unload")
        invokeLifecycleMethod(unityPlayerView, "destroy")
    }

    fun onLowMemory(unityPlayerView: View?) {
        invokeLifecycleMethod(unityPlayerView, "lowMemory")
    }

    private fun invokeLifecycleMethod(unityPlayerView: View?, methodName: String) {
        if (unityPlayerView == null) return
        try {
            val clazz = unityPlayerView.javaClass
            val method = clazz.getMethod(methodName)
            method.invoke(unityPlayerView)
            Log.v(TAG, "Dispatched lifecycle signal to Unity: $methodName")
        } catch (e: NoSuchMethodException) {
            // Ignore missing methods
        } catch (e: Exception) {
            Log.e(TAG, "Failed calling lifecycle method $methodName: ${e.message}")
        }
    }
}
