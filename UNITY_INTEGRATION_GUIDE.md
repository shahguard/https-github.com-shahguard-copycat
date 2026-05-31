# 🐾 Unity 3D Talking Tom Integration Guide

This guide describes how to integrate a high-fidelity 3D cartoon pet (such as Talking Tom) from Unity into this Android Jetpack Compose application. We have pre-built the architecture so that the app compiles successfully right now, and automatically switches to the live 3D player whenever you import your `:unityLibrary`!

---

## 🏗️ 1. Architecture Built in Android Studio

We implemented a decoupled, reflection-based Unity bridge to ensure that your native Android codebase remains cleanly maintainable, and compilation never breaks when you work across multiple machines:

1. **`UnityBridge.kt` (`com.example.unity`)**:
   - Detects the existence of `com.unity3d.player.UnityPlayer` class using classloader reflection.
   - Instantiates the real `UnityPlayer` view dynamically at runtime.
   - Houses the `sendActionTo3DCat(action)` method which maps perfectly to `UnityPlayer.UnitySendMessage("3D_Cartoon_Cat", "TriggerAILine", actionTag)`.
   - Propagates critical Android activity lifecycles safely (`onPause`, `onResume`, `onDestroy`, `onLowMemory`) to Unity to maintain elegant memory collection.
2. **`MainDashboard.kt` (`com.example.ui`)**:
   - Automatically renders the dynamic 3D view using Compose's `AndroidView` wrapper if `isUnityLibraryAvailable()` is true.
   - Falls back to the beautiful custom-drawn animated vector pet Fallback when Unity is not loaded.
3. **`PetViewModel.kt` (`com.example.ui.viewmodel`)**:
   - Listens to active Buster character state changes and automatically dispatches corresponding actions under thehood:
     - `HAPPY_DANCE` ➔ `"dance"`
     - `SPEAKING` ➔ `"sing"`
     - `HEARING` ➔ `"hear"`
     - `EATING` ➔ `"eat"`
     - `SLEEPING` ➔ `"sleep"`
     - `BATHING` ➔ `"shower"`
     - `DIZZY` ➔ `"dizzy"`
     - `IDLE` ➔ `"idle"`

---

## 🎨 2. Sourcing & Preparing the 3D Asset
A 3D bipedal cartoon pet requires a 3D Mesh (body), a Skeleton Rig (internal bones), and Animation Clips (idle, dance, sing, tap reactions).

We have customized this segment with your newly chosen **Talking Tom Baby** asset link!

