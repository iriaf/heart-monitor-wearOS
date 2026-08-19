# Heart Monitor (Wear OS + Server)

## Overview
A Wear OS application paired with a FastAPI backend for prototyping heart-monitoring behavior and device-server interactions. The Kotlin app runs on a Wear OS device (or emulator) to collect hardware sensor data, while the Python server provides local HTTP/WebSocket endpoints to process the telemetry during development.

## Tech Stack
* **Client (Wear OS):** Kotlin, Android Jetpack, Wear OS UI libraries
* **Server:** Python 3.8+, FastAPI, Uvicorn
* **Build System:** Gradle

---

## Repository Layout
```text
.
├── app/                    # Wear OS app (Kotlin, Gradle project)
├── gradle/                 # Gradle wrapper files
├── server/                 # FastAPI backend server
├── build.gradle.kts        # Multi-module build configuration
├── settings.gradle.kts     # Gradle settings
├── gradlew                 # Gradle wrapper (Unix)
└── gradlew.bat             # Gradle wrapper (Windows)
```
## How to Run
Clone the repository and follow the instructions below to start the backend server and the Wear OS client.

1. Run the FastAPI Server (Backend)
Navigate to the server directory from the repository root:

```bash
cd server
```
Create and activate a virtual environment:

```bash
python3 -m venv .venv
```

# macOS / Linux
```bash
source .venv/bin/activate
```

# Windows (PowerShell)
```powershell
.venv\Scripts\Activate.ps1
```

Install dependencies:
Install them via the requirements.txt file:

```bash
pip install -r requirements.txt
```
Alternatively, install the core requirements directly:

```bash
pip install fastapi uvicorn aiofiles aiocsv jinja2
```
Start the server:
The server will listen on port 8000 by default. The --reload flag enables hot-reloading for development.

```bash
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

2. Build & Run the Wear OS App (Frontend)
The recommended approach is to open the app/ module in Android Studio and run it directly on a physical Wear OS device or emulator.

In the file `app/src/main/java/com/example/myapplication/service/HeartRateService.kt`, make sure to 
configure the URL for the WebSocket access, otherwise your watch/emulator will not be able to connect to the server.


Alternatively, you can use the Gradle wrapper from the repository root.

Build the debug APK:
# Unix
```bash
./gradlew assembleDebug
```

# Windows
```windows
gradlew.bat assembleDebug
```

Install to a connected device:

```bash
./gradlew installDebug
```

## Developer Setup & Troubleshooting
Prerequisites: JDK, Android SDK, Android Studio, and a Wear OS emulator/physical device.

Network Routing: If you are using an emulator and it cannot reach the localhost server on your host machine, use ADB port-forwarding/reverse:


```bash
adb reverse tcp:8000 tcp:8000
```
Also, make sure that the device running the server has its firewall profile on the private network.


Debugging: Enable logging on both sides to track connection states. Use Android Logcat for the Wear OS app and the Uvicorn console for the FastAPI server.

Flaky Connections: If the app claims to be "connected" but data is not flowing, ensure explicit timeouts and connection checks are properly handled before relying on the server-side state.

## TODOs
- Properly implement debug mode.
- Improve the feel of zooming in and out of the graph.
- Revamp visuals (both website and watch app).
- Implement basic statistical analysis of data.
- Modularize watch-related code.
- Remove unnecessary images (mipmaps and playstore image).
- Improve this README (eternally in maintenance :)).

## Contributing
Please open issues for specific TODO items and bugs.

Server changes: Follow the development flow above and add tests where appropriate.

App changes: Prefer Android Studio run/debug for fast iteration.

## License
(TBA lol)

## Contact
Send an e-mail if you want to ig