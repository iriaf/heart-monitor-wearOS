# Heart Monitor (Wear OS + Server)

## Overview
A Wear OS application paired with a FastAPI backend for prototyping heart-monitoring behavior and device-server interactions. The Kotlin app runs on a Wear OS device (or emulator) to collect hardware sensor data, while the Python server provides local HTTP/WebSocket endpoints to process the telemetry during development.

## Tech Stack
* **Client (Wear OS):** Kotlin, Android Jetpack, Wear OS UI libraries
* **Client (Web Frontend):** JavaScript, Chart.js library
* **Server:** Python 3.8+, FastAPI, Uvicorn
* **Build System:** Gradle

## Key Features
* **Direct Sensor Access:** Bypasses standard high-level health APIs to read directly from the Wear OS PPG hardware, preventing conflicts with native apps like Samsung Health.
* **Battery-Optimized Telemetry:** Implements configurable data batching to minimize strain on the smartwatch, preserving battery life during long sessions.
* **WebSocket-based approach:** Features a network layer with auto-reconnection logic, explicit state handling, and connection drop recovery. 
* **Data Recovery:** If the frontend disconnects, the server buffers the data locally to a `.csv` file. Upon reconnection, the client seamlessly fetches the missing timeframe and synchronizes the real-time chart.
* **Fluid UI & Live Dashboard:** Utilizes a custom display queue to render data smoothly on a Chart.js graph, masking network latency.
* **Local Data Ownership:** All telemetry is written asynchronously to local disk as flat `.csv` files, ensuring complete data privacy and easy import into the website for later statistical analysis.

## System Architecture
The system is built on a highly decoupled, event-driven architecture:
1. **Hardware Layer:** `SensorManager` reads raw BPM via hardware sensors.
2. **Wear OS Client:** Batches data and transmits via an OkHttp WebSocket connection over Wi-Fi.
3. **FastAPI Backend:** Acts as the middleman between the Wear OS Client and the Web Frontend and does asynchronous CSV writing.
4. **Web Frontend:** Consumes the WebSocket stream, plotting the heart rate graph in real-time.

## Motivation & Use Case
This project begun as a personal project, for my own usage. I like to train on an ergometric bike and, as i'm doing so, it's pleasant to watch something on my computer's main monitor. However, it's kind of annoying to keep turning on my watch to check my heart rate, so i decided that having a dashboard on my second monitor show real-time heart rate data would be cool, and as a bonus it would also serve as an introduction to the world of mobile // Kotlin development.

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

### 1. Run the FastAPI Server (Backend)
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

### 2. Build & Run the Wear OS App (Frontend)
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
MIT License

Copyright (c) 2026 Yazdan Tajik

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