1. **Obtain the Character**:
   - **Model Reference**: *Talking Tom Baby* by *juicetime32* (from Sketchfab Model ID `f049f53340404bfb87ef2e14c99b37f7`).
   - **Direct Google Drive Download Link**: [Download Talking Tom Baby Asset File](https://drive.google.com/file/d/1x81J1nL3kWPGz3G1U0bdfP6nZCgoLvrW/view?usp=sharing)
2. **Auto-Rig & Animate via Mixamo**:
   - Extract the downloaded FBX/OBJ file from your Google Drive link.
   - Upload the pet model directly to [Adobe Mixamo](https://www.mixamo.com/) (free).
   - Position the joint rings (Chin, Wrists, Elbows, Knees, Groin).
   - Search and attach animations such as:
     - **Idle breathing** (perfect for the idle state)
     - **Hip-hop / Club dancing** (perfect for the "dance" action)
     - **Singing / Speaking performance** (perfect for speaking AI voices)
     - **Tap responses / dizzy head shakes** (perfect for interactions)
   - Download the animated FBX files configured for Unity.

---

## 🎮 3. Building the 3D Interactive Controller in Unity

1. Create a new Unity Android project.
2. Place your Rigged Cat in the center of the Scene. Set its Game Object name to **`3D_Cartoon_Cat`** (this is what Android targets!).
3. Set up your **Animator State Machine** with the states and parameters:
   - Triggers: `onTap`, `Purr`, `Stomp`, `DefaultReact`, `SingingLoop`, `EatLoop`, `SleepLoop`, `ShowerLoop`
   - Booleans: `IsDancing`
4. Attach the following C# script (`CatInteraction.cs`) onto the cat GameObject:

### 📄 `CatInteraction.cs`
```csharp
using UnityEngine;

public class CatInteraction : MonoBehaviour
{
    private Animator catAnimator;
    private AudioSource catVoice;

    void Start()
    {
        catAnimator = GetComponent<Animator>();
        catVoice = GetComponent<AudioSource>();
    }

    void Update()
    {
        // Detect mobile screen taps / raycast touch
        if (Input.touchCount > 0 && Input.GetTouch(0).phase == TouchPhase.Began)
        {
            Ray ray = Camera.main.ScreenPointToRay(Input.GetTouch(0).position);
            RaycastHit hit;

            if (Physics.Raycast(ray, out hit))
            {
                // Head Pat triggers purring/giggles
                if (hit.collider.name == "Head" || hit.collider.CompareTag("Head"))
                {
                    catAnimator.SetTrigger("Purr");
                    PlayCuteSound("purr_sound");
                }
                // Foot poke triggers stomp reaction
                else if (hit.collider.name == "Foot" || hit.collider.CompareTag("Foot"))
                {
                    catAnimator.SetTrigger("Stomp");
                    PlayCuteSound("oww_sound");
                }
                else
                {
                    catAnimator.SetTrigger("DefaultReact");
                }
            }
        }
    }

    /// <summary>
    /// Router endpoint called from Android Native Studio.
    /// Maps to UnityPlayer.UnitySendMessage("3D_Cartoon_Cat", "TriggerAILine", actionTag)
    /// </summary>
    public void TriggerAILine(string actionTag)
    {
        // Clear old repeating properties
        catAnimator.SetBool("IsDancing", false);

        switch (actionTag)
        {
            case "dance":
                catAnimator.SetBool("IsDancing", true);
                break;
            case "sing":
                catAnimator.SetTrigger("SingingLoop");
                break;
            case "eat":
                catAnimator.SetTrigger("EatLoop");
                break;
            case "sleep":
                catAnimator.SetTrigger("SleepLoop");
                break;
            case "shower":
                catAnimator.SetTrigger("ShowerLoop");
                break;
            case "dizzy":
                catAnimator.SetTrigger("DefaultReact");
                break;
            default:
                // Return to Idle breathing loop
                break;
        }
    }

    private void PlayCuteSound(string soundName)
    {
        if (catVoice != null) {
            AudioClip clip = Resources.Load<AudioClip>("Sounds/" + soundName);
            if (clip != null) {
                catVoice.PlayOneShot(clip);
            }
        }
    }
}
```

---

## 🚀 4. Exporting Unity as an Android Module

1. Open **File > Build Settings** in Unity.
2. Choose **Android** platform and tick **Export Project**.
3. Open **Player Settings**:
   - Ensure **Minimum API Level** is **26** (matching your Android Studio settings).
   - Configure **Scripting Backend** to **IL2CPP**.
   - Check **both** ARMv7 and ARM64 system architectures.
4. Click **Export** and choose a local output directory. Unity will generate a library folder named `unityLibrary`.

---

## 🔌 5. Integrating the Module into Android Studio

Once exported, you move the file folder into your current project workspace:

1. Copy the `unityLibrary` folder into your Android project root path.
2. In your project's root `settings.gradle.kts` structure, ensure it includes the module:
   ```kotlin
   include(":app")
   include(":unityLibrary")
   project(":unityLibrary").projectDir = file("unityLibrary")
   ```
3. In your app-level `/app/build.gradle.kts`, depend on the module inside the dependencies block:
   ```kotlin
   dependencies {
       implementation(project(":unityLibrary"))
       implementation(fileTree(mapOf("dir" to project(":unityLibrary").projectDir.toString() + "/libs", "include" to listOf("*.jar"))))
   }
   ```
4. Build / Sync Gradle. The dynamic classloader reflection in `UnityBridge` will automatically detect the classes, initialize the real interactive 3D character viewport, and hook up the OpenRouter AI chat triggers seamlessly!
